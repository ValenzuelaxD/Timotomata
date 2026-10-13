package timotomata.parser.ast;

public class Negacion implements Expresion {
    public Expresion expresion;

    /** Línea del signo menos. */
    public int linea;

    public Negacion(Expresion expresion) {
        this.expresion = expresion;
    }

    public String toString() {
        return "(-" + expresion + ")";
    }
}