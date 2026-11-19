namespace Syntax;

file class Tool
{
    public int RunDotted() { return 1; }
}

file class Gen
{
    public int Plain() { return 1; }
}

file class Gen<T>
{
    public int Generic() { return 2; }
}

public class UsesDotted
{
    public int Go() { return new Tool().RunDotted() + new Gen().Plain() + new Gen<int>().Generic(); }
}
