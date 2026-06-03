package timotomata.parser;

import java.util.*;
import timotomata.lexer.*;
import timotomata.parser.ast.*;

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
    public List<ErrorInfo> erroresSintacticos = new ArrayList<>();

    public List<ErrorInfo> getErroresSintacticos() { return erroresSintacticos; }

    public Parser(List<Token> tokens) {
        // Filtrar comentarios y tokens desconocidos antes del parseo
        this.tokens = new ArrayList<>();
        for (Token t : tokens) {
            if (t.tipo != TipoToken.COMENTARIO && t.tipo != TipoToken.DESCONOCIDO) {
                this.tokens.add(t);
            }
        }
    }

    // =============================================================
    //  PUNTO DE ENTRADA
    // =============================================================
    public Programa parsear() {
        programa = new Programa();
        erroresSintacticos.clear();

        // Pre-validación: detectar llaves y paréntesis desbalanceados
        verificarBalance();
        // Pre-validación: detectar ';' faltantes
        verificarPuntoComaFaltante();

        NodoDerivacion raiz = new NodoDerivacion("PROGRAMA");
        try {
            programa(raiz);  // PROGRAMA → SENTENCIA PROGRAMA | ε
        } catch (ErrorSintactico e) {
            erroresSintacticos.add(e.getInfo());
        }
        arbolDerivacion = raiz;
        return programa;
    }

    // =============================================================
    //  PRE-VALIDACIÓN: Detectar llaves y paréntesis desbalanceados
    // =============================================================
    private void verificarBalance() {
        Stack<TipoToken> pila = new Stack<>();
        for (Token t : tokens) {
            if (t.tipo == TipoToken.PAREN_IZQ || t.tipo == TipoToken.LLAVE_IZQ) {
                pila.push(t.tipo);
            } else if (t.tipo == TipoToken.PAREN_DER) {
                if (pila.isEmpty() || pila.peek() != TipoToken.PAREN_IZQ) {
                    erroresSintacticos.add(new ErrorInfo(TablaErrores.P001, t.linea, t.columna));
                    return;
                }
                pila.pop();
            } else if (t.tipo == TipoToken.LLAVE_DER) {
                if (pila.isEmpty() || pila.peek() != TipoToken.LLAVE_IZQ) {
                    erroresSintacticos.add(new ErrorInfo(TablaErrores.P002, t.linea, t.columna));
                    return;
                }
                pila.pop();
            }
        }

        if (!pila.isEmpty()) {
            // Buscar la posición del último token antes de EOF para reportar la línea correcta
            TipoToken falta = pila.peek();
            String esperado = (falta == TipoToken.PAREN_IZQ) ? ")" : "}";
            Token ultimoToken = tokens.isEmpty() ? null : tokens.get(tokens.size() - 1);
            int lineaReporte = ultimoToken != null ? ultimoToken.linea : 1;
            erroresSintacticos.add(new ErrorInfo(TablaErrores.P003, lineaReporte, 0, esperado, ultimoToken != null ? ultimoToken.lexema : ""));
        }
    }

    private String contextoEnLinea(Token token) {
        StringBuilder sb = new StringBuilder();
        int idx = tokens.indexOf(token);
        if (idx > 0) {
            Token anterior = tokens.get(idx - 1);
            sb.append(" después de '").append(anterior.lexema).append("'");
        }
        return sb.toString();
    }

    //  AUXILIAR: crear nodo terminal para un token consumido
    //  Estructura: nodo azul (categoría gramatical) → nodo verde (lexema)
    private NodoDerivacion t(Token token) {
        NodoDerivacion nodoCat = new NodoDerivacion(token.tipo.name());
        NodoDerivacion nodoLex = new NodoDerivacion(token.lexema);
        nodoLex.lexema = token.lexema;
        nodoCat.agregarHijo(nodoLex);
        return nodoCat;
    }

    // =============================================================
    //  NO TERMINAL: PROGRAMA
    // =============================================================
    private void programa(NodoDerivacion nodo) {
        while (actual < tokens.size()) {
            int prod = Gramatica.obtenerProduccion(Gramatica.PROGRAMA, ver().tipo);
            if (prod == Gramatica.P_PROGRAMA_EPS) {
                return; // ε — fin normal del programa
            }
            if (prod == -1) {
                // Token que no pertenece a PROGRAMA — error de sincronización
                if (actual < tokens.size()) {
                    tokens.get(actual).tieneError = true;
                }
                sugerirErrorInesperado();
                avanzar();
                sincronizar();
                continue;
            }

            // P_PROGRAMA_REC: PROGRAMA → SENTENCIA PROGRAMA
            NodoDerivacion nSent = new NodoDerivacion("SENTENCIA");
            try {
                sentencia(nSent);
                nodo.agregarHijo(nSent);
            } catch (ErrorSintactico e) {
                erroresSintacticos.add(e.getInfo());
                sincronizar();
                continue;
            }
            NodoDerivacion nProg = new NodoDerivacion("PROGRAMA");
            programa(nProg);
            nodo.agregarHijo(nProg);
            return;
        }
    }

    // =============================================================
    //  MODO PÁNICO — Sincronización mejorada con puntuación
    // =============================================================
    private void sincronizar() {
        int profundidadParentesis = 0;
        int profundidadLlaves = 0;

        while (actual < tokens.size()) {
            TipoToken t = tokens.get(actual).tipo;

            // Actualizar profundidad actual
            if (t == TipoToken.PAREN_IZQ) profundidadParentesis++;
            else if (t == TipoToken.PAREN_DER) {
                if (profundidadParentesis > 0) profundidadParentesis--;
                else { avanzar(); return; } // ')' fuera de contexto → sincronizar
            }
            else if (t == TipoToken.LLAVE_IZQ) profundidadLlaves++;
            else if (t == TipoToken.LLAVE_DER) {
                if (profundidadLlaves > 0) profundidadLlaves--;
                else { avanzar(); return; } // '}' fuera de contexto → sincronizar
            }

            // Solo sincronizar si estamos en nivel 0 de anidamiento
            if (profundidadParentesis == 0 && profundidadLlaves == 0) {
                if (t == TipoToken.PUNTO_COMA) {
                    avanzar(); // consumir el ;
                    return;
                }
                if (esInicioSentencia(t) || t == TipoToken.EOF) {
                    return; // no consumir — es el inicio de la siguiente sentencia
                }
            }
            avanzar();
        }
    }

    private boolean esInicioSentencia(TipoToken t) {
        return t == TipoToken.SENSOR || t == TipoToken.UMBRAL || t == TipoToken.RANGO
            || t == TipoToken.SI || t == TipoToken.CALCULAR || t == TipoToken.FIN;
    }

    // =============================================================
    //  SUGERENCIAS PARA ERRORES COMUNES
    // =============================================================
    private String sugerirErrorInesperado() {
        Token actual_ = ver();
        // No duplicar error si el lexer ya detectó una palabra mal escrita (L006)
        if (!actual_.tieneSugerencia) {
            erroresSintacticos.add(new ErrorInfo(TablaErrores.P006, actual_.linea, actual_.columna, actual_.lexema));
        }
        String msg = "";

        // Sugerencias contextuales para errores comunes
        if (ultimoConsumido != null) {
            TipoToken ultimo = ultimoConsumido.tipo;
            TipoToken ahora = actual_.tipo;

            // Falta ';' después de sentencia
            if ((ultimo == TipoToken.PUNTO_COMA || ultimo == TipoToken.ID
                || ultimo == TipoToken.NUMERO || ultimo == TipoToken.CADENA
                || ultimo == TipoToken.ESTADO_SISTEMA || ultimo == TipoToken.RANGO
                || ultimo == TipoToken.SENSOR || ultimo == TipoToken.UMBRAL)
                && esInicioSentencia(ahora)) {
                msg += "\n  ¿Falta ';' después de '" + ultimoConsumido.lexema + "'?";
            }
            // Falta 'entonces' después de condición
            else if (ultimo == TipoToken.ID || ultimo == TipoToken.NUMERO
                || ultimo == TipoToken.PAREN_DER) {
                if (ahora == TipoToken.LLAVE_IZQ || ahora == TipoToken.ESTADO
                    || ahora == TipoToken.ALERTA) {
                    msg += "\n  ¿Falta 'entonces' después de la condición?";
                }
            }
            // '=' inesperado
            else if (ahora == TipoToken.ASIGNACION && ultimo == TipoToken.ASIGNACION) {
                msg += "\n  ¿Quiso escribir '==' (comparación)?";
            }
        }

        return msg;
    }

    private void agregarError(TablaErrores codigo, int linea, int columna, Object... args) {
        erroresSintacticos.add(new ErrorInfo(codigo, linea, columna, args));
    }



    // =============================================================
    //  NO TERMINAL: SENTENCIA
    // =============================================================
    private void sentencia(NodoDerivacion nodo) {
        int prod = Gramatica.obtenerProduccion(Gramatica.SENTENCIA, ver().tipo);
        if (prod == -1) {
            if (actual < tokens.size()) {
                tokens.get(actual).tieneError = true;
            }
            throw error(TablaErrores.P007, ver().lexema);
        }

        switch (prod) {
            case Gramatica.P_SENT_SENSOR: {
                Token t1 = consumir(TipoToken.SENSOR, "Se esperaba la palabra reservada 'sensor'");
                Token t2 = consumir(TipoToken.ID, "Se esperaba un identificador (nombre del sensor)");

                NodoDerivacion nTipoOpc = new NodoDerivacion("TIPO_OPC");
                tipoOpc(nTipoOpc, t2.lexema);

                Token t3 = consumir(TipoToken.PUNTO_COMA, "Se esperaba ';' para cerrar la declaración del sensor");
                nodo.agregarHijo(t(t1));
                nodo.agregarHijo(t(t2));
                nodo.agregarHijo(nTipoOpc);
                nodo.agregarHijo(t(t3));
                programa.sensores.add(t2.lexema);
                break;
            }
            case Gramatica.P_SENT_UMBRAL: {
                Token t1 = consumir(TipoToken.UMBRAL, "Se esperaba la palabra reservada 'umbral'");
                Token t2 = consumir(TipoToken.ID, "Se esperaba un identificador (nombre del umbral)");
                Token t3 = consumir(TipoToken.ASIGNACION, "Se esperaba '=' después del nombre del umbral");
                NodoDerivacion nVal = valorUmbral();
                Token t5 = consumir(TipoToken.PUNTO_COMA, "Se esperaba ';' para cerrar la declaración del umbral");
                nodo.agregarHijo(t(t1));
                nodo.agregarHijo(t(t2));
                nodo.agregarHijo(t(t3));
                nodo.agregarHijo(nVal);
                nodo.agregarHijo(t(t5));
                programa.umbrales.put(t2.lexema, (double) ultimoValorSemantico);
                break;
            }
            case Gramatica.P_SENT_RANGO: {
                Token t1 = consumir(TipoToken.RANGO, "Se esperaba la palabra reservada 'rango'");
                Token t2 = consumir(TipoToken.ID, "Se esperaba un identificador (nombre del sensor)");
                Token t3 = consumir(TipoToken.MINIMO, "Se esperaba la palabra reservada 'minimo'");
                Token t4 = consumir(TipoToken.ASIGNACION, "Se esperaba '=' después de 'minimo'");

                // Aceptar número negativo: MENOS NUMERO o solo NUMERO
                boolean minNeg = verificar(TipoToken.MENOS);
                Token tMinSigno = minNeg ? consumir(TipoToken.MENOS, "") : null;
                Token t5 = consumir(TipoToken.NUMERO, "Se esperaba un número para el valor mínimo");

                Token t6 = consumir(TipoToken.MAXIMO, "Se esperaba la palabra reservada 'maximo'");
                Token t7 = consumir(TipoToken.ASIGNACION, "Se esperaba '=' después de 'maximo'");

                // Aceptar número negativo: MENOS NUMERO o solo NUMERO
                boolean maxNeg = verificar(TipoToken.MENOS);
                Token tMaxSigno = maxNeg ? consumir(TipoToken.MENOS, "") : null;
                Token t8 = consumir(TipoToken.NUMERO, "Se esperaba un número para el valor máximo");

                Token t9 = consumir(TipoToken.PUNTO_COMA, "Se esperaba ';' para cerrar la declaración de rango");

                // Agregar hijos en orden izquierda-derecha
                nodo.agregarHijo(t(t1));
                nodo.agregarHijo(t(t2));
                nodo.agregarHijo(t(t3));
                nodo.agregarHijo(t(t4));
                if (minNeg) nodo.agregarHijo(t(tMinSigno));
                nodo.agregarHijo(t(t5));
                nodo.agregarHijo(t(t6));
                nodo.agregarHijo(t(t7));
                if (maxNeg) nodo.agregarHijo(t(tMaxSigno));
                nodo.agregarHijo(t(t8));
                nodo.agregarHijo(t(t9));

                double minVal = Double.parseDouble(t5.lexema);
                if (minNeg) minVal = -minVal;
                double maxVal = Double.parseDouble(t8.lexema);
                if (maxNeg) maxVal = -maxVal;
                programa.rangos.put(t2.lexema, new Programa.RangoSeguro(t2.lexema, minVal, maxVal));
                break;
            }
            case Gramatica.P_SENT_SI: {
                Token t1 = consumir(TipoToken.SI, "Se esperaba la palabra reservada 'si'");
                nodo.agregarHijo(t(t1));
                NodoDerivacion nCond = condicion();
                nodo.agregarHijo(nCond);
                Expresion cond = (Expresion) ultimoValorSemantico;

                Token t3 = consumir(TipoToken.ENTONCES,
                    "Se esperaba la palabra reservada 'entonces' después de la condición");
                nodo.agregarHijo(t(t3));

                NodoDerivacion nConsec = new NodoDerivacion("CONSECUENCIA");
                consecuencia(nConsec, cond);
                nodo.agregarHijo(nConsec);
                break;
            }
            case Gramatica.P_SENT_CALCULAR: {
                Token t1 = consumir(TipoToken.CALCULAR, "Se esperaba la palabra reservada 'calcular'");
                nodo.agregarHijo(t(t1));

                NodoDerivacion nAux = new NodoDerivacion("AUX_CALCULAR");
                auxCalcular(nAux);
                nodo.agregarHijo(nAux);
                break;
            }
            case Gramatica.P_SENT_FIN: {
                Token t1 = consumir(TipoToken.FIN, "Se esperaba la palabra reservada 'fin'");
                Token t2 = consumir(TipoToken.PUNTO_COMA, "Se esperaba ';' después de 'fin'");
                nodo.agregarHijo(t(t1));
                nodo.agregarHijo(t(t2));
                break;
            }
        }
    }

    // TIPO_OPC → TIPO TIPO_VAL | ε
    private void tipoOpc(NodoDerivacion nodo, String sensorName) {
        int prod = Gramatica.obtenerProduccion(Gramatica.TIPO_OPC, ver().tipo);
        if (prod == Gramatica.P_TIPO_OPC_TIPO) {
            Token t1 = consumir(TipoToken.TIPO, "Se esperaba la palabra reservada 'tipo'");
            Token t2 = ver();
            int pVal = Gramatica.obtenerProduccion(Gramatica.TIPO_VAL, ver().tipo);
            if (pVal == -1) {
                if (actual < tokens.size()) {
                    tokens.get(actual).tieneError = true;
                }
                throw error(TablaErrores.P008, ver().lexema);
            }
            avanzar();
            nodo.agregarHijo(t(t1));
            nodo.agregarHijo(t(t2));
            programa.tiposSensores.put(sensorName, t2.lexema.toLowerCase());
        }
    }

    // AUX_CALCULAR → ( ID , TIPO_OP ) ; | FUNC_ANALISIS ( ID PARAMS_ANALISIS ) ;
    private void auxCalcular(NodoDerivacion nodo) {
        int prod = Gramatica.obtenerProduccion(Gramatica.AUX_CALCULAR, ver().tipo);
        if (prod == -1) {
            if (actual < tokens.size()) {
                tokens.get(actual).tieneError = true;
            }
            throw error(TablaErrores.P017, ver().lexema);
        }
        if (prod == Gramatica.P_AUX_CALCULAR_OLD) {
            Token t1 = consumir(TipoToken.PAREN_IZQ, "Se esperaba '('");
            Token t2 = consumir(TipoToken.ID, "Se esperaba un identificador (nombre del sensor)");
            Token t3 = consumir(TipoToken.COMA, "Se esperaba ',' después del nombre del sensor");
            nodo.agregarHijo(t(t1));
            nodo.agregarHijo(t(t2));
            nodo.agregarHijo(t(t3));

            Calculo calculo = new Calculo(t2.lexema, "");

            NodoDerivacion nTipoOp = new NodoDerivacion("TIPO_OP");
            tipoOp(nTipoOp, calculo);
            nodo.agregarHijo(nTipoOp);

            Token t5 = consumir(TipoToken.PAREN_DER, "Se esperaba ')' de cierre");
            Token t6 = consumir(TipoToken.PUNTO_COMA, "Se esperaba ';' para cerrar el statement de calcular");
            nodo.agregarHijo(t(t5));
            nodo.agregarHijo(t(t6));

            programa.calculos.add(calculo);
        } else {
            NodoDerivacion nFunc = new NodoDerivacion("FUNC_ANALISIS");
            String funcName = funcAnalisis(nFunc);
            nodo.agregarHijo(nFunc);

            Token t1 = consumir(TipoToken.PAREN_IZQ, "Se esperaba '('");
            Token t2 = consumir(TipoToken.ID, "Se esperaba un identificador (nombre del sensor)");
            nodo.agregarHijo(t(t1));
            nodo.agregarHijo(t(t2));

            Calculo calculo = new Calculo(t2.lexema, funcName);

            NodoDerivacion nParamsAn = new NodoDerivacion("PARAMS_ANALISIS");
            paramsAnalisis(nParamsAn, calculo);
            nodo.agregarHijo(nParamsAn);

            Token t3 = consumir(TipoToken.PAREN_DER, "Se esperaba ')' de cierre");
            Token t4 = consumir(TipoToken.PUNTO_COMA, "Se esperaba ';' para cerrar el statement de calcular");
            nodo.agregarHijo(t(t3));
            nodo.agregarHijo(t(t4));

            programa.calculos.add(calculo);
        }
    }

    private String funcAnalisis(NodoDerivacion nodo) {
        int prod = Gramatica.obtenerProduccion(Gramatica.FUNC_ANALISIS, ver().tipo);
        if (prod == -1) {
            if (actual < tokens.size()) {
                tokens.get(actual).tieneError = true;
            }
            throw error(TablaErrores.P018, ver().lexema);
        }
        Token t = avanzar();
        nodo.agregarHijo(t(t));
        return t.lexema.toLowerCase();
    }

    private void paramsAnalisis(NodoDerivacion nodo, Calculo calculo) {
        int prod = Gramatica.obtenerProduccion(Gramatica.PARAMS_ANALISIS, ver().tipo);
        if (prod == Gramatica.P_PARAMS_AN_COMA) {
            Token t1 = consumir(TipoToken.COMA, "Se esperaba ','");
            Token t2 = consumir(TipoToken.VENTANA, "Se esperaba la palabra reservada 'ventana'");
            Token t3 = consumir(TipoToken.ASIGNACION, "Se esperaba '=' después de 'ventana'");
            Token t4 = consumir(TipoToken.NUMERO, "Se esperaba un número para el tamaño de ventana");

            nodo.agregarHijo(t(t1));
            nodo.agregarHijo(t(t2));
            nodo.agregarHijo(t(t3));
            nodo.agregarHijo(t(t4));

            calculo.parametros.add(new Parametro("ventana", t4.lexema));
        }
    }

    // CONSECUENCIA → ACCION | { ACCIONES }
    private void consecuencia(NodoDerivacion nodo, Expresion cond) {
        int prod = Gramatica.obtenerProduccion(Gramatica.CONSECUENCIA, ver().tipo);
        if (prod == -1) {
            if (actual < tokens.size()) {
                tokens.get(actual).tieneError = true;
            }
            throw error(TablaErrores.P015, ver().lexema);
        }
        if (prod == Gramatica.P_CONSEC_ACCION) {
            NodoDerivacion nAccion = new NodoDerivacion("ACCION");
            Map<String, String> actionData = new HashMap<>();
            accion(nAccion, actionData);
            nodo.agregarHijo(nAccion);

            String estado = actionData.get("estado");
            String alerta = actionData.get("alerta");
            programa.reglas.add(new Regla(cond, estado, alerta));
        } else {
            Token t1 = consumir(TipoToken.LLAVE_IZQ, "Se esperaba '{' para iniciar bloque de acciones");
            nodo.agregarHijo(t(t1));

            NodoDerivacion nAcciones = new NodoDerivacion("ACCIONES");
            List<Map<String, String>> actionsList = new ArrayList<>();
            acciones(nAcciones, actionsList);
            nodo.agregarHijo(nAcciones);

            Token t2 = consumir(TipoToken.LLAVE_DER, "Se esperaba '}' para cerrar bloque de acciones");
            nodo.agregarHijo(t(t2));

            String estado = null;
            String alerta = null;
            for (Map<String, String> act : actionsList) {
                if (act.containsKey("estado")) estado = act.get("estado");
                if (act.containsKey("alerta")) alerta = act.get("alerta");
            }
            programa.reglas.add(new Regla(cond, estado, alerta));
        }
    }

    private void accion(NodoDerivacion nodo, Map<String, String> actionData) {
        int prod = Gramatica.obtenerProduccion(Gramatica.ACCION, ver().tipo);
        if (prod == -1) {
            if (actual < tokens.size()) {
                tokens.get(actual).tieneError = true;
            }
            throw error(TablaErrores.P016, ver().lexema);
        }
        if (prod == Gramatica.P_ACCION_ESTADO) {
            Token t1 = consumir(TipoToken.ESTADO, "Se esperaba la palabra reservada 'estado'");
            Token t2 = consumir(TipoToken.ASIGNACION, "Se esperaba '=' después de 'estado'");
            Token t3 = consumir(TipoToken.ESTADO_SISTEMA, "Se esperaba NORMAL, PICO, CAIDA o INESTABLE");
            Token t4 = consumir(TipoToken.PUNTO_COMA, "Se esperaba ';' para cerrar la acción");
            nodo.agregarHijo(t(t1));
            nodo.agregarHijo(t(t2));
            nodo.agregarHijo(t(t3));
            nodo.agregarHijo(t(t4));
            actionData.put("estado", t3.lexema);
        } else {
            Token t1 = consumir(TipoToken.ALERTA, "Se esperaba la palabra reservada 'alerta'");
            Token t2 = consumir(TipoToken.ASIGNACION, "Se esperaba '=' después de 'alerta'");
            Token t3 = consumir(TipoToken.CADENA, "Se esperaba una cadena de texto entre comillas dobles");
            Token t4 = consumir(TipoToken.PUNTO_COMA, "Se esperaba ';' para cerrar la acción");
            nodo.agregarHijo(t(t1));
            nodo.agregarHijo(t(t2));
            nodo.agregarHijo(t(t3));
            nodo.agregarHijo(t(t4));

            String rawStr = t3.lexema;
            if (rawStr.startsWith("\"") && rawStr.endsWith("\"") && rawStr.length() >= 2) {
                rawStr = rawStr.substring(1, rawStr.length() - 1);
            }
            actionData.put("alerta", rawStr);
        }
    }

    private void acciones(NodoDerivacion nodo, List<Map<String, String>> actionsList) {
        NodoDerivacion nAccion = new NodoDerivacion("ACCION");
        Map<String, String> actionData = new HashMap<>();
        accion(nAccion, actionData);
        nodo.agregarHijo(nAccion);
        actionsList.add(actionData);

        NodoDerivacion nRest = new NodoDerivacion("ACCIONES_REST");
        accionesRest(nRest, actionsList);
        nodo.agregarHijo(nRest);
    }

    private void accionesRest(NodoDerivacion nodo, List<Map<String, String>> actionsList) {
        int prod = Gramatica.obtenerProduccion(Gramatica.ACCIONES_REST, ver().tipo);
        if (prod == Gramatica.P_ACC_REST_REC) {
            NodoDerivacion nAccion = new NodoDerivacion("ACCION");
            Map<String, String> actionData = new HashMap<>();
            accion(nAccion, actionData);
            nodo.agregarHijo(nAccion);
            actionsList.add(actionData);

            NodoDerivacion nRest = new NodoDerivacion("ACCIONES_REST");
            accionesRest(nRest, actionsList);
            nodo.agregarHijo(nRest);
        }
    }

    // =============================================================
    //  NO TERMINAL: VALOR_UMBRAL
    // =============================================================
    private NodoDerivacion valorUmbral() {
        NodoDerivacion nodo = new NodoDerivacion("VALOR_UMBRAL");
        int prod = Gramatica.obtenerProduccion(Gramatica.VALOR_UMBRAL, ver().tipo);
        if (prod == -1) {
            if (actual < tokens.size()) {
                tokens.get(actual).tieneError = true;
            }
            throw error(TablaErrores.P021, ver().lexema);
        }

        switch (prod) {
            case Gramatica.P_VALOR_NEG: {
                Token t1 = consumir(TipoToken.MENOS, "Se esperaba '-' para valor negativo");
                Token t2 = consumir(TipoToken.NUMERO, "Se esperaba un número después de '-'");
                nodo.agregarHijo(t(t1));
                nodo.agregarHijo(t(t2));
                ultimoValorSemantico = -Double.parseDouble(t2.lexema);
                break;
            }
            case Gramatica.P_VALOR_POS: {
                Token t = consumir(TipoToken.NUMERO, "Se esperaba un número");
                nodo.agregarHijo(t(t));
                ultimoValorSemantico = Double.parseDouble(t.lexema);
                break;
            }
        }
        return nodo;
    }

    // =============================================================
    //  NO TERMINAL: CONDICION (Compuesta)
    // =============================================================
    private NodoDerivacion condicion() {
        NodoDerivacion nodo = new NodoDerivacion("CONDICION");
        NodoDerivacion nSimple = condSimple();
        nodo.agregarHijo(nSimple);
        Expresion expr = (Expresion) ultimoValorSemantico;

        NodoDerivacion nComp = new NodoDerivacion("COND_COMPUESTA");
        condCompuesta(nComp, expr);
        nodo.agregarHijo(nComp);
        return nodo;
    }

    private NodoDerivacion condSimple() {
        NodoDerivacion nodo = new NodoDerivacion("COND_SIMPLE");
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

    private void condCompuesta(NodoDerivacion nodo, Expresion izquierda) {
        int prod = Gramatica.obtenerProduccion(Gramatica.COND_COMPUESTA, ver().tipo);
        if (prod == Gramatica.P_COND_COMP_LOG) {
            NodoDerivacion nLog = new NodoDerivacion("LOG_OP");
            String op = logOp(nLog);
            nodo.agregarHijo(nLog);

            NodoDerivacion nSimple = condSimple();
            Expresion der = (Expresion) ultimoValorSemantico;
            nodo.agregarHijo(nSimple);

            Expresion nuevaIzq = new Binaria(izquierda, op, der);

            NodoDerivacion nComp = new NodoDerivacion("COND_COMPUESTA");
            condCompuesta(nComp, nuevaIzq);
            nodo.agregarHijo(nComp);
            ultimoValorSemantico = nuevaIzq;
        } else {
            ultimoValorSemantico = izquierda;
        }
    }

    private String logOp(NodoDerivacion nodo) {
        int prod = Gramatica.obtenerProduccion(Gramatica.LOG_OP, ver().tipo);
        if (prod == -1) {
            if (actual < tokens.size()) {
                tokens.get(actual).tieneError = true;
            }
            throw error(TablaErrores.P019, ver().lexema);
        }
        Token t = avanzar();
        nodo.agregarHijo(t(t));
        return t.lexema.toLowerCase();
    }

    // =============================================================
    //  NO TERMINAL: OP_REL
    // =============================================================
    private NodoDerivacion operadorRelacional() {
        NodoDerivacion nodo = new NodoDerivacion("OP_REL");
        int prod = Gramatica.obtenerProduccion(Gramatica.OP_REL, ver().tipo);
        if (prod == -1) {
            if (actual < tokens.size()) {
                tokens.get(actual).tieneError = true;
            }
            throw error(TablaErrores.P020, ver().lexema);
        }

        Token op;
        String opStr;
        switch (prod) {
            case Gramatica.P_OP_MAYOR:
                op = consumir(TipoToken.MAYOR, "Se esperaba '>'");
                opStr = ">";
                break;
            case Gramatica.P_OP_MENOR:
                op = consumir(TipoToken.MENOR, "Se esperaba '<'");
                opStr = "<";
                break;
            case Gramatica.P_OP_IGUAL:
                op = consumir(TipoToken.IGUAL_IGUAL, "Se esperaba '=='");
                opStr = "==";
                break;
            case Gramatica.P_OP_MAYIG:
                op = consumir(TipoToken.MAYOR_IGUAL, "Se esperaba '>='");
                opStr = ">=";
                break;
            case Gramatica.P_OP_MENIG:
                op = consumir(TipoToken.MENOR_IGUAL, "Se esperaba '<='");
                opStr = "<=";
                break;
            case Gramatica.P_OP_DIF:
                op = consumir(TipoToken.DIFERENTE, "Se esperaba '!=' o '<>'");
                opStr = "!=";
                break;
            default:
                throw error("Error interno en OP_REL: producción no reconocida");
        }
        nodo.agregarHijo(t(op));
        ultimoValorSemantico = opStr;
        return nodo;
    }

    // =============================================================
    //  NO TERMINAL: EXPRESION
    // =============================================================
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

    // EXPRESION_SIG
    private NodoDerivacion expresionSig(Expresion izquierda) {
        NodoDerivacion nodo = new NodoDerivacion("EXPRESION_SIG");
        int prod = Gramatica.obtenerProduccion(Gramatica.EXPRESION_SIG, ver().tipo);

        switch (prod) {
            case Gramatica.P_EXPR_SIG_MAS: {
                Token t = consumir(TipoToken.MAS, "Se esperaba '+'");
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
                Token t = consumir(TipoToken.MENOS, "Se esperaba '-'");
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
                ultimoValorSemantico = izquierda;
                break;
        }
        return nodo;
    }

    // =============================================================
    //  NO TERMINAL: TERMINO
    // =============================================================
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

    // TERMINO_SIG
    private NodoDerivacion terminoSig(Expresion izquierda) {
        NodoDerivacion nodo = new NodoDerivacion("TERMINO_SIG");
        int prod = Gramatica.obtenerProduccion(Gramatica.TERMINO_SIG, ver().tipo);

        switch (prod) {
            case Gramatica.P_TERM_SIG_POR: {
                Token t = consumir(TipoToken.POR, "Se esperaba '*'");
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
                Token t = consumir(TipoToken.DIV, "Se esperaba '/'");
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

    // =============================================================
    //  NO TERMINAL: FACTOR
    // =============================================================
    private NodoDerivacion factor() {
        NodoDerivacion nodo = new NodoDerivacion("FACTOR");
        int prod = Gramatica.obtenerProduccion(Gramatica.FACTOR, ver().tipo);
        if (prod == -1) {
            if (actual < tokens.size()) {
                tokens.get(actual).tieneError = true;
            }
            throw error(TablaErrores.P022, ver().lexema);
        }

        switch (prod) {
            case Gramatica.P_FACT_NUM: {
                Token t = consumir(TipoToken.NUMERO, "Se esperaba un número");
                nodo.agregarHijo(t(t));
                ultimoValorSemantico = new Numero(Double.parseDouble(t.lexema));
                break;
            }
            case Gramatica.P_FACT_ID: {
                Token t = consumir(TipoToken.ID, "Se esperaba un identificador");
                nodo.agregarHijo(t(t));
                ultimoValorSemantico = new Variable(t.lexema);
                break;
            }
            case Gramatica.P_FACT_ABS: {
                Token t1 = consumir(TipoToken.ABS, "Se esperaba 'abs'");
                Token t2 = consumir(TipoToken.PAREN_IZQ, "Se esperaba '(' después de 'abs'");
                nodo.agregarHijo(t(t1));
                nodo.agregarHijo(t(t2));
                NodoDerivacion nExpr = expresion();
                Expresion expr = (Expresion) ultimoValorSemantico;
                nodo.agregarHijo(nExpr);
                Token t3 = consumir(TipoToken.PAREN_DER, "Se esperaba ')' de cierre de 'abs()'");
                nodo.agregarHijo(t(t3));
                ultimoValorSemantico = new Abs(expr);
                break;
            }
            case Gramatica.P_FACT_MENOS: {
                Token t = consumir(TipoToken.MENOS, "Se esperaba '-' (negación)");
                nodo.agregarHijo(t(t));
                NodoDerivacion nFact = factor();
                Expresion expr = (Expresion) ultimoValorSemantico;
                nodo.agregarHijo(nFact);
                ultimoValorSemantico = new Negacion(expr);
                break;
            }
            default:
                throw error("Error interno en FACTOR: producción no reconocida");
        }
        return nodo;
    }

    // =============================================================
    //  NO TERMINAL: TIPO_OP
    // =============================================================
    private void tipoOp(NodoDerivacion nodo, Calculo calculo) {
        int prod = Gramatica.obtenerProduccion(Gramatica.TIPO_OP, ver().tipo);
        if (prod == -1) {
            if (actual < tokens.size()) {
                tokens.get(actual).tieneError = true;
            }
            throw error(TablaErrores.P023, ver().lexema);
        }

        Token t;
        String opStr;
        switch (prod) {
            case Gramatica.P_TIPO_SENO:
                t = consumir(TipoToken.SENO, "Se esperaba 'seno'"); opStr = "seno"; break;
            case Gramatica.P_TIPO_COSENO:
                t = consumir(TipoToken.COSENO, "Se esperaba 'coseno'"); opStr = "coseno"; break;
            case Gramatica.P_TIPO_CUADRADA:
                t = consumir(TipoToken.CUADRADA, "Se esperaba 'cuadrada'"); opStr = "cuadrada"; break;
            case Gramatica.P_TIPO_PROMEDIO:
                t = consumir(TipoToken.PROMEDIO, "Se esperaba 'promedio'"); opStr = "promedio"; break;
            case Gramatica.P_TIPO_MAXIMO:
                t = consumir(TipoToken.MAXIMO, "Se esperaba 'maximo'"); opStr = "maximo"; break;
            case Gramatica.P_TIPO_SUMA:
                t = consumir(TipoToken.SUMA, "Se esperaba 'suma'"); opStr = "suma"; break;
            default:
                throw error("Error interno en TIPO_OP: producción no reconocida");
        }
        nodo.agregarHijo(t(t));
        calculo.operacion = opStr;

        Token pIzq = consumir(TipoToken.PAREN_IZQ, "Se esperaba '(' después del nombre de la operación");
        nodo.agregarHijo(t(pIzq));

        NodoDerivacion nParams = new NodoDerivacion("LISTA_PARAMS");
        listaParams(nParams, calculo);
        nodo.agregarHijo(nParams);

        Token pDer = consumir(TipoToken.PAREN_DER, "Se esperaba ')' de cierre");
        nodo.agregarHijo(t(pDer));

        ultimoValorSemantico = opStr;
    }

    // =============================================================
    //  NO TERMINAL: LISTA_PARAMS
    // =============================================================
    private void listaParams(NodoDerivacion nodo, Calculo calculo) {
        int prod = Gramatica.obtenerProduccion(Gramatica.LISTA_PARAMS, ver().tipo);
        if (prod == -1) {
            if (actual < tokens.size()) {
                tokens.get(actual).tieneError = true;
            }
            throw error(TablaErrores.P024, ver().lexema);
        }

        switch (prod) {
            case Gramatica.P_PARAMS_PARAM: {
                NodoDerivacion nParam = new NodoDerivacion("PARAM");
                param(nParam, calculo);
                nodo.agregarHijo(nParam);
                NodoDerivacion nSig = new NodoDerivacion("LISTA_PARAMS_SIG");
                listaParamsSig(nSig, calculo);
                nodo.agregarHijo(nSig);
                break;
            }
            case Gramatica.P_PARAMS_EPS:
                break;
        }
    }

    // LISTA_PARAMS_SIG
    private void listaParamsSig(NodoDerivacion nodo, Calculo calculo) {
        int prod = Gramatica.obtenerProduccion(Gramatica.LISTA_PARAMS_SIG, ver().tipo);

        switch (prod) {
            case Gramatica.P_PARAMS_SIG_COMA: {
                Token t = consumir(TipoToken.COMA, "Se esperaba ',' entre parámetros");
                nodo.agregarHijo(t(t));
                NodoDerivacion nRec = new NodoDerivacion("LISTA_PARAMS");
                listaParams(nRec, calculo);
                nodo.agregarHijo(nRec);
                break;
            }
            case Gramatica.P_PARAMS_SIG_EPS:
                break;
        }
    }

    // =============================================================
    //  NO TERMINAL: PARAM
    // =============================================================
    private void param(NodoDerivacion nodo, Calculo calculo) {
        int prod = Gramatica.obtenerProduccion(Gramatica.PARAM, ver().tipo);
        if (prod == -1) {
            if (actual < tokens.size()) {
                tokens.get(actual).tieneError = true;
            }
            throw error(TablaErrores.P024, ver().lexema);
        }

        switch (prod) {
            case Gramatica.P_PARAM_AMPL: {
                Token t1 = consumir(TipoToken.AMPLITUD, "Se esperaba 'amplitud'");
                Token t2 = consumir(TipoToken.ASIGNACION, "Se esperaba '=' después de 'amplitud'");
                Token t3 = consumir(TipoToken.NUMERO, "Se esperaba un número para la amplitud");
                nodo.agregarHijo(t(t1));
                nodo.agregarHijo(t(t2));
                nodo.agregarHijo(t(t3));
                calculo.parametros.add(new Parametro("amplitud", t3.lexema));
                break;
            }
            case Gramatica.P_PARAM_FREC: {
                Token t1 = consumir(TipoToken.FRECUENCIA, "Se esperaba 'frecuencia'");
                Token t2 = consumir(TipoToken.ASIGNACION, "Se esperaba '=' después de 'frecuencia'");
                Token t3 = consumir(TipoToken.NUMERO, "Se esperaba un número para la frecuencia");
                nodo.agregarHijo(t(t1));
                nodo.agregarHijo(t(t2));
                nodo.agregarHijo(t(t3));
                calculo.parametros.add(new Parametro("frecuencia", t3.lexema));
                break;
            }
            case Gramatica.P_PARAM_VENT: {
                Token t1 = consumir(TipoToken.VENTANA, "Se esperaba 'ventana'");
                Token t2 = consumir(TipoToken.ASIGNACION, "Se esperaba '=' después de 'ventana'");
                Token t3 = consumir(TipoToken.NUMERO, "Se esperaba un número para la ventana");
                nodo.agregarHijo(t(t1));
                nodo.agregarHijo(t(t2));
                nodo.agregarHijo(t(t3));
                calculo.parametros.add(new Parametro("ventana", t3.lexema));
                break;
            }
            case Gramatica.P_PARAM_CON: {
                Token t1 = consumir(TipoToken.CON, "Se esperaba 'con'");
                Token t2 = consumir(TipoToken.ASIGNACION, "Se esperaba '=' después de 'con'");
                Token t3 = consumir(TipoToken.ID, "Se esperaba un identificador después de 'con ='");
                nodo.agregarHijo(t(t1));
                nodo.agregarHijo(t(t2));
                nodo.agregarHijo(t(t3));
                calculo.parametros.add(new Parametro("con", t3.lexema));
                break;
            }
        }
    }

    // =============================================================
    //  PRE-VALIDACIÓN: Detectar ';' faltantes
    // =============================================================
    private void verificarPuntoComaFaltante() {
        for (int i = 0; i < tokens.size(); i++) {
            Token actual = tokens.get(i);

            // Último token antes de EOF
            if (i + 1 >= tokens.size()) {
                if (actual.tipo == TipoToken.ESTADO_SISTEMA || actual.tipo == TipoToken.CADENA) {
                    agregarError(TablaErrores.P029, actual.linea, actual.columna, actual.lexema);
                }
                continue;
            }

            Token siguiente = tokens.get(i + 1);

            // DESPUÉS de ESTADO_SISTEMA siempre debe venir ';'
            if (actual.tipo == TipoToken.ESTADO_SISTEMA && siguiente.tipo != TipoToken.PUNTO_COMA) {
                agregarError(TablaErrores.P004, actual.linea, actual.columna, actual.lexema);
            }

            // DESPUÉS de CADENA (literal de texto) siempre debe venir ';'
            if (actual.tipo == TipoToken.CADENA && siguiente.tipo != TipoToken.PUNTO_COMA) {
                agregarError(TablaErrores.P005, actual.linea, actual.columna);
            }
        }
    }

    // =============================================================
    //  MÉTODOS AUXILIARES
    // =============================================================
    private Token consumir(TipoToken tipo, String mensaje) {
        if (verificar(tipo)) return avanzar();
        if (actual < tokens.size()) {
            tokens.get(actual).tieneError = true;
        }
        // Agregar información del token encontrado
        Token actualToken = ver();
        String encontrado = (actualToken.tipo == TipoToken.EOF)
            ? "fin del programa"
            : "'" + actualToken.lexema + "'";
        throw error(mensaje + " pero se encontró " + encontrado
            + " en línea " + actualToken.linea + ", columna " + actualToken.columna);
    }

    private boolean verificar(TipoToken tipo) {
        return actual < tokens.size() && tokens.get(actual).tipo == tipo;
    }

    private Token avanzar() {
        ultimoConsumido = tokens.get(actual);
        return tokens.get(actual++);
    }

    private Token ver() {
        if (actual >= tokens.size()) {
            return new Token(TipoToken.EOF, "", tokens.isEmpty() ? 1 : tokens.get(tokens.size() - 1).linea);
        }
        return tokens.get(actual);
    }

    private String contexto() {
        StringBuilder sb = new StringBuilder();
        if (ultimoConsumido != null) {
            sb.append(" después de '").append(ultimoConsumido.lexema).append("'");
        }
        if (actual < tokens.size() && tokens.get(actual).tipo != TipoToken.EOF) {
            sb.append(" pero se encontró '").append(ver().lexema).append("'");
        }
        return sb.toString();
    }

    private RuntimeException error(TablaErrores codigo, Object... args) {
        Token tok = ver();
        return new ErrorSintactico(new ErrorInfo(codigo, tok.linea, tok.columna, args));
    }

    private RuntimeException error(String mensaje) {
        Token tok = ver();
        return new ErrorSintactico(new ErrorInfo(
            TablaErrores.P006.getCodigo(), "Sintáctico", "Error de sintaxis", mensaje,
            tok.linea, tok.columna));
    }
}
