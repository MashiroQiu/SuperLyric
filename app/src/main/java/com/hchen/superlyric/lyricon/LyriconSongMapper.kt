/*
 * This file is part of SuperLyric.
 * Licensed under the GNU General Public License, version 3.
 * Copyright (C) 2026 HChenX
 */
package com.hchen.superlyric.lyricon

import com.hchen.superlyricapi.SuperLyricData
import com.hchen.superlyricapi.SuperLyricLine
import io.github.proify.lyricon.lyric.model.LyricWord
import io.github.proify.lyricon.lyric.model.RichLyricLine
import io.github.proify.lyricon.lyric.model.Song

internal object LyriconSongMapper {
    fun trackKey(data: SuperLyricData): String =
        data.lyricId?.takeIf { it.isNotBlank() }
            ?: "${data.title.orEmpty()}\u0000${data.artist.orEmpty()}\u0000${data.album.orEmpty()}"

    fun song(data: SuperLyricData): Song = Song(
        id = trackKey(data),
        name = data.title,
        artist = data.artist,
        duration = data.duration.coerceAtLeast(0),
        lyrics = data.allLyrics?.mapNotNull(::line)
    )

    fun line(source: SuperLyricLine): RichLyricLine? {
        val begin = source.startTime.coerceAtLeast(0)
        val end = source.endTime
        if (end <= begin || source.text.isBlank()) return null
        return RichLyricLine(
            begin = begin,
            end = end,
            text = source.text,
            translation = source.translation,
            secondary = source.secondary,
            words = source.words?.mapNotNull { word ->
                // SuperLyric uses absolute song timestamps, as does Lyricon.
                val wordBegin = word.startTime.coerceIn(begin, end)
                val wordEnd = word.endTime.coerceIn(begin, end)
                if (wordEnd <= wordBegin) null else LyricWord(
                    begin = wordBegin,
                    end = wordEnd,
                    text = word.word
                )
            }?.takeIf { it.isNotEmpty() }
        )
    }

    fun text(data: SuperLyricData): String? = data.currentLyric?.let { line ->
        listOfNotNull(
            line.text.takeIf { it.isNotBlank() },
            (line.translation ?: data.translation?.text)?.takeIf { it.isNotBlank() },
            (line.secondary ?: data.secondary?.text)?.takeIf { it.isNotBlank() }
        ).distinct().joinToString("\n").takeIf { it.isNotEmpty() }
    }
}
