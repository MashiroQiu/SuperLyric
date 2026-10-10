package com.hchen.superlyric.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class PlaybackOwnershipTest {
    private static final String QQ = "com.tencent.qqmusic";
    private static final String NETEASE = "com.netease.cloudmusic";

    @Test public void switchingWithoutPausingKeepsSystemSelectedPlayer() {
        assertTrue(PlaybackOwnership.mayActivate(NETEASE, QQ, NETEASE, true));
        assertFalse(PlaybackOwnership.mayActivate(NETEASE, NETEASE, QQ, true));
        assertFalse(PlaybackOwnership.mayPublish(NETEASE, NETEASE, QQ, true, true));
        assertTrue(PlaybackOwnership.mayPublish(NETEASE, NETEASE, NETEASE, true, true));
        assertTrue(PlaybackOwnership.mayActivate(QQ, NETEASE, QQ, true));
        assertFalse(PlaybackOwnership.mayPublish(QQ, QQ, NETEASE, true, true));
    }
    @Test public void lyricsAloneCannotReplaceAnotherPlayingPlayer() {
        assertFalse(PlaybackOwnership.mayPublish(null, NETEASE, QQ, true, true));
        assertFalse(PlaybackOwnership.mayPublish(null, NETEASE, QQ, false, false));
        assertFalse(PlaybackOwnership.mayPublish(null, null, QQ, false, false));
        assertTrue(PlaybackOwnership.mayPublish(null, NETEASE, QQ, false, true));
        assertTrue(PlaybackOwnership.mayPublish(null, null, QQ, false, null));
    }
    @Test public void repeatedPlayingSnapshotsDoNotTakeBackOwnership() {
        assertFalse(PlaybackOwnership.mayActivate(null, NETEASE, QQ, true));
        assertTrue(PlaybackOwnership.mayActivate(null, NETEASE, QQ, false));
    }
    @Test public void queuedPacketsExpireEvenAfterSwitchingBack() {
        assertFalse(PlaybackOwnership.mayDeliver(1, 2, QQ, NETEASE, true));
        assertFalse(PlaybackOwnership.mayDeliver(1, 3, QQ, QQ, true));
        assertFalse(PlaybackOwnership.mayDeliver(3, 3, NETEASE, QQ, true));
        assertFalse(PlaybackOwnership.mayDeliver(1, 3, QQ, QQ, false));
        assertTrue(PlaybackOwnership.mayDeliver(3, 3, QQ, QQ, true));
        assertTrue(PlaybackOwnership.mayDeliver(3, 3, NETEASE, QQ, false));
    }
}
