namespace Syntax;

public static class Ext
{
    extension(Order order)
    {
        public int Twice() { return order.Total * 2; }

        public int Doubled { get { return order.Total * 2; } }
    }

    extension(Grid grid)
    {
        public int Twice() { return grid[0] * 2; }
    }

    extension<T>(List<T> items)
    {
        public int Size() { return items.Count; }
    }
}

public class ExtUser
{
    public int Go(Order order) { return order.Twice(); }

    public int GoStatic(Order order) { return Ext.Twice(order); }

    public int GoGrid(Grid grid) { return grid.Twice(); }

    public int GoProperty(Order order) { return order.Doubled; }

    public int GoGeneric(List<int> items) { return items.Size(); }
}
