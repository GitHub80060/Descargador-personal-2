package com.personal.ytdescargador

import android.Manifest
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private data class Quality(val label: String, val maxHeight: Int?, val audioOnly: Boolean)

    private val qualities = listOf(
        Quality("Mejor calidad disponible (hasta 4K)", null, false),
        Quality("1080p (Full HD)", 1080, false),
        Quality("720p (HD)", 720, false),
        Quality("480p", 480, false),
        Quality("Solo audio (MP3)", null, true)
    )

    private lateinit var etLink: EditText
    private lateinit var btnPaste: Button
    private lateinit var btnDownload: Button
    private lateinit var btnCancel: Button
    private lateinit var spQuality: Spinner
    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatus: TextView

    @Volatile private var ready = false
    @Volatile private var downloading = false
    @Volatile private var currentProcessId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etLink = findViewById(R.id.etLink)
        btnPaste = findViewById(R.id.btnPaste)
        btnDownload = findViewById(R.id.btnDownload)
        btnCancel = findViewById(R.id.btnCancel)
        spQuality = findViewById(R.id.spQuality)
        progressBar = findViewById(R.id.progressBar)
        tvStatus = findViewById(R.id.tvStatus)

        spQuality.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            qualities.map { it.label }
        )

        btnPaste.setOnClickListener { pasteFromClipboard() }
        btnDownload.setOnClickListener { onDownloadClicked() }
        btnCancel.setOnClickListener {
            currentProcessId?.let { YoutubeDL.getInstance().destroyProcessById(it) }
        }

        handleSharedIntent(intent)

        // Android 9 o menor necesita permiso para guardar en carpetas públicas
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                100
            )
        }

        initEngine()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSharedIntent(intent)
    }

    private fun handleSharedIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            val link = extractUrl(text)
            if (link != null) etLink.setText(link)
        }
    }

    private fun extractUrl(text: String): String? =
        Regex("https?://\\S+").find(text)?.value

    private fun pasteFromClipboard() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = cm.primaryClip?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
        val link = extractUrl(text)
        if (link != null) etLink.setText(link) else setStatus("No hay ningún link en el portapapeles.")
    }

    /** Inicia yt-dlp y FFmpeg en segundo plano y luego intenta actualizar yt-dlp. */
    private fun initEngine() {
        Thread {
            try {
                YoutubeDL.getInstance().init(applicationContext)
                FFmpeg.getInstance().init(applicationContext)
                ready = true
                runOnUiThread {
                    setStatus(getString(R.string.status_ready))
                    btnDownload.isEnabled = true
                }
            } catch (e: Exception) {
                runOnUiThread { setStatus("Error al iniciar: ${e.message}") }
                return@Thread
            }

            // YouTube cambia seguido: actualizamos yt-dlp para que no deje de funcionar
            try {
                YoutubeDL.getInstance().updateYoutubeDL(
                    applicationContext,
                    YoutubeDL.UpdateChannel.STABLE
                )
            } catch (_: Exception) {
                // Sin internet o sin cambios: se ignora
            }
        }.start()
    }

    private fun onDownloadClicked() {
        if (!ready || downloading) return
        val link = extractUrl(etLink.text.toString().trim())
        if (link == null) {
            setStatus("Pega un link válido que empiece por https://")
            return
        }
        startDownload(link, qualities[spQuality.selectedItemPosition])
    }

    private fun startDownload(url: String, quality: Quality) {
        downloading = true
        setBusy(true)
        setStatus("Conectando…")
        val processId = UUID.randomUUID().toString()
        currentProcessId = processId

        Thread {
            val tmpDir = File(cacheDir, "dl_${System.currentTimeMillis()}").apply { mkdirs() }
            try {
                val request = YoutubeDLRequest(url)
                request.addOption("--no-playlist")
                request.addOption("--no-mtime")
                request.addOption("-o", File(tmpDir, "%(title).120B.%(ext)s").absolutePath)

                if (quality.audioOnly) {
                    request.addOption("-x")
                    request.addOption("--audio-format", "mp3")
                    request.addOption("--audio-quality", "0")
                } else {
                    val filter = quality.maxHeight?.let { "[height<=$it]" } ?: ""
                    request.addOption("-f", "bv*$filter+ba/b$filter/b")
                    // Mayor resolución primero; a igual resolución, prefiere H.264/AAC (compatible con todo)
                    request.addOption("-S", "res,vcodec:h264,acodec:m4a")
                    request.addOption("--merge-output-format", "mp4")
                }

                YoutubeDL.getInstance().execute(request, processId) { progress, eta, _ ->
                    runOnUiThread {
                        if (progress >= 0) {
                            progressBar.isIndeterminate = false
                            progressBar.progress = progress.toInt()
                            setStatus("Descargando… ${progress.toInt()}%  (faltan ~${eta}s)")
                        }
                    }
                }

                runOnUiThread {
                    progressBar.isIndeterminate = true
                    setStatus("Guardando en el teléfono…")
                }

                val outputs = tmpDir.listFiles { f ->
                    f.isFile && f.extension.lowercase() in setOf("mp4", "mkv", "webm", "mp3", "m4a", "opus")
                }.orEmpty()

                if (outputs.isEmpty()) {
                    runOnUiThread { setStatus("No se generó ningún archivo. Intenta de nuevo.") }
                } else {
                    val saved = outputs.map { saveToPublicStorage(it) }
                    runOnUiThread {
                        progressBar.isIndeterminate = false
                        progressBar.progress = 100
                        setStatus("¡Listo! Guardado en:\n${saved.first()}")
                    }
                }
            } catch (e: YoutubeDL.CanceledException) {
                runOnUiThread { setStatus("Descarga cancelada.") }
            } catch (e: Exception) {
                val msg = e.message?.lineSequence()?.lastOrNull { it.isNotBlank() } ?: "error desconocido"
                runOnUiThread { setStatus("Error: $msg") }
            } finally {
                tmpDir.deleteRecursively()
                downloading = false
                currentProcessId = null
                runOnUiThread { setBusy(false) }
            }
        }.start()
    }

    /** Copia el archivo a Películas/YTDescargador o Música/YTDescargador. Devuelve la ruta legible. */
    private fun saveToPublicStorage(file: File): String {
        val ext = file.extension.lowercase()
        val isAudio = ext in setOf("mp3", "m4a", "opus")
        val mime = when (ext) {
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            else -> "audio/ogg"
        }
        val baseDir = if (isAudio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES
        val relative = "$baseDir/YTDescargador"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relative)
            }
            val collection = if (isAudio)
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

            val uri = contentResolver.insert(collection, values)
                ?: throw IllegalStateException("No se pudo crear el archivo en el almacenamiento")
            contentResolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { it.copyTo(out) }
            } ?: throw IllegalStateException("No se pudo escribir el archivo")
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(baseDir), "YTDescargador")
            dir.mkdirs()
            file.copyTo(File(dir, file.name), overwrite = true)
        }
        return "$relative/${file.name}"
    }

    private fun setBusy(busy: Boolean) {
        btnDownload.isEnabled = !busy && ready
        btnPaste.isEnabled = !busy
        spQuality.isEnabled = !busy
        btnCancel.visibility = if (busy) View.VISIBLE else View.GONE
        progressBar.visibility = if (busy) View.VISIBLE else progressBar.visibility
        if (busy) {
            progressBar.isIndeterminate = true
            progressBar.progress = 0
        }
    }

    private fun setStatus(text: String) {
        tvStatus.text = text
    }
}
