R3 配套修复测试版：历史歌曲歌词回滚与后台重复工作。

已用阻塞旧请求的并发测试复现 SuperLyricApi 4.0 的缓存回滚：新歌 FULL 已经到达后，旧的补拉请求仍覆盖缓存。原实现测试为 expected new but actual old，修复后通过。修复源码随 SuperLyric 仓库中的 superlyric-api 内部模块保留，API/Binder 协议保持不变。

SuperLyric：过期补拉与 latest 快照失效校验、按歌曲/发布者隔离缓存和 STOP、保留未变化的媒体会话监听、拦截已注销回调、优先使用同应用正在播放的会话；已取消取词不能继续发下一次 HTTP 请求。

Lyricon：更换来源时取消旧加工和缓存，最终主线程投递前再次检查代际，避免旧增强结果覆盖新歌；去重完整歌曲和重复加工，合并排队的进度刷新。原动画、刷新率、图标、翻译及超级岛/摄像头边距保留。

完整回归：SuperLyric 含内置 API 共 545 项，Lyricon 共 133 项测试通过；两边 debug/release 构建、签名及 API102 契约检查通过。相同签名，可覆盖 R2；release 测试包未混淆。

节电测试中的明确变化：100 次相同媒体会话列表不再重挂/重放；100 次重复歌曲输入只执行一次加工；同一缺失曲目的重复 STATE 共用一次补拉。未连接手机，CPU/真实耗电降幅及设备播放/热重载效果仍待实测。这些修复不能证明手机上所有错歌原因均已排除。

- [SuperLyric R3 APK](https://github.com/MashiroQiu/SuperLyric/raw/refs/heads/apk-20261111-paired/SuperLyric-4.1-stale-r3-release.apk)
- [Lyricon R3 APK](https://github.com/MashiroQiu/lyricon/raw/refs/heads/apk-20261111-paired/Lyricon-1.0.40-stale-r3-release.apk)
- [旧歌复现和耗电复查说明](https://github.com/MashiroQiu/SuperLyric/blob/integrate/upstream-hot-reload-20261111/docs/STALE-LYRICS-AND-POWER-R3.md)
- [Lyricon 加工链修复](https://github.com/MashiroQiu/lyricon/blob/integrate/power-paired-20261111/docs/STALE-SONG-PIPELINE-R3.md)

重点复测酷狗概念版连续播放、自动/手动切歌、换播放器和锁屏恢复。如果仍错歌，先记录正确歌名与错误歌名并保留发生前后的 LSPosed/logcat 日志，再切歌恢复。耗电比较请保持同一首歌、相同效果、刷新率和前台界面，分别记录稳定播放和暂停各 30 秒。
