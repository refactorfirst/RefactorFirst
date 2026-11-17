namespace Members;

public class Node
{
    private int value;

    public int Value
    {
        get { return value; }
        set { this.value = value; }
    }
}

public class Holder
{
    private Node child = new Node();
    private List<int> items = new List<int>();

    public Node Child
    {
        get { return child; }
        set { child = value; }
    }

    public List<int> Items
    {
        get { return items; }
        set { items = value; }
    }
}

/// Three ways to write a property inside an object initializer. Only the middle one replaces it.
public class Nester
{
    /// `Child = { ... }` reads the existing child and sets a value on it.
    public Holder Nested() { return new Holder { Child = { Value = 1 } }; }

    /// `Child = new Node()` replaces the child.
    public Holder Replaced() { return new Holder { Child = new Node() }; }

    /// `Items = { ... }` reads the existing list and adds to it.
    public Holder Filled() { return new Holder { Items = { 1, 2 } }; }
}
