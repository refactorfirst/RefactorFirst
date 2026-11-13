namespace Alpha;

public class AlphaService
{
    private readonly Beta.BetaStore _store = new Beta.BetaStore();

    public int Count() => _store.Size;
}
