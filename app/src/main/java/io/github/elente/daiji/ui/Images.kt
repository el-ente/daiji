package io.github.elente.daiji.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import io.github.elente.daiji.data.AppEntry
import io.github.elente.daiji.data.AppsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Decodes artwork downsampled to the size it is shown at, and keeps it in a
 * memory-bounded cache. Covers use RGB_565 to halve their memory.
 */
object ImageCache {
    private val maxKb = (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()

    private val cache = object : LruCache<String, ImageBitmap>(maxKb) {
        override fun sizeOf(key: String, value: ImageBitmap): Int =
            (value.width * value.height * 4 / 1024).coerceAtLeast(1)
    }

    private val bannerKeys = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    fun peek(key: String): ImageBitmap? = cache.get(key)

    suspend fun loadFile(path: String, width: Int, height: Int): ImageBitmap? {
        val key = "$path@$width"
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            decodeFile(path, width, height)?.also { cache.put(key, it) }
        }
    }

    suspend fun loadAppArt(context: Context, app: AppEntry, width: Int, height: Int): Pair<ImageBitmap, Boolean>? {
        val key = "app:${app.packageName}@$width"
        cache.get(key)?.let { return it to (key in bannerKeys) }
        return withContext(Dispatchers.IO) {
            val (drawable, isBanner) = AppsRepository.loadArt(context, app)
            if (drawable == null) return@withContext null
            val (w, h) = if (isBanner) width to height else height to height
            val bitmap = runCatching { drawable.toBitmap(w, h).asImageBitmap() }.getOrNull()
                ?: return@withContext null
            cache.put(key, bitmap)
            if (isBanner) bannerKeys.add(key)
            bitmap to isBanner
        }
    }

    private fun decodeFile(path: String, width: Int, height: Int): ImageBitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            null
        } else {
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= width && bounds.outHeight / (sample * 2) >= height) {
                sample *= 2
            }
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeFile(path, options)?.asImageBitmap()
        }
    } catch (t: Throwable) {
        null
    }
}

@Composable
fun rememberFileImage(path: String?, width: Int, height: Int): ImageBitmap? {
    val state = produceState(
        initialValue = path?.let { ImageCache.peek("$it@$width") },
        path,
        width,
    ) {
        value = if (path == null) null else ImageCache.loadFile(path, width, height)
    }
    return state.value
}

@Composable
fun rememberAppArt(app: AppEntry, width: Int, height: Int): Pair<ImageBitmap, Boolean>? {
    val context = LocalContext.current
    val state = produceState<Pair<ImageBitmap, Boolean>?>(initialValue = null, app.packageName) {
        value = ImageCache.loadAppArt(context.applicationContext, app, width, height)
    }
    return state.value
}
