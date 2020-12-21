package ch.ninecode.util

trait Using
{
    def using[T <: AutoCloseable, R] (resource: T)(block: T => R): R =
    {
        try
        {
            block(resource)
        }
        finally
        {
            resource.close()
        }
    }
}