package timotomata.parser.ast;

import java.util.*;

public class Programa {
    public List<String> sensores = new ArrayList<>();
    public Map<String, Double> umbrales = new HashMap<>();
    public List<Regla> reglas = new ArrayList<>();

    public void imprimir() {
        System.out.println("Programa");
        System.out.println("  Sensores: " + sensores);
        System.out.println("  Umbrales: " + umbrales);
        System.out.println("  Reglas:");

        for (Regla r : reglas) {
            System.out.println("    si " + r.condicion + " entonces estado = " + r.estado);
        }
    }
}
