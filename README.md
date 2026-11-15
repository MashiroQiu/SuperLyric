SuperLyric 4.2 R5：修复停止后旧歌词重新出现，补充酷狗检索回退。

日志证据：22:51:46 转发了 Where To Start，22:51:47 服务清除歌词，22:51:48 状态栏再次收到播放状态；22:52:17 SuperLyric 应用的接收器注册成功。转发层之前只暂停显示，保留了旧歌和原生播放回调。

修复：收到当前来源 STOP 后，取消原生播放回调，清除缓存 Song、歌词文档和播放状态，并清空输出歌曲；新歌词到达前，原生播放状态不能重新激活旧歌。其它来源的 STOP 不清除当前歌词。

另经公开接口复现：DAMIDAMI + 完整歌手名 Sihan、三Z-STUDIO、HOYO-MiX 的检索返回无关歌曲；单歌名查询能找到正确的 191 秒版本，其哈希存在歌词候选。新增一次受限回退：组合查询没有合格结果时再搜歌名，仍保留歌手、版本和时长评分。原组合查询命中时没有额外请求。

原始日志没有记录播放器实际元数据和 HTTP 响应，所以尚不能认定本次取词缺失一定由组合查询导致。R5 将取词身份、发布结果和最终失败记入 LSPosed 日志；不记录歌词全文，也没有新增定时轮询。

验证：619 项测试通过；debug/release 构建、lint、签名与最终 APK API102 契约检查通过。使用和 R4 相同的签名，可覆盖更新；Lyricon 不需要更换。请复测截图歌曲、自动/手动切歌和播放器切换。未连接手机，仍需验证端到端取词及显示。

[下载 R5 APK](https://github.com/MashiroQiu/SuperLyric/raw/refs/heads/apk-20261111-paired/SuperLyric-4.2-api102-r5-release.apk)

[日志证据、修复与测试说明](https://github.com/MashiroQiu/SuperLyric/blob/integrate/upstream-hot-reload-20261111/docs/STOP-AND-KUGOU-R5.md)

SHA256: `a416a7d2361f9225e6166176678f7f9e05a0fe9fc9f11960f0a4c7bd0460718e`
