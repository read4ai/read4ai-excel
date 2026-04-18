package ai.read4ai.excel

/**
 * Base exception for errors during Excel parsing.
 *
 * Use the sealed subclasses to distinguish error types:
 * - [EncryptedFile] -- the file is password-protected
 * - [InvalidFormat] -- the file is not a valid Excel format
 * - [TooLarge] -- the file exceeds the configured size limit
 */
sealed class ExcelParseException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {

    /** The Excel file is encrypted / password-protected and cannot be parsed. */
    class EncryptedFile(
        message: String = "The Excel file is encrypted and cannot be parsed",
        cause: Throwable? = null,
    ) : ExcelParseException(message, cause)

    /** The file is not a valid Excel format (corrupted or unsupported). */
    class InvalidFormat(
        message: String = "The file is not a valid Excel format",
        cause: Throwable? = null,
    ) : ExcelParseException(message, cause)

    /** The Excel file exceeds the maximum allowed size. */
    class TooLarge(
        message: String = "The Excel file exceeds the maximum allowed size",
        cause: Throwable? = null,
    ) : ExcelParseException(message, cause)
}
