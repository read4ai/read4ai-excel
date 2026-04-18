package ai.read4ai.excel.grid

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Provides an [ExecutorService] for parallel image processing using virtual threads (Java 21+). */
internal object ExecutorProvider {
    fun create(): ExecutorService = Executors.newVirtualThreadPerTaskExecutor()
}
