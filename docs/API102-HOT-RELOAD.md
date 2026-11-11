# API 102 generation reload on upstream 4.1

This fork merges HChenX/SuperLyric master 359238e and retains the Lyricon output bridge and API 102 lifecycle. The tested feature branch preceding the merge was 601c2d3. The two Binder facades now share the upstream LyricRuntime; the old parallel service/provider implementation is removed.

## Handover

Old-generation onHotReloading blocks further publication, closes provider acquisition and debug receivers, saves identity/documents as JSON, and retires the service. Legacy and v4 clients keep framework Binder endpoints. Delegate transactions drain before retirement and wait for replacement; failure opens an error gate rather than reporting successful reload.

Service snapshots contain framework Bundles, primitive values, serialized wire frames, platform PlaybackState and external/framework Binders. No kernel doc, provider, source callback or old module Stub crosses generations. Publisher PID ownership, generations, media facts, cached documents, playback anchors, legacy receivers and v4 listeners are restored. Legacy schema-1 SuperLyricData snapshots migrate at the API boundary.

The derived HookTool entrance retains service dispatch hooks until API 102 replaces their IDs. Host Application initialization includes named playback subprocesses. A guarded discovery transaction supplies the v4 endpoint to running processes whose AMS service cache predates v4; access remains checked by the facade. New-generation failure/retirement logs and framework status must be checked on a device.

Cleanup covers provider timeout/retry/fetch pools, cache writes, debounce handlers, session callbacks, tracker and broadcaster threads, callback death links, provider SDK jobs/boot receiver, SuperLyricApi background work, and module-owned library workers. Shared host library dispatchers are excluded. The pinned SDK fields used by reflection have contract tests and R8 rules. Frame/legacy SDK receiver carriers retain identity across screen registration and generation changes; released provider exports and unlinked death recipients are removed so switches do not retain old providers. In-flight owned Binder callbacks drain before delegate retirement.

## Lyricon output

SystemUI's main process consumes upstream 4.0 lyric/state data. Translation and phonetic text map to separate fields. One provider follows the arbitrated player package; immutable document identity avoids rebuilding Song on every state frame. Native media callbacks retain playback position/speed and seek behavior. Central boot recreates the provider and replays the document. Screen-off pauses display synchronization and detaches the receiver; screen-on fetches current state.

## Validation and installation

The workspace builds signed debug/release variants and runs all eight module suites. The published release test APK deliberately disables R8/resource shrinking via gradle/hotreload-test.gradle; production release defaults still enable optimization. The packaged DEX is checked for API102 callbacks, SDK reflection contracts and lifecycle methods. Device ART/Binder unloading and actual power consumption are not validated by these checks.

See [installation and power checks](POWER-AND-UPGRADE-20261111.md). The signing certificate changed because the old private key was unavailable; back up preferences before replacing the old packages. Initial activation of this pair requires one reboot. Test subsequent same-signed updates with unchanged SystemUI, system_server and music-process PIDs. Check lyrics, icons, translations, seeks, player switching, screen-off restoration and retired workers. Restart the affected process if the framework rejects reload or cleanup fails.
