namespace Syntax;

file class Helper
{
    public int One() { return 1; }
}

public class UsesFirst
{
    public int Go() { return new Helper().One(); }
}
