package ch.ninecode.util

object Util
{
    /**
     * The list of classes that can be persisted in RDD.
     */
    lazy val classes: Array[Class[_]] =
    {
        Array [Class[_]](
            classOf [ch.ninecode.util.Complex],
            classOf [ch.ninecode.util.Sequences],
            classOf [ch.ninecode.util.ThreePhaseComplexDataElement]
        )
    }
}
