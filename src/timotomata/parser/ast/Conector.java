package timotomata.parser.ast;

import java.util.ArrayList;
import java.util.List;

/**
 * Conjunción o disyunción de dos condiciones.
 *
 * <p>{@code Y} y {@code O} comparten precedencia y se asocian a la izquierda,
 * así que {@code a y b o c} se lee {@code (a y b) o c}. Esa es una decisión,
 * no un descuido: en un lenguaje de monitoreo las condiciones suelen ser cortas
 * y del mismo tipo, y obligar a agrupar para resolver una ambigüedad que el
 * programador casi nunca tiene no aporta nada.
 *
 * <p>Quien necesite comportamiento distinto escribe los paréntesis:
 * {@code a y (b o c)}.
 */
public class Conector extends Condicion {

    /** Cuál de los dos conectores es. */
    public enum Tipo {
        Y("y"),
        O("o");

        private final String texto;

        Tipo(String texto) {
            this.texto = texto;
        }

        @Override
        public String toString() {
            return texto;
        }
    }

    public Tipo tipo;
    public Condicion izquierda;
    public Condicion derecha;

    public Conector(Tipo tipo, Condicion izquierda, Condicion derecha) {
        this.tipo = tipo;
        this.izquierda = izquierda;
        this.derecha = derecha;
    }

    @Override
    public List<String> variables() {
        List<String> r = new ArrayList<>(izquierda.variables());
        for (String v : derecha.variables()) {
            if (!r.contains(v)) r.add(v);
        }
        return r;
    }

    @Override
    public String toString() {
        return "(" + izquierda + " " + tipo + " " + derecha + ")";
    }
}