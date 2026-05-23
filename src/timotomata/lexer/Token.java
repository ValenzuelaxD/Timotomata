package timotomata.lexer;

public class Token {
    public TipoToken tipo;
    public String lexema;
    public int linea;

    public Token(TipoToken tipo, String lexema, int linea) {
        this.tipo = tipo;
        this.lexema = lexema;
        this.linea = linea;
    }

    public String toString() {
        return tipo + " -> '" + lexema + "' linea " + linea;
    }
}
