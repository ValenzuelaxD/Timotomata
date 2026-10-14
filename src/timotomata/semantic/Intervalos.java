package timotomata.semantic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * CONJUNTOS DE INTERVALOS
 * =======================
 *
 * Una unión disjunta de intervalos sobre la recta real, en forma canónica:
 * ordenados, sin solaparse y sin intervalos vacíos.
 *
 * <p>Es la representación con la que el analizador razona sobre reglas. Cada
 * comparación se traduce a un conjunto —{@code voltaje > 220} es el intervalo
 * {@code (220, +inf)}— y con esa representación se puede preguntar si una regla
 * es alcanzable, si dos reglas se contradicen, o qué valores dejan sin cubrir
 * ninguna.
 *
 * <p>La forma canónica no es un detalle: sin ella, {@code unir} de dos conjuntos
 * que se tocan produciría {@code [0,5) U [5,10]} en lugar de {@code [0,10]}, y
 * la pregunta "¿queda algo sin cubrir?" respondería que sí cuando no queda
 * nada.
 *
 * <p>Toda operación devuelve una lista nueva y ordenada, y no toca sus
 * argumentos.
 */
public final class Intervalos {

    private Intervalos() { }

    /** La recta real entera. */
    public static final List<Intervalo> UNIVERSO = List.of(
        new Intervalo(Double.NEGATIVE_INFINITY, true, Double.POSITIVE_INFINITY, true));

    /** El conjunto vacío. */
    public static final List<Intervalo> VACIO = List.of();

    /**
     * Intervalo semiabierto, con sus extremos incluidos o no.
     *
     * <p>Es inmutable. {@link Double#NEGATIVE_INFINITY} y
     * {@link Double#POSITIVE_INFINITY} funcionan como extremos abiertos naturales.
     */
    public record Intervalo(double lo, boolean loInc, double hi, boolean hiInc) {

        public boolean contiene(double v) {
            if (v < lo) return false;
            if (v > hi) return false;
            if (v == lo && !loInc) return false;
            if (v == hi && !hiInc) return false;
            return true;
        }

        public boolean esVacio() {
            return lo > hi || (lo == hi && !(loInc && hiInc));
        }

        @Override
        public String toString() {
            String inf = lo == Double.NEGATIVE_INFINITY ? "-inf" : formato(lo);
            String sup = hi == Double.POSITIVE_INFINITY ? "+inf" : formato(hi);
            return (loInc ? "[" : "(") + inf + ", " + sup + (hiInc ? "]" : ")");
        }

        private static String formato(double v) {
            if (v == Math.floor(v) && !Double.isInfinite(v)) {
                return String.valueOf((long) v);
            }
            return String.valueOf(v);
        }
    }

    // =============================================================
    //  CONSTRUCTORES
    // =============================================================

    /**
     * Conjunto definido por un operador relacional contra una constante.
     *
     * <p>Devuelve vacío para operadores que no son relacionales, para que el
     * llamante pueda descartar la comparación sin tratar cada caso.
     */
    public static List<Intervalo> deOperador(String op, double c) {
        List<Intervalo> r = new ArrayList<>();
        switch (op) {
            case ">"  -> r.add(new Intervalo(c, false, Double.POSITIVE_INFINITY, true));
            case ">=" -> r.add(new Intervalo(c, true,  Double.POSITIVE_INFINITY, true));
            case "<"  -> r.add(new Intervalo(Double.NEGATIVE_INFINITY, true, c, false));
            case "<=" -> r.add(new Intervalo(Double.NEGATIVE_INFINITY, true, c, true));
            case "==" -> r.add(new Intervalo(c, true, c, true));
            case "!=" -> {
                r.add(new Intervalo(Double.NEGATIVE_INFINITY, true, c, false));
                r.add(new Intervalo(c, false, Double.POSITIVE_INFINITY, true));
            }
            default -> { }
        }
        return canonica(r);
    }

    /** Invierte un operador relacional: {@code a > b} es {@code b < a}. */
    public static String invertirOperador(String op) {
        return switch (op) {
            case ">"  -> "<";
            case ">=" -> "<=";
            case "<"  -> ">";
            case "<=" -> ">=";
            default   -> op;          // == y != son simétricos
        };
    }

    // =============================================================
    //  ÁLGEBRA DE CONJUNTOS
    // =============================================================

    public static boolean esVacio(List<Intervalo> a) {
        return a.isEmpty();
    }

    /** Unión. */
    public static List<Intervalo> unir(List<Intervalo> a, List<Intervalo> b) {
        List<Intervalo> r = new ArrayList<>(a);
        r.addAll(b);
        return canonica(r);
    }

    /** Intersección. */
    public static List<Intervalo> interseccion(List<Intervalo> a, List<Intervalo> b) {
        List<Intervalo> r = new ArrayList<>();
        for (Intervalo x : a) {
            for (Intervalo y : b) {
                Intervalo i = cruzar(x, y);
                if (i != null && !i.esVacio()) r.add(i);
            }
        }
        return canonica(r);
    }

    /** Complemento dentro de la recta real. */
    public static List<Intervalo> complemento(List<Intervalo> a) {
        List<Intervalo> r = new ArrayList<>();
        double lo = Double.NEGATIVE_INFINITY;
        boolean loInc = true;
        for (Intervalo x : canonica(a)) {
            // Hueco hasta el comienzo de x, respetando que x sea abierto.
            Intervalo hueco = new Intervalo(lo, loInc, x.lo(), !x.loInc());
            if (!hueco.esVacio()) r.add(hueco);
            lo = x.hi();
            loInc = !x.hiInc();
        }
        Intervalo cola = new Intervalo(lo, loInc, Double.POSITIVE_INFINITY, true);
        if (!cola.esVacio()) r.add(cola);
        return r;
    }

    /** {@code true} si {@code b} no aporta ningún valor que {@code a} no tenga. */
    public static boolean cubre(List<Intervalo> a, List<Intervalo> b) {
        if (b.isEmpty()) return true;
        if (a.isEmpty()) return false;
        List<Intervalo> falta = interseccion(b, complemento(a));
        return falta.isEmpty();
    }

    /** {@code true} si los dos conjuntos tienen algún valor en común. */
    public static boolean solapan(List<Intervalo> a, List<Intervalo> b) {
        return !interseccion(a, b).isEmpty();
    }

    // =============================================================
    //  REPRESENTACIÓN DE REGLAS
    // =============================================================

    /**
     * Restricción exacta de una regla, variable por variable.
     *
     * <p>No se calcula aquí: depende de cómo las reglas del programa se
     * comparan contra constantes, y eso es del analizador semántico. Lo que sí
     * vive acá es el contrato, que es lo importante:
     *
     * <p>El cálculo debe devolver {@code null} —en vez de una aproximación—
     * cuando la regla no se puede traducir exactamente. Esa distinción es lo
     * que separa un análisis correcto de uno que miente:
     *
     * <ul>
     *   <li>Una conjunción de comparaciones contra constantes sí es exacta:
     *       describe un producto de restricciones, una por variable.</li>
     *   <li>Una disyunción <b>no</b>. La regla {@code a > 10 o b < 3} se
     *       cumple con cualquier valor de {@code a} si {@code b} es pequeño, así
     *       que proyectarla sobre {@code a} da la recta entera, no
     *       {@code (10, +inf)}.</li>
     *   <li>Una negación tampoco.</li>
     *   <li>Comparar dos variables entre sí tampoco: {@code a > b} no impone
     *       ninguna restricción sobre {@code a} por separado.</li>
     * </ul>
     *
     * <p>Como el llamante descarta lo que vuelve {@code null}, nunca opera sobre
     * una sobre-aproximación creyendo que es exacta.
     */

    /**
     * Representación legible de un conjunto: los intervalos unidos por `U`.
     *
     * <p>No usar {@code List.toString()}: entre corchetes y con el `toString()`
     * de cada record queda `[(220.0, +inf)]`, que se confunde con un intervalo.
     */
    public static String texto(List<Intervalo> a) {
        if (a.isEmpty()) return "(vacio)";
        StringBuilder sb = new StringBuilder();
        for (Intervalo i : a) {
            if (sb.length() > 0) sb.append(" U ");
            sb.append(i);
        }
        return sb.toString();
    }

    // =============================================================
    //  INTERNOS
    // =============================================================

    private static Intervalo cruzar(Intervalo a, Intervalo b) {
        double lo = Math.max(a.lo(), b.lo());
        boolean loInc;
        if (a.lo() == b.lo()) loInc = a.loInc() && b.loInc();
        else loInc = (a.lo() > b.lo()) ? a.loInc() : b.loInc();

        double hi = Math.min(a.hi(), b.hi());
        boolean hiInc;
        if (a.hi() == b.hi()) hiInc = a.hiInc() && b.hiInc();
        else hiInc = (a.hi() < b.hi()) ? a.hiInc() : b.hiInc();

        return new Intervalo(lo, loInc, hi, hiInc);
    }

    /**
     * Pone la lista en forma canónica: sin vacíos, ordenada y sin solapamientos
     * ni extremos compartidos.
     */
    private static List<Intervalo> canonica(List<Intervalo> entrada) {
        List<Intervalo> r = new ArrayList<>();
        for (Intervalo i : entrada) {
            if (!i.esVacio()) r.add(i);
        }
        r.sort(Comparator.comparingDouble(Intervalo::lo)
                          .thenComparingDouble(Intervalo::hi));

        List<Intervalo> out = new ArrayList<>();
        for (Intervalo i : r) {
            if (out.isEmpty()) {
                out.add(i);
                continue;
            }
            Intervalo ult = out.get(out.size() - 1);
            if (i.lo() < ult.hi() || (i.lo() == ult.hi() && ult.hiInc() && i.loInc())) {
                // Se tocan o se solapan: se funden.
                out.set(out.size() - 1, new Intervalo(
                    ult.lo(), ult.loInc(),
                    Math.max(ult.hi(), i.hi()),
                    (ult.hi() > i.hi()) ? ult.hiInc() : i.hiInc()));
            } else {
                out.add(i);
            }
        }
        return out;
    }
}