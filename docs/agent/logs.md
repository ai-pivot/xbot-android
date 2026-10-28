# 端侧日志（logs/）

**原则：日志常驻、全局可用，不要再为某一个问题临时加探针。**
0.4.3 加过 `ChatScrollTrace`（只为查滚动跳底部），0.4.8 又加过上传失败提示 —— 用完就废、
下次出问题又得重加。现在是统一的一套。

## 结构

```
logs/AppLog.kt     环形缓冲 + 落盘 + logcat + 崩溃捕获 + 脱敏（纯函数部分可 JVM 单测）
logs/LogBundle.kt  设备信息 + zip 打包 + 上传
ui/LogsScreen.kt   设置 → 日志：查看 / 复制 / 打包 / 上传 / 清空
```

三层输出：

1. **内存环形缓冲** `AppLog.DEFAULT_CAPACITY = 1500` 条，UI 实时看、一键复制；
2. **落盘** `filesDir/logs/app.log`，超 512KB 滚动成 `app.1.log` / `app.2.log`，
   进程被杀、闪退之后还能捞；`Thread.setDefaultUncaughtExceptionHandler` 会把崩溃栈
   同步写进去再交给上一个 handler；
3. **logcat**，tag 加前缀 `xbot-`（如 `xbot-http`），`adb logcat -s xbot-http` 直接看。

`AppLog.revision`（StateFlow）每写一条自增，日志页据此刷新，别的地方不要订阅它。

## 约定

- **脱敏在写入前统一做**（`redactSecrets`）：`cookie / authorization / password /
  secret / token / access_token / refresh_token / api_key / signature` 的 `key=value`
  与 `key: value` 都会被替换成 `key=***`。加新日志时不用自己处理敏感字段，但**不要**
  把整个响应体（尤其登录接口）直接塞进日志。
- **高频事件要过滤**：SSE 的 `progress / stream_content / sync_progress / text` 每几毫秒
  一条，只记结构性事件（见 `XbotSseClient.NOISY_EVENTS`）。HTTP 只记
  方法/路径/状态码/耗时/响应字节数，失败额外带截断后的错误体与异常栈。
- 模块 tag：`http`（每个 REST 调用）、`sse`（连接/重连/事件）、`upload`（附件）、
  `log`（日志包本身）、`chat`（会话动作）、`scroll`（列表贴底）、`crash`。

## 打包与上传

- `LogBundle.packageZip(context)` → `cacheDir/logs/xbot-log-<时间>.zip`，内含：
  `device.txt`（版本/机型/Android/ABI/locale/服务器地址/当前会话，**不含账号密码**）、
  `session.txt`（内存日志快照）、`files/app*.log`（落盘日志）。
- `LogBundle.upload(context, api)` 走现有 `POST /api/files/upload`（与附件同一条通道，
  10MB 限制对几百 KB 的包够用），返回 `upload_key`，再用
  `XbotApi.downloadUrl(key)` 拼出可直接打开的链接。
- 用户操作路径：**设置 → 日志 → 上传**，把状态行里的链接发过来即可。

## 排查姿势

1. 先看「设置 → 日志」最近几十行（`http` 会显示每个请求的状态码和耗时，
   `upload` 会显示失败原因和是否走了缓冲回退）；
2. 需要完整上下文再「打包 / 上传」，比让对方描述现象快得多；
3. 崩溃查 `files/app.log` 末尾的 `E/crash`。
