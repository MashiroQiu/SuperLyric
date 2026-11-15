# STOP invalidation and Kugou search fallback

## Device log evidence

The supplied LSPosed archive records Kugou Concept 5.3.21 (11621). At 22:51:46 Lyricon receives
Where To Start, at 22:51:47 the server clears the lyric, and at 22:51:48 Lyricon receives a playing
state without a new song. At 22:52:17 the SuperLyric dashboard successfully registers its v4
listener. The archive has no Android logcat stream containing host acquisition diagnostics.

The bridge only paused its provider on STOP and retained the old Song, lyric identity and native
MediaController callback. Native playback could reactivate that content after the server cleared
it. STOP now detaches the controller and clears both cached documents and the provider Song.
Native playback cannot update the output until a lyric document exists again. An unrelated
publisher's STOP does not clear the active song.

## Public API reproduction

The screenshot song DAMIDAMI has a 191-second Kugou candidate with hash
397ef7e45d2291913000a27e186af72d and artist Sihan、三Z-STUDIO、HOYO-MiX.
An actual query to mobileservice.kugou.com using the title and that complete artist string returns
five unrelated songs. A title-only query returns the exact 191-second song first, and the KRC
search endpoint reports matching lyric candidates for its hash.

Search now tries the title alone only when the combined query has no candidates passing the
existing identity score. Title, artist, version and duration scoring remain unchanged. A normal
combined-query hit adds no requests; a failed match adds at most one search request. Direct hash
retrieval and cancellation checks remain in place. The device's exact metadata and failed network
response are absent from the archive, so this reproduction does not prove every missing lyric
has this cause.

Track identity, publication outcomes and terminal fetch failures now use module logs at their
existing event boundaries. Full lyric documents are not logged. This makes the next LSPosed
archive useful if acquisition still fails, without a polling loop or per-frame diagnostic work.

## Validation

StoppedSongTest invokes the real bridge STOP listener with a mock main Handler and provider. It
checks document invalidation and that subsequent native playback cannot revive the output.
KuGouSearchFallbackTest uses intercepted HTTP responses for empty/unrelated combined results,
correct title fallback, preserved rejection of unrelated lyrics, and no extra request on success.
No phone is connected to the workspace; end-to-end device behavior still requires testing.

Validation result: 619 tests pass with zero failures, errors or skips. Debug and signed release
builds, both lint variants, signature and packaged API 102 contract checks pass.
