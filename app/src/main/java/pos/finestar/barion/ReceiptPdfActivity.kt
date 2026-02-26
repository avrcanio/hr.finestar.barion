package pos.finestar.barion

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class ReceiptPdfActivity : ComponentActivity() {
    private val httpClient = OkHttpClient()
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var pdfRenderer: PdfRenderer? = null

    private var isLoading by mutableStateOf(true)
    private var errorMessage by mutableStateOf<String?>(null)
    private var pageCount by mutableStateOf(0)
    private var currentPage by mutableStateOf(0)
    private var pageBitmap by mutableStateOf<Bitmap?>(null)
    private var normalizedUrl by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val rawUrl = intent.getStringExtra(EXTRA_PDF_URL).orEmpty()
        normalizedUrl = normalizePdfUrl(rawUrl)

        setContent {
            ReceiptPdfScreen(
                isLoading = isLoading,
                errorMessage = errorMessage,
                bitmap = pageBitmap,
                pageCount = pageCount,
                currentPage = currentPage,
                onRetry = { loadPdf() },
                onPreviousPage = { renderPage(currentPage - 1) },
                onNextPage = { renderPage(currentPage + 1) },
                onClose = { finish() }
            )
        }

        if (normalizedUrl.isBlank()) {
            errorMessage = "Nedostaje PDF link."
            isLoading = false
            return
        }
        loadPdf()
    }

    override fun onDestroy() {
        pageBitmap?.recycle()
        pageBitmap = null
        pdfRenderer?.close()
        pdfRenderer = null
        fileDescriptor?.close()
        fileDescriptor = null
        super.onDestroy()
    }

    private fun loadPdf() {
        lifecycleScope.launch {
            isLoading = true
            errorMessage = null
            val pdfFile = runCatching { withContext(Dispatchers.IO) { downloadPdf(normalizedUrl) } }.getOrElse { error ->
                isLoading = false
                errorMessage = error.message ?: "Ne mogu preuzeti PDF."
                return@launch
            }
            val openResult = runCatching { openPdf(pdfFile) }.getOrElse { error ->
                isLoading = false
                errorMessage = error.message ?: "Ne mogu otvoriti PDF."
                return@launch
            }
            pageCount = openResult
            if (pageCount < 1) {
                isLoading = false
                errorMessage = "PDF nema stranica."
                return@launch
            }
            renderPage(0)
        }
    }

    private fun openPdf(pdfFile: File): Int {
        pageBitmap?.recycle()
        pageBitmap = null
        pdfRenderer?.close()
        pdfRenderer = null
        fileDescriptor?.close()
        fileDescriptor = null

        fileDescriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
        pdfRenderer = PdfRenderer(fileDescriptor!!)
        return pdfRenderer?.pageCount ?: 0
    }

    private fun renderPage(index: Int) {
        val renderer = pdfRenderer ?: return
        if (index !in 0 until renderer.pageCount) return
        lifecycleScope.launch {
            isLoading = true
            errorMessage = null
            val bitmap = runCatching {
                withContext(Dispatchers.IO) {
                    renderer.openPage(index).use { page ->
                        val maxWidthPx = (resources.displayMetrics.widthPixels * 0.98f).toInt().coerceAtLeast(1)
                        val aspect = if (page.width > 0) page.height.toFloat() / page.width.toFloat() else 1f
                        val bitmapWidth = maxWidthPx
                        val bitmapHeight = (bitmapWidth * aspect).toInt().coerceAtLeast(1)
                        Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888).also { output ->
                            page.render(output, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    }
                }
            }.getOrElse { error ->
                isLoading = false
                errorMessage = error.message ?: "Ne mogu prikazati PDF stranicu."
                return@launch
            }
            pageBitmap?.recycle()
            pageBitmap = bitmap
            currentPage = index
            isLoading = false
        }
    }

    private fun downloadPdf(url: String): File {
        val cacheBustedUrl = addCacheBuster(url)
        val request = Request.Builder()
            .url(cacheBustedUrl)
            .header("Cache-Control", "no-cache")
            .header("Pragma", "no-cache")
            .build()
        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Preuzimanje nije uspjelo (${response.code}).")
            }
            val body = response.body ?: throw IllegalStateException("PDF odgovor je prazan.")
            val cacheFile = File(cacheDir, "receipt-${cacheBustedUrl.hashCode()}.pdf")
            cacheFile.outputStream().use { output ->
                body.byteStream().use { input -> input.copyTo(output) }
            }
            cacheFile
        }
    }

    private fun normalizePdfUrl(rawUrl: String): String {
        val raw = rawUrl.trim()
        if (raw.isBlank()) return ""
        if (raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true)) {
            return raw
        }
        val base = BuildConfig.BARION_API_BASE_URL.trimEnd('/')
        return if (raw.startsWith("/")) "$base$raw" else "$base/$raw"
    }

    private fun addCacheBuster(url: String): String {
        val parsed = url.toHttpUrlOrNull() ?: return url
        return parsed.newBuilder()
            .setQueryParameter("_cb", System.currentTimeMillis().toString())
            .build()
            .toString()
    }

    companion object {
        private const val EXTRA_PDF_URL = "pdf_url"

        fun createIntent(context: Context, pdfUrl: String): Intent {
            return Intent(context, ReceiptPdfActivity::class.java).putExtra(EXTRA_PDF_URL, pdfUrl)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReceiptPdfScreen(
    isLoading: Boolean,
    errorMessage: String?,
    bitmap: Bitmap?,
    pageCount: Int,
    currentPage: Int,
    onRetry: () -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onClose: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Račun PDF") },
                actions = {
                    TextButton(onClick = onClose) {
                        Text("Zatvori")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when {
                isLoading && bitmap == null -> CircularProgressIndicator()
                errorMessage != null -> {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Button(onClick = onRetry) {
                        Text("Pokušaj ponovo")
                    }
                }
                bitmap != null -> {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Receipt PDF",
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.FillWidth
                    )
                    if (pageCount > 1) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(onClick = onPreviousPage, enabled = currentPage > 0) {
                                Text("Prethodna")
                            }
                            Text("Stranica ${currentPage + 1}/$pageCount")
                            Button(onClick = onNextPage, enabled = currentPage < pageCount - 1) {
                                Text("Sljedeća")
                            }
                        }
                    }
                }
            }
        }
    }
}
