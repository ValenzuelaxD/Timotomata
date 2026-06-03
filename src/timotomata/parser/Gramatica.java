package timotomata.parser;

import java.util.*;
import timotomata.lexer.TipoToken;

/**
 * GRAMÁTICA FORMAL DEL LENGUAJE TIMOTOMATA (EXTENDIDA)
 * ====================================================
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
        LISTA_PARAMS_SIG = 13,
        TIPO_OPC      = 14,
        TIPO_VAL      = 15,
        AUX_CALCULAR  = 16,
        FUNC_ANALISIS = 17,
        PARAMS_ANALISIS = 18,
        CONSECUENCIA  = 19,
        ACCION        = 20,
        ACCIONES      = 21,
        ACCIONES_REST = 22,
        COND_SIMPLE   = 23,
        COND_COMPUESTA = 24,
        LOG_OP        = 25;

    public static final int NUM_NT = 26;

    public static final String[] NOMBRES_NT = {
        "PROGRAMA", "SENTENCIA", "VALOR_UMBRAL",
        "CONDICION", "EXPRESION", "EXPRESION_SIG",
        "TERMINO", "TERMINO_SIG", "FACTOR", "OP_REL",
        "TIPO_OP", "LISTA_PARAMS", "PARAM", "LISTA_PARAMS_SIG",
        "TIPO_OPC", "TIPO_VAL", "AUX_CALCULAR", "FUNC_ANALISIS",
        "PARAMS_ANALISIS", "CONSECUENCIA", "ACCION", "ACCIONES",
        "ACCIONES_REST", "COND_SIMPLE", "COND_COMPUESTA", "LOG_OP"
    };

    //  IDs DE PRODUCCIONES (Mantenemos los antiguos iguales para compatibilidad)
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
        P_PARAM_CON       = 40,
        P_SENT_FIN        = 41;

    // Nuevas producciones
    public static final int
        P_SENT_RANGO       = 42,
        P_TIPO_OPC_TIPO    = 43,
        P_TIPO_OPC_EPS     = 44,
        P_TIPO_VAL_ELEC    = 45,
        P_TIPO_VAL_TERM    = 46,
        P_AUX_CALCULAR_OLD = 47,
        P_AUX_CALCULAR_NEW = 48,
        P_FUNC_PROM        = 49,
        P_FUNC_MAX         = 50,
        P_FUNC_FLUC        = 51,
        P_PARAMS_AN_COMA   = 52,
        P_PARAMS_AN_EPS    = 53,
        P_CONSEC_ACCION    = 54,
        P_CONSEC_BLOQUE    = 55,
        P_ACCION_ESTADO    = 56,
        P_ACCION_ALERTA    = 57,
        P_ACCIONES_REC     = 58,
        P_ACC_REST_REC     = 59,
        P_ACC_REST_EPS     = 60,
        P_COND_SIMPLE      = 61,
        P_COND_COMP_LOG    = 62,
        P_COND_COMP_EPS    = 63,
        P_LOG_Y            = 64,
        P_LOG_O            = 65;

    public static final int NUM_PROD = 66;

    static int T(TipoToken t) { return t.ordinal(); }
    static int NT(int nt)     { return -(nt + 1); }
    static boolean esTerminal(int sym)   { return sym >= 0; }
    static int idNoTerminal(int sym)     { return -sym - 1; }

    public static final int[][] PRODUCCIONES = {
        /*  0 */ { NT(PROGRAMA),     NT(SENTENCIA), NT(PROGRAMA) },
        /*  1 */ { NT(PROGRAMA) },                                    // ε

        // Modificadas para soportar la gramática extendida
        /*  2 */ { NT(SENTENCIA),    T(TipoToken.SENSOR),       T(TipoToken.ID), NT(TIPO_OPC), T(TipoToken.PUNTO_COMA) },
        /*  3 */ { NT(SENTENCIA),    T(TipoToken.UMBRAL),       T(TipoToken.ID),
                                       T(TipoToken.ASIGNACION),  NT(VALOR_UMBRAL),
                                       T(TipoToken.PUNTO_COMA) },
        /*  4 */ { NT(SENTENCIA),    T(TipoToken.SI),           NT(CONDICION),
                                       T(TipoToken.ENTONCES),    NT(CONSECUENCIA) },
        /*  5 */ { NT(SENTENCIA),    T(TipoToken.CALCULAR),     NT(AUX_CALCULAR) },

        /*  6 */ { NT(VALOR_UMBRAL), T(TipoToken.MENOS),       T(TipoToken.NUMERO) },
        /*  7 */ { NT(VALOR_UMBRAL), T(TipoToken.NUMERO) },

        // Modificado para soportar lógica compuesta
        /*  8 */ { NT(CONDICION),    NT(COND_SIMPLE), NT(COND_COMPUESTA) },

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

        /* 41 */ { NT(SENTENCIA),    T(TipoToken.FIN),         T(TipoToken.PUNTO_COMA) },

        // Nuevas producciones del plan
        /* 42 */ { NT(SENTENCIA),    T(TipoToken.RANGO), T(TipoToken.ID), T(TipoToken.MINIMO),
                                       T(TipoToken.ASIGNACION), T(TipoToken.NUMERO),
                                       T(TipoToken.MAXIMO), T(TipoToken.ASIGNACION), T(TipoToken.NUMERO), T(TipoToken.PUNTO_COMA) },
        /* 43 */ { NT(TIPO_OPC),     T(TipoToken.TIPO), NT(TIPO_VAL) },
        /* 44 */ { NT(TIPO_OPC) },                                    // ε
        /* 45 */ { NT(TIPO_VAL),     T(TipoToken.ELECTRICO) },
        /* 46 */ { NT(TIPO_VAL),     T(TipoToken.TERMICO) },

        /* 47 */ { NT(AUX_CALCULAR), T(TipoToken.PAREN_IZQ), T(TipoToken.ID), T(TipoToken.COMA), NT(TIPO_OP), T(TipoToken.PAREN_DER), T(TipoToken.PUNTO_COMA) },
        /* 48 */ { NT(AUX_CALCULAR), NT(FUNC_ANALISIS), T(TipoToken.PAREN_IZQ), T(TipoToken.ID), NT(PARAMS_ANALISIS), T(TipoToken.PAREN_DER), T(TipoToken.PUNTO_COMA) },

        /* 49 */ { NT(FUNC_ANALISIS), T(TipoToken.PROMEDIO) },
        /* 50 */ { NT(FUNC_ANALISIS), T(TipoToken.MAXIMO) },
        /* 51 */ { NT(FUNC_ANALISIS), T(TipoToken.FLUCTUACION) },

        /* 52 */ { NT(PARAMS_ANALISIS), T(TipoToken.COMA), T(TipoToken.VENTANA), T(TipoToken.ASIGNACION), T(TipoToken.NUMERO) },
        /* 53 */ { NT(PARAMS_ANALISIS) },                            // ε

        /* 54 */ { NT(CONSECUENCIA),  NT(ACCION) },
        /* 55 */ { NT(CONSECUENCIA),  T(TipoToken.LLAVE_IZQ), NT(ACCIONES), T(TipoToken.LLAVE_DER) },

        /* 56 */ { NT(ACCION),        T(TipoToken.ESTADO), T(TipoToken.ASIGNACION), T(TipoToken.ESTADO_SISTEMA), T(TipoToken.PUNTO_COMA) },
        /* 57 */ { NT(ACCION),        T(TipoToken.ALERTA), T(TipoToken.ASIGNACION), T(TipoToken.CADENA), T(TipoToken.PUNTO_COMA) },

        /* 58 */ { NT(ACCIONES),      NT(ACCION), NT(ACCIONES_REST) },
        /* 59 */ { NT(ACCIONES_REST), NT(ACCION), NT(ACCIONES_REST) },
        /* 60 */ { NT(ACCIONES_REST) },                              // ε

        /* 61 */ { NT(COND_SIMPLE),   NT(EXPRESION), NT(OP_REL), NT(EXPRESION) },

        /* 62 */ { NT(COND_COMPUESTA), NT(LOG_OP), NT(COND_SIMPLE), NT(COND_COMPUESTA) },
        /* 63 */ { NT(COND_COMPUESTA) },                             // ε

        /* 64 */ { NT(LOG_OP),        T(TipoToken.Y) },
        /* 65 */ { NT(LOG_OP),        T(TipoToken.O) }
    };

    public static final BitSet[] FIRST = new BitSet[NUM_NT];
    public static final boolean[] FIRST_EPS = new boolean[NUM_NT];
    public static final BitSet[] FOLLOW = new BitSet[NUM_NT];
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
