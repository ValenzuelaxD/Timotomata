package timotomata.parser.ast;

import java.util.List;

/**
 * Recorrido compartido de las expresiones numéricas.
 *
 * <p>Las expresiones son un árbol sin método común para saber qué nombres
 * contienen: hay cinco subclases de {@link Expresion} y cada una conoce cómo
 * seguir bajando. El recorrido estaba duplicado entre el parser (para marcar
 * usos) y el analizador semántico (para verificarlos), y los dos podían
 * dejar de ver la misma variable.
 *
 * <p>Este es el único lugar donde se recorre un árbol de expresiones.
 */
public final class Expressiones {

    private Expressiones() { }

    /**
     * Agrega a {@code acc} las variables de {@code e}, sin repetir.
     *
     * @param e   expresión a recorrer; {@code null} se ignora
     * @param acc acumulador, que se modifica en el sitio
     */
    public static void recoger(Expresion e, List<String> acc) {
        if (e == null) return;
        if (e instanceof Variable v) {
            if (!acc.contains(v.nombre)) acc.add(v.nombre);
        } else if (e instanceof Binaria b) {
            recoger(b.izquierda, acc);
            recoger(b.derecha, acc);
        } else if (e instanceof Negacion n) {
            recoger(n.expresion, acc);
        } else if (e instanceof Abs a) {
            recoger(a.expresion, acc);
        }
    }

    /** Variables de la expresión, en orden de aparición y sin repetir. */
    public static List<String> variables(Expresion e) {
        List<String> r = new java.util.ArrayList<>();
        recoger(e, r);
        return r;
    }
}