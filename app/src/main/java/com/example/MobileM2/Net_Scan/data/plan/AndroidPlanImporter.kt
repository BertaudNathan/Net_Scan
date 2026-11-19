package com.example.MobileM2.Net_Scan.data.plan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import com.example.MobileM2.Net_Scan.domain.ImportedPlan
import com.example.MobileM2.Net_Scan.domain.PlanImporter
import com.example.MobileM2.Net_Scan.domain.PlanRules
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Copie un plan (image, ou première page d'un PDF) dans le stockage privé de l'application, réduit
 * à [PlanRules.MAX_IMAGE_SIDE] pixels de côté. Les plans de bâtiments clients sont sensibles : ils
 * ne quittent pas l'appareil et disparaissent avec les données de l'application.
 */
class AndroidPlanImporter(context: Context) : PlanImporter {
    private val appContext = context.applicationContext
    private val directory = File(appContext.filesDir, "plans")

    override suspend fun import(visitId: Long, uri: String): ImportedPlan = withContext(Dispatchers.IO) {
        val bitmap = if (isPdf(uri)) renderFirstPage(uri) else decodeImage(uri)
        try {
            directory.mkdirs()
            // Un nouveau nom à chaque import : l'image affichée ne peut pas rester en cache.
            val file = File(directory, "visit_${visitId}_${System.currentTimeMillis()}.png")
            file.outputStream().use { if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) throw IOException("PNG encoding failed") }
            directory.listFiles { other -> other.name.startsWith("visit_${visitId}_") && other != file }?.forEach { it.delete() }
            ImportedPlan(file.absolutePath, bitmap.width, bitmap.height)
        } finally {
            bitmap.recycle()
        }
    }

    private fun decodeImage(uri: String): Bitmap {
        val source = ImageDecoder.createSource(appContext.contentResolver, uri.toUri())
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val scale = scaleFor(info.size.width, info.size.height)
            if (scale < 1f) decoder.setTargetSize((info.size.width * scale).roundToInt(), (info.size.height * scale).roundToInt())
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }

    private fun renderFirstPage(uri: String): Bitmap {
        val descriptor = appContext.contentResolver.openFileDescriptor(uri.toUri(), "r") ?: throw IOException("Unreadable PDF")
        return descriptor.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                if (renderer.pageCount == 0) throw IOException("Empty PDF")
                renderer.openPage(0).use { page ->
                    // Un plan A3 imprimé en 72 points par pouce est petit : on le rend net à la taille maximale.
                    val scale = PlanRules.MAX_IMAGE_SIDE.toFloat() / max(page.width, page.height)
                    val bitmap = createBitmap((page.width * scale).roundToInt(), (page.height * scale).roundToInt())
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }
        }
    }

    /** Type annoncé par la source, sinon en-tête du fichier : toutes les sources n'indiquent pas le type. */
    private fun isPdf(uri: String): Boolean {
        val resolver = appContext.contentResolver
        if (resolver.getType(uri.toUri()) == PDF) return true
        val header = ByteArray(PDF_MAGIC.size)
        val read = resolver.openInputStream(uri.toUri())?.use { it.read(header) } ?: return false
        return read == header.size && header.contentEquals(PDF_MAGIC)
    }

    private fun scaleFor(width: Int, height: Int): Float = minOf(1f, PlanRules.MAX_IMAGE_SIDE.toFloat() / max(width, height))

    private companion object {
        const val PDF = "application/pdf"
        val PDF_MAGIC = "%PDF".toByteArray(Charsets.US_ASCII)
    }
}
