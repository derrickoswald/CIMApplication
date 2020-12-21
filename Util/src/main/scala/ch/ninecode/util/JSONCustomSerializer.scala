package ch.ninecode.util

import org.json4s.CustomSerializer
import org.json4s.Formats
import org.json4s.JValue

/**
 * Add error reporting to partial function evaluation.
 *
 * Implemented as kind of a kludge via object properties.
 *
 * @param ser partial functions to take strings to T and vice versa
 * @param m required manifest for Scala type magic
 * @tparam T the type being serialized
 */
class JSONCustomSerializer[T](ser: Formats => (PartialFunction[JValue, T], PartialFunction[Any, JValue]))(implicit m: Manifest[T])
extends CustomSerializer[T](ser)
{
    def errors: String = ""
}
