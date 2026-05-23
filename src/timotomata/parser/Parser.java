package timotomata.parser;

import java.util.*;
import timotomata.lexer.Token;
import timotomata.lexer.TipoToken;
import timotomata.parser.ast.*;

/**
 * PARSER RECURSIVO DESCENDENTE BASADO EN GRAMÁTICA LL(1)
 * ======================================================
 *
 * Cada método implementa un no-terminal de la gramática.
 * Las decisiones sobre qué producción usar se toman mediante
 * la tabla de parsing LL(1) definida en Gramatica.java.
 *
 * Gramática:
 *
 *   PROGRAMA      → SENTENCIA PROGRAMA | ε
 *   SENTENCIA     → SENSOR ID PUNTO_COMA
 *                 | UMBRAL ID ASIGNACION VALOR_UMBRAL PUNTO_COMA
 *                 | SI CONDICION ENTONCES ESTADO ASIGNACION ESTADO_SISTEMA PUNTO_COMA
 *   VALOR_UMBRAL  → MENOS NUMERO | NUMERO
 *   CONDICION     → EXPRESION OP_REL EXPRESION
 *   EXPRESION     → TERMINO EXPRESION_SIG
 *   EXPRESION_SIG → MAS TERMINO EXPRESION_SIG | MENOS TERMINO EXPRESION_SIG | ε
 *   TERMINO       → FACTOR TERMINO_SIG
 *   TERMINO_SIG   → POR FACTOR TERMINO_SIG | DIV FACTOR TERMINO_SIG | ε
 *   FACTOR        → NUMERO | ID | ABS PAREN_IZQ EXPRESION PAREN_DER | MENOS FACTOR
 *   OP_REL        → MAYOR | MENOR | IGUAL_IGUAL | MAYOR_IGUAL | MENOR_IGUAL | DIFERENTE
 */
public class Parser {
    private List<Token> tokens;
    private int actual = 0;

    // Objeto que se construye durante el parseo
    private Programa programa;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    // ============================================================
    //  PUNTO DE ENTRADA
    // ============================================================
    public Programa parsear() {
        programa = new Programa();
        programa();  // PROGRAMA → SENTENCIA PROGRAMA | ε
        return programa;
    }

    // ============================================================
    //  NO TERMINAL: PROGRAMA
    //  PROGRAMA → SENTENCIA PROGRAMA | ε
    // ============================================================
    private void programa() {
        int prod = Gramatica.obtenerProduccion(Gramatica.PROGRAMA, ver().tipo);
        // Si no hay producción y estamos en ε (EOF), terminamos
        if (prod == -1) {
            throw error("Token inesperado: " + ver().lexema);
        }

        switch (prod) {
            case Gramatica.P_PROGRAMA_REC:
                // PROGRAMA → SENTENCIA PROGRAMA
                sentencia();
                programa();
                break;
            case Gramatica.P_PROGRAMA_EPS:
                // PROGRAMA → ε
                break;
        }
    }

    // ============================================================
    //  NO TERMINAL: SENTENCIA
    //  SENTENCIA → SENSOR ID PUNTO_COMA
    //            | UMBRAL ID ASIGNACION VALOR_UMBRAL PUNTO_COMA
    //            | SI CONDICION ENTONCES ESTADO ASIGNACION ESTADO_SISTEMA PUNTO_COMA
    // ============================================================
    private void sentencia() {
        int prod = Gramatica.obtenerProduccion(Gramatica.SENTENCIA, ver().tipo);
        if (prod == -1) {
            throw error("Se esperaba SENSOR, UMBRAL o SI");
        }

        switch (prod) {
            case Gramatica.P_SENT_SENSOR: {
                // SENTENCIA → SENSOR ID PUNTO_COMA
                consumir(TipoToken.SENSOR, "Se esperaba SENSOR");
                Token nombre = consumir(TipoToken.ID, "Se esperaba el nombre del sensor");
                consumir(TipoToken.PUNTO_COMA, "Se esperaba ;");
                programa.sensores.add(nombre.lexema);
                break;
            }
            case Gramatica.P_SENT_UMBRAL: {
                // SENTENCIA → UMBRAL ID ASIGNACION VALOR_UMBRAL PUNTO_COMA
                consumir(TipoToken.UMBRAL, "Se esperaba UMBRAL");
                Token nombre = consumir(TipoToken.ID, "Se esperaba el nombre del umbral");
                consumir(TipoToken.ASIGNACION, "Se esperaba =");
                double val = valorUmbral();
                programa.umbrales.put(nombre.lexema, val);
                consumir(TipoToken.PUNTO_COMA, "Se esperaba ;");
                break;
            }
            case Gramatica.P_SENT_SI: {
                // SENTENCIA → SI CONDICION ENTONCES ESTADO ASIGNACION ESTADO_SISTEMA PUNTO_COMA
                consumir(TipoToken.SI, "Se esperaba SI");
                Expresion cond = condicion();
                consumir(TipoToken.ENTONCES, "Se esperaba ENTONCES");
                consumir(TipoToken.ESTADO, "Se esperaba ESTADO");
                consumir(TipoToken.ASIGNACION, "Se esperaba =");
                Token estado = consumir(TipoToken.ESTADO_SISTEMA,
                    "Se esperaba NORMAL, PICO, CAIDA o INESTABLE");
                consumir(TipoToken.PUNTO_COMA, "Se esperaba ;");
                programa.reglas.add(new Regla(cond, estado.lexema));
                break;
            }
        }
    }

    // ============================================================
    //  NO TERMINAL: VALOR_UMBRAL
    //  VALOR_UMBRAL → MENOS NUMERO | NUMERO
    // ============================================================
    private double valorUmbral() {
        int prod = Gramatica.obtenerProduccion(Gramatica.VALOR_UMBRAL, ver().tipo);
        if (prod == -1) {
            throw error("Se esperaba NUMERO o MENOS");
        }

        switch (prod) {
            case Gramatica.P_VALOR_NEG: {
                // VALOR_UMBRAL → MENOS NUMERO
                consumir(TipoToken.MENOS, "Se esperaba -");
                Token num = consumir(TipoToken.NUMERO, "Se esperaba un numero");
                return -Double.parseDouble(num.lexema);
            }
            case Gramatica.P_VALOR_POS: {
                // VALOR_UMBRAL → NUMERO
                Token num = consumir(TipoToken.NUMERO, "Se esperaba un numero");
                return Double.parseDouble(num.lexema);
            }
            default:
                throw error("Error interno: producción inesperada en VALOR_UMBRAL");
        }
    }

    // ============================================================
    //  NO TERMINAL: CONDICION
    //  CONDICION → EXPRESION OP_REL EXPRESION
    // ============================================================
    private Expresion condicion() {
        int prod = Gramatica.obtenerProduccion(Gramatica.CONDICION, ver().tipo);
        if (prod == -1) {
            throw error("Se esperaba expresion en la condicion");
        }

        // CONDICION → EXPRESION OP_REL EXPRESION
        Expresion izq = expresion();
        String op = operadorRelacional();
        Expresion der = expresion();
        return new Binaria(izq, op, der);
    }

    // ============================================================
    //  NO TERMINAL: OP_REL
    //  OP_REL → MAYOR | MENOR | IGUAL_IGUAL
    //         | MAYOR_IGUAL | MENOR_IGUAL | DIFERENTE
    // ============================================================
    private String operadorRelacional() {
        int prod = Gramatica.obtenerProduccion(Gramatica.OP_REL, ver().tipo);
        if (prod == -1) {
            throw error("Se esperaba operador relacional >, <, ==, >=, <= o !=");
        }

        Token op;
        switch (prod) {
            case Gramatica.P_OP_MAYOR:
                op = consumir(TipoToken.MAYOR, null);
                return op.lexema;
            case Gramatica.P_OP_MENOR:
                op = consumir(TipoToken.MENOR, null);
                return op.lexema;
            case Gramatica.P_OP_IGUAL:
                op = consumir(TipoToken.IGUAL_IGUAL, null);
                return "==";
            case Gramatica.P_OP_MAYIG:
                op = consumir(TipoToken.MAYOR_IGUAL, null);
                return ">=";
            case Gramatica.P_OP_MENIG:
                op = consumir(TipoToken.MENOR_IGUAL, null);
                return "<=";
            case Gramatica.P_OP_DIF:
                op = consumir(TipoToken.DIFERENTE, null);
                return "!=";
            default:
                throw error("Error interno: producción inesperada en OP_REL");
        }
    }

    // ============================================================
    //  NO TERMINAL: EXPRESION
    //  EXPRESION → TERMINO EXPRESION_SIG
    //
    //  EXPRESION_SIG → MAS TERMINO EXPRESION_SIG
    //                | MENOS TERMINO EXPRESION_SIG
    //                | ε
    //
    //  Para lograr asociatividad a la izquierda, EXPRESION_SIG
    //  recibe el valor acumulado por la izquierda como parámetro.
    // ============================================================
    private Expresion expresion() {
        // EXPRESION → TERMINO EXPRESION_SIG
        Expresion izq = termino();
        return expresionSig(izq);
    }

    private Expresion expresionSig(Expresion izquierda) {
        int prod = Gramatica.obtenerProduccion(Gramatica.EXPRESION_SIG, ver().tipo);
        // -1 = ε (el lookahead está en FOLLOW, no en FIRST)

        switch (prod) {
            case Gramatica.P_EXPR_SIG_MAS: {
                // EXPRESION_SIG → MAS TERMINO EXPRESION_SIG
                consumir(TipoToken.MAS, null);
                Expresion der = termino();
                // Construir árbol izquierdo-asociativo:
                // (izquierda + der) se pasa como nueva izquierda
                return expresionSig(new Binaria(izquierda, "+", der));
            }
            case Gramatica.P_EXPR_SIG_MENOS: {
                // EXPRESION_SIG → MENOS TERMINO EXPRESION_SIG
                consumir(TipoToken.MENOS, null);
                Expresion der = termino();
                return expresionSig(new Binaria(izquierda, "-", der));
            }
            default:
                // EXPRESION_SIG → ε: devolver la expresión acumulada
                return izquierda;
        }
    }

    // ============================================================
    //  NO TERMINAL: TERMINO
    //  TERMINO → FACTOR TERMINO_SIG
    //
    //  TERMINO_SIG → POR FACTOR TERMINO_SIG
    //              | DIV FACTOR TERMINO_SIG
    //              | ε
    // ============================================================
    private Expresion termino() {
        // TERMINO → FACTOR TERMINO_SIG
        Expresion izq = factor();
        return terminoSig(izq);
    }

    private Expresion terminoSig(Expresion izquierda) {
        int prod = Gramatica.obtenerProduccion(Gramatica.TERMINO_SIG, ver().tipo);

        switch (prod) {
            case Gramatica.P_TERM_SIG_POR: {
                // TERMINO_SIG → POR FACTOR TERMINO_SIG
                consumir(TipoToken.POR, null);
                Expresion der = factor();
                return terminoSig(new Binaria(izquierda, "*", der));
            }
            case Gramatica.P_TERM_SIG_DIV: {
                // TERMINO_SIG → DIV FACTOR TERMINO_SIG
                consumir(TipoToken.DIV, null);
                Expresion der = factor();
                return terminoSig(new Binaria(izquierda, "/", der));
            }
            default:
                // TERMINO_SIG → ε
                return izquierda;
        }
    }

    // ============================================================
    //  NO TERMINAL: FACTOR
    //  FACTOR → NUMERO | ID | ABS PAREN_IZQ EXPRESION PAREN_DER | MENOS FACTOR
    // ============================================================
    private Expresion factor() {
        int prod = Gramatica.obtenerProduccion(Gramatica.FACTOR, ver().tipo);
        if (prod == -1) {
            throw error("Se esperaba numero, identificador, abs() o -");
        }

        switch (prod) {
            case Gramatica.P_FACT_NUM: {
                // FACTOR → NUMERO
                Token t = consumir(TipoToken.NUMERO, "Se esperaba un numero");
                return new Numero(Double.parseDouble(t.lexema));
            }
            case Gramatica.P_FACT_ID: {
                // FACTOR → ID
                Token t = consumir(TipoToken.ID, "Se esperaba un identificador");
                return new Variable(t.lexema);
            }
            case Gramatica.P_FACT_ABS: {
                // FACTOR → ABS PAREN_IZQ EXPRESION PAREN_DER
                consumir(TipoToken.ABS, null);
                consumir(TipoToken.PAREN_IZQ, "Se esperaba (");
                Expresion expr = expresion();
                consumir(TipoToken.PAREN_DER, "Se esperaba )");
                return new Abs(expr);
            }
            case Gramatica.P_FACT_MENOS: {
                // FACTOR → MENOS FACTOR
                consumir(TipoToken.MENOS, null);
                Expresion expr = factor();
                return new Negacion(expr);
            }
            default:
                throw error("Error interno: producción inesperada en FACTOR");
        }
    }

    // ============================================================
    //  MÉTODOS AUXILIARES PARA CONSUMIR TOKENS
    // ============================================================
    private Token consumir(TipoToken tipo, String mensaje) {
        if (verificar(tipo)) return avanzar();
        if (mensaje != null) throw error(mensaje);
        throw error("Se esperaba " + tipo + " pero se encontró " + ver().lexema);
    }

    private boolean verificar(TipoToken tipo) {
        return actual < tokens.size() && tokens.get(actual).tipo == tipo;
    }

    private Token avanzar() {
        return tokens.get(actual++);
    }

    private Token ver() {
        return tokens.get(actual);
    }

    private RuntimeException error(String mensaje) {
        return new RuntimeException("Error sintactico en linea " + ver().linea + ": " + mensaje);
    }
}
