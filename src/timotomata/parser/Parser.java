package timotomata.parser;

import java.util.*;
import timotomata.lexer.Token;
import timotomata.lexer.TipoToken;
import timotomata.parser.ast.*;

// ErrorSintacticoDetalle ahora está en su propio archivo


public class Parser {
    private List<Token> tokens;
    private int actual = 0;

    // Último token consumido exitosamente (para contexto en errores)
    private Token ultimoConsumido = null;

    // AST que se construye durante el parseo
    private Programa programa;

    // Para pasar valores semánticos entre métodos
    private Object ultimoValorSemantico;

    // Árbol de derivación raíz (resultado final)
    public NodoDerivacion arbolDerivacion;

    // Errores sintácticos recolectados durante el parseo (panic-mode recovery)
    public List<String> erroresSintacticos = new ArrayList<>();
    public List<ErrorSintacticoDetalle> erroresSintacticosDetalle = new ArrayList<>();

    public List<String> getErroresSintacticos() { return erroresSintacticos; }
    public List<ErrorSintacticoDetalle> getErroresSintacticosDetalle() { return erroresSintacticosDetalle; }

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    
    //  PUNTO DE ENTRADA
    
    public Programa parsear() {
        programa = new Programa();
        erroresSintacticos.clear();
        NodoDerivacion raiz = new NodoDerivacion("PROGRAMA");
        try {
            programa(raiz);  // PROGRAMA → SENTENCIA PROGRAMA | ε
        } catch (RuntimeException e) {
            erroresSintacticos.add(e.getMessage());
        }
        arbolDerivacion = raiz;
        return programa;
    }

    
    //  AUXILIAR: crear nodo terminal para un token consumido
    
    private NodoDerivacion t(Token token) {
        return new NodoDerivacion(token.tipo.name() + "(" + token.lexema + ")");
    }

    
    //  NO TERMINAL: PROGRAMA
    //  PROGRAMA → SENTENCIA PROGRAMA | ε
    
    /**
     * PROGRAMA → SENTENCIA PROGRAMA | ε
     *
     * Con recuperación de errores: si una sentencia falla,
     * se registra el error, se sincroniza (avanzando hasta ; o EOF)
     * y se continúa con la siguiente sentencia.
     */
    private void programa(NodoDerivacion nodo) {
        while (actual < tokens.size()) {
            int prod = Gramatica.obtenerProduccion(Gramatica.PROGRAMA, ver().tipo);
            if (prod == Gramatica.P_PROGRAMA_EPS) {
                return; // ε — fin normal del programa
            }
            if (prod == -1) {
                // Token que no pertenece a PROGRAMA — error de sincronización
                String msgSync = "Error sintactico en linea " + ver().linea
                    + ": Token inesperado" + contexto();
                erroresSintacticos.add(msgSync);
                erroresSintacticosDetalle.add(new ErrorSintacticoDetalle(msgSync,
                    ver().linea, ver().columna, ver().lexema.length()));
                avanzar();
                sincronizar();
                continue;
            }

            // P_PROGRAMA_REC: PROGRAMA → SENTENCIA PROGRAMA
            NodoDerivacion nSent = new NodoDerivacion("SENTENCIA");
            try {
                sentencia(nSent);
                nodo.agregarHijo(nSent);
            } catch (RuntimeException e) {
                erroresSintacticos.add(e.getMessage());
                sincronizar();
                // Después de sincronizar, intentar parsear más sentencias
                continue;
            }
            NodoDerivacion nProg = new NodoDerivacion("PROGRAMA");
            programa(nProg);
            nodo.agregarHijo(nProg);
            return;
        }
    }

    // Modo panico
     
    private void sincronizar() {
        while (actual < tokens.size()) {
            TipoToken t = tokens.get(actual).tipo;
            if (t == TipoToken.PUNTO_COMA) {
                avanzar(); // consumir el ;
                return;
            }
            if (t == TipoToken.SENSOR || t == TipoToken.UMBRAL
                || t == TipoToken.SI || t == TipoToken.CALCULAR
                || t == TipoToken.EOF) {
                return; // no consumir — es el inicio de la siguiente sentencia
            }
            avanzar();
        }
    }

    
    //  NO TERMINAL: SENTENCIA
    //  SENTENCIA → SENSOR ID PUNTO_COMA
    //            | UMBRAL ID ASIGNACION VALOR_UMBRAL PUNTO_COMA
    //            | SI CONDICION ENTONCES ESTADO ASIGNACION ESTADO_SISTEMA PUNTO_COMA
    //            | CALCULAR ID COMA TIPO_OP COMA LISTA_PARAMS PUNTO_COMA
    
    private void sentencia(NodoDerivacion nodo) {
        int prod = Gramatica.obtenerProduccion(Gramatica.SENTENCIA, ver().tipo);
        if (prod == -1) {
            throw error("Se esperaba SENSOR, UMBRAL, SI o CALCULAR" + contexto());
        }

        switch (prod) {
            case Gramatica.P_SENT_SENSOR: {
                Token t1 = consumir(TipoToken.SENSOR, "Se esperaba SENSOR");
                Token t2 = consumir(TipoToken.ID, "Se esperaba el nombre del sensor");
                Token t3 = consumir(TipoToken.PUNTO_COMA, "Se esperaba ;");
                nodo.agregarHijo(t(t1));
                nodo.agregarHijo(t(t2));
                nodo.agregarHijo(t(t3));
                programa.sensores.add(t2.lexema);
                break;
            }
            case Gramatica.P_SENT_UMBRAL: {
                Token t1 = consumir(TipoToken.UMBRAL, "Se esperaba UMBRAL");
                Token t2 = consumir(TipoToken.ID, "Se esperaba el nombre del umbral");
                Token t3 = consumir(TipoToken.ASIGNACION, "Se esperaba =");
                NodoDerivacion nVal = valorUmbral();
                Token t5 = consumir(TipoToken.PUNTO_COMA, "Se esperaba ;");
                nodo.agregarHijo(t(t1));
                nodo.agregarHijo(t(t2));
                nodo.agregarHijo(t(t3));
                nodo.agregarHijo(nVal);
                nodo.agregarHijo(t(t5));
                programa.umbrales.put(t2.lexema, (double) ultimoValorSemantico);
                break;
            }
            case Gramatica.P_SENT_SI: {
                Token t1 = consumir(TipoToken.SI, "Se esperaba SI");
                nodo.agregarHijo(t(t1));
                NodoDerivacion nCond = condicion();
                nodo.agregarHijo(nCond);
                Expresion cond = (Expresion) ultimoValorSemantico;
                Token t3 = consumir(TipoToken.ENTONCES, "Se esperaba ENTONCES");
                Token t4 = consumir(TipoToken.ESTADO, "Se esperaba ESTADO");
                Token t5 = consumir(TipoToken.ASIGNACION, "Se esperaba =");
                Token t6 = consumir(TipoToken.ESTADO_SISTEMA,
                    "Se esperaba NORMAL, PICO, CAIDA o INESTABLE");
                Token t7 = consumir(TipoToken.PUNTO_COMA, "Se esperaba ;");
                nodo.agregarHijo(t(t3));
                nodo.agregarHijo(t(t4));
                nodo.agregarHijo(t(t5));
                nodo.agregarHijo(t(t6));
                nodo.agregarHijo(t(t7));
                programa.reglas.add(new Regla(cond, t6.lexema));
                break;
            }
            case Gramatica.P_SENT_CALCULAR: {
                Token t1 = consumir(TipoToken.CALCULAR, "Se esperaba CALCULAR");
                Token t2 = consumir(TipoToken.PAREN_IZQ, "Se esperaba (");
                Token t3 = consumir(TipoToken.ID, "Se esperaba nombre del sensor");
                Token t4 = consumir(TipoToken.COMA, "Se esperaba ,");
                nodo.agregarHijo(t(t1));
                nodo.agregarHijo(t(t2));
                nodo.agregarHijo(t(t3));
                nodo.agregarHijo(t(t4));

                Calculo calculo = new Calculo(t3.lexema, "");

                NodoDerivacion nTipoOp = new NodoDerivacion("TIPO_OP");
                tipoOp(nTipoOp, calculo);
                nodo.agregarHijo(nTipoOp);

                Token t6 = consumir(TipoToken.PAREN_DER, "Se esperaba )");
                Token t7 = consumir(TipoToken.PUNTO_COMA, "Se esperaba ;");
                nodo.agregarHijo(t(t6));
                nodo.agregarHijo(t(t7));

                programa.calculos.add(calculo);
                break;
            }
            case Gramatica.P_SENT_FIN: {
                Token t1 = consumir(TipoToken.FIN, "Se esperaba FIN");
                Token t2 = consumir(TipoToken.PUNTO_COMA, "Se esperaba ;");
                nodo.agregarHijo(t(t1));
                nodo.agregarHijo(t(t2));
                // fin; — sentencia que termina el programa sin acción adicional
                break;
            }
        }
    }

    
    //  NO TERMINAL: VALOR_UMBRAL
    //  VALOR_UMBRAL → MENOS NUMERO | NUMERO
    //  Devuelve NodoDerivacion, deja el valor double en ultimoValorSemantico
    
    private NodoDerivacion valorUmbral() {
        NodoDerivacion nodo = new NodoDerivacion("VALOR_UMBRAL");
        int prod = Gramatica.obtenerProduccion(Gramatica.VALOR_UMBRAL, ver().tipo);
        if (prod == -1) {
            throw error("Se esperaba NUMERO o MENOS" + contexto());
        }

        switch (prod) {
            case Gramatica.P_VALOR_NEG: {
                Token t1 = consumir(TipoToken.MENOS, null);
                Token t2 = consumir(TipoToken.NUMERO, null);
                nodo.agregarHijo(t(t1));
                nodo.agregarHijo(t(t2));
                ultimoValorSemantico = -Double.parseDouble(t2.lexema);
                break;
            }
            case Gramatica.P_VALOR_POS: {
                Token t = consumir(TipoToken.NUMERO, null);
                nodo.agregarHijo(t(t));
                ultimoValorSemantico = Double.parseDouble(t.lexema);
                break;
            }
        }
        return nodo;
    }

    
    //  NO TERMINAL: CONDICION
    //  CONDICION → EXPRESION OP_REL EXPRESION
    
    private NodoDerivacion condicion() {
        NodoDerivacion nodo = new NodoDerivacion("CONDICION");
        int prod = Gramatica.obtenerProduccion(Gramatica.CONDICION, ver().tipo);
        if (prod == -1) {
            throw error("Se esperaba expresion en la condicion" + contexto());
        }

        NodoDerivacion nExprIzq = expresion();
        Expresion izq = (Expresion) ultimoValorSemantico;
        nodo.agregarHijo(nExprIzq);

        NodoDerivacion nOpRel = operadorRelacional();
        String op = (String) ultimoValorSemantico;
        nodo.agregarHijo(nOpRel);

        NodoDerivacion nExprDer = expresion();
        Expresion der = (Expresion) ultimoValorSemantico;
        nodo.agregarHijo(nExprDer);

        ultimoValorSemantico = new Binaria(izq, op, der);
        return nodo;
    }

    
    //  NO TERMINAL: OP_REL
    //  OP_REL → MAYOR | MENOR | IGUAL_IGUAL | ...
    
    private NodoDerivacion operadorRelacional() {
        NodoDerivacion nodo = new NodoDerivacion("OP_REL");
        int prod = Gramatica.obtenerProduccion(Gramatica.OP_REL, ver().tipo);
        if (prod == -1) {
            throw error("Se esperaba operador relacional" + contexto());
        }

        Token op;
        String opStr;
        switch (prod) {
            case Gramatica.P_OP_MAYOR:
                op = consumir(TipoToken.MAYOR, null);
                opStr = ">";
                break;
            case Gramatica.P_OP_MENOR:
                op = consumir(TipoToken.MENOR, null);
                opStr = "<";
                break;
            case Gramatica.P_OP_IGUAL:
                op = consumir(TipoToken.IGUAL_IGUAL, null);
                opStr = "==";
                break;
            case Gramatica.P_OP_MAYIG:
                op = consumir(TipoToken.MAYOR_IGUAL, null);
                opStr = ">=";
                break;
            case Gramatica.P_OP_MENIG:
                op = consumir(TipoToken.MENOR_IGUAL, null);
                opStr = "<=";
                break;
            case Gramatica.P_OP_DIF:
                op = consumir(TipoToken.DIFERENTE, null);
                opStr = "!=";
                break;
            default:
                throw error("Error interno en OP_REL"); // no contexto porque es interno
        }
        nodo.agregarHijo(t(op));
        ultimoValorSemantico = opStr;
        return nodo;
    }

    
    //  NO TERMINAL: EXPRESION
    //  EXPRESION → TERMINO EXPRESION_SIG
    
    private NodoDerivacion expresion() {
        NodoDerivacion nodo = new NodoDerivacion("EXPRESION");
        NodoDerivacion nTerm = termino();
        Expresion izq = (Expresion) ultimoValorSemantico;
        nodo.agregarHijo(nTerm);
        NodoDerivacion nExprSig = expresionSig(izq);
        nExprSig.sintetico = true;
        nodo.agregarHijo(nExprSig);
        return nodo;
    }

    
    //  EXPRESION_SIG → MAS TERMINO EXPRESION_SIG | MENOS TERMINO EXPRESION_SIG | ε
    
    private NodoDerivacion expresionSig(Expresion izquierda) {
        NodoDerivacion nodo = new NodoDerivacion("EXPRESION_SIG");
        int prod = Gramatica.obtenerProduccion(Gramatica.EXPRESION_SIG, ver().tipo);

        switch (prod) {
            case Gramatica.P_EXPR_SIG_MAS: {
                Token t = consumir(TipoToken.MAS, null);
                nodo.agregarHijo(t(t));
                NodoDerivacion nTerm = termino();
                Expresion der = (Expresion) ultimoValorSemantico;
                nodo.agregarHijo(nTerm);
                Expresion nuevaIzq = new Binaria(izquierda, "+", der);
                NodoDerivacion nRec = expresionSig(nuevaIzq);
                nodo.agregarHijo(nRec);
                ultimoValorSemantico = nuevaIzq;
                break;
            }
            case Gramatica.P_EXPR_SIG_MENOS: {
                Token t = consumir(TipoToken.MENOS, null);
                nodo.agregarHijo(t(t));
                NodoDerivacion nTerm = termino();
                Expresion der = (Expresion) ultimoValorSemantico;
                nodo.agregarHijo(nTerm);
                Expresion nuevaIzq = new Binaria(izquierda, "-", der);
                NodoDerivacion nRec = expresionSig(nuevaIzq);
                nodo.agregarHijo(nRec);
                ultimoValorSemantico = nuevaIzq;
                break;
            }
            default:
                // ε: ya tenemos el valor en izquierda
                ultimoValorSemantico = izquierda;
                break;
        }
        return nodo;
    }

    
    //  NO TERMINAL: TERMINO
    //  TERMINO → FACTOR TERMINO_SIG
    
    private NodoDerivacion termino() {
        NodoDerivacion nodo = new NodoDerivacion("TERMINO");
        NodoDerivacion nFact = factor();
        Expresion izq = (Expresion) ultimoValorSemantico;
        nodo.agregarHijo(nFact);
        NodoDerivacion nTermSig = terminoSig(izq);
        nTermSig.sintetico = true;
        nodo.agregarHijo(nTermSig);
        return nodo;
    }

    
    //  TERMINO_SIG → POR FACTOR TERMINO_SIG | DIV FACTOR TERMINO_SIG | ε
    
    private NodoDerivacion terminoSig(Expresion izquierda) {
        NodoDerivacion nodo = new NodoDerivacion("TERMINO_SIG");
        int prod = Gramatica.obtenerProduccion(Gramatica.TERMINO_SIG, ver().tipo);

        switch (prod) {
            case Gramatica.P_TERM_SIG_POR: {
                Token t = consumir(TipoToken.POR, null);
                nodo.agregarHijo(t(t));
                NodoDerivacion nFact = factor();
                Expresion der = (Expresion) ultimoValorSemantico;
                nodo.agregarHijo(nFact);
                Expresion nuevaIzq = new Binaria(izquierda, "*", der);
                NodoDerivacion nRec = terminoSig(nuevaIzq);
                nodo.agregarHijo(nRec);
                ultimoValorSemantico = nuevaIzq;
                break;
            }
            case Gramatica.P_TERM_SIG_DIV: {
                Token t = consumir(TipoToken.DIV, null);
                nodo.agregarHijo(t(t));
                NodoDerivacion nFact = factor();
                Expresion der = (Expresion) ultimoValorSemantico;
                nodo.agregarHijo(nFact);
                Expresion nuevaIzq = new Binaria(izquierda, "/", der);
                NodoDerivacion nRec = terminoSig(nuevaIzq);
                nodo.agregarHijo(nRec);
                ultimoValorSemantico = nuevaIzq;
                break;
            }
            default:
                ultimoValorSemantico = izquierda;
                break;
        }
        return nodo;
    }

    
    //  NO TERMINAL: FACTOR
    //  FACTOR → NUMERO | ID | ABS(...) | MENOS FACTOR
    
    private NodoDerivacion factor() {
        NodoDerivacion nodo = new NodoDerivacion("FACTOR");
        int prod = Gramatica.obtenerProduccion(Gramatica.FACTOR, ver().tipo);
        if (prod == -1) {
            throw error("Se esperaba numero, identificador, abs() o -" + contexto());
        }

        switch (prod) {
            case Gramatica.P_FACT_NUM: {
                Token t = consumir(TipoToken.NUMERO, null);
                nodo.agregarHijo(t(t));
                ultimoValorSemantico = new Numero(Double.parseDouble(t.lexema));
                break;
            }
            case Gramatica.P_FACT_ID: {
                Token t = consumir(TipoToken.ID, null);
                nodo.agregarHijo(t(t));
                ultimoValorSemantico = new Variable(t.lexema);
                break;
            }
            case Gramatica.P_FACT_ABS: {
                Token t1 = consumir(TipoToken.ABS, null);
                Token t2 = consumir(TipoToken.PAREN_IZQ, null);
                nodo.agregarHijo(t(t1));
                nodo.agregarHijo(t(t2));
                NodoDerivacion nExpr = expresion();
                Expresion expr = (Expresion) ultimoValorSemantico;
                nodo.agregarHijo(nExpr);
                Token t3 = consumir(TipoToken.PAREN_DER, null);
                nodo.agregarHijo(t(t3));
                ultimoValorSemantico = new Abs(expr);
                break;
            }
            case Gramatica.P_FACT_MENOS: {
                Token t = consumir(TipoToken.MENOS, null);
                nodo.agregarHijo(t(t));
                NodoDerivacion nFact = factor();
                Expresion expr = (Expresion) ultimoValorSemantico;
                nodo.agregarHijo(nFact);
                ultimoValorSemantico = new Negacion(expr);
                break;
            }
            default:
                throw error("Error interno en FACTOR"); // no contexto porque es interno
        }
        return nodo;
    }

    
    //  NO TERMINAL: TIPO_OP
    //  TIPO_OP → SENO PAREN_IZQ LISTA_PARAMS PAREN_DER | ...
    //  Ahora recibe el Calculo para setear operacion y agregar parametros internamente
    
    private void tipoOp(NodoDerivacion nodo, Calculo calculo) {
        int prod = Gramatica.obtenerProduccion(Gramatica.TIPO_OP, ver().tipo);
        if (prod == -1) {
            throw error("Se esperaba SENO, COSENO, CUADRADA, PROMEDIO, MAXIMO o SUMA" + contexto());
        }

        Token t;
        String opStr;
        switch (prod) {
            case Gramatica.P_TIPO_SENO:
                t = consumir(TipoToken.SENO, null); opStr = "seno"; break;
            case Gramatica.P_TIPO_COSENO:
                t = consumir(TipoToken.COSENO, null); opStr = "coseno"; break;
            case Gramatica.P_TIPO_CUADRADA:
                t = consumir(TipoToken.CUADRADA, null); opStr = "cuadrada"; break;
            case Gramatica.P_TIPO_PROMEDIO:
                t = consumir(TipoToken.PROMEDIO, null); opStr = "promedio"; break;
            case Gramatica.P_TIPO_MAXIMO:
                t = consumir(TipoToken.MAXIMO, null); opStr = "maximo"; break;
            case Gramatica.P_TIPO_SUMA:
                t = consumir(TipoToken.SUMA, null); opStr = "suma"; break;
            default:
                throw error("Error interno en TIPO_OP"); // no contexto porque es interno
        }
        nodo.agregarHijo(t(t));
        calculo.operacion = opStr;

        // Consumir ( parametros )
        Token pIzq = consumir(TipoToken.PAREN_IZQ, "Se esperaba (");
        nodo.agregarHijo(t(pIzq));

        NodoDerivacion nParams = new NodoDerivacion("LISTA_PARAMS");
        nParams.sintetico = true;
        listaParams(nParams, calculo);
        nodo.agregarHijo(nParams);

        Token pDer = consumir(TipoToken.PAREN_DER, "Se esperaba )");
        nodo.agregarHijo(t(pDer));

        ultimoValorSemantico = opStr;
    }

    
    //  NO TERMINAL: LISTA_PARAMS
    //  LISTA_PARAMS → PARAM LISTA_PARAMS_SIG | ε
    
    private void listaParams(NodoDerivacion nodo, Calculo calculo) {
        int prod = Gramatica.obtenerProduccion(Gramatica.LISTA_PARAMS, ver().tipo);
        if (prod == -1) {
            throw error("Se esperaba AMPLITUD, FRECUENCIA, VENTANA o CON" + contexto());
        }

        switch (prod) {
            case Gramatica.P_PARAMS_PARAM: {
                NodoDerivacion nParam = new NodoDerivacion("PARAM");
                nParam.sintetico = true;
                param(nParam, calculo);
                nodo.agregarHijo(nParam);
                NodoDerivacion nSig = new NodoDerivacion("LISTA_PARAMS_SIG");
                nSig.sintetico = true;
                listaParamsSig(nSig, calculo);
                nodo.agregarHijo(nSig);
                break;
            }
            case Gramatica.P_PARAMS_EPS:
                // ε — sin parámetros
                break;
        }
    }

    
    //  LISTA_PARAMS_SIG → COMA LISTA_PARAMS | ε
    
    private void listaParamsSig(NodoDerivacion nodo, Calculo calculo) {
        int prod = Gramatica.obtenerProduccion(Gramatica.LISTA_PARAMS_SIG, ver().tipo);

        switch (prod) {
            case Gramatica.P_PARAMS_SIG_COMA: {
                Token t = consumir(TipoToken.COMA, "Se esperaba ,");
                nodo.agregarHijo(t(t));
                NodoDerivacion nRec = new NodoDerivacion("LISTA_PARAMS");
                nRec.sintetico = true;
                listaParams(nRec, calculo);
                nodo.agregarHijo(nRec);
                break;
            }
            case Gramatica.P_PARAMS_SIG_EPS:
                // ε
                break;
        }
    }

    
    //  NO TERMINAL: PARAM
    //  PARAM → AMPLITUD ASIGNACION NUMERO | FRECUENCIA ASIGNACION NUMERO
    //        | VENTANA ASIGNACION NUMERO | CON ASIGNACION ID
    
    private void param(NodoDerivacion nodo, Calculo calculo) {
        int prod = Gramatica.obtenerProduccion(Gramatica.PARAM, ver().tipo);
        if (prod == -1) {
            throw error("Se esperaba AMPLITUD, FRECUENCIA, VENTANA o CON" + contexto());
        }

        switch (prod) {
            case Gramatica.P_PARAM_AMPL: {
                Token t1 = consumir(TipoToken.AMPLITUD, null);
                Token t2 = consumir(TipoToken.ASIGNACION, "Se esperaba =");
                Token t3 = consumir(TipoToken.NUMERO, null);
                nodo.agregarHijo(t(t1));
                nodo.agregarHijo(t(t2));
                nodo.agregarHijo(t(t3));
                calculo.parametros.add(new Parametro("amplitud", t3.lexema));
                break;
            }
            case Gramatica.P_PARAM_FREC: {
                Token t1 = consumir(TipoToken.FRECUENCIA, null);
                Token t2 = consumir(TipoToken.ASIGNACION, "Se esperaba =");
                Token t3 = consumir(TipoToken.NUMERO, null);
                nodo.agregarHijo(t(t1));
                nodo.agregarHijo(t(t2));
                nodo.agregarHijo(t(t3));
                calculo.parametros.add(new Parametro("frecuencia", t3.lexema));
                break;
            }
            case Gramatica.P_PARAM_VENT: {
                Token t1 = consumir(TipoToken.VENTANA, null);
                Token t2 = consumir(TipoToken.ASIGNACION, "Se esperaba =");
                Token t3 = consumir(TipoToken.NUMERO, null);
                nodo.agregarHijo(t(t1));
                nodo.agregarHijo(t(t2));
                nodo.agregarHijo(t(t3));
                calculo.parametros.add(new Parametro("ventana", t3.lexema));
                break;
            }
            case Gramatica.P_PARAM_CON: {
                Token t1 = consumir(TipoToken.CON, null);
                Token t2 = consumir(TipoToken.ASIGNACION, "Se esperaba =");
                Token t3 = consumir(TipoToken.ID, null);
                nodo.agregarHijo(t(t1));
                nodo.agregarHijo(t(t2));
                nodo.agregarHijo(t(t3));
                calculo.parametros.add(new Parametro("con", t3.lexema));
                break;
            }
        }
    }

    
    //  MÉTODOS AUXILIARES
    
    private Token consumir(TipoToken tipo, String mensaje) {
        if (verificar(tipo)) return avanzar();
        if (mensaje != null) throw error(mensaje + contexto());
        throw error("Se esperaba " + tipo + contexto());
    }

    private boolean verificar(TipoToken tipo) {
        return actual < tokens.size() && tokens.get(actual).tipo == tipo;
    }

    private Token avanzar() {
        ultimoConsumido = tokens.get(actual);
        return tokens.get(actual++);
    }

    private Token ver() {
        return tokens.get(actual);
    }

    /** Retorna contexto: " despues de 'X' pero se encontró 'Y'" */
    private String contexto() {
        StringBuilder sb = new StringBuilder();
        if (ultimoConsumido != null) {
            sb.append(" despues de '").append(ultimoConsumido.lexema).append("'");
        }
        if (actual < tokens.size() && tokens.get(actual).tipo != TipoToken.EOF) {
            sb.append(" pero se encontró '").append(ver().lexema).append("'");
        }
        return sb.toString();
    }

    private RuntimeException error(String mensaje) {
        String msg = "Error sintactico en linea " + ver().linea + ": " + mensaje;
        erroresSintacticosDetalle.add(new ErrorSintacticoDetalle(msg,
            ver().linea, ver().columna, ver().lexema.length()));
        return new RuntimeException(msg);
    }
}
