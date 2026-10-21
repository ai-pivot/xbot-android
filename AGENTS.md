# AGENTS.md

xbot 服务端的**纯原生 Android 客户端**：Kotlin + Jetpack Compose（无 XML 布局），单模块 `app/`，
包名 `dev.xbot.android`。客户端只消费 xbot 的 REST + RPC + SSE 接口，没有本地业务逻辑的后端。

## 架构（详见 docs/agent/）

```
app/src/main/java/dev/xbot/android/
  data/       wire 模型、REST/RPC 客户端、cookie jar、SSE 客户端、AppPrefs
  chat/       ChatReducer（纯函数状态机）+ ChatViewModel
  sessions/   会话列表 ViewModel/Screen
  auth/       登录 ViewModel/Screen
  service/    前台 SSE 通知服务、开机自启、前台可见性
  ui/         Compose 导航、聊天/设置页、markdown/tool 组件、theme
```

核心是 `chat/ChatReducer.kt`：`(state, event) -> state` 的纯函数，一个 `turn_id` 一个 turn，
iteration 按序号合并，checkpoint 替换 / delta 追加，收到 turn 结束信号绝不清空已渲染内容。
**改消息流先改 reducer 并补 `app/src/test` 的单测**（纯 JVM，无 Robolectric）。

## 构建 / 测试

```bash
./gradlew :app:assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest    # 纯 JVM 单测
./gradlew :app:assembleRelease      # 需要 keystore.properties（见下）
```

需要 JDK 17+（实测 21）与 Android SDK `platforms;android-35` + `build-tools;35.0.0`，
用 gitignored 的 `local.properties` 指定 `sdk.dir`。

## GOTCHAS

- **本机默认没有可用的 Android SDK**：直接 `./gradlew` 会报 `SDK location not found`。仓库里只有
  `tools/sdk/cmdline-tools`（`tools/` 整体 gitignored），先装平台再写 `local.properties`：
  `tools/sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=$PWD/tools/sdk "platforms;android-35" "build-tools;35.0.0"`
- **`tools/` `dist/` `keystore.properties` `keystore/` `local.properties` 全部 gitignored**，不要在这些目录里找源码或提交它们。
- **Compose 里 `TopAppBar` 的 title 只拿「剩余宽度」**（先量 navigationIcon、再量 actions）。
  往 `actions` 里塞长文本会把标题挤成单字。详见 `docs/agent/ui.md`。
- **发版 APK 用 `keystore/xbot-release.jks` 签名**；同 versionCode 可覆盖安装，降级会被 Android 拒绝，
  所以更新清单里的 `versionCode` 必须**大于**已安装版本，否则 app 内检查更新不会提示。
- **`release.yml` 会把 `CHANGELOG.md` 全文当作更新说明**（文件不存在时退化成 `Release x.y.z`）；
  该文件按版本分段维护，改动时保持 `## [x.y.z]` 小节标题格式。
- **MinIO 用 `xbotlan` 别名**（`127.0.0.1:9000`）写 `uploads/share/android/`，公网前缀是
  `https://minio.cjiw.site/uploads/share/android/`；`local` 别名签名已失效，别用。

## 知识文件

- `docs/agent/ui.md` — Compose 界面约定、TopAppBar 布局陷阱、聊天页结构
- `docs/agent/ui-composer.md` — 输入区高度（为什么必须 `BasicTextField`）、上下文预算指示器的数据来源与三种口径

## 约定

- 状态机逻辑放 reducer（纯函数），副作用只在 ViewModel；UI 层不做数据推导。
- 文案用中文，代码标识符用英文；提交信息 `type(scope): 描述`（`feat`/`fix`/`ui`/`docs`/`ci`）。
- 硬截断文案（`take(n)`）一律改用 `maxLines` + `TextOverflow.Ellipsis`。
- 版本号只在 `version.properties`，发版前 `versionCode` 必须递增。
