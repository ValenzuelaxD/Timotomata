package timotomata.lexer;

public enum TipoToken {
    SENSOR, UMBRAL, SI, ENTONCES, ESTADO, ABS,
    ID, NUMERO, ESTADO_SISTEMA,
    MAYOR, MENOR, IGUAL_IGUAL, MAYOR_IGUAL, MENOR_IGUAL, DIFERENTE,
    MAS, MENOS, POR, DIV,
    ASIGNACION, PUNTO_COMA, PAREN_IZQ, PAREN_DER, DOS_PUNTOS,
    CALCULAR, COMA,
    SINO, MUESTREO,
    /**
     * Conectores lógicos de las condiciones.
     *
     * <p>`Y`, `O` y `NO` son palabras reservadas y, por lo tanto, dejan de
     * poder usarse como nombres. Es el costo de escribir el lenguaje en
     * castellano: un identificador `y` o `o` es más natural en un programa de
     * este dominio que cualquier otra cosa, así que conviene saber que el
     * analizador léxico los habrá rechazado con un mensaje de palabra
     * reservada, no de identificador desconocido.
     */
    Y, O, NO,
    SENO, COSENO, CUADRADA, PROMEDIO, MAXIMO, SUMA,
    AMPLITUD, FRECUENCIA, VENTANA, CON,
    ELECTRICO, TERMICO, LUMINICO, MECANICO, QUIMICO, HIDRAULICO,
    EOF
}
