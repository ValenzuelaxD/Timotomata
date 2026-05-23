package timotomata.parser.ast;

public class Abs implements Expresion {
    public Expresion expresion;

    public Abs(Expresion expresion) {
        this.expresion = expresion;
    }

    public String toString() {
        return "abs(" + expresion + ")";
    }
}
