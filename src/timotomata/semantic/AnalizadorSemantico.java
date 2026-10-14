package timotomata.semantic;

import java.util.*;

import timotomata.lexer.Token;
import timotomata.parser.ast.*;
import timotomata.simbolos.Simbolo;
import timotomata.simbolos.TablaSimbolos;

// Intervalo es un record anidado dentro de Intervalos; el analizador vive en
// el mismo paquete, asi que alcanza con importar el record.
import timotomata.semantic.Intervalos.Intervalo;

/**
 * ANÁLISIS SEMÁNTICO DEL LENGUAJE TIMOTOMATA
 * ==========================================
 *
 * Se ejecuta después del parser, sobre el AST y sobre la tabla de símbolos
 * que este construyó. Ya no re-escanea la lista de tokens para deducir las
 * declaraciones: la tabla es la única fuente de verdad.
 *
* <h2>Errores semánticos propios</h2>
 * <ol>
 *   <li>1. Sensor y umbral con el mismo nombre (colisión de tipos)</li>
 *   <li>2. Declaración duplicada de sensor, umbral o señal</li>
 *   <li>3. Sensor usado como umbral (límite fijo de una condición)</li>
 *   <li>4. Umbral usado como sensor (objetivo de calcular() o de CON=...)</li>
 *   <li>5. Rango mínimo/máximo inválido (min &gt;= max)</li>
 *   <li>6. Diferencia entre máximo y mínimo insuficiente</li>
 *   <li>7. Estado incompatible con el rango (PICO en caída / CAIDA en pico)</li>
 *   <li>8. Reglas contradictorias (se solapan y asignan estados distintos)</li>
 *   <li>9. Magnitudes mezcladas dentro de una expresión de {@code calcular}</li>
 *   <li>10. Parámetros físicamente inválidos (frecuencia, ventana, amplitud)</li>
 *   <li>11. Combinación de magnitudes incompatibles en SUMA(CON=...)</li>
 * </ol>
 *
 * <h2>Reglas genéricas</h2>
 * <ol start="13">
 *   <li>13. Variable o identificador no declarado</li>
 *   <li>14. Incompatibilidad de tipos entre sensores de magnitud distinta</li>
 *   <li>15. Firma incorrecta de la llamada a {@code calcular()}</li>
 *   <li>16. División entre cero constante</li>
 * </ol>
 *
 * <h2>Tipos de sensor declarados</h2>
 * <ol start="17">
 *   <li>17. Umbral fuera del rango representable por el transductor</li>
 *   <li>18. {@code FRECUENCIA=} por encima de la banda del sensor</li>
 *   <li>19. {@code VENTANA=} por debajo del mínimo físico del tipo</li>
 *   <li>20. Tipo de sensor ausente en una referencia</li>
 * </ol>
 *
 * <h2>Reglas de flujo de datos</h2>
 * <ol start="21">
 *   <li>21. Orden de definición: una señal usada antes de su {@code calcular}</li>
 *   <li>22. Autodependencia: el destino también aparece en la expresión</li>
 *   <li>23. Umbral de otra magnitud en la comparación</li>
 * </ol>
 *
 * <p>Las reglas 3, 14, 16 y 23 recorren las comparaciones hoja de la condición
 * en vez de la condición entera. Desde que existen {@code Y}, {@code O} y
 * {@code NO} eso no es un detalle: {@code voltaje > vmax y temp > tmax} junta
 * dos magnitudes en la misma línea sin que haya nada malo en ello, y agrupar
 * por toda la línea las marcaría como error.
 *
 * <p>Las reglas 18 y 19 existían solo de forma parcial contra una constante
 * global ({@code FRECUENCIA_MUESTREO = 1.0}) que no pertenecía al lenguaje.
 * Ahora se comparan contra la ficha técnica del tipo declarado, que sí.
 *
 * <p>La 9 es la 11 aplicada a las expresiones: es la única que revisa que una
 * expresión de {@code calcular} mida en una sola magnitud. Antes se numeraba 27
 * y se añadió al margen; ocupa este hueco porque la serie del documento se
 * saltaba el 9.
 *
 * <p>La 12 ("programa terminado de forma inválida") queda omitida porque el
 * lenguaje ya no tiene sentencia {@code FIN}.
 *
 * <p>El analizador solo reporta defectos. Lo que un programa deja
 * deliberadamente fuera —la banda de operación normal, entre el mínimo y el
 * máximo de un sensor— no se reporta: el sistema se comporta como se le pide
 * y no dispara nada, y eso no es un error del código.
 */
public class AnalizadorSemantico {

    /** Margen mínimo exigido entre el máximo y el mínimo de un sensor. */
    public static final double MARGEN_SEGURIDAD = 10.0;

    // =============================================================
    //  PUNTO DE ENTRADA
    // =============================================================

    /**
     * Corre todas las reglas y devuelve los errores.
     *
     * <p>El analizador solo reporta defectos. Lo que el programa deja
     * deliberadamente fuera —la banda de operación normal, entre el mínimo y
     * el máximo de un sensor— no se reporta: se comporta como se le pide y no
     * dispara nada, y eso no es un error del código.
     *
     * @param tokens los tokens del programa; se reserva para las fases
     *               posteriores y hoy no lo consulta ninguna regla
     */
    public List<String> analizar(Programa programa, List<Token> tokens) {
        List<String> errores = new ArrayList<>();
        if (programa == null) return errores;

        // Un documento vacío, o que solo tiene comentarios, no es un programa:
        // todavía no hay nada escrito. Se sale antes de correr nada. Hoy ninguna
        // regla dispararía sobre un programa vacío —todas recorren colecciones
        // vacías—, así que la guarda no cambia el resultado; lo que evita es que
        // la siguiente regla que exija una declaración reporte un error sobre un
        // documento que el usuario acaba de abrir o de vaciar.
        if (esProgramaVacio(programa)) return errores;

        TablaSimbolos simbolos = programa.simbolos;

        resolverTiposDeSenales(programa);

        regla1Colision(simbolos, errores);
        regla2Duplicadas(simbolos, errores);
        regla3SensorComoUmbral(programa, errores);
        regla4UmbralComoSensor(programa, errores);
        reglas5y6Rango(programa, errores);
        regla7EstadoIncompatible(programa, errores);
        regla8ReglasContradictorias(programa, errores);
        regla10ParametrosInvalidos(programa, errores);
        regla11Magnitudes(programa, errores);
        regla9MezclaEnExpresion(programa, errores);
        regla13NoDeclarados(programa, errores);
        regla14TiposIncompatibles(programa, errores);
        regla15LlamadasIncorrectas(programa, errores);
        regla16DivisionPorCero(programa, errores);
        reglas17a19Fisica(programa, errores);
        regla20TiposAusentes(programa, errores);
        regla21OrdenDefinicion(programa, errores);
        regla22Autodependencia(programa, errores);
        regla23UmbralDeOtraMagnitud(programa, errores);

        ordenarPorLinea(errores);
        return errores;
    }

    private void err(List<String> errores, int linea, String mensaje) {
        errores.add("Error semantico en linea " + linea + ": " + mensaje);
    }

    private void ordenarPorLinea(List<String> errores) {
        errores.sort(Comparator.comparingInt(this::lineaDe));
    }

    private int lineaDe(String error) {
        int i = error.toLowerCase().indexOf("en linea ");
        if (i < 0) return 0;
        int ini = i + "en linea ".length();
        int fin = ini;
        while (fin < error.length() && Character.isDigit(error.charAt(fin))) fin++;
        try {
            return fin > ini ? Integer.parseInt(error.substring(ini, fin)) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // =============================================================
    //  RESOLUCIÓN DE TIPOS DE LAS SEÑALES DERIVADAS
    // =============================================================

    /**
     * Una señal derivada (`calcular(origen, op, destino)`) se mide en la misma
     * magnitud física que su origen, así que hereda su tipo declarado.
     *
     * <p>El parseo no puede resolverlo porque la firma de la operación aún no
     * se conoce cuando se declara el destino; por eso se completa acá, antes
     * de correr cualquier verificación que dependa del tipo.
     *
     * <p>Solo se hereda de un canal o de otra señal. Un umbral sí declara un
     * tipo, pero no es un canal físico: no hay transductor detrás. La regla 4
     * ya marca leer de un umbral como error, y si la señal heredara su tipo
     * pasaría inadvertida y quedaría sin verificar. Que se quede sin tipo hace
     * que la regla 20 la señale por separado.
     */
    private void resolverTiposDeSenales(Programa programa) {
        for (Calculo c : programa.calculos) {
            Simbolo destino = programa.simbolos.buscar(c.destino);
            if (destino == null || destino.tipo() != null) continue;

            // El tipo sale de lo que la transformación lee: para una operación,
            // de su CON=; para una expresión, de las variables que aparecen.
            TipoSensor t = tipoDeLectura(c, programa);
            if (t != null) destino.asignarTipo(t);
        }
    }

    /**
     * Magnitud física que lee o declara un cálculo, o {@code null}.
     *
     * <p>Para una expresión son las variables que aparecen. Para una operación
     * es su {@code CON=}, incluido el de los generadores: ahí el {@code CON=} no
     * es una dependencia de datos —un {@code SENO} no lee el canal— sino la
     * declaración de *qué magnitud* representa la onda. Sin eso la señal
     * no tendría unidad, y las reglas 17 a 19 no tendrían contra qué
     * verificar la frecuencia.
     */
    private TipoSensor tipoDeLectura(Calculo c, Programa programa) {
        if (c.esExpresion()) {
            for (String v : Expressiones.variables(c.expresion)) {
                Simbolo s = programa.simbolos.buscar(v);
                if (esMedible(s) && s.tipo() != null) return s.tipo();
            }
            return null;
        }
        String canal = c.canal();
        if (canal == null) return null;
        Simbolo s = programa.simbolos.buscar(canal);
        return esMedible(s) ? s.tipo() : null;
    }

    /**
 * ¿La señal la produce un generador de onda?
 *
 * <p>Un {@code SENO}, {@code COSENO} o {@code CUADRADA} construye su valor a
 * partir del reloj, no de un canal. Antes el destino heredaba el tipo del
 * argumento posicional, y ese argumento —el canal que la cabecera nominal
 * nombraba— no lo leía nadie: la señal salía con la unidad de un canal que no
 * había participar en el cálculo. Ahora que el canal es un {@code CON=} y los
 * generadores no lo admiten, la señal se queda sin tipo, que es la respuesta
 * honesta: una onda sintética no tiene magnitud física.
 *
 * <p>Por eso la re/**
 * ¿La señal la produce un generador de onda?
 *
 * <p>Un {@code SENO}, {@code COSENO} o {@code CUADRADA} construye su valor a
 * partir del reloj, no leyendo un canal. Su {@code CON=} no es una dependencia
 * de datos: declara <i>qué magnitud</i> representa la onda, y es lo que permite
 * verificar la frecuencia contra la banda y contra la Nyquist.
 *
 * <p>Las señales de este tipo quedan fuera de la regla 20 porque su tipo
 * depende de que el generador traiga su {@code CON=}, y si no lo trae es la
 * regla 15 la que lo dice, con un mensaje que explica qué escribir. Anunciarlo
 * dos veces por el mismo error solo hace más ruidoso el diagnóstico.
 */
    private boolean esSintetica(Programa programa, String nombre) {
        for (Calculo c : programa.calculos) {
            if (!nombre.equals(c.destino)) continue;
            String op = c.operacion == null ? "" : c.operacion.toLowerCase();
            return op.equals("seno") || op.equals("coseno") || op.equals("cuadrada");
        }
        return false;
    }

    /** Nombres que designan un canal medible (sensor o señal derivada). */
    private boolean esMedible(Simbolo s) {
        return s != null
            && (s.clase() == Simbolo.Clase.SENSOR || s.clase() == Simbolo.Clase.SENAL);
    }

    private Set<String> medibles(Programa programa) {
        Set<String> r = new LinkedHashSet<>();
        for (Simbolo s : programa.simbolos.todos()) {
            if (esMedible(s)) r.add(s.nombre());
        }
        return r;
    }

    // =============================================================
    //  PROGRAMA VACÍO Y RELOJ DEL MUESTREO
    // =============================================================

    /**
     * Si el programa no declara absolutamente nada, todavía no hay programa:
     * el editor está vacío o solo tiene comentarios. No es un defecto del
     * código, así que ninguna regla debe reportar sobre él.
     *
     * <p>Se mira cada una de las siete cosas que puede contener una sentencia
     * —sensores, umbrales, reglas, cálculos, el reloj, {@code estado} y
     * {@code sino}. Basta con que haya una para que el programa se considere
     * escrito: a partir de ahí, lo que falte sí es un defecto real.
     */
    private static boolean esProgramaVacio(Programa programa) {
        return programa.sensoresDeclarados.isEmpty()
            && programa.umbrales.isEmpty()
            && programa.reglas.isEmpty()
            && programa.calculos.isEmpty()
            && programa.muestreo == null
            && programa.estadoInicial == null
            && programa.estadoPorDefecto == null;
    }

    /**
     * Frecuencia de muestreo efectiva del programa, en Hz.
     *
     * <p>Devuelve 0 cuando el reloj no está declarado o no es positivo: es lo
     * que las reglas 18 y 19 usan como Nyquist del programa, y sin reloj no hay
     * contra qué validar una {@code FRECUENCIA=}.
     */
    private double frecuenciaMuestreo(Programa programa) {
        return programa.muestreo == null || programa.muestreo <= 0
            ? 0.0 : 1.0 / programa.muestreo;
    }

    // =============================================================
    //  REGLA 1 — Sensor y umbral con el mismo nombre
    // =============================================================

    /**
     * Colisión de tipos: el mismo identificador declarado con dos clases
     * distintas —por ejemplo, como sensor y como umbral—.
     *
     * <p>La tabla de símbolos conserva la primera declaración, así que la
     * colisión no se ve comparando símbolos: se ve en los reintentos de
     * declaración que quedaron registrados, comparando la clase del símbolo
     * guardado con la clase que se intentó declarar.
     */
    private void regla1Colision(TablaSimbolos simbolos, List<String> errores) {
        for (TablaSimbolos.Duplicado d : simbolos.duplicados()) {
            Simbolo guardado = simbolos.buscar(d.nombre());
            if (guardado == null || guardado.clase() == d.clase()) continue;
            err(errores, d.lineaRepetida(), "'" + d.nombre() + "' esta declarado como "
                + guardado.clase().name().toLowerCase() + " (linea " + d.lineaOriginal()
                + ") y como " + d.clase().name().toLowerCase()
                + " (linea " + d.lineaRepetida() + "): colision de tipos.");
        }
    }

    // =============================================================
    //  REGLA 2 — Declaración duplicada
    // =============================================================

    /** Re-declaración del mismo nombre con la misma clase. */
    private void regla2Duplicadas(TablaSimbolos simbolos, List<String> errores) {
        for (TablaSimbolos.Duplicado d : simbolos.duplicados()) {
            Simbolo guardado = simbolos.buscar(d.nombre());
            if (guardado == null || guardado.clase() != d.clase()) continue;
            err(errores, d.lineaRepetida(), "declaracion duplicada: el "
                + d.clase().name().toLowerCase() + " '" + d.nombre()
                + "' ya fue declarado en la linea " + d.lineaOriginal() + ".");
        }
    }

    // =============================================================
    //  REGLA 3 — Sensor usado como umbral
    // =============================================================

    /**
     * En una condición, la expresión de la derecha es el límite fijo. Un canal
     * medible se mide, no fija límites: si la derecha lo referencia, es un
     * error. La detección ya no depende del nombre sino de la clase del
     * símbolo.
     */
    private void regla3SensorComoUmbral(Programa programa, List<String> errores) {
        for (Regla r : programa.reglas) {
            for (Comparacion cond : comparaciones(r.condicion)) {
                for (String var : Expressiones.variables(cond.derecha)) {
                    // Solo los canales físicos. Una señal derivada sí puede
                    // fijar un límite: `vibracion > referencia * 1.5` compara
                    // contra una forma de onda conocida, y es exactamente el
                    // tipo de comparación que un sistema de monitoreo quiere
                    // hacer. Lo que no tiene sentido es poner un transductor
                    // a la derecha, porque no se mide, se lee.
                    Simbolo s = programa.simbolos.buscar(var);
                    if (s == null || s.clase() != Simbolo.Clase.SENSOR) continue;
                    err(errores, r.linea, "el sensor '" + var
                        + "' no puede usarse como umbral: la expresion derecha de la"
                        + " condicion es un limite fijo y un sensor se lee, no fija"
                        + " limites.");
                }
            }
        }
    }

    // =============================================================
    //  REGLA 4 — Umbral usado como sensor
    // =============================================================

    private void regla4UmbralComoSensor(Programa programa, List<String> errores) {
        TablaSimbolos simbolos = programa.simbolos;
        Set<String> medibles = medibles(programa);
        for (Calculo c : programa.calculos) {
            // El canal de entrada ya no es un argumento sino un CON=, y es el
            // único sitio por el que una operación puede leer. La comprobación
            // de los parametros cubre las dos formas por igual.
            for (Parametro p : c.parametros) {
                if (!"con".equalsIgnoreCase(p.nombre)) continue;
                if (!medibles.contains(p.valor)) {
                    Simbolo s = simbolos.buscar(p.valor);
                    if (s != null && s.clase() == Simbolo.Clase.UMBRAL) {
                        err(errores, c.linea, "el umbral '" + p.valor
                            + "' no puede usarse como sensor en CON=...: es un"
                            + " limite fijo y no hay transductor detras.");
                    }
                }
            }
        }
    }

    // =============================================================
    //  REGLAS 5 y 6 — Rango mínimo/máximo
    // =============================================================

    /**
     * Busca el umbral mínimo y el máximo de un sensor aceptando las variantes
     * {@code S_min}, {@code min_S} y {@code minS}, sin distinguir
     * mayúsculas. Es la convención de nombres del lenguaje.
     */
    private double[] buscarRango(String sensor, Programa programa) {
        String min = buscarUmbral(sensor, "min", programa);
        String max = buscarUmbral(sensor, "max", programa);
        if (min == null || max == null) return null;
        Double vMin = programa.umbrales.get(min);
        Double vMax = programa.umbrales.get(max);
        if (vMin == null || vMax == null) return null;
        return new double[]{vMin, vMax};
    }

    private String buscarUmbral(String sensor, String prefijo, Programa programa) {
        String[] variantes = {
            sensor + "_" + prefijo,          // voltaje_min
            prefijo + "_" + sensor,          // min_voltaje
            prefijo + sensor                 // minvoltaje
        };
        for (String nombre : programa.umbrales.keySet()) {
            for (String v : variantes) {
                if (nombre.equalsIgnoreCase(v)) return nombre;
            }
        }
        return null;
    }

    private void reglas5y6Rango(Programa programa, List<String> errores) {
        for (Sensor s : programa.sensoresDeclarados) {
            double[] rango = buscarRango(s.nombre(), programa);
            if (rango == null) continue;
            double min = rango[0], max = rango[1];

            if (min >= max) {
                err(errores, s.linea(), "rango invalido del sensor '" + s.nombre()
                    + "': el minimo (" + min + ") es mayor o igual que el maximo ("
                    + max + ").");
                continue;
            }
            if (max - min < MARGEN_SEGURIDAD) {
                err(errores, s.linea(), "la diferencia entre el maximo (" + max
                    + ") y el minimo (" + min + ") del sensor '" + s.nombre()
                    + "' es " + (max - min) + ", inferior al margen de seguridad ("
                    + MARGEN_SEGURIDAD + ").");
            }
        }
    }

    // =============================================================
    //  REGLA 7 — Estado incompatible con el rango detectado
    // =============================================================

    private void regla7EstadoIncompatible(Programa programa, List<String> errores) {
        for (Regla r : programa.reglas) {
            Comparacion cond = condicionRelacional(r);
            if (cond == null || r.estado == null) continue;
            String estado = r.estado.toLowerCase();
            String op = cond.operador;

            if (estado.equals("pico") && (op.equals("<") || op.equals("<="))) {
                err(errores, r.linea, "estado PICO asignado a una condicion de"
                    + " caida ('" + op + "'): PICO corresponde a valores por encima"
                    + " del maximo ('>' o '>=').");
            } else if (estado.equals("caida") && (op.equals(">") || op.equals(">="))) {
                err(errores, r.linea, "estado CAIDA asignado a una condicion por"
                    + " encima del maximo ('" + op + "'): CAIDA corresponde a"
                    + " valores por debajo del minimo ('<' o '<=').");
            }
        }
    }

    // =============================================================
    //  REGLA 8 — Reglas de sensores contradictorias
    // =============================================================

    /**
 * Comparación simple de una regla, o {@code null}.
 *
 * <p>Solo la devuelve si la condición es exactamente una comparación. Con
 * conectores o negaciones ya no hay un único intervalo que la describa, y las
 * reglas que razonan sobre intervalos —la 7 y la 8— no tienen nada que decir.
 * No es que se las saltee por comodidad: una condición compuesta no se puede
 * reducir a un intervalo sin.expandirla por De Morgan, que es trabajo de la
 * fase de análisis de estados alcanzables.
 */
    private Comparacion condicionRelacional(Regla r) {
        return r.condicion instanceof Comparacion c && esRelacional(c.operador)
            ? c : null;
    }

    /**
     * Todas las comparaciones hoja de una condición, en orden de aparición.
     *
     * <p>Las reglas que solo necesitan saber qué se lee —la 13, la 14, la 23—
     * sí pueden trabajar con condiciones compuestas, y lo hacen con esta
     * lista en lugar de con la condición entera.
     */
    private List<Comparacion> comparaciones(Condicion c) {
        List<Comparacion> r = new ArrayList<>();
        recogerComparaciones(c, r);
        return r;
    }

    private void recogerComparaciones(Condicion c, List<Comparacion> acc) {
        if (c instanceof Comparacion comp) {
            acc.add(comp);
        } else if (c instanceof Conector con) {
            recogerComparaciones(con.izquierda, acc);
            recogerComparaciones(con.derecha, acc);
        } else if (c instanceof Negado neg) {
            recogerComparaciones(neg.condicion, acc);
        }
    }

    private static boolean esRelacional(String op) {
        return ">".equals(op) || "<".equals(op) || "==".equals(op)
            || ">=".equals(op) || "<=".equals(op) || "!=".equals(op);
    }

    /** Regla reducida a "variable + intervalo de valores que la cumplen". */
    private static class CondicionReducida {
        final Regla regla;
        final String variable;
        final List<Intervalo> intervalos;
        CondicionReducida(Regla regla, String variable, List<Intervalo> intervalos) {
            this.regla = regla;
            this.variable = variable;
            this.intervalos = intervalos;
        }
    }

    private void regla8ReglasContradictorias(Programa programa,
                                             List<String> errores) {
        List<CondicionReducida> condiciones = new ArrayList<>();
        for (Regla r : programa.reglas) {
            Comparacion cond = condicionRelacional(r);
            if (cond == null || r.estado == null) continue;
            if (!(cond.izquierda instanceof Variable var)) continue;
            Double cte = evaluar(cond.derecha, programa.umbrales);
            if (cte == null) continue;

            List<Intervalo> ivs = intervalo(cond.operador, cte);
            if (ivs.isEmpty()) continue;
            condiciones.add(new CondicionReducida(r, var.nombre, ivs));
        }

        for (int i = 0; i < condiciones.size(); i++) {
            for (int j = i + 1; j < condiciones.size(); j++) {
                CondicionReducida a = condiciones.get(i);
                CondicionReducida b = condiciones.get(j);
                if (!a.variable.equals(b.variable)) continue;
                if (a.regla.estado.equalsIgnoreCase(b.regla.estado)) continue;
                if (!solapan(a.intervalos, b.intervalos)) continue;

                err(errores, b.regla.linea, "regla contradictoria sobre '"
                    + a.variable + "': se solapa con la regla de la linea "
                    + a.regla.linea + " (ambas se cumplen para el mismo valor)"
                    + " pero asignan estados distintos ('" + a.regla.estado
                    + "' y '" + b.regla.estado + "').");
            }
        }
    }

    // =============================================================
    //  REGLA 10 — Parámetros de simulación físicamente inválidos
    // =============================================================

    private void regla10ParametrosInvalidos(Programa programa,
                                            List<String> errores) {
        for (Calculo c : programa.calculos) {
            for (Parametro p : c.parametros) {
                String nombre = p.nombre == null ? "" : p.nombre.toLowerCase();
                Double valor = numero(p.valor);
                if (valor == null) continue;

                switch (nombre) {
                    case "frecuencia" -> {
                        if (valor <= 0) {
                            err(errores, c.linea, "frecuencia invalida (" + valor
                                + "): debe ser positiva y no cero.");
                        }
                    }
                    case "ventana" -> {
                        if (valor <= 0) {
                            err(errores, c.linea, "ventana de promedio invalida ("
                                + valor + "): debe ser positiva.");
                        }
                    }
                    case "amplitud" -> {
                        // La amplitud se mide contra el rango del sensor que
                        // alimenta el generador, si lo hay.
                        String canal = c.canal();
                        double[] rango = canal == null
                            ? null : buscarRango(canal, programa);
                        if (rango != null && valor > (rango[1] - rango[0])) {
                            err(errores, c.linea, "la amplitud (" + valor
                                + ") excede el rango del sensor '" + canal
                                + "' [" + rango[0] + ", " + rango[1] + "] (rango = "
                                + (rango[1] - rango[0]) + ").");
                        }
                    }
                    default -> { }
                }
            }
        }
    }

    // =============================================================
    //  REGLA 11 — Combinación de magnitudes incompatibles en SUMA
    // =============================================================

    private void regla11Magnitudes(Programa programa, List<String> errores) {
        Set<String> medibles = medibles(programa);
        for (Calculo c : programa.calculos) {
            if (!"suma".equalsIgnoreCase(c.operacion)) continue;
            Map<TipoSensor, String> vistas = new LinkedHashMap<>();
            for (Parametro p : c.parametros) {
                if (p.nombre == null || !p.nombre.equalsIgnoreCase("con")) continue;
                if (!medibles.contains(p.valor)) continue;
                TipoSensor m = programa.tipoDe(p.valor);
                if (m == null) continue;
                vistas.putIfAbsent(m, p.valor);
            }
            reportarMagnitudes(errores, c.linea, "SUMA", vistas);
        }
    }

    // =============================================================
    //  REGLA 9 — Magnitudes mezcladas dentro de una expresión
    // =============================================================

    /**
     * Una expresión de {@code calcular} tiene que medir en una sola magnitud.
     *
     * <p>Es la regla 11 aplicada a las expresiones. {@code SUMA} ya tenía su
     * comprobación, y la regla 14 cubre cada lado de cada comparación, pero
     * {@code calcular(v + t, mezcla)} se quedaba en silencio: el destino
     * heredaba el tipo de la primera variable con tipo —volts— y la suma sin
     * unidad pasaba por buena.
     *
     * <p>No es un caso raro. Sumar un voltaje con una temperatura es
     * exactamente el error que alguien escribe un día creyendo que las dos
     * señales comparten escala, y sin un tipo declarado que lo impida solo lo
     * detecta quien recuerda la física.
     *
     * <p>Las constantes y los umbrales quedan fuera: no tienen magnitud propia,
     * la toman del otro lado. Solo se miran los sensores y las señales derivadas.
     */
    private void regla9MezclaEnExpresion(Programa programa, List<String> errores) {
        Set<String> medibles = medibles(programa);
        for (Calculo c : programa.calculos) {
            if (!c.esExpresion()) continue;
            Map<TipoSensor, String> vistas = magnitudesDe(c.expresion, medibles, programa);
            reportarMagnitudes(errores, c.linea, "la expresion", vistas);
        }
    }

    // =============================================================
    //  REGLA 13 — Variable o identificador no declarado
    // =============================================================

    private void regla13NoDeclarados(Programa programa, List<String> errores) {
        TablaSimbolos simbolos = programa.simbolos;

        for (Regla r : programa.reglas) {
            Set<String> yaVistos = new LinkedHashSet<>();
            for (String var : r.condicion.variables()) {
                if (simbolos.declarado(var) || !yaVistos.add(var)) continue;
                err(errores, r.linea, "identificador '" + var
                    + "' no declarado: se usa en la condicion sin haber sido"
                    + " definido con 'sensor', 'umbral' o 'calcular'.");
            }
        }

        for (Calculo c : programa.calculos) {
            if (!simbolos.declarado(c.destino)) {
                err(errores, c.linea, "identificador '" + c.destino
                    + "' no declarado: es la señal de destino de calcular().");
            }
            for (Parametro p : c.parametros) {
                if (p.nombre == null || !p.nombre.equalsIgnoreCase("con")) continue;
                if (simbolos.declarado(p.valor)) continue;
                err(errores, c.linea, "identificador '" + p.valor
                    + "' no declarado: se usa en CON= sin haber sido definido"
                    + " con 'sensor'.");
            }
        }
    }

    // =============================================================
    //  REGLA 14 — Incompatibilidad de tipos
    // =============================================================

    /**
     * En Timotomata la "magnitud física" es el tipo declarado del canal:
     * operar entre un canal ELÉCTRICO y uno TÉRMICO es una incompatibilidad de
     * tipos.
     *
     * <p>La comprobación es <b>por lado de cada comparación</b>, no sobre la
     * condición entera. `voltaje > 220` mezcla cero magnitudes, y
     * `amp + temp > 10` mezcla dos. Pero `voltaje > max y temp > tempMax`
     * también junta dos magnitudes en la misma línea, y no hay nada malo en
     * ello: una conjuncion de dos preguntas sobre sensores distintos es
     * justamente lo que un sistema de monitoreo hace todo el tiempo.
     *
     * <p>Antes de los conectores esto no se confundía porque una condición era
     * una sola comparación. Con `y` hay que separar "mezclar magnitudes" de
 * "mencionar dos sensores", que es lo que hace esta regla.
     */
    private void regla14TiposIncompatibles(Programa programa,
                                           List<String> errores) {
        Set<String> medibles = medibles(programa);

        for (Regla r : programa.reglas) {
            for (Comparacion c : comparaciones(r.condicion)) {
                Map<TipoSensor, String> izquierda = magnitudesDe(c.izquierda, medibles, programa);
                Map<TipoSensor, String> derecha = magnitudesDe(c.derecha, medibles, programa);

                if (!reportarMagnitudes(errores, r.linea,
                        "incompatibilidad de tipos: la operacion", izquierda)) {
                    reportarMagnitudes(errores, r.linea,
                        "incompatibilidad de tipos: la operacion", derecha);
                }
            }
        }
    }

    /** Magnitudes distintas que aparecen dentro de una sola expresión. */
    private Map<TipoSensor, String> magnitudesDe(Expresion e, Set<String> medibles,
                                                 Programa programa) {
        Map<TipoSensor, String> vistas = new LinkedHashMap<>();
        for (String var : Expressiones.variables(e)) {
            if (!medibles.contains(var)) continue;
            TipoSensor m = programa.tipoDe(var);
            if (m == null) continue;
            vistas.putIfAbsent(m, var);
        }
        return vistas;
    }

    // =============================================================
    //  REGLA 15 — Llamadas a calcular() con argumentos incorrectos
    // =============================================================

    /**
     * Cantidad o tipo de parámetros que no coinciden con la firma de la
     * operación.
     *
     * <p>Desde que el canal de entrada es un {@code CON=} y no un argumento
     * posicional, las firmas son:
     *
     * <ul>
     *   <li>{@code SENO/COSENO/CUADRADA} → CON + AMPLITUD + FRECUENCIA. El
     *       {@code CON=} no se lee —un generador produce su valor a partir del
     *       reloj— pero declara qué magnitud representa la onda, y sin él no
     *       hay unidad contra la que verificar la frecuencia.</li>
     *   <li>{@code PROMEDIO} → CON + VENTANA.</li>
     *   <li>{@code MAXIMO} → CON + VENTANA.</li>
     *   <li>{@code SUMA} → 2 o más CON.</li>
     * </ul>
     */
    private void regla15LlamadasIncorrectas(Programa programa,
                                            List<String> errores) {
        for (Calculo c : programa.calculos) {
            String op = c.operacion == null ? "" : c.operacion.toLowerCase();
            Map<String, Integer> conteo = new LinkedHashMap<>();
            for (Parametro p : c.parametros) {
                String n = p.nombre == null ? "" : p.nombre.toLowerCase();
                conteo.merge(n, 1, Integer::sum);
            }
            int nAmpl = conteo.getOrDefault("amplitud", 0);
            int nFrec = conteo.getOrDefault("frecuencia", 0);
            int nVent = conteo.getOrDefault("ventana", 0);
            int nCon  = conteo.getOrDefault("con", 0);
            boolean soloCon = conteo.keySet().stream().allMatch(k -> k.equals("con"));
            String nombre = op.toUpperCase();

            if (op.equals("seno") || op.equals("coseno") || op.equals("cuadrada")) {
                // El CON= de un generador no es una lectura: declara que
                // magnitud representa la onda. Es lo que permite verificar la
                // frecuencia contra la banda del transductor y contra la
                // Nyquist del programa; sin el, la onda se queda sin unidad.
                if (nAmpl != 1 || nFrec != 1 || nCon > 1
                    || c.parametros.size() != 2 + nCon
                    || (!soloCon && c.parametros.size() != 3)) {
                    err(errores, c.linea, "llamada con argumentos incorrectos: "
                        + nombre + " exige CON, AMPLITUD y FRECUENCIA. El CON= no se"
                        + " lee —un generador produce su valor a partir del reloj—"
                        + " pero declara que magnitud representa la onda, y sin el no"
                        + " hay unidad contra la que verificar la frecuencia.");
                }
            } else if (op.equals("promedio")) {
                if (c.parametros.size() != 2 || nCon != 1 || nVent != 1) {
                    err(errores, c.linea, "llamada con argumentos incorrectos: "
                        + "PROMEDIO exige CON y VENTANA (2 parametros), como en "
                        + "PROMEDIO(CON=voltaje, VENTANA=20).");
                }
            } else if (op.equals("maximo")) {
                if (c.parametros.size() != 2 || nCon != 1 || nVent != 1) {
                    err(errores, c.linea, "llamada con argumentos incorrectos: "
                        + "MAXIMO exige CON y VENTANA (2 parametros). El maximo va "
                        + "sobre una ventana de muestras, como PROMEDIO: sin "
                        + "ventana no se sabria sobre que intervalo se toma.");
                }
            } else if (op.equals("suma")) {
                if (!soloCon || nCon < 2) {
                    err(errores, c.linea, "llamada con argumentos incorrectos: "
                        + "SUMA exige 2 o mas CON (ej. SUMA(CON=a, CON=b)).");
                }
            }
        }
    }

    // =============================================================
    //  REGLA 16 — División entre cero constante
    // =============================================================

    private void regla16DivisionPorCero(Programa programa, List<String> errores) {
        for (Regla r : programa.reglas) {
            for (Comparacion c : comparaciones(r.condicion)) {
                buscarDivisionPorCero(c.izquierda, r.linea, programa.umbrales, errores);
                buscarDivisionPorCero(c.derecha, r.linea, programa.umbrales, errores);
            }
        }
    }

    private void buscarDivisionPorCero(Expresion e, int linea,
                                       Map<String, Double> umbrales,
                                       List<String> errores) {
        if (e instanceof Binaria b) {
            if ("/".equals(b.operador)) {
                Double den = evaluar(b.derecha, umbrales);
                if (den != null && den == 0.0) {
                    err(errores, linea, "division entre cero constante: el"
                        + " denominador (" + b.derecha + ") vale 0.");
                }
            }
            buscarDivisionPorCero(b.izquierda, linea, umbrales, errores);
            buscarDivisionPorCero(b.derecha, linea, umbrales, errores);
        } else if (e instanceof Negacion n) {
            buscarDivisionPorCero(n.expresion, linea, umbrales, errores);
        } else if (e instanceof Abs a) {
            buscarDivisionPorCero(a.expresion, linea, umbrales, errores);
        }
    }

    // =============================================================
    //  REGLAS 17 a 19 — Física contra la ficha del tipo declarado
    // =============================================================

    /**
     * Verificaciones que solo son posibles porque el tipo del sensor se
     * declara en el lenguaje. Antes no existía información de unidad, rango ni
     * banda, así que ninguna de ellas podía escribirse.
     */
    private void reglas17a19Fisica(Programa programa, List<String> errores) {
        Set<String> medibles = medibles(programa);
        double hzPrograma = frecuenciaMuestreo(programa);

        // REGLA 17 — cada umbral se valida contra el tipo que él mismo
        // declara, no contra el nombre de la variable.
        for (Simbolo s : programa.simbolos.todos()) {
            if (s.clase() != Simbolo.Clase.UMBRAL) continue;
            TipoSensor t = s.tipo();
            if (t == null || s.valor() == null) continue;
            if (t.enRango(s.valor())) continue;
            err(errores, s.linea(), "el umbral '" + s.nombre() + "' (" + s.valor()
                + ") esta fuera del rango representable por un sensor "
                + t.etiqueta() + " [" + t.rangoMin() + ", " + t.rangoMax()
                + " " + t.unidad() + "].");
        }

        for (Calculo c : programa.calculos) {
            // Reglas 18 y 19 verifican la fisica contra el tipo del canal que
            // alimenta la operacion. Sin canal (un generador) no hay nada que
            // contrastar.
            TipoSensor t = tipoDeLectura(c, programa);
            if (t == null) continue;

            for (Parametro p : c.parametros) {
                String nombre = p.nombre == null ? "" : p.nombre.toLowerCase();
                Double valor = numero(p.valor);
                // Los valores no positivos ya los reporta la regla 10.
                if (valor == null || valor <= 0) continue;

                // REGLA 18 — la frecuencia debe ser observable. El límite real
                // es el menor entre la banda del transductor y la mitad de la
                // frecuencia de muestreo del programa (criterio de Nyquist).
                if (nombre.equals("frecuencia")) {
                    double limiteTransductor = t.bandaMaxima();
                    double limiteNyquist = hzPrograma / 2.0;
                    double limite = Math.min(limiteTransductor, limiteNyquist);
                    if (valor <= limite) continue;
                    String causa = limiteNyquist < limiteTransductor
                        ? "el programa muestrea cada " + programa.muestreo
                            + " s (" + formatoHz(hzPrograma) + " Hz), asi que por"
                            + " Nyquist no puede observar mas de "
                            + formatoHz(limiteNyquist) + " Hz"
                        : "el transductor solo sigue hasta "
                            + formatoHz(limiteTransductor) + " Hz";
                    err(errores, c.linea, "frecuencia (" + formatoHz(valor)
                        + " Hz) no observable en un sensor " + t.etiqueta() + ": "
                        + causa + ".");
                }

                // REGLA 19 — la ventana debe cubrir tiempo suficiente para que
                // el sensor responda.
                if (nombre.equals("ventana") && valor < t.ventanaMinima()) {
                    double segundos = valor * t.periodoMuestreo();
                    err(errores, c.linea, "ventana (" + entero(valor)
                        + ") inferior al minimo fisico para " + t.etiqueta() + " ("
                        + t.ventanaMinima() + " muestras): con un transductor de "
                        + formatoHz(t.frecuenciaMuestreo()) + " Hz, " + entero(valor)
                        + " muestras cubren " + formatoSegundos(segundos)
                        + " y el sensor no puede responder tan rapido.");
                }
            }
        }
    }

    private static String formatoHz(double hz) {
        if (hz == Math.floor(hz) && !Double.isInfinite(hz)) return String.valueOf((long) hz);
        return String.format("%.3f", hz).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static String formatoSegundos(double s) {
        if (s < 1.0) return String.format("%.2f s", s);
        if (s < 60.0) return String.format("%.1f s", s);
        return String.format("%.1f min", s / 60.0);
    }

    /** Numero sin decimales cuando es entero. */
    private static String entero(double v) {
        return (v == Math.floor(v) && !Double.isInfinite(v))
            ? String.valueOf((long) v) : String.valueOf(v);
    }

    // =============================================================
    //  REGLA 20 — Tipo ausente
    // =============================================================

    /**
     * Un canal medible usado en una condición o en `CON=` debe tener tipo
     * declarado. Antes esto no era un error porque el tipo se deducía del
     * nombre y, si no se deducía, la verificación se saltaba en silencio.
     */
    private void regla20TiposAusentes(Programa programa, List<String> errores) {
        Set<String> sinTipo = new LinkedHashSet<>();
        for (Simbolo s : programa.simbolos.todos()) {
            if (esMedible(s) && s.tipo() == null && !esSintetica(programa, s.nombre())) {
                sinTipo.add(s.nombre());
            }
        }
        if (sinTipo.isEmpty()) return;

        for (String nombre : sinTipo) {
            Simbolo s = programa.simbolos.buscar(nombre);
            err(errores, s.linea(), "el sensor '" + nombre + "' no tiene tipo"
                + " declarado: no se pueden verificar unidades, rango ni"
                + " frecuencia sin el.");
        }
    }

    // =============================================================
    //  REGLAS 21 a 23 — Flujo de datos
    // =============================================================

    /**
     * REGLA 21 — Orden de definición.
     *
     * <p>Los cálculos se ejecutan una vez por tick, en el orden en que están
     * escritos. Por eso una señal derivada todavía no tiene valor en las
     * líneas anteriores a su {@code calcular}: leerla ahí no da "el valor del
     * tick anterior", da nada.
     *
     * <p>Esto no es una convención de estilo: es una restricción del modelo de
     * ejecución, y el generador de código la necesita para saber que puede
     * asignar cada señal una sola vez por tick, en un solo lugar.
     *
     * <p>El uso dentro de la propia línea de definición no se reporta: la
     * autodependencia tiene su propia regla y produce un mensaje más útil.
     */
    private void regla21OrdenDefinicion(Programa programa, List<String> errores) {
        for (Map.Entry<String, Integer> uso : usosDeSenales(programa).entrySet()) {
            Simbolo s = programa.simbolos.buscar(uso.getKey());
            if (s == null || s.clase() != Simbolo.Clase.SENAL) continue;
            if (uso.getValue() < s.linea()) {
                err(errores, uso.getValue(), "se usa '" + uso.getKey()
                    + "' antes de calcularlo: la senal se define en la linea "
                    + s.linea() + ", y hasta entonces no tiene valor. Los calculos"
                    + " se ejecutan en el orden en que estan escritos.");
            }
        }
    }

    /**
     * REGLA 22 — Autodependencia.
     *
     * <p>{@code calcular(x, op, x)} leería el valor de {@code x} para escribir
     * en {@code x}. El resultado sería indefinido o silenciosamente igual al
     * anterior; en ningún caso es lo que el programador quiso escribir. Lo más
     * probable es que se haya olvidado el destino.
     */
    private void regla22Autodependencia(Programa programa, List<String> errores) {
        for (Calculo c : programa.calculos) {
            if (c.origenes().contains(c.destino)) {
                err(errores, c.linea, "autodependencia: '" + c.destino
                    + "' se calcula a partir de si mismo. El resultado se leeria"
                    + " de donde todavia no se ha escrito.");
            }
        }
    }

    /**
     * REGLA 23 — Magnitudes incompatibles con un umbral.
     *
     * <p>La regla 14 solo mira los canales y señales medibles, porque comparar
     * un umbral contra un umbral no tiene sentido físico. Pero un umbral sí
     * lleva tipo declarado, y comparar un canal contra el umbral de otra
     * magnitud sí lo tiene:
     *
     * <pre>
     *   si voltaje_prom &gt; temp_max entonces estado = PICO;   // volts contra grados
     * </pre>
     *
     * <p>Salta porque el umbral no entra en la colección de medibles, no porque
     * la comparación sea válida. Los volts nunca superan los 125 grados del
     * rango térmico, así que la regla nunca se dispara y el estado se queda
     * siempre en su valor por defecto: un fallo silencioso.
     */
    private void regla23UmbralDeOtraMagnitud(Programa programa,
                                             List<String> errores) {
        for (Regla r : programa.reglas) {
            // Por comparación, no sobre la condición entera: si no, un
            // `voltaje > vmax y temperatura > tmax` cruzaría 'voltaje' con
            // 'tmax' aunque estén en preguntas distintas.
            for (Comparacion c : comparaciones(r.condicion)) {
                Map<TipoSensor, String> umbrales = new LinkedHashMap<>();
                Map<TipoSensor, String> canales = new LinkedHashMap<>();

                for (String var : Expressiones.variables(c.izquierda)) {
                    agregarPorClase(programa, var, umbrales, canales);
                }
                for (String var : Expressiones.variables(c.derecha)) {
                    agregarPorClase(programa, var, umbrales, canales);
                }

                // Solo se reporta el cruce umbral/canal. Los clashes entre dos
                // canales los cubre la regla 14, y repetirlos sería ruido.
                boolean reportado = false;
                for (Map.Entry<TipoSensor, String> ca : canales.entrySet()) {
                    for (Map.Entry<TipoSensor, String> u : umbrales.entrySet()) {
                        if (ca.getKey().equals(u.getKey())) continue;
                        err(errores, r.linea, "incompatibilidad de tipos: se compara '"
                            + ca.getValue() + "' (" + ca.getKey().etiqueta() + ") contra el"
                            + " umbral '" + u.getValue() + "' (" + u.getKey().etiqueta()
                            + "), que es de otra magnitud. El umbral nunca se cruza con"
                            + " este sensor, asi que la condicion nunca se cumple.");
                        reportado = true;
                    }
                }
                if (reportado) return;
            }
        }
    }

    /** Clasifica un nombre como umbral o como canal, si tiene tipo declarado. */
    private void agregarPorClase(Programa programa, String var,
                                  Map<TipoSensor, String> umbrales,
                                  Map<TipoSensor, String> canales) {
        Simbolo s = programa.simbolos.buscar(var);
        TipoSensor m = programa.tipoDe(var);
        if (s == null || m == null) return;
        if (s.clase() == Simbolo.Clase.UMBRAL) umbrales.putIfAbsent(m, var);
        else canales.putIfAbsent(m, var);
    }

    /**
     * Primer uso de cada nombre, con su línea.
     *
     * <p>Cubre las tres vías por las que un nombre puede leerse: la condición
     * de una regla, la expresión de un {@code calcular} y sus parámetros
     * {@code CON=}. Guarda la línea mínima, que es la que decide si el uso
     * antecede a la definición.
     */
    private Map<String, Integer> usosDeSenales(Programa programa) {
        Map<String, Integer> usos = new LinkedHashMap<>();

        for (Regla r : programa.reglas) {
            for (String v : r.condicion.variables()) {
                usos.merge(v, r.linea, Math::min);
            }
        }
        for (Calculo c : programa.calculos) {
            for (String canal : c.canales()) {
                usos.merge(canal, c.linea, Math::min);
            }
            if (c.esExpresion()) {
                for (String v : Expressiones.variables(c.expresion)) {
                    usos.merge(v, c.linea, Math::min);
                }
            }
            for (Parametro p : c.parametros) {
                if (p.nombre != null && p.nombre.equalsIgnoreCase("con")) {
                    usos.merge(p.valor, c.linea, Math::min);
                }
            }
        }
        return usos;
    }

/**
     * Señala un conjunto de magnitudes incompatibles dentro de una sola
     * operación, y devuelve si lo hubo.
     *
     * <p>Habla de "señales" y no de "sensores" porque las dos familias cuentan:
     * una expresión puede mezclar un transductor con una señal derivada, y el
     * error es el mismo —sumar volts y grados no produce volts.
     */
    private boolean reportarMagnitudes(List<String> errores, int linea,
                                       String contexto,
                                       Map<TipoSensor, String> vistas) {
        if (vistas.size() < 2) return false;
        Iterator<Map.Entry<TipoSensor, String>> it = vistas.entrySet().iterator();
        Map.Entry<TipoSensor, String> a = it.next();
        Map.Entry<TipoSensor, String> b = it.next();
        err(errores, linea, contexto + " combina senales de magnitudes"
            + " incompatibles: '" + a.getValue() + "' (" + a.getKey().etiqueta()
            + ") y '" + b.getValue() + "' (" + b.getKey().etiqueta() + ").");
        return true;
    }

    // =============================================================
    //  EXPRESIONES: evaluación e intervalos
    // =============================================================

    private Double evaluar(Expresion e, Map<String, Double> umbrales) {
        if (e == null) return null;
        if (e instanceof Numero n) return n.valor;
        if (e instanceof Variable v) return umbrales.get(v.nombre);
        if (e instanceof Negacion n) {
            Double x = evaluar(n.expresion, umbrales);
            return x == null ? null : -x;
        }
        if (e instanceof Abs a) {
            Double x = evaluar(a.expresion, umbrales);
            return x == null ? null : Math.abs(x);
        }
        if (e instanceof Binaria b) {
            if (esRelacional(b.operador)) return null;
            Double izq = evaluar(b.izquierda, umbrales);
            Double der = evaluar(b.derecha, umbrales);
            if (izq == null || der == null) return null;
            return switch (b.operador) {
                case "+" -> izq + der;
                case "-" -> izq - der;
                case "*" -> izq * der;
                case "/" -> der == 0 ? null : izq / der;
                default -> null;
            };
        }
        return null;
    }

    /**
     * Traduce una comparación contra una constante al conjunto de valores que
     * la cumplen.
     *
     * <p>Delegado en {@link Intervalos}; queda aquí porque las reglas lo usan
     * con demasiada frecuencia como para calificar el nombre cada vez.
     */
    private List<Intervalo> intervalo(String op, double c) {
        return Intervalos.deOperador(op, c);
    }

    private boolean solapan(List<Intervalo> a, List<Intervalo> b) {
        return Intervalos.solapan(a, b);
    }

    private static Double numero(String lexema) {
        if (lexema == null) return null;
        try {
            return Double.parseDouble(lexema);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}