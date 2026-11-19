namespace Syntax;

public class Clamped
{
    public int Value
    {
        get => field;
        set => field = value < 0 ? 0 : value;
    }
}
