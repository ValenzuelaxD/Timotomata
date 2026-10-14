package timotomata.parser.ast;

/**
 * El registro de máquina {@code estado} leído como valor.
 *
 * <p>{@code estado} no es una magnitud: no tiene unidad, ni rango, ni ficha
 * técnica. Es el valor que la máquina tiene ahora mismo, y solo tiene sentido
 * compararlo con otro estado. Por eso es una clase propia y no un
 * {@link Variable} cualquiera —si lo fuera, el analizador lo trataría como un
 * canal más y le buscaría un tipo declarado que no existe.
 *
 * <p>Habilita escribir {@code si estado == PICO y voltaje > max entonces ...},
 * que es la forma de expresar "seguimos en PICO y empeora", un patrón que en
 * un autómata de estados es corriente y que antes no se podía escribir.
 */
public class EstadoActual implements Expresion {

    public int linea;

    @Override
    public String toString() {
        return "estado";
    }
}