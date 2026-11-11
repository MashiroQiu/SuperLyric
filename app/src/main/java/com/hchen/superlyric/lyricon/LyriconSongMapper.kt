/*
 * This file is part of SuperLyric. Licensed under the GNU General Public License, version 3.
 * Copyright (C) 2026 HChenX
 */
package com.hchen.superlyric.lyricon

import com.hchen.superlyricapi.model.Lyric
import io.github.proify.lyricon.lyric.model.RichLyricLine
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.lyric.model.LyricWord

internal object LyriconSongMapper {
    fun song(source: Lyric): Song = Song(
        id = source.id, name = source.title, artist = source.artist,
        duration = source.duration.coerceAtLeast(0),
        lyrics = source.lines.map { line ->
            RichLyricLine(
                begin = line.start, end = line.end, text = line.text,
                translation = line.translation, secondary = line.phonetic,
                words = line.words?.map { LyricWord(begin = it.start, end = it.end, text = it.text) }
            )
        }
    )
}
