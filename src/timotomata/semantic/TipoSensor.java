package timotomata.semantic;

import java.util.Locale;

/**
 * TIPO DE SENSOR — ficha técnica declarada en el lenguaje.
 * =====================================================
 *
 * El lenguaje declara el tipo en la sentencia de declaración:
 *
 *     sensor voltaje : ELECTRICO;
 *
 * Antes esta información no existía: el analizador semántico adivinaba la
 * unidad a partir del lexema del identificador (ver {@code Magnitud}, que
 * esta clase reemplaza). Adivinar por nombre es frágil —un sensor llamado
 * {@code v1} no se clasifica, y las reglas dependent de tipo se saltaban en
 * silencio—, mientras que declarar hace la verificación siempre correcta y
 * le da al generador de código el tipo real de cada canal.
 *
 * <p>Cada constante es la ficha canónica del tipo. Los valores proceden de la
 * física del transductor, no son arbitrarios:
 *
 * <ul>
 *   <li><b>frecuenciaMuestreo</b>: cuántos muestras por segundo puede tomar el
 *       transductor. Marca el período mínimo observable.</li>
 *   <li><b>bandaMaxima</b>: frecuencia máxima que el sensor puede faithfully
 *       seguir (límite de anchura de banda). Todo {@code FRECUENCIA=} por
 *       encima de este valor es físicamente imposible de observar.</li>
 *   <li><b>ventanaMinima</b>: cantidad mínima de muestras que exige
 *       {@code PROMEDIO()} para tener sentido físico. Un sensor térmico no
 *       "promedia" 3 muestras tomadas cada 10 segundos.</li>
 * </ul>
 *
 * El conjunto es <b>cerrado</b>: agregar un tipo obliga a tocar el generador
 * de código, que despacha sobre este enum. Eso es intencional — un tipo
 * abierto no se puede usar para generar código.
 */
public enum TipoSensor {

    /** Tensión, corriente, potencia, resistencia. Red fundamental 50/60 Hz. */
    ELECTRICO("V", 0.0, 250.0, 100.0, 50.0, 5),

    /** Temperatura. Transductor con constante de tiempo de segundos. */
    TERMICO("°C", -40.0, 125.0, 0.1, 0.05, 20),

    /** Iluminación ambiental. Transductor rápido, rango muy amplio. */
    LUMINICO("lux", 0.0, 10000.0, 10.0, 5.0, 3),

    /** Vibración, velocidad, RPM. Exige muestreo en kHz por el criterio de Nyquist. */
    MECANICO("mm/s", 0.0, 50.0, 2000.0, 1000.0, 3),

    /** pH, humedad, concentración, gases. Lectura muy lenta (10-30 s). */
    QUIMICO("pH", 0.0, 14.0, 0.01, 0.005, 50),

    /** Presión, caudal, nivel. */
    HIDRAULICO("bar", 0.0, 400.0, 1.0, 0.5, 10);

    private final String unidad;
    private final double rangoMin;
    private final double rangoMax;
    private final double frecuenciaMuestreo;
    private final double bandaMaxima;
    private final int ventanaMinima;

    TipoSensor(String unidad, double rangoMin, double rangoMax,
               double frecuenciaMuestreo, double bandaMaxima, int ventanaMinima) {
        this.unidad = unidad;
        this.rangoMin = rangoMin;
        this.rangoMax = rangoMax;
        this.frecuenciaMuestreo = frecuenciaMuestreo;
        this.bandaMaxima = bandaMaxima;
        this.ventanaMinima = ventanaMinima;
    }

    // =============================================================
    //  ACCESORES DE LA FICHA
    // =============================================================

    /** Unidad canónica del tipo (V, °C, lux, mm/s, pH, bar). */
    public String unidad() {
        return unidad;
    }

    public double rangoMin() {
        return rangoMin;
    }

    public double rangoMax() {
        return rangoMax;
    }

    /** Muestras por segundo que admite el transductor. */
    public double frecuenciaMuestreo() {
        return frecuenciaMuestreo;
    }

    /** Frecuencia máxima observable (Hz). Límite superior de {@code FRECUENCIA=}. */
    public double bandaMaxima() {
        return bandaMaxima;
    }

    /** Muestras mínimas que exige {@code PROMEDIO()} en este tipo. */
    public int ventanaMinima() {
        return ventanaMinima;
    }

    /** Periodo de muestreo en segundos: duración de una muestra. */
    public double periodoMuestreo() {
        return 1.0 / frecuenciaMuestreo;
    }

    /** Etiqueta en mayúsculas, como aparece en el programa fuente. */
    public String etiqueta() {
        return name().toUpperCase(Locale.ROOT);
    }

    // =============================================================
    //  VERIFICACIONES SOBRE LA FICHA
    // =============================================================

    /**
     * Verifica que una frecuencia de operación sea observable por el sensor.
     *
     * <p>Una señal por encima de la banda del transductor no se puede medir:
     * el muestreo la destruiría por aliasing. Se usa para
     * {@code SENO}/{@code COSENO}/{@code CUADRADA}, que declaran
     * {@code FRECUENCIA=} en hercios.
     *
     * @return {@code true} si la frecuencia es físicamente observable
     */
    public boolean frecuenciaObservable(double frecuenciaHz) {
        return frecuenciaHz > 0 && frecuenciaHz <= bandaMaxima;
    }

    /** Rango representable por el transductor. */
    public boolean enRango(double valor) {
        return valor >= rangoMin && valor <= rangoMax;
    }

    // =============================================================
    //  CONVERSIÓN DESDE EL LENGUAJE
    // =============================================================

    /**
     * Resuelve el lexema de una palabra reservada de tipo a su constante.
     *
     * <p>Ya no se deduce del nombre del sensor: el tipo viaja explícito en el
     * token, así que la correspondencia es exacta y no admite falsos
     * positivos ni negativos.
     *
     * @return el tipo, o {@code null} si el lexema no es un tipo válido
     */
    public static TipoSensor deLexema(String lexema) {
        if (lexema == null) return null;
        try {
            return valueOf(lexema.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Nombres aceptados tras {@code :} en la declaración de un sensor. */
    public static String listaNombres() {
        StringBuilder sb = new StringBuilder();
        for (TipoSensor t : values()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(t.etiqueta());
        }
        return sb.toString();
    }
}