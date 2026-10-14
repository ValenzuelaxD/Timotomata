package timotomata.parser.ast;

/**
 * CONDICIÓN — la parte booleana de una regla.
 *
 * <p>Un booleano no es un número. Antes de que existieran los conectores, la
 * condición era directamente una {@link Expresion} relacional, y eso obligaba a
 * que todo lo que supiera de condiciones supiera también de aritmética: la
 * regla 8 tenía que comprobar que el operador fuera relacional antes de poder
 * traducir la condición a un intervalo de valores.
 *
 * <p>Con {@code Y}, {@code O} y {@code NO} esa disciplina se vuelve inevitable:
 * un {@link Conector} no tiene intervalo, y {@code estado == PICO} no tiene
 * izquierda en forma de magnitud. Separar las dos jerarquías hace que cada
 * análisis se aplique a lo que corresponde.
 *
 * <p>La precedencia es plana: {@code Y} y {@code O} tienen la misma, y la
 * evaluación es de izquierda a derecha. {@code NO} liga más fuerte que ambos y
 * se aplica solo a la comparación que sigue.
 *
 * <pre>
 *   si a &gt; 10 y b &lt; 2 o c == 5        -&gt;  (a &gt; 10 y b &lt; 2) o c == 5
 *   si no a &gt; 10 y b &lt; 2             -&gt;  no(a &gt; 10) y b &lt; 2
 *   si no no a &gt; 10                   -&gt;  a &gt; 10
 * </pre>
 *
 * <h2>Por qué no hay paréntesis en las condiciones</h2>
 *
 * <p>Un paréntesis podría ser una expresión agrupada —{@code (a + b) / 2}— o
 * una condición agrupada —{@code (a &gt; 1 y b &lt; 2)}—, y los dos empiezan
 * igual: paréntesis seguido de un identificador. El AFD no puede separarlos, y
 * hacerlo exigiría mirar dos tokens, con lo que la gramática dejaría de ser
 * LL(1) y {@link timotomata.parser.Gramatica#conflictos()} empezaría a
 * reportar conflictos.
 *
 * <p>Se prefiere perder el agrupamiento antes que la propiedad. La forma de
 * negar una conjunción es escribir su opuesta:
 *
 * <pre>
 *   // en vez de  si no (a &gt; 10 y b &lt; 2)
 *   si a &lt;= 10 o b &gt;= 2 entonces ...
 * </pre>
 */
public abstract class Condicion {

    public int linea;

    /**
     * Nombres leídos por la condición, en orden de aparición y sin repetir.
     *
     * <p>La sobreescribe cada subclase; es el único punto por el que el resto
     * del compilador consume las variables de una regla.
     */
    public abstract java.util.List<String> variables();

    @Override
    public abstract String toString();
}