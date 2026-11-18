namespace Members;

public class Order
{
    private int total;

    public int Total
    {
        get { return total; }
        set { total = value; }
    }
}

public class Clerk
{
    public void Write(Order order) { order.Total = 5; }

    public int Read(Order order) { return order.Total; }

    public void Bump(Order order) { order.Total += 1; }

    public Order Create() { return new Order { Total = 2 }; }
}
