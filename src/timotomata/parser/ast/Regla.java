package timotomata.parser.ast;

public class Regla {
    public Expresion condicion;
    public String estado;
    public int linea;

    public Regla(Expresion condicion, String estado) {
        this.condicion = condicion;
        this.estado = estado;
    }

    public Regla(Expresion condicion, String estado, int linea) {
        this.condicion = condicion;
        this.estado = estado;
        this.linea = linea;
    }
}
