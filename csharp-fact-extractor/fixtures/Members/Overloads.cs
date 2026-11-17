namespace Members;

/// Legal overloads that differ only by a type parameter or by how a parameter is passed.
public class Overloads
{
    public int Saved;

    public void Save() { Saved++; }

    public void Save<T>() { if (typeof(T) == typeof(int)) { Saved++; } }

    public void Add(int amount) { Saved += amount; }

    public void Add(ref int amount) { if (amount > 0) { amount = 0; } }

    public void Read(out int amount) { amount = Saved; }
}

public class OverloadCaller
{
    public void Go(Overloads overloads)
    {
        int amount = 1;
        overloads.Save<int>();
        overloads.Add(ref amount);
    }
}
