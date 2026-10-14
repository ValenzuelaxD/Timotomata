package timotomata.parser.ast;

import java.util.List;

/**
 * Negación de una condición.
 *
 * <p>{@code NO} liga más fuerte que {@code Y} y {@code O}: se aplica solo a la
 * comparación que lo sigue, nunca al resultado de una conjunción. Así
 * {@code no a > 10 y b < 2} se lee {@code no(a > 10) y b < 2}, que es lo que
 * cualquiera escribiría de memoria.
 *
 * <p>Se puede encadenar: {@code no no a > 10} equivale a {@code a > 10}.
 */
public class Negado extends Condicion {

    public Condicion condicion;

    public Negado(Condicion condicion) {
        this.condicion = condicion;
    }

    @Override
    public List<String> variables() {
        return condicion.variables();
    }

    @Override
    public String toString() {
        return "no " + condicion;
    }
}