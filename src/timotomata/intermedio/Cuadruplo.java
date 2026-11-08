package timotomata.intermedio;

/**
 * Una instruccion de la representacion intermedia en forma de cuadruplo:
 *
 * <pre>
 *   (operador, argumento 1, argumento 2, resultado)
 * </pre>
 *
 * <p>Los campos que la instruccion no usa valen {@code null} y se muestran
 * como un guion, igual que en la notacion de la asignatura. El campo
 * {@code resultado} es siempre explicito: es lo que permite insertar saltos y
 * etiquetas sin renumerar nada, que es justo lo que un triplo no puede hacer.
 *
 * <p>La {@code linea} es la del fuente que origino la instruccion, para poder
 * situarla en el editor si hiciera falta.
 */
public final class Cuadruplo {

    /** Posicion de esta instruccion dentro de la tabla. */
    public final int indice;

    /** Operador: aritmetico, relacional, logico, de control o nombrado. */
    public final String op;

    /** Primer operando, o {@code null}. */
    public final String arg1;

    /** Segundo operando, o {@code null}. */
    public final String arg2;

    /** Donde se guarda el resultado, o la etiqueta destino en los saltos. */
    public final String resultado;

    /** Linea del fuente, o {@code 0} si la instruccion no viene de una. */
    public final int linea;

    public Cuadruplo(int indice, String op, String arg1, String arg2,
                     String resultado, int linea) {
        this.indice = indice;
        this.op = op;
        this.arg1 = arg1;
        this.arg2 = arg2;
        this.resultado = resultado;
        this.linea = linea;
    }

    /** Texto de un campo, con guion cuando no se usa. */
    private static String campo(String valor) {
        return valor == null || valor.isEmpty() ? "-" : valor;
    }

    public int getIndice() { return indice; }
    public String getOp() { return campo(op); }
    public String getArg1() { return campo(arg1); }
    public String getArg2() { return campo(arg2); }
    public String getResultado() { return campo(resultado); }
    public int getLinea() { return linea; }

    @Override
    public String toString() {
        return String.format("%3d: (%s, %s, %s, %s)",
            indice, getOp(), getArg1(), getArg2(), getResultado());
    }
}
