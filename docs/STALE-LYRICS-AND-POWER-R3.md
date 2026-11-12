# Stale lyrics and background-work audit

The device report describes historical lyrics appearing in both the SuperLyric receiver UI and Lyricon's status bar during Kugou Concept playback, with the next track change restoring the current song. This identifies an upstream delivery problem, not only an animation-clock drift.

## Reproduced cache failure

SuperLyricApi 4.0 starts an asynchronous latest-frame request when a STATE frame arrives without its matching document. The old request could return after a newer FULL frame and replace that newer document. The cache did not record which track generation the request belonged to.

The regression test blocks the old latest request, accepts the new FULL document, and then releases the old response. With the original cache algorithm the test fails with expected new but actual old. With the patched algorithm it passes. This is a deterministic JVM reproduction; identifying every cause on the reported phone still requires device logs.

The API source is retained in superlyric-api, with upstream revision and license in that directory. It is an internal dependency in this fork, not another application or repository. Public Binder transactions, models, API version and reload retirement fields remain unchanged.

Changes:

- Scope missing-document requests to their cache generation, publisher and track; invalidate on FULL, clear, unbind or a newer target.
- Coalesce repeated states for one missing track into one request and retain the newest position.
- Serialize receiver frame application and healed delivery. Reject a latest snapshot when live events arrived during its fetch.
- Ignore a STOP belonging to the previous track or publisher.
- Prevent the receiver dashboard's pending pull from replacing a newer live update.
- Retain media callbacks by MediaSession.Token, since Android can return new MediaController objects for the same session. Add/remove only changed tokens and reject callbacks from removed registrations.
- Prefer the playing session within a package over a paused historical session. A genuine new playback transition can select another session. Initial snapshots follow system priority.

## Power audit

No new periodic cache reset, media-session polling loop or playback timer is added.

The callback test sends 100 unchanged session lists and observes no new registration or snapshot delivery. The cache test sends 100 additional matching missing-track states and observes one in-flight latest request. Obsolete fetch tasks cannot open a subsequent HTTP call after cancellation, and the worker's token is removed when it finishes.

Lyricon's paired patch invalidates pending processing on source changes, checks generations at final main-thread delivery, and deduplicates identical raw and processed songs. The test sends 100 identical songs and observes one processing run. Queued position updates are coalesced, and logs avoid formatting entire songs and all word timings.

Existing remaining costs: native-vsync highlighting/scrolling, word effects when enabled, horizontal fading-edge compositing, and playback-position shared-memory polling while playing and interactive. Screen-off/paused polling gates remain in place. The fixes remove avoidable work; they are not measurements of battery savings.

## Device checks

Install the paired R3 builds over R2; signing keys are unchanged. Test a long Kugou Concept playlist, automatic and manual track changes, switching players, screen-off/on, and configuration reprocessing. If historical lyrics recur, collect logcat/LSPosed logs around the event and note both the real song and the incorrect song before changing tracks.

For CPU comparison keep the song, effects, refresh rate, screen and thermal state comparable. Capture playing and paused intervals for at least 30 seconds; tools/lyrics-power-trace.pbtxt includes SystemUI scheduling, CPU frequency/idle and lyric drawing trace sections. No phone was connected to the build workspace, so actual power reduction and native reload behavior remain unmeasured.
