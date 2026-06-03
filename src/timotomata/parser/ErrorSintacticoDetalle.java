package timotomata.parser;

/** Información de un error sintáctico con posición */
public class ErrorSintacticoDetalle {
    public String mensaje;
    public int linea;
    public int columna;
    public int longitud;

    public ErrorSintacticoDetalle(String mensaje, int linea, int columna, int longitud) {
        this.mensaje = mensaje;
        this.linea = linea;
        this.columna = columna;
        this.longitud = longitud;
    }

    public int getLinea() { return linea; }
    public int getColumna() { return columna; }
    public int getLongitud() { return longitud; }
    public String getMensaje() { return mensaje; }
}
