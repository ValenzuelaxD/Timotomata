package timotomata.parser.ast;

/**
 * Sentencia `calcular(origen, OPERACION(...), destino);`
 *
 * <p>El destino es una señal derivada distinta del canal de origen: el sensor
 * físico se conserva como lectura del mundo real, y el resultado del cálculo
 * vive aparte. Esto evita la ambigüedad de tratar al identificador del sensor
 * a la vez como entrada del hardware y como resultado del cálculo — con lo
 * cual las reglas no sabían si estaban midiendo una cosa u otra.
 *
 * <p>Si se omite el destino, el cálculo sobrescribe el canal de origen
 * (comportamiento heredado, que se conserva por compatibilidad).
 */
public class Calculo {

    /** Sensor o señal de la que se lee. */
    public String sensor;

    /** Identificador del destino; {@code null} significa sobrescribir el origen. */
    public String destino;

    /** Operacion aplicada: seno, coseno, cuadrada, promedio, maximo, suma. */
    public String operacion;

    public java.util.List<Parametro> parametros = new java.util.ArrayList<>();

    public int linea = 0;

    /** {@code true} cuando el resultado se guarda en una señal derivada nueva. */
    public boolean deriva() {
        return destino != null;
    }

    /** Nombre por el que el resultado queda disponible. */
    public String resultado() {
        return destino != null ? destino : sensor;
    }

    public Calculo(String sensor, String operacion) {
        this.sensor = sensor;
        this.operacion = operacion;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("calcular(").append(sensor).append(", ").append(operacion).append("(");
        for (int i = 0; i < parametros.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(parametros.get(i));
        }
        sb.append(")");
        if (deriva()) sb.append(", ").append(destino);
        sb.append(")");
        return sb.toString();
    }
}