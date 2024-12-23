package tv.cloudwalker.adtech.vastdata.validator

import timber.log.Timber
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VastErrorHandler @Inject constructor() {  // Remove parameters from primary constructor

    private val maxerrorLogSize: Int = 100  // Move as property
    private val shouldRetryOnError: (Throwable) -> Boolean = { error ->  // Move as property
        when (error) {
            is IOException,
            is SocketTimeoutException,
            is ConnectException -> true
            else -> false
        }
    }
    private val errorLog = ConcurrentLinkedQueue<ErrorEntry>()

    data class ErrorEntry(
        val error: Throwable,
        val timestamp: Long = System.currentTimeMillis(),
        val vastId: String? = null,
        val operation: String? = null
    )

    data class ErrorStats(
        val totalErrors: Int,
        val retryableErrors: Int,
        val nonRetryableErrors: Int,
        val lastError: ErrorEntry?
    )

    fun logError(
        error: Throwable,
        vastId: String? = null,
        operation: String? = null
    ) {
        val entry = ErrorEntry(error, System.currentTimeMillis(), vastId, operation)
        errorLog.offer(entry)

        // Trim Timber if it exceeds max size
        while (errorLog.size > maxerrorLogSize) {
            errorLog.poll()
        }

        Timber.e(TAG, "VAST Error - ID: $vastId, Operation: $operation", error)
    }

    fun shouldRetry(error: Throwable): Boolean {
        return shouldRetryOnError(error)
    }

    fun getErrorStats(): ErrorStats {
        return errorLog.toList().let { errors ->
            ErrorStats(
                totalErrors = errors.size,
                retryableErrors = errors.count { shouldRetryOnError(it.error) },
                nonRetryableErrors = errors.count { !shouldRetryOnError(it.error) },
                lastError = errors.maxByOrNull { it.timestamp }
            )
        }
    }

    fun getErrorsForVastId(vastId: String): List<ErrorEntry> {
        return errorLog.filter { it.vastId == vastId }
    }

    fun clearErrorLog() {
        errorLog.clear()
    }

    fun clearErrorsForVastId(vastId: String) {
        errorLog.removeIf { it.vastId == vastId }
    }

    fun clearErrorsOlderThan(timeMs: Long) {
        val cutoffTime = System.currentTimeMillis() - timeMs
        errorLog.removeIf { it.timestamp < cutoffTime }
    }

    companion object {
        private const val TAG = "VastErrorHandler"
    }
}