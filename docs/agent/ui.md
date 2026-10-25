# UI（Compose）约定与陷阱

所有界面都是 Compose，没有 XML 布局。文件都在 `app/src/main/java/dev/xbot/android/ui/`。
主题 token 在 `ui/theme/Theme.kt`，只定义了 `lightColorScheme` / `darkColorScheme`，**没有自定义
Typography**，所以字号一律显式写 `MaterialTheme.typography.*`，别依赖默认值。

颜色语义：主要信息 `onSurface` / `onBackground`，次要文字 `onSurfaceVariant`，
可点击/强调 `primary`，状态告警 `Color(0xFFF59E0B)`，正常连接 `Color(0xFF22C55E)`。

## TopAppBar 的槽位宽度语义（踩过的坑）

`TopAppBar` 的测量顺序是：先 `navigationIcon`，再 `actions`，**`title` 只拿到剩下的宽度**。

所以往 `actions` 里放长文本（例如模型名 `TextButton`，`widthIn(max=150.dp)` + 两个 `IconButton`）
在 360dp 屏上会吃掉约 270dp，title 只剩约 30dp —— 标题被截成一个汉字，
不带 `maxLines`/`softWrap` 的副标题会**逐字竖排**。0.3.3 的聊天页头部就是这个症状。

规则：

- `actions` 只放图标按钮；可变的文本信息（模型名、状态）放副标题。
- 标题：`titleMedium` + `SemiBold` + `maxLines = 1` + `TextOverflow.Ellipsis`。
- 副标题：单行 `Row`，状态点 6dp + `labelSmall` 文本，每段都加 `maxLines = 1, softWrap = false`。
- 副标题里最长的可变量（模型名）用 `Modifier.weight(1f, fill = false)` + `Ellipsis`，
  让它先被压缩，而不是挤压标题。
- 文案不要 `take(n)` 硬截断（会在 token 中间切断），交给 `Ellipsis`。

参考实现：`ui/ChatScreen.kt` 的 `Scaffold(topBar = ...)` 段。

## 聊天页结构（ui/ChatScreen.kt）

- `Scaffold(topBar = TopAppBar, bottomBar = Composer, snackbarHost = ...)`，内容区是 `LazyColumn`。
- 贴底（auto-follow）逻辑在 `ui/ChatScroll.kt`（纯函数，`ChatScrollTest` 覆盖），ChatScreen 只喂事件：
  - 「尾部指纹」`tailFingerprint()` **不含 `turns.size`**：上滑补页是往前面插入历史，
    长度变了但尾部没变，用长度判断会把用户拽回底部（0.4.1 修的）。
  - `LaunchedEffect` 只以 `(tail, state.loading)` 为 key：**绝不能把 `itemCount` 放进 key**
    （补页会变），**也不能把 `follow` 放进 key**（`follow` 一变就会滚，等于任何重算都可能把人拽走）。
  - 别用 `canScrollForward` 推断「用户回到底部」：remeasure 期间它会瞬时变成 `false`
    （输入区「工作目录 · 上下文」状态栏出现、图片加载、insets 变化都会触发），
    旧实现据此把 `follow` 置回 true，空闲会话照样跳底部（0.4.2 修的）。
    现在只有 `isScrollInProgress` 由 true→false（一次滚动真的结束）且 `isSettledAtBottom()`
    （布局非空 + 最后一项就是末尾 + 底边在可视区内）才能恢复 `follow`。
  - 滚动一开始就 `follow = false`，不看此刻能否向下滚；「跳到顶部」按钮显式置 `false`。
  - 贴底滚动前先等 `isScrollInProgress == false`，别和用户手势抢 `LazyListState`。
  - **`scrollToItem(last)` 不是「到底部」**：它只把最后一项的**顶部**对齐，而它触发的
    measure 要到**下一帧的 layout 之后**才生效；在那之前 `canScrollForward` / `scrollBy`
    用的还是旧位置的测量结果（跟随状态下旧位置就在底部 → 读到 false），于是 `scrollBy`
    一个像素都不消费 —— 停在最后一项顶部。新一轮对话里 TurnBlock 的开头正是用户气泡，
    所以症状会表现为「跳到了用户那条消息」。`withFrameNanos` 回调也排在 layout 之前，
    必须**等两帧**再判断/再滚（见 `scrollToBottom`）。
  - 上滑补页的锚点恢复带 `scrollEpoch` 保护：请求发出后用户又滚过就不再拽回旧锚点。
  - 跟随尾部要用**单个长生命周期 collector**（`snapshotFlow { tail }` 在
    `LaunchedEffect(listState)` 里），不要用以 `tail` 为 key 的 `LaunchedEffect`：
    后者每次尾部变化都取消上一次协程，滚动做到一半被取消就又停到最后一项顶部。
  - 复现类问题用设置 → 滚动诊断（`ui/ChatScrollTrace.kt`）：环形记录最近 200 条滚动事件
    与布局快照，也能从 logcat 标签 `ChatScroll` 取。
- 历史补页靠边界触发（`isScrollInProgress to firstVisibleItemIndex` + `pendingAnchor` 还原位置），
  不要用 `LaunchedEffect(Unit)` 一次性补页。
- Markdown / 表格 / Mermaid / 图片分别由 `ui/components/MarkdownText.kt`、`MermaidView.kt`、
  `MarkdownImage.kt` 负责，图片 URL 会经 `ImageUrl.kt` 把 `localhost` 重写成 `AppPrefs.mediaBaseUrl`。

## 自查清单

- 改头部/底部栏后，按 360dp 窄屏想象一次测量：`actions` 宽度 + 必要导航图标是否已经占满整行。
- 长标题、长模型名、系统字体放大（fontScale 1.3+）三种情况都不能换行或撑破 64dp 的 TopAppBar。
- 新增可点击元素要带 `contentDescription`（纯装饰的 `Icon` 传 `null` 并在宿主上加语义）。
