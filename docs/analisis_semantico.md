# Análisis Semántico — Lenguaje Timotomata

> Tercera y última fase del front-end. Se ejecuta sobre el AST y sobre la
> tabla de símbolos que el parser construyó, y es donde el programa deja de ser
> una lista de declaraciones para convertirse en un **cálculo verificado**.

---

## Índice

1. [Qué se verifica y por qué](#1-qué-se-verifica-y-por-qué)
2. [Las fichas de los tipos de sensor](#2-las-fichas-de-los-tipos-de-sensor)
3. [Las 25 reglas](#3-las-25-reglas)
4. [Reglas de coherencia interna](#4-reglas-de-coherencia-interna)
5. [Reglas de física](#5-reglas-de-física)
6. [Reglas de flujo de datos](#6-reglas-de-flujo-de-datos)
7. [Los intervalos y el análisis de reglas](#7-los-intervalos-y-el-análisis-de-reglas)
8. [Orden de ejecución y recuperacion](#8-orden-de-ejecución-y-recuperación)
9. [Ejecutar el análisis sin la interfaz](#9-ejecutar-el-análisis-sin-la-interfaz)

---

## 1. Qué se verifica y por qué

Un programa Timotomata declara canales, umbrales, señales derivadas y reglas.
La gramática garantiza que eso esté bien *escrito*. El análisis semántico
responde tres preguntas distintas:

| Pregunta | Qué la responde |
|:---------|:----------------|
| ¿Los nombres significan una sola cosa? | Reglas 1–4 |
| ¿Los números significan algo medible? | Reglas 5–7, 17 |
| ¿Lo declarado forma un cálculo coherente? | Reglas 8, 11, 14, 21–23 |
| ¿Qué código no se ejecutará nunca? | Regla 24 |
| ¿Qué valores dejan el sistema quieto? | Regla 25 (aviso) |
| ¿La condición es una pregunta bien formada? | Reglas 3, 14, 23, 16 (por comparación) |

La tercera es la que da cuerpo al proyecto. Sin ella, declarar
`umbral temp_max : TERMICO = 80;` al lado de `umbral voltaje_max : ELECTRICO = 220;`
no dice nada: el compilador aceptaría `si voltaje > temp_max` sin pestañear, y la
regla nunca se dispararía porque 220 V no supera 80 °C. El programa "funciona" y
no hace nada. **La regla 23 existe para que eso sea un error de compilación.**

### Lo que este analizador NO hace

- No evalúa el programa. No calcula valores de sensores ni simula ticks.
- No conoce el hardware concreto. Usa las fichas de `TipoSensor`, no un
  catálogo de transductores reales.
- No optimiza. Detecta contradicciones, pero no las reescribe.

---

## 2. Las fichas de los tipos de sensor

Cada tipo declarado lleva su ficha técnica. No es decorativo: es el criterio
con el que las reglas 17 a 19 juzgan lo que el programa escribe.

| Tipo | Unidad | Rango | Muestreo | Banda | `VENTANA` mín. |
|:-----|:-------|:------|:---------|:------|:---------------|
| `ELECTRICO` | V | 0 – 250 | 100 Hz | 50 Hz | 5 |
| `TERMICO` | °C | −40 – 125 | 0.1 Hz | 0.05 Hz | 20 |
| `LUMINICO` | lux | 0 – 10000 | 10 Hz | 5 Hz | 3 |
| `MECANICO` | mm/s | 0 – 50 | 2000 Hz | 1000 Hz | 3 |
| `QUIMICO` | pH | 0 – 14 | 0.01 Hz | 0.005 Hz | 50 |
| `HIDRAULICO` | bar | 0 – 400 | 1 Hz | 0.5 Hz | 10 |

Los tres campos de la derecha son los que hacen trabajo real:

- **Muestreo** — cuántas muestras por segundo puede tomar el transductor. Define
  la resolución temporal mínima de cualquier ventana.
- **Banda** — la frecuencia máxima que el transductor puede seguir. Ningún
  `FRECUENCIA=` puede estar por encima, porque la señal ya llega deformada.
- **`VENTANA` mínima** — cuántas muestras hacen falta para que el filtro tenga
  sentido. Una ventana más corta que eso promedia ruido, no señal.

---

## 3. Las 27 reglas

| # | Regla | Qué detecta |
|:-:|:------|:------------|
| 1 | Colisión sensor/umbral | El mismo nombre declara las dos cosas |
| 2 | Declaración duplicada | Un nombre declarado dos veces |
| 3 | Sensor como umbral | La parte derecha de una comparación no puede medirse |
| 4 | Umbral como sensor | No hay transductor detrás de un umbral |
| 5 | Rango inválido | `min >= max` en la ficha del sensor |
| 6 | Margen insuficiente | Rango demasiado estrecho para ser útil |
| 7 | Estado invertido | `PICO` en una condición de caída |
| 8 | Reglas contradictorias | Dos reglas se solapan con estados distintos |
| 10 | Parámetros inválidos | Frecuencia, ventana o amplitud físicamente imposibles |
| 11 | `SUMA` incompatible | Mezcla de magnitudes distintas |
| 13 | Identificador no declarado | Se usa un nombre que no existe |
| 14 | Tipos incompatibles | Se opera entre magnitudes distintas |
| 15 | Firma incorrecta | `calcular()` con los parámetros equivocados |
| 16 | División por cero constante | El denominador vale 0 |
| 17 | Umbral fuera de rango | El valor no existe en ese transductor |
| 18 | Frecuencia inobservable | Por encima de la banda o del Nyquist del programa |
| 19 | Ventana insuficiente | Por debajo del mínimo físico del tipo |
| 20 | Tipo ausente | Un canal usado sin `sensor x : TIPO;` |
| 21 | Orden de definición | Se usa una señal antes de su `calcular` |
| 22 | Autodependencia | `calcular(x, op, x)` |
| 23 | Umbral de otra magnitud | Se compara un canal contra el umbral equivocado |
| 24 | Regla inalcanzable | Las reglas anteriores ya cubren toda su región |
| 25 | Zona sin cubrir | Ninguna regla dispara para ciertos valores |
| 26 | Señal muerta | Una señal derivada que nadie lee |
| 27 | Expresión incompatible | Mezcla de magnitudes dentro de una expresión |

Las reglas 9 y 12 están omitidas: la 9 por decisión del proyecto, y la 12 porque
el lenguaje ya no tiene la sentencia `FIN`.

---

## 4. Reglas de coherencia interna

### Regla 1 — Colisión de tipos

Un nombre no puede ser sensor y umbral a la vez. Son categorías distintas: un
sensor se mide, un umbral fija un límite de comparación.

```
sensor  tension : ELECTRICO;
umbral tension : ELECTRICO = 220;   // error
```

### Reglas 2, 20 — Declaraciones y tipos

Cada nombre se declara una vez (regla 2) y todo canal usado lleva tipo
declarado (regla 20).

La 20 existe por una razón histórica concreta: antes el tipo se deducía del
nombre del identificador, y si no se deducía, **la verificación se saltaba en
silencio**. Un sensor sin tipo no lo marcaba ninguna regla; simplemente no se
comprobaba. Con el tipo declarado, la omisión es un error explícito.

### Reglas 3, 4 — Dirección de la lectura

```
si voltaje > voltaje_max entonces ...    // correcto: sensor a la izquierda
si 250 > voltaje entonces ...            // regla 3: un sensor no es un límite
calcular(PROMEDIO(CON=voltaje_max, VENTANA=5), x); // regla 4: no hay sensor detrás
```

### Regla 11 — `SUMA` con magnitudes incompatibles

`SUMA(CON=a, CON=b)` exige que `a` y `b` midan lo mismo. Sumar volts y grados no
produce volts: produce una suma sin unidad.

### Regla 27 — Magnitudes mezcladas dentro de una expresión

La regla 11 aplicada a las expresiones. Una cuenta también tiene que medir en
una sola magnitud:

```
calcular(v + t, mezcla);   // 'v' es ELECTRICO y 't' es TERMICO
```
```
Error semantico en linea 15: la expresion combina senales de magnitudes
incompatibles: 'v' (ELECTRICO) y 't' (TERMICO).
```

Sin ella, `calcular(v + t, mezcla)` pasaba en silencio: el destino heredaba el
tipo de la primera variable con tipo —volts— y la suma sin unidad quedaba
declarada como si significara algo. No es un caso raro: sumar un voltaje con una
temperatura es exactamente el error que alguien escribe un día creyendo que las
dos señales comparten escala, y sin tipos declarados que lo impidan solo lo
detecta quien recuerda la física.

Las constantes y los umbrales quedan fuera: no tienen magnitud propia, la toman
del otro lado de la operación. Solo se miran sensores y señales derivadas.

---

## 5. Reglas de física

Aquí es donde el lenguaje se aparta de la mayoría. Un compilador corriente
verifica tipos; este verifica además que los números **signifiquen algo**.

### Reglas 17, 5, 6 — El rango tiene que existir

```
umbral vmax : ELECTRICO = 5000;
// error: el umbral 'vmax' (5000.0) esta fuera del rango representable por un
// sensor ELECTRICO [0.0, 250.0 V].
```

Un umbral fuera del rango del transductor nunca se cruza, así que la regla
asociada es código muerto. Y una ficha cuyo rango sea degenerado (`min >= max`)
no describe ningún sensor posible.

`MARGEN_SEGURIDAD = 10.0` exige además que el rango sea ancho suficiente para
que haya margen entre el valor normal y el límite. **Es una constante sin
justificar**: 10.0 vale para voltios, no para pH ni para temperatura. Queda
pendiente convertirla en un campo de `TipoSensor`.

### Regla 18 — Nyquist contra la banda

`FRECUENCIA=` tiene dos techos, y el menor manda:

```java
// dentro de la regla 18, sobre hzPrograma = 1 / muestreo
limiteTransductor = tipo.bandaMaxima();
limiteNyquist     = hzPrograma / 2.0;
limite = Math.min(limiteTransductor, limiteNyquist);
```

El primer techo es el transductor —`TipoSensor.frecuenciaObservable()` solo
contesta esto—. El segundo es el reloj del programa: con `muestreo = 0.02` la
Nyquist es 25 Hz, así que pedir 30 Hz es pedirle al programa que cambie más
rápido que su propia cadencia. El mensaje de error dice cuál de los dos está
ganando.

```
frecuencia (30 Hz) no observable en un sensor ELECTRICO: el programa muestrea
cada 0.02 s (50 Hz), asi que por Nyquist no puede observar mas de 25 Hz.
```

### Regla 19 — La ventana tiene que cubrir tiempo

```
ventana (5) inferior al minimo fisico para TERMICO (20 muestras): con un
transductor de 0.1 Hz, 5 muestras cubren 50.0 s y el sensor no puede responder
tan rapido.
```

Es la regla que le da sentido a `VENTANA` como parámetro obligatorio en lugar de
opcional: sin ventana declarada no se sabe sobre qué intervalo se toma el
máximo.

### Regla 10 — Amplitud y rangos

La amplitud de un generador no puede exceder el rango del sensor que alimenta,
porque el resultado nunca superaría el tope físico y la forma de onda se
distorsionaría.

---

## 6. Reglas de flujo de datos

Estas tres reglas son nuevas, y son las que convierten la lista de
declaraciones en un grafo.

### El grafo

`TablaSimbolos.registrarCalculo()` registra una arista por cada `calcular`, en el
momento exacto en que el parser lo termina de leer:

```java
programa.simbolos.registrarCalculo(calculo.resultado(), calculo.origenes());
```

`origenes()` devuelve los nombres que la transformación lee: para una operación
nombrada son los `CON=`; para una expresión, las variables que aparecen. Un
generador (`SENO`, `COSENO`, `CUADRADA`) no lee nada —su valor depende solo del
tiempo— y queda como una raíz.

```
voltaje_prom            <- [voltaje]              (PROMEDIO, VENTANA=10)
voltaje_pico            <- [voltaje]              (MAXIMO, VENTANA=10)
total                   <- [voltaje, corriente]   (SUMA, por sus CON=)
voltaje_desvio          <- [voltaje]              (abs(voltaje - 220))
par_medio               <- [voltaje, corriente]   ((voltaje + corriente) / 2)
voltaje_prom_ajustado   <- [voltaje_prom]         (voltaje_prom * 1.1)
vibracion_onda          (no aparece)              (SENO: generador)
```

Cada transformación aporta aristas según **su propia firma**, no según una
regla única:

| Transformación | De qué depende |
|:---------------|:---------------|
| Expresión | De las variables que aparecen en ella |
| `PROMEDIO`, `MAXIMO` | De su `CON=` — la ventana se aplica sobre ese canal |
| `SUMA` | De sus `CON=`, que es su firma |
| `SENO`, `COSENO`, `CUADRADA` | De nada: dependen del reloj |

Un generador no aporta ninguna arista y por eso ni siquiera aparece en el grafo:
en `calcular(SENO(CON=voltaje, AMPLITUD=100, FRECUENCIA=0.1), voltaje_onda)` la
señal resultante no depende de `voltaje`. El `CON=` de un generador **no es una
dependencia de datos**: declara qué magnitud representa la onda, no de dónde sale
su valor. Confundirlo agregaría una dependencia que el código generado no tiene.

### Regla 21 — Orden de definición

```
si voltaje_futuro > voltaje_max entonces estado = PICO;   // linea 26
calcular(voltaje + 1, voltaje_futuro);              // linea 27
```
```
se usa 'voltaje_futuro' antes de calcularlo: la senal se define en la linea 27,
y hasta entonces no tiene valor. Los calculos se ejecutan en el orden en que
estan escritos.
```

No es una convención de estilo: es una restricción del modelo de ejecución. Los
cálculos corren una vez por tick, en orden de fuente, así que leer antes de
escribir no da "el valor del tick anterior" — da nada. Y el generador de código
necesita poder asignar cada señal **una sola vez por tick, en un solo lugar**.

### Regla 22 — Autodependencia

```
calcular(voltaje_x2, voltaje_x2 * 3, voltaje_x2);
```
```
autodependencia: 'voltaje_x2' se calcula a partir de si mismo. El resultado se
leeria de donde todavia no se ha escrito.
```

Lo más probable es que se haya olvidado el destino. Conviene notar que esta
regla y la 2 se disparan juntas en ese caso: el nombre ya estaba declarado, y
además se está leyendo a sí mismo.

### Regla 23 — Umbral de otra magnitud

Esta es la que evita el fallo silencioso descrito en la §1.

```
si voltaje_prom > temp_max entonces estado = PICO;
```
```
incompatibilidad de tipos: se compara 'voltaje_prom' (ELECTRICO) contra el
umbral 'temp_max' (TERMICO), que es de otra magnitud. El umbral nunca se cruza
con este sensor, asi que la condicion nunca se cumple.
```

La regla 14 no la cubría: mira solo los canales y señales medibles, porque
comparar un umbral contra otro umbral no tiene sentido físico. Pero un umbral
también lleva tipo declarado, y ahí está el hueco.

### Cómo los conectores cambian el alcance de las reglas 14 y 23

Las dos reglas agrupan por **comparación**, no por línea. Antes no hacía falta
decirlo porque una condición era una sola comparación; con `y` y `o` hay varias
en la misma línea y juntarlas daría falsos positivos:

```
si voltaje > voltaje_max y temperatura > temp_max entonces estado = INESTABLE;
```

Sin el agrupamiento por comparación, esta línea complainaría dos veces: la 14
diría que "combina magnitudes incompatibles" y la 23 cruzaría `voltaje` con
`temp_max`. Las dos serían falsas —son dos preguntas sobre sensores distintos, que
es lo que un sistema de monitoreo hace todo el tiempo—. Lo que sí es un error
sigue siendo `amperio + templado > 10`, donde una sola operación suma dos
magnitudes.

La 3 y la 16 se ajustaron igual: recorren las comparaciones hoja en vez de la
condición entera, y por eso funcionan con condiciones compuestas.

### Lo que los conectores todavía no cubren

Las reglas 7 y 8 razonan sobre intervalos, y una condición compuesta no se
reduce a un intervalo sin expandirla por De Morgan. `condicionRelacional()`
devuelve `null` cuando la condición no es una única comparación, y ambas reglas
se quedan calladas. No es que se las salte: es que no pueden decir nada. Es
justo el trabajo que falta para la fase de análisis de estados alcanzables.

### La memoria que cada cálculo necesita

`Calculo.memoria()` es el contrato con el generador de código:

| Transformación | Memoria |
|:---------------|:--------|
| `PROMEDIO(VENTANA=n)`, `MAXIMO(VENTANA=n)` | buffer circular de `n` muestras + índice |
| `SENO`, `COSENO`, `CUADRADA` | acumulador de fase |
| `SUMA`, cualquier expresión | ninguna |

Que la operación exija memoria es la razón de ser de la regla 19.

### El coste de declarar señales implícitamente

El destino de un `calcular` es su propia declaración. Eso evita tener que escribir el tipo de una señal derivada —lo hereda de lo que su transformación lee o declara— pero tiene un precio: **nada obliga a revisar que el nombre del destino sea el que se quería escribir**.

```
calcular(v * 2, voltage_doble);   // voltage
si voltaje_doble > 100 ...        // voltaje
```

Si la errata se nota, la regla 13 la pilla: falta el nombre correcto. Lo que **no** detectaba nadie era el caso contrario, donde la señal equivocada no la usa nadie:

```
calcular(v * 2, voltage_doble);   // y no se vuelve a mencionar
si v > 100 ...                    // voltage_doble queda huerfana
```

Ahí no falta nada: la señal existe, tiene tipo, y simplemente se calcula cada tick para que nadie la mire.

### Regla 26 — Señales derivadas que nadie lee

```
Aviso semantico en linea 28: la senal derivada 'voltaje_medio_manual' no la lee
ninguna regla ni ningun calculo. Si existe solo para graficarla, esto es
esperado; si no, el nombre esta mal escrito o sobra el calculo.
```

Es **aviso** y no error porque en este dominio el caso legítimo existe: una
señal puede calcularse únicamente para graficarla, y eso es una razón de peso
en un sistema de monitoreo.

Para que la regla no dé falsos positivos hay que marcar **también el canal de
origen** como uso. Una señal que solo se lee como origen de otro `calcular` no
aparece en ninguna condición, y sin ese marcado se reportaría como muerta
cuando es la base de un cálculo encadenado:

```
calcular(potencia_kw, potencia_kw - potencia_nominal, desvio_potencia);
           └──────────┘
           lectura legitima: es el origen de desvio_potencia
```

---

## 7. Intervalos, alcanzabilidad y zonas sin cubrir

`Intervalos` traduce cada comparación al conjunto de valores que la cumplen —`voltaje > 220` es `(220, +inf]`— y con esa representación se puede preguntar si una regla se ejecuta, si dos se contradicen, o qué valores dejan sin cubrir ninguna.

Los conjuntos se mantienen en **forma canónica**: ordenados, sin solapamientos y sin extremos compartidos. No es cosmético: sin ella, unir `[0,5)` con `[5,10]` daría dos intervalos en lugar de `[0,10]`, y la pregunta "¿queda algo sin cubrir?" respondería que sí cuando no queda nada.

### Regla 8 — Reglas contradictorias

```
regla contradictoria sobre 'voltaje': se solapa con la regla de la linea 10
(ambas se cumplen para el mismo valor) pero asignan estados distintos
('PICO' y 'CAIDA').
```

Dos reglas se contradicen cuando sus intervalos se solapan y los estados son distintos. La primera que se cumple gana: la segunda no es "incorrecta", es inalcanzable.

### Regla 24 — Qué código no se va a ejecutar nunca

La contrapartida: no solo qué reglas se contradicen, sino cuáles **jamás** se ejecutan. Eso es código muerto que el optimizador puede descartar.

```
si voltaje > voltaje_min entonces estado = PICO;      // (180, +inf]
si voltaje > voltaje_max entonces estado = INESTABLE; // (220, +inf] ⊆ la anterior
```
```
regla inalcanzable: las reglas anteriores ya cubren todos los valores de
'voltaje' = (220, +inf], asi que esta nunca se cumple.
```

**Por qué solo las reglas de una sola variable pueden probar cobertura.** Para tapar una regla hace falta que *toda* la región donde se cumple esté dentro de la región de alguna regla anterior. Al trabajar por ejes se comparan proyecciones, y proyectar pierde información:

```
linea 18:  si voltaje > 220 y temperatura > 80   ->  estado = INESTABLE
linea 38:  si voltaje > 220                       ->  estado = PICO
```

La proyección de la 18 sobre `voltaje` es `(220, +inf]`, igual que la de la 38, así que un análisis ingenuo daría la 38 por muerta. **No lo es**: con `voltaje = 230` y `temperatura = 20` la 18 no dispara y la 38 sí. La correlación entre variables es justo lo que el análisis por ejes destruye.

Por eso la cobertura solo la aportan reglas cuya región tiene **una única variable**: ahí su proyección *es* su región. Con esa salvedad el argumento es sólido —si al menos una variable de la regla está tapada por una regla univariable anterior, cualquier punto donde se cumpla la nueva dispara también esa anterior— y el análisis se limita a lo que puede demostrar.

Una conjunción también es código muerto si *sus dos partes* lo son:

```
si voltaje < voltaje_min y temperatura > temp_max   ->  inalcanzable
```

porque cualquier punto donde se cumpla tiene `voltaje < 180`, y la regla anterior de `voltaje` ya se cumplió antes.

### Regla 25 — Qué valores no disparan nada

La otra dirección: no basta con saber qué código no corre, también hay que saber qué valores dejan el sistema como está.

```
Aviso semantico en linea 7: ninguna regla dispara para [-inf, 80] de
'temperatura': en esos valores el estado se queda donde lo dejo la ultima regla
que se cumplio, o en el valor de 'sino'.
```

Es un **aviso**, no un error, y la distinción es deliberada. La banda entre el mínimo y el máximo es justamente donde el sistema funciona con normalidad y **a propósito** no dispara nada. Reportarlo como error obligaría a silenciarlo en todos los programas bien hechos.

| | Errores | Avisos |
|:--|:--------|:-------|
| cuentan para el código de salida | sí | no |
| se muestran en la salida | sí | sí |

Solo se reporta sobre variables que todas las reglas que las tocan restringen exactamente; si alguna usa una disyunción, la unión no es confiable y el hueco podría estar cubierto de todos modos.

### Regla 7 — Estado invertido

```
si voltaje > voltaje_max entonces estado = CAIDA;   // error
```
```
estado CAIDA asignado a una condicion por encima del maximo ('voltaje > 220.0'):
CAIDA corresponde a valores por debajo del minimo ('voltaje < 180.0').
```

---

## 8. Orden de ejecución y recuperación

Las reglas corren en un orden fijo dentro de `AnalizadorSemantico.analizar()`.
Ese orden importa por dos razones:

1. **Las reglas 3 y 4 necesitan `medibles()`, que depende de la resolución de
   tipos.** Por eso `resolverTiposDeSenales()` corre antes que cualquier otra.
2. **Los errores se ordenan por línea al final** (`ordenarPorLinea`), así que el
   orden de las reglas no afecta al orden en que se muestran.

```java
resolverTiposDeSenales(programa);        // una señal hereda el tipo de su canal
reglaMuestreo(programa, errores);
regla1Colision(...);   regla2Duplicadas(...);
regla3SensorComoUmbral(...);  regla4UmbralComoSensor(...);
reglas5y6Rango(...);  regla7EstadoIncompatible(...);  regla8ReglasContradictorias(...);
regla10ParametrosInvalidos(...);  regla11Magnitudes(...);
regla13NoDeclarados(...);  regla14TiposIncompatibles(...);
regla15LlamadasIncorrectas(...);  regla16DivisionPorCero(...);
reglas17a19Fisica(...);  regla20TiposAusentes(...);
regla21OrdenDefinicion(...);  regla22Autodependencia(...);  regla23UmbralDeOtraMagnitud(...);
regla24Inalcanzables(...);  regla25ZonasSinCubrir(programa, avisos);
regla26SenalesMuertas(programa, avisos);
regla27MezclaEnExpresion(...);
ordenarPorLinea(errores);
```

### Herencia de tipos

Una señal derivada se mide en la misma magnitud que lo que su transformación
consume, así que hereda su tipo. Pero **solo de un canal o de otra señal**:

```java
private TipoSensor tipoDeLectura(Calculo c, Programa programa) {
    if (c.esExpresion()) {
        // De la primera variable con tipo, que está en su expresión.
        for (String v : Expressiones.variables(c.expresion)) { ... }
        return null;
    }
    // De su CON=. Para un generador no es una lectura: declara qué magnitud
    // representa la onda, y es lo que permite verificar la frecuencia.
    Simbolo s = programa.simbolos.buscar(c.canal());
    return esMedible(s) ? s.tipo() : null;
}
```

Antes el tipo venía del argumento posicional del origen, que en la forma de
expresión **no se usaba**: `calcular(inexistente, v * 2, d)` dejaba a `d` sin
tipo aunque su expresión solo leyera un sensor perfectamente válido. Buscar en lo
que la transformación consume de verdad elimina esa clase de fallo.

Un umbral declara un tipo, pero no es un canal físico: no hay transductor
detrás. La regla 4 ya marca leer de un umbral como error, y si la señal
heredara su tipo pasaría inadvertida y quedaría sin verificar. Que se quede sin
tipo hace que la regla 20 la señale por separado.

Las señales de un generador quedan fuera de la regla 20: su tipo depende de que
traiga su `CON=`, y si no lo trae es la regla 15 —que valida la firma— la que lo
dice, con un mensaje que explica qué escribir. Anunciarlo dos veces por el mismo
error solo haría más ruidoso el diagnóstico.

---

## 9. Ejecutar el análisis sin la interfaz

El proyecto no tiene framework de tests. Lo que sí tiene es un punto de entrada
que devuelve texto plano y un código de retorno utilizable:

```
compile_and_run_cli.bat                          -> todos los tests\
compile_and_run_cli.bat tests\test_tipos.txt     -> un archivo
compile_and_run_cli.bat -v tests\test_tipos.txt  -> ademas tabla, flujo y memoria
```

```
=== test_tipos.txt ===
  260 tokens - lexico 0 - sintactico 0 - semantico 0
  -- gramatica --
     LL(1) sin conflictos: 16 no terminales, 54 producciones, 45 tokens
  -- tabla de simbolos --
     voltaje                SENSOR  ELECTRICO   V    linea 7
     ...
  -- flujo de datos --
     total                  <- [voltaje, corriente]
  -- memoria por calculo --
     voltaje_prom           PROMEDIO       buffer circular de 10 muestras
     vibracion_onda         SENO           acumulador de fase
     voltaje_pico           MAXIMO         buffer circular de 10 muestras
     total                  SUMA           ninguna
  -- 11 regla(s), muestreo = 0.01 s, estado inicial = normal, por defecto = normal
```

Devuelve 0 si todo compila y 1 si hay errores, así que sirve como comprobación
automatizable: `compile_and_run_cli.bat || echo FALLO`.

### Cobertura por archivo

| Archivo | Errores | Qué ejercita |
|:--------|--------:|:-------------|
| `test_tipos.txt` | 0 | Showcase de los 6 tipos, muestreo, `sino` |
| `test_calcular.txt` | 0 | Las dos formas de `calcular`, paréntesis, encadenamiento |
| `test_semantico_ok.txt` | 0 | Programa válido completo |
| `test_basico.txt`, `test_comentarios.txt`, `test_simulacion.txt` | 0 | Léxico y gramática |
| `test_condiciones.txt` | 0 | Conectores, negación, lectura de estado, rama `sino` |
| `test_alcanzable.txt` | 9 + 1 av. | Reglas 24 y 25: código muerto y zonas sin cubrir |
| `test_flujo.txt` | 4 | Reglas 21, 22 y 23 |
| `test_reglas_tipo.txt` | 9 | Reglas 17 a 20, una por tipo |
| `test_semantico.txt` | 22 | Reglas 1 a 16 |
| `test_operadores.txt` | 13 | Encadenamiento de operadores y precedencia |
| `test_case.txt` | 3 | Errores de uso |
| `test_errores.txt` | 4 léx + 2 sint. | Errores deToken |

---

*Documentación generada a partir del código fuente del compilador Timotomata.*
