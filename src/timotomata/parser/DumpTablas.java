package timotomata.parser;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import timotomata.lexer.TipoToken;

/**
 * Vuelca las tablas de {@link Gramatica} como markdown.
 *
 * <p>Herramienta de mantenimiento: las tablas FIRST/FOLLOW, la de parsing LL(1)
 * y la de producciones de {@code docs/analisis_lexico_sintactico.md} se copian
 * de acá para que no puedan quedar desalineadas de la gramática real.
 *
 * <pre>
 *   java timotomata.parser.DumpTablas            &gt; tablas.md
 *   java timotomata.parser.DumpTablas tablas.md   (escribe UTF-8 directo)
 * </pre>
 */
public class DumpTablas {

    private static final List<String> LINEAS = new ArrayList<>();

    private static void linea(String s) {
        LINEAS.add(s);
    }

    public static void main(String[] args) throws Exception {
        volumetricas();
        String salida = String.join("\n", LINEAS) + "\n";

        // Sin argumentos se escribe en stdout. Con un argumento se escribe el
        // archivo en UTF-8: por consola el epsilon se pierde al redirigir, y
        // el documento tiene que llevar el simbolo correcto.
        if (args.length == 0) {
            System.out.print(salida);
        } else {
            java.nio.file.Files.writeString(java.nio.file.Path.of(args[0]),
                salida, java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private static void volumetricas() {
        linea("### PRODUCCIONES");
        for (int p = 0; p < Gramatica.NUM_PROD; p++) {
            StringBuilder sb = new StringBuilder();
            sb.append("| ").append(p).append(" | `")
              .append(Gramatica.NOMBRES_NT[Gramatica.idNoTerminal(Gramatica.PRODUCCIONES[p][0])]);
            boolean primera = true;
            for (int i = 1; i < Gramatica.PRODUCCIONES[p].length; i++) {
                // Separador simple: cada fila del markdown es una produccion,
                // y una barra dentro del codigo rompe la tabla.
                sb.append(primera ? " → " : " ")
                  .append(nombre(Gramatica.PRODUCCIONES[p][i]));
                primera = false;
            }
            if (primera) sb.append(" → ε");
            linea(sb + "` |");
        }

        linea("### FIRSTFOLLOW");
        for (int nt = 0; nt < Gramatica.NUM_NT; nt++) {
            linea(String.format("| `%s` | %s | %s | %s |",
                Gramatica.NOMBRES_NT[nt],
                lista(Gramatica.FIRST[nt]),
                Gramatica.FIRST_EPS[nt] ? "si" : "no",
                lista(Gramatica.FOLLOW[nt])));
        }

        linea("### LL1");
        for (int nt = 0; nt < Gramatica.NUM_NT; nt++) {
            StringBuilder sb = new StringBuilder();
            for (TipoToken t : TipoToken.values()) {
                int p = Gramatica.TABLA[nt][t.ordinal()];
                if (p == -1) continue;
                if (sb.length() > 0) sb.append(", ");
                sb.append('`').append(t).append("`→").append(p);
            }
            linea(String.format("| `%s` | %s |", Gramatica.NOMBRES_NT[nt], sb));
        }

        linea("### CONFLICTOS");
        linea(String.valueOf(Gramatica.conflictos()));
    }

    private static String nombre(int sym) {
        return Gramatica.esTerminal(sym)
            ? TipoToken.values()[sym].name()
            : Gramatica.NOMBRES_NT[Gramatica.idNoTerminal(sym)];
    }

    private static String lista(BitSet bs) {
        if (bs.isEmpty()) return "—";
        List<String> l = new ArrayList<>();
        for (int t = bs.nextSetBit(0); t >= 0; t = bs.nextSetBit(t + 1))
            l.add(TipoToken.values()[t].name());
        return String.join(", ", l);
    }
}