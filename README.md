SuperLyric 4.2 上游同步版 R4（API 102 / 真正热重载 / Lyricon 对接）。

完整合入上游 4.2 正式版 9492d7，包含相对上次基线新增的 22 个提交。

采用上游实现：播放器取得活动发布权后才能改写共享播放时钟；酷狗歌词接口 HTTPS 修复；QQ 等播放器候选歌词回退；歌词解析修复；按需构造旧接口载荷、关闭日志时减少诊断构造、广播队列拥堵处理和统一配额管理。原广播器的重复配额/超时账本已移除，热重载接入上游新管理方式。

逐项核对后继续保留：API 4.0 旧歌词缓存回滚保护、旧 STOP/异步快照保护、MediaSession.Token 回调管理及正在播放会话优先、已取消取词不得继续 HTTP 请求、Lyricon 启动/重连歌词重发、无订阅者时停止逐行调度、API 102 状态接力和工作线程清理。上游这些提交没有完整替代这些修复。

验证：九个模块 614 项测试通过；debug / 签名 release 构建、debug / release lint、APK 签名和最终 DEX API 102 契约检查通过。新增测试覆盖后台播放器迟到歌词不改写当前播放时钟，以及广播订阅者热重载后的 UID 配额、全量歌词能力和退出通知。release 测试包保持未混淆。

与 R3 使用相同签名，版本号更高，可覆盖安装。本次只更新 SuperLyric，Lyricon 继续使用现有 R3 包；超级岛边距、图标、翻译、动画设置保持不变。初次从非热重载版本迁移仍需重启；R3 的后续覆盖更新可测试 LSPosed 热重载。

没有连接手机，未实测 CPU、耗电或设备热重载。4.2 的发送端优化不能证明 Lyricon RenderThread 高占用已经解决。重点复测酷狗概念版长时间播放、自动/手动切歌、换播放器、开机播放及锁屏恢复。

- [R4 APK 直链](https://github.com/MashiroQiu/SuperLyric/raw/refs/heads/apk-20261111-paired/SuperLyric-4.2-api102-r4-release.apk)
- [合并与补丁取舍记录](https://github.com/MashiroQiu/SuperLyric/blob/integrate/upstream-hot-reload-20261111/docs/UPSTREAM-4.2-AUDIT.md)
- [配套 Lyricon R3](https://github.com/MashiroQiu/lyricon/releases/tag/paired-api102-20261112-r3)

SHA256: `9e856a6ca8fc365d3f20c65c69dd9d43e60daef3bc85415782a508dd64bd7d88`
