package timotomata.intermedio;

import java.util.ArrayList;
import java.util.List;

/**
 * Tabla de cuadruplos de un programa, con los contadores que hacen falta para
 * nombrar temporales y etiquetas.
 *
 * <p>Los temporales son globales al programa ({@code t0, t1, ...}) y las
 * etiquetas tambien ({@code L1, L2, ...}). Reiniciar los contadores por
 * sentencia daria tablas mas cortas pero mucho mas dificiles de leer y de
 * seguir, y aqui la tabla se muestra al usuario.
 */
public final class TablaCuadruplos {

    private final List<Cuadruplo> lista = new ArrayList<>();
    private int contadorTemporales;
    private int contadorEtiquetas;

    /**
     * Agrega una instruccion y devuelve el cuadruplo creado.
     *
     * <p>El indice se asigna solo: es la posicion que ocupa en la tabla, y es
     * la que se usa para senalar una instruccion concreta.
     */
    public Cuadruplo emitir(String op, String arg1, String arg2,
                            String resultado, int linea) {
        Cuadruplo c = new Cuadruplo(lista.size(), op, arg1, arg2, resultado, linea);
        lista.add(c);
        return c;
    }

    /** Siguiente temporal libre: {@code t0}, {@code t1}, ... */
    public String nuevoTemporal() {
        return "t" + contadorTemporales++;
    }

    /** Siguiente etiqueta libre: {@code L1}, {@code L2}, ... */
    public String nuevaEtiqueta() {
        return "L" + (++contadorEtiquetas);
    }

    /** Cuadruplos en orden de ejecucion. */
    public List<Cuadruplo> cuadruplos() {
        return List.copyOf(lista);
    }

    public int size() {
        return lista.size();
    }

    /** Volcado en texto, una instruccion por linea. */
    public String texto() {
        StringBuilder sb = new StringBuilder();
        for (Cuadruplo c : lista) {
            sb.append(c).append(System.lineSeparator());
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return texto();
    }
}
