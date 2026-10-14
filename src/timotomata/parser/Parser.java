package timotomata.parser;

import java.util.*;
import timotomata.lexer.Token;
import timotomata.lexer.TipoToken;
import timotomata.parser.ast.*;
import timotomata.semantic.TipoSensor;


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

    public List<String> getErroresSintacticos() { return erroresSintacticos; }

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
                erroresSintacticos.add("Error sintactico en linea " + ver().linea
                    + ": Token inesperado" + contexto());
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
                || t == TipoToken.ESTADO
                || t == TipoToken.EOF) {
                return; // no consumir — es el inicio de la siguiente sentencia
            }
            avanzar();
        }
    }

    
    //  NO TERMINAL: SENTENCIA
    //  SENTENCIA → SENSOR ID : TIPO_MAGNITUD PUNTO_COMA
    //       | UMBRAL ID = VALOR_UMBRAL PUNTO_COMA
    //            | SI CONDICION ENTONCES ESTADO = ESTADO_SISTEMA PUNTO_COMA
    //            | CALCULAR ( CALCULAR_CUERPO ) PUNTO_COMA
    //            | ESTADO = ESTADO_SISTEMA PUNTO_COMA
    
    private void sentencia(NodoDerivacion nodo) {
        int prod = Gramatica.obtenerProduccion(Gramatica.SENTENCIA, ver().tipo);
        if (prod == -1) {
            throw error("Se esperaba SENSOR, UMBRAL, SI o CALCULAR" + contexto());
        }

        switch (prod) {
            case Gramatica.P_SENT_SENSOR: {
                Token t1 = consumir(TipoToken.SENSOR, "Se esperaba SENSOR");
                Token t2 = consumir(TipoToken.ID, "Se esperaba el nombre del sensor");
                Token t3 = consumir(TipoToken.DOS_PUNTOS, "Se esperaba : y el tipo del sensor");
                Token t4 = consumirTipoMagnitud("Se esperaba un tipo de sensor valido");
                Token t5 = consumir(TipoToken.PUNTO_COMA, "Se esperaba ;");

                TipoSensor tipo = TipoSensor.deLexema(t4.lexema);
                nodo.agregarHijo(t(t1));
                nodo.agregarHijo(t(t2));
                nodo.agregarHijo(t(t3));
                nodo.agregarHijo(t(t4));
                nodo.agregarHijo(t(t5));

                programa.sensores.add(t2.lexema);
                programa.sensoresDeclarados.add(new Sensor(t2.lexema, tipo, t1.linea));
                programa.simbolos.declararSensor(t2.lexema, tipo, t1.linea);
                break;
            }
            case Gramatica.P_SENT_UMBRAL: {
                Token t1 = consumir(TipoToken.UMBRAL, "Se esperaba UMBRAL");
                Token t2 = consumir(TipoToken.ID, "Se esperaba el nombre del umbral");
                Token t6 = consumir(TipoToken.DOS_PUNTOS, "Se esperaba : y el tipo del umbral");
                Token t7 = consumirTipoMagnitud("Se esperaba un tipo de sensor valido");
                Token t3 = consumir(TipoToken.ASIGNACION, "Se esperaba =");
                NodoDerivacion nVal = valorUmbral();
                Token t5 = consumir(TipoToken.PUNTO_COMA, "Se esperaba ;");
                TipoSensor tipoU = TipoSensor.deLexema(t7.lexema);
                nodo.agregarHijo(t(t1));
                nodo.agregarHijo(t(t2));
                nodo.agregarHijo(t(t6));
                nodo.agregarHijo(t(t7));
                nodo.agregarHijo(t(t3));
                nodo.agregarHijo(nVal);
                nodo.agregarHijo(t(t5));
                programa.umbrales.put(t2.lexema, (double) ultimoValorSemantico);
                programa.simbolos.declararUmbral(t2.lexema, tipoU,
                    (double) ultimoValorSemantico, t1.linea);
                break;
            }
            case Gramatica.P_SENT_SI: {
                Token t1 = consumir(TipoToken.SI, "Se esperaba SI");
                nodo.agregarHijo(t(t1));
                NodoDerivacion nCond = condicion();
                nodo.agregarHijo(nCond);
                Condicion cond = (Condicion) ultimoValorSemantico;
                Token t3 = consumir(TipoToken.ENTONCES, "Se esperaba ENTONCES");
                Token t4 = consumir(TipoToken.ESTADO, "Se esperaba ESTADO");
                Token t5 = consumir(TipoToken.ASIGNACION, "Se esperaba =");
                Token t6 = consumir(TipoToken.ESTADO_SISTEMA,
                    "Se esperaba NORMAL, PICO, CAIDA o INESTABLE");
                nodo.agregarHijo(t(t3));
                nodo.agregarHijo(t(t4));
                nodo.agregarHijo(t(t5));
                nodo.agregarHijo(t(t6));

                Regla regla = new Regla(cond, t6.lexema, t1.linea);

                // Rama sino de esta regla concreta. Es distinto del `sino = X;`
                // global: aquel dice a qué estado se vuelve el sistema cuando
                // ninguna regla se cumple; este dice a qué se vuelve cuando
                // esta regla no.
                if (ver().tipo == TipoToken.SINO) {
                    Token t8 = consumir(TipoToken.SINO, "Se esperaba SINO");
                    Token t9 = consumir(TipoToken.ESTADO, "Se esperaba ESTADO");
                    Token t10 = consumir(TipoToken.ASIGNACION, "Se esperaba =");
                    Token t11 = consumir(TipoToken.ESTADO_SISTEMA,
                        "Se esperaba NORMAL, PICO, CAIDA o INESTABLE");
                    nodo.agregarHijo(t(t8));
                    nodo.agregarHijo(t(t9));
                    nodo.agregarHijo(t(t10));
                    nodo.agregarHijo(t(t11));
                    regla.ramaSino = cond;
                    regla.estadoSino = t11.lexema;
                }

                Token t7 = consumir(TipoToken.PUNTO_COMA, "Se esperaba ;");
                nodo.agregarHijo(t(t7));
                programa.reglas.add(regla);
                break;
            }
            case Gramatica.P_SENT_CALCULAR: {
                Token t1 = consumir(TipoToken.CALCULAR, "Se esperaba CALCULAR");
                Token t2 = consumir(TipoToken.PAREN_IZQ, "Se esperaba (");
                nodo.agregarHijo(t(t1));
                nodo.agregarHijo(t(t2));

                Calculo calculo = new Calculo();
                calculo.linea = t1.linea;

                NodoDerivacion nCuerpo = new NodoDerivacion("CALCULAR_CUERPO");
                calcularCuerpo(nCuerpo, calculo);
                nodo.agregarHijo(nCuerpo);

                Token t6 = consumir(TipoToken.PAREN_DER, "Se esperaba )");
                Token t7 = consumir(TipoToken.PUNTO_COMA, "Se esperaba ;");
                nodo.agregarHijo(t(t6));
                nodo.agregarHijo(t(t7));

                programa.calculos.add(calculo);

                // Los canales de entrada también son usos. Sin esto, una señal
                // que solo se lee como CON= de otro calculo quedaría marcada
                // como no leída, y la regla 26 daría un falso positivo.
                for (String canal : calculo.canales()) {
                    programa.simbolos.registrarUso(canal, t1.linea);
                }

                // Arista del grafo de flujo de datos: el destino depende de lo
                // que la transformación lee. Para una operación nombrada son
                // sus CON=; para una expresión, las variables que aparecen. Un
                // generador no lee nada y queda sin aristas.
                programa.simbolos.registrarCalculo(
                    calculo.resultado(), calculo.origenes());
                break;
            }
            case Gramatica.P_SENT_ESTADO: {
                Token t1 = consumir(TipoToken.ESTADO, "Se esperaba ESTADO");
                Token t2 = consumir(TipoToken.ASIGNACION, "Se esperaba =");
                Token t3 = consumir(TipoToken.ESTADO_SISTEMA,
                    "Se esperaba NORMAL, PICO, CAIDA o INESTABLE");
                Token t4 = consumir(TipoToken.PUNTO_COMA, "Se esperaba ;");
                nodo.agregarHijo(t(t1));
                nodo.agregarHijo(t(t2));
                nodo.agregarHijo(t(t3));
                nodo.agregarHijo(t(t4));
                programa.estadoInicial = t3.lexema.toLowerCase();
                break;
            }
            case Gramatica.P_SENT_SINO: {
                Token t1 = consumir(TipoToken.SINO, "Se esperaba SINO");
                Token t2 = consumir(TipoToken.ASIGNACION, "Se esperaba =");
                Token t3 = consumir(TipoToken.ESTADO_SISTEMA,
                    "Se esperaba NORMAL, PICO, CAIDA o INESTABLE");
                Token t4 = consumir(TipoToken.PUNTO_COMA, "Se esperaba ;");
                nodo.agregarHijo(t(t1));
                nodo.agregarHijo(t(t2));
                nodo.agregarHijo(t(t3));
                nodo.agregarHijo(t(t4));
                programa.estadoPorDefecto = t3.lexema.toLowerCase();
                break;
            }
            case Gramatica.P_SENT_MUESTREO: {
                Token t1 = consumir(TipoToken.MUESTREO, "Se esperaba MUESTREO");
                Token t2 = consumir(TipoToken.ASIGNACION, "Se esperaba =");
                Token t3 = consumir(TipoToken.NUMERO,
                    "Se esperaba un numero de segundos por muestreo");
                Token t4 = consumir(TipoToken.PUNTO_COMA, "Se esperaba ;");
                nodo.agregarHijo(t(t1));
                nodo.agregarHijo(t(t2));
                nodo.agregarHijo(t(t3));
                nodo.agregarHijo(t(t4));
                programa.muestreo = Double.parseDouble(t3.lexema);
                programa.lineaMuestreo = t1.linea;
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
    //  CONDICION → NEGACION COMPARACION CONDICION_SIG
    //
    //  La condición se arma como una cadena: COMPARACION y, si aparece un
    //  conector, otra comparación más. `Y` y `O` comparten precedencia y se
    //  asocian a la izquierda, así que `a y b o c` es `(a y b) o c`.
    //
    //  Devuelve un Condicion, no un Expresion: un booleano no es un número y
    //  mezclarlos obligaría a cada análisis a preguntar primero de qué tipo
    //  está tratando.

    private NodoDerivacion condicion() {
        NodoDerivacion nodo = new NodoDerivacion("CONDICION");
        if (Gramatica.obtenerProduccion(Gramatica.CONDICION, ver().tipo) == -1) {
            throw error("Se esperaba una comparacion en la condicion"
                + " (expresion operador-relacional expresion)" + contexto());
        }

        Condicion c = negacion(nodo);
        nodo.agregarHijo(condicionSig(nodo, c));
        return nodo;
    }


    //  NO TERMINAL: NEGACION
    //  NEGACION → NO NEGACION | ε
    //
    //  NO se aplica solo a la comparación que viene justo después, nunca al
    //  resultado de una conjunción. `no a > 1 y b < 2` se lee
    //  `no(a > 1) y b < 2`, que es lo que se quiso escribir.

    private Condicion negacion(NodoDerivacion padre) {
        if (ver().tipo != TipoToken.NO) return comparacion(padre);

        Token t = consumir(TipoToken.NO, null);
        padre.agregarHijo(t(t));

        NodoDerivacion nNo = new NodoDerivacion("NEGACION");
        padre.agregarHijo(nNo);

        Condicion interna = negacion(nNo);
        Negado n = new Negado(interna);
        n.linea = t.linea;
        return n;
    }


    //  NO TERMINAL: COMPARACION
    //  COMPARACION → EXPRESION OP_REL EXPRESION

    private Condicion comparacion(NodoDerivacion padre) {
        NodoDerivacion nodo = new NodoDerivacion("COMPARACION");
        if (Gramatica.obtenerProduccion(Gramatica.COMPARACION, ver().tipo) == -1) {
            throw error("Se esperaba expresion en la condicion" + contexto());
        }

        int linea = ver().linea;

        NodoDerivacion nExprIzq = expresion();
        Expresion izq = (Expresion) ultimoValorSemantico;
        nodo.agregarHijo(nExprIzq);
        marcarUso(izq);

        NodoDerivacion nOpRel = operadorRelacional();
        String op = (String) ultimoValorSemantico;
        nodo.agregarHijo(nOpRel);

        NodoDerivacion nExprDer = expresion();
        Expresion der = (Expresion) ultimoValorSemantico;
        nodo.agregarHijo(nExprDer);
        marcarUso(der);

        Comparacion c = new Comparacion(izq, op, der);
        c.linea = linea;
        padre.agregarHijo(nodo);
        return c;
    }


    //  NO TERMINAL: CONDICION_SIG
    //  CONDICION_SIG → CONECTOR NEGACION COMPARACION CONDICION_SIG | ε
    //
    //  `Y` y `O` tienen la misma precedencia y se asocian a la izquierda, así
    //  que `a y b o c` queda `(a y b) o c`. Quien necesite otro criterio
    //  agrupa con paréntesis.

    private NodoDerivacion condicionSig(NodoDerivacion padre, Condicion acumulado) {
        NodoDerivacion nodo = new NodoDerivacion("CONDICION_SIG");
        padre.agregarHijo(nodo);

        // La tabla distingue "seguir por un conector" de "terminar" comparando con
// la producción concreta, no con -1: la alternativa ε de CONDICION_SIG tiene
// entradas legítimas para todo token de FOLLOW, y ENTONCES está en él.
        int prod = Gramatica.obtenerProduccion(Gramatica.CONDICION_SIG, ver().tipo);
        if (prod != Gramatica.P_COND_SIG_CON) {
            ultimoValorSemantico = acumulado;
            return nodo;
        }

        Conector.Tipo tipo = conector(nodo);
        Condicion der = negacion(nodo);

        Condicion completo = new Conector(tipo, acumulado, der);
        completo.linea = acumulado.linea;

        condicionSig(nodo, completo);
        ultimoValorSemantico = completo;
        return nodo;
    }


    //  NO TERMINAL: CONECTOR
    //  CONECTOR → Y | O

    private Conector.Tipo conector(NodoDerivacion padre) {
        NodoDerivacion nodo = new NodoDerivacion("CONECTOR");
        int prod = Gramatica.obtenerProduccion(Gramatica.CONECTOR, ver().tipo);

        Token t;
        Conector.Tipo tipo;
        switch (prod) {
            case Gramatica.P_CON_Y -> {
                t = consumir(TipoToken.Y, null);
                tipo = Conector.Tipo.Y;
            }
            case Gramatica.P_CON_O -> {
                t = consumir(TipoToken.O, null);
                tipo = Conector.Tipo.O;
            }
            default -> throw error("Error interno en CONECTOR"); // interno
        }
        nodo.agregarHijo(t(t));
        padre.agregarHijo(nodo);
        ultimoValorSemantico = tipo;
        return tipo;
    }

    /** Anota en la tabla de símbolos los identificadores leídos en una expresión. */
    private void marcarUso(Expresion e) {
        int linea = ver().linea;
        for (String v : Expressiones.variables(e)) {
            programa.simbolos.registrarUso(v, linea);
        }
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
                Binaria nuevaIzq = new Binaria(izquierda, "+", der);
                nuevaIzq.linea = t.linea;
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
                Binaria nuevaIzq = new Binaria(izquierda, "-", der);
                nuevaIzq.linea = t.linea;
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
                Binaria nuevaIzq = new Binaria(izquierda, "*", der);
                nuevaIzq.linea = t.linea;
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
                Binaria nuevaIzq = new Binaria(izquierda, "/", der);
                nuevaIzq.linea = t.linea;
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
    //  FACTOR → NUMERO | ID | ABS(...) | MENOS FACTOR | ( ... )
    //           | ESTADO | ESTADO_SISTEMA

    private NodoDerivacion factor() {
        NodoDerivacion nodo = new NodoDerivacion("FACTOR");
        int prod = Gramatica.obtenerProduccion(Gramatica.FACTOR, ver().tipo);
        if (prod == -1) {
            throw error("Se esperaba numero, identificador, abs(), parentesis, -"
                + " o estado" + contexto());
        }

        switch (prod) {
            case Gramatica.P_FACT_NUM: {
                Token t = consumir(TipoToken.NUMERO, null);
                nodo.agregarHijo(t(t));
                ultimoValorSemantico = new Numero(Double.parseDouble(t.lexema));
                ((Numero) ultimoValorSemantico).linea = t.linea;
                break;
            }
            case Gramatica.P_FACT_ID: {
                Token t = consumir(TipoToken.ID, null);
                nodo.agregarHijo(t(t));
                ultimoValorSemantico = new Variable(t.lexema);
                ((Variable) ultimoValorSemantico).linea = t.linea;
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
                ((Abs) ultimoValorSemantico).linea = t1.linea;
                break;
            }
            case Gramatica.P_FACT_MENOS: {
                Token t = consumir(TipoToken.MENOS, null);
                nodo.agregarHijo(t(t));
                NodoDerivacion nFact = factor();
                Expresion expr = (Expresion) ultimoValorSemantico;
                nodo.agregarHijo(nFact);
                ultimoValorSemantico = new Negacion(expr);
                ((Negacion) ultimoValorSemantico).linea = t.linea;
                break;
            }
            case Gramatica.P_FACT_PAREN: {
                Token tIzq = consumir(TipoToken.PAREN_IZQ, "Se esperaba (");
                nodo.agregarHijo(t(tIzq));
                NodoDerivacion nExpr = expresion();
                Expresion expr = (Expresion) ultimoValorSemantico;
                nodo.agregarHijo(nExpr);
                Token tDer = consumir(TipoToken.PAREN_DER, "Se esperaba )");
                nodo.agregarHijo(t(tDer));
                // El agrupamiento no produce un nodo propio: la expresión
                // interior ya expresa exactamente lo mismo.
                ultimoValorSemantico = expr;
                break;
            }
            case Gramatica.P_FACT_ESTADO: {
                Token t = consumir(TipoToken.ESTADO, null);
                nodo.agregarHijo(t(t));
                EstadoActual e = new EstadoActual();
                e.linea = t.linea;
                ultimoValorSemantico = e;
                break;
            }
            case Gramatica.P_FACT_LITERAL: {
                Token t = consumir(TipoToken.ESTADO_SISTEMA, null);
                nodo.agregarHijo(t(t));
                LiteralEstado e = new LiteralEstado(t.lexema.toLowerCase());
                e.linea = t.linea;
                ultimoValorSemantico = e;
                break;
            }
            default:
                throw error("Error interno en FACTOR"); // no contexto porque es interno
        }
        return nodo;
    }

//  NO TERMINAL: CALCULAR_CUERPO
    //  CALCULAR_CUERPO → TIPO_OP COMA ID
    //                  | EXPRESION COMA ID
    //
    //  Dos caminos: una operación nombrada de las seis, o una expresión
    //  arbitraria. Se distinguen por el primer token: las operaciones empiezan
    //  por su palabra reservada y las expresiones por ABS, ID, NUMERO, MENOS o
    //  PAREN_IZQ. Los conjuntos son disjuntos, así que la decisión es LL(1).
    //
    //  El canal de entrada no aparece aquí: es el parámetro CON= de la
    //  operación, o las variables de la expresión. Por eso el destino es
    //  siempre obligatorio.

    private void calcularCuerpo(NodoDerivacion nodo, Calculo calculo) {
        int prod = Gramatica.obtenerProduccion(Gramatica.CALCULAR_CUERPO, ver().tipo);

        switch (prod) {
            case Gramatica.P_CALC_OP_EXPR: {
                NodoDerivacion nOp = new NodoDerivacion("TIPO_OP");
                tipoOp(nOp, calculo);
                nodo.agregarHijo(nOp);
                break;
            }
            case Gramatica.P_CALC_EXPR_EXPR: {
                NodoDerivacion nExpr = expresion();
                calculo.expresion = (Expresion) ultimoValorSemantico;
                marcarUso(calculo.expresion);
                nodo.agregarHijo(nExpr);
                break;
            }
            default:
                throw error("Se esperaba una operacion (SENO, COSENO, CUADRADA,"
                    + " PROMEDIO, MAXIMO, SUMA) o una expresion" + contexto());
        }

        Token coma = consumir(TipoToken.COMA, "Se esperaba , antes del destino");
        Token destino = consumir(TipoToken.ID,
            "Se esperaba el nombre de la señal de destino");
        nodo.agregarHijo(t(coma));
        nodo.agregarHijo(t(destino));
        calculo.destino = destino.lexema;

        // El destino ES la declaración de la señal. El tipo se resuelve en la
        // fase semántica, cuando ya se conoce la firma de la operación y el
        // tipo del canal de entrada.
        programa.simbolos.declararSenal(destino.lexema, null, calculo.linea);
    }


    //  NO TERMINAL: TIPO_OP
    //  TIPO_OP → SENO PAREN_IZQ LISTA_PARAMS PAREN_DER | …
    //
    //  Solo operaciones nombradas. Las expresiones van por CALCULAR_CUERPO.

    private void tipoOp(NodoDerivacion nodo, Calculo calculo) {
        int prod = Gramatica.obtenerProduccion(Gramatica.TIPO_OP, ver().tipo);

        if (prod == -1) {
            throw error("Se esperaba SENO, COSENO, CUADRADA, PROMEDIO,"
                + " MAXIMO o SUMA" + contexto());
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

        // La firma de cada operación (cantidad/tipos de parámetros) se valida
        // en la fase semántica (AnalizadorSemantico), como error de llamada.
        ultimoValorSemantico = opStr;
    }
    //  NO TERMINAL: TIPO_MAGNITUD
    //  TIPO_MAGNITUD → ELECTRICO | TERMICO | LUMINICO
    //               | MECANICO | QUIMICO | HIDRAULICO

    private Token consumirTipoMagnitud(String mensaje) {
        int prod = Gramatica.obtenerProduccion(Gramatica.TIPO_MAGNITUD, ver().tipo);
        if (prod == -1) throw error(mensaje + contexto());
        return avanzar();
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
        return new RuntimeException("Error sintactico en linea " + ver().linea + ": " + mensaje);
    }
}
