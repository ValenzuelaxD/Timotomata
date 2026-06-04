package timotomata.lexer;

/**
 * Catálogo maestro de errores del lenguaje Timotomata.
 * Cada error tiene un código único (E001-E0XX), tipo (Léxico/Sintáctico),
 * categoría y descripción.
 */
public enum TablaErrores {

    // ─── ERRORES LÉXICOS (L001-L005) ───
    L001("L001", "Léxico", "Carácter inválido",
        "Carácter '%s' no pertenece al alfabeto del lenguaje"),
    L002("L002", "Léxico", "Comentario no cerrado",
        "Comentario de bloque iniciado en línea %d no fue cerrado, se esperaba '*/'"),
    L003("L003", "Léxico", "Cadena no cerrada",
        "Cadena de texto iniciada en línea %d no fue cerrada, se esperaba '\"'"),
    L004("L004", "Léxico", "Secuencia no reconocida",
        "Secuencia no reconocida en el lenguaje: \"%s\""),
    L005("L005", "Léxico", "Operador incompleto",
        "Se esperaba '=' después de '!' para formar el operador '!='"),

    // ─── ERRORES LÉXICOS — Sugerencias de palabras (L006-L007) ───
    L006("L006", "Léxico", "Posible palabra reservada mal escrita",
        "¿Quiso decir '%s'? (se encontró '%s')"),
    L007("L007", "Léxico", "Palabra no reconocida",
        "Palabra '%s' no reconocida en el lenguaje"),

    // ─── ERRORES SINTÁCTICOS — Pre-validación (P001-P003) ───
    P001("P001", "Sintáctico", "Paréntesis sin abrir",
        "')' de cierre sin '(' de apertura"),
    P002("P002", "Sintáctico", "Llave sin abrir",
        "'}' de cierre sin '{' de apertura"),
    P003("P003", "Sintáctico", "Delimitador sin cerrar",
        "Falta '%s' de cierre (no balanceado) después de '%s'"),

    // ─── ERRORES SINTÁCTICOS — Sentinelas faltantes (P004-P005) ───
    P004("P004", "Sintáctico", "Falta punto y coma",
        "Falta ';' después de '%s'"),
    P005("P005", "Sintáctico", "Falta punto y coma (cadena)",
        "Falta ';' después de la cadena de texto"),

    // ─── ERRORES SINTÁCTICOS — Token inesperado (P006) ───
    P006("P006", "Sintáctico", "Token inesperado",
        "Token inesperado '%s'"),

    // ─── ERRORES SINTÁCTICOS — Sentencia: esperado inicio de sentencia (P007) ───
    P007("P007", "Sintáctico", "Sentencia inválida",
        "Se esperaba SENSOR, UMBRAL, RANGO, SI, CALCULAR o FIN, pero se encontró '%s'"),

    // ─── ERRORES SINTÁCTICOS — Sensor (P008-P009) ───
    P008("P008", "Sintáctico", "Tipo de sensor inválido",
        "Se esperaba 'electrico' o 'termico', pero se encontró '%s'"),
    P009("P009", "Sintáctico", "Falta punto y coma (sensor)",
        "Se esperaba ';' para cerrar la declaración del sensor"),

    // ─── ERRORES SINTÁCTICOS — Umbral (P010) ───
    P010("P010", "Sintáctico", "Falta '=' en umbral",
        "Se esperaba '=' después del nombre del umbral"),

    // ─── ERRORES SINTÁCTICOS — Rango (P011-P013) ───
    P011("P011", "Sintáctico", "Rango incompleto",
        "Se esperaba la palabra reservada 'minimo' o 'maximo'"),
    P012("P012", "Sintáctico", "Número inválido en rango",
        "Se esperaba un número para el valor mínimo/máximo"),
    P013("P013", "Sintáctico", "Falta punto y coma (rango)",
        "Se esperaba ';' para cerrar la declaración de rango"),

    // ─── ERRORES SINTÁCTICOS — Si/Entonces (P014-P016) ───
    P014("P014", "Sintáctico", "Falta 'entonces'",
        "Se esperaba la palabra reservada 'entonces' después de la condición"),
    P015("P015", "Sintáctico", "Consecuencia inválida",
        "Se esperaba '{' o una acción (estado = ... o alerta = ...), pero se encontró '%s'"),
    P016("P016", "Sintáctico", "Acción inválida",
        "Se esperaba 'estado = ...' o 'alerta = ...', pero se encontró '%s'"),

    // ─── ERRORES SINTÁCTICOS — Calcular (P017-P019) ───
    P017("P017", "Sintáctico", "Calcular inválido",
        "Se esperaba '(' o una función de análisis (promedio, maximo, fluctuacion), pero se encontró '%s'"),
    P018("P018", "Sintáctico", "Función de análisis inválida",
        "Se esperaba 'promedio', 'maximo' o 'fluctuacion', pero se encontró '%s'"),

    // ─── ERRORES SINTÁCTICOS — Condiciones (P019-P021) ───
    P019("P019", "Sintáctico", "Operador lógico inválido",
        "Se esperaba 'y' u 'o' (operador lógico), pero se encontró '%s'"),
    P020("P020", "Sintáctico", "Operador relacional inválido",
        "Se esperaba un operador relacional (>, <, ==, >=, <=, !=), pero se encontró '%s'"),
    P021("P021", "Sintáctico", "Valor numérico inválido",
        "Se esperaba un número o '-' para valor numérico, pero se encontró '%s'"),

    // ─── ERRORES SINTÁCTICOS — Expresiones (P022) ───
    P022("P022", "Sintáctico", "Factor inválido",
        "Se esperaba un número, identificador, 'abs()' o '-', pero se encontró '%s'"),

    // ─── ERRORES SINTÁCTICOS — Calcular: operación (P023-P024) ───
    P023("P023", "Sintáctico", "Operación inválida",
        "Se esperaba 'seno', 'coseno', 'cuadrada', 'promedio', 'maximo' o 'suma', pero se encontró '%s'"),
    P024("P024", "Sintáctico", "Parámetro inválido",
        "Se esperaba 'amplitud', 'frecuencia', 'ventana' o 'con', pero se encontró '%s'"),

    // ─── ERRORES SINTÁCTICOS — Estado del sistema (P025) ───
    P025("P025", "Sintáctico", "Estado del sistema inválido",
        "Se esperaba NORMAL, PICO, CAIDA o INESTABLE, pero se encontró '%s'"),

    // ─── ERRORES SINTÁCTICOS — Sugerencias (P026-P028) ───
    P026("P026", "Sintáctico", "Falta punto y coma (sugerencia)",
        "¿Falta ';' después de '%s'?"),
    P027("P027", "Sintáctico", "Falta 'entonces' (sugerencia)",
        "¿Falta 'entonces' después de la condición?"),
    P028("P028", "Sintáctico", "Operador confundido (sugerencia)",
        "¿Quiso escribir '==' (comparación) en vez de '='?"),

    // ─── ERRORES SINTÁCTICOS — Faltas de cierre (P029-P031) ───
    P029("P029", "Sintáctico", "Falta ';' (fin de programa)",
        "Falta ';' después de '%s' (fin del programa inesperado)"),
    P030("P030", "Sintáctico", "Falta ';' antes de '}'",
        "Falta ';' después de '%s' antes de cerrar el bloque '}'"),
    P031("P031", "Sintáctico", "Falta ';' (token inesperado)",
        "Falta ';' después de '%s' pero se encontró '%s'");

    private final String codigo;
    private final String tipo;
    private final String categoria;
    private final String descripcion;

    TablaErrores(String codigo, String tipo, String categoria, String descripcion) {
        this.codigo = codigo;
        this.tipo = tipo;
        this.categoria = categoria;
        this.descripcion = descripcion;
    }

    public String getCodigo()       { return codigo; }
    public String getTipo()         { return tipo; }
    public String getCategoria()    { return categoria; }
    public String getDescripcion()  { return descripcion; }

    /**
     * Formatea la descripción del error con los argumentos dados.
     * Ejemplo: L001.formatear("€") → "Carácter '€' no pertenece al alfabeto del lenguaje"
     */
    public String formatear(Object... args) {
        try {
            return String.format(descripcion, args);
        } catch (Exception e) {
            return descripcion;
        }
    }
}
