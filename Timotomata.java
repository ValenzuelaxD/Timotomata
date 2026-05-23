import java.util.*;

public class Timotomata {

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

            System.out.println("t=" + tiempo +
                    " | voltaje=" + voltaje +
                    " | previo=" + previo +
                    " | estado=" + estado);

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

    enum TipoToken {
        SENSOR, UMBRAL, SI, ENTONCES, ESTADO, ABS,
        ID, NUMERO, ESTADO_SISTEMA,
        MAYOR, MENOR, IGUAL_IGUAL, MAYOR_IGUAL, MENOR_IGUAL, DIFERENTE,
        MAS, MENOS, POR, DIV,
        ASIGNACION, PUNTO_COMA, PAREN_IZQ, PAREN_DER,
        EOF
    }

    static class Token {
        TipoToken tipo;
        String lexema;
        int linea;

        Token(TipoToken tipo, String lexema, int linea) {
            this.tipo = tipo;
            this.lexema = lexema;
            this.linea = linea;
        }

        public String toString() {
            return tipo + " -> '" + lexema + "' linea " + linea;
        }
    }

    static class Lexer {
        String fuente;
        List<Token> tokens = new ArrayList<>();
        int actual = 0;
        int linea = 1;

        Lexer(String fuente) {
            this.fuente = fuente;
        }

        List<Token> escanear() {
            while (!fin()) {
                char c = avanzar();

                switch (c) {
                    case ' ', '\t', '\r' -> {}
                    case '\n' -> linea++;
                    case ';' -> agregar(TipoToken.PUNTO_COMA, ";");
                    case '(' -> agregar(TipoToken.PAREN_IZQ, "(");
                    case ')' -> agregar(TipoToken.PAREN_DER, ")");
                    case '+' -> agregar(TipoToken.MAS, "+");
                    case '-' -> agregar(TipoToken.MENOS, "-");
                    case '*' -> agregar(TipoToken.POR, "*");
                    case '/' -> agregar(TipoToken.DIV, "/");

                    case '=' -> {
                        if (coincidir('=')) agregar(TipoToken.IGUAL_IGUAL, "==");
                        else agregar(TipoToken.ASIGNACION, "=");
                    }

                    case '>' -> {
                        if (coincidir('=')) agregar(TipoToken.MAYOR_IGUAL, ">=");
                        else agregar(TipoToken.MAYOR, ">");
                    }

                    case '<' -> {
                        if (coincidir('=')) agregar(TipoToken.MENOR_IGUAL, "<=");
                        else agregar(TipoToken.MENOR, "<");
                    }

                    case '!' -> {
                        if (coincidir('=')) agregar(TipoToken.DIFERENTE, "!=");
                        else throw new RuntimeException("Error lexico en linea " + linea + ": simbolo ! no valido");
                    }

                    default -> {
                        if (Character.isDigit(c)) {
                            numero(c);
                        } else if (Character.isLetter(c)) {
                            palabra(c);
                        } else {
                            throw new RuntimeException("Error lexico en linea " + linea + ": caracter no valido " + c);
                        }
                    }
                }
            }

            tokens.add(new Token(TipoToken.EOF, "", linea));
            return tokens;
        }

        void numero(char primero) {
            StringBuilder sb = new StringBuilder();
            sb.append(primero);

            while (!fin() && (Character.isDigit(ver()) || ver() == '.')) {
                sb.append(avanzar());
            }

            agregar(TipoToken.NUMERO, sb.toString());
        }

        void palabra(char primero) {
            StringBuilder sb = new StringBuilder();
            sb.append(primero);

            while (!fin() && (Character.isLetterOrDigit(ver()) || ver() == '_')) {
                sb.append(avanzar());
            }

            String texto = sb.toString();

            switch (texto) {
                case "sensor" -> agregar(TipoToken.SENSOR, texto);
                case "umbral" -> agregar(TipoToken.UMBRAL, texto);
                case "si" -> agregar(TipoToken.SI, texto);
                case "entonces" -> agregar(TipoToken.ENTONCES, texto);
                case "estado" -> agregar(TipoToken.ESTADO, texto);
                case "abs" -> agregar(TipoToken.ABS, texto);
                case "NORMAL", "PICO", "CAIDA", "INESTABLE" -> agregar(TipoToken.ESTADO_SISTEMA, texto);
                default -> agregar(TipoToken.ID, texto);
            }
        }

        char avanzar() {
            return fuente.charAt(actual++);
        }

        char ver() {
            return fuente.charAt(actual);
        }

        boolean coincidir(char esperado) {
            if (fin()) return false;
            if (fuente.charAt(actual) != esperado) return false;
            actual++;
            return true;
        }

        boolean fin() {
            return actual >= fuente.length();
        }

        void agregar(TipoToken tipo, String lexema) {
            tokens.add(new Token(tipo, lexema, linea));
        }
    }

    static class Programa {
        List<String> sensores = new ArrayList<>();
        Map<String, Double> umbrales = new HashMap<>();
        List<Regla> reglas = new ArrayList<>();

        void imprimir() {
            System.out.println("Programa");
            System.out.println("  Sensores: " + sensores);
            System.out.println("  Umbrales: " + umbrales);
            System.out.println("  Reglas:");

            for (Regla r : reglas) {
                System.out.println("    si " + r.condicion + " entonces estado = " + r.estado);
            }
        }
    }

    interface Expresion {}

    static class Numero implements Expresion {
        double valor;

        Numero(double valor) {
            this.valor = valor;
        }

        public String toString() {
            return String.valueOf(valor);
        }
    }

    static class Variable implements Expresion {
        String nombre;

        Variable(String nombre) {
            this.nombre = nombre;
        }

        public String toString() {
            return nombre;
        }
    }

    static class Abs implements Expresion {
        Expresion expresion;

        Abs(Expresion expresion) {
            this.expresion = expresion;
        }

        public String toString() {
            return "abs(" + expresion + ")";
        }
    }

    static class Binaria implements Expresion {
        Expresion izquierda;
        String operador;
        Expresion derecha;

        Binaria(Expresion izquierda, String operador, Expresion derecha) {
            this.izquierda = izquierda;
            this.operador = operador;
            this.derecha = derecha;
        }

        public String toString() {
            return izquierda + " " + operador + " " + derecha;
        }
    }

    static class Regla {
        Expresion condicion;
        String estado;

        Regla(Expresion condicion, String estado) {
            this.condicion = condicion;
            this.estado = estado;
        }
    }

    static class Parser {
        List<Token> tokens;
        int actual = 0;

        Parser(List<Token> tokens) {
            this.tokens = tokens;
        }

        Programa parsear() {
            Programa programa = new Programa();

            while (!verificar(TipoToken.EOF)) {
                if (coincidir(TipoToken.SENSOR)) {
                    Token nombre = consumir(TipoToken.ID, "Se esperaba el nombre del sensor");
                    consumir(TipoToken.PUNTO_COMA, "Se esperaba ;");
                    programa.sensores.add(nombre.lexema);

                } else if (coincidir(TipoToken.UMBRAL)) {
                    Token nombre = consumir(TipoToken.ID, "Se esperaba el nombre del umbral");
                    consumir(TipoToken.ASIGNACION, "Se esperaba =");
                    Token valor = consumir(TipoToken.NUMERO, "Se esperaba un numero");
                    consumir(TipoToken.PUNTO_COMA, "Se esperaba ;");
                    programa.umbrales.put(nombre.lexema, Double.parseDouble(valor.lexema));

                } else if (coincidir(TipoToken.SI)) {
                    Expresion condicion = condicion();
                    consumir(TipoToken.ENTONCES, "Se esperaba la palabra reservada entonces");
                    consumir(TipoToken.ESTADO, "Se esperaba la palabra reservada estado");
                    consumir(TipoToken.ASIGNACION, "Se esperaba =");
                    Token estado = consumir(TipoToken.ESTADO_SISTEMA, "Se esperaba NORMAL, PICO, CAIDA o INESTABLE");
                    consumir(TipoToken.PUNTO_COMA, "Se esperaba ;");
                    programa.reglas.add(new Regla(condicion, estado.lexema));

                } else {
                    throw new RuntimeException("Token inesperado en linea " + ver().linea + ": " + ver().lexema);
                }
            }

            return programa;
        }

        Expresion condicion() {
            Expresion izquierda = expresion();

            Token operador = consumirOperadorRelacional();

            Expresion derecha = expresion();

            return new Binaria(izquierda, operador.lexema, derecha);
        }

        Token consumirOperadorRelacional() {
            if (coincidir(TipoToken.MAYOR, TipoToken.MENOR, TipoToken.IGUAL_IGUAL,
                    TipoToken.MAYOR_IGUAL, TipoToken.MENOR_IGUAL, TipoToken.DIFERENTE)) {
                return anterior();
            }

            throw new RuntimeException("Error sintactico en linea " + ver().linea +
                    ": se esperaba operador relacional >, <, ==, >=, <= o !=");
        }

        Expresion expresion() {
            Expresion expr = termino();

            while (coincidir(TipoToken.MAS, TipoToken.MENOS)) {
                Token op = anterior();
                Expresion derecha = termino();
                expr = new Binaria(expr, op.lexema, derecha);
            }

            return expr;
        }

        Expresion termino() {
            Expresion expr = factor();

            while (coincidir(TipoToken.POR, TipoToken.DIV)) {
                Token op = anterior();
                Expresion derecha = factor();
                expr = new Binaria(expr, op.lexema, derecha);
            }

            return expr;
        }

        Expresion factor() {
            if (coincidir(TipoToken.NUMERO)) {
                return new Numero(Double.parseDouble(anterior().lexema));
            }

            if (coincidir(TipoToken.ID)) {
                return new Variable(anterior().lexema);
            }

            if (coincidir(TipoToken.ABS)) {
                consumir(TipoToken.PAREN_IZQ, "Se esperaba ( despues de abs");
                Expresion expr = expresion();
                consumir(TipoToken.PAREN_DER, "Se esperaba ) despues de la expresion");
                return new Abs(expr);
            }

            throw new RuntimeException("Error sintactico en linea " + ver().linea +
                    ": se esperaba numero, identificador o abs()");
        }

        boolean coincidir(TipoToken... tipos) {
            for (TipoToken tipo : tipos) {
                if (verificar(tipo)) {
                    avanzar();
                    return true;
                }
            }
            return false;
        }

        Token consumir(TipoToken tipo, String mensaje) {
            if (verificar(tipo)) return avanzar();

            throw new RuntimeException("Error sintactico en linea " + ver().linea + ": " + mensaje);
        }

        boolean verificar(TipoToken tipo) {
            return actual < tokens.size() && tokens.get(actual).tipo == tipo;
        }

        Token avanzar() {
            return tokens.get(actual++);
        }

        Token anterior() {
            return tokens.get(actual - 1);
        }

        Token ver() {
            return tokens.get(actual);
        }
    }

    static class AnalizadorSemantico {
        Set<String> sensores = new HashSet<>();
        Map<String, Double> umbrales = new HashMap<>();
        List<String> errores = new ArrayList<>();

        void analizar(Programa programa) {
            for (String s : programa.sensores) {
                if (sensores.contains(s) || umbrales.containsKey(s)) {
                    errores.add("Variable redeclarada: " + s);
                }

                sensores.add(s);
            }

            for (String u : programa.umbrales.keySet()) {
                if (sensores.contains(u) || umbrales.containsKey(u)) {
                    errores.add("Variable redeclarada: " + u);
                }

                umbrales.put(u, programa.umbrales.get(u));
            }

            for (Regla r : programa.reglas) {
                validar(r.condicion);
            }
        }

        void validar(Expresion expr) {
            if (expr instanceof Variable v) {
                if (!sensores.contains(v.nombre)
                        && !umbrales.containsKey(v.nombre)
                        && !v.nombre.equals("previo")) {
                    errores.add("Variable no declarada: " + v.nombre);
                }
            }

            if (expr instanceof Binaria b) {
                validar(b.izquierda);
                validar(b.derecha);
            }

            if (expr instanceof Abs a) {
                validar(a.expresion);
            }
        }
    }
}