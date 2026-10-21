# 聊天状态机（chat/）——历史装载的两条路径

`ChatReducer` 是纯函数状态机，`ChatViewModel` 只负责拉数据、派发事件。历史装载有两条**语义不同**的路径，
混用会产生「界面上留着服务端已经没有的消息」这类 bug。

## withHistory：替换

`ChatReducer.withHistory(state, response)` 用响应**重建** `turns`（`response.messages` 全量映射），
同时重置 `hasMore / oldestId`。何时必须用它：

- 首次进入会话（`ChatViewModel.open()` 里 `_state = ChatReducer.empty(...)` 之后）；
- **服务端可能删掉了 turn 的任何情况**：Rewind 回退、历史压缩截断。

## mergeLatestHistory：合并

`ChatReducer.mergeLatestHistory(state, response)` 从 `state.turns` 出发，把响应里出现的 turn 覆盖上去，
并保留更早的分页游标（`oldestId`）。它是为「无缝补页」服务的：用户上滑加载过多页之后，
SSE 重连 / `resync_required` 触发的重载不能把这些页丢掉，否则会陷入 reload/loadMore 循环。

**它只增不减**：服务端不再返回的 turn 会被原样保留。所以拿它处理「删除类」事件，界面就会永远滞后。

## 什么时候用哪个

| 触发 | 用哪个 | 说明 |
|---|---|---|
| `open()` 首次载入 | `withHistory` | state 是空的 |
| 普通 `reload()`（重连、resync、下拉刷新） | `mergeLatestHistory` | 保住已加载的旧页 |
| **Rewind 成功后** | `withHistory` | 回退删掉了 turn，合并会让它继续显示 |
| SSE `session.action == "history_rewound"` | `withHistory` | 可能是另一端发起的回退 |

实现上由 `ChatViewModel.reload(replaceTurns: Boolean = false)` 统一入口：

- `reload(replaceTurns = true)` → `withHistory`（整体替换）；
- 默认 → `mergeLatestHistory`（合并）。

SSE 侧由 reducer 标记 `ChatUiState.needsReload` + `reloadReplacesHistory`（`applySession` 处理
`history_rewound`），ViewModel 消费时把后者传给 `reload(replaceTurns = ...)`。
**不要只用 `needsReload` 一个布尔量**：`resync_required` 也用它，而那条路径必须是合并语义。

## 踩过的症状

Rewind 在服务端已成功、客户端也解析对了响应，但屏幕上的消息不消失，**必须退出会话再重新进来**才正常。
原因是 `reload()` 当时无条件走合并分支（会话已加载时 `turns.isNotEmpty() && oldestId > 0` 恒成立），
而 `open()` 会重置 state，才「看起来好了」。新增用例
`replace reload drops turns the server no longer returns` 同时钉住了两条路径的差异。
