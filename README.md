配套测试版：SuperLyric 4.1 上游新架构 + API102 热重载 + Lyricon 对接，以及 Lyricon 后台调度优化。

重要：使用新的测试签名，不能覆盖旧签名 APK。先在旧 Lyricon 设置中导出配置、保留旧 APK，再替换两模块并启用作用域，重启一次。后续使用相同新签名的覆盖更新测试免重启热重载。详见安装说明。

完整回归测试：SuperLyric 474 项、Lyricon 117 项通过；两边 debug/release 构建、签名与最终 DEX 契约检查通过。发布的 release 测试包未混淆，保留原刷新率和特效。没有连接手机，尚未实测耗电下降、显示一致性或 ART/Binder 热重载效果。

- [SuperLyric APK](https://github.com/MashiroQiu/SuperLyric/raw/refs/heads/apk-20261111-paired/SuperLyric-4.1-upstream-api102-release.apk)
- [Lyricon APK](https://github.com/MashiroQiu/lyricon/raw/refs/heads/apk-20261111-paired/Lyricon-1.0.40-power-api102-release.apk)
- [安装、配置备份与耗电检测说明](https://github.com/MashiroQiu/SuperLyric/blob/integrate/upstream-hot-reload-20261111/docs/POWER-AND-UPGRADE-20261111.md)
- [SuperLyric 源码](https://github.com/MashiroQiu/SuperLyric/tree/integrate/upstream-hot-reload-20261111)
- [Lyricon 源码](https://github.com/MashiroQiu/lyricon/tree/integrate/power-paired-20261111)

SuperLyric 上游 359238e 已作为合并父提交纳入，旧可用分支保留。迁移到两代稳定 Binder、序列化状态恢复、4.0 歌词/翻译/罗马音输出；清理退出的 SDK 回调、死亡监听和工作线程。Lyricon 保留超级岛/摄像头边距、隐藏规则、图标和翻译逻辑，停止暂停/熄屏空转并复用不可变着色器。

SHA256: `73bd748e09db018bbe3d9ac721eb175b0df921cee90747859cc212c35b15d327`
