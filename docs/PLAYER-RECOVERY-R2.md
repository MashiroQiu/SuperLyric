# Lyricon output recovery

The previous paired build disabled the provider SDK's automatic state replay. Provider registration is asynchronous: the bridge sent the song before the output connection existed, cached the song identity locally, and did not resend it when registration succeeded. A newly created provider after player switching could therefore send playback updates without ever sending its song.

The bridge now enables cached state replay on initial connection and reconnection. This applies both to interruption by a second player and to pausing the first player before opening another. Provider callbacks are checked against the current provider identity; callbacks from a retired output cannot reconnect or update its replacement.

The pinned provider SDK only attempts registration once after a timeout. The bridge now retries failed output registration with backoff, stopping the retry when connected, retired or screen-off. The input subscription also retries with capped backoff while interactive, including after the previous six-attempt limit would have expired. Unlock and service-ready events detach and rediscover the transport. A delayed initial snapshot cannot overwrite a newer live input event.

Central restart restores the cached song as well as sending it. Native MediaSession callbacks remain the playback clock. Translation and phonetic mapping, provider identity and API 102 reload behavior are preserved.

Validation covers cached SDK replay before connection, a second player's song after pausing the first, and the latest song after reconnection. These are JVM SDK behavior tests, not a device-level test of focus changes, startup or native Binder delivery. The full module suites and APK builds are run for the release candidate; device observations are still required.

Both R2 APKs use the same signing key as the preceding paired release and have larger version codes. They can cover that release without uninstalling. The still older signing certificate remains incompatible.

Device checks: reboot and play immediately; switch players while playing; pause the first and play the second; switch back; lock/unlock; update through LSPosed hot reload. Inspect logcat tags SuperLyric-Lyricon, CentralConnection, RegistrationDispatcher and SLDiag if output is still absent.
