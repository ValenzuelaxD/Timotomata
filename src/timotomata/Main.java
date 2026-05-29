package timotomata;

import java.util.*;
import timotomata.lexer.*;
import timotomata.parser.Parser;
import timotomata.parser.NodoDerivacion;
import timotomata.parser.ast.*;
import timotomata.semantico.AnalizadorSemantico;

public class Main {

    public static void main(String[] args) {

        Scanner sc = new Scanner(System.in);

        System.out.println("===== COMPILADOR TIMOTOMATA =====");
        System.out.println("Para la interfaz grafica, ejecuta: compile_and_run_gui.bat");
        System.out.println();
        System.out.println("Escribe tu codigo Timotomata.");
        System.out.println("Presiona Ctrl+Z (Enter) cuando termines.");
        System.out.println();

        StringBuilder entrada = new StringBuilder();

        while (sc.hasNextLine()) {
            String linea = sc.nextLine();
            entrada.append(linea).append("\n");
        }

        String codigo = entrada.toString();

        System.out.println("\n===== CODIGO FUENTE INGRESADO =====");
        System.out.println(codigo);

        // ─── Fase 1: Análisis Léxico ───
        Lexer lexer = new Lexer(codigo);
        List<Token> tokens = lexer.escanear();

        System.out.println("===== TOKENS GENERADOS =====");
        for (Token t : tokens) {
            if (t.tipo == TipoToken.EOF) continue;
            System.out.println(t);
        }

        // ─── Fase 2: Análisis Sintáctico ───
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

        System.out.println("\n===== ARBOL DE DERIVACION =====");
        System.out.println(arbolToString(parser.arbolDerivacion, "", true));

        // ─── Fase 3: Análisis Semántico ───
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
        System.out.println("Calculos: " + programa.calculos.size() + " operacion(es)");
        for (Calculo c : programa.calculos) {
            System.out.println("  - " + c.sensor + " : " + c.operacion + " " + c.parametros);
        }
        System.out.println("\nAnalisis completado sin errores.");
    }

    /**
     * Convierte el árbol de derivación a texto indentado.
     */
    private static String arbolToString(NodoDerivacion nodo, String prefijo, boolean esUltimo) {
        StringBuilder sb = new StringBuilder();

        sb.append(prefijo);
        sb.append(esUltimo ? "└── " : "├── ");
        sb.append(nodo.valor).append("\n");

        String nuevoPrefijo = prefijo + (esUltimo ? "    " : "│   ");

        for (int i = 0; i < nodo.hijos.size(); i++) {
            sb.append(arbolToString(nodo.hijos.get(i), nuevoPrefijo, i == nodo.hijos.size() - 1));
        }

        return sb.toString();
    }
}
