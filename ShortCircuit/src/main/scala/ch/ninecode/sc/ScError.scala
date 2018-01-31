package ch.ninecode.sc

/**
 * Errors encountered in processing.
 *
 * @param fatal if <code>true</code> processing should stop, otherwise it can  proceed
 * @param message the error message
 */
case class ScError (
   fatal: Boolean,
   message: String)
{
    override def toString: String = (if (fatal) "FATAL: " else "NONFATAL: ") + message
}