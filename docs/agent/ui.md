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
- 跟随底部逻辑：用户上滑（`collectIsDraggedAsState` + `canScrollForward`）时关掉 `follow`，
  回到底部（`canScrollForward == false`）时重新开启；历史补页靠边界触发，不要用 `LaunchedEffect(Unit)` 一次性补页。
- Markdown / 表格 / Mermaid / 图片分别由 `ui/components/MarkdownText.kt`、`MermaidView.kt`、
  `MarkdownImage.kt` 负责，图片 URL 会经 `ImageUrl.kt` 把 `localhost` 重写成 `AppPrefs.mediaBaseUrl`。

## 自查清单

- 改头部/底部栏后，按 360dp 窄屏想象一次测量：`actions` 宽度 + 必要导航图标是否已经占满整行。
- 长标题、长模型名、系统字体放大（fontScale 1.3+）三种情况都不能换行或撑破 64dp 的 TopAppBar。
- 新增可点击元素要带 `contentDescription`（纯装饰的 `Icon` 传 `null` 并在宿主上加语义）。
