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
 * La gramática está factorizada por la izquierda y sin recursión
 * izquierda directa, por lo que es LL(1).
 */
public class Gramatica {

    // ============================================================
    //  IDs DE NO TERMINALES
    // ============================================================
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
        OP_REL        = 9;

    public static final int NUM_NT = 10;

    public static final String[] NOMBRES_NT = {
        "PROGRAMA", "SENTENCIA", "VALOR_UMBRAL",
        "CONDICION", "EXPRESION", "EXPRESION_SIG",
        "TERMINO", "TERMINO_SIG", "FACTOR", "OP_REL"
    };

    // ============================================================
    //  IDs DE PRODUCCIONES
    // ============================================================
    public static final int
        P_PROGRAMA_REC  = 0,
        P_PROGRAMA_EPS  = 1,
        P_SENT_SENSOR   = 2,
        P_SENT_UMBRAL   = 3,
        P_SENT_SI       = 4,
        P_VALOR_NEG     = 5,
        P_VALOR_POS     = 6,
        P_COND          = 7,
        P_EXPR          = 8,
        P_EXPR_SIG_MAS  = 9,
        P_EXPR_SIG_MENOS=10,
        P_EXPR_SIG_EPS  =11,
        P_TERM          =12,
        P_TERM_SIG_POR  =13,
        P_TERM_SIG_DIV  =14,
        P_TERM_SIG_EPS  =15,
        P_FACT_NUM      =16,
        P_FACT_ID       =17,
        P_FACT_ABS      =18,
        P_FACT_MENOS    =19,
        P_OP_MAYOR      =20,
        P_OP_MENOR      =21,
        P_OP_IGUAL      =22,
        P_OP_MAYIG      =23,
        P_OP_MENIG      =24,
        P_OP_DIF        =25;

    public static final int NUM_PROD = 26;

    // ============================================================
    //  CODIFICACIÓN DE SÍMBOLOS
    // ============================================================
    // Terminal: ordinal de TipoToken (0..22)
    // No terminal: -(id + 1) → valores negativos (-1..-10)
    static int T(TipoToken t) { return t.ordinal(); }
    static int NT(int nt)     { return -(nt + 1); }
    static boolean esTerminal(int sym)   { return sym >= 0; }
    static int idNoTerminal(int sym)     { return -sym - 1; }

    // ============================================================
    //  PRODUCCIONES
    // ============================================================
    // Cada producción: { cabeza_nt_codificado, sym1, sym2, ... }
    // Donde cabeza_nt_codificado = NT(id_del_no_terminal)
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

        /*  5 */ { NT(VALOR_UMBRAL), T(TipoToken.MENOS),       T(TipoToken.NUMERO) },
        /*  6 */ { NT(VALOR_UMBRAL), T(TipoToken.NUMERO) },

        /*  7 */ { NT(CONDICION),    NT(EXPRESION), NT(OP_REL), NT(EXPRESION) },

        /*  8 */ { NT(EXPRESION),    NT(TERMINO), NT(EXPRESION_SIG) },
        /*  9 */ { NT(EXPRESION_SIG), T(TipoToken.MAS),         NT(TERMINO),
                                       NT(EXPRESION_SIG) },
        /* 10 */ { NT(EXPRESION_SIG), T(TipoToken.MENOS),       NT(TERMINO),
                                       NT(EXPRESION_SIG) },
        /* 11 */ { NT(EXPRESION_SIG) },                              // ε

        /* 12 */ { NT(TERMINO),      NT(FACTOR), NT(TERMINO_SIG) },
        /* 13 */ { NT(TERMINO_SIG),  T(TipoToken.POR),          NT(FACTOR),
                                       NT(TERMINO_SIG) },
        /* 14 */ { NT(TERMINO_SIG),  T(TipoToken.DIV),          NT(FACTOR),
                                       NT(TERMINO_SIG) },
        /* 15 */ { NT(TERMINO_SIG) },                               // ε

        /* 16 */ { NT(FACTOR),       T(TipoToken.NUMERO) },
        /* 17 */ { NT(FACTOR),       T(TipoToken.ID) },
        /* 18 */ { NT(FACTOR),       T(TipoToken.ABS),          T(TipoToken.PAREN_IZQ),
                                       NT(EXPRESION),            T(TipoToken.PAREN_DER) },
        /* 19 */ { NT(FACTOR),       T(TipoToken.MENOS),        NT(FACTOR) },

        /* 20 */ { NT(OP_REL),       T(TipoToken.MAYOR) },
        /* 21 */ { NT(OP_REL),       T(TipoToken.MENOR) },
        /* 22 */ { NT(OP_REL),       T(TipoToken.IGUAL_IGUAL) },
        /* 23 */ { NT(OP_REL),       T(TipoToken.MAYOR_IGUAL) },
        /* 24 */ { NT(OP_REL),       T(TipoToken.MENOR_IGUAL) },
        /* 25 */ { NT(OP_REL),       T(TipoToken.DIFERENTE) },
    };

    // ============================================================
    //  FIRST y FOLLOW SETS
    // ============================================================
    public static final BitSet[] FIRST = new BitSet[NUM_NT];
    public static final boolean[] FIRST_EPS = new boolean[NUM_NT];
    public static final BitSet[] FOLLOW = new BitSet[NUM_NT];

    // ============================================================
    //  TABLA DE PARSING LL(1)
    // ============================================================
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

    // ============================================================
    //  CÁLCULO DE FIRST
    //  Algoritmo de punto fijo:
    //    - Para cada producción A → α:
    //      FIRST(A) ⊇ FIRST(α)
    // ============================================================
    static void calcularFIRST() {
        boolean cambios;
        do {
            cambios = false;
            for (int[] prod : PRODUCCIONES) {
                int cabeza = idNoTerminal(prod[0]);  // ← BUG FIX: decodificar
                boolean todoEpsilon = true;

                for (int i = 1; i < prod.length; i++) {
                    int sym = prod[i];
                    if (esTerminal(sym)) {
                        // Terminal: añadirlo directamente
                        if (!FIRST[cabeza].get(sym)) {
                            FIRST[cabeza].set(sym);
                            cambios = true;
                        }
                        todoEpsilon = false;
                        break;
                    } else {
                        int nt = idNoTerminal(sym);
                        // Añadir FIRST[nt] - {ε} a FIRST[cabeza]
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
                        // ε ∈ FIRST[nt] → continuar con el siguiente símbolo
                    }
                }

                if (todoEpsilon && !FIRST_EPS[cabeza]) {
                    FIRST_EPS[cabeza] = true;
                    cambios = true;
                }
            }
        } while (cambios);
    }

    // ============================================================
    //  CÁLCULO DE FOLLOW
    //  Algoritmo de punto fijo:
    //    1. $ ∈ FOLLOW(PROGRAMA)
    //    2. Para A → α B β: FIRST(β) - {ε} ⊆ FOLLOW(B)
    //    3. Para A → α B (o A → α B β con ε ∈ FIRST(β)):
    //       FOLLOW(A) ⊆ FOLLOW(B)
    // ============================================================
    static void calcularFOLLOW() {
        FOLLOW[PROGRAMA].set(TipoToken.EOF.ordinal());

        boolean cambios;
        do {
            cambios = false;
            for (int[] prod : PRODUCCIONES) {
                int cabeza = idNoTerminal(prod[0]);  // ← BUG FIX: decodificar

                for (int i = 1; i < prod.length; i++) {
                    int sym = prod[i];
                    if (esNoTerminal(sym)) {
                        int nt = idNoTerminal(sym);

                        // Calcular FIRST(β) donde β = prod[i+1..]
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

                        // Añadir FIRST(β) a FOLLOW[nt]
                        for (int t = firstBeta.nextSetBit(0); t >= 0;
                                t = firstBeta.nextSetBit(t + 1)) {
                            if (!FOLLOW[nt].get(t)) {
                                FOLLOW[nt].set(t);
                                cambios = true;
                            }
                        }

                        // Si β =>* ε, añadir FOLLOW[cabeza] a FOLLOW[nt]
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

    // ============================================================
    //  CONSTRUCCIÓN DE LA TABLA LL(1)
    //  Para cada producción A → α:
    //    1. Para cada t ∈ FIRST(α): TABLA[A][t] = prod
    //    2. Si ε ∈ FIRST(α): para cada t ∈ FOLLOW[A]: TABLA[A][t] = prod
    // ============================================================
    static void construirTabla() {
        for (int p = 0; p < PRODUCCIONES.length; p++) {
            int[] prod = PRODUCCIONES[p];
            int cabeza = idNoTerminal(prod[0]);  // ← BUG FIX: decodificar

            // Calcular FIRST(cuerpo)
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

            // Para cada t ∈ FIRST(cuerpo): TABLA[cabeza][t] = p
            for (int t = firstCuerpo.nextSetBit(0); t >= 0;
                    t = firstCuerpo.nextSetBit(t + 1)) {
                TABLA[cabeza][t] = p;
            }

            // Si ε ∈ FIRST(cuerpo): para cada t ∈ FOLLOW[cabeza]: TABLA[cabeza][t] = p
            if (cuerpoEpsilon) {
                for (int t = FOLLOW[cabeza].nextSetBit(0); t >= 0;
                        t = FOLLOW[cabeza].nextSetBit(t + 1)) {
                    TABLA[cabeza][t] = p;
                }
            }
        }
    }

    // ============================================================
    //  CONSULTA
    // ============================================================
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

    // Auxiliar para ver si un símbolo es no terminal
    static boolean esNoTerminal(int sym) { return sym < 0; }
}
