package timotomata.parser.ast;

public class Regla {
    public Expresion condicion;
    public String estado;
    public String alerta;

    public Regla(Expresion condicion, String estado) {
        this(condicion, estado, null);
    }

    public Regla(Expresion condicion, String estado, String alerta) {
        this.condicion = condicion;
        this.estado = estado;
        this.alerta = alerta;
    }
}
