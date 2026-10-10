package com.hchen.superlyric.provider;
import org.junit.Test;
import static org.junit.Assert.*;
public class QQCaptureSupportTest {
    static class Song {}
    static class Lyric {}
    @Test public void matchesVariableAndDefaultBeanConstructors() {
        assertTrue(QQCaptureSupport.lyricConstructor(new Class<?>[]{Song.class, Lyric.class, Lyric.class, Lyric.class, int.class}));
        assertTrue(QQCaptureSupport.lyricConstructor(new Class<?>[]{Song.class, Lyric.class, Lyric.class, Lyric.class, int.class, Object.class}));
    }
    @Test public void rejectsUnrelatedAndShortConstructors() {
        assertFalse(QQCaptureSupport.lyricConstructor(new Class<?>[]{Song.class, Lyric.class}));
        assertFalse(QQCaptureSupport.lyricConstructor(new Class<?>[]{Song.class, Lyric.class, String.class, Lyric.class}));
        assertFalse(QQCaptureSupport.lyricConstructor(new Class<?>[]{Song.class, int.class, int.class, int.class}));
    }
    @Test public void bindsEarlyBeanToLaterMediaSessionIdentity() {
        assertTrue(QQCaptureSupport.sameSong("qq-id", "Song", "Artist", "media-id", " Song ", "Artist"));
        assertFalse(QQCaptureSupport.sameSong("qq-id", "Song", "Artist", "other-id", "Other song", "Artist"));
        assertFalse(QQCaptureSupport.sameSong("qq-id", "Song", "Artist", "other-id", "Song", "Other artist"));
    }
    @Test public void missingNamesDoNotMatchUnrelatedIdentifiers() {
        assertFalse(QQCaptureSupport.sameSong("qq-id", "", "", "other-id", "", ""));
        assertTrue(QQCaptureSupport.sameSong("qq-id", "", "", "qq-id", "Song", "Artist"));
    }
}
