namespace Shapes;

/// A class with a primary constructor: the constructor is the parameter list, not the whole class.
public class Counter(int start)
{
    private int _value = start;

    public int Next()
    {
        _value++;
        return _value;
    }

    public Result<int> Current()
    {
        return new Result<int> { Value = _value };
    }
}
