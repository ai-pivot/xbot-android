# 推送（push/）——FCM 数据消息 + 本地通知

后台通知不再由 app 自己维持 SSE 前台服务。服务端通过 **FCM data-only** 下发语义事件，
app 收到后自己渲染通知。前台 SSE 仍然保留，只用于 app 可见时的实时流。

## 数据流

```
服务端 push.Notifier ──FCM data-only──► XbotMessagingService
                                            │
                                            ▼
                                      Notifications.show()
                                            │
                                            ▼
                                系统通知（点击带 chat_id 深链）
```

## 消息格式（服务端 → app）

`data` 里全部是字符串：

```json
{
  "kind": "ask_user" | "turn_done" | "test",
  "chat_id": "chat_xxx",
  "title": "xbot 需要你的回答",
  "body": "要部署到哪个目录？",
  "request_id": "req_xxx",
  "ts": "1729560000"
}
```

- `ask_user`：高优先级；通知 id 按 `request_id` 稳定（重复投递只覆盖，不堆叠）。
- `turn_done`：普通优先级；通知 id 按 `chat_id` 稳定。
- 兼容 `notification` 消息（系统直接展示）作为回退：`XbotMessagingService` 会优先读
  `data`，没有时读 `message.notification`。

## 文件

| 文件 | 作用 |
|---|---|
| `push/XbotMessagingService.kt` | 接收 FCM 消息，解析 data，调用通知渲染 |
| `push/Notifications.kt` | 渠道 `xbot_messages`、图标、深链、稳定 id |
| `push/PushRegistrar.kt` | 取 token；登录后注册、`onNewToken` 重注册、退出注销；401 静默重登再试 |
| `push/PushPresence.kt` | 前台每 30s 续租，后台置 false（服务端 90s 租约） |
| `data/XbotApi.kt` | `pushRegister/pushUnregister/pushPresence/pushTest` |
| `MainActivity.kt` / `ui/App.kt` | 读取通知的 `chat_id`，冷启动/热启动都导航到对应会话 |

服务端接口（统一 provider 无关）：

```
POST /api/push/register   {provider, platform, address, metadata, device_name, app_version}
POST /api/push/unregister {provider, address}
POST /api/push/presence   {provider, address, active}
POST /api/push/test
```

Android 用 `provider=fcm, platform=android, address=<FCM token>`。

## 配置 / 构建

- `app/google-services.json` **已提交**：只含 project_id / app_id / api_key 等客户端标识
  （API key 受包名/SHA 限制）；真正的秘密是服务端的 service account JSON，绝不提交。
- 服务端必须启用 `push.enable=true` + `push.fcm.enable=true`，并放好
  `~/.xbot/fcm-service-account.json`（600）。
- release/dev 两个通道用同一个 release keystore；**本地 debug 包签名不同**，FCM 仍能拿
  token（FCM 不校验 SHA-1），但如果以后加 Google 登录需要单独登记 debug SHA-1。

## 坑

- **强行停止收不到**：Android 系统行为，不是 bug。
- **OEM 可能延迟 data-only**：服务端 `push.fcm.mode` 可切 `notification` 回退；
  app 侧对两种模式都做了兼容。
- **registration 需要登录态**：`PushRegistrar.registerIfLoggedIn` 只在 cookies 非空时调用；
  登录成功后 `LoginViewModel` 会主动注册一次。
- **presence 是设备级的**：不要用“服务端有没有 SSE 连接”判断，桌面浏览器会把手机通知静音。
- **通知权限**：Android 13+ 必须授权 `POST_NOTIFICATIONS`；设置页的「通知权限」卡片提供入口。
- 旧的 `service/ChatNotificationService`、`BootReceiver`、`AppVisibility` 已删除；
  不要再把后台 SSE 加回来。
