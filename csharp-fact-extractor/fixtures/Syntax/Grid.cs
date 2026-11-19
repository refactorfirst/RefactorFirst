namespace Syntax;

public class Grid
{
    private readonly int[] cells = new int[9];

    public int this[int index]
    {
        get { return cells[index]; }
        set { cells[index] = value; }
    }
}

public class GridUser
{
    public void Write(Grid grid) { grid[0] = 1; }

    public int Read(Grid grid) { return grid[0]; }

    public void Bump(Grid grid) { grid[0] += 1; }

    public int? ReadIfAny(Grid? grid) { return grid?[0]; }
}
