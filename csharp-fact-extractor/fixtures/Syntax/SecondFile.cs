namespace Syntax;

file class Helper
{
    public int Two() { return 2; }
}

public class UsesSecond
{
    public int Go() { return new Helper().Two(); }
}
