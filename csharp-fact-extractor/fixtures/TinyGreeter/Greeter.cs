namespace TinyGreeter;

public class Greeter
{
    private readonly MessageStore _store;

    public Greeter()
    {
        _store = new MessageStore(this);
    }

    public string Punctuation { get; set; } = "!";

    public string Greet(string name)
    {
        if (string.IsNullOrEmpty(name))
        {
            return _store.Find("hello") + Punctuation;
        }
        return _store.Find("hello") + " " + name + Punctuation;
    }
}
