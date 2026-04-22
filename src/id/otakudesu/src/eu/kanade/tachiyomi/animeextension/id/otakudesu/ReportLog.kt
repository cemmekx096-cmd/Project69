package eu.kanade.tachiyomi.animeextension.id.otakudesu

import android.util.Log

// Enum untuk level log
enum class LogLevel {
    INFO,
    ERROR,
    DEBUG,
    WARN,
}

// Kelas untuk menangani log aplikasi
object ReportLog {

    // Fungsi utama untuk mencatat log berdasarkan level
    fun log(tag: String, message: String, level: LogLevel) {
        when (level) {
            LogLevel.INFO -> Log.i(tag, message)
            LogLevel.ERROR -> Log.e(tag, message)
            LogLevel.DEBUG -> Log.d(tag, message)
            LogLevel.WARN -> Log.w(tag, message)
        }
    }

    fun reportFeature(tag: String, message: String) {
        log(tag, message, LogLevel.INFO)
    }

    fun reportError(tag: String, errorMessage: String) {
        log(tag, errorMessage, LogLevel.ERROR)
    }

    fun reportDebug(tag: String, message: String) {
        log(tag, message, LogLevel.DEBUG)
    }

    fun reportWarn(tag: String, message: String) {
        log(tag, message, LogLevel.WARN)
    }
}

object LogConfig {
    var isDebugEnabled = true

    fun setDebugMode(enabled: Boolean) {
        isDebugEnabled = enabled
    }
}

fun logIfDebug(tag: String, message: String, level: LogLevel = LogLevel.DEBUG) {
    if (LogConfig.isDebugEnabled) {
        ReportLog.log(tag, message, level)
    }
}

class FeatureTracker(private val featureName: String) {

    fun start() {
        ReportLog.reportFeature(featureName, "Feature started")
    }

    fun success(message: String = "Feature completed successfully") {
        ReportLog.reportFeature(featureName, message)
    }

    fun error(errorMessage: String) {
        ReportLog.reportError(featureName, "Error: $errorMessage")
    }

    fun warn(message: String) {
        ReportLog.reportWarn(featureName, "Warning: $message")
    }

    fun debug(message: String) {
        ReportLog.reportDebug(featureName, message)
    }
}

class PerformanceTracker(private val operationName: String) {
    private var startTime: Long = 0

    fun start() {
        startTime = System.currentTimeMillis()
        ReportLog.reportDebug("Performance-$operationName", "Started")
    }

    fun end() {
        val duration = System.currentTimeMillis() - startTime
        ReportLog.reportDebug("Performance-$operationName", "Completed in ${duration}ms")
    }
}
