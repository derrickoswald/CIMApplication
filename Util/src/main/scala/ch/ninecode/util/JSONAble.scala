package ch.ninecode.util

/**
 * JSON support.
 */
trait JSONAble[T]
{
    /**
     * Output equivalent JSON options.
     */
    def toJSON: String

    /**
     * Create one of these option objects from JSON.
     * @param text the JSON text
     * @return either an error message in Left or the options instance in Right
     */
    def fromJSON (text: String): Either[String, T]
}