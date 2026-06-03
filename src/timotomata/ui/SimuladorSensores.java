package timotomata.ui;

import java.util.*;
import timotomata.parser.ast.*;

public class SimuladorSensores {
    public static class Muestra {
        public final int tiempo;
        public final double valor;
        public final String estado;
        public final String alerta;

        public Muestra(int tiempo, double valor, String estado, String alerta) {
            this.tiempo = tiempo;
            this.valor = valor;
            this.estado = estado;
            this.alerta = alerta;
        }

        public int getTiempo() { return tiempo; }
        public double getValor() { return Math.round(valor * 100.0) / 100.0; }
        public String getEstado() { return estado; }
        public String getAlerta() { return alerta != null ? alerta : "—"; }
    }

    public static List<Muestra> generarSimulacion(Programa programa, String sensor, int numMuestras) {
        List<Muestra> result = new ArrayList<>();
        
        // 1. Obtener límites de rango
        double minSeguro = 110.0;
        double maxSeguro = 127.0;
        if (programa.rangos.containsKey(sensor)) {
            Programa.RangoSeguro r = programa.rangos.get(sensor);
            minSeguro = r.minimo;
            maxSeguro = r.maximo;
        }
        
        // 2. Determinar si hay alguna instrucción "calcular" para este sensor
        Calculo calc = null;
        for (Calculo c : programa.calculos) {
            if (c.sensor.equals(sensor)) {
                calc = c;
                break;
            }
        }
        
        double amp = (maxSeguro - minSeguro) * 0.8;
        double frec = 0.25;
        double centro = (maxSeguro + minSeguro) / 2.0;
        String operacion = (calc != null) ? calc.operacion : "fluctuacion";
        
        if (calc != null) {
            for (Parametro p : calc.parametros) {
                if (p.nombre.equals("amplitud")) {
                    amp = Double.parseDouble(p.valor);
                } else if (p.nombre.equals("frecuencia")) {
                    frec = Double.parseDouble(p.valor);
                }
            }
        }
        
        Random rand = new Random();
        
        for (int t = 1; t <= numMuestras; t++) {
            double val = centro;
            
            // Generar según operación
            switch (operacion.toLowerCase()) {
                case "seno":
                    val = centro + amp * Math.sin(frec * t);
                    break;
                case "coseno":
                    val = centro + amp * Math.cos(frec * t);
                    break;
                case "cuadrada":
                    val = centro + amp * (Math.sin(frec * t) >= 0 ? 1.0 : -1.0);
                    break;
                case "promedio":
                    // Genera una caminata aleatoria y calcula promedio
                    double rWalk = centro + amp * Math.sin(0.15 * t) + (rand.nextDouble() - 0.5) * (amp * 0.4);
                    val = rWalk;
                    break;
                case "maximo":
                    val = centro + amp * 0.5 * Math.log(t + 1) + (rand.nextDouble() - 0.5) * (amp * 0.2);
                    break;
                case "suma":
                    val = centro + amp * 0.05 * t + (rand.nextDouble() - 0.5) * (amp * 0.3);
                    break;
                case "fluctuacion":
                default:
                    double base = centro + (maxSeguro - minSeguro) * 0.4 * Math.sin(0.1 * t);
                    double spike = 0.0;
                    if (t == 10 || t == 25) {
                        spike = (maxSeguro - minSeguro) * 0.6; // Provocar Pico
                    } else if (t == 18 || t == 40) {
                        spike = -(maxSeguro - minSeguro) * 0.6; // Provocar Caída
                    }
                    val = base + (rand.nextDouble() - 0.5) * ((maxSeguro - minSeguro) * 0.2) + spike;
                    break;
            }
            
            // 3. Evaluar las reglas sobre este valor
            String estado = "NORMAL";
            String alerta = null;
            
            // Crear el contexto de variables para evaluación
            Map<String, Double> contexto = new HashMap<>();
            // Agregar umbrales
            for (Map.Entry<String, Double> e : programa.umbrales.entrySet()) {
                contexto.put(e.getKey(), e.getValue());
            }
            // Colocar el sensor actual
            contexto.put(sensor, val);
            // Colocar 'previo' por si lo usan
            contexto.put("previo", centro);
            
            // Evaluar reglas
            for (Regla r : programa.reglas) {
                if (evaluarCondicion(r.condicion, contexto)) {
                    if (r.estado != null) {
                        estado = r.estado;
                    }
                    if (r.alerta != null) {
                        alerta = r.alerta;
                    }
                }
            }
            
            result.add(new Muestra(t, val, estado, alerta));
        }
        
        return result;
    }

    private static boolean evaluarCondicion(Expresion cond, Map<String, Double> contexto) {
        if (cond == null) return false;
        Object res = evaluar(cond, contexto);
        if (res instanceof Boolean) {
            return (Boolean) res;
        }
        return false;
    }

    private static Object evaluar(Expresion expr, Map<String, Double> contexto) {
        if (expr instanceof Numero n) {
            return n.valor;
        }
        if (expr instanceof Variable v) {
            if (contexto.containsKey(v.nombre)) {
                return contexto.get(v.nombre);
            }
            return 0.0;
        }
        if (expr instanceof Negacion neg) {
            Object val = evaluar(neg.expresion, contexto);
            if (val instanceof Double) {
                return -(Double) val;
            }
            return 0.0;
        }
        if (expr instanceof Abs a) {
            Object val = evaluar(a.expresion, contexto);
            if (val instanceof Double) {
                return Math.abs((Double) val);
            }
            return 0.0;
        }
        if (expr instanceof Binaria bin) {
            Object izq = evaluar(bin.izquierda, contexto);
            Object der = evaluar(bin.derecha, contexto);
            
            String op = bin.operador.toLowerCase();
            
            if (op.equals("y") || op.equals("o")) {
                boolean bIzq = false;
                boolean bDer = false;
                if (izq instanceof Boolean) bIzq = (Boolean) izq;
                if (der instanceof Boolean) bDer = (Boolean) der;
                
                if (op.equals("y")) return bIzq && bDer;
                if (op.equals("o")) return bIzq || bDer;
            }
            
            if (izq instanceof Double && der instanceof Double) {
                double dIzq = (Double) izq;
                double dDer = (Double) der;
                
                return switch (op) {
                    case "+" -> dIzq + dDer;
                    case "-" -> dIzq - dDer;
                    case "*" -> dIzq * dDer;
                    case "/" -> dDer != 0 ? dIzq / dDer : 0.0;
                    case ">" -> dIzq > dDer;
                    case "<" -> dIzq < dDer;
                    case "==" -> dIzq == dDer;
                    case ">=" -> dIzq >= dDer;
                    case "<=" -> dIzq <= dDer;
                    case "!=" -> dIzq != dDer;
                    default -> 0.0;
                };
            }
            return false;
        }
        return 0.0;
    }
}
