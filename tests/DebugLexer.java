package tests;

import java.util.*;
import timotomata.lexer.*;

/**
 * Debug test para diagnosticar bugs del lexer.
 */
public class DebugLexer {

    public static void main(String[] args) {
        System.out.println("=== DEBUG LEXER ===\n");

        // Test 1: String simple
        String input1 = "\"hola\"";
        System.out.println("Input: " + input1 + " (length=" + input1.length() + ")");
        System.out.println("Chars: ");
        for (int i = 0; i < input1.length(); i++) {
            System.out.println("  [" + i + "] = '" + input1.charAt(i) + "' (U+" + Integer.toHexString(input1.charAt(i)) + ")");
        }
        dumpTokens(input1);

        // Test 2: Operator ==
        String input2 = "==";
        System.out.println("\nInput: " + input2 + " (length=" + input2.length() + ")");
        dumpTokens(input2);

        // Test 3: String inside code
        String input3 = "alerta = \"hello\";";
        System.out.println("\nInput: " + input3);
        dumpTokens(input3);

        // Test 4: Just a semicolon
        String input4 = ";";
        System.out.println("\nInput: " + input4);
        dumpTokens(input4);

        // Test 5: > alone
        String input5 = ">";
        System.out.println("\nInput: " + input5);
        dumpTokens(input5);

        // Test 6: >=
        String input6 = ">=";
        System.out.println("\nInput: " + input6);
        dumpTokens(input6);

        // Test 7: ==  followed by semicolon
        String input7 = "==;";
        System.out.println("\nInput: " + input7);
        dumpTokens(input7);

        // Test 8: ! alone  
        String input8 = "!";
        System.out.println("\nInput: " + input8);
        dumpTokens(input8);

        // Test 9: !=
        String input9 = "!=";
        System.out.println("\nInput: " + input9);
        dumpTokens(input9);
    }

    static void dumpTokens(String input) {
        try {
            Lexer lexer = new Lexer(input);
            List<Token> tokens = lexer.escanear();
            System.out.println("  Tokens (" + tokens.size() + "):");
            for (Token t : tokens) {
                System.out.println("    " + t.tipo + "  '" + t.lexema + "'  L" + t.linea + " C" + t.columna);
            }
            if (!lexer.getErroresLexicos().isEmpty()) {
                System.out.println("  Errores:");
                for (String e : lexer.getErroresLexicos()) {
                    System.out.println("    " + e);
                }
            }
        } catch (Exception e) {
            System.out.println("  EXCEPCIÓN: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            e.printStackTrace(System.out);
        }
    }
}
