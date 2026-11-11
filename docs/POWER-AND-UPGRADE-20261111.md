# Matched build: installation and power checks

## Installation

This pair uses the same new test signing certificate. The old signing private key was not retained across workspace replacement and cannot be recovered from an APK. These APKs cannot replace the old differently signed packages directly.

Before uninstalling anything, in the old Lyricon app open Settings, backup/export, and save lyricon_backup.bin outside the app. Its backup code removes stored AI API keys; re-enter these after restoration. Keep both old APKs for rollback. SuperLyric's app has no matching preference-export UI in the old build; record its switches, or use the root-only read/backup helper below.

The optional tools/backup-module-preferences.sh copies only known module preference directories to a private root-owned archive; it does not uninstall, restore, change signatures, or change scopes. Review paths.txt and copy preferences.tar to private storage before removal. Android/LSPosed layouts can differ. A missing directory is not a successful backup. An archive containing keys/settings must not be posted publicly. Root-file restoration requires matching the newly created directory ownership and SELinux labels; do not extract the archive over system paths blindly.

After backup, remove the old packages, install the new pair, enable them in LSPosed with their declared scopes, import the Lyricon backup and restore the recorded SuperLyric switches, and reboot once to remove the old injected generations. Subsequent same-certificate APK updates are the hot-reload test path. Save the new private signing keystore locally for future builds; it is excluded from Git and public releases.

## What changed for idle power

- Native lyrics, companion translation and phonetic text stay separate in the 4.0-to-Lyricon conversion. The bridge retains the last immutable lyric and only rebuilds the Song on a new document, not each STATE frame.
- Only the main SystemUI process starts the bridge. Its Binder registration runs on a background worker, with bounded retries instead of an endless service-absent loop.
- Screen-off detaches the bridge receiver and native session listener, and pauses its display provider. Screen-on re-registers and pulls the current snapshot, including track/player changes while the screen was off.
- The service keeps its framework playback clock, but suspends line dispatch when no receivers remain, including Binder death. Paused/idle trackers remove queued ticks.
- All owned acquisition pools, debounce handlers, cache writers, SDK jobs, death recipients and library workers have retirement paths for module replacement.

Lyricon is paired with a patch for queued-start/stop races, paused/hidden shared-memory readers, initial screen state, idle rendering, and immutable shader reuse. Native animation refresh rate and effect formulas are preserved; the earlier 30 FPS cap is not enabled by default.

## Phone evidence still required

No phone is attached to this workspace. Build and JVM tests are not proof of actual energy savings or successful ART/Binder hot reload. Screenshots alone cannot distinguish expensive effects from repeated rendering or other SystemUI plugins.

Compare the same song, lyric style, brightness, display refresh rate, network and screen on both builds. Wait for transitions to settle. Record at least 30 seconds each of playing/visible lyrics and paused/hidden lyrics; separately check screen-off music, screen-off pause, and no music app for sustained idle wakeups. Measure longer idle periods for battery consumption. Module APK UID usage alone misses injected code in SystemUI, system_server and the music process.

For a 30-second scheduling/render trace, copy tools/lyrics-power-trace.pbtxt to the phone and run as root:

    perfetto --txt -c /sdcard/Download/lyrics-power-trace.pbtxt -o /data/misc/perfetto-traces/lyrics-play.pftrace

Repeat with a different output name for pause or screen-off. Inspect SystemUI RenderThread/main thread and SuperLyric tracker/media threads. Kernel trace availability varies by ROM; check Perfetto errors instead of treating an empty trace as no CPU work. Process/scheduling traces contain app/process names; review before sharing.

Validate all original display behavior: island and camera margins, default hidden clock/notification area, player icons after switching, native/translated/phonetic text, seek, intro, interlude, next song, pause/resume, screen restoration, and full lyric retrieval in the SuperLyric app. For hot reload, compare SystemUI/system_server/player PIDs before and after a same-signed update; unchanged PIDs plus restored lyrics and retired old workers are the success criteria.
