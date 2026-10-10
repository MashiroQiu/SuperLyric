# API 102 真正热重载（实验性测试版）

适用于支持 API 102 真正热重载的 LSPosed 2.2.1。通过框架的 `onHotReloading` / `onHotReloaded` 更换模块代码，不通过重启 SystemUI、杀音乐播放器或重启手机实现更新。

## 安装与验证

1. 从旧版本升级到首次支持热重载的这两个 APK 后，重启手机一次，让当前运行进程加载支持交接的代码。旧版本无法追溯补上热重载生命周期。
2. 保持原作用域启用，在网易云或 QQ 音乐播放一首歌。记录 `system_server`、`com.android.systemui` 和播放器 PID。
3. 覆盖安装支持热重载的后续 APK；也可重新安装本测试 APK，观察框架是否实际触发重载。查看 LSPosed 的热重载结果和模块日志中的 `Hot reload complete`。安装成功本身不证明热重载发生。
4. 比较更新前后的 PID；目标进程 PID 应保持不变，歌词、播放器图标和播放进度应恢复。依次单独重载 Lyricon、单独重载 SuperLyric，再测试连续更新。
5. 验证暂停/恢复、拖进度、QQ 与网易云互切、有岛/无岛边界、外语翻译，以及重载时打开歌词控制弹窗的情况。

ADB 可逐个记录进程，例如：

```sh
adb shell pidof system_server
adb shell pidof com.android.systemui
adb shell pidof com.tencent.qqmusic
adb shell pidof com.netease.cloudmusic
```

## 实现

- Lyricon 保存现有状态栏根视图、处理后的歌词、播放器信息、播放位置和超级岛区域；移除旧视图、监听器、广播接收器、共享内存映射、AI 任务与网络资源；新一代重新安装钩子并将新视图挂到原有状态栏。
- SuperLyric 保存发布者 PID、接收者的框架 Binder、各播放器完整歌词与框架播放状态。对外发布的服务 Binder 始终使用系统 `Binder`，模块 Stub 不直接导出；交接时清空旧委托，保留同一端点，让新版本接管客户端连接。
- 用 API 102 同 ID 钩子原子替换系统服务的转发钩子，避免默认 HookTool 先卸钩造成的空窗。保留修改的 HookTool 入口及其 LGPL 许可证。
- 新接收器重新收到完整歌曲快照；SuperLyric 在 Lyricon 中心启动后重新建立 Provider 并重放歌词缓存，避免把 Binder 存活误认为旧中心仍可用。
- 注销 SDK 常驻广播、取消并等待旧协程、结束歌词追踪和网络线程。只关闭本模块类加载器拥有的 OkHttp/Okio/协程调度器，不关闭宿主共享库的线程。
- 跨代快照使用框架对象、Java 集合和序列化数据，不传递旧模块的歌词模型或回调对象。

## 验证范围和限制

APK 构建、签名及 JVM 回归测试可在工作区验证；没有连接你的手机，因此未实机验证 HyperOS、system_server、ART/本地库及 Binder 的热重载行为。此版本是实验性测试包，不能把单元测试通过等同于手机实测成功。

旧代清理失败时会报告重载失败，日志提示重启相应进程恢复，不会假报成功。新作用域、入口结构或本地库 ABI 等结构变化不能承诺免重启。其他第三方 Provider 是否自动恢复取决于其 SDK 对中心重启的处理；本次补齐 SuperLyric 与 Lyricon 两端的重连。

此前的超级岛/摄像头限宽、默认隐藏时钟与通知图标、翻译选择、播放器图标切换和 QQ 时间轴修复均保留。

本次 SuperLyric 验证：16 项 JVM 测试通过，release 构建成功，签名校验通过。包含 7 项原有播放时间轴回归与 9 项重载交接/SDK 生命周期/Okio 退出测试。

## QQ 回归修复测试（2026101003）

用户提供的 LSPosed 2.2.1 日志显示 QQ 音乐 20.9.0.8 的主进程和 :QQPlayerService 均初始化 Provider，但播放子进程缺少 Application 初始化记录。入口原先将包名与完整进程名比较，现允许同包名的命名子进程。

QQ 原先只匹配 8 参数构造函数，现按前三个歌词参数的实际类型匹配，并对找不到类/构造函数记录明确警告。捕获的完整歌词缓存等待播放元数据；后续 metadata 到达或其 ID 格式变化时，以歌曲标题/歌手匹配缓存并生成绑定当前 ID 的新包。缓存通过 Parcel 字节保存，跨热重载传递不引用旧代模型。

应用 API 页面在已有增量数据但缺少完整歌词时仍拉取服务快照。QQ、网易云网络引擎、协调器的关键日志改为 XposedLog，以便在 LSPosed 导出的日志里检查；此前 AndroidLog 的这些错误不在本次导出日志中。因此不能仅凭没有报错就断言全量捕获或网络拉取正常。

本包为关闭混淆的签名 release 测试包，功能回归测试与最终 APK 验证通过；QQ 恢复和网易云应用内完整歌词展示仍需要手机验证。此前热重载和状态栏修改均保留。

关闭混淆的测试包可通过 `./gradlew :app:assembleRelease --init-script gradle/hotreload-test.gradle` 重建；常规 release 默认仍开启 R8。新增进程归属、构造形状和歌曲身份匹配测试，合计 22 项 JVM 测试通过。

MediaSession 元数据监听改为覆盖 `setMetadata` 的全部重载，避免只监听单参数转发方法而漏掉内部 setter 路径；嵌套同曲回调由协调器现有去重处理。
