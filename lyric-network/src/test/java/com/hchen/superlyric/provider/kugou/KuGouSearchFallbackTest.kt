/*
 * This file is part of SuperLyric.
 *
 * SuperLyric is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (C) 2025-2026 HChenX
 */
package com.hchen.superlyric.provider.kugou

import com.hchen.superlyric.kernel.identity.TrackIdentity
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class KuGouSearchFallbackTest {
    private val identity = TrackIdentity("kugou", null, "DAMIDAMI", "Sihan、三Z-STUDIO、HOYO-MiX", 191000)
    private val hash = "397ef7e45d2291913000a27e186af72d"
    private val match = """{"data":{"info":[{"songname":"DAMIDAMI","singername":"Sihan、三Z-STUDIO、HOYO-MiX","duration":191,"hash":"397ef7e45d2291913000a27e186af72d"}]}}"""
    private fun source(queries: MutableList<String>, respond: (Int) -> String) = KuGouApiSource(
        OkHttpClient.Builder().addInterceptor { chain ->
            queries += chain.request().url.queryParameter("keyword").orEmpty()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(respond(queries.size).toResponseBody()).build()
        }.build(),
    )
    @Test fun `missing combined query falls back to title with identity scoring`() {
        val queries = mutableListOf<String>()
        val result = source(queries) { if (it == 1) """{"data":{"info":[]}}""" else match }.searchBestHashes(identity)
        assertEquals(listOf(hash), result)
        assertEquals(listOf("${identity.title} ${identity.artist}", identity.title), queries)
    }
    @Test fun `unrelated combined results also trigger the title fallback`() {
        val queries = mutableListOf<String>()
        val unrelated = match.replace("DAMIDAMI", "GAMBIT 险棋").replace("191", "224")
        assertEquals(listOf(hash), source(queries) { if (it == 1) unrelated else match }.searchBestHashes(identity))
        assertEquals(2, queries.size)
    }
    @Test fun `successful combined query makes only one request`() {
        val queries = mutableListOf<String>()
        assertEquals(listOf(hash), source(queries) { match }.searchBestHashes(identity))
        assertEquals(1, queries.size)
    }
    @Test fun `title fallback still rejects unrelated songs`() {
        val queries = mutableListOf<String>()
        val unrelated = match.replace("DAMIDAMI", "Where To Start").replace("191", "270")
        assertTrue(source(queries) { unrelated }.searchBestHashes(identity).isEmpty())
        assertEquals(2, queries.size)
    }
}
