package timotomata.parser;

import java.util.*;
import timotomata.lexer.TipoToken;

/**
 * GRAMÁTICA FORMAL DEL LENGUAJE TIMOTOMATA
 * ==========================================
 *
 * Notación BNF:
 *
 *   PROGRAMA      → SENTENCIA PROGRAMA
 *                 | ε
 *
 *   SENTENCIA     → SENSOR ID PUNTO_COMA
 *                 | UMBRAL ID ASIGNACION VALOR_UMBRAL PUNTO_COMA
 *                 | SI CONDICION ENTONCES ESTADO ASIGNACION ESTADO_SISTEMA PUNTO_COMA
 *                 | CALCULAR PAREN_IZQ ID COMA TIPO_OP PAREN_DER PUNTO_COMA
 *
 *   VALOR_UMBRAL  → MENOS NUMERO
 *                 | NUMERO
 *
 *   CONDICION     → EXPRESION OP_REL EXPRESION
 *
 *   EXPRESION     → TERMINO EXPRESION_SIG
 *   EXPRESION_SIG → MAS TERMINO EXPRESION_SIG
 *                 | MENOS TERMINO EXPRESION_SIG
 *                 | ε
 *
 *   TERMINO       → FACTOR TERMINO_SIG
 *   TERMINO_SIG   → POR FACTOR TERMINO_SIG
 *                 | DIV FACTOR TERMINO_SIG
 *                 | ε
 *
 *   FACTOR        → NUMERO
 *                 | ID
 *                 | ABS PAREN_IZQ EXPRESION PAREN_DER
 *                 | MENOS FACTOR
 *
 *   OP_REL        → MAYOR | MENOR | IGUAL_IGUAL
 *                 | MAYOR_IGUAL | MENOR_IGUAL | DIFERENTE
 *
 *   TIPO_OP       → SENO PAREN_IZQ LISTA_PARAMS PAREN_DER
 *                 | COSENO PAREN_IZQ LISTA_PARAMS PAREN_DER
 *                 | CUADRADA PAREN_IZQ LISTA_PARAMS PAREN_DER
 *                 | PROMEDIO PAREN_IZQ LISTA_PARAMS PAREN_DER
 *                 | MAXIMO PAREN_IZQ LISTA_PARAMS PAREN_DER
 *                 | SUMA PAREN_IZQ LISTA_PARAMS PAREN_DER
 *
 *   LISTA_PARAMS  → PARAM LISTA_PARAMS_SIG
 *                 | ε
 *
 *   LISTA_PARAMS_SIG → COMA LISTA_PARAMS
 *                      | ε
 *
 *   PARAM         → AMPLITUD ASIGNACION NUMERO
 *                 | FRECUENCIA ASIGNACION NUMERO
 *                 | VENTANA ASIGNACION NUMERO
 *                 | CON ASIGNACION ID
 */
public class Gramatica {

    
    //  IDs DE NO TERMINALES
    
    public static final int
        PROGRAMA      = 0,
        SENTENCIA     = 1,
        VALOR_UMBRAL  = 2,
        CONDICION     = 3,
        EXPRESION     = 4,
        EXPRESION_SIG = 5,
        TERMINO       = 6,
        TERMINO_SIG   = 7,
        FACTOR        = 8,
        OP_REL        = 9,
        TIPO_OP       = 10,
        LISTA_PARAMS  = 11,
        PARAM         = 12,
        LISTA_PARAMS_SIG = 13;

    public static final int NUM_NT = 14;

    public static final String[] NOMBRES_NT = {
        "PROGRAMA", "SENTENCIA", "VALOR_UMBRAL",
        "CONDICION", "EXPRESION", "EXPRESION_SIG",
        "TERMINO", "TERMINO_SIG", "FACTOR", "OP_REL",
        "TIPO_OP", "LISTA_PARAMS", "PARAM", "LISTA_PARAMS_SIG"
    };

    
    //  IDs DE PRODUCCIONES
    
    public static final int
        P_PROGRAMA_REC    = 0,
        P_PROGRAMA_EPS    = 1,
        P_SENT_SENSOR     = 2,
        P_SENT_UMBRAL     = 3,
        P_SENT_SI         = 4,
        P_SENT_CALCULAR   = 5,
        P_VALOR_NEG       = 6,
        P_VALOR_POS       = 7,
        P_COND            = 8,
        P_EXPR            = 9,
        P_EXPR_SIG_MAS    = 10,
        P_EXPR_SIG_MENOS  = 11,
        P_EXPR_SIG_EPS    = 12,
        P_TERM            = 13,
        P_TERM_SIG_POR    = 14,
        P_TERM_SIG_DIV    = 15,
        P_TERM_SIG_EPS    = 16,
        P_FACT_NUM        = 17,
        P_FACT_ID         = 18,
        P_FACT_ABS        = 19,
        P_FACT_MENOS      = 20,
        P_OP_MAYOR        = 21,
        P_OP_MENOR        = 22,
        P_OP_IGUAL        = 23,
        P_OP_MAYIG        = 24,
        P_OP_MENIG        = 25,
        P_OP_DIF          = 26,
        P_TIPO_SENO       = 27,
        P_TIPO_COSENO     = 28,
        P_TIPO_CUADRADA   = 29,
        P_TIPO_PROMEDIO   = 30,
        P_TIPO_MAXIMO     = 31,
        P_TIPO_SUMA       = 32,
        P_PARAMS_PARAM    = 33,
        P_PARAMS_EPS      = 34,
        P_PARAMS_SIG_COMA = 35,
        P_PARAMS_SIG_EPS  = 36,
        P_PARAM_AMPL      = 37,
        P_PARAM_FREC      = 38,
        P_PARAM_VENT      = 39,
        P_PARAM_CON       = 40;

    public static final int NUM_PROD = 41;

    
    //  CODIFICACIÓN DE SÍMBOLOS
    
    // Terminal: ordinal de TipoToken (0..22)
    // No terminal: -(id + 1) → valores negativos (-1..-13)
    static int T(TipoToken t) { return t.ordinal(); }
    static int NT(int nt)     { return -(nt + 1); }
    static boolean esTerminal(int sym)   { return sym >= 0; }
    static int idNoTerminal(int sym)     { return -sym - 1; }

    
    //  PRODUCCIONES
    
    // Cada producción: { cabeza_nt_codificado, sym1, sym2, ... }
    public static final int[][] PRODUCCIONES = {
        /*  0 */ { NT(PROGRAMA),     NT(SENTENCIA), NT(PROGRAMA) },
        /*  1 */ { NT(PROGRAMA) },                                    // ε

        /*  2 */ { NT(SENTENCIA),    T(TipoToken.SENSOR),       T(TipoToken.ID),
                                       T(TipoToken.PUNTO_COMA) },
        /*  3 */ { NT(SENTENCIA),    T(TipoToken.UMBRAL),       T(TipoToken.ID),
                                       T(TipoToken.ASIGNACION),  NT(VALOR_UMBRAL),
                                       T(TipoToken.PUNTO_COMA) },
        /*  4 */ { NT(SENTENCIA),    T(TipoToken.SI),           NT(CONDICION),
                                       T(TipoToken.ENTONCES),    T(TipoToken.ESTADO),
                                       T(TipoToken.ASIGNACION),  T(TipoToken.ESTADO_SISTEMA),
                                       T(TipoToken.PUNTO_COMA) },
        /*  5 */ { NT(SENTENCIA),    T(TipoToken.CALCULAR),     T(TipoToken.PAREN_IZQ),
                                       T(TipoToken.ID),          T(TipoToken.COMA),
                                       NT(TIPO_OP),
                                       T(TipoToken.PAREN_DER),   T(TipoToken.PUNTO_COMA) },

        /*  6 */ { NT(VALOR_UMBRAL), T(TipoToken.MENOS),       T(TipoToken.NUMERO) },
        /*  7 */ { NT(VALOR_UMBRAL), T(TipoToken.NUMERO) },

        /*  8 */ { NT(CONDICION),    NT(EXPRESION), NT(OP_REL), NT(EXPRESION) },

        /*  9 */ { NT(EXPRESION),    NT(TERMINO), NT(EXPRESION_SIG) },
        /* 10 */ { NT(EXPRESION_SIG), T(TipoToken.MAS),         NT(TERMINO),
                                       NT(EXPRESION_SIG) },
        /* 11 */ { NT(EXPRESION_SIG), T(TipoToken.MENOS),       NT(TERMINO),
                                       NT(EXPRESION_SIG) },
        /* 12 */ { NT(EXPRESION_SIG) },                              // ε

        /* 13 */ { NT(TERMINO),      NT(FACTOR), NT(TERMINO_SIG) },
        /* 14 */ { NT(TERMINO_SIG),  T(TipoToken.POR),          NT(FACTOR),
                                       NT(TERMINO_SIG) },
        /* 15 */ { NT(TERMINO_SIG),  T(TipoToken.DIV),          NT(FACTOR),
                                       NT(TERMINO_SIG) },
        /* 16 */ { NT(TERMINO_SIG) },                               // ε

        /* 17 */ { NT(FACTOR),       T(TipoToken.NUMERO) },
        /* 18 */ { NT(FACTOR),       T(TipoToken.ID) },
        /* 19 */ { NT(FACTOR),       T(TipoToken.ABS),          T(TipoToken.PAREN_IZQ),
                                       NT(EXPRESION),            T(TipoToken.PAREN_DER) },
        /* 20 */ { NT(FACTOR),       T(TipoToken.MENOS),        NT(FACTOR) },

        /* 21 */ { NT(OP_REL),       T(TipoToken.MAYOR) },
        /* 22 */ { NT(OP_REL),       T(TipoToken.MENOR) },
        /* 23 */ { NT(OP_REL),       T(TipoToken.IGUAL_IGUAL) },
        /* 24 */ { NT(OP_REL),       T(TipoToken.MAYOR_IGUAL) },
        /* 25 */ { NT(OP_REL),       T(TipoToken.MENOR_IGUAL) },
        /* 26 */ { NT(OP_REL),       T(TipoToken.DIFERENTE) },

        /* 27 */ { NT(TIPO_OP),      T(TipoToken.SENO),        T(TipoToken.PAREN_IZQ),
                                       NT(LISTA_PARAMS),         T(TipoToken.PAREN_DER) },
        /* 28 */ { NT(TIPO_OP),      T(TipoToken.COSENO),      T(TipoToken.PAREN_IZQ),
                                       NT(LISTA_PARAMS),         T(TipoToken.PAREN_DER) },
        /* 29 */ { NT(TIPO_OP),      T(TipoToken.CUADRADA),    T(TipoToken.PAREN_IZQ),
                                       NT(LISTA_PARAMS),         T(TipoToken.PAREN_DER) },
        /* 30 */ { NT(TIPO_OP),      T(TipoToken.PROMEDIO),    T(TipoToken.PAREN_IZQ),
                                       NT(LISTA_PARAMS),         T(TipoToken.PAREN_DER) },
        /* 31 */ { NT(TIPO_OP),      T(TipoToken.MAXIMO),      T(TipoToken.PAREN_IZQ),
                                       NT(LISTA_PARAMS),         T(TipoToken.PAREN_DER) },
        /* 32 */ { NT(TIPO_OP),      T(TipoToken.SUMA),        T(TipoToken.PAREN_IZQ),
                                       NT(LISTA_PARAMS),         T(TipoToken.PAREN_DER) },

        /* 33 */ { NT(LISTA_PARAMS), NT(PARAM),                NT(LISTA_PARAMS_SIG) },
        /* 34 */ { NT(LISTA_PARAMS) },                               // ε

        /* 35 */ { NT(LISTA_PARAMS_SIG), T(TipoToken.COMA),    NT(LISTA_PARAMS) },
        /* 36 */ { NT(LISTA_PARAMS_SIG) },                          // ε

        /* 37 */ { NT(PARAM),        T(TipoToken.AMPLITUD),    T(TipoToken.ASIGNACION),
                                       T(TipoToken.NUMERO) },
        /* 38 */ { NT(PARAM),        T(TipoToken.FRECUENCIA),  T(TipoToken.ASIGNACION),
                                       T(TipoToken.NUMERO) },
        /* 39 */ { NT(PARAM),        T(TipoToken.VENTANA),     T(TipoToken.ASIGNACION),
                                       T(TipoToken.NUMERO) },
        /* 40 */ { NT(PARAM),        T(TipoToken.CON),         T(TipoToken.ASIGNACION),
                                       T(TipoToken.ID) },
    };

    
    //  FIRST y FOLLOW SETS
    
    public static final BitSet[] FIRST = new BitSet[NUM_NT];
    public static final boolean[] FIRST_EPS = new boolean[NUM_NT];
    public static final BitSet[] FOLLOW = new BitSet[NUM_NT];

    
    //  TABLA DE PARSING LL(1)
    
    public static final int[][] TABLA = new int[NUM_NT][TipoToken.values().length];

    static {
        for (int i = 0; i < NUM_NT; i++) {
            FIRST[i] = new BitSet();
            FOLLOW[i] = new BitSet();
            Arrays.fill(TABLA[i], -1);
        }
        calcularFIRST();
        calcularFOLLOW();
        construirTabla();
    }

    
    //  CÁLCULO DE FIRST
    
    static void calcularFIRST() {
        boolean cambios;
        do {
            cambios = false;
            for (int[] prod : PRODUCCIONES) {
                int cabeza = idNoTerminal(prod[0]);
                boolean todoEpsilon = true;

                for (int i = 1; i < prod.length; i++) {
                    int sym = prod[i];
                    if (esTerminal(sym)) {
                        if (!FIRST[cabeza].get(sym)) {
                            FIRST[cabeza].set(sym);
                            cambios = true;
                        }
                        todoEpsilon = false;
                        break;
                    } else {
                        int nt = idNoTerminal(sym);
                        for (int t = FIRST[nt].nextSetBit(0); t >= 0;
                                t = FIRST[nt].nextSetBit(t + 1)) {
                            if (!FIRST[cabeza].get(t)) {
                                FIRST[cabeza].set(t);
                                cambios = true;
                            }
                        }
                        if (!FIRST_EPS[nt]) {
                            todoEpsilon = false;
                            break;
                        }
                    }
                }

                if (todoEpsilon && !FIRST_EPS[cabeza]) {
                    FIRST_EPS[cabeza] = true;
                    cambios = true;
                }
            }
        } while (cambios);
    }

    
    //  CÁLCULO DE FOLLOW
    
    static void calcularFOLLOW() {
        FOLLOW[PROGRAMA].set(TipoToken.EOF.ordinal());

        boolean cambios;
        do {
            cambios = false;
            for (int[] prod : PRODUCCIONES) {
                int cabeza = idNoTerminal(prod[0]);

                for (int i = 1; i < prod.length; i++) {
                    int sym = prod[i];
                    if (esNoTerminal(sym)) {
                        int nt = idNoTerminal(sym);

                        BitSet firstBeta = new BitSet();
                        boolean betaEpsilon = true;
                        for (int j = i + 1; j < prod.length; j++) {
                            int s = prod[j];
                            if (esTerminal(s)) {
                                firstBeta.set(s);
                                betaEpsilon = false;
                                break;
                            } else {
                                int snt = idNoTerminal(s);
                                for (int t = FIRST[snt].nextSetBit(0); t >= 0;
                                        t = FIRST[snt].nextSetBit(t + 1)) {
                                    firstBeta.set(t);
                                }
                                if (!FIRST_EPS[snt]) {
                                    betaEpsilon = false;
                                    break;
                                }
                            }
                        }

                        for (int t = firstBeta.nextSetBit(0); t >= 0;
                                t = firstBeta.nextSetBit(t + 1)) {
                            if (!FOLLOW[nt].get(t)) {
                                FOLLOW[nt].set(t);
                                cambios = true;
                            }
                        }

                        if (betaEpsilon) {
                            for (int t = FOLLOW[cabeza].nextSetBit(0); t >= 0;
                                    t = FOLLOW[cabeza].nextSetBit(t + 1)) {
                                if (!FOLLOW[nt].get(t)) {
                                    FOLLOW[nt].set(t);
                                    cambios = true;
                                }
                            }
                        }
                    }
                }
            }
        } while (cambios);
    }

    
    //  CONSTRUCCIÓN DE LA TABLA LL(1)
    
    static void construirTabla() {
        for (int p = 0; p < PRODUCCIONES.length; p++) {
            int[] prod = PRODUCCIONES[p];
            int cabeza = idNoTerminal(prod[0]);

            BitSet firstCuerpo = new BitSet();
            boolean cuerpoEpsilon = true;
            for (int i = 1; i < prod.length; i++) {
                int sym = prod[i];
                if (esTerminal(sym)) {
                    firstCuerpo.set(sym);
                    cuerpoEpsilon = false;
                    break;
                } else {
                    int nt = idNoTerminal(sym);
                    for (int t = FIRST[nt].nextSetBit(0); t >= 0;
                            t = FIRST[nt].nextSetBit(t + 1)) {
                        firstCuerpo.set(t);
                    }
                    if (!FIRST_EPS[nt]) {
                        cuerpoEpsilon = false;
                        break;
                    }
                }
            }

            for (int t = firstCuerpo.nextSetBit(0); t >= 0;
                    t = firstCuerpo.nextSetBit(t + 1)) {
                TABLA[cabeza][t] = p;
            }

            if (cuerpoEpsilon) {
                for (int t = FOLLOW[cabeza].nextSetBit(0); t >= 0;
                        t = FOLLOW[cabeza].nextSetBit(t + 1)) {
                    TABLA[cabeza][t] = p;
                }
            }
        }
    }

    
    //  CONSULTA
    
    public static int obtenerProduccion(int noTerminal, TipoToken lookahead) {
        return TABLA[noTerminal][lookahead.ordinal()];
    }

    public static String nombreProduccion(int prodId) {
        if (prodId < 0 || prodId >= PRODUCCIONES.length) return "???";
        int[] prod = PRODUCCIONES[prodId];
        StringBuilder sb = new StringBuilder(NOMBRES_NT[idNoTerminal(prod[0])] + " →");
        if (prod.length == 1) {
            sb.append(" ε");
        } else {
            for (int i = 1; i < prod.length; i++) {
                int sym = prod[i];
                if (esTerminal(sym)) {
                    sb.append(" ").append(TipoToken.values()[sym]);
                } else {
                    sb.append(" ").append(NOMBRES_NT[idNoTerminal(sym)]);
                }
            }
        }
        return sb.toString();
    }

    static boolean esNoTerminal(int sym) { return sym < 0; }
}
