package timotomata.semantic;

import java.util.*;
import timotomata.lexer.TipoToken;
import timotomata.lexer.Token;
import timotomata.parser.ast.*;

/**
 * ANÁLISIS SEMÁNTICO DEL LENGUAJE TIMOTOMATA
 * ==========================================
 *
 * Se ejecuta después del parser, sobre el AST (solo sentencias completas)
 * y sobre la lista de tokens (para las líneas de declaraciones).
 *
 * Reglas del documento "Errores semánticos propios":
 *   1. Sensor y umbral con el mismo nombre (colisión de tipos)
 *   2. Declaración duplicada de sensor o umbral
 *   3. Sensor usado como umbral (límite fijo de una condición)
 *   4. Umbral usado como sensor (objetivo de calcular() o de CON=...)
 *   5. Rango mínimo/máximo inválido (min >= max)
 *   6. Diferencia entre máximo y mínimo insuficiente ( < margen de seguridad )
 *   7. Estado incompatible con el rango detectado (PICO en caída / CAIDA en pico)
 *   8. Reglas contradictorias (se solapan y asignan estados distintos)
 *  10. Parámetros de simulación físicamente inválidos (frecuencia, ventana, amplitud)
 *  11. Combinación de magnitudes incompatibles en SUMA(CON=...)
 *
 * Reglas genéricas añadidas:
 *  13. Variable o identificador no declarado (usar x sin definirla)
 *  14. Incompatibilidad de tipos: una operación (aritmética o comparación)
 *      mezcla sensores de magnitudes físicas distintas. La parte "asignar"
 *      la garantiza la gramática (umbral = NUMERO, estado = keyword).
 *  15. Llamada a calcular() con argumentos incorrectos: cantidad o tipo de
 *      parámetros que no coinciden con la firma de la operación.
 *  16. División entre cero constante (denominador evaluable a 0 en tiempo
 *      de compilación).
 *
 * Las reglas 9 ("operación sin historial/muestras") y 12 ("programa
 * terminado de forma inválida") quedan omitidas: la 9 por pedido, y la 12
 * porque el lenguaje ya no tiene sentencia FIN.
 */
public class AnalizadorSemantico {

    /** Frecuencia de muestreo del simulador (los ejemplos usan 0.05 a 0.5). */
    public static final double FRECUENCIA_MUESTREO = 1.0;

    /** Margen mínimo exigido entre el máximo y el mínimo de un sensor. */
    public static final double MARGEN_SEGURIDAD = 10.0;

    // =============================================================
    //  PUNTO DE ENTRADA
    // =============================================================

    public List<String> analizar(Programa programa, List<Token> tokens) {
        List<String> errores = new ArrayList<>();
        if (programa == null) return errores;

        List<Declaracion> declaraciones = declaraciones(tokens);
        Set<String> sensores = new LinkedHashSet<>(programa.sensores);

        regla1Colision(programa, declaraciones, errores);
        regla2Duplicadas(declaraciones, errores);
        regla3SensorComoUmbral(programa, sensores, errores);
        regla4UmbralComoSensor(programa, errores);
        reglas5y6Rango(programa, declaraciones, errores);
        regla7EstadoIncompatible(programa, errores);
        regla8ReglasContradictorias(programa, errores);
        regla10ParametrosInvalidos(programa, errores);
        regla11Magnitudes(programa, sensores, errores);
        regla13NoDeclarados(programa, errores);
        regla14TiposIncompatibles(programa, errores);
        regla15LlamadasIncorrectas(programa, errores);
        regla16DivisionPorCero(programa, errores);

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
    //  DECLARACIONES (a partir de los tokens)
    // =============================================================

    /** Declaración de sensor o umbral con su línea. */
    private static class Declaracion {
        final String nombre;
        final TipoToken tipo;   // SENSOR o UMBRAL
        final int linea;
        Declaracion(String nombre, TipoToken tipo, int linea) {
            this.nombre = nombre;
            this.tipo = tipo;
            this.linea = linea;
        }
    }

    /**
     * Extrae todas las declaraciones de la lista de tokens. En la gramática
     * solo SENSOR y UMBRAL van seguidos de un ID, así que el patrón es
     * inequívoco y conserva duplicados y líneas (el mapa de Programa pisa
     * los duplicados).
     */
    private List<Declaracion> declaraciones(List<Token> tokens) {
        List<Declaracion> decls = new ArrayList<>();
        if (tokens == null) return decls;
        for (int i = 0; i + 1 < tokens.size(); i++) {
            Token t = tokens.get(i);
            Token sig = tokens.get(i + 1);
            if (sig.tipo != TipoToken.ID) continue;
            if (t.tipo == TipoToken.SENSOR || t.tipo == TipoToken.UMBRAL) {
                decls.add(new Declaracion(sig.lexema, t.tipo, t.linea));
            }
        }
        return decls;
    }

    private boolean esSensor(String nombre, Collection<String> sensores) {
        return nombre != null && sensores.contains(nombre);
    }

    // =============================================================
    //  REGLA 1 — Sensor y umbral con el mismo nombre
    // =============================================================

    private void regla1Colision(Programa programa, List<Declaracion> decls,
                                List<String> errores) {
        Map<String, Declaracion> sensor = new HashMap<>();
        Map<String, Declaracion> umbral = new HashMap<>();
        for (Declaracion d : decls) {
            Map<String, Declaracion> mapa =
                d.tipo == TipoToken.SENSOR ? sensor : umbral;
            mapa.putIfAbsent(d.nombre, d);
        }
        for (String nombre : sensor.keySet()) {
            Declaracion ds = sensor.get(nombre);
            Declaracion du = umbral.get(nombre);
            if (du == null) continue;
            int linea = Math.max(ds.linea, du.linea);
            err(errores, linea, "'" + nombre + "' esta declarado como sensor"
                + " (linea " + ds.linea + ") y como umbral (linea " + du.linea
                + "): colision de tipos.");
        }
    }

    // =============================================================
    //  REGLA 2 — Declaración duplicada
    // =============================================================

    private void regla2Duplicadas(List<Declaracion> decls, List<String> errores) {
        Map<String, Integer> primeraVez = new HashMap<>();
        for (Declaracion d : decls) {
            String clave = d.tipo + " " + d.nombre;
            Integer primera = primeraVez.get(clave);
            if (primera == null) {
                primeraVez.put(clave, d.linea);
            } else {
                String que = d.tipo == TipoToken.SENSOR ? "sensor" : "umbral";
                err(errores, d.linea, "declaracion duplicada: el " + que + " '"
                    + d.nombre + "' ya fue declarado en la linea " + primera + ".");
            }
        }
    }

    // =============================================================
    //  REGLA 3 — Sensor usado como umbral
    // =============================================================

    /**
     * En una condición, la expresión de la derecha es el límite fijo.
     * Un sensor se mide, no fija límites: si la derecha referencia un
     * sensor declarado, es un error.
     */
    private void regla3SensorComoUmbral(Programa programa, Set<String> sensores,
                                        List<String> errores) {
        for (Regla r : programa.reglas) {
            Binaria cond = condicionRelacional(r);
            if (cond == null) continue;
            for (String var : variables(cond.derecha)) {
                if (!esSensor(var, sensores)) continue;
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
        Set<String> umbrales = programa.umbrales.keySet();
        for (Calculo c : programa.calculos) {
            if (umbrales.contains(c.sensor)) {
                err(errores, c.linea, "el umbral '" + c.sensor
                    + "' no puede usarse como sensor: es el objetivo de calcular().");
            }
            for (Parametro p : c.parametros) {
                if (p.nombre != null && p.nombre.equalsIgnoreCase("con")
                        && umbrales.contains(p.valor)) {
                    err(errores, c.linea, "el umbral '" + p.valor
                        + "' no puede usarse como sensor en CON=... (suma de"
                        + " sensores).");
                }
            }
        }
    }

    // =============================================================
    //  REGLAS 5 y 6 — Rango mínimo/máximo
    // =============================================================

    /**
     * Busca el umbral mínimo y el máximo de un sensor aceptando las
     * variantes S_min / min_S / minS (y su análogo con max), sin
     * distinguir mayúsculas.
     */
    private double[] buscarRango(String sensor, Programa programa,
                                 List<Declaracion> decls) {
        String min = buscarUmbral(sensor, "min", programa, decls);
        String max = buscarUmbral(sensor, "max", programa, decls);
        if (min == null || max == null) return null;
        Double vMin = programa.umbrales.get(min);
        Double vMax = programa.umbrales.get(max);
        if (vMin == null || vMax == null) return null;
        return new double[]{vMin, vMax};
    }

    private String buscarUmbral(String sensor, String prefijo, Programa programa,
                                List<Declaracion> decls) {
        String[] variantes = {
            sensor + "_" + prefijo,          // voltaje_min
            prefijo + "_" + sensor,          // min_voltaje
            prefijo + sensor                 // minvoltaje
        };
        for (Declaracion d : decls) {
            if (d.tipo != TipoToken.UMBRAL) continue;
            for (String v : variantes) {
                if (d.nombre.equalsIgnoreCase(v)) return d.nombre;
            }
        }
        // El mapa puede tener el nombre aunque el token no se haya hallado.
        for (String nombre : programa.umbrales.keySet()) {
            for (String v : variantes) {
                if (nombre.equalsIgnoreCase(v)) return nombre;
            }
        }
        return null;
    }

    private void reglas5y6Rango(Programa programa, List<Declaracion> decls,
                               List<String> errores) {
        for (Declaracion d : decls) {
            if (d.tipo != TipoToken.SENSOR) continue;
            double[] rango = buscarRango(d.nombre, programa, decls);
            if (rango == null) continue;
            double min = rango[0], max = rango[1];

            if (min >= max) {
                err(errores, d.linea, "rango invalido del sensor '" + d.nombre
                    + "': el minimo (" + min + ") es mayor o igual que el maximo ("
                    + max + ").");
                continue;
            }
            if (max - min < MARGEN_SEGURIDAD) {
                err(errores, d.linea, "la diferencia entre el maximo (" + max
                    + ") y el minimo (" + min + ") del sensor '" + d.nombre
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

    /** Condición de la regla si es una comparación, o null. */
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
            // Solo cuando el lado izquierdo es la variable medida y el
            // límite derecho es un valor fijo (número o umbral).
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
                        } else if (valor > FRECUENCIA_MUESTREO) {
                            err(errores, c.linea, "la frecuencia (" + valor
                                + ") supera la frecuencia de muestreo ("
                                + FRECUENCIA_MUESTREO + ").");
                        }
                    }
                    case "ventana" -> {
                        if (valor <= 0) {
                            err(errores, c.linea, "ventana de promedio invalida ("
                                + valor + "): debe ser positiva.");
                        }
                    }
                    case "amplitud" -> {
                        double[] rango = buscarRangoDeclarado(c.sensor, programa);
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

    /** Rango [min,max] del sensor declarado, o null si no tiene par. */
    private double[] buscarRangoDeclarado(String sensor, Programa programa) {
        String min = null, max = null;
        for (String nombre : programa.umbrales.keySet()) {
            String n = nombre.toLowerCase();
            String s = sensor == null ? "\u0000" : sensor.toLowerCase();
            if (n.equals(s + "_min") || n.equals("min_" + s) || n.equals("min" + s)) {
                min = nombre;
            } else if (n.equals(s + "_max") || n.equals("max_" + s)
                    || n.equals("max" + s)) {
                max = nombre;
            }
        }
        if (min == null || max == null) return null;
        return new double[]{programa.umbrales.get(min), programa.umbrales.get(max)};
    }

    // =============================================================
    //  REGLA 11 — Combinación de magnitudes incompatibles en SUMA
    // =============================================================

    private void regla11Magnitudes(Programa programa, Set<String> sensores,
                                   List<String> errores) {
        for (Calculo c : programa.calculos) {
            if (!"suma".equalsIgnoreCase(c.operacion)) continue;
            Map<Magnitud, String> vistas = new LinkedHashMap<>();
            for (Parametro p : c.parametros) {
                if (p.nombre == null || !p.nombre.equalsIgnoreCase("con")) continue;
                if (!esSensor(p.valor, sensores)) continue;
                Magnitud m = Magnitud.clasificar(p.valor);
                if (m == null) continue;
                vistas.putIfAbsent(m, p.valor);
            }
            reportarMagnitudes(errores, c.linea, "SUMA", vistas);
        }
    }

    // =============================================================
    //  REGLA 13 — Variable o identificador no declarado
    // =============================================================

    /**
     * Se usa un identificador que no fue declarado con `sensor` ni con
     * `umbral`. El lenguaje distingue mayúsculas, así que la comparación
     * es exacta. Cubre condiciones, el objetivo de calcular() y los CON=.
     */
    private void regla13NoDeclarados(Programa programa, List<String> errores) {
        Set<String> declarados = new LinkedHashSet<>(programa.sensores);
        declarados.addAll(programa.umbrales.keySet());

        for (Regla r : programa.reglas) {
            Set<String> yaVistos = new LinkedHashSet<>();
            for (String var : variables(r.condicion)) {
                if (declarados.contains(var) || !yaVistos.add(var)) continue;
                err(errores, r.linea, "identificador '" + var
                    + "' no declarado: se usa en la condicion sin haber sido"
                    + " definido con 'sensor' o 'umbral'.");
            }
        }

        for (Calculo c : programa.calculos) {
            if (c.sensor != null && !c.sensor.isEmpty()
                    && !declarados.contains(c.sensor)) {
                err(errores, c.linea, "identificador '" + c.sensor
                    + "' no declarado: es el sensor objetivo de calcular().");
            }
            for (Parametro p : c.parametros) {
                if (p.nombre == null || !p.nombre.equalsIgnoreCase("con")) continue;
                if (declarados.contains(p.valor)) continue;
                err(errores, c.linea, "identificador '" + p.valor
                    + "' no declarado: se usa en CON= sin haber sido definido"
                    + " con 'sensor'.");
            }
        }
    }

    // =============================================================
    //  REGLA 14 — Incompatibilidad de tipos (operaciones que mezclan
    //             sensores de magnitudes físicas distintas)
    // =============================================================

    /**
     * En Timotomata la "magnitud física" es el tipo del sensor: operar o
     * comparar ELECTRICO con TERMICO es incompatibilidad de tipos. Abarca
     * la aritmética (+ - * /) y la comparación, sea cual sea la posición
     * del sensor en la expresión. Solo se reporta si AMBAS magnitudes se
     * reconocen en la tabla de {@link Magnitud}; la parte "asignar" de la
     * regla la garantiza la gramática (umbral = NUMERO, estado = keyword).
     */
    private void regla14TiposIncompatibles(Programa programa,
                                           List<String> errores) {
        for (Regla r : programa.reglas) {
            Map<Magnitud, String> vistas = new LinkedHashMap<>();
            for (String var : variables(r.condicion)) {
                if (!esSensor(var, programa.sensores)) continue;
                Magnitud m = Magnitud.clasificar(var);
                if (m == null) continue;
                vistas.putIfAbsent(m, var);
            }
            reportarMagnitudes(errores, r.linea,
                               "incompatibilidad de tipos: la operacion", vistas);
        }
    }

    // =============================================================
    //  REGLA 15 — Llamada a calcular() con argumentos incorrectos
    // =============================================================

    /**
     * Cantidad o tipo de parámetros que no coinciden con la firma de la
     * operación. (Antes esto se lanzaba como error sintáctico desde el
     * parser; ahora pertenece a la fase semántica.)
     *
     * Firmas: SENO/COSENO/CUADRADA → AMPLITUD + FRECUENCIA (2).
     *         PROMEDIO → VENTANA (1). MAXIMO → sin parámetros.
     *         SUMA → 2 o más CON.
     * Los valores numéricos (AMPLITUD/FRECUENCIA/VENTANA) y que CON reciba
     * un ID los exige la gramática.
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
                if (!c.parametros.isEmpty()) {
                    err(errores, c.linea, "llamada con argumentos incorrectos: "
                        + "MAXIMO no acepta parametros ().");
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

    /**
     * El denominador se evalúa en tiempo de compilación y da 0 (número
     * literal o umbral con valor 0). Los denominadores que dependen de un
     * sensor no son constantes y no se reportan.
     */
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

    private void reportarMagnitudes(List<String> errores, int linea,
                                    String contexto, Map<Magnitud, String> vistas) {
        if (vistas.size() < 2) return;
        Iterator<Map.Entry<Magnitud, String>> it = vistas.entrySet().iterator();
        Map.Entry<Magnitud, String> a = it.next();
        Map.Entry<Magnitud, String> b = it.next();
        err(errores, linea, contexto + " combina sensores de magnitudes"
            + " incompatibles: '" + a.getValue() + "' (" + a.getKey()
            + ") y '" + b.getValue() + "' (" + b.getKey() + ").");
    }

    // =============================================================
    //  EXPRESIONES: variables, evaluación y intervalos
    // =============================================================

    /** Nombres de variable que aparecen en la expresión (en orden). */
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
        // Numero no aporta variables
    }

    /** Evalúa la expresión; null si no es una constante (sensor/desconocida). */
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
        double lo, hi;
        boolean loInc, hiInc;
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

    /** Convierte un lexema numérico a Double (null si no lo es). */
    private static Double numero(String lexema) {
        if (lexema == null) return null;
        try {
            return Double.parseDouble(lexema);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
