package ai.read4ai.excel.grid

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Provides an [ExecutorService] for parallel image processing. */
internal object ExecutorProvider {
    fun create(): ExecutorService = Executors.newCachedThreadPool()
}
