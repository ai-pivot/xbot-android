# 输入区（Composer）与上下文预算指示器

`ui/components/Composer.kt` 是底部输入区（Scaffold 的 `bottomBar`）；
`ui/components/ContextBudgetIndicator.kt` 是输入框**上方**那行上下文占用文字（`85K/200K`）+ 明细弹窗。

## 高度为什么用 BasicTextField 而不是 OutlinedTextField

Material3 的 `OutlinedTextField` / `TextField` 在**装饰盒内部**写死了 `TextFieldDefaults.MinHeight`
（56dp）和 `ContainerHeight`，从外面套 `Modifier.height(40.dp)` 或 `defaultMinSize` 都压不下去，
只会裁切内容。想真正压缩高度必须改用 `BasicTextField` + 自己做背景/边框/占位符：

- 单行时高度 ≈ `bodyMedium` 行高（20sp）+ 上下 8dp padding ≈ 36dp，比 56dp 省 20dp。
- 占位符放在 `Box` 里、`BasicTextField` 之前，`value.isEmpty()` 时显示；`Box` 高度取两者的最大值。
- 圆角/边框用 `clip(RoundedCornerShape(18.dp)) + background(surfaceVariant α0.55) + border(1.dp outline α0.45)`。
- `maxLines = 6` 保留多行自增长；`cursorBrush = SolidColor(primary)` 否则光标不可见。

图标按钮同理由默认 48dp 压到 36dp（`Modifier.size(36.dp)` + `Icon` 20dp），行内 `spacedBy(2.dp)`，
整条输入区（含 `imePadding` / `navigationBarsPadding` 之外的 6dp 内边距）在单行时约 48dp。

## 上下文预算的数据来源

只有一条真实数据源：`POST /api/session/status`（服务端 `channel/web/web_rest.go: handleSessionStatus`）。

```
{ "token_usage": { "available": bool, "prompt_tokens": n, "completion_tokens": n,
                   "max_context_tokens": n, "usage_percent": 0..100|null,
                   "model": "...", "subscription_name": "..." },
  "cwd": "...", "todos": [...] }
```

要点：

- **`available == false` 表示服务端还没拿到真实 API token 数**（新会话、或从没跑过 LLM）。
  这时不要画 0%，直接不显示指示器（`ChatReducer.withContextBudget` 返回 `contextBudget = null`）。
- `usage_percent` 是指针，可能是 `null`；`0.0` 且 `available == true` 是合法值（刚清空上下文）。
- 服务端 `protocol.TokenUsage`（progress 事件里的那个）**没有 max_context**，
  只有 `prompt_tokens / max_output_tokens`。所以回合进行中只能用
  「上一次 status 拿到的 `max_context_tokens` 做分母」估算 ——
  `ChatReducer.withLiveTokenUsage`，拿不到上限就保持原值不动。
- 刷新时机（`ChatViewModel`）：`reload()` 成功之后、SSE 里 `busy` 由 true 转 false 时。
  失败静默 —— 指示器只是辅助信息，不该因为它弹错误条。

## 状态与 UI 的契约

- `ContextBudgetUi`（`chat/ChatModels.kt`）：`promptTokens / maxContextTokens / percent(0..1) / model / exact`。
  `exact = false` 表示是自己估的，弹窗里会标注「回合进行中估算」。
- 配色阈值：`< 60%` primary，`60%~85%` 琥珀 `0xFFF59E0B`，`>= 85%` error。
- 显示形式：输入框上方右对齐的一行小字 `已用输入/上下文上限`（如 `85K/200K`），
  按 `formatTokens` 压缩成 K/M；上限未知时右半显示 `—`。
- 文字可点：弹出「上下文预算」对话框，显示已用输入 / 上限 / 占用 / 模型。
  没有数据时（`contextBudget == null`）**整行都不存在**，输入区高度与宽度都不会跳。
