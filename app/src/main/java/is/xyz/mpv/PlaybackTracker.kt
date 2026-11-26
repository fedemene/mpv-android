package `is`.xyz.mpv

import android.content.Context
import android.content.SharedPreferences
import java.io.File

/**
 * Tracks per-file playback state (NEW, PLAYING, PLAYED) in SharedPreferences.
 */
object PlaybackTracker {
    private const val PREFS_NAME = "playback_tracker"
    private const val KEY_PREFIX = "status:"

    enum class PlaybackStatus(val value: Int) {
        NEW(0),
        PLAYING(1),
        PLAYED(2);

        companion object {
            fun fromInt(value: Int): PlaybackStatus = entries.firstOrNull { it.value == value } ?: NEW
        }
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun normalizeKey(rawKey: String?): String? {
        if (rawKey.isNullOrBlank()) return null
        if (rawKey.startsWith("memory://")) return null
        return if (!rawKey.contains("://")) {
            File(rawKey).absolutePath
        } else {
            rawKey
        }
    }

    @JvmStatic
    fun getStatus(context: Context, key: String?): PlaybackStatus {
        val normalized = normalizeKey(key) ?: return PlaybackStatus.NEW
        val stored = prefs(context).getInt(KEY_PREFIX + normalized, PlaybackStatus.NEW.value)
        return PlaybackStatus.fromInt(stored)
    }

    @JvmStatic
    fun setStatus(context: Context, key: String?, status: PlaybackStatus) {
        val normalized = normalizeKey(key) ?: return
        val editor = prefs(context).edit()
        if (status == PlaybackStatus.NEW) {
            editor.remove(KEY_PREFIX + normalized)
        } else {
            editor.putInt(KEY_PREFIX + normalized, status.value)
        }
        editor.apply()
    }

    @JvmStatic
    fun markPlaying(context: Context, vararg keys: String?) {
        updateKeys(context, PlaybackStatus.PLAYING, keys)
    }

    @JvmStatic
    fun markPlayed(context: Context, vararg keys: String?) {
        updateKeys(context, PlaybackStatus.PLAYED, keys)
    }

    private fun updateKeys(context: Context, status: PlaybackStatus, keys: Array<out String?>) {
        val normalizedKeys = keys.mapNotNull { normalizeKey(it) }.distinct()
        if (normalizedKeys.isEmpty()) return
        val editor = prefs(context).edit()
        for (k in normalizedKeys) {
            editor.putInt(KEY_PREFIX + k, status.value)
        }
        editor.apply()
    }
}
