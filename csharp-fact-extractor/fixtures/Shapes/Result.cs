namespace Shapes;

/// Two types with one name: a plain one and a generic one. Both must survive extraction.
public class Result
{
    public bool Ok { get; set; }

    public string Describe() => Ok ? "ok" : "failed";
}

public class Result<T>
{
    public T? Value { get; set; }

    public bool HasValue() => Value is not null;
}

/// A generic type with no namesake keeps its plain name.
public class Box<T>
{
    public T? Content { get; set; }
}
