R2 配套修复测试版。

SuperLyric 恢复异步连接后的歌曲/播放状态重放，补齐输出连接失败重试、开机输入订阅退避重试和解锁恢复，防止延迟快照覆盖较新的实时事件。覆盖“第二个播放器打断”和“先暂停第一个、再播放第二个”的连接时序。

Lyricon 完成逐字高亮、渐变、滚动等待、切句、翻译布局、图标/封面和控制面板动画代码审查。复用 Java 渐变对象和矩阵、移除重复重绘与父布局动画、清理过期切句及隐藏动画。保留原刷新率、逐字高亮、图标、超级岛/摄像头边距和原有设置。Android 渐隐边的离屏合成成本仍保留，以保持透明度与外观。

SuperLyric 477 项、Lyricon 125 项 JVM 测试通过，两边 debug/release 编译通过，release APK 签名与 API102 契约检查通过。测试不等于手机验证；尚无 CPU/耗电下降、开机切换恢复或热重载的设备实测结论。

这两个 APK 沿用上一套 paired-api102-20261111 的签名，可以覆盖该版本，无需卸载。发布为未混淆的 release 测试包。更早的旧签名包仍不能直接覆盖。

- [SuperLyric R2 APK](https://github.com/MashiroQiu/SuperLyric/raw/refs/heads/apk-20261111-paired/SuperLyric-4.1-recovery-r2-release.apk)
- [Lyricon R2 APK](https://github.com/MashiroQiu/lyricon/raw/refs/heads/apk-20261111-paired/Lyricon-1.0.40-render-r2-release.apk)
- [播放器连接修复说明](https://github.com/MashiroQiu/SuperLyric/blob/integrate/upstream-hot-reload-20261111/docs/PLAYER-RECOVERY-R2.md)
- [动画审查与保留成本](https://github.com/MashiroQiu/lyricon/blob/integrate/power-paired-20261111/docs/ANIMATION-AUDIT-R2.md)

请重点比较：开机后直接播放；播放中换播放器；暂停第一个后播放第二个；切回第一个；锁屏再解锁。CPU 对比请保持同一首歌、相同效果、刷新率和前台界面，分别记录稳定播放和暂停各 30 秒。若仍异常，可用仓库 tools/lyrics-power-trace.pbtxt 获取包含 Lyricon.wordDraw / Lyricon.scrollDraw 标记的 Perfetto 记录。
