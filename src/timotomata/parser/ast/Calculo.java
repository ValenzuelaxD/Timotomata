package timotomata.parser.ast;

import java.util.*;

public class Calculo {
    public String sensor;       // nombre del sensor
    public String operacion;    // seno, coseno, cuadrada, promedio, maximo, suma
    public List<Parametro> parametros = new ArrayList<>();

    public Calculo(String sensor, String operacion) {
        this.sensor = sensor;
        this.operacion = operacion;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("calcular ").append(sensor).append(" , ").append(operacion);
        for (Parametro p : parametros) {
            sb.append(" , ").append(p);
        }
        return sb.toString();
    }
}
