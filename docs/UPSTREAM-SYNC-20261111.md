# Upstream snapshot — 2026-11-11

- Fork's tested feature line before this sync: 33c1cf2, including cross-player ownership and recursive system-session callback fixes, plus API 102 hot reload and Lyricon provider support.
- HChenX/SuperLyric master: 359238e, 140 commits not in that feature line.
- Exact upstream tree/history is mirrored to branch upstream-sync-20261111. This is upstream source, not a replacement hot-reload build.

Changes include a ground-up lyric kernel, module separation (lyric-kernel, lyric-hook, lyric-network, lyric-service, shared), per-provider capture rewrites including QQ, parser fixes (LRC offsets, companion channels and post-processing), network matching/source attribution and caching, sanitizer and registration race fixes, dashboard restructuring and many regression tests.

A normal merge was attempted locally and aborted cleanly: the old and new provider/service structures conflict extensively, including HookEntrance, SuperLyricProxy, service/API UI, dependencies and parser/provider classes. No conflicted or partially merged tree is published to api102-hotreload. That branch retains our changes and this report.

The 140 upstream commits have NOT been integrated into api102-hotreload. Porting is still needed for the neutral Binder service endpoint, generation state serialization, provider/worker shutdown, SDK callback retirement and the Lyricon bridge. Simply resolving conflicts by keeping old files would leave two active implementations or omit the new invariants; simply taking upstream would lose the hot-reload lifetime guarantees.

No merged APK is claimed or provided. Next integration needs to adapt the above lifecycle hooks to the new modules, run upstream and retained regression suites, then build and validate reload with unchanged SystemUI/system_server/player PIDs on a device.
