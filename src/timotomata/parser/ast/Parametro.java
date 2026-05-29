package timotomata.parser.ast;

public class Parametro {
    public String nombre;   // amplitud, frecuencia, ventana, con
    public String valor;    // puede ser numero o identificador

    public Parametro(String nombre, String valor) {
        this.nombre = nombre;
        this.valor = valor;
    }

    public String toString() {
        return nombre + " " + valor;
    }
}
