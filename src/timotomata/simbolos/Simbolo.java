package timotomata.simbolos;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import timotomata.semantic.TipoSensor;

/**
 * SÍMBOLO — una entrada de la tabla de símbolos.
 *
 * Es la unidad de información que el front-end construye y que las fases
 * posteriores consultan. Antes esta información se reconstruía en tres
 * lugares distintos a partir de la lista de tokens; ahora se declara una vez
 * durante el parseo y se lee.
 */
public class Simbolo {

    /** Categoría del símbolo dentro del lenguaje. */
    public enum Clase {
        /** Canal físico declarado con `sensor x : TIPO;`. Solo lectura. */
        SENSOR,
        /** Constante declarada con `umbral x = 220;`. */
        UMBRAL,
        /** Señal derivada: el destino de un `calcular(origen, op, destino)`. */
        SENAL
    }

    private final String nombre;
    private final Clase clase;
    private TipoSensor tipo;
    private final Double valor;
    private final int linea;
    private int primerUso = 0;
    private final Set<String> origenes = new LinkedHashSet<>();

    public Simbolo(String nombre, Clase clase, TipoSensor tipo, Double valor, int linea) {
        this.nombre = nombre;
        this.clase = clase;
        this.tipo = tipo;
        this.valor = valor;
        this.linea = linea;
    }

    // ─── Identidad ───

    public String nombre() {
        return nombre;
    }

    public Clase clase() {
        return clase;
    }

    /** Tipo de sensor declarado. {@code null} para umbrales. */
    public TipoSensor tipo() {
        return tipo;
    }

    /**
     * Fija el tipo de una señal derivada.
     *
     * <p>El parseo no puede resolverlo: el destino de un `calcular` hereda la
     * magnitud física de su origen, y eso solo se sabe cuando ya se conoce la
     * firma de la operación y el tipo del sensor de entrada. Lo completa la
     * fase semántica.
     */
    public void asignarTipo(TipoSensor tipo) {
        this.tipo = tipo;
    }

    /** Constante, solo para umbrales. */
    public Double valor() {
        return valor;
    }

    /** Línea de la declaración. */
    public int linea() {
        return linea;
    }

    /**
     * Nombres de los que este símbolo lee su valor.
     *
     * <p>Solo tiene sentido para señales derivadas: cada entrada es una arista
     * del grafo de flujo de datos. Los sensores tienen el origen físico, que
     * no es un símbolo del programa, así que la lista queda vacía.
     */
    public Set<String> origenes() {
        return Collections.unmodifiableSet(origenes);
    }

    public void agregarOrigen(String nombre) {
        if (nombre != null) origenes.add(nombre);
    }

    /** Línea del primer uso en una expresión, o 0 si nunca se usó. */
    public int primerUso() {
        return primerUso;
    }

    public boolean usado() {
        return primerUso > 0;
    }

    void registrarUso(int linea) {
        if (this.primerUso == 0) this.primerUso = linea;
    }

    // ─── Presentación ───

    /** Nombre del tipo declarado, o `—` si no aplica. */
    public String nombreTipo() {
        return tipo == null ? "—" : tipo.etiqueta();
    }

    /** Unidad física del tipo, o `—` si no aplica. */
    public String unidad() {
        return tipo == null ? "—" : tipo.unidad();
    }

    /** Valor como texto, o `—` si no aplica. */
    public String valorTexto() {
        if (clase != Clase.UMBRAL || valor == null) return "—";
        double v = valor;
        return (v == Math.floor(v) && !Double.isInfinite(v))
            ? String.valueOf((long) v)
            : String.valueOf(v);
    }

    @Override
    public String toString() {
        return nombre + " (" + clase + (tipo == null ? "" : "/" + tipo) + ")";
    }
}