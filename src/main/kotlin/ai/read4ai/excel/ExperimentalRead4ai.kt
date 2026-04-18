package ai.read4ai.excel

/**
 * Marker for APIs that are still experimental and may change without notice.
 *
 * Consumers must opt in explicitly via `@OptIn(ExperimentalRead4ai::class)` or propagate
 * the requirement with `@ExperimentalRead4ai` on the caller's signature.
 */
@RequiresOptIn(
    message = "This read4ai API is experimental and may change without notice.",
    level = RequiresOptIn.Level.WARNING,
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
)
annotation class ExperimentalRead4ai
