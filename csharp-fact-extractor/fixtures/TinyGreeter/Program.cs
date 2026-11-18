namespace TinyGreeter;

public class Program
{
    public static void Main(string[] args)
    {
        var greeter = new Greeter();
        System.Console.WriteLine(greeter.Greet("world"));
    }
}
