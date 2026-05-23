package timotomata.parser.ast;

public class Negacion implements Expresion {
    public Expresion expresion;

    public Negacion(Expresion expresion) {
        this.expresion = expresion;
    }

    public String toString() {
        return "(-" + expresion + ")";
    }
}
