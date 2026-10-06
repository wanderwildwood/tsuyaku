package com.wanderwildwood.tsuyaku.engine

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.GZIPOutputStream

class FetchTest {
    @get:Rule val tmp = TemporaryFolder()

    private val body = "Hola, ¿dónde está la estación? 🚉".toByteArray()
    private val gz = ByteArrayOutputStream().also { o -> GZIPOutputStream(o).use { it.write(body) } }.toByteArray()
    private val sha = MessageDigest.getInstance("SHA-256").digest(body).joinToString("") { "%02x".format(it) }

    @Test fun aWholeFileLandsUnderItsName() = runBlocking {
        val dest = tmp.root.resolve("es/vocab.spm")
        var counted = 0L
        Fetch.save(ByteArrayInputStream(gz), dest, body.size.toLong(), sha, gzipped = true) { counted = it }
        assertArrayEquals(body, dest.readBytes())
        assertEquals(gz.size.toLong(), counted)
        assertFalse(tmp.root.resolve("es/vocab.spm.part").exists())
    }

    @Test fun aWrongHashKeepsNothing() = runBlocking {
        val dest = tmp.root.resolve("model.bin")
        expect(Why.Damaged) { Fetch.save(ByteArrayInputStream(gz), dest, body.size.toLong(), "0".repeat(64), gzipped = true) {} }
        assertFalse(dest.exists())
        assertFalse(tmp.root.resolve("model.bin.part").exists())
    }

    @Test fun aShortFileKeepsNothing() = runBlocking {
        val dest = tmp.root.resolve("lex.bin")
        expect(Why.Damaged) { Fetch.save(ByteArrayInputStream(body), dest, body.size + 10L, null, gzipped = false) {} }
        assertFalse(dest.exists())
    }

    @Test fun aLongFileKeepsNothing() = runBlocking {
        val dest = tmp.root.resolve("lex.bin")
        expect(Why.Damaged) { Fetch.save(ByteArrayInputStream(body), dest, body.size - 1L, null, gzipped = false) {} }
        assertFalse(dest.exists())
    }

    @Test fun aCutConnectionKeepsNothing() = runBlocking {
        val dest = tmp.root.resolve("model.bin")
        val cut = gz.copyOf(gz.size / 2)
        expect(Why.Offline) { Fetch.save(ByteArrayInputStream(cut), dest, body.size.toLong(), sha, gzipped = true) {} }
        assertFalse(dest.exists())
        assertFalse(tmp.root.resolve("model.bin.part").exists())
    }

    private suspend fun expect(why: Why, block: suspend () -> Unit) {
        try {
            block()
            fail("expected $why")
        } catch (e: FetchFailed) {
            assertEquals(why, e.why)
        }
    }
}
