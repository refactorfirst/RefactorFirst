namespace Syntax;

public class Order
{
    private int total;

    public int Total
    {
        get { return total; }
        set { total = value; }
    }
}

public class Writer
{
    public void Tuple(Order a, Order b) { (a.Total, b.Total) = (1, 2); }

    public int TupleWithLocal(Order a)
    {
        (a.Total, var rest) = (1, 2);
        return rest;
    }

    public void Parenthesized(Order a) { (a.Total) = 5; }

    public void NullConditional(Order? a) { a?.Total = 5; }

    public int? NullConditionalRead(Order? a) { return a?.Total; }

    public void NestedTuple(Order a, Order b, Order c) { (a.Total, (b.Total, c.Total)) = (1, (2, 3)); }

    public void NullConditionalBump(Order? a) { a?.Total += 1; }

    public string NameOf(Order a) { return nameof(a.Total); }
}
