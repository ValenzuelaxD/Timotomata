package timotomata.lexer;

import java.util.*;

// Alfabeto  : 20 clases de caracteres (LETRA, DIGITO, etc.)
// Estados   : 14 estados (Q0 inicial, Q_ID, Q_NUM, ..., Q_CADENA)
// Transición: TABLA_TRANS[estado][clase] - siguiente estado

public class Lexer {

    //  1. ALFABETO (clases de caracteres) 
    public static final int
        LETRA = 0,  DIGITO = 1,  PUNTO = 2,
        ESP = 3,    NL = 4,
        MAS = 5,    MENOS = 6,   POR = 7,      DIV = 8,
        IGUAL = 9,  MAYOR_ = 10, MENOR_ = 11,   EXCL = 12,
        PUNTOCOMA = 13, PIZQ = 14, PDER = 15, COMA_ = 16, 
        LIZQ = 17,  LDER = 18,   COMILLAS = 19, OTRO = 20;
    public static final int NUM_CLASES = 21;

    //  2. ESTADOS DEL AFD 
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
        Q_COM_BLOQ_FIN = 12,// Vimos '*' dentro de /*, esperando '/' para cerrar
        Q_CADENA = 13;      // Dentro de literal de cadena "..."
    public static final int NUM_ESTADOS = 14;
    public static final int SIN_TRANS = -1;

    //  3. FUNCIÓN DE TRANSICIÓN 
    public static final int[][] TABLA_TRANS = new int[NUM_ESTADOS][NUM_CLASES];

    //  4. ESTADOS DE ACEPTACIÓN 
    public static final boolean[] ES_ACEPTACION = new boolean[NUM_ESTADOS];

    static {
        // Inicializar todo a SIN_TRANS
        for (int[] fila : TABLA_TRANS) Arrays.fill(fila, SIN_TRANS);

        // Transiciones desde Q0 (estado inicial)
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
        TABLA_TRANS[Q0][LIZQ]     = Q0;      // token directo ({)
        TABLA_TRANS[Q0][LDER]     = Q0;      // token directo (})
        TABLA_TRANS[Q0][COMILLAS] = Q_CADENA;// inicia cadena (")

        //  Transiciones desde Q_ID 
        TABLA_TRANS[Q_ID][LETRA]  = Q_ID;
        TABLA_TRANS[Q_ID][DIGITO] = Q_ID;
        ES_ACEPTACION[Q_ID] = true;

        //  Transiciones desde Q_NUM (parte entera) 
        TABLA_TRANS[Q_NUM][DIGITO] = Q_NUM;
        TABLA_TRANS[Q_NUM][PUNTO]  = Q_NUM_PUNTO;
        ES_ACEPTACION[Q_NUM] = true;

        //  Transiciones desde Q_NUM_PUNTO (acabamos de ver .) 
        TABLA_TRANS[Q_NUM_PUNTO][DIGITO] = Q_NUM_DEC;

        //  Transiciones desde Q_NUM_DEC (parte decimal) 
        TABLA_TRANS[Q_NUM_DEC][DIGITO] = Q_NUM_DEC;
        ES_ACEPTACION[Q_NUM_DEC] = true;

        //  Transiciones desde Q_EQ (vimos =) 
        TABLA_TRANS[Q_EQ][IGUAL] = Q0;   // ==   → token IGUAL_IGUAL

        //  Transiciones desde Q_GT (vimos >) 
        TABLA_TRANS[Q_GT][IGUAL] = Q0;   // >=   → token MAYOR_IGUAL

        //  Transiciones desde Q_LT (vimos <) 
        TABLA_TRANS[Q_LT][IGUAL] = Q0;   // <=   → token MENOR_IGUAL
        TABLA_TRANS[Q_LT][MAYOR_] = Q0;  // <>   → token DIFERENTE

        //  Transiciones desde Q_NOT (vimos !) 
        TABLA_TRANS[Q_NOT][IGUAL] = Q0;  // !=   → token DIFERENTE

        //  Transiciones desde Q_DIV (vimos /) 
        TABLA_TRANS[Q_DIV][DIV] = Q_COM_LINEA;  // //   → comentario de línea
        TABLA_TRANS[Q_DIV][POR] = Q_COM_BLOQ;    // /*   → comentario de bloque

        //  Transiciones desde Q_COM_LINEA (//) 
        for (int c = 0; c < NUM_CLASES; c++) {
            if (c != NL) TABLA_TRANS[Q_COM_LINEA][c] = Q_COM_LINEA;
        }
        TABLA_TRANS[Q_COM_LINEA][NL] = Q0;

        //  Transiciones desde Q_COM_BLOQ (/*) 
        for (int c = 0; c < NUM_CLASES; c++) {
            if (c != POR) TABLA_TRANS[Q_COM_BLOQ][c] = Q_COM_BLOQ;
        }
        TABLA_TRANS[Q_COM_BLOQ][POR] = Q_COM_BLOQ_FIN;

        //  Transiciones desde Q_COM_BLOQ_FIN (vimos * dentro de /*) 
        TABLA_TRANS[Q_COM_BLOQ_FIN][DIV] = Q0;           // */  → cierra comentario
        TABLA_TRANS[Q_COM_BLOQ_FIN][POR] = Q_COM_BLOQ_FIN; // **  → sigue esperando /
        for (int c = 0; c < NUM_CLASES; c++) {
            if (c != DIV && c != POR)
                TABLA_TRANS[Q_COM_BLOQ_FIN][c] = Q_COM_BLOQ; // vuelve a comentario
        }

        //  Transiciones desde Q_CADENA (") 
        for (int c = 0; c < NUM_CLASES; c++) {
            if (c != COMILLAS) TABLA_TRANS[Q_CADENA][c] = Q_CADENA;
        }
        TABLA_TRANS[Q_CADENA][COMILLAS] = Q0;
    }

    // ---- 5. ATRIBUTOS DEL LEXER ----
    String fuente;
    List<Token> tokens = new ArrayList<>();
    List<ErrorInfo> erroresLexicos = new ArrayList<>();
    int actual = 0;
    int linea = 1;
    int columna = 1;

    public Lexer(String fuente) {
        this.fuente = fuente;
    }

    public List<ErrorInfo> getErroresLexicos() {
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
            case '{' -> LIZQ;
            case '}' -> LDER;
            case '"' -> COMILLAS;
            default -> OTRO;
        };
    }

    // ---- 7. SIMULADOR DEL AFD 
    public List<Token> escanear() {
        while (!fin()) {
            int inicio = actual;
            int inicioLinea = linea;
            int inicioColumna = columna;
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
                // Actualizar línea y columna
                if (clase == NL) {
                    if (estado != Q_COM_LINEA) {
                        linea++;
                    }
                    columna = 1;
                } else {
                    columna++;
                }

                //  Caso 1: Transición a Q0 (token completado) 
                if (sigEstado == Q0 && estado != Q0) {
                    if (estado == Q_COM_BLOQ_FIN && clase == DIV) {
                        // */ — comentario de bloque cerrado
                        String lexema = fuente.substring(inicio, actual);
                        agregar(TipoToken.COMENTARIO, lexema, inicioLinea, inicioColumna);
                        inicio = actual;
                        estado = Q0;
                        break;
                    }
                    if (estado == Q_COM_LINEA && clase == NL) {
                        // // comentario — termina en nueva línea
                        String lexema = fuente.substring(inicio, actual - 1);
                        agregar(TipoToken.COMENTARIO, lexema, inicioLinea, inicioColumna);
                        linea++;
                        columna = 1;
                        inicio = actual;
                        estado = Q0;
                        break;
                    }
                    if (estado == Q_CADENA && clase == COMILLAS) {
                        // "cadena" — literal de cadena
                        String lexema = fuente.substring(inicio, actual);
                        agregar(TipoToken.CADENA, lexema, inicioLinea, inicioColumna);
                        inicio = actual;
                        estado = Q0;
                        break;
                    }
                    
                    // Solo llegamos aquí para ==, >=, <=, !=, <>
                    String lexema = fuente.substring(inicio, actual);
                    emitirTokenCompuesto(estado, clase, lexema, inicioLinea, inicioColumna);
                    inicio = actual;
                    inicioColumna = columna;
                    estado = Q0;
                    ultimoAcept = -1;
                    posUltimaAcept = -1;
                    break;
                }

                estado = sigEstado;

                //  Caso 2: Estado de aceptación 
                if (ES_ACEPTACION[estado]) {
                    ultimoAcept = estado;
                    posUltimaAcept = actual;
                }

                //  Caso 3: Token directo de 1 carácter 
                if (estado == Q0 && actual - inicio == 1) {
                    String lexema = fuente.substring(inicio, actual);
                    int claseInicial = clasificar(lexema.charAt(0));
                    emitirTokenDirecto(claseInicial, lexema, inicioLinea, inicioColumna);
                    inicio = actual;
                    inicioColumna = columna;
                    ultimoAcept = -1;
                    posUltimaAcept = -1;
                    break;
                }
            }

            //  Al salir del bucle 
            if (inicio == actual && !fin()) {
                char cActual = fuente.charAt(actual);
                int claseActual = clasificar(cActual);
                if (TABLA_TRANS[Q0][claseActual] == SIN_TRANS) {
                    String lexema = String.valueOf(cActual);
                    agregar(TipoToken.DESCONOCIDO, lexema, inicioLinea, inicioColumna);
                    erroresLexicos.add(new ErrorInfo(TablaErrores.L001, inicioLinea, inicioColumna, cActual));
                    columna = inicioColumna + 1;
                    actual++;
                }
            } else if (ultimoAcept != -1) {
                String lexema = fuente.substring(inicio, posUltimaAcept);
                emitirToken(ultimoAcept, lexema, inicioLinea, inicioColumna);
                actual = posUltimaAcept;
            } else if (estado == Q_EQ || estado == Q_GT || estado == Q_LT
                    || estado == Q_NOT || estado == Q_DIV) {
                String lexema = fuente.substring(inicio, inicio + 1);
                emitirTokenUnario(estado, lexema, inicioLinea, inicioColumna);
                columna = inicioColumna + 1;
                actual = inicio + 1;
            } else if (estado == Q_COM_LINEA) {
                // Comentario de línea al final del archivo
                String lexema = fuente.substring(inicio, actual);
                agregar(TipoToken.COMENTARIO, lexema, inicioLinea, inicioColumna);
            } else if (estado == Q_COM_BLOQ || estado == Q_COM_BLOQ_FIN) {
                String lexema = fuente.substring(inicio, actual);
                agregar(TipoToken.DESCONOCIDO, lexema, inicioLinea, inicioColumna);
                erroresLexicos.add(new ErrorInfo(TablaErrores.L002, inicioLinea, inicioColumna, inicioLinea));
                columna = inicioColumna + 1;
                actual = inicio + 1;
            } else if (estado == Q_CADENA) {
                String lexema = fuente.substring(inicio, actual);
                agregar(TipoToken.DESCONOCIDO, lexema, inicioLinea, inicioColumna);
                erroresLexicos.add(new ErrorInfo(TablaErrores.L003, inicioLinea, inicioColumna, inicioLinea));
                columna = inicioColumna + 1;
                actual = inicio + 1;
            } else if (actual > inicio) {
                String lexema = fuente.substring(inicio, actual);
                agregar(TipoToken.DESCONOCIDO, lexema, inicioLinea, inicioColumna);
                erroresLexicos.add(new ErrorInfo(TablaErrores.L004, inicioLinea, inicioColumna, lexema));
                columna = inicioColumna + 1;
                actual = inicio + 1;
            }
        }

        tokens.add(new Token(TipoToken.EOF, "", linea));
        return tokens;
    }

    // ---- 8. EMISIÓN DE TOKENS ----
    void emitirToken(int estado, String lexema, int linea, int columna) {
        if (estado == Q_ID) {
            String lexemaLower = lexema.toLowerCase();
            switch (lexemaLower) {
                case "sensor"     -> agregar(TipoToken.SENSOR, lexema, linea, columna);
                case "umbral"     -> agregar(TipoToken.UMBRAL, lexema, linea, columna);
                case "si"         -> agregar(TipoToken.SI, lexema, linea, columna);
                case "entonces"   -> agregar(TipoToken.ENTONCES, lexema, linea, columna);
                case "estado"     -> agregar(TipoToken.ESTADO, lexema, linea, columna);
                case "abs"        -> agregar(TipoToken.ABS, lexema, linea, columna);
                case "calcular"   -> agregar(TipoToken.CALCULAR, lexema, linea, columna);
                case "normal", "pico", "caida", "inestable" ->
                    agregar(TipoToken.ESTADO_SISTEMA, lexema, linea, columna);
                case "seno"       -> agregar(TipoToken.SENO, lexema, linea, columna);
                case "coseno"     -> agregar(TipoToken.COSENO, lexema, linea, columna);
                case "cuadrada"   -> agregar(TipoToken.CUADRADA, lexema, linea, columna);
                case "promedio"   -> agregar(TipoToken.PROMEDIO, lexema, linea, columna);
                case "maximo"     -> agregar(TipoToken.MAXIMO, lexema, linea, columna);
                case "suma"       -> agregar(TipoToken.SUMA, lexema, linea, columna);
                case "amplitud"   -> agregar(TipoToken.AMPLITUD, lexema, linea, columna);
                case "frecuencia" -> agregar(TipoToken.FRECUENCIA, lexema, linea, columna);
                case "ventana"    -> agregar(TipoToken.VENTANA, lexema, linea, columna);
                case "con"        -> agregar(TipoToken.CON, lexema, linea, columna);
                case "fin"        -> agregar(TipoToken.FIN, lexema, linea, columna);
                
                // Nuevas palabras reservadas
                case "tipo"       -> agregar(TipoToken.TIPO, lexema, linea, columna);
                case "electrico"  -> agregar(TipoToken.ELECTRICO, lexema, linea, columna);
                case "termico"    -> agregar(TipoToken.TERMICO, lexema, linea, columna);
                case "rango"      -> agregar(TipoToken.RANGO, lexema, linea, columna);
                case "minimo"     -> agregar(TipoToken.MINIMO, lexema, linea, columna);
                case "y"          -> agregar(TipoToken.Y, lexema, linea, columna);
                case "o"          -> agregar(TipoToken.O, lexema, linea, columna);
                case "alerta"     -> agregar(TipoToken.ALERTA, lexema, linea, columna);
                case "fluctuacion"-> agregar(TipoToken.FLUCTUACION, lexema, linea, columna);

                default -> {
                    agregar(TipoToken.ID, lexema, linea, columna);
                    detectarPalabraMalEscrita(lexema, linea, columna);
                }
            }
        } else if (estado == Q_NUM || estado == Q_NUM_DEC) {
            agregar(TipoToken.NUMERO, lexema, linea, columna);
        }
    }

    void emitirTokenDirecto(int clase, String lexema, int linea, int columna) {
        switch (clase) {
            case MAS  -> agregar(TipoToken.MAS, lexema, linea, columna);
            case MENOS-> agregar(TipoToken.MENOS, lexema, linea, columna);
            case POR  -> agregar(TipoToken.POR, lexema, linea, columna);
            case PUNTOCOMA -> agregar(TipoToken.PUNTO_COMA, lexema, linea, columna);
            case PIZQ -> agregar(TipoToken.PAREN_IZQ, lexema, linea, columna);
            case PDER -> agregar(TipoToken.PAREN_DER, lexema, linea, columna);
            case COMA_ -> agregar(TipoToken.COMA, lexema, linea, columna);
            case LIZQ -> agregar(TipoToken.LLAVE_IZQ, lexema, linea, columna);
            case LDER -> agregar(TipoToken.LLAVE_DER, lexema, linea, columna);
            case ESP, NL -> {}  // ignorar
            default -> { }
        }
    }

    void emitirTokenCompuesto(int estadoOrigen, int clase, String lexema, int linea, int columna) {
        if (estadoOrigen == Q_EQ && clase == IGUAL) {
            agregar(TipoToken.IGUAL_IGUAL, lexema, linea, columna);
        } else if (estadoOrigen == Q_GT && clase == IGUAL) {
            agregar(TipoToken.MAYOR_IGUAL, lexema, linea, columna);
        } else if (estadoOrigen == Q_LT) {
            if (clase == IGUAL) {
                agregar(TipoToken.MENOR_IGUAL, lexema, linea, columna);
            } else if (clase == MAYOR_) {
                agregar(TipoToken.DIFERENTE, lexema, linea, columna);
            }
        } else if (estadoOrigen == Q_NOT && clase == IGUAL) {
            agregar(TipoToken.DIFERENTE, lexema, linea, columna);
        }
    }

    void emitirTokenUnario(int estado, String lexema, int linea, int columna) {
        switch (estado) {
            case Q_EQ  -> agregar(TipoToken.ASIGNACION, lexema, linea, columna);
            case Q_GT  -> agregar(TipoToken.MAYOR, lexema, linea, columna);
            case Q_LT  -> agregar(TipoToken.MENOR, lexema, linea, columna);
            case Q_DIV -> agregar(TipoToken.DIV, lexema, linea, columna);
            case Q_NOT -> {
                agregar(TipoToken.DESCONOCIDO, lexema, linea, columna);
                erroresLexicos.add(new ErrorInfo(TablaErrores.L005, linea, columna));
            }
        }
    }

    // ---- 9. MÉTODOS AUXILIARES ----
    char ver() {
        return fuente.charAt(actual);
    }

    char avanzar() {
        return fuente.charAt(actual++);
    }

    boolean fin() {
        return actual >= fuente.length();
    }

    void agregar(TipoToken tipo, String lexema, int linea, int columna) {
        tokens.add(new Token(tipo, lexema, linea, columna));
    }

    // ---- 10. DETECCIÓN DE PALABRAS RESERVADAS MAL ESCRITAS ----
    // Lista de todas las palabras reservadas del lenguaje
    private static final String[] PALABRAS_RESERVADAS = {
        "sensor", "umbral", "rango", "si", "entonces",
        "calcular", "fin", "tipo", "minimo", "maximo",
        "estado", "alerta", "electrico", "termico",
        "abs", "seno", "coseno", "cuadrada", "promedio",
        "suma", "fluctuacion", "amplitud", "frecuencia",
        "ventana", "con", "y", "o",
        "normal", "pico", "caida", "inestable"
    };

    /**
     * Detección de palabras no reconocidas que se parecen a palabras reservadas.
     *
     * Estrategias:
     * 1. LEVENSHTEIN: Calcula la distancia de edición a TODAS las palabras reservadas
     *    y encuentra la más cercana. Usa un umbral dinámico según la longitud:
     *    - Palabras cortas (2-4 chars): distancia ≤ 1
     *    - Palabras medianas (5-7 chars): distancia ≤ 2
     *    - Palabras largas (8+ chars): distancia ≤ 3
     *    Si hay match → L006 con sugerencia "¿Quiso decir 'X'?"
     * 2. PREFIJO: Si el ID es inicio de una palabra reservada (incompleta)
     *    Ej: 'calcu' → 'calcular'
     *
     * Si no se encuentra ninguna coincidencia cercana, igual se reporta
     * un error L007: "Palabra '%s' no reconocida en el lenguaje".
     */
    private void detectarPalabraMalEscrita(String lexema, int linea, int columna) {
        String lower = lexema.toLowerCase();
        if (lower.length() > 15 || lower.length() < 2) return;

        String mejorSugerencia = null;
        int mejorDistancia = Integer.MAX_VALUE;

        // ── Calcular Levenshtein a TODAS las palabras reservadas ──
        for (String reserva : PALABRAS_RESERVADAS) {
            int dist = levenshtein(lower, reserva);
            if (dist < mejorDistancia) {
                mejorDistancia = dist;
                mejorSugerencia = reserva;
            }
        }

        // ── Verificar si es prefijo de alguna palabra (más prioritario) ──
        String mejorPrefijo = null;
        int mejorSobra = Integer.MAX_VALUE;
        if (lower.length() >= 3) {
            for (String reserva : PALABRAS_RESERVADAS) {
                if (reserva.startsWith(lower)) {
                    int sobra = reserva.length() - lower.length();
                    if (sobra >= 1 && sobra <= 5 && sobra < mejorSobra) {
                        mejorSobra = sobra;
                        mejorPrefijo = reserva;
                    }
                }
            }
        }

        // ── Determinar umbral dinámico según longitud ──
        int umbral;
        if (lower.length() == 2) {
            umbral = 2;        // Palabras de 2 letras: permite transposición ('is' → 'si')
        } else if (lower.length() <= 4) {
            umbral = 1;        // Palabras muy cortas: solo 1 error
        } else if (lower.length() <= 7) {
            umbral = 2;        // Palabras medianas: hasta 2 errores
        } else {
            umbral = 3;        // Palabras largas: hasta 3 errores
        }

        // Si hay prefijo, tiene prioridad sobre Levenshtein
        if (mejorPrefijo != null) {
            erroresLexicos.add(new ErrorInfo(
                TablaErrores.L006, linea, columna,
                mejorPrefijo, lexema));
            if (!tokens.isEmpty()) {
                tokens.get(tokens.size() - 1).tieneSugerencia = true;
            }
            return;
        }

        // Si Levenshtein encontró una coincidencia cercana → sugerencia
        if (mejorDistancia >= 1 && mejorDistancia <= umbral) {
            // Verificar coincidencia adicional: primera letra igual o distancia muy baja
            boolean primeraLetraOk = lower.charAt(0) == mejorSugerencia.charAt(0);
            boolean muyCercana = mejorDistancia <= 1;
            boolean esTransposicion = mejorDistancia == 2 
                && lower.length() == mejorSugerencia.length()
                && lower.charAt(0) == mejorSugerencia.charAt(1)
                && lower.charAt(1) == mejorSugerencia.charAt(0);

            if (primeraLetraOk || muyCercana || esTransposicion) {
                erroresLexicos.add(new ErrorInfo(
                    TablaErrores.L006, linea, columna,
                    mejorSugerencia, lexema));
                if (!tokens.isEmpty()) {
                    tokens.get(tokens.size() - 1).tieneSugerencia = true;
                }
                return;
            }
        }

        // ── No se encontró coincidencia cercana: es un nombre de variable válido ──
        // No se agrega error — los identificadores no reservados son nombres de variable válidos
    }

    /** Distancia de Levenshtein (edición mínima) entre dos strings. */
    private static int levenshtein(String a, String b) {
        int m = a.length(), n = b.length();
        int[][] dp = new int[m + 1][n + 1];
        for (int i = 0; i <= m; i++) dp[i][0] = i;
        for (int j = 0; j <= n; j++) dp[0][j] = j;
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                int costo = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(
                    Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + costo);
            }
        }
        return dp[m][n];
    }
}
