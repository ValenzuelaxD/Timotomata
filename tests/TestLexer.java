package tests;

import java.util.*;
import timotomata.lexer.*;

/**
 * Pruebas unitarias del Lexer de Timotomata.
 * Verifica que cada tipo de token se reconozca correctamente,
 * los lexemas sean los esperados, las líneas sean correctas,
 * y los errores léxicos se detecten apropiadamente.
 */
public class TestLexer {

    static int pasados = 0;
    static int fallidos = 0;

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  PRUEBAS DEL LEXER TIMOTOMATA");
        System.out.println("========================================\n");

        // ─── 1. PALABRAS RESERVADAS ───
        System.out.println(">>> 1. PALABRAS RESERVADAS\n");
        testToken("sensor", TipoToken.SENSOR, "sensor");
        testToken("umbral", TipoToken.UMBRAL, "umbral");
        testToken("si", TipoToken.SI, "si");
        testToken("entonces", TipoToken.ENTONCES, "entonces");
        testToken("calcular", TipoToken.CALCULAR, "calcular");
        testToken("fin", TipoToken.FIN, "fin");
        testToken("tipo", TipoToken.TIPO, "tipo");
        testToken("rango", TipoToken.RANGO, "rango");
        testToken("minimo", TipoToken.MINIMO, "minimo");
        testToken("maximo", TipoToken.MAXIMO, "maximo");
        testToken("estado", TipoToken.ESTADO, "estado");
        testToken("alerta", TipoToken.ALERTA, "alerta");
        testToken("electrico", TipoToken.ELECTRICO, "electrico");
        testToken("termico", TipoToken.TERMICO, "termico");
        testToken("abs", TipoToken.ABS, "abs");
        testToken("seno", TipoToken.SENO, "seno");
        testToken("coseno", TipoToken.COSENO, "coseno");
        testToken("cuadrada", TipoToken.CUADRADA, "cuadrada");
        testToken("promedio", TipoToken.PROMEDIO, "promedio");
        testToken("suma", TipoToken.SUMA, "suma");
        testToken("fluctuacion", TipoToken.FLUCTUACION, "fluctuacion");
        testToken("amplitud", TipoToken.AMPLITUD, "amplitud");
        testToken("frecuencia", TipoToken.FRECUENCIA, "frecuencia");
        testToken("ventana", TipoToken.VENTANA, "ventana");
        testToken("con", TipoToken.CON, "con");
        testToken("y", TipoToken.Y, "y");
        testToken("o", TipoToken.O, "o");

        // Estados del sistema (en mayúsculas)
        testToken("PICO", TipoToken.ESTADO_SISTEMA, "PICO");
        testToken("CAIDA", TipoToken.ESTADO_SISTEMA, "CAIDA");
        testToken("NORMAL", TipoToken.ESTADO_SISTEMA, "NORMAL");
        testToken("INESTABLE", TipoToken.ESTADO_SISTEMA, "INESTABLE");

        // ─── 2. IDENTIFICADORES ───
        System.out.println("\n>>> 2. IDENTIFICADORES\n");
        testToken("voltaje", TipoToken.ID, "voltaje");
        testToken("temperatura", TipoToken.ID, "temperatura");
        testToken("maxVolt", TipoToken.ID, "maxVolt");
        testToken("sensor1", TipoToken.ID, "sensor1");
        testToken("_temp", TipoToken.ID, "_temp");
        testToken("a", TipoToken.ID, "a");

        // ─── 3. NÚMEROS ───
        System.out.println("\n>>> 3. NÚMEROS\n");
        testToken("0", TipoToken.NUMERO, "0");
        testToken("127", TipoToken.NUMERO, "127");
        testToken("3.14", TipoToken.NUMERO, "3.14");
        testToken("0.5", TipoToken.NUMERO, "0.5");
        testToken("1000000", TipoToken.NUMERO, "1000000");
        testToken("99.99", TipoToken.NUMERO, "99.99");

        // ─── 4. OPERADORES (1 carácter) ───
        System.out.println("\n>>> 4. OPERADORES DE 1 CARÁCTER\n");
        testToken(">", TipoToken.MAYOR, ">");
        testToken("<", TipoToken.MENOR, "<");
        testToken("+", TipoToken.MAS, "+");
        testToken("-", TipoToken.MENOS, "-");
        testToken("*", TipoToken.POR, "*");
        testToken("/", TipoToken.DIV, "/");
        testToken("=", TipoToken.ASIGNACION, "=");

        // ─── 5. OPERADORES COMPUESTOS (2 caracteres) ───
        System.out.println("\n>>> 5. OPERADORES COMPUESTOS\n");
        testSecuencia("==", new TipoToken[]{TipoToken.IGUAL_IGUAL});
        testSecuencia(">=", new TipoToken[]{TipoToken.MAYOR_IGUAL});
        testSecuencia("<=", new TipoToken[]{TipoToken.MENOR_IGUAL});
        testSecuencia("!=", new TipoToken[]{TipoToken.DIFERENTE});

        // ─── 6. DELIMITADORES ───
        System.out.println("\n>>> 6. DELIMITADORES\n");
        testToken(";", TipoToken.PUNTO_COMA, ";");
        testToken("(", TipoToken.PAREN_IZQ, "(");
        testToken(")", TipoToken.PAREN_DER, ")");
        testToken("{", TipoToken.LLAVE_IZQ, "{");
        testToken("}", TipoToken.LLAVE_DER, "}");
        testToken(",", TipoToken.COMA, ",");

        // ─── 7. CADENAS ───
        System.out.println("\n>>> 7. CADENAS DE TEXTO\n");
        testSecuencia("\"hola\"", new TipoToken[]{TipoToken.CADENA});
        testSecuencia("\"alerta de voltaje\"", new TipoToken[]{TipoToken.CADENA});
        testSecuencia("\"\"", new TipoToken[]{TipoToken.CADENA});
        testSecuencia("\"hello world 123\"", new TipoToken[]{TipoToken.CADENA});

        // ─── 8. COMENTARIOS ───
        System.out.println("\n>>> 8. COMENTARIOS\n");
        testSecuencia("// comentario", new TipoToken[]{TipoToken.COMENTARIO});
        testSecuencia("//", new TipoToken[]{TipoToken.COMENTARIO});
        testSecuencia("// sensor de prueba", new TipoToken[]{TipoToken.COMENTARIO});

        // ─── 9. EOF ───
        System.out.println("\n>>> 9. EOF\n");
        testSecuencia("", new TipoToken[]{});

        // ─── 10. NÚMERO DE LÍNEAS ───
        System.out.println("\n>>> 10. NÚMERO DE LÍNEAS\n");
        // "sensor v;\numbral x = 10;\nfin;"
        // Línea 1: sensor(1) v(1) ;(1)
        // Línea 2: umbral(2) x(2) =(2) 10(2) ;(2)
        // Línea 3: fin(3) ;(3)
        testLineas("sensor v;\numbral x = 10;\nfin;",
            new int[]{1, 1, 1, 2, 2, 2, 2, 2, 3, 3});
        // Una sola línea
        testLineas("si a > 10 entonces", new int[]{1, 1, 1, 1, 1});
        // Múltiples líneas con saltos extra
        testLineas("sensor x;\n\n\nfin;", new int[]{1, 1, 1, 4, 4});

        // ─── 11. MÚLTIPLES TOKENS EN SECUENCIA ───
        System.out.println("\n>>> 11. SECUENCIAS DE TOKENS\n");
        testSecuencia("sensor voltaje;",
            new TipoToken[]{TipoToken.SENSOR, TipoToken.ID, TipoToken.PUNTO_COMA});
        testSecuencia("si a > 10 entonces",
            new TipoToken[]{TipoToken.SI, TipoToken.ID, TipoToken.MAYOR,
                           TipoToken.NUMERO, TipoToken.ENTONCES});
        testSecuencia("rango v minimo = -5 maximo = 100;",
            new TipoToken[]{TipoToken.RANGO, TipoToken.ID, TipoToken.MINIMO,
                           TipoToken.ASIGNACION, TipoToken.MENOS, TipoToken.NUMERO,
                           TipoToken.MAXIMO, TipoToken.ASIGNACION, TipoToken.NUMERO,
                           TipoToken.PUNTO_COMA});
        testSecuencia("calcular promedio(voltaje, ventana = 10);",
            new TipoToken[]{TipoToken.CALCULAR, TipoToken.PROMEDIO, TipoToken.PAREN_IZQ,
                           TipoToken.ID, TipoToken.COMA, TipoToken.VENTANA,
                           TipoToken.ASIGNACION, TipoToken.NUMERO, TipoToken.PAREN_DER,
                           TipoToken.PUNTO_COMA});
        testSecuencia("estado = PICO;", 
            new TipoToken[]{TipoToken.ESTADO, TipoToken.ASIGNACION, TipoToken.ESTADO_SISTEMA,
                           TipoToken.PUNTO_COMA});
        testSecuencia("alerta = \"hello\";",
            new TipoToken[]{TipoToken.ALERTA, TipoToken.ASIGNACION, TipoToken.CADENA,
                           TipoToken.PUNTO_COMA});

        // ─── 12. ERRORES LÉXICOS ───
        System.out.println("\n>>> 12. ERRORES LÉXICOS\n");
        testError("@", "símbolo @ inválido");
        testError("#sensor", "símbolo # inválido");
        testError("12.34.56", "número con múltiples puntos");

        // ─── 13. ESPACIOS Y SALTOS ───
        System.out.println("\n>>> 13. ESPACIOS Y SALTOS DE LÍNEA\n");
        testSecuencia("  \t  sensor  \t  ", new TipoToken[]{TipoToken.SENSOR});
        testSecuencia("\n\n\nsensor\n\n", new TipoToken[]{TipoToken.SENSOR});
        testSecuencia("sensor\tvoltaje", new TipoToken[]{TipoToken.SENSOR, TipoToken.ID});

        // ─── 14. CASE INSENSITIVE (palabras reservadas) ───
        System.out.println("\n>>> 14. CASE INSENSITIVE\n");
        testToken("SENSOR", TipoToken.SENSOR, "SENSOR");
        testToken("Sensor", TipoToken.SENSOR, "Sensor");
        testToken("SI", TipoToken.SI, "SI");
        testToken("ENTONCES", TipoToken.ENTONCES, "ENTONCES");

        // ─── 15. IDENTIFICADOR VS PALABRA RESERVADA ───
        System.out.println("\n>>> 15. ID VS PALABRA RESERVADA\n");
        testToken("sensorXYZ", TipoToken.ID, "sensorXYZ");
        testToken("miSi", TipoToken.ID, "miSi");
        testToken("finito", TipoToken.ID, "finito");
        testToken("calculadora", TipoToken.ID, "calculadora");

        // ─── RESUMEN ───
        System.out.println("\n========================================");
        System.out.println("  RESUMEN");
        System.out.println("========================================");
        System.out.println("  Pasados:  " + pasados);
        System.out.println("  Fallidos: " + fallidos);
        System.out.println("  Total:    " + (pasados + fallidos));
        System.out.println("========================================");
        if (fallidos == 0) {
            System.out.println("  ¡TODAS LAS PRUEBAS DEL LEXER PASARON!");
        } else {
            System.out.println("  ¡HAY " + fallidos + " PRUEBAS FALLIDAS!");
        }
        System.out.println("========================================");
    }

    /** Verifica que un código con un solo token genera exactamente ese tipo con ese lexema. */
    static void testToken(String nombre, TipoToken esperado, String lexemaEsperado) {
        try {
            Lexer lexer = new Lexer(nombre);
            List<Token> tokens = lexer.escanear();

            // Filtrar EOF para análisis
            List<Token> sinEof = new ArrayList<>();
            for (Token t : tokens) {
                if (t.tipo != TipoToken.EOF) sinEof.add(t);
            }

            if (sinEof.size() != 1) {
                fallidos++;
                System.out.println("  ✗ " + nombre + " — esperaba 1 token, obtuvo " + sinEof.size());
                for (Token t : tokens) {
                    System.out.println("    → " + t.tipo + " '" + t.lexema + "' L" + t.linea);
                }
                return;
            }

            Token tok = sinEof.get(0);

            if (tok.tipo != esperado) {
                fallidos++;
                System.out.println("  ✗ " + nombre + " — tipo esperado: " + esperado + ", obtuvo: " + tok.tipo);
            } else if (!tok.lexema.equals(lexemaEsperado)) {
                fallidos++;
                System.out.println("  ✗ " + nombre + " — lexema esperado: '" + lexemaEsperado + "', obtuvo: '" + tok.lexema + "'");
            } else                if (!lexer.getErroresLexicos().isEmpty()) {
                    fallidos++;
                    System.out.println("  ✗ " + nombre + " — errores inesperados:");
                    for (ErrorInfo ei : lexer.getErroresLexicos()) {
                        System.out.println("    " + ei.toPlainText());
                    }
            } else {
                pasados++;
                System.out.println("  ✓ " + nombre + " → " + tok.tipo + " '" + tok.lexema + "'");
            }
        } catch (Exception e) {
            fallidos++;
            System.out.println("  ✗ " + nombre + " — excepción: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /** Verifica que las líneas de los tokens sean las correctas para código multi-línea. */
    static void testLineas(String codigo, int[] lineasEsperadas) {
        try {
            Lexer lexer2 = new Lexer(codigo);
            List<Token> tokens2 = lexer2.escanear();
            List<Token> sinEof2 = new ArrayList<>();
            for (Token t : tokens2) {
                if (t.tipo != TipoToken.EOF) sinEof2.add(t);
            }

            boolean ok = true;
            if (sinEof2.size() != lineasEsperadas.length) {
                fallidos++;
                System.out.println("  ✗ Líneas — cantidad de tokens: esperaba " + lineasEsperadas.length
                    + ", obtuvo " + sinEof2.size());
                for (Token t : sinEof2) {
                    System.out.println("    → " + t.tipo + " '" + t.lexema + "' L" + t.linea);
                }
                return;
            }

            for (int i = 0; i < sinEof2.size(); i++) {
                if (sinEof2.get(i).linea != lineasEsperadas[i]) {
                    ok = false;
                    System.out.println("  ✗ Líneas — token " + i + " ('" + sinEof2.get(i).lexema
                        + "'): esperaba L" + lineasEsperadas[i] + ", obtuvo L" + sinEof2.get(i).linea);
                }
            }

            if (ok) {
                pasados++;
                System.out.println("  ✓ Líneas — todas las líneas correctas para " + sinEof2.size() + " tokens");
            } else {
                fallidos++;
            }
        } catch (Exception e) {
            fallidos++;
            System.out.println("  ✗ Líneas — excepción: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /** Verifica que la secuencia de tipos de token sea la esperada (sin contar EOF). */
    static void testSecuencia(String codigo, TipoToken[] esperados) {
        try {
            Lexer lexer = new Lexer(codigo);
            List<Token> tokens = lexer.escanear();
            List<Token> sinEof = new ArrayList<>();
            for (Token t : tokens) {
                if (t.tipo != TipoToken.EOF) sinEof.add(t);
            }

            if (sinEof.size() != esperados.length) {
                fallidos++;
                System.out.println("  ✗ Secuencia '" + codigo + "' — cantidad: esperaba "
                    + esperados.length + " tokens, obtuvo " + sinEof.size());
                for (Token t : sinEof) {
                    System.out.println("    → " + t.tipo + " '" + t.lexema + "' L" + t.linea);
                }
            if (!lexer.getErroresLexicos().isEmpty()) {
                for (ErrorInfo ei : lexer.getErroresLexicos()) {
                    System.out.println("    [ERROR] " + ei.toPlainText());
                }
                }
                return;
            }

            boolean ok = true;
            for (int i = 0; i < sinEof.size(); i++) {
                if (sinEof.get(i).tipo != esperados[i]) {
                    ok = false;
                    System.out.println("  ✗ Secuencia '" + codigo + "' — token " + i
                        + ": esperaba " + esperados[i] + ", obtuvo " + sinEof.get(i).tipo
                        + " ('" + sinEof.get(i).lexema + "')");
                }
            }

            if (ok) {
                pasados++;
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < sinEof.size(); i++) {
                    if (i > 0) sb.append(" → ");
                    sb.append(sinEof.get(i).tipo);
                }
                System.out.println("  ✓ Secuencia '" + codigo + "' → " + sb);
            } else {
                fallidos++;
            }
        } catch (Exception e) {
            fallidos++;
            System.out.println("  ✗ Secuencia '" + codigo + "' — excepción: "
                + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /** Verifica que el lexer genere errores léxicos para código inválido. */
    static void testError(String codigo, String descripcion) {
        try {
            Lexer lexer = new Lexer(codigo);
            List<Token> tokens = lexer.escanear();
            List<ErrorInfo> errores = lexer.getErroresLexicos();

            if (errores.isEmpty()) {
                fallidos++;
                System.out.println("  ✗ Error léxico (" + descripcion + ") — no se detectó error");
                System.out.println("    Tokens generados:");
                for (Token t : tokens) {
                    System.out.println("      → " + t.tipo + " '" + t.lexema + "'");
                }
            } else {
                pasados++;
                System.out.println("  ✓ Error léxico (" + descripcion + ") — " + errores.get(0).toPlainText());
            }
        } catch (Exception e) {
            // Una excepción también cuenta como error detectado
            pasados++;
            System.out.println("  ✓ Error léxico (" + descripcion + ") — excepción: " + e.getMessage());
        }
    }
}
