namespace Members;

public partial class Validator
{
    private int notes;

    public partial int Check(int amount)
    {
        if (amount > 10) return 2;
        if (amount > 0) return 1;
        Note(amount);
        return 0;
    }

    partial void Note(int amount)
    {
        if (amount < 0) { notes++; }
    }
}
