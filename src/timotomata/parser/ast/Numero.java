package timotomata.parser.ast;

public class Numero implements Expresion {
    public double valor;

    public Numero(double valor) {
        this.valor = valor;
    }

    public String toString() {
        return String.valueOf(valor);
    }
}
