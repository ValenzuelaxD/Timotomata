package timotomata.simbolos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import timotomata.semantic.TipoSensor;

/**
 * TABLA DE SÍMBOLOS
 * ==================
 *
 * Estructura única que registra lo declarado en el programa y sus atributos.
 *
 * Antes de esta clase, la misma información se derivaba en tres sitios
 * distintos y de forma independiente:
 *
 *   - `AnalizadorSemantico.declaraciones(tokens)` re-leía la lista de tokens
 *     buscando el patrón `SENSOR|UMBRAL` seguido de `ID`;
 *   - `AppController.construirTablaSimbolos()` la volvía a armar para la tabla
 *     de la interfaz;
 *   - `AppController.buscarLineaDeclaracion()` la recorría otra vez.
 *
 * Los tres podían desincronizarse. Ahora se llena una vez, durante el parseo,
 * y todos leen de acá.
 *
 * Conserva el orden de inserción, de modo que la tabla de la interfaz mantiene
 * el orden del programa fuente.
 */
public class TablaSimbolos {

    private final Map<String, Simbolo> simbolos = new LinkedHashMap<>();
    private final List<Duplicado> duplicados = new ArrayList<>();

    /**
     * Re-declaración de un nombre. El símbolo original se conserva en la
     * tabla; esto solo deja rastro del intento, para que la fase semántica lo
     * reporte como error.
     */
    public record Duplicado(String nombre, Simbolo.Clase clase,
                            int lineaOriginal, int lineaRepetida) { }

    /** Nombres declarados más de una vez, en orden de aparición. */
    public List<Duplicado> duplicados() {
        return Collections.unmodifiableList(duplicados);
    }

    /** Declara un símbolo y avisa si el nombre ya estaba en uso. */
    private Simbolo registrar(String nombre, Simbolo.Clase clase,
                              TipoSensor tipo, Double valor, int linea) {
        Simbolo previo = simbolos.get(nombre);
        if (previo != null) {
            duplicados.add(new Duplicado(nombre, clase, previo.linea(), linea));
            return previo;
        }
        Simbolo s = new Simbolo(nombre, clase, tipo, valor, linea);
        simbolos.put(nombre, s);
        return s;
    }

    // =============================================================
    //  DECLARACIONES
    // =============================================================

    /**
     * Registra un canal físico: `sensor voltaje : ELECTRICO;`.
     *
     * @return el símbolo creado, o el existente si el nombre ya estaba declarado
     */
    public Simbolo declararSensor(String nombre, TipoSensor tipo, int linea) {
        return registrar(nombre, Simbolo.Clase.SENSOR, tipo, null, linea);
    }

    /**
     * Registra una constante: {@code umbral voltaje_max : ELECTRICO = 220;}.
     *
     * <p>El umbral declara su propio tipo, de modo que su valor se verifica
     * contra la ficha de ese tipo y no contra el nombre de la variable.
     *
     * @return el símbolo creado, o el existente si el nombre ya estaba declarado
     */
    public Simbolo declararUmbral(String nombre, TipoSensor tipo, double valor, int linea) {
        return registrar(nombre, Simbolo.Clase.UMBRAL, tipo, valor, linea);
    }

    /**
     * Registra una señal derivada: el destino de `calcular(origen, op, destino)`.
     * Hereda el tipo físico de la operación y del origen, porque la señal
     * resultante se mide en la misma magnitud que su canal de entrada.
     *
     * @return el símbolo creado, o el existente si el nombre ya estaba declarado
     */
    public Simbolo declararSenal(String nombre, TipoSensor tipo, int linea) {
        return registrar(nombre, Simbolo.Clase.SENAL, tipo, null, linea);
    }

    // =============================================================
    //  FLUJO DE DATOS
    // =============================================================

    /**
     * Anota que {@code destino} se calcula a partir de {@code origenes}.
     *
     * <p>Cada arista del grafo de flujo de datos se registra acá, una sola vez,
     * en el momento en que el parser termina de leer el {@code calcular}. El
     * grafo resultante es lo que la fase semántica usa para verificar el orden
     * de definición y la ausencia de ciclos, y lo que el futuro generador de
     * código usará como orden de evaluación.
     *
     * @param destino  nombre de la señal resultante (o el canal sobrescrito)
     * @param origenes nombres de los que se lee; vacío para los generadores
     */
    public void registrarCalculo(String destino, Collection<String> origenes) {
        Simbolo s = simbolos.get(destino);
        if (s == null) return;
        for (String o : origenes) {
            s.agregarOrigen(o);
        }
    }

    /**
     * Grafo de flujo de datos: señal derivada → de qué se lee.
     *
     * <p>Solo incluye señales con al menos un origen. Los sensores quedan
     * fuera porque su origen es físico, no un símbolo del programa.
     */
    public Map<String, Set<String>> grafoDependencias() {
        Map<String, Set<String>> g = new LinkedHashMap<>();
        for (Simbolo s : simbolos.values()) {
            if (s.clase() == Simbolo.Clase.SENAL && !s.origenes().isEmpty()) {
                g.put(s.nombre(), s.origenes());
            }
        }
        return Collections.unmodifiableMap(g);
    }

    // =============================================================
    //  CONSULTAS
    // =============================================================

    /** Busca un símbolo por nombre. {@code null} si no existe. */
    public Simbolo buscar(String nombre) {
        return simbolos.get(nombre);
    }

    /** {@code true} si el nombre está declarado. */
    public boolean declarado(String nombre) {
        return simbolos.containsKey(nombre);
    }

    /** Anota que un símbolo se usó en la línea dada. */
    public void registrarUso(String nombre, int linea) {
        Simbolo s = simbolos.get(nombre);
        if (s != null) s.registrarUso(linea);
    }

    /** Todos los símbolos, en orden de declaración. */
    public Collection<Simbolo> todos() {
        return Collections.unmodifiableCollection(simbolos.values());
    }

    /** Tipo físico de un símbolo, o {@code null} si no existe o no tiene. */
    public TipoSensor tipoDe(String nombre) {
        Simbolo s = simbolos.get(nombre);
        return s == null ? null : s.tipo();
    }

    /** Valor constante de un símbolo, o {@code null} si no es umbral. */
    public Double valorDe(String nombre) {
        Simbolo s = simbolos.get(nombre);
        return s == null || s.clase() != Simbolo.Clase.UMBRAL ? null : s.valor();
    }

    public int tamano() {
        return simbolos.size();
    }

    public boolean vacia() {
        return simbolos.isEmpty();
    }

    public void limpiar() {
        simbolos.clear();
        duplicados.clear();
    }

    /** Volcado legible, para el panel de la interfaz. */
    public void imprimir() {
        System.out.println("Tabla de simbolos (" + simbolos.size() + ")");
        for (Simbolo s : simbolos.values()) {
            System.out.printf("  %-14s %-7s %-10s %-8s linea %d%n",
                s.nombre(), s.clase(), s.nombreTipo(), s.valorTexto(), s.linea());
        }
    }
}