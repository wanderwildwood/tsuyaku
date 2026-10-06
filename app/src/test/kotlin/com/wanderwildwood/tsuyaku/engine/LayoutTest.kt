package com.wanderwildwood.tsuyaku.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LayoutTest {
    @get:Rule val tmp = TemporaryFolder()

    private val files = listOf(
        ModelFile("model", "models/es-en/x/model.esen.intgemm.alphas.bin.gz", 10, 30, null),
        ModelFile("vocab", "models/es-en/x/vocab.esen.spm.gz", 5, 8, null),
    )

    private fun put(root: File, f: ModelFile, bytes: Long) {
        val file = Layout.file(root, "es", f)
        file.parentFile!!.mkdirs()
        file.writeBytes(ByteArray(bytes.toInt()))
    }

    @Test fun aPackIsCompleteOnlyWhenEveryFileIsWhole() {
        val card = tmp.newFolder("card")
        val phone = tmp.newFolder("phone")
        put(card, files[0], 30)
        put(card, files[1], 7) // cut short
        assertNull(Layout.whereComplete(listOf(card, phone), "es", files))
        put(card, files[1], 8)
        assertEquals(card, Layout.whereComplete(listOf(card, phone), "es", files))
    }

    @Test fun aDownloadCarriesOnWhereItStarted() {
        val card = tmp.newFolder("card")
        val phone = tmp.newFolder("phone")
        put(phone, files[0], 30)
        // The card went in after the first file came; the rest still goes beside it.
        assertEquals(phone, Layout.resumeIn(listOf(card, phone), card, "es", files))
        assertEquals(listOf(files[1]), Layout.missing(phone, "es", files))
    }

    @Test fun aNewPackGoesHome() {
        val card = tmp.newFolder("card")
        val phone = tmp.newFolder("phone")
        assertEquals(card, Layout.resumeIn(listOf(card, phone), card, "es", files))
    }

    @Test fun theFileOnDiskDropsTheGzipEnding() =
        assertEquals("model.esen.intgemm.alphas.bin", files[0].name)
}
