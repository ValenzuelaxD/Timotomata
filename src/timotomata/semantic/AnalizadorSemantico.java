package timotomata.semantic;

import java.util.*;

import timotomata.lexer.Token;
import timotomata.parser.ast.*;
import timotomata.simbolos.Simbolo;
import timotomata.simbolos.TablaSimbolos;

/**
 * ANÁLISIS SEMÁNTICO DEL LENGUAJE TIMOTOMATA
 * ==========================================
 *
 * Se ejecuta después del parser, sobre el AST y sobre la tabla de símbolos
 * que este construyó. Ya no re-escanea la lista de tokens para deducir las
 * declaraciones: la tabla es la única fuente de verdad.
 *
 * <h2>Reglas del documento "Errores semánticos propios"</h2>
 * <ol>
 *   <li>1. Sensor y umbral con el mismo nombre (colisión de tipos)</li>
 *   <li>2. Declaración duplicada de sensor, umbral o señal</li>
 *   <li>3. Sensor usado como umbral (límite fijo de una condición)</li>
 *   <li>4. Umbral usado como sensor (objetivo de calcular() o de CON=...)</li>
 *   <li>5. Rango mínimo/máximo inválido (min &gt;= max)</li>
 *   <li>6. Diferencia entre máximo y mínimo insuficiente</li>
 *   <li>7. Estado incompatible con el rango (PICO en caída / CAIDA en pico)</li>
 *   <li>8. Reglas contradictorias (se solapan y asignan estados distintos)</li>
 *   <li>10. Parámetros físicamente inválidos (frecuencia, ventana, amplitud)</li>
 *   <li>11. Combinación de magnitudes incompatibles en SUMA(CON=...)</li>
 * </ol>
 *
 * <h2>Reglas genéricas</h2>
 * <ol start="13">
 *   <li>13. Variable o identificador no declarado</li>
 *   <li>14. Incompatibilidad de tipos entre sensores de magnitud distinta</li>
 *   <li>15. Llamada a calcular() con argumentos incorrectos</li>
 *   <li>16. División entre cero constante</li>
 * </ol>
 *
 * <h2>Reglas nuevas: tipos de sensor declarados</h2>
 * <ol start="17">
 *   <li>17. Umbral fuera del rango representable por el transductor</li>
 *   <li>18. {@code FRECUENCIA=} por encima de la banda del sensor</li>
 *   <li>19. {@code VENTANA=} por debajo del mínimo físico del tipo</li>
 *   <li>20. Tipo de sensor ausente en una referencia</li>
 * </ol>
 *
 * <p>Las reglas 18 y 19 existían solo de forma parcial contra una constante
 * global ({@code FRECUENCIA_MUESTREO = 1.0}) que no pertenecía al lenguaje.
 * Ahora se comparan contra la ficha técnica del tipo declarado, que sí.
 *
 * <p>Las reglas 9 ("operación sin historial") y 12 ("programa terminado de
 * forma inválida") quedan omitidas: la 9 por pedido, y la 12 porque el
 * lenguaje ya no tiene sentencia FIN.
 */
public class AnalizadorSemantico {

    /** Margen mínimo exigido entre el máximo y el mínimo de un sensor. */
    public static final double MARGEN_SEGURIDAD = 10.0;

    // =============================================================
    //  PUNTO DE ENTRADA
    // =============================================================

    public List<String> analizar(Programa programa, List<Token> tokens) {
        List<String> errores = new ArrayList<>();
        if (programa == null) return errores;

        TablaSimbolos simbolos = programa.simbolos;

        resolverTiposDeSenales(programa);

        reglaMuestreo(programa, errores);
        regla1Colision(simbolos, errores);
        regla2Duplicadas(simbolos, errores);
        regla3SensorComoUmbral(programa, errores);
        regla4UmbralComoSensor(programa, errores);
        reglas5y6Rango(programa, errores);
        regla7EstadoIncompatible(programa, errores);
        regla8ReglasContradictorias(programa, errores);
        regla10ParametrosInvalidos(programa, errores);
        regla11Magnitudes(programa, errores);
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
            if (!c.deriva()) continue;
            Simbolo origen = programa.simbolos.buscar(c.sensor);
            if (!esMedible(origen)) continue;
            Simbolo destino = programa.simbolos.buscar(c.destino);
            if (destino != null && destino.tipo() == null) {
                destino.asignarTipo(origen.tipo());
            }
        }
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
    //  MUESTREO — el reloj del programa
    // =============================================================

    /**
     * {@code muestreo = 0.01;} declara la duración de un tick, en segundos.
     *
     * <p>Sin ella el programa no es ejecutable: el kernel de muestreo es un
     * bucle sobre el tiempo, y {@code FRECUENCIA=} no tiene contra qué
     * validarse. Antes el paso de muestreo era una constante global del
     * analizador ({@code FRECUENCIA_MUESTREO = 1.0}) que no pertenecía al
     * lenguaje.
     */
    private void reglaMuestreo(Programa programa, List<String> errores) {
        if (programa.muestreo == null) {
            int linea = programa.sensoresDeclarados.isEmpty()
                ? 1 : programa.sensoresDeclarados.get(0).linea();
            err(errores, linea, "falta la declaracion 'muestreo = <segundos>;':"
                + " define la duracion de un tick y es necesaria para validar"
                + " la frecuencia de muestreo y las ventanas de promediado.");
            return;
        }
        if (programa.muestreo <= 0) {
            err(errores, programa.lineaMuestreo, "muestreo invalido ("
                + programa.muestreo + "): la duracion de un tick debe ser"
                + " positiva.");
        }
    }

    /** Frecuencia de muestreo efectiva del programa, en Hz. */
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
        Set<String> medibles = medibles(programa);
        for (Regla r : programa.reglas) {
            Binaria cond = condicionRelacional(r);
            if (cond == null) continue;
            for (String var : variables(cond.derecha)) {
                if (!medibles.contains(var)) continue;
                err(errores, r.linea, "el sensor '" + var
                    + "' no puede usarse como umbral: la expresion derecha de la"
                    + " condicion es un limite fijo y un sensor se mide, no fija"
                    + " limites.");
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
            if (!medibles.contains(c.sensor)) {
                Simbolo s = simbolos.buscar(c.sensor);
                if (s != null && s.clase() == Simbolo.Clase.UMBRAL) {
                    err(errores, c.linea, "el umbral '" + c.sensor
                        + "' no puede usarse como sensor: es el objetivo de calcular().");
                }
            }
            for (Parametro p : c.parametros) {
                if (!"con".equalsIgnoreCase(p.nombre)) continue;
                if (!medibles.contains(p.valor)) {
                    Simbolo s = simbolos.buscar(p.valor);
                    if (s != null && s.clase() == Simbolo.Clase.UMBRAL) {
                        err(errores, c.linea, "el umbral '" + p.valor
                            + "' no puede usarse como sensor en CON=... (suma de"
                            + " sensores).");
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
            Binaria cond = condicionRelacional(r);
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

    private Binaria condicionRelacional(Regla r) {
        if (r.condicion instanceof Binaria b && esRelacional(b.operador)) return b;
        return null;
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
            Binaria cond = condicionRelacional(r);
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
                        double[] rango = buscarRango(c.sensor, programa);
                        if (rango != null && valor > (rango[1] - rango[0])) {
                            err(errores, c.linea, "la amplitud (" + valor
                                + ") excede el rango del sensor '" + c.sensor
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
    //  REGLA 13 — Variable o identificador no declarado
    // =============================================================

    private void regla13NoDeclarados(Programa programa, List<String> errores) {
        TablaSimbolos simbolos = programa.simbolos;

        for (Regla r : programa.reglas) {
            Set<String> yaVistos = new LinkedHashSet<>();
            for (String var : variables(r.condicion)) {
                if (simbolos.declarado(var) || !yaVistos.add(var)) continue;
                err(errores, r.linea, "identificador '" + var
                    + "' no declarado: se usa en la condicion sin haber sido"
                    + " definido con 'sensor', 'umbral' o 'calcular'.");
            }
        }

        for (Calculo c : programa.calculos) {
            if (c.sensor != null && !c.sensor.isEmpty()
                    && !simbolos.declarado(c.sensor)) {
                err(errores, c.linea, "identificador '" + c.sensor
                    + "' no declarado: es el sensor origen de calcular().");
            }
            if (c.deriva() && !simbolos.declarado(c.destino)) {
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
     * operar o comparar un canal ELÉCTRICO con uno TÉRMICO es una
     * incompatibilidad de tipos. Abarca la aritmética y la comparación, sea
     * cual sea la posición.
     */
    private void regla14TiposIncompatibles(Programa programa,
                                           List<String> errores) {
        Set<String> medibles = medibles(programa);
        for (Regla r : programa.reglas) {
            Map<TipoSensor, String> vistas = new LinkedHashMap<>();
            for (String var : variables(r.condicion)) {
                if (!medibles.contains(var)) continue;
                TipoSensor m = programa.tipoDe(var);
                if (m == null) continue;
                vistas.putIfAbsent(m, var);
            }
            reportarMagnitudes(errores, r.linea,
                               "incompatibilidad de tipos: la operacion", vistas);
        }
    }

    // =============================================================
    //  REGLA 15 — Llamadas a calcular() con argumentos incorrectos
    // =============================================================

    /**
     * Cantidad o tipo de parámetros que no coinciden con la firma de la
     * operación.
     *
     * <p>Firmas: SENO/COSENO/CUADRADA → AMPLITUD + FRECUENCIA (2).
     * PROMEDIO → VENTANA (1). MAXIMO → VENTANA (1).
     * SUMA → 2 o más CON.
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
                if (c.parametros.size() != 2 || nAmpl != 1 || nFrec != 1) {
                    err(errores, c.linea, "llamada con argumentos incorrectos: "
                        + nombre + " exige AMPLITUD y FRECUENCIA (2 parametros).");
                }
            } else if (op.equals("promedio")) {
                if (c.parametros.size() != 1 || nVent != 1) {
                    err(errores, c.linea, "llamada con argumentos incorrectos: "
                        + "PROMEDIO exige VENTANA (1 parametro).");
                }
            } else if (op.equals("maximo")) {
                if (c.parametros.size() != 1 || nVent != 1) {
                    err(errores, c.linea, "llamada con argumentos incorrectos: "
                        + "MAXIMO exige VENTANA (1 parametro): el maximo va sobre "
                        + "una ventana de muestras, como PROMEDIO. Sin ventana "
                        + "no se sabria sobre que intervalo se toma.");
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
            buscarDivisionPorCero(r.condicion, r.linea, programa.umbrales, errores);
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
            if (!medibles.contains(c.sensor)) continue;
            TipoSensor t = programa.tipoDe(c.sensor);
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
            if (esMedible(s) && s.tipo() == null) sinTipo.add(s.nombre());
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
            if (!c.deriva()) continue;
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
            Map<TipoSensor, String> umbrales = new LinkedHashMap<>();
            Map<TipoSensor, String> canales = new LinkedHashMap<>();
            for (String var : variables(r.condicion)) {
                Simbolo s = programa.simbolos.buscar(var);
                TipoSensor m = programa.tipoDe(var);
                if (s == null || m == null) continue;
                if (s.clase() == Simbolo.Clase.UMBRAL) umbrales.putIfAbsent(m, var);
                else canales.putIfAbsent(m, var);
            }
            // Solo se reporta el cruce umbral/canal. Los clashes entre dos
            // canales los cubre la regla 14, y repetirlos sería ruido.
            for (Map.Entry<TipoSensor, String> c : canales.entrySet()) {
                for (Map.Entry<TipoSensor, String> u : umbrales.entrySet()) {
                    if (c.getKey().equals(u.getKey())) continue;
                    err(errores, r.linea, "incompatibilidad de tipos: se compara '"
                        + c.getValue() + "' (" + c.getKey().etiqueta() + ") contra el"
                        + " umbral '" + u.getValue() + "' (" + u.getKey().etiqueta()
                        + "), que es de otra magnitud. El umbral nunca se cruza con"
                        + " este sensor, asi que la condicion nunca se cumple.");
                    return;
                }
            }
        }
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
            for (String v : variables(r.condicion)) {
                usos.merge(v, r.linea, Math::min);
            }
        }
        for (Calculo c : programa.calculos) {
            usos.merge(c.sensor, c.linea, Math::min);
            if (c.esExpresion()) {
                for (String v : variables(c.expresion)) {
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

    private void reportarMagnitudes(List<String> errores, int linea,
                                    String contexto, Map<TipoSensor, String> vistas) {
        if (vistas.size() < 2) return;
        Iterator<Map.Entry<TipoSensor, String>> it = vistas.entrySet().iterator();
        Map.Entry<TipoSensor, String> a = it.next();
        Map.Entry<TipoSensor, String> b = it.next();
        err(errores, linea, contexto + " combina sensores de magnitudes"
            + " incompatibles: '" + a.getValue() + "' (" + a.getKey().etiqueta()
            + ") y '" + b.getValue() + "' (" + b.getKey().etiqueta() + ").");
    }

    // =============================================================
    //  EXPRESIONES: variables, evaluación y intervalos
    // =============================================================

    private List<String> variables(Expresion e) {
        List<String> vars = new ArrayList<>();
        recogerVariables(e, vars);
        return vars;
    }

    private void recogerVariables(Expresion e, List<String> vars) {
        if (e == null) return;
        if (e instanceof Variable v) {
            if (!vars.contains(v.nombre)) vars.add(v.nombre);
        } else if (e instanceof Binaria b) {
            recogerVariables(b.izquierda, vars);
            recogerVariables(b.derecha, vars);
        } else if (e instanceof Negacion n) {
            recogerVariables(n.expresion, vars);
        } else if (e instanceof Abs a) {
            recogerVariables(a.expresion, vars);
        }
    }

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

    /** Intervalo de valores de una variable para el que la condición se cumple. */
    private static class Intervalo {
        final double lo, hi;
        final boolean loInc, hiInc;
        Intervalo(double lo, boolean loInc, double hi, boolean hiInc) {
            this.lo = lo; this.loInc = loInc; this.hi = hi; this.hiInc = hiInc;
        }
    }

    private List<Intervalo> intervalo(String op, double c) {
        List<Intervalo> ivs = new ArrayList<>();
        switch (op) {
            case ">"  -> ivs.add(new Intervalo(c, false, Double.POSITIVE_INFINITY, true));
            case ">=" -> ivs.add(new Intervalo(c, true,  Double.POSITIVE_INFINITY, true));
            case "<"  -> ivs.add(new Intervalo(Double.NEGATIVE_INFINITY, true, c, false));
            case "<=" -> ivs.add(new Intervalo(Double.NEGATIVE_INFINITY, true, c, true));
            case "==" -> ivs.add(new Intervalo(c, true, c, true));
            case "!=" -> {
                ivs.add(new Intervalo(Double.NEGATIVE_INFINITY, true, c, false));
                ivs.add(new Intervalo(c, false, Double.POSITIVE_INFINITY, true));
            }
            default -> { }
        }
        return ivs;
    }

    private boolean solapan(List<Intervalo> a, List<Intervalo> b) {
        for (Intervalo x : a) {
            for (Intervalo y : b) {
                if (solapan(x, y)) return true;
            }
        }
        return false;
    }

    private boolean solapan(Intervalo a, Intervalo b) {
        double lo = Math.max(a.lo, b.lo);
        boolean loInc;
        if (a.lo == b.lo) loInc = a.loInc && b.loInc;
        else loInc = (a.lo > b.lo) ? a.loInc : b.loInc;

        double hi = Math.min(a.hi, b.hi);
        boolean hiInc;
        if (a.hi == b.hi) hiInc = a.hiInc && b.hiInc;
        else hiInc = (a.hi < b.hi) ? a.hiInc : b.hiInc;

        return lo < hi || (lo == hi && loInc && hiInc);
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