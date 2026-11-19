namespace Syntax;

file class Tool
{
    public int RunA() { return 1; }
}

public class UsesA
{
    public int Go() { return new Tool().RunA(); }
}
