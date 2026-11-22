package app.galaxywifi

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build

data class NowPlayingInfo(
    val packageName: String,
    val title: String,
    val artist: String,
    val playing: Boolean,
    val icon: Bitmap?
)

object NowPlaying {
    fun read(context: Context): NowPlayingInfo? {
        val app = context.applicationContext
        val session = activeController(app) ?: return fallbackIfMusic(app)
        val pkg = session.packageName.orEmpty()
        if (pkg.isBlank()) return fallbackIfMusic(app)
        val meta = session.metadata
        val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?.takeIf { it.isNotBlank() }
            .orEmpty()
        val artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?.takeIf { it.isNotBlank() }
            ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty()
        val state = session.playbackState?.state
        val playing = state == PlaybackState.STATE_PLAYING ||
            state == PlaybackState.STATE_BUFFERING ||
            state == PlaybackState.STATE_FAST_FORWARDING ||
            state == PlaybackState.STATE_REWINDING
        val icon = appIconBitmap(app, pkg, 96)
        return NowPlayingInfo(pkg, title, artist, playing, icon)
    }

    fun togglePlayPause(context: Context): Boolean {
        val c = activeController(context.applicationContext) ?: return false
        return try {
            val state = c.playbackState?.state
            if (state == PlaybackState.STATE_PLAYING) {
                c.transportControls.pause()
            } else {
                c.transportControls.play()
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun openApp(context: Context, packageName: String): Boolean {
        val launch = WidgetAppIcons.launchIntent(context, packageName) ?: return false
        return try {
            val opts = android.app.ActivityOptions.makeBasic()
            if (Build.VERSION.SDK_INT >= 26) {
                opts.launchDisplayId = ScreenModes.COVER_DISPLAY
            }
            context.startActivity(launch, opts.toBundle())
            true
        } catch (_: Exception) {
            try {
                context.startActivity(launch)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun activeController(context: Context): MediaController? {
        if (!MediaNotifListener.isEnabled(context)) return null
        return try {
            val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val list = msm.getActiveSessions(MediaNotifListener.component(context))
            list.firstOrNull { ctrl ->
                val st = ctrl.playbackState?.state
                st == PlaybackState.STATE_PLAYING ||
                    st == PlaybackState.STATE_BUFFERING ||
                    st == PlaybackState.STATE_PAUSED ||
                    st == PlaybackState.STATE_FAST_FORWARDING
            } ?: list.firstOrNull()
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun fallbackIfMusic(context: Context): NowPlayingInfo? {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        @Suppress("DEPRECATION")
        if (!am.isMusicActive) return null
        return NowPlayingInfo(
            packageName = "",
            title = "",
            artist = "",
            playing = true,
            icon = null
        )
    }

    private fun appIconBitmap(context: Context, packageName: String, sizePx: Int): Bitmap? {
        return try {
            val d = context.packageManager.getApplicationIcon(packageName)
            drawableToBitmap(d, sizePx)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun drawableToBitmap(drawable: Drawable, size: Int): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return Bitmap.createScaledBitmap(drawable.bitmap, size, size, true)
        }
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        return bmp
    }
}
