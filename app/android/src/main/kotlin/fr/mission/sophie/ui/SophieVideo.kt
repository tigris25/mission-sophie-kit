package fr.mission.sophie.ui

import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import fr.mission.sophie.data.MediaRepo

/**
 * Sophie's replaceable video ("sophie.mp4"), muted and looping while [speaking], paused and dimmed otherwise.
 * Returns false (nothing drawn) when there is no video, so callers keep their previous layout.
 */
@Composable
fun SophieVideo(media: MediaRepo, speaking: Boolean, modifier: Modifier = Modifier): Boolean {
    val src = remember { media.video("sophie") } ?: return false
    val player = remember { MediaPlayer() }
    var prepared by remember { mutableStateOf(false) }
    var ratio by remember { mutableFloatStateOf(2f / 3f) }

    DisposableEffect(Unit) { onDispose { player.release() } }
    LaunchedEffect(speaking, prepared) {
        if (!prepared) return@LaunchedEffect
        if (speaking) player.start() else if (player.isPlaying) player.pause()
    }
    val dim by animateFloatAsState(if (speaking) 0f else 0.45f, tween(400), label = "dim")
    val glow by animateFloatAsState(if (speaking) 1f else 0.25f, tween(400), label = "glow")
    val shape = RoundedCornerShape(20.dp)

    Box(modifier.aspectRatio(ratio).clip(shape).border(3.dp, Palette.sophie.copy(alpha = glow), shape)) {
        AndroidView(
            factory = { ctx ->
                TextureView(ctx).apply {
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                            try {
                                player.setSurface(Surface(st))
                                when (src) {
                                    is MediaRepo.Source.FileSrc -> player.setDataSource(src.file.absolutePath)
                                    is MediaRepo.Source.AssetSrc -> media.openAsset(src.path).use { player.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
                                }
                                player.isLooping = true
                                player.setVolume(0f, 0f)
                                player.setOnVideoSizeChangedListener { _, vw, vh -> if (vw > 0 && vh > 0) ratio = vw.toFloat() / vh }
                                player.setOnPreparedListener { it.seekTo(0); prepared = true }
                                player.prepareAsync()
                            } catch (e: Exception) {
                                // Unreadable video: the panel simply stays dark.
                            }
                        }
                        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean = true
                        override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
    }
    return true
}
