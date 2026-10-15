package dev.chalicechore.channelsnap.ui.components

import android.content.Context
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Launcher icons straight from PackageManager. An image library has no fetcher for
 * `getApplicationIcon`, so a small LRU keyed by package name does the job without
 * an extra dependency and avoids re-rasterising while scrolling.
 */
private object AppIconCache {
    private const val ICON_PX = 128
    private val cache = LruCache<String, ImageBitmap>(200)

    fun peek(pkg: String): ImageBitmap? = cache.get(pkg)

    suspend fun load(context: Context, pkg: String): ImageBitmap? = cache.get(pkg)
        ?: withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.getApplicationIcon(pkg).toBitmap(ICON_PX, ICON_PX).asImageBitmap()
            }.getOrNull()
        }?.also { cache.put(pkg, it) }
}

/** App icon, or a lettered placeholder while loading or when the app isn't installed. */
@Composable
fun AppIcon(packageName: String, label: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val context = LocalContext.current
    var icon by remember(packageName) { mutableStateOf(AppIconCache.peek(packageName)) }
    LaunchedEffect(packageName) {
        if (icon == null) icon = AppIconCache.load(context, packageName)
    }

    val current = icon
    if (current != null) {
        Image(bitmap = current, contentDescription = null, modifier = modifier.size(size))
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}
