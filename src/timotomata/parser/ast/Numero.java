package timotomata.parser.ast;

public class Numero implements Expresion {
    public double valor;

    /** Línea del literal. */
    public int linea;

    public Numero(double valor) {
        this.valor = valor;
    }

    public String toString() {
        return String.valueOf(valor);
    }
}