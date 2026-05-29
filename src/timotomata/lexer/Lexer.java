package timotomata.lexer;

import java.util.*;

// ============================================================
//  ANALIZADOR LÉXICO BASADO EN AFD CON TABLA DE TRANSICIONES
// ============================================================
//
//  ❖ Alfabeto  : 17 clases de caracteres (LETRA, DIGITO, etc.)
//  ❖ Estados   : 13 estados (Q0 inicial, Q_ID, Q_NUM, ...)
//  ❖ Transición: TABLA_TRANS[estado][clase] → siguiente estado
//  ❖ Aceptación: esAceptacion[estado] = true/false
//  ❖ Maximal Munch : se avanza mientras haya transición;
//       al no haber, se retrocede al último estado de aceptación.
// ============================================================

public class Lexer {

    // ---- 1. ALFABETO (clases de caracteres) ----
    public static final int
        LETRA = 0,  DIGITO = 1,  PUNTO = 2,
        ESP = 3,    NL = 4,
        MAS = 5,    MENOS = 6,   POR = 7,      DIV = 8,
        IGUAL = 9,  MAYOR_ = 10, MENOR_ = 11,   EXCL = 12,
        PUNTOCOMA = 13, PIZQ = 14, PDER = 15, COMA_ = 16, OTRO = 17;
    public static final int NUM_CLASES = 18;

    // ---- 2. ESTADOS DEL AFD ----
    public static final int
        Q0 = 0,             // Estado inicial
        Q_ID = 1,           // Leyendo identificador / palabra reservada
        Q_NUM = 2,          // Leyendo parte entera de un número
        Q_NUM_PUNTO = 3,    // Acabamos de ver '.' en un número
        Q_NUM_DEC = 4,      // Leyendo parte decimal
        Q_EQ = 5,           // Vimos '=', esperando ver si es '=' o algo más
        Q_GT = 6,           // Vimos '>', esperando ver si es '='
        Q_LT = 7,           // Vimos '<', esperando ver si es '=' o '>'
        Q_NOT = 8,          // Vimos '!', esperando ver si es '='
        Q_DIV = 9,          // Vimos '/', esperando ver si es '/' o '*' (comentario)
        Q_COM_LINEA = 10,   // Dentro de comentario //
        Q_COM_BLOQ = 11,    // Dentro de comentario /* */
        Q_COM_BLOQ_FIN = 12;// Vimos '*' dentro de /*, esperando '/' para cerrar
    public static final int NUM_ESTADOS = 13;
    public static final int SIN_TRANS = -1;

    // ---- 3. FUNCIÓN DE TRANSICIÓN ────────────────────────────
    // TABLA_TRANS[estado][clase] = siguiente estado
    // SIN_TRANS = transición no definida (estado muerto)
    // ───────────────────────────────────────────────────────────
    public static final int[][] TABLA_TRANS = new int[NUM_ESTADOS][NUM_CLASES];

    // ---- 4. ESTADOS DE ACEPTACIÓN ----
    // Los estados que, al no poder avanzar, producen un token.
    public static final boolean[] ES_ACEPTACION = new boolean[NUM_ESTADOS];

    static {
        // Inicializar todo a SIN_TRANS
        for (int[] fila : TABLA_TRANS) Arrays.fill(fila, SIN_TRANS);

        // ─── Transiciones desde Q0 (estado inicial) ───
        TABLA_TRANS[Q0][LETRA]    = Q_ID;
        TABLA_TRANS[Q0][DIGITO]   = Q_NUM;
        TABLA_TRANS[Q0][ESP]      = Q0;      // ignorar espacios
        TABLA_TRANS[Q0][NL]       = Q0;      // salto de línea
        TABLA_TRANS[Q0][MAS]      = Q0;      // token directo
        TABLA_TRANS[Q0][MENOS]    = Q0;      // token directo
        TABLA_TRANS[Q0][POR]      = Q0;      // token directo
        TABLA_TRANS[Q0][DIV]      = Q_DIV;   // podría ser /, //, /*
        TABLA_TRANS[Q0][IGUAL]    = Q_EQ;    // podría ser = o ==
        TABLA_TRANS[Q0][MAYOR_]   = Q_GT;    // podría ser > o >=
        TABLA_TRANS[Q0][MENOR_]   = Q_LT;    // podría ser <, <=, <>
        TABLA_TRANS[Q0][EXCL]     = Q_NOT;   // podría ser ! o !=
        TABLA_TRANS[Q0][PUNTOCOMA]= Q0;      // token directo
        TABLA_TRANS[Q0][PIZQ]     = Q0;      // token directo
        TABLA_TRANS[Q0][PDER]     = Q0;      // token directo
        TABLA_TRANS[Q0][COMA_]    = Q0;      // token directo
        // OTRO → SIN_TRANS (error léxico)

        // ─── Transiciones desde Q_ID ───
        TABLA_TRANS[Q_ID][LETRA]  = Q_ID;
        TABLA_TRANS[Q_ID][DIGITO] = Q_ID;
        ES_ACEPTACION[Q_ID] = true;

        // ─── Transiciones desde Q_NUM (parte entera) ───
        TABLA_TRANS[Q_NUM][DIGITO] = Q_NUM;
        TABLA_TRANS[Q_NUM][PUNTO]  = Q_NUM_PUNTO;
        ES_ACEPTACION[Q_NUM] = true;

        // ─── Transiciones desde Q_NUM_PUNTO (acabamos de ver .) ───
        TABLA_TRANS[Q_NUM_PUNTO][DIGITO] = Q_NUM_DEC;
        // Si viene otro '.' → SIN_TRANS (error: segundo punto decimal)

        // ─── Transiciones desde Q_NUM_DEC (parte decimal) ───
        TABLA_TRANS[Q_NUM_DEC][DIGITO] = Q_NUM_DEC;
        ES_ACEPTACION[Q_NUM_DEC] = true;

        // ─── Transiciones desde Q_EQ (vimos =) ───
        TABLA_TRANS[Q_EQ][IGUAL] = Q0;   // ==   → token IGUAL_IGUAL
        // Cualquier otra cosa → SIN_TRANS → retroceder y emitir ASIGNACION

        // ─── Transiciones desde Q_GT (vimos >) ───
        TABLA_TRANS[Q_GT][IGUAL] = Q0;   // >=   → token MAYOR_IGUAL

        // ─── Transiciones desde Q_LT (vimos <) ───
        TABLA_TRANS[Q_LT][IGUAL] = Q0;   // <=   → token MENOR_IGUAL
        TABLA_TRANS[Q_LT][MAYOR_] = Q0;  // <>   → token DIFERENTE

        // ─── Transiciones desde Q_NOT (vimos !) ───
        TABLA_TRANS[Q_NOT][IGUAL] = Q0;  // !=   → token DIFERENTE

        // ─── Transiciones desde Q_DIV (vimos /) ───
        TABLA_TRANS[Q_DIV][DIV] = Q_COM_LINEA;  // //   → comentario de línea
        TABLA_TRANS[Q_DIV][POR] = Q_COM_BLOQ;    // /*   → comentario de bloque
        // Cualquier otra cosa → SIN_TRANS → retroceder y emitir DIV

        // ─── Transiciones desde Q_COM_LINEA (//) ───
        // Todo se queda en Q_COM_LINEA excepto NL que vuelve a Q0
        for (int c = 0; c < NUM_CLASES; c++) {
            if (c != NL) TABLA_TRANS[Q_COM_LINEA][c] = Q_COM_LINEA;
        }
        TABLA_TRANS[Q_COM_LINEA][NL] = Q0;

        // ─── Transiciones desde Q_COM_BLOQ (/*) ───
        // Todo se queda salvo POR que va a Q_COM_BLOQ_FIN
        for (int c = 0; c < NUM_CLASES; c++) {
            if (c != POR) TABLA_TRANS[Q_COM_BLOQ][c] = Q_COM_BLOQ;
        }
        TABLA_TRANS[Q_COM_BLOQ][POR] = Q_COM_BLOQ_FIN;

        // ─── Transiciones desde Q_COM_BLOQ_FIN (vimos * dentro de /*) ───
        TABLA_TRANS[Q_COM_BLOQ_FIN][DIV] = Q0;           // */  → cierra comentario
        TABLA_TRANS[Q_COM_BLOQ_FIN][POR] = Q_COM_BLOQ_FIN; // **  → sigue esperando /
        for (int c = 0; c < NUM_CLASES; c++) {
            if (c != DIV && c != POR)
                TABLA_TRANS[Q_COM_BLOQ_FIN][c] = Q_COM_BLOQ; // vuelve a comentario
        }
    }

    // ---- 5. ATRIBUTOS DEL LEXER ----
    String fuente;
    List<Token> tokens = new ArrayList<>();
    List<String> erroresLexicos = new ArrayList<>();
    int actual = 0;
    int linea = 1;

    public Lexer(String fuente) {
        this.fuente = fuente;
    }

    public List<String> getErroresLexicos() {
        return erroresLexicos;
    }

    // ---- 6. CLASIFICADOR DE CARACTERES ----
    int clasificar(char c) {
        if (Character.isLetter(c) || c == '_') return LETRA;
        if (Character.isDigit(c)) return DIGITO;
        return switch (c) {
            case '.' -> PUNTO;
            case ' ', '\t', '\r' -> ESP;
            case '\n' -> NL;
            case '+' -> MAS;
            case '-' -> MENOS;
            case '*' -> POR;
            case '/' -> DIV;
            case '=' -> IGUAL;
            case '>' -> MAYOR_;
            case '<' -> MENOR_;
            case '!' -> EXCL;
            case ';' -> PUNTOCOMA;
            case '(' -> PIZQ;
            case ')' -> PDER;
            case ',' -> COMA_;
            default -> OTRO;
        };
    }

    // ---- 7. SIMULADOR DEL AFD ─────────────────────────────────
    // Algoritmo:
    //   1. Arrancar en Q0, marcar inicio del lexema.
    //   2. Mientras haya transición definida:
    //        - Consumir el carácter, seguir al siguiente estado.
    //        - Si es estado de aceptación, recordar posición.
    //        - Si vuelve a Q0 tras 1 carácter, emitir token directo.
    //        - Si vuelve a Q0 tras 2+ caracteres (==, >=, etc.),
    //          emitir token compuesto.
    //   3. Si no hay transición:
    //        - Si hay un último estado de aceptación, emitir ese
    //          token y retroceder a esa posición.
    //        - Si no, es error léxico.
    // ───────────────────────────────────────────────────────────
    public List<Token> escanear() {
        while (!fin()) {
            int inicio = actual;
            int inicioLinea = linea;
            int estado = Q0;
            int ultimoAcept = -1;
            int posUltimaAcept = -1;

            while (!fin()) {
                char c = ver();
                int clase = clasificar(c);
                int sigEstado = TABLA_TRANS[estado][clase];

                if (sigEstado == SIN_TRANS) break;

                // Consumir el carácter
                avanzar();
                // Incrementar línea para saltos de línea fuera de comentarios de línea
                if (clase == NL && estado != Q_COM_LINEA) {
                    linea++;
                }

                // ─── Caso 1: Transición a Q0 (token completado) ───
                if (sigEstado == Q0 && estado != Q0) {
                    if (estado == Q_COM_BLOQ_FIN && clase == DIV) {
                        // */ — comentario de bloque cerrado
                        inicio = actual;
                        break;
                    }
                    if (estado == Q_COM_LINEA && clase == NL) {
                        // // comentario — termina en nueva línea
                        linea++;
                        inicio = actual;
                        break;
                    }
                    // Solo llegamos aquí para ==, >=, <=, !=, <>
                    String lexema = fuente.substring(inicio, actual);
                    emitirTokenCompuesto(estado, clase, lexema, inicioLinea);
                    inicio = actual;
                    ultimoAcept = -1;
                    posUltimaAcept = -1;
                    break;
                }

                estado = sigEstado;

                // ─── Caso 2: Estado de aceptación ───
                if (ES_ACEPTACION[estado]) {
                    ultimoAcept = estado;
                    posUltimaAcept = actual;
                }

                // ─── Caso 3: Token directo de 1 carácter ───
                if (estado == Q0 && actual - inicio == 1) {
                    String lexema = fuente.substring(inicio, actual);
                    int claseInicial = clasificar(lexema.charAt(0));
                    emitirTokenDirecto(claseInicial, lexema, inicioLinea);
                    inicio = actual;
                    ultimoAcept = -1;
                    posUltimaAcept = -1;
                    break;
                }
            }

            // ─── Al salir del bucle ───
            if (inicio == actual && !fin()) {
                char cActual = fuente.charAt(actual);
                int claseActual = clasificar(cActual);
                if (TABLA_TRANS[Q0][claseActual] == SIN_TRANS) {
                    erroresLexicos.add("Error léxico en línea " + inicioLinea
                        + ": caracter no válido '" + cActual + "'");
                    actual++;
                }
            } else if (ultimoAcept != -1) {
                String lexema = fuente.substring(inicio, posUltimaAcept);
                emitirToken(ultimoAcept, lexema, inicioLinea);
                actual = posUltimaAcept;
            } else if (estado == Q_EQ || estado == Q_GT || estado == Q_LT
                    || estado == Q_NOT || estado == Q_DIV) {
                String lexema = fuente.substring(inicio, inicio + 1);
                emitirTokenUnario(estado, lexema, inicioLinea);
                actual = inicio + 1;
            } else if (estado == Q_COM_BLOQ || estado == Q_COM_BLOQ_FIN) {
                erroresLexicos.add("Error léxico en línea " + inicioLinea
                    + ": comentario de bloque no cerrado");
                actual = inicio + 1;
            } else if (actual > inicio) {
                erroresLexicos.add("Error léxico en línea " + inicioLinea
                    + ": secuencia no válida \"" + fuente.substring(inicio, actual) + "\"");
                actual = inicio + 1;
            }
        }

        // ─── Mostrar errores acumulados ───
        if (!erroresLexicos.isEmpty()) {
            System.out.println("\n===== ERRORES LÉXICOS =====");
            for (String err : erroresLexicos) {
                System.out.println(err);
            }
        }

        tokens.add(new Token(TipoToken.EOF, "", linea));
        return tokens;
    }

    // ---- 8. EMISIÓN DE TOKENS ─────────────────────────────────

    void emitirToken(int estado, String lexema, int linea) {
        if (estado == Q_ID) {
            // Comparar en minúsculas para que las palabras reservadas
            // sean case-insensitive (Sensor, SENSOR, sensor → SENSOR)
            String lexemaLower = lexema.toLowerCase();
            switch (lexemaLower) {
                case "sensor"   -> agregar(TipoToken.SENSOR, lexema, linea);
                case "umbral"   -> agregar(TipoToken.UMBRAL, lexema, linea);
                case "si"       -> agregar(TipoToken.SI, lexema, linea);
                case "entonces" -> agregar(TipoToken.ENTONCES, lexema, linea);
                case "estado"   -> agregar(TipoToken.ESTADO, lexema, linea);
                case "abs"      -> agregar(TipoToken.ABS, lexema, linea);
                case "normal", "pico", "caida", "inestable" ->
                    agregar(TipoToken.ESTADO_SISTEMA, lexema, linea);
                case "calcular" -> agregar(TipoToken.CALCULAR, lexema, linea);
                default -> {
                    // Palabras reservadas UPPERCASE (operaciones CALCULAR)
                    switch (lexema) {
                        case "SENO"     -> agregar(TipoToken.SENO, lexema, linea);
                        case "COSENO"   -> agregar(TipoToken.COSENO, lexema, linea);
                        case "CUADRADA" -> agregar(TipoToken.CUADRADA, lexema, linea);
                        case "PROMEDIO" -> agregar(TipoToken.PROMEDIO, lexema, linea);
                        case "MAXIMO"   -> agregar(TipoToken.MAXIMO, lexema, linea);
                        case "SUMA"     -> agregar(TipoToken.SUMA, lexema, linea);
                        case "AMPLITUD" -> agregar(TipoToken.AMPLITUD, lexema, linea);
                        case "FRECUENCIA" -> agregar(TipoToken.FRECUENCIA, lexema, linea);
                        case "VENTANA"  -> agregar(TipoToken.VENTANA, lexema, linea);
                        case "CON"      -> agregar(TipoToken.CON, lexema, linea);
                        default -> agregar(TipoToken.ID, lexema, linea);
                    }
                }
            }
        } else if (estado == Q_NUM || estado == Q_NUM_DEC) {
            agregar(TipoToken.NUMERO, lexema, linea);
        }
    }

    void emitirTokenDirecto(int clase, String lexema, int linea) {
        switch (clase) {
            case MAS  -> agregar(TipoToken.MAS, lexema, linea);
            case MENOS-> agregar(TipoToken.MENOS, lexema, linea);
            case POR  -> agregar(TipoToken.POR, lexema, linea);
            case PUNTOCOMA -> agregar(TipoToken.PUNTO_COMA, lexema, linea);
            case PIZQ -> agregar(TipoToken.PAREN_IZQ, lexema, linea);
            case PDER -> agregar(TipoToken.PAREN_DER, lexema, linea);
            case COMA_ -> agregar(TipoToken.COMA, lexema, linea);
            case ESP, NL -> {}  // espacios y saltos de línea se ignoran
            default -> { /* no debería ocurrir */ }
        }
    }

    void emitirTokenCompuesto(int estadoOrigen, int clase, String lexema, int linea) {
        if (estadoOrigen == Q_EQ && clase == IGUAL) {
            agregar(TipoToken.IGUAL_IGUAL, lexema, linea);
        } else if (estadoOrigen == Q_GT && clase == IGUAL) {
            agregar(TipoToken.MAYOR_IGUAL, lexema, linea);
        } else if (estadoOrigen == Q_LT) {
            if (clase == IGUAL) {
                agregar(TipoToken.MENOR_IGUAL, lexema, linea);
            } else if (clase == MAYOR_) {
                agregar(TipoToken.DIFERENTE, lexema, linea);
            }
        } else if (estadoOrigen == Q_NOT && clase == IGUAL) {
            agregar(TipoToken.DIFERENTE, lexema, linea);
        }
    }

    void emitirTokenUnario(int estado, String lexema, int linea) {
        switch (estado) {
            case Q_EQ  -> agregar(TipoToken.ASIGNACION, lexema, linea);
            case Q_GT  -> agregar(TipoToken.MAYOR, lexema, linea);
            case Q_LT  -> agregar(TipoToken.MENOR, lexema, linea);
            case Q_DIV -> agregar(TipoToken.DIV, lexema, linea);
            case Q_NOT -> erroresLexicos.add("Error léxico en línea " + linea
                + ": '!' debe ir seguido de '=' para formar !=");
        }
    }

    // ---- 9. MÉTODOS AUXILIARES ─────────────────────────────────
    char ver() {
        return fuente.charAt(actual);
    }

    char avanzar() {
        return fuente.charAt(actual++);
    }

    boolean fin() {
        return actual >= fuente.length();
    }

    void agregar(TipoToken tipo, String lexema, int linea) {
        tokens.add(new Token(tipo, lexema, linea));
    }
}
