package timotomata.parser.ast;

public class Variable implements Expresion {
    public String nombre;

    /** Línea donde aparece la referencia, para el registro de uso de símbolos. */
    public int linea;

    public Variable(String nombre) {
        this.nombre = nombre;
    }

    public String toString() {
        return nombre;
    }
}