package timotomata.parser.ast;

import timotomata.semantic.TipoSensor;

/**
 * Declaración de un canal físico: `sensor voltaje : ELECTRICO;`.
 *
 * El tipo se declara de forma explícita. Antes la unidad física se deducía
 * del nombre del identificador, lo que dejaba sin verificar cualquier sensor
 * que no contuviera una palabra reconocible ("v1", "s1", "entrada").
 */
public class Sensor {

    private final String nombre;
    private final TipoSensor tipo;
    private final int linea;

    public Sensor(String nombre, TipoSensor tipo, int linea) {
        this.nombre = nombre;
        this.tipo = tipo;
        this.linea = linea;
    }

    public String nombre() {
        return nombre;
    }

    /** Tipo declarado. */
    public TipoSensor tipo() {
        return tipo;
    }

    public int linea() {
        return linea;
    }

    /** Unidad canónica del tipo declarado. */
    public String unidad() {
        return tipo == null ? "—" : tipo.unidad();
    }

    @Override
    public String toString() {
        return "sensor " + nombre + " : " + (tipo == null ? "?" : tipo.etiqueta()) + ";";
    }
}