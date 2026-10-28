# 流式渲染与事件合批

模型输出到屏幕这条链路上有两个独立的问题，分别由两处代码处理：

```
SSE 事件 → EventBatcher（leading + 60ms trailing）→ ChatReducer.applyEvent
        → ChatScreen 渲染 → PacedMarkdown（按积压量配速显示）→ MarkdownText
        → 内容长高 → 粘底修正（layout 驱动 deficit）
```

## 1. 事件合批：`chat/EventBatcher.kt`

`Flow<T>.batchByWindow(windowMs = 60, maxBatch = 64)`：

- **leading**：窗口里第一条立刻发出去（不然每个回合第一个字要多等一个窗口）；
- **trailing**：随后最多再攒 60ms（或攒够一批），一次性 `apply`；
- 上游结束把剩余的发完。

`ChatViewModel.startStream()` 里把 `client.events` 接上它，一批事件在**一个**
`_state.update` 里 `fold` 完 —— 否则模型每吐一个 token 就是一次重组 + 重排。

参考 Paseo 移动端：服务端 `agent-stream-coalescer`（每 agent 每 60ms 一条）+ 客户端
`session-stream-reducers`（每帧只提交一次）。

## 2. 配速显示：`ui/TextReveal.kt`

`PacedMarkdown(text, streaming)`：

- **到达只更新目标长度，显示速率由积压反推**：`computeRevealStep(backlog, elapsed)`，
  每帧推进 `backlog * elapsed / 150ms`（向上取整、至少 1 字）。积压多追得快，积压少慢慢补，
  观感连续 —— 这是"不蹦字"的关键，缩小合批窗口解决不了。
- **首次出现整段渲染**（`revealed` 初始化为 `text.length`），只有"增长"才配速；
  历史消息、重新挂载不需要特判。
- **`streaming = false` 立刻 snap 到全文**，绝不把字留在尾巴上。
- 切点走 `clampToSafeRevealBoundary`：判断切点**之后**的字符是不是附加成分 / ZWJ /
  低位代理，是就整簇后退 —— 避免 emoji、组合音标闪半个字形。
- 渲染按 `stableBlockBoundary` 切成 head + tail：head 字符串只在整块写完时变，
  `MarkdownText` 里的 `remember(text) { parseMarkdown(text) }` 能命中；每帧只重解析短 tail。

参考 Paseo `agent-stream/text-reveal.ts` + `docs/agent-stream-performance.md`（他们实测：
每帧推进文本的帧占比 6% → 87%，可见更新间隔 p95 383ms → 17ms）。

## 3. 粘底：layout 驱动

配速让正文每帧长一点，靠"尾部指纹变化"触发贴底会滞后一整个合批窗口。`ChatScreen` 里
另一个 effect 直接盯 `layoutInfo`：跟随状态下最后一项超出视口多少就 `scrollBy(deficit)`，
单次滚动同帧生效；`isScrollInProgress` 为 true（用户手势）时立即停手。

## 不要做的事

- 不要在 `ChatReducer` 里做配速/节流：状态必须存**全文**，否则复制、选中、滚动几何都会和屏幕不一致。
- 不要用「定时器每 N ms 取当前文本」实现配速：会被到达节奏带着走，且丢字。
- 不要为了"看起来平滑"去调小 SSE 合批窗口：块更小更频繁，反而更抖。
