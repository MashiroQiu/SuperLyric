# QQ music timeline / Lyricon bridge fixes (2026101001)

QQ's obfuscated lyric rows can contain a row index, duration, start and absolute end time. The former resolver picked the first monotonic numeric field from only 15 rows; row indices and constant positive durations also passed that test. They can collapse the timeline into milliseconds and cause playback to select its last line.

QQTimelineFields now checks the complete timeline, rejects counters and constant fields, distinguishes absolute end times from starts and durations, and refuses to cache an ambiguous partial timeline. Parsed lines are sorted before missing end times are filled.

Stopped playback and track changes discard the previous lyric array. A stopped same-ID track can resolve lyrics again, and QQ deduplication no longer rejects a recaptured full packet while the orchestrator is inactive. Playback interpolation uses PlaybackState's actual timestamp and does not advance paused states.

The Lyricon bridge retains a complete song through temporary NONE/STOPPED states and restores it on PLAYING. Cached songs are cleared for different tracks and publishers. Each MediaController callback verifies its bound controller before changing the current provider, so a delayed old-session callback cannot clear the new player's song.

Verification: 4 timeline-field regression tests and 3 playback-clock tests passed; release build completed. The bridge changes are included in the release compilation. The release is signed with the previous test APK's certificate for update compatibility. No connected QQ Music device was available; use cold launch, app switching, pause/resume and song changes to validate the runtime behavior.
