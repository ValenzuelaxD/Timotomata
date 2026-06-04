package timotomata.lexer;

/**
 * Excepción personalizada del parser que lleva un ErrorInfo estructurado.
 * Permite que cada throw error(...) preserve el código específico del error
 * en lugar de perder la información al ser catch-eado como RuntimeException genérica.
 */
public class ErrorSintactico extends RuntimeException {
    private final ErrorInfo info;
    private final boolean skip;

    public ErrorSintactico(ErrorInfo info) {
        super(info.getMensaje());
        this.info = info;
        this.skip = false;
    }

    public ErrorSintactico(ErrorInfo info, boolean skip) {
        super(info.getMensaje());
        this.info = info;
        this.skip = skip;
    }

    public ErrorInfo getInfo() {
        return info;
    }

    public boolean isSkip() {
        return skip;
    }
}
