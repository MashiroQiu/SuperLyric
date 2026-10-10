package com.hchen.superlyric.provider;

/** Match actual bean structure and cached song identity, independent of obfuscated names. */
final class QQCaptureSupport {
    private QQCaptureSupport() {}
    static boolean lyricConstructor(Class<?>[] types) {
        return types.length >= 4 && !types[0].isPrimitive() && !types[1].isPrimitive()
            && types[1] != String.class && types[1] == types[2] && types[2] == types[3];
    }
    static boolean sameSong(String id, String title, String artist,
                            String otherId, String otherTitle, String otherArtist) {
        if (id != null && !id.isEmpty() && id.equals(otherId)) return true;
        return title != null && !title.trim().isEmpty() && artist != null && !artist.trim().isEmpty()
            && otherTitle != null && otherArtist != null
            && title.trim().equalsIgnoreCase(otherTitle.trim())
            && artist.trim().equalsIgnoreCase(otherArtist.trim());
    }
}
