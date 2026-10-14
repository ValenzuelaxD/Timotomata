package timotomata.parser.ast;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Sentencia `calcular(origen, OPERACION(...), destino);`
 *
 * <p>El destino es una señal derivada distinta del canal de origen: el sensor
 * físico se conserva como lectura del mundo real, y el resultado del cálculo
 * vive aparte. Esto evita la ambigüedad de tratar al identificador del sensor
 * a la vez como entrada del hardware y como resultado del cálculo.
 *
 * <p>Si se omite el destino, el cálculo sobrescribe el canal de origen
 * (comportamiento heredado, que se conserva por compatibilidad).
 *
 * <h2>Dos formas de transformar</h2>
 * <ul>
 *   <li><b>Operación nombrada</b> — {@code PROMEDIO(VENTANA=10)},
 *       {@code SENO(AMPLITUD=250, FRECUENCIA=0.1)}. Son 6 operaciones
 *       predefinidas con firma fija.</li>
 *   <li><b>Expresión arbitraria</b> — {@code voltaje * 2},
 *       {@code abs(voltaje - 220)}. Cuando se usa, {@link #expresion} queda
 *       informada y {@link #operacion} es {@code null}.</li>
 * </ul>
 *
 * <p>La segunda forma sustituye a las variables: da la misma expresividad
 * sin introducir almacenamiento mutable, ámbito ni una cuarta clase de
 * símbolo.
 */
public class Calculo {

    /** Sensor o señal de la que se lee. */
    public String sensor;

    /** Identificador del destino; {@code null} significa sobrescribir el origen. */
    public String destino;

    /** Operacion nombrada; {@code null} si el cálculo usa una expresión. */
    public String operacion;

    /** Expresión de transformación; {@code null} si se usa una operación. */
    public Expresion expresion;

    public List<Parametro> parametros = new ArrayList<>();

    public int linea = 0;

    public Calculo(String sensor, String operacion) {
        this.sensor = sensor;
        this.operacion = operacion;
    }

    /** {@code true} cuando el resultado se guarda en una señal derivada nueva. */
    public boolean deriva() {
        return destino != null;
    }

    /** Nombre por el que el resultado queda disponible. */
    public String resultado() {
        return destino != null ? destino : sensor;
    }

    /** {@code true} si la transformación es una operación nombrada. */
    public boolean esOperacion() {
        return operacion != null;
    }

    /** {@code true} si la transformación es una expresión arbitraria. */
    public boolean esExpresion() {
        return expresion != null;
    }

    // =============================================================
    //  DEPENDENCIAS DE DATOS
    // =============================================================

    /**
     * Nombres de los que este cálculo lee su valor.
     *
     * <p>Es la arista del grafo de flujo de datos, y depende de la firma de
     * cada transformación:
     *
     * <ul>
     *   <li><b>Expresión</b> — las variables que aparecen en ella.</li>
     *   <li><b>PROMEDIO, MAXIMO</b> — el canal de la cabecera. La ventana se
     *       aplica sobre ese canal, así que la dependencia es con él y no con
     *       un parámetro.</li>
     *   <li><b>SUMA</b> — los operandos de sus {@code CON=}, que es su firma.</li>
     *   <li><b>SENO, COSENO, CUADRADA</b> — nada. Un generador produce su
     *       valor a partir del reloj, no del canal de la cabecera: en
     *       {@code calcular(voltaje, SENO(...), x)} la señal {@code x} no
     *       depende de {@code voltaje}, aunque el nombre esté ahí.</li>
     * </ul>
     */
    public Set<String> origenes() {
        Set<String> r = new LinkedHashSet<>();
        if (esExpresion()) {
            recorrerVariables(expresion, r);
            return r;
        }
        if (operacion == null) return r;

        switch (operacion.toLowerCase()) {
            case "seno":
            case "coseno":
            case "cuadrada":
                return r;
            case "suma":
                for (Parametro p : parametros) {
                    if (esCon(p)) r.add(p.valor);
                }
                return r;
            default:
                if (sensor != null) r.add(sensor);
                for (Parametro p : parametros) {
                    if (esCon(p)) r.add(p.valor);
                }
                return r;
        }
    }

    private static boolean esCon(Parametro p) {
        return p.nombre != null && p.nombre.equalsIgnoreCase("con") && p.valor != null;
    }

    private static void recorrerVariables(Expresion e, Set<String> acc) {
        if (e instanceof Variable v) {
            acc.add(v.nombre);
        } else if (e instanceof Binaria b) {
            recorrerVariables(b.izquierda, acc);
            recorrerVariables(b.derecha, acc);
        } else if (e instanceof Negacion n) {
            recorrerVariables(n.expresion, acc);
        } else if (e instanceof Abs a) {
            recorrerVariables(a.expresion, acc);
        }
    }

    // =============================================================
    //  MEMORIA REQUERIDA
    // =============================================================

    /** Cuánta memoria necesita este cálculo, y de qué tipo. */
    public static class Memoria {
        /** Tamaño del buffer circular, o 0 si no necesita historial. */
        public final int muestras;
        /** {@code true} si necesita una fase acumulada (generadores de onda). */
        public final boolean fase;

        Memoria(int muestras, boolean fase) {
            this.muestras = muestras;
            this.fase = fase;
        }

        public boolean historico() {
            return muestras > 0;
        }

        @Override
        public String toString() {
            if (historico()) return "buffer circular de " + muestras + " muestras";
            if (fase) return "acumulador de fase";
            return "ninguna";
        }
    }

    /**
     * Memoria que el código objeto tiene que reservar para este cálculo.
     *
     * <p>Es el contrato con el generador de código: {@code PROMEDIO} y
     * {@code MAXIMO} necesitan un buffer circular de {@code VENTANA} muestras
     * más un índice; los generadores de onda necesitan la fase acumulada; el
     * resto no necesita estado.
     *
     * <p>Que la operación exija memoria es la razón de ser de la regla
     * semántica que valida {@code VENTANA} contra el mínimo físico del tipo.
     */
    public Memoria memoria() {
        if (esExpresion()) return new Memoria(0, false);
        if (operacion == null) return new Memoria(0, false);
        switch (operacion.toLowerCase()) {
            case "promedio":
            case "maximo": {
                int v = 0;
                for (Parametro p : parametros) {
                    if (p.nombre != null && p.nombre.equalsIgnoreCase("ventana")) {
                        try {
                            v = (int) Math.floor(Double.parseDouble(p.valor));
                        } catch (NumberFormatException e) {
                            v = 0;
                        }
                    }
                }
                return new Memoria(Math.max(v, 0), false);
            }
            case "seno":
            case "coseno":
            case "cuadrada":
                return new Memoria(0, true);
            default:
                return new Memoria(0, false);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("calcular(").append(sensor).append(", ");
        if (esExpresion()) {
            sb.append(expresion);
        } else {
            sb.append(operacion).append("(");
            for (int i = 0; i < parametros.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(parametros.get(i));
            }
            sb.append(")");
        }
        if (deriva()) sb.append(", ").append(destino);
        sb.append(")");
        return sb.toString();
    }
}