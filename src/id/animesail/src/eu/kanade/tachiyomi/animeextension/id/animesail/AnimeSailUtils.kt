package eu.kanade.tachiyomi.animeextension.id.animesail

import android.util.Log

// ======================== Log Levels ========================

enum class LogLevel {
    INFO,
    ERROR,
    DEBUG,
    WARN,
}

// ======================= ReportLog ==========================

object ReportLog {

    private const val GLOBAL_TAG = "AnimeSail"

    fun log(tag: String, message: String, level: LogLevel) {
        val fullTag = "$GLOBAL_TAG-$tag"
        when (level) {
            LogLevel.INFO -> Log.i(fullTag, message)
            LogLevel.ERROR -> Log.e(fullTag, message)
            LogLevel.DEBUG -> Log.d(fullTag, message)
            LogLevel.WARN -> Log.w(fullTag, message)
        }
    }

    fun reportFeature(tag: String, message: String) {
        log(tag, message, LogLevel.INFO)
    }

    fun reportError(tag: String, errorMessage: String, exception: Exception? = null) {
        val fullMessage = if (exception != null) {
            "$errorMessage: ${exception.message}"
        } else {
            errorMessage
        }
        log(tag, fullMessage, LogLevel.ERROR)
        exception?.printStackTrace()
    }

    fun reportDebug(tag: String, message: String) {
        log(tag, message, LogLevel.DEBUG)
    }

    fun reportWarn(tag: String, message: String) {
        log(tag, message, LogLevel.WARN)
    }
}

// ==================== LogConfig ======================

object LogConfig {
    var isDebugEnabled = true

    fun setDebugMode(enabled: Boolean) {
        isDebugEnabled = enabled
    }
}

// ================= Extension Functions ================

fun logIfDebug(tag: String, message: String, level: LogLevel = LogLevel.DEBUG) {
    if (LogConfig.isDebugEnabled) {
        ReportLog.log(tag, message, level)
    }
}

// ================ Feature Tracker ===================

class FeatureTracker(private val featureName: String) {

    fun start() {
        ReportLog.reportFeature(featureName, "⏩ Started")
    }

    fun success(message: String = "Completed successfully") {
        ReportLog.reportFeature(featureName, "✅ $message")
    }

    fun error(errorMessage: String, exception: Exception? = null) {
        ReportLog.reportError(featureName, "❌ $errorMessage", exception)
    }

    fun warn(message: String) {
        ReportLog.reportWarn(featureName, "⚠️ $message")
    }

    fun debug(message: String) {
        ReportLog.reportDebug(featureName, "🔍 $message")
    }
}

// ============== Performance Tracker ===============

class PerformanceTracker(private val operationName: String) {
    private var startTime: Long = 0

    fun start() {
        startTime = System.currentTimeMillis()
        ReportLog.reportDebug("Performance", "⏱️ $operationName started")
    }

    fun end() {
        val duration = System.currentTimeMillis() - startTime
        ReportLog.reportDebug("Performance", "⏱️ $operationName completed in ${duration}ms")
    }
}

// ================= Helper Functions =================

/**
 * Fix URL to absolute URL
 */
fun fixUrl(url: String, baseUrl: String = "https://154.26.137.28"): String {
    return when {
        url.startsWith("http://") || url.startsWith("https://") -> url
        url.startsWith("//") -> "https:$url"
        url.startsWith("/") -> "$baseUrl$url"
        else -> "$baseUrl/$url"
    }
}

/**
 * Safe null URL fixer
 */
fun fixUrlNull(url: String?, baseUrl: String = "https://154.26.137.28"): String? {
    return if (url.isNullOrBlank()) null else fixUrl(url, baseUrl)
}

/**
 * Extract quality from filename
 */
fun extractQuality(filename: String): String {
    return Regex("""(\d{3,4})[pP]""")
        .find(filename)?.groupValues?.get(1)
        ?.let { "${it}p" }
        ?: "Unknown"
}
