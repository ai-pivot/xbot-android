# 会话（sessions/）——新建会话与工作目录

`SessionsScreen` + `SessionsViewModel`：会话列表、新建/Fork/重命名/删除。

## 新建会话时指定工作目录

**服务端的 `POST /api/chats/create` 只接受 `label` / `subscription_id` / `model`，没有 `work_dir`。**
工作目录只能建完会话后单独设置：

```
POST /api/chats/create  {label}                 → chat_id
POST /api/rpc            set_cwd {channel, chat_id, dir, force: true}
```

`force: true` 是**显式用户动作**的语义（服务端 `rpc_table.go` 注释里直接写了「新建会话弹窗等」）：
传什么路径就用什么路径，不会被既有 cwd 或自动推断值顶掉。Web 端 `components/agent/api.ts` 的
`setCwd` 同样是 `force: true`，两端行为一致。**不要传 `force: false`**，否则服务端可能按自己的规则改写路径。

失败处理：`set_cwd` 失败**不代表会话创建失败**。`SessionsViewModel.createChat` 会保留会话、
通过 `SessionsUiState.notice` 弹一条 snackbar（「会话已创建，但工作目录设置失败：…」），
用户仍可在会话里再改。

## 候选目录从哪来

工作目录在**服务器**上，手机侧看不到文件系统，服务端也没有目录浏览接口
（`channel/web` 里没有 `list_dir` / `fs/list` 之类的端点）。所以 UI 是「手输 + 候选」：

- `AppPrefs.recentWorkDirs`：最近用过的，最新在前，最多 5 个，存 SharedPreferences（JSON 数组）。
  只有 `set_cwd` 成功才记；纯本地状态，跨设备不同步（Web 端存 localStorage）。
- 现有会话里出现过的 `work_dir` 作为补充。
- 合并逻辑是纯函数 `data/WorkDirSuggestions.kt: workDirSuggestions(recent, sessions, limit)`，
  去重、跳过空串、截断，`WorkDirSuggestionsTest` 覆盖。

候选在 `SessionsViewModel.refresh()` 里算好放进 `SessionsUiState.workDirSuggestions`，
UI 只负责渲染（项目约定：UI 层不做数据推导）。

## 与「会话项显示工作目录」的关系

列表项用的是 `UserChat.workDir`（服务端 `work_dir`，`omitempty`），新建会话成功后由 `refresh()` 重新拉取，
所以刚建好就能看到路径；`set_cwd` 失败时该字段为空，列表里自然不显示。
