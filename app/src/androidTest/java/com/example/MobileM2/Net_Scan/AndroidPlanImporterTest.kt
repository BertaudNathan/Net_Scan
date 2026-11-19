package com.example.MobileM2.Net_Scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.MobileM2.Net_Scan.data.plan.AndroidPlanImporter
import com.example.MobileM2.Net_Scan.domain.PlanRules
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/** Import réel d'un plan : image réduite, PDF rendu, fichier invalide refusé. */
@RunWith(AndroidJUnit4::class)
class AndroidPlanImporterTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val importer = AndroidPlanImporter(context)
    private val visitId = 9_999L
    private val sources = mutableListOf<File>()

    @After fun tearDown() {
        sources.forEach(File::delete)
        File(context.filesDir, "plans").listFiles { file -> file.name.startsWith("visit_${visitId}_") }?.forEach { it.delete() }
    }

    @Test fun aLargeImageIsReducedAndCopied() = runBlocking {
        val source = file("plan.png")
        Bitmap.createBitmap(3000, 1500, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
            .also { bitmap -> source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }

        val imported = importer.import(visitId, Uri.fromFile(source).toString())
        assertEquals(PlanRules.MAX_IMAGE_SIDE, imported.widthPx)
        assertEquals(PlanRules.MAX_IMAGE_SIDE / 2, imported.heightPx)
        val copy = File(imported.imagePath)
        assertTrue(copy.path.startsWith(context.filesDir.path))
        assertEquals(imported.widthPx, BitmapFactory.decodeFile(copy.path).width)
    }

    @Test fun aPdfIsRenderedFromItsFirstPage() = runBlocking {
        val source = file("plan.pdf")
        PdfDocument().apply {
            val page = startPage(PdfDocument.PageInfo.Builder(842, 595, 1).create())
            page.canvas.drawRect(40f, 40f, 800f, 555f, Paint().apply { style = Paint.Style.STROKE; strokeWidth = 4f })
            finishPage(page)
            source.outputStream().use(::writeTo)
            close()
        }

        val imported = importer.import(visitId, Uri.fromFile(source).toString())
        assertEquals(PlanRules.MAX_IMAGE_SIDE, imported.widthPx)
        assertEquals((595f * PlanRules.MAX_IMAGE_SIDE / 842f).toInt(), imported.heightPx, 1)
    }

    @Test fun aReimportReplacesThePreviousCopy() = runBlocking {
        val source = file("plan.png")
        Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
            .also { bitmap -> source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        val first = importer.import(visitId, Uri.fromFile(source).toString())
        Thread.sleep(5)
        val second = importer.import(visitId, Uri.fromFile(source).toString())
        assertTrue(File(second.imagePath).exists())
        assertTrue(!File(first.imagePath).exists())
    }

    @Test fun somethingElseIsRefused() = runBlocking {
        val source = file("notes.txt").apply { writeText("pas un plan") }
        try {
            importer.import(visitId, Uri.fromFile(source).toString())
            fail("A text file must not be imported as a plan")
        } catch (_: Exception) {
            // Attendu : le ViewModel affiche alors « import impossible ».
        }
    }

    private fun file(name: String) = File(context.cacheDir, "import-test-$name").also { sources += it }

    private fun assertEquals(expected: Int, actual: Int, tolerance: Int) =
        assertTrue("expected $expected ± $tolerance, got $actual", kotlin.math.abs(expected - actual) <= tolerance)
}
