namespace Syntax;

public static class Ext
{
    extension(Order order)
    {
        public int Twice() { return order.Total * 2; }
    }
}

public class ExtUser
{
    public int Go(Order order) { return order.Twice(); }
}
