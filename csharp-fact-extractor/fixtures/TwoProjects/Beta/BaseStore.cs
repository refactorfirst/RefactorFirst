namespace Beta;

public abstract class BaseStore
{
    protected int Capacity;

    protected virtual string Describe() => "base";

    public virtual int Limit() => Capacity;

    protected void Reset()
    {
        Capacity = 0;
    }
}
