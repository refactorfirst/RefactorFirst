namespace Syntax;

file class Tool
{
    public int RunB() { return 1; }
}

public class UsesB
{
    public int Go() { return new Tool().RunB(); }
}
