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
