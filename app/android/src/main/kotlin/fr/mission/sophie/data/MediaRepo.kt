package fr.mission.sophie.data

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File

/**
 * Finds replaceable media by base name ("call_telepathie", "sophie", "sfx_level_up"...).
 * Imported files (app private storage) win over files built into assets/audio and assets/images.
 */
class MediaRepo(private val context: Context, private val stores: Stores) {

    sealed interface Source {
        data class FileSrc(val file: File) : Source
        data class AssetSrc(val path: String) : Source
    }

    /** Key "<kind>:<base name>" (kind = audio, image or video), so sophie.png and sophie.mp4 coexist. */
    private var index: Map<String, Source> = emptyMap()

    private fun kindOf(fileName: String): String? = when (fileName.substringAfterLast('.', "").lowercase()) {
        in AUDIO_EXT -> "audio"
        in IMAGE_EXT -> "image"
        in VIDEO_EXT -> "video"
        else -> null
    }

    private fun find(kind: String, id: String): Source? = index["$kind:${id.lowercase()}"]
    private val durations = HashMap<String, Long?>()
    private val bitmaps = HashMap<String, Bitmap?>()

    init { rebuild() }

    @Synchronized
    fun rebuild() {
        val map = HashMap<String, Source>()
        for (folder in listOf("audio", "images", "video")) {
            context.assets.list(folder)?.forEach { name ->
                val base = baseName(name) ?: return@forEach
                map["${kindOf(name)}:$base"] = Source.AssetSrc("$folder/$name")
            }
        }
        stores.mediaDir.listFiles()?.forEach { f ->
            val base = baseName(f.name) ?: return@forEach
            map["${kindOf(f.name)}:$base"] = Source.FileSrc(f)
        }
        index = map
        durations.clear()
        bitmaps.clear()
    }

    private fun baseName(name: String): String? {
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext !in AUDIO_EXT && ext !in IMAGE_EXT && ext !in VIDEO_EXT) return null
        // Case-insensitive: "Sophie.png" and "sophie.png" are the same picture.
        return name.substringBeforeLast('.').lowercase()
    }

    fun audio(id: String): Source? = find("audio", id)
    fun hasAudio(id: String) = audio(id) != null

    private fun ext(s: Source) = when (s) {
        is Source.FileSrc -> s.file.extension.lowercase()
        is Source.AssetSrc -> s.path.substringAfterLast('.').lowercase()
    }

    fun openAsset(path: String): AssetFileDescriptor = context.assets.openFd(path)

    /** Duration in ms, cached; null when there is no file or it cannot be read. */
    @Synchronized
    fun audioDurationMs(id: String): Long? {
        if (durations.containsKey(id)) return durations[id]
        val src = audio(id)
        val d = src?.let { s ->
            val r = MediaMetadataRetriever()
            try {
                when (s) {
                    is Source.FileSrc -> r.setDataSource(s.file.absolutePath)
                    is Source.AssetSrc -> openAsset(s.path).use { fd -> r.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length) }
                }
                r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            } catch (e: Exception) {
                null
            } finally {
                r.release()
            }
        }
        durations[id] = d
        return d
    }

    /** Video (mp4/webm), e.g. "sophie" played while Sophie speaks. */
    fun video(id: String): Source? = find("video", id)

    fun hasImage(id: String): Boolean = find("image", id) != null

    /**
     * Animated GIF/WebP as a fresh drawable (Android 9+), or null when the picture is missing or not animated.
     * Each call returns a new instance: one per view.
     */
    fun animatedImage(id: String): android.graphics.drawable.Drawable? {
        if (android.os.Build.VERSION.SDK_INT < 28) return null
        val src = find("image", id)?.takeIf { ext(it) == "gif" || ext(it) == "webp" } ?: return null
        return try {
            val source = when (src) {
                is Source.FileSrc -> android.graphics.ImageDecoder.createSource(src.file)
                is Source.AssetSrc -> android.graphics.ImageDecoder.createSource(context.assets, src.path)
            }
            android.graphics.ImageDecoder.decodeDrawable(source)
                .takeIf { it is android.graphics.drawable.AnimatedImageDrawable }
        } catch (e: Exception) {
            null
        }
    }

    @Synchronized
    fun image(id: String): Bitmap? {
        if (bitmaps.containsKey(id)) return bitmaps[id]
        val src = find("image", id)
        val bmp = try {
            when (src) {
                is Source.FileSrc -> BitmapFactory.decodeFile(src.file.absolutePath)
                is Source.AssetSrc -> context.assets.open(src.path).use { BitmapFactory.decodeStream(it) }
                null -> null
            }
        } catch (e: Exception) {
            null
        }
        bitmaps[id] = bmp
        return bmp
    }

    /** Copies every audio/image file of a folder chosen by the user. Returns the number of files copied. */
    fun importFolder(tree: Uri): Int {
        val dir = DocumentFile.fromTreeUri(context, tree) ?: return 0
        var n = 0
        dir.listFiles().filter { it.isFile }.forEach { doc ->
            val name = doc.name ?: return@forEach
            if (baseName(name) == null) return@forEach
            // Remove any older file with the same base name but another extension.
            val base = name.substringBeforeLast('.')
            // Replace an older file of the same kind (e.g. sophie.jpg by sophie.png), keep the others (sophie.mp4).
            stores.mediaDir.listFiles()?.filter { it.nameWithoutExtension.equals(base, ignoreCase = true) && kindOf(it.name) == kindOf(name) }?.forEach { it.delete() }
            context.contentResolver.openInputStream(doc.uri)?.use { input ->
                File(stores.mediaDir, name).outputStream().use { input.copyTo(it) }
                n++
            }
        }
        rebuild()
        return n
    }

    fun clearImported() {
        stores.mediaDir.listFiles()?.forEach { it.delete() }
        rebuild()
    }

    fun importedCount(): Int = stores.mediaDir.listFiles()?.size ?: 0

    companion object {
        val AUDIO_EXT = setOf("mp3", "ogg", "m4a", "wav")
        val IMAGE_EXT = setOf("png", "jpg", "jpeg", "webp", "gif")
        val VIDEO_EXT = setOf("mp4", "webm", "3gp")
    }
}
