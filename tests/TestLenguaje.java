package tests;

import java.util.*;
import timotomata.lexer.*;
import timotomata.parser.*;

public class TestLenguaje {

    static int pasados = 0;
    static int fallidos = 0;

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  PRUEBAS DE LEXER + PARSER TIMOTOMATA");
        System.out.println("========================================\n");

        // ─── CASOS VÁLIDOS (deberían compilar sin errores) ───
        System.out.println(">>> CASOS VÁLIDOS (esperado: 0 errores)\n");

        test("SENSOR básico", "sensor voltaje;", true);
        test("SENSOR con tipo", "sensor voltaje tipo electrico;", true);
        test("SENSOR con tipo térmico", "sensor temp tipo termico;", true);
        test("Múltiples sensores", "sensor v tipo electrico;\nsensor t tipo termico;", true);

        test("UMBRAL simple", "umbral maxV = 127;", true);
        test("UMBRAL decimal", "umbral umbralRef = 3.14;", true);
        test("Múltiples umbrales", "umbral a = 10;\numbral b = 20;", true);

        test("RANGO simple", "rango voltaje minimo = 110 maximo = 127;", true);
        test("RANGO con negativos", "rango temp minimo = -10 maximo = 80;", true);
        test("RANGO ambos negativos", "rango x minimo = -40 maximo = -10;", true);

        test("SI simple (acción única)", "si voltaje > 127 entonces estado = PICO;", true);
        test("SI con bloque", "si voltaje > 127 entonces { estado = PICO; }", true);
        test("SI con alerta", "si voltaje > 127 entonces alerta = \"Alerta!\";", true);
        test("SI con bloque múltiple", "si t > 80 entonces { estado = PICO; alerta = \"高温\"; }", true);

        test("Condición compuesta (y)", "si voltaje > 100 y temp > 50 entonces estado = PICO;", true);
        test("Condición compuesta (o)", "si voltaje > 100 o temp > 50 entonces estado = PICO;", true);
        test("Múltiples operadores lógicos", "si a > 1 y b > 2 o c > 3 entonces estado = PICO;", true);

        test("Operador ==", "si voltaje == 127 entonces estado = PICO;", true);
        test("Operador <=", "si voltaje <= 110 entonces estado = CAIDA;", true);
        test("Operador >=", "si voltaje >= 120 entonces estado = PICO;", true);
        test("Operador !=", "si voltaje != 0 entonces estado = NORMAL;", true);

        test("CALCULAR promedio", "calcular promedio(voltaje, ventana = 10);", true);
        test("CALCULAR fluctuacion", "calcular fluctuacion(temperatura);", true);
        test("CALCULAR maximo", "calcular maximo(voltaje, ventana = 5);", true);

        test("CALCULAR seno", "calcular (voltaje, seno(amplitud = 1, frecuencia = 60));", true);
        test("CALCULAR coseno", "calcular (temp, coseno(amplitud = 2));", true);
        test("CALCULAR suma", "calcular (v, suma());", true);
        test("CALCULAR promedio-op", "calcular (v, promedio());", true);

        test("FIN", "fin;", true);

        test("Comentario simple", "// Esto es un comentario", true);
        test("Código con comentarios", "sensor v tipo electrico;\n// declaraciones\numbral x = 10;", true);

        test("Programa completo ejemplo", 
            "// Monitoreo de subestacion electrica\n" +
            "sensor voltaje tipo electrico;\n" +
            "sensor temperatura tipo termico;\n" +
            "rango voltaje minimo = 110 maximo = 127;\n" +
            "rango temperatura minimo = -10 maximo = 80;\n" +
            "umbral maxVolt = 127;\n" +
            "umbral minVolt = 110;\n" +
            "si voltaje >= maxVolt o temperatura > 80 entonces {\n" +
            "    estado = PICO;\n" +
            "    alerta = \"Voltaje o temperatura elevada\";\n" +
            "}\n" +
            "si voltaje <= minVolt entonces estado = CAIDA;\n" +
            "calcular promedio(voltaje, ventana = 10);\n" +
            "calcular fluctuacion(temperatura);\n" +
            "fin;",
            true);

        test("Expresión con abs", "si abs(voltaje) > 100 entonces estado = PICO;", true);
        test("Expresión aritmética", "si voltaje + 10 > 127 entonces estado = PICO;", true);
        test("Expresión con resta", "si voltaje - 10 < 110 entonces estado = CAIDA;", true);
        test("Expresión con multiplicación", "si voltaje * 2 > 200 entonces estado = PICO;", true);
        test("Expresión con división", "si voltaje / 2 < 60 entonces estado = CAIDA;", true);
        test("Negación unaria", "si -voltaje > -100 entonces estado = PICO;", true);

        // ─── CASOS INVÁLIDOS (deberían reportar errores) ───
        System.out.println("\n>>> CASOS INVÁLIDOS (esperado: errores)\n");

        test("Sensor sin ID", "sensor;", false);
        test("Sensor sin punto y coma", "sensor voltaje", false);
        test("Tipo inválido", "sensor v tipo numerico;", false);

        test("Umbral sin =", "umbral maxV 127;", false);
        test("Umbral sin número", "umbral maxV = ;", false);

        test("RANGO sin punto y coma", "rango v minimo = 0 maximo = 100", false);
        test("RANGO sin minimo", "rango v maximo = 100;", false);
        test("RANGO sin maximo", "rango v minimo = 0;", false);

        test("SI sin entonces", "si voltaje > 100 estado = PICO;", false);
        test("SI sin condición", "si entonces estado = PICO;", false);
        test("SI sin acción", "si voltaje > 100 entonces", false);

        test("CALCULAR sin argumentos", "calcular;", false);
        test("CALCULAR función inválida", "calcular division(voltaje);", false);

        test("Token inválido", "@#$%", false);
        test("Llave desbalanceada (abre)", "si a > 1 entonces { estado = PICO;", false);
        test("Paréntesis desbalanceado", "calcular promedio(voltaje, ventana = 10;", false);

        test("Entonces faltante con bloque", "si voltaje > 127 { estado = PICO; }", false);
        test("Estado sistema inválido", "si voltaje > 127 entonces estado = ALGO;", false);

        // ─── RESUMEN ───
        System.out.println("\n========================================");
        System.out.println("  RESUMEN");
        System.out.println("========================================");
        System.out.println("  Pasados:  " + pasados);
        System.out.println("  Fallidos: " + fallidos);
        System.out.println("  Total:    " + (pasados + fallidos));
        System.out.println("========================================");
        if (fallidos == 0) {
            System.out.println("  ¡TODAS LAS PRUEBAS PASARON!");
        } else {
            System.out.println("  ¡HAY " + fallidos + " PRUEBAS FALLIDAS!");
        }
        System.out.println("========================================");
    }

    static void test(String nombre, String codigo, boolean debeSerValido) {
        try {
            // Fase 1: Lexer
            Lexer lexer = new Lexer(codigo);
            List<Token> tokens = lexer.escanear();
            List<ErrorInfo> erroresLex = lexer.getErroresLexicos();

            // Fase 2: Parser (solo si no hay errores léxicos fatales)
            List<ErrorInfo> erroresSint = new ArrayList<>();
            if (erroresLex.isEmpty()) {
                Parser parser = new Parser(tokens);
                parser.parsear();
                erroresSint = parser.getErroresSintacticos();
            }

            boolean esValido = erroresLex.isEmpty() && erroresSint.isEmpty();

            if (esValido == debeSerValido) {
                pasados++;
                System.out.println("  ✓ " + nombre);
            } else {
                fallidos++;
                System.out.println("  ✗ " + nombre + " — FALLO");
                if (debeSerValido) {
                    System.out.println("    Se esperaba válido, pero tiene errores:");
                    for (ErrorInfo e : erroresLex) System.out.println("      [LEX] " + e.toPlainText());
                    for (ErrorInfo e : erroresSint) System.out.println("      [SIN] " + e.toPlainText());
                } else {
                    System.out.println("    Se esperaba inválido, pero pasó sin errores");
                }
            }
        } catch (Exception e) {
            if (debeSerValido) {
                fallidos++;
                System.out.println("  ✗ " + nombre + " — FALLO (excepción)");
                System.out.println("    " + e.getClass().getSimpleName() + ": " + e.getMessage());
            } else {
                pasados++;
                System.out.println("  ✓ " + nombre + " (excepción como esperado)");
            }
        }
    }
}
