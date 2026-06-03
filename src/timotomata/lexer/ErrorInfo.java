package timotomata.lexer;

/**
 * Modelo de datos para una entrada en la tabla de errores.
 * Contiene código, tipo, categoría, mensaje, línea y columna.
 * Es un POJO puro (sin dependencias JavaFX) para que funcione tanto en tests como en la UI.
 */
public class ErrorInfo {

    private final String codigo;
    private final String tipo;
    private final String categoria;
    private final String mensaje;
    private final int linea;
    private final int columna;

    public ErrorInfo(String codigo, String tipo, String categoria, String mensaje, int linea, int columna) {
        this.codigo = codigo;
        this.tipo = tipo;
        this.categoria = categoria;
        this.mensaje = mensaje;
        this.linea = linea;
        this.columna = columna;
    }

    public ErrorInfo(TablaErrores error, int linea, int columna, Object... args) {
        this.codigo = error.getCodigo();
        this.tipo = error.getTipo();
        this.categoria = error.getCategoria();
        this.mensaje = error.formatear(args);
        this.linea = linea;
        this.columna = columna;
    }

    /** Formato plano para backward compatibility con tests */
    public String toPlainText() {
        return "[" + codigo + "] " + tipo + ": " + mensaje
            + " (línea " + linea + ", columna " + columna + ")";
    }

    public String getCodigo()    { return codigo; }
    public String getTipo()      { return tipo; }
    public String getCategoria() { return categoria; }
    public String getMensaje()   { return mensaje; }
    public int getLinea()        { return linea; }
    public int getColumna()      { return columna; }
}
