package timotomata.parser.ast;

public class Variable implements Expresion {
    public String nombre;

    public Variable(String nombre) {
        this.nombre = nombre;
    }

    public String toString() {
        return nombre;
    }
}
