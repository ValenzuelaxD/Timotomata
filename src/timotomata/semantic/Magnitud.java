package timotomata.semantic;

import java.text.Normalizer;

/**
 * Magnitud (unidad física) de un sensor, clasificada por su nombre.
 *
 * El lenguaje no tiene sintaxis para declarar unidades, así que la
 * clasificación es una tabla heurística sobre el lexema del identificador.
 * Si el nombre no reconoce ninguna magnitud se retorna null y la regla de
 * "combinación de magnitudes incompatibles" NO se reporta (para evitar
 * falsos positivos con nombres arbitrarios).
 */
import java.text.Normalizer;

public enum Magnitud {
    ELECTRICO,
    TERMICO;

    /**
     * Clasifica el nombre de un sensor. No distingue mayúsculas ni acentos.
     *
     * @return la magnitud, o null si el nombre no corresponde a ninguna.
     */
    public static Magnitud clasificar(String nombre) {
        if (nombre == null) return null;

        String n = Normalizer.normalize(nombre.toLowerCase(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
//
        if (contiene(n, "voltaj", "tension", "volt", "corrient", "amper",
                "potencia", "watt", "resistenc", "ohm", "capacit", "energ")) {
            return ELECTRICO;
        }

        if (contiene(n, "temper", "temp", "calor", "celsius", "fahrenheit",
                "kelvin", "termo")) {
            return TERMICO;
        }

        return null;
    }

    private static boolean contiene(String texto, String... claves) {
        for (String c : claves) {
            if (texto.contains(c)) {
                return true;
            }
        }
        return false;
    }
}
