package timotomata.semantico;

import java.util.*;
import timotomata.parser.ast.*;

public class AnalizadorSemantico {
    public Set<String> sensores = new HashSet<>();
    public Map<String, Double> umbrales = new HashMap<>();
    public List<String> errores = new ArrayList<>();

    public void analizar(Programa programa) {
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

        for (Calculo c : programa.calculos) {
            validarCalculo(c);
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

    void validarCalculo(Calculo c) {
        // Validar que el sensor destino exista
        if (!sensores.contains(c.sensor)) {
            errores.add("CALCULAR: sensor '" + c.sensor + "' no declarado");
            return; // No seguir validando si el sensor no existe
        }

        // Validar parámetros CON: el sensor referenciado debe existir
        for (Parametro p : c.parametros) {
            if (p.nombre.equals("con") && !sensores.contains(p.valor)) {
                errores.add("CALCULAR: sensor '" + p.valor + "' no declarado");
            }
        }
    }
}
