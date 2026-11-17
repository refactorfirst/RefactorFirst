namespace Members;

/// A static constructor and an instance constructor: both are written "Cache()".
public class Cache
{
    private static int size;

    static Cache()
    {
        if (Environment.ProcessorCount > 1) { size = 2; }
    }

    public Cache() { }

    public int Size() => size;
}
