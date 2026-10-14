package timotomata.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import timotomata.lexer.Lexer;
import timotomata.lexer.TipoToken;
import timotomata.lexer.Token;
import timotomata.parser.Gramatica;
import timotomata.parser.Parser;
import timotomata.parser.ast.Calculo;
import timotomata.parser.ast.Programa;
import timotomata.parser.ast.Regla;
import timotomata.semantic.AnalizadorSemantico;
import timotomata.simbolos.Simbolo;

/**
 * COMPILADOR DE LÍNEA DE COMANDOS
 * ================================
 *
 * Corre las cuatro fases del front-end sobre un archivo y vuelca el resultado,
 * sin abrir la interfaz gráfica.
 *
 * <pre>
 *   java timotomata.tools.CompiladorCLI tests/test_tipos.txt
 *   java timotomata.tools.CompiladorCLI -v tests/*.txt
 * </pre>
 *
 * <p>La GUI viene mostrando tokens, tabla de símbolos y errores, pero medirlos
 * exige abrirla. Para todo lo que se depura iterando sobre la gramática — la
 * tabla LL(1) que se regenera sola, el conteo de reglas, el grafo de flujo de
 * datos — hace falta un punto de entrada que devuelva texto plano y salga con un
 * código de retorno utilizable desde un script.
 *
 * <h2>Salida</h2>
 * Devuelve 1 si hubo cualquier error, 0 si el programa compila limpio. Eso lo
 * hace utilizable como comprobación en integración continua sin escribir un
 * framework de tests: {@code CompiladorCLI tests/*.txt && echo OK}.
 */
public final class CompiladorCLI {

    private static boolean verbose = false;

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.err.println("uso: CompiladorCLI [-v] <archivo.txt> [mas archivos...]");
            System.exit(2);
        }

        List<Path> archivos = new ArrayList<>();
        for (String a : args) {
            if (a.equals("-v") || a.equals("--verbose")) verbose = true;
            else archivos.add(Path.of(a));
        }

        int fallos = 0;
        int rutasInexistentes = 0;
        for (Path p : archivos) {
            if (!Files.isReadable(p)) {
                System.err.println("no se puede leer: " + p);
                rutasInexistentes++;
                continue;
            }
            try {
                if (!compilar(p)) fallos++;
            } catch (IOException e) {
                System.err.println("error de lectura en " + p + ": " + e.getMessage());
                rutasInexistentes++;
            }
        }
        System.out.println();
        System.out.println(fallos + rutasInexistentes == 0
            ? archivos.size() + " archivo(s): todos compilan."
            : (fallos > 0 ? fallos + " de " + archivos.size() + " archivo(s) con errores." : "")
              + (rutasInexistentes > 0 ? rutasInexistentes + " archivo(s) ilegibles." : ""));
        System.exit(fallos + rutasInexistentes == 0 ? 0 : 1);
    }

    /** Corre las fases sobre un archivo. Devuelve {@code true} si no hubo errores. */
    private static boolean compilar(Path ruta) throws IOException {
        System.out.println("=== " + ruta.getFileName() + " ===");

        String fuente = Files.readString(ruta, StandardCharsets.UTF_8);

        Lexer lexer = new Lexer(fuente);
        List<Token> tokens = lexer.escanear();
        List<String> erroresLexicos = lexer.getErroresLexicos();

        Parser parser = new Parser(tokens);
        Programa programa = parser.parsear();
        List<String> erroresSintacticos = parser.getErroresSintacticos();

        List<String> erroresSemanticos = new ArrayList<>();
        List<String> avisos = new ArrayList<>();
        if (erroresSintacticos.isEmpty()) {
            erroresSemanticos = new AnalizadorSemantico().analizar(programa, tokens, avisos);
        }

        System.out.printf("  %d tokens - lexico %d - sintactico %d - semantico %d - avisos %d%n",
            tokens.size(), erroresLexicos.size(), erroresSintacticos.size(),
            erroresSemanticos.size(), avisos.size());

        for (String e : erroresLexicos) System.out.println("  [LEX] " + e);
        for (String e : erroresSintacticos) System.out.println("  [SIN] " + e);
        for (String e : erroresSemanticos) System.out.println("  [SEM] " + e);
        for (String a : avisos) System.out.println("  " + a);

        if (verbose && erroresSintacticos.isEmpty()) {
            volcar(programa);
        }

        return erroresLexicos.isEmpty() && erroresSintacticos.isEmpty()
            && erroresSemanticos.isEmpty();
    }

    private static void volcar(Programa programa) {
        List<String> conflictos = Gramatica.conflictos();
        System.out.println("  -- gramatica --");

        if (conflictos.isEmpty()) {
            System.out.printf("     LL(1) sin conflictos: %d no terminales, "
                + "%d producciones, %d tokens%n",
                Gramatica.NUM_NT, Gramatica.NUM_PROD, TipoToken.values().length);
        } else {
            System.out.println("     LL(1) CON " + conflictos.size() + " CONFLICTO(S):");
            for (String c : conflictos) System.out.println("       " + c);
        }

        System.out.println("  -- tabla de simbolos --");
        for (Simbolo s : programa.simbolos.todos()) {
            System.out.printf("     %-22s %-7s %-11s %-4s linea %d%n",
                s.nombre(), s.clase(), s.nombreTipo(), s.unidad(), s.linea());
        }

        Map<String, Set<String>> grafo = programa.simbolos.grafoDependencias();
        if (!grafo.isEmpty()) {
            System.out.println("  -- flujo de datos --");
            for (Map.Entry<String, Set<String>> e : grafo.entrySet()) {
                System.out.printf("     %-22s <- %s%n", e.getKey(), e.getValue());
            }
        }

        if (!programa.calculos.isEmpty()) {
            System.out.println("  -- memoria por calculo --");
            for (Calculo c : programa.calculos) {
                System.out.printf("     %-22s %-14s %s%n",
                    c.resultado(),
                    c.esExpresion() ? "(expresion)" : c.operacion.toUpperCase(),
                    c.memoria());
            }
        }

        System.out.printf("  -- %d regla(s), muestreo = %s, estado inicial = %s, por defecto = %s%n",
            programa.reglas.size(),
            programa.muestreo == null ? "sin declarar" : programa.muestreo + " s",
            programa.estadoInicial,
            programa.estadoPorDefecto);

        System.out.println("  -- reglas en orden de evaluacion --");
        for (Regla r : programa.reglas) {
            System.out.printf("     linea %-4d %s  ->  %s%n", r.linea, r.condicion, r.estado);
        }
    }
}