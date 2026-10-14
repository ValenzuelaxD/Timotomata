package timotomata.parser.ast;

/**
 * Literal de estado del sistema: {@code NORMAL}, {@code PICO}, {@code CAIDA} o
 * {@code INESTABLE}.
 *
 * <p>Es el otro operando de {@code estado == PICO}. Como {@link EstadoActual},
 * no es una magnitud y no tiene unidad: el analizador solo admite que uno de
 * los dos lados de una comparación sea un estado.
 */
public class LiteralEstado implements Expresion {

    /** El lexema tal como lo escribió el programador. */
    public final String nombre;

    public int linea;

    public LiteralEstado(String nombre) {
        this.nombre = nombre;
    }

    @Override
    public String toString() {
        return nombre;
    }
}