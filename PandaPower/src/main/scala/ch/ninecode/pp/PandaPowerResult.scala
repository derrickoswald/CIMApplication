package ch.ninecode.pp

case class PandaPowerResult (success: Boolean, errors: List[String])
{
    def combine (other: PandaPowerResult): PandaPowerResult =
        PandaPowerResult (success && other.success, if (!other.success) errors ::: other.errors else errors)
    override def toString: String = s"${if (success) "Succeeded" else "Failed"}${errors.mkString ("\n", "\n", "")}"
}
object PandaPowerResult
{
    def apply (): PandaPowerResult = PandaPowerResult (true, List())
}

