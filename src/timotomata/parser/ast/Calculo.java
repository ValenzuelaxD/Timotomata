package timotomata.parser.ast;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Sentencia {@code calcular(OPERACION | EXPRESION , destino);}
 *
 * <p>El canal de entrada <b>no</b> es el primer argumento: es el parámetro
 * {@code CON=} de la operación. Antes lo era, y el resultado era que
 * {@code calcular(v, v * 2, x)} tenía que repetir {@code v} dos veces, y que
 * {@code calcular(w, v * 2, x)} con {@code w} equivocado se aceptaba en
 * silencio: la expresión mandaba y la cabecera no se usaba para nada.
 *
 * <p>No era solo una molestia sintáctica. Mantener ambas formas a la vez —con
 * y sin cabecera— hace que {@code calcular(v, ...)} empiece igual en las dos,
 * y una tabla LL(1) no puede decidir con un token. Al meter el canal dentro de
 * la operación, las dos familias arrancan con tokens distintos: una palabra
 * reservada, o el inicio de una expresión.
 *
 * <h2>Las dos formas</h2>
 * <pre>
 *   calcular(PROMEDIO(CON=voltaje, VENTANA=20), voltaje_prom);   // operación
 *   calcular(voltaje * 2, voltaje_x2);                           // expresión
 * </pre>
 *
 * <p>El destino es obligatorio en las dos: {@code calcular} siempre escribe en
 * algún sitio, y ese sitio queda declarado por la propia sentencia.
 */
public class Calculo {

    /**
     * Nombre de la señal donde se escribe el resultado. Siempre informado: es
     * el segundo argumento de la sentencia.
     */
    public String destino;

    /** Operación nombrada; {@code null} si el cálculo usa una expresión. */
    public String operacion;

    /** Expresión de transformación; {@code null} si se usa una operación. */
    public Expresion expresion;

    public List<Parametro> parametros = new ArrayList<>();

    public int linea = 0;

    public Calculo() { }

    public Calculo(String operacion) {
        this.operacion = operacion;
    }

    /** Nombre por el que el resultado queda disponible. */
    public String resultado() {
        return destino;
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
    //  CANALES DE ENTRADA
    // =============================================================

    /** Los canales nombrados con {@code CON=}, en el orden en que aparecen. */
    public List<String> canales() {
        List<String> r = new ArrayList<>();
        for (Parametro p : parametros) {
            if (esCon(p)) r.add(p.valor);
        }
        return r;
    }

    /**
     * Primer canal de entrada, o {@code null}.
     *
     * <p>Es el que aporta la magnitud física al destino. Las operaciones con
     * una sola entrada —{@code PROMEDIO}, {@code MAXIMO}— tienen exactamente
     * uno; {@code SUMA} puede tener varios y se verifican entre sí; los
     * generadores no tienen ninguno.
     */
    public String canal() {
        List<String> c = canales();
        return c.isEmpty() ? null : c.get(0);
    }

    private static boolean esCon(Parametro p) {
        return p.nombre != null && p.nombre.equalsIgnoreCase("con") && p.valor != null;
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
     *   <li><b>PROMEDIO, MAXIMO</b> — su {@code CON=}.</li>
     *   <li><b>SUMA</b> — sus {@code CON=}, que es su firma.</li>
     *   <li><b>SENO, COSENO, CUADRADA</b> — nada. Un generador produce su
     *       valor a partir del reloj, no de un canal.</li>
     * </ul>
     */
    public Set<String> origenes() {
        Set<String> r = new LinkedHashSet<>();
        if (esExpresion()) {
            recorrerVariables(expresion, r);
            return r;
        }
        r.addAll(canales());
        return r;
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
        sb.append("calcular(");
        if (esExpresion()) {
            sb.append(expresion);
        } else {
            sb.append(operacion).append('(');
            for (int i = 0; i < parametros.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(parametros.get(i));
            }
            sb.append(')');
        }
        sb.append(", ").append(destino).append(')');
        return sb.toString();
    }
}