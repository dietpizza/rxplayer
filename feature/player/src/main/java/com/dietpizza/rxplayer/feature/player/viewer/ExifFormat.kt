package com.dietpizza.rxplayer.feature.player.viewer

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * EXIF → info-sheet rows (plan §4 #12), unit tested.
 *
 * Only the tags in [READ_TAGS] are ever read. That whitelist is the privacy guarantee: nothing
 * GPS-related (or any other location tag) is in it, so location can never reach the sheet.
 */
internal object ExifFormat {
    // Tag names as in the EXIF spec / android.media.ExifInterface.TAG_* (kept as plain strings so
    // this stays JVM-testable).
    const val MAKE = "Make"
    const val MODEL = "Model"
    const val LENS_MODEL = "LensModel"
    const val ISO = "PhotographicSensitivity"
    const val ISO_LEGACY = "ISOSpeedRatings"
    const val F_NUMBER = "FNumber"
    const val EXPOSURE_TIME = "ExposureTime"
    const val FOCAL_LENGTH = "FocalLength"
    const val DATE_TIME_ORIGINAL = "DateTimeOriginal"
    const val DATE_TIME = "DateTime"

    /** Everything we ever ask the EXIF reader for. No location tags, on purpose. */
    val READ_TAGS: List<String> = listOf(
        MAKE, MODEL, LENS_MODEL, ISO, ISO_LEGACY, F_NUMBER, EXPOSURE_TIME, FOCAL_LENGTH,
        DATE_TIME_ORIGINAL, DATE_TIME,
    )

    private val EXIF_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.ROOT)

    /**
     * Camera rows from raw EXIF strings. [get] is only called with [READ_TAGS]; values may be
     * null/blank. Rational values arrive as decimals (ExifInterface.getAttribute does that).
     */
    fun rows(get: (String) -> String?, locale: Locale = Locale.getDefault()): List<Pair<String, String>> {
        val v = HashMap<String, String>()
        for (tag in READ_TAGS) get(tag)?.trim()?.takeIf { it.isNotEmpty() }?.let { v[tag] = it }
        val out = ArrayList<Pair<String, String>>()
        camera(v[MAKE], v[MODEL])?.let { out += "Camera" to it }
        v[LENS_MODEL]?.let { out += "Lens" to it }
        val exposure = listOfNotNull(
            v[F_NUMBER]?.toDoubleOrNull()?.let(::aperture),
            v[EXPOSURE_TIME]?.toDoubleOrNull()?.let(::shutter),
            (v[ISO] ?: v[ISO_LEGACY])?.let(::iso),
            v[FOCAL_LENGTH]?.toDoubleOrNull()?.let(::focalLength),
        )
        if (exposure.isNotEmpty()) out += "Exposure" to exposure.joinToString(" · ")
        (v[DATE_TIME_ORIGINAL] ?: v[DATE_TIME])?.let { date(it, locale) }?.let { out += "Taken" to it }
        return out
    }

    /** "Google Pixel 8" from make "Google" + model "Pixel 8"; no repeated make. */
    fun camera(make: String?, model: String?): String? {
        val m = make?.trim().orEmpty()
        val mo = model?.trim().orEmpty()
        return when {
            m.isEmpty() && mo.isEmpty() -> null
            m.isEmpty() -> mo
            mo.isEmpty() -> m
            mo.startsWith(m, ignoreCase = true) -> mo
            else -> "$m $mo"
        }
    }

    /** ƒ/1.8 */
    fun aperture(f: Double): String? {
        if (f <= 0.0 || f.isNaN()) return null
        return "ƒ/" + trim(f, 1)
    }

    /** 1/120 s, 0.5 s, 2 s */
    fun shutter(seconds: Double): String? {
        if (seconds <= 0.0 || seconds.isNaN()) return null
        if (seconds >= 1.0) return trim(seconds, 1) + " s"
        val denominator = 1.0 / seconds
        val rounded = denominator.roundToLong()
        // Exposures that aren't 1/n (e.g. 0.3 s) read better as decimals.
        return if (rounded >= 2 && abs(denominator - rounded) / denominator < 0.05) "1/$rounded s" else trim(seconds, 2) + " s"
    }

    /** ISO 100 (multi-valued tags keep the first). */
    fun iso(raw: String): String? {
        val first = raw.split(',', ' ').firstOrNull { it.isNotBlank() }?.trim() ?: return null
        val n = first.toDoubleOrNull()?.roundToInt() ?: return null
        return if (n > 0) "ISO $n" else null
    }

    /** 6.9 mm */
    fun focalLength(mm: Double): String? {
        if (mm <= 0.0 || mm.isNaN()) return null
        return trim(mm, 1) + " mm"
    }

    /** EXIF "2024:05:01 14:32:10" → localized medium date-time, or null if unparseable. */
    fun date(raw: String, locale: Locale = Locale.getDefault()): String? = runCatching {
        LocalDateTime.parse(raw.trim(), EXIF_DATE)
            .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(locale))
    }.getOrNull()

    /** 4032 × 3024 · 12.2 MP */
    fun resolution(width: Int, height: Int): String? {
        if (width <= 0 || height <= 0) return null
        val mp = width.toLong() * height / 1_000_000.0
        return if (mp >= 0.95) "$width × $height · ${trim(mp, 1)} MP" else "$width × $height"
    }

    /** 2.4 MB */
    fun size(bytes: Long): String? = when {
        bytes <= 0 -> null
        bytes >= 1L shl 30 -> String.format(Locale.ROOT, "%.2f GB", bytes / (1L shl 30).toDouble())
        bytes >= 1L shl 20 -> String.format(Locale.ROOT, "%.1f MB", bytes / (1L shl 20).toDouble())
        bytes >= 1024 -> "${bytes / 1024} KB"
        else -> "$bytes B"
    }

    private fun trim(value: Double, decimals: Int): String {
        val s = String.format(Locale.ROOT, "%.${decimals}f", value)
        return if ('.' in s) s.trimEnd('0').trimEnd('.') else s
    }
}
