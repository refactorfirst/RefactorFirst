namespace Beta;

public class BetaStore : BaseStore
{
    public int Size { get; set; }

    public override int Limit() => Capacity + 1;

    public void Clear()
    {
        Reset();
        Size = 0;
    }
}
