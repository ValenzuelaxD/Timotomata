package timotomata.parser.ast;

import java.util.*;

public class Programa {
    public List<String> sensores = new ArrayList<>();
    public Map<String, String> tiposSensores = new HashMap<>();
    
    public static class RangoSeguro {
        public String sensor;
        public double minimo;
        public double maximo;

        public RangoSeguro(String sensor, double minimo, double maximo) {
            this.sensor = sensor;
            this.minimo = minimo;
            this.maximo = maximo;
        }

        @Override
        public String toString() {
            return "[min: " + minimo + ", max: " + maximo + "]";
        }
    }
    
    public Map<String, RangoSeguro> rangos = new HashMap<>();
    public Map<String, Double> umbrales = new HashMap<>();
    public List<Regla> reglas = new ArrayList<>();
    public List<Calculo> calculos = new ArrayList<>();

    public void imprimir() {
        System.out.println("Programa");
        System.out.println("  Sensores: " + sensores);
        System.out.println("  Tipos Sensores: " + tiposSensores);
        System.out.println("  Rangos: " + rangos);
        System.out.println("  Umbrales: " + umbrales);
        System.out.println("  Reglas:");
        for (Regla r : reglas) {
            System.out.println("    si " + r.condicion + " entonces estado = " + r.estado + (r.alerta != null ? " alerta = " + r.alerta : ""));
        }
        System.out.println("  Calculos:");
        for (Calculo c : calculos) {
            System.out.println("    " + c);
        }
    }
}
