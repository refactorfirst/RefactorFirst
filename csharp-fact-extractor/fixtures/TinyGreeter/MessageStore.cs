namespace TinyGreeter;

public class MessageStore
{
    private readonly Greeter _owner;

    public MessageStore(Greeter owner)
    {
        _owner = owner;
    }

    public string Find(string key)
    {
        return key == "hello" ? "Hello" : key;
    }
}
