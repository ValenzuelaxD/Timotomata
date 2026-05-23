package timotomata;

import java.util.*;
import timotomata.lexer.*;
import timotomata.parser.Parser;
import timotomata.parser.ast.*;
import timotomata.semantico.AnalizadorSemantico;

public class Main {

    public static void main(String[] args) {

        Scanner sc = new Scanner(System.in);

        System.out.println("===== COMPILADOR TIMOTOMATA =====");
        System.out.println("Escribe tu codigo Timotomata.");
        System.out.println("Cuando termines, escribe FIN en una linea nueva.");
        System.out.println();

        StringBuilder entrada = new StringBuilder();

        while (true) {
            String linea = sc.nextLine();

            if (linea.equalsIgnoreCase("FIN")) {
                break;
            }

            entrada.append(linea).append("\n");
        }

        String codigo = entrada.toString();

        System.out.println("\n===== CODIGO FUENTE INGRESADO =====");
        System.out.println(codigo);

        Lexer lexer = new Lexer(codigo);
        List<Token> tokens = lexer.escanear();

        System.out.println("===== TOKENS GENERADOS =====");
        for (Token t : tokens) {
            System.out.println(t);
        }

        Parser parser = new Parser(tokens);
        Programa programa;

        try {
            programa = parser.parsear();
        } catch (RuntimeException e) {
            System.out.println("\nERROR SINTACTICO:");
            System.out.println(e.getMessage());
            return;
        }

        System.out.println("\n===== AST GENERADO =====");
        programa.imprimir();

        AnalizadorSemantico semantico = new AnalizadorSemantico();
        semantico.analizar(programa);

        if (!semantico.errores.isEmpty()) {
            System.out.println("\n===== ERRORES SEMANTICOS =====");
            for (String e : semantico.errores) {
                System.out.println(e);
            }
            return;
        }

        System.out.println("\n===== TABLA DE SIMBOLOS =====");
        System.out.println("Sensores: " + semantico.sensores);
        System.out.println("Umbrales: " + semantico.umbrales);

        ejecutarSimulacion(programa, semantico, sc);
    }

    static void ejecutarSimulacion(Programa programa, AnalizadorSemantico semantico, Scanner sc) {
        double previo = 0;
        boolean primeraLectura = true;
        int tiempo = 1;

        System.out.println("\n===== SIMULACION =====");
        System.out.println("Ahora ingresa valores numericos para el sensor voltaje.");
        System.out.println("Escribe -1 para terminar.");
        System.out.println();

        while (true) {
            System.out.print("voltaje> ");
            double voltaje = sc.nextDouble();

            if (voltaje == -1) {
                System.out.println("Simulacion finalizada.");
                break;
            }

            if (primeraLectura) {
                previo = voltaje;
                primeraLectura = false;
            }

            Map<String, Double> memoria = new HashMap<>();
            memoria.put("voltaje", voltaje);
            memoria.put("previo", previo);
            memoria.putAll(semantico.umbrales);

            String estado = "NORMAL";

            for (Regla regla : programa.reglas) {
                if (evaluarCondicion(regla.condicion, memoria)) {
                    estado = regla.estado;
                }
            }

            System.out.println("t=" + tiempo
                    + " | voltaje=" + voltaje
                    + " | previo=" + previo
                    + " | estado=" + estado);

            previo = voltaje;
            tiempo++;
        }
    }

    static boolean evaluarCondicion(Expresion expr, Map<String, Double> memoria) {
        if (expr instanceof Binaria b) {
            double izq = evaluarNumero(b.izquierda, memoria);
            double der = evaluarNumero(b.derecha, memoria);

            return switch (b.operador) {
                case ">" -> izq > der;
                case "<" -> izq < der;
                case "==" -> izq == der;
                case ">=" -> izq >= der;
                case "<=" -> izq <= der;
                case "!=" -> izq != der;
                default -> false;
            };
        }

        return false;
    }

    static double evaluarNumero(Expresion expr, Map<String, Double> memoria) {
        if (expr instanceof Numero n) {
            return n.valor;
        }

        if (expr instanceof Variable v) {
            return memoria.getOrDefault(v.nombre, 0.0);
        }

        if (expr instanceof Abs a) {
            return Math.abs(evaluarNumero(a.expresion, memoria));
        }

        if (expr instanceof Binaria b) {
            double izq = evaluarNumero(b.izquierda, memoria);
            double der = evaluarNumero(b.derecha, memoria);

            return switch (b.operador) {
                case "+" -> izq + der;
                case "-" -> izq - der;
                case "*" -> izq * der;
                case "/" -> izq / der;
                default -> 0;
            };
        }

        return 0;
    }
}
