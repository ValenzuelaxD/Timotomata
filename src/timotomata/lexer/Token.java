package timotomata.lexer;

public class Token {
    public TipoToken tipo;
    public String lexema;
    public int linea;
    public int columna;
    public boolean tieneError = false;

    public Token(TipoToken tipo, String lexema, int linea) {
        this(tipo, lexema, linea, 1);
    }

    public Token(TipoToken tipo, String lexema, int linea, int columna) {
        this.tipo = tipo;
        this.lexema = lexema;
        this.linea = linea;
        this.columna = columna;
    }

    public String toString() {
        return tipo + " -> '" + lexema + "' [L:" + linea + ", C:" + columna + "]";
    }
}
