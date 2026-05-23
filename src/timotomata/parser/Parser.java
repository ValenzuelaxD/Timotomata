package timotomata.parser;

import java.util.*;
import timotomata.lexer.Token;
import timotomata.lexer.TipoToken;
import timotomata.parser.ast.*;

public class Parser {
    List<Token> tokens;
    int actual = 0;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public Programa parsear() {
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
        throw new RuntimeException("Error sintactico en linea " + ver().linea
                + ": se esperaba operador relacional >, <, ==, >=, <= o !=");
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
        throw new RuntimeException("Error sintactico en linea " + ver().linea
                + ": se esperaba numero, identificador o abs()");
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
