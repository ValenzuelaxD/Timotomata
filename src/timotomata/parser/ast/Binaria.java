package timotomata.parser.ast;

public class Binaria implements Expresion {
    public Expresion izquierda;
    public String operador;
    public Expresion derecha;

    public Binaria(Expresion izquierda, String operador, Expresion derecha) {
        this.izquierda = izquierda;
        this.operador = operador;
        this.derecha = derecha;
    }

    public String toString() {
        return izquierda + " " + operador + " " + derecha;
    }
}
