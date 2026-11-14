# SuperLyric 4.2 integration and patch audit

Upstream release: HChenX/SuperLyric tag 4.2, commit 9492d71398467ffff67ed15883d2c14198d1e428.
Previous upstream merge parent: 359238ea0d1fe1142308497de94b97291ddbcc87.
The release adds 22 commits and changes 117 files relative to that parent. This merge includes the
complete release history. The application version is 4.2; the public lyric API remains 4.0.

## Decisions

| Reported issue / retained feature | What upstream 4.2 contains | Integration decision |
| :--- | :--- | :--- |
| Player switching freezes or misaligns lyrics | cd67e64 moves tracker writes behind successful active-publisher arbitration in both publishing paths | Use upstream arbitration and clock writes unchanged; no parallel implementation |
| Older song replaces a newer FULL document in both receiver UI and status bar | App still depends on released SuperLyricApi 4.0; asynchronous cache healing has no generation guard there | Retain vendored API 4.0 cache generations, publisher/track isolation, serialized delivery and stale STOP protection |
| Initial dashboard pull replaces a newer live event | ApiReceiverManager changes mock/reset locking only | Retain R3 live-revision check on the asynchronous pull |
| Repeated MediaController wrappers replay historical sessions | SystemPlayStateListener is unchanged from 359238e; it still rebuilds all callbacks keyed by MediaController | Retain R3 MediaSession.Token registration, stale callback rejection and playing-session priority |
| Startup / player switch loses Lyricon output | Upstream has no Lyricon bridge | Retain R2 cached provider replay, connection retries and retired-provider checks |
| Cancelled lyric fetch opens another HTTP request | FetchSlot is unchanged from 359238e | Retain request token checks and thread-local cleanup |
| Unnecessary publishing / logging / queue work | cd67e64 adds lazy legacy payloads, diagnostic gates, bounded enqueue and SubscriberBudget | Use upstream implementations; remove duplicated old broadcaster quota and timeout accounting |
| No receiver needs lyric line advancement | PlaybackTracker is unchanged from 359238e | Retain subscriber-interest gating and pending-loop cancellation; adapt death/eviction notifications to SubscriberBudget |
| Genuine API 102 reload | Dependencies use API 102, but official module.prop targets 101 and HookEntrance refuses reload | Retain API 102 metadata, stable Binder handover, state restoration and worker retirement |
| QQ last-line / failed lyric retrieval | The integrated architecture publishes full documents; 4.2 also tries remaining accepted QQ candidates after a failed download | Use upstream providers and network sources unchanged; do not restore pre-rewrite QQ timeline workarounds; device retrieval still needs observation |
| Original / translated / phonetic lyrics | API 4.0 fields remain separate; parser fixes improve format handling | Use upstream parsing; retain the Lyricon translation/phonetic mapping at the output boundary |
| Lyricon icons, island/camera bounds and animation cost | This release changes SuperLyric, not the Lyricon renderer | Keep paired Lyricon R3; no renderer changes or CPU reduction claims from this merge |

Two overlapping problems are not equivalent: publisher arbitration protects the shared playback
clock; cache generations protect asynchronous document replacement. Both are required.

The broadcaster merge keeps SubscriberBudget unchanged. Reload snapshots save subscriber UID and
legacy full-document capability, release the old budget, and restore registrations through the
upstream admission path. Death and eviction still notify the existing tracker interest gate.

Upstream removed the unused API-to-kernel converter. Legacy reload migration now reuses the
existing fromLegacyDoc conversion, exposed at the same adapter boundary, instead of restoring a
second conversion implementation. Upstream hook exception handling and release packaging changes
are retained. The release test APK continues to disable shrinking to preserve the existing
reload/reflection contract; it is not directly size-comparable to the upstream optimized APK.

## Verification

All nine module suites pass: 614 tests, zero failures, errors or skipped tests. Debug and signed
release builds, signature and packaged API 102 contract checks pass. BroadcasterReloadTest covers 3.x/v4 subscriber handover, UID quota preservation,
legacy capability preservation, and idle notification after removal. PublisherClockOwnershipTest
checks that an inactive publisher can cache a full document without writing the active clock.
The existing R2/R3 cache, receiver, fetch and session regression tests remain enabled.

Device checks still require long Kugou Concept playback, automatic/manual track changes, player
switching while playing and after pause, startup playback, lock/unlock, and LSPosed live reload.
No phone is connected to this workspace; unit tests do not measure CPU, battery use or ART reload.
