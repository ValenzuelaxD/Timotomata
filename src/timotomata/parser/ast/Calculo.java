package timotomata.parser.ast;

import java.util.*;

public class Calculo {
    public String sensor;       // nombre del sensor
    public String operacion;    // seno, coseno, cuadrada, promedio, maximo, suma
    public List<Parametro> parametros = new ArrayList<>();
    public int linea = 0;       // línea del 'calcular' para errores semánticos

    public Calculo(String sensor, String operacion) {
        this.sensor = sensor;
        this.operacion = operacion;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("calcular(").append(sensor).append(", ").append(operacion).append("(");
        for (int i = 0; i < parametros.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(parametros.get(i));
        }
        sb.append("))");
        return sb.toString();
    }
}
