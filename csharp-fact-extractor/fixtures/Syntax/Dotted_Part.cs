namespace Syntax;

file class Tool
{
    public int RunUnderscore() { return 1; }
}

public class UsesUnderscore
{
    public int Go() { return new Tool().RunUnderscore(); }
}
