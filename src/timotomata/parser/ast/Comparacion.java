package timotomata.parser.ast;

import java.util.ArrayList;
import java.util.List;

/**
 * Comparación entre dos expresiones numéricas.
 *
 * <p>Es la hoja de toda condición, y la única forma en que un intervalo de
 * valores se puede traducir. Los seis operadores relacionales no dependen del
 * orden de los operandos: {@code a > b} y {@code b < a} describen el mismo
 * conjunto de valores, y el analizador lo tiene en cuenta al construir el
 * intervalo.
 */
public class Comparacion extends Condicion {

    public Expresion izquierda;
    public String operador;
    public Expresion derecha;

    public Comparacion(Expresion izquierda, String operador, Expresion derecha) {
        this.izquierda = izquierda;
        this.operador = operador;
        this.derecha = derecha;
    }

    @Override
    public List<String> variables() {
        List<String> r = new ArrayList<>();
        Expressiones.recoger(izquierda, r);
        Expressiones.recoger(this.derecha, r);
        return r;
    }

    @Override
    public String toString() {
        return izquierda + " " + operador + " " + derecha;
    }
}