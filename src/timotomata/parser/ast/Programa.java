package timotomata.parser.ast;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import timotomata.semantic.TipoSensor;
import timotomata.simbolos.TablaSimbolos;

/**
 * Raíz del AST: el programa completo.
 *
 * <h2>Semántica de ejecución</h2>
 *
 * <p>Un programa Timotomata es un <b>kernel de muestreo</b>. La máquina lo
 * ejecuta una vez por instante {@code t = 0, 1, 2, …}, y en cada pasada:
 *
 * <ol>
 *   <li>Lee el valor de cada canal físico.</li>
 *   <li>Ejecuta los {@code calcular} <b>en el orden del fuente</b>, escribiendo
 *       cada resultado en su señal derivada.</li>
 *   <li>Evalúa las reglas <b>en el orden del fuente, y gana la primera que se
 *       cumpla</b>. Si ninguna se cumple, queda el estado de
 *       {@link #estadoPorDefecto}.</li>
 *   <li>El resultado observable es {@code estado}.</li>
 * </ol>
 *
 * <p>La precedencia "gana la primera" es parte del lenguaje: es lo que
 * determina qué reglas son alcanzables y cuáles quedan sombreadas.
 *
 * <p>Las cuatro colecciones planas (`sensores`, `umbrales`, `reglas`,
 * `calculos`) se conservan porque las consume el analizador semántico y la
 * interfaz. A ellas se suma la información que las fases posteriores necesitan
 * y que antes no existía:
 *
 * <ul>
 *   <li>{@link #sensoresDeclarados} — cada canal con su tipo declarado y línea.</li>
 *   <li>{@link #muestreo} — el reloj del programa: segundos por tick.</li>
 *   <li>{@link #estadoInicial} — valor de {@code estado} antes de la primera regla.</li>
 *   <li>{@link #estadoPorDefecto} — valor de {@code sino = X;} cuando nada dispara.</li>
 *   <li>{@link #simbolos} — la tabla de símbolos con atributos.</li>
 * </ul>
 */
public class Programa {

    // ─── Colecciones existentes (sin cambios de comportamiento) ───
    public List<String> sensores = new ArrayList<>();
    public Map<String, Double> umbrales = new LinkedHashMap<>();
    public List<Regla> reglas = new ArrayList<>();
    public List<Calculo> calculos = new ArrayList<>();

    // ─── Información nueva ───
    /** Canales con su tipo declarado y línea. */
    public List<Sensor> sensoresDeclarados = new ArrayList<>();

    /** Valor de `estado` fijado antes de cualquier regla, o {@code null}. */
    public String estadoInicial;

    /** Valor de `sino = X;`: salida cuando ninguna regla se cumple. */
    public String estadoPorDefecto;

    /**
     * Duración de un tick de muestreo, en segundos (`muestreo = 0.01;`).
     *
     * <p>Es el reloj del programa. Sin él no se puede generar el kernel de
     * muestreo, y las operaciones con memoria ({@code PROMEDIO},
     * {@code SENO}) no tienen base temporal contra la cual validar.
     */
    public Double muestreo;

    /** Línea de la declaración de `muestreo`. */
    public int lineaMuestreo;

    /** Tabla de símbolos con atributos, construida durante el parseo. */
    public TablaSimbolos simbolos = new TablaSimbolos();

    // ─── Consultas ───

    /** Sensor por nombre, o {@code null}. */
    public Sensor sensor(String nombre) {
        for (Sensor s : sensoresDeclarados) {
            if (s.nombre().equals(nombre)) return s;
        }
        return null;
    }

    /**
     * Tipo declarado de un símbolo medible, o {@code null}.
     *
     * <p>Consulta primero la tabla de símbolos, que conoce tanto los canales
     * declarados como las señales derivadas, y solo después la lista de
     * sensores como respaldo. Sin ese orden, una señal cuyo canal de origen es
     * otra señal —{@code calcular(voltaje_prom, voltaje_prom * 1.1, ajustado)}—
     * resolvería su tipo a {@code null} y quedaría sin verificar para siempre.
     */
    public TipoSensor tipoDe(String nombre) {
        TipoSensor t = simbolos.tipoDe(nombre);
        if (t != null) return t;
        Sensor s = sensor(nombre);
        return s == null ? null : s.tipo();
    }

    public void imprimir() {
        System.out.println("Programa");
        System.out.println("  Sensores: " + sensores);
        System.out.println("  Umbrales: " + umbrales);
        if (estadoInicial != null) System.out.println("  Estado inicial: " + estadoInicial);
        if (estadoPorDefecto != null) System.out.println("  Estado por defecto: " + estadoPorDefecto);
        System.out.println("  Reglas:");
        for (Regla r : reglas) {
            System.out.println("    si " + r.condicion + " entonces estado = " + r.estado);
        }
        System.out.println("  Calculos:");
        for (Calculo c : calculos) {
            System.out.println("    " + c);
        }
    }
}