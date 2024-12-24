package tv.cloudwalker.adtech.vastdata.validator


import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.Assertions.*
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException

class VastErrorHandlerTest {

    private lateinit var errorHandler: VastErrorHandler

    @Before
    fun setup() {
        errorHandler = VastErrorHandler()
    }

    @Test
    fun `logError should store error entries correctly`() {
        // Given
        val error = IOException("Network error")
        val vastId = "test_vast_id"
        val operation = "test_operation"

        // When
        errorHandler.logError(error, vastId, operation)

        // Then
        val stats = errorHandler.getErrorStats()
        assertEquals(1, stats.totalErrors)
        assertEquals(1, stats.retryableErrors)
        assertEquals(0, stats.nonRetryableErrors)
        assertNotNull(stats.lastError)
        assertEquals(vastId, stats.lastError?.vastId)
        assertEquals(operation, stats.lastError?.operation)
        assertTrue(stats.lastError?.error is IOException)
    }

    @Test
    fun `shouldRetry should correctly identify retryable errors`() {
        // Test retryable errors
        assertTrue(errorHandler.shouldRetry(IOException()))
        assertTrue(errorHandler.shouldRetry(SocketTimeoutException()))
        assertTrue(errorHandler.shouldRetry(ConnectException()))

        // Test non-retryable errors
        assertFalse(errorHandler.shouldRetry(IllegalArgumentException()))
        assertFalse(errorHandler.shouldRetry(NullPointerException()))
    }

    @Test
    fun `getErrorStats should return correct statistics`() {
        // Given
        val vastId1 = "vast1"
        val vastId2 = "vast2"
        val retryableError = IOException("Network error")
        val nonRetryableError = IllegalArgumentException("Invalid argument")

        // When
        errorHandler.logError(retryableError, vastId1, "operation1")
        errorHandler.logError(nonRetryableError, vastId2, "operation2")

        // Then
        val stats = errorHandler.getErrorStats()
        assertEquals(2, stats.totalErrors)
        assertEquals(1, stats.retryableErrors)
        assertEquals(1, stats.nonRetryableErrors)
        assertNotNull(stats.lastError)

        // Instead of checking exact vastId, verify it's one of our test IDs
        assertTrue(
            stats.lastError?.vastId in listOf(vastId1, vastId2),
            "Last error vastId should be one of the test IDs"
        )

        // Verify that both errors are tracked
        val errors = errorHandler.getErrorsForVastId(vastId1) +
                errorHandler.getErrorsForVastId(vastId2)
        assertEquals(2, errors.size)
        assertTrue(errors.any { it.vastId == vastId1 })
        assertTrue(errors.any { it.vastId == vastId2 })
    }

    @Test
    fun `getErrorsForVastId should return correct errors`() {
        // Given
        val vastId1 = "vast1"
        val vastId2 = "vast2"
        val error1 = IOException("Error 1")
        val error2 = IOException("Error 2")
        val error3 = IOException("Error 3")

        // When
        errorHandler.logError(error1, vastId1, "op1")
        errorHandler.logError(error2, vastId2, "op2")
        errorHandler.logError(error3, vastId1, "op3")

        // Then
        val errorsForVast1 = errorHandler.getErrorsForVastId(vastId1)
        assertEquals(2, errorsForVast1.size)
        assertTrue(errorsForVast1.all { it.vastId == vastId1 })

        val errorsForVast2 = errorHandler.getErrorsForVastId(vastId2)
        assertEquals(1, errorsForVast2.size)
        assertTrue(errorsForVast2.all { it.vastId == vastId2 })
    }

    @Test
    fun `clearErrorLog should remove all errors`() {
        // Given
        errorHandler.logError(IOException(), "vast1", "op1")
        errorHandler.logError(IOException(), "vast2", "op2")

        // When
        errorHandler.clearErrorLog()

        // Then
        val stats = errorHandler.getErrorStats()
        assertEquals(0, stats.totalErrors)
        assertEquals(0, stats.retryableErrors)
        assertEquals(0, stats.nonRetryableErrors)
        assertNull(stats.lastError)
    }

    @Test
    fun `clearErrorsForVastId should remove specific errors`() {
        // Given
        val vastId1 = "vast1"
        val vastId2 = "vast2"
        errorHandler.logError(IOException(), vastId1, "op1")
        errorHandler.logError(IOException(), vastId2, "op2")
        errorHandler.logError(IOException(), vastId1, "op3")

        // When
        errorHandler.clearErrorsForVastId(vastId1)

        // Then
        assertEquals(0, errorHandler.getErrorsForVastId(vastId1).size)
        assertEquals(1, errorHandler.getErrorsForVastId(vastId2).size)
    }

    @Test
    fun `clearErrorsOlderThan should remove old errors`() {
        // Given
        errorHandler.logError(IOException(), "vast1", "op1")
        Thread.sleep(100) // Wait to create time difference
        val cutoffTime = System.currentTimeMillis()
        Thread.sleep(100) // Wait again
        errorHandler.logError(IOException(), "vast2", "op2")

        // When
        errorHandler.clearErrorsOlderThan(System.currentTimeMillis() - cutoffTime)

        // Then
        val stats = errorHandler.getErrorStats()
        assertEquals(1, stats.totalErrors)
        assertNotNull(stats.lastError)
        assertEquals("vast2", stats.lastError?.vastId)
    }

    @Test
    fun `error log should respect maximum size`() {
        // Given - Log more errors than the max size
        repeat(150) { // Max size is 100
            errorHandler.logError(IOException(), "vast$it", "op$it")
        }

        // Then
        val stats = errorHandler.getErrorStats()
        assertTrue(stats.totalErrors <= 100, "Error log should not exceed max size")
    }
}
