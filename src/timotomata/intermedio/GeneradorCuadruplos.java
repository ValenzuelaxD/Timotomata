package timotomata.intermedio;

import java.util.List;
import java.util.Locale;

import timotomata.parser.ast.Abs;
import timotomata.parser.ast.Binaria;
import timotomata.parser.ast.Calculo;
import timotomata.parser.ast.Comparacion;
import timotomata.parser.ast.Condicion;
import timotomata.parser.ast.Conector;
import timotomata.parser.ast.EstadoActual;
import timotomata.parser.ast.Expresion;
import timotomata.parser.ast.LiteralEstado;
import timotomata.parser.ast.Negacion;
import timotomata.parser.ast.Negado;
import timotomata.parser.ast.Numero;
import timotomata.parser.ast.Parametro;
import timotomata.parser.ast.Programa;
import timotomata.parser.ast.Regla;
import timotomata.parser.ast.Variable;

/**
 * Genera la tabla de cuadruplos de un programa Timotomata a partir del AST.
 *
 * <p>El programa se traduce en el orden en que la maquina lo ejecuta:
 *
 * <ol>
 *   <li>El estado inicial, si lo hay.</li>
 *   <li>Los {@code calcular} en orden de fuente.</li>
 *   <li>Las reglas en orden de fuente.</li>
 *   <li>El estado por defecto de {@code sino = X;}, si lo hay.</li>
 * </ol>
 *
 * <h2>Gana la primera regla</h2>
 *
 * <p>La semantica del lenguaje es que la primera regla que se cumple es la que
 * fija el estado, y las siguientes ya no se evaluan. Por eso cada regla que
 * dispara termina en un {@code GOTO FIN}, y la etiqueta {@code FIN} va despues
 * del estado por defecto: solo la alcanza quien no disparo ninguna regla. Sin
 * ese salto, dos reglas podrian escribir {@code estado} en el mismo muestreo.
 *
 * <h2>Asignaciones</h2>
 *
 * <p>{@code estado} se asigna como valor ({@code (=, PICO, -, estado)}) porque
 * es un registro de maquina, no una magnitud. Las senales derivadas solo se
 * escriben desde {@code calcular}, y siempre a traves de un temporal para que
 * la tabla sea uniforme.
 *
 * <h2>Inicializacion y cuerpo</h2>
 *
 * <p>La asignacion de {@code estado = X;} aparece al principio porque forma
 * parte del programa, pero se ejecuta una sola vez: el resto de la tabla
 * (calculos, reglas y {@code sino}) es el cuerpo que la maquina repite en cada
 * muestreo. Un ejecutor tendria que separar ambas partes para conservar el
 * estado entre ticks; la tabla, como representacion, las muestra seguidas.
 */
public final class GeneradorCuadruplos {

    /** Nombre del registro de maquina que guarda el estado. */
    private static final String ESTADO = "estado";

    /** Etiqueta a la que saltan las reglas que ya dispararon. */
    private static final String ETIQUETA_FIN = "FIN";

    private TablaCuadruplos tabla;

    /**
     * Traduce el programa completo.
     *
     * @return la tabla de cuadruplos; vacia si el programa es {@code null}.
     */
    public TablaCuadruplos generar(Programa programa) {
        tabla = new TablaCuadruplos();
        if (programa == null) return tabla;

        if (programa.estadoInicial != null) {
            tabla.emitir("=", estado(programa.estadoInicial), null, ESTADO,
                programa.lineaEstadoInicial);
        }

        for (Calculo calculo : programa.calculos) {
            generarCalculo(calculo);
        }

        for (Regla regla : programa.reglas) {
            generarRegla(regla);
        }

        // El estado por defecto solo se alcanza si ninguna regla disparo:
        // todas las que disparan saltan a FIN, que va justo despues.
        if (programa.estadoPorDefecto != null) {
            tabla.emitir("=", estado(programa.estadoPorDefecto), null, ESTADO,
                programa.lineaEstadoPorDefecto);
        }

        if (!programa.reglas.isEmpty()) {
            tabla.emitir("LABEL", null, null, ETIQUETA_FIN, 0);
        }

        return tabla;
    }

    // =============================================================
    //  CALCULOS
    // =============================================================

    private void generarCalculo(Calculo calculo) {
        if (calculo == null) return;

        if (calculo.esExpresion()) {
            String valor = generarExpresion(calculo.expresion);
            tabla.emitir("=", valor, null, calculo.destino, calculo.linea);
        } else if (calculo.esOperacion()) {
            String valor = generarOperacion(calculo);
            tabla.emitir("=", valor, null, calculo.destino, calculo.linea);
        }
    }

    /**
     * Emite la operacion nombrada y devuelve el temporal con su resultado.
     *
     * <p>Solo hay dos huecos de argumento, asi que cada operacion usa los que
     * le sirven:
     *
     * <ul>
     *   <li>{@code PROMEDIO}, {@code MAXIMO} - canal y ventana.</li>
     *   <li>{@code SENO}, {@code COSENO}, {@code CUADRADA} - amplitud y
     *       frecuencia; el canal {@code CON=} es declarativo, el generador no
     *       lo lee (construye la onda a partir del reloj).</li>
     *   <li>{@code SUMA} - se encadena de dos en dos para admitir 2 o mas
     *       canales.</li>
     * </ul>
     */
    private String generarOperacion(Calculo calculo) {
        String temporal = tabla.nuevoTemporal();
        String op = calculo.operacion == null
            ? "?" : calculo.operacion.toUpperCase(Locale.ROOT);

        switch (calculo.operacion == null ? "" : calculo.operacion.toLowerCase(Locale.ROOT)) {
            case "promedio":
            case "maximo":
                tabla.emitir(op, calculo.canal(), parametro(calculo, "ventana"),
                    temporal, calculo.linea);
                break;

            case "seno":
            case "coseno":
            case "cuadrada":
                tabla.emitir(op, parametro(calculo, "amplitud"),
                    parametro(calculo, "frecuencia"), temporal, calculo.linea);
                break;

            case "suma": {
                List<String> canales = calculo.canales();
                if (canales.size() < 2) {
                    // La fase semantica ya rechaza SUMA con menos de dos CON.
                    // Se emite igual para no romper la tabla de un programa
                    // que solo tiene errores semanticos.
                    tabla.emitir(op, canales.isEmpty() ? null : canales.get(0),
                        null, temporal, calculo.linea);
                    break;
                }
                String acumulado = canales.get(0);
                for (int i = 1; i < canales.size(); i++) {
                    boolean ultimo = i == canales.size() - 1;
                    String destino = ultimo ? temporal : tabla.nuevoTemporal();
                    tabla.emitir(op, acumulado, canales.get(i), destino, calculo.linea);
                    acumulado = destino;
                }
                break;
            }

            default:
                tabla.emitir(op, null, null, temporal, calculo.linea);
                break;
        }

        return temporal;
    }

    /** Valor de un parametro con nombre, o {@code null} si no aparece. */
    private static String parametro(Calculo calculo, String nombre) {
        for (Parametro p : calculo.parametros) {
            if (p.nombre != null && p.nombre.equalsIgnoreCase(nombre)) {
                return p.valor;
            }
        }
        return null;
    }

    // =============================================================
    //  EXPRESIONES
    // =============================================================

    /**
     * Traduce una expresion y devuelve el operando que la representa.
     *
     * <p>Un literal o una variable no generan instruccion: se usan
     * directamente como argumento. Lo que genera instruccion es cada nodo
     * interno, y su resultado queda en un temporal nuevo.
     */
    private String generarExpresion(Expresion expresion) {
        if (expresion == null) return null;

        if (expresion instanceof Numero numero) {
            return literal(numero.valor);
        }
        if (expresion instanceof Variable variable) {
            return variable.nombre;
        }
        if (expresion instanceof EstadoActual) {
            return ESTADO;
        }
        if (expresion instanceof LiteralEstado literalEstado) {
            return estado(literalEstado.nombre);
        }
        if (expresion instanceof Negacion negacion) {
            String operando = generarExpresion(negacion.expresion);
            String temporal = tabla.nuevoTemporal();
            tabla.emitir("NEG", operando, null, temporal, negacion.linea);
            return temporal;
        }
        if (expresion instanceof Abs abs) {
            String operando = generarExpresion(abs.expresion);
            String temporal = tabla.nuevoTemporal();
            tabla.emitir("ABS", operando, null, temporal, abs.linea);
            return temporal;
        }
        if (expresion instanceof Binaria binaria) {
            String izquierda = generarExpresion(binaria.izquierda);
            String derecha = generarExpresion(binaria.derecha);
            String temporal = tabla.nuevoTemporal();
            tabla.emitir(binaria.operador, izquierda, derecha, temporal, binaria.linea);
            return temporal;
        }
        return "?";
    }

    // =============================================================
    //  CONDICIONES
    // =============================================================

    /**
     * Traduce una condicion y devuelve el temporal booleano que la representa.
     *
     * <p>Las comparaciones dejan {@code 1} o {@code 0} en su temporal, que es
     * lo que consume {@code JT}. {@code Y}, {@code O} y {@code NO} se emiten
     * como operadores propios: las expresiones del lenguaje no tienen efectos
     * laterales, asi que evaluar los dos lados siempre da el mismo resultado
     * que un cortocircuito.
     */
    private String generarCondicion(Condicion condicion) {
        if (condicion == null) return null;

        if (condicion instanceof Comparacion comparacion) {
            String izquierda = generarExpresion(comparacion.izquierda);
            String derecha = generarExpresion(comparacion.derecha);
            String temporal = tabla.nuevoTemporal();
            tabla.emitir(comparacion.operador, izquierda, derecha, temporal,
                comparacion.linea);
            return temporal;
        }
        if (condicion instanceof Conector conector) {
            String izquierda = generarCondicion(conector.izquierda);
            String derecha = generarCondicion(conector.derecha);
            String temporal = tabla.nuevoTemporal();
            String op = conector.tipo == Conector.Tipo.Y ? "AND" : "OR";
            tabla.emitir(op, izquierda, derecha, temporal, conector.linea);
            return temporal;
        }
        if (condicion instanceof Negado negado) {
            String operando = generarCondicion(negado.condicion);
            String temporal = tabla.nuevoTemporal();
            tabla.emitir("NOT", operando, null, temporal, negado.linea);
            return temporal;
        }
        return "?";
    }

    // =============================================================
    //  REGLAS
    // =============================================================

    /**
     * Emite una regla.
     *
     * <p>Sin rama {@code sino}:
     *
     * <pre>
     *   (JT, t, -, L1)        si se cumple, salta a la asignacion
     *   (GOTO, -, -, L2)      si no, pasa a la regla siguiente
     *   (LABEL, -, -, L1)
     *   (=, ESTADO, -, estado)
     *   (GOTO, -, -, FIN)     ya disparo: las demas no se evaluan
     *   (LABEL, -, -, L2)
     * </pre>
     *
     * <p>Con rama {@code sino}, la etiqueta {@code L2} lleva a la asignacion
     * del estado alternativo y la ejecucion continua con la regla siguiente:
     * un {@code sino} no es una regla cumplida, asi que no cierra la cadena.
     * Si una regla posterior dispara, su estado manda; si ninguna lo hace,
     * queda el {@code sino} global (que por eso se emite despues de todas las
     * reglas). La rama verdadera si salta a {@code FIN}, porque ahi si gano
     * una regla y las siguientes no deben evaluarse.
     */
    private void generarRegla(Regla regla) {
        String condicion = generarCondicion(regla.condicion);
        String etiquetaVerdadera = tabla.nuevaEtiqueta();

        tabla.emitir("JT", condicion, null, etiquetaVerdadera, regla.linea);

        if (regla.tieneRamaSino()) {
            String etiquetaFalsa = tabla.nuevaEtiqueta();
            tabla.emitir("GOTO", null, null, etiquetaFalsa, regla.linea);

            tabla.emitir("LABEL", null, null, etiquetaVerdadera, regla.linea);
            tabla.emitir("=", estado(regla.estado), null, ESTADO, regla.linea);
            tabla.emitir("GOTO", null, null, ETIQUETA_FIN, regla.linea);

            tabla.emitir("LABEL", null, null, etiquetaFalsa, regla.linea);
            tabla.emitir("=", estado(regla.estadoSino), null, ESTADO, regla.linea);
        } else {
            String etiquetaSiguiente = tabla.nuevaEtiqueta();
            tabla.emitir("GOTO", null, null, etiquetaSiguiente, regla.linea);

            tabla.emitir("LABEL", null, null, etiquetaVerdadera, regla.linea);
            tabla.emitir("=", estado(regla.estado), null, ESTADO, regla.linea);
            tabla.emitir("GOTO", null, null, ETIQUETA_FIN, regla.linea);

            tabla.emitir("LABEL", null, null, etiquetaSiguiente, regla.linea);
        }
    }

    // =============================================================
    //  AUXILIARES
    // =============================================================

    /** Literales sin el {@code .0} que sobra cuando el valor es entero. */
    private static String literal(double valor) {
        if (!Double.isInfinite(valor) && !Double.isNaN(valor)
                && valor == Math.rint(valor)) {
            return String.valueOf((long) valor);
        }
        return String.valueOf(valor);
    }

    /**
     * Literales de estado siempre en mayusculas.
     *
     * <p>El parser guarda el estado inicial y los literales en minusculas pero
     * deja {@code regla.estado} como se escribio, asi que sin normalizar la
     * tabla mezclaria {@code normal} con {@code PICO} en la misma columna.
     */
    private static String estado(String valor) {
        return valor == null ? null : valor.toUpperCase(Locale.ROOT);
    }
}
