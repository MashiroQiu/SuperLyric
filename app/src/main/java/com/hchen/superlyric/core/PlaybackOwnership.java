package com.hchen.superlyric.core;

import java.util.Objects;

/** Lyrics update a session's cache; only playback priority may replace a playing owner. */
final class PlaybackOwnership {
    private PlaybackOwnership() {}

    static boolean mayPublish(String preferred, String active, String candidate,
                              boolean activePlaying, Boolean candidatePlaying) {
        if (preferred != null) return preferred.equals(candidate);
        if (Boolean.FALSE.equals(candidatePlaying)) return false;
        return active == null || Objects.equals(active, candidate) || !activePlaying;
    }

    static boolean mayActivate(String preferred, String active, String candidate, boolean wasPlaying) {
        if (preferred != null) return preferred.equals(candidate);
        return active == null || Objects.equals(active, candidate) || !wasPlaying;
    }

    static boolean mayDeliver(long queuedEpoch, long currentEpoch, String publisher, String active,
                              boolean activeOnly) {
        return queuedEpoch == currentEpoch && (!activeOnly || Objects.equals(publisher, active));
    }
}
