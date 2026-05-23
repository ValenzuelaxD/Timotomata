package timotomata.parser.ast;

public class Regla {
    public Expresion condicion;
    public String estado;

    public Regla(Expresion condicion, String estado) {
        this.condicion = condicion;
        this.estado = estado;
    }
}
