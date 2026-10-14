package timotomata.parser.ast;

/**
 * Sentencia {@code si CONDICION entonces estado = X;}
 *
 * <p>La condición es un árbol de {@link Condicion}, no una {@link Expresion}:
 * una expresión numérica no puede ser verdadera o falsa.
 */
public class Regla {

    public Condicion condicion;
    public String estado;
    public int linea;

    /** Rama {@code sino} de esta regla concreta, o {@code null}. */
    public Condicion ramaSino;
    public String estadoSino;

    public Regla(Condicion condicion, String estado) {
        this.condicion = condicion;
        this.estado = estado;
    }

    public Regla(Condicion condicion, String estado, int linea) {
        this.condicion = condicion;
        this.estado = estado;
        this.linea = linea;
    }

    /** {@code true} si la regla declara su propia rama {@code sino}. */
    public boolean tieneRamaSino() {
        return ramaSino != null;
    }
}