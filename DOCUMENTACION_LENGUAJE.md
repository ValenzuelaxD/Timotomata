# Documentación Completa del Lenguaje Timotomata

## Índice

1. [Estructura del compilador](#1-estructura-del-compilador)
2. [El Autómata Finito Determinista (AFD)](#2-el-autómata-finito-determinista-afd)
3. [Tabla completa de tokens](#3-tabla-completa-de-tokens)
4. [Palabras reservadas](#4-palabras-reservadas)
5. [Operadores](#5-operadores)
6. [Identificadores y literales](#6-identificadores-y-literales)
7. [Signos de puntuación](#7-signos-de-puntuación)
8. [Gramática formal completa](#8-gramática-formal-completa)
9. [Arquitectura del código](#9-arquitectura-del-código)
10. [Ejemplos completos](#10-ejemplos-completos)

---

## 1. Estructura del compilador

El front-end del compilador se divide en **3 fases**, más una capa de interfaz
gráfica:

```
Código fuente
     ↓
[ LEXER ]     AFD de 13 estados y 19 clases → tokens
     ↓
[ PARSER ]    tabla LL(1) → AST + Árbol de Derivación + Tabla de Símbolos
     ↓
[ SEMANTICO ] 20 reglas contra la ficha técnica de cada tipo de sensor
     ↓
  Veredicto + AST + Tabla de símbolos
```

| Fase | Archivo principal | Función |
|------|------------------|---------|
| **Lexer** | `src/timotomata/lexer/Lexer.java` | Convierte texto → tokens |
| **Parser** | `src/timotomata/parser/Parser.java` | Tokens → AST y tabla de símbolos |
| **Semántico** | `src/timotomata/semantic/AnalizadorSemantico.java` | Verifica tipos, rangos y signos |
| **Símbolos** | `src/timotomata/simbolos/TablaSimbolos.java` | Registro único de declaraciones |
| **UI** | `src/timotomata/ui/AppController.java` | Interfaz gráfica JavaFX |

### Archivos del proyecto

```
src/timotomata/
├── Main.java                          ← Punto de entrada
├── lexer/
│   ├── Lexer.java                     ← AFD del analizador léxico
│   ├── TipoToken.java                 ← Enumeración de todos los tokens
│   └── Token.java                     ← Clase Token {tipo, lexema, linea}
├── parser/
│   ├── Gramatica.java                 ← Gramática formal LL(1) con tabla de parsing
│   ├── Parser.java                    ← Parser recursivo descendente
│   └── NodoDerivacion.java            ← Nodo del árbol de derivación
│   └── ast/
│       ├── Programa.java              ← AST raíz
│       ├── Expresion.java             ← Interfaz de expresiones
│       ├── Numero.java                ← Expresión: número literal
│       ├── Variable.java              ← Expresión: variable
│       ├── Binaria.java               ← Expresión: operación binaria
│       ├── Negacion.java              ← Expresión: negación
│       ├── Abs.java                   ← Expresión: valor absoluto
│       ├── Calculo.java               ← AST: sentencia calcular
│       ├── Parametro.java             ← Parámetro de cálculo
│       └── Regla.java                 ← AST: regla si/entonces
└── ui/
    ├── AppController.java             ← Controlador JavaFX
    ├── PanelLexer.java                ← Panel de análisis léxico
    ├── PanelDerivacion.java           ← Panel del árbol de derivación
    └── PanelAST.java                  ← Panel del AST
```

---

## 2. El Autómata Finito Determinista (AFD)

### ¿Dónde está definido?

El AFD está implementado en **`src/timotomata/lexer/Lexer.java`**. Es la base del analizador léxico.

### Estados del AFD (13 estados)

```
Q0 = 0             Estado inicial
Q_ID = 1           Leyendo identificador / palabra reservada
Q_NUM = 2          Leyendo parte entera de un número
Q_NUM_PUNTO = 3    Acabamos de ver '.' en un número
Q_NUM_DEC = 4      Leyendo parte decimal
Q_EQ = 5           Vimos '=', esperando ver si es '==' o solo '='
Q_GT = 6           Vimos '>', esperando ver si es '>='
Q_LT = 7           Vimos '<', esperando ver si es '<=' o '<>'
Q_NOT = 8          Vimos '!', esperando ver si es '!='
Q_DIV = 9          Vimos '/', esperando ver si es '//' o '/*'
Q_COM_LINEA = 10   Dentro de comentario //
Q_COM_BLOQ = 11    Dentro de comentario /* */
Q_COM_BLOQ_FIN = 12  Vimos '*' dentro de /*, esperando '/' para cerrar
```

### Clases de caracteres (alfabeto, 18 clases)

```java
LETRA = 0      DIGITO = 1     PUNTO = 2
ESP = 3        NL = 4
MAS = 5        MENOS = 6      POR = 7        DIV = 8
IGUAL = 9      MAYOR_ = 10    MENOR_ = 11    EXCL = 12
PUNTOCOMA = 13 PIZQ = 14      PDER = 15      COMA_ = 16
OTRO = 17
```

### Diagrama de transiciones

```
                    ┌──────────────────────────────────────────┐
                    │                                          │
                    ▼ ESP, NL                                  │
              ┌──────────┐                                     │
   LETRA ────→│   Q_ID   │←──── LETRA, DIGITO                 │
              └────┬─────┘                                     │
                   │ (aceptación)                              │
                   │                                           │
              ┌──────────┐                                     │
   DIGITO ───→│  Q_NUM   │←──── DIGITO                        │
              └────┬─────┘                                     │
                   │ PUNTO                                     │
                   ▼                                           │
              ┌────────────┐                                   │
              │Q_NUM_PUNTO │                                   │
              └─────┬──────┘                                   │
                    │ DIGITO                                   │
                    ▼                                           │
              ┌──────────┐                                     │
              │Q_NUM_DEC │←──── DIGITO                         │
              └────┬─────┘                                     │
                   │ (aceptación)                              │
                   │                                           │
         IGUAL ───→ Q_EQ ── IGUAL ──→ Q0 (==)                │
         MAYOR ───→ Q_GT ── IGUAL ──→ Q0 (>=)                │
         MENOR ───→ Q_LT ── IGUAL ──→ Q0 (<=)                │
                   │      ── MAYOR ──→ Q0 (<>)                │
         EXCL  ───→ Q_NOT ─ IGUAL ──→ Q0 (!=)                │
                   │                                           │
         DIV   ───→ Q_DIV ── DIV ──→ Q_COM_LINEA ── NL ──→ Q0│
                          ── POR ──→ Q_COM_BLOQ ──...──→ Q0  │
                   │                                           │
   +, -, *, ;, (, ), , ───→ Q0 (token directo)               │
                   │                                           │
                   └───────────────────────────────────────────┘
```

### Algoritmo de escaneo (Maximal Munch)

El lexer usa el principio de **Maximal Munch**: siempre consume la mayor cantidad de caracteres posible antes de decidir qué token emitir.

```
1. Arrancar en Q0, marcar inicio del lexema
2. Mientras haya transición definida:
   - Consumir el carácter
   - Si es estado de aceptación, recordar posición
3. Si no hay transición:
   - Si hay último estado de aceptación → emitir token, retroceder
   - Si no → error léxico
```

### Código relevante del AFD

```java
// En Lexer.java:

// Tabla de transiciones: TABLA_TRANS[estado][clase] = siguiente estado
public static final int[][] TABLA_TRANS = new int[NUM_ESTADOS][NUM_CLASES];

// Función de transición principal (en escanear()):
int sigEstado = TABLA_TRANS[estado][clase];
if (sigEstado == SIN_TRANS) break;  // No hay transición → emitir token
```

---

## 3. Tabla completa de tokens

Cada token se compone de: **`{tipo, lexema, línea}`**.

| # | TipoToken (enum) | Cómo se genera | Ejemplo de lexema |
|---|-----------------|---------------|-------------------|
| 1 | `SENSOR` | Palabra reservada | `sensor` |
| 2 | `UMBRAL` | Palabra reservada | `umbral` |
| 3 | `SI` | Palabra reservada | `si` |
| 4 | `ENTONCES` | Palabra reservada | `entonces` |
| 5 | `ESTADO` | Palabra reservada | `estado` |
| 6 | `ABS` | Palabra reservada | `abs` |
| 7 | `CALCULAR` | Palabra reservada | `calcular` |
| 8 | `ESTADO_SISTEMA` | Palabra reservada | `normal`, `pico`, `caida`, `inestable` |
| 9 | `SENO` | Palabra reservada | `SENO` |
| 10 | `COSENO` | Palabra reservada | `COSENO` |
| 11 | `CUADRADA` | Palabra reservada | `CUADRADA` |
| 12 | `PROMEDIO` | Palabra reservada | `PROMEDIO` |
| 13 | `MAXIMO` | Palabra reservada | `MAXIMO` |
| 14 | `SUMA` | Palabra reservada | `SUMA` |
| 15 | `AMPLITUD` | Palabra reservada | `AMPLITUD` |
| 16 | `FRECUENCIA` | Palabra reservada | `FRECUENCIA` |
| 17 | `VENTANA` | Palabra reservada | `VENTANA` |
| 18 | `CON` | Palabra reservada | `CON` |
| 19 | `ID` | Identificador | `voltaje`, `temp1`, `x` |
| 20 | `NUMERO` | Literal numérico | `220`, `0.1`, `5` |
| 21 | `MAS` | Operador | `+` |
| 22 | `MENOS` | Operador | `-` |
| 23 | `POR` | Operador | `*` |
| 24 | `DIV` | Operador | `/` |
| 25 | `MAYOR` | Operador | `>` |
| 26 | `MENOR` | Operador | `<` |
| 27 | `IGUAL_IGUAL` | Operador | `==` |
| 28 | `MAYOR_IGUAL` | Operador | `>=` |
| 29 | `MENOR_IGUAL` | Operador | `<=` |
| 30 | `DIFERENTE` | Operador | `!=` o `<>` |
| 31 | `ASIGNACION` | Signo | `=` |
| 32 | `PUNTO_COMA` | Signo | `;` |
| 33 | `COMA` | Signo | `,` |
| 34 | `PAREN_IZQ` | Signo | `(` |
| 35 | `PAREN_DER` | Signo | `)` |
| 36 | `EOF` | Fin de archivo | `""` (vacío) |

### Código de la enumeración

```java
// En TipoToken.java:
public enum TipoToken {
    SENSOR, UMBRAL, SI, ENTONCES, ESTADO, ABS,
    ID, NUMERO, ESTADO_SISTEMA,
    MAYOR, MENOR, IGUAL_IGUAL, MAYOR_IGUAL, MENOR_IGUAL, DIFERENTE,
    MAS, MENOS, POR, DIV,
    ASIGNACION, PUNTO_COMA, PAREN_IZQ, PAREN_DER,
    CALCULAR, COMA,
    SENO, COSENO, CUADRADA, PROMEDIO, MAXIMO, SUMA,
    AMPLITUD, FRECUENCIA, VENTANA, CON,
    EOF
}
```

---

## 4. Palabras reservadas

Todas las palabras reservadas son **case-insensitive**: no importa si se escriben en mayúsculas, minúsculas o mixtas.

### Palabras de sentencias

| Token | Lexemas válidos | Uso |
|-------|----------------|-----|
| `SENSOR` | `sensor`, `Sensor`, `SENSOR` | Declara un sensor con su tipo: `sensor voltaje : ELECTRICO;` |
| `UMBRAL` | `umbral`, `Umbral` | Declara un umbral: `umbral max = 250;` |
| `SI` | `si`, `SI` | Inicia regla: `si voltaje > 220 entonces ...` |
| `ENTONCES` | `entonces` | Separa condición de acción |
| `ESTADO` | `estado` | Asigna estado del sistema |
| `CALCULAR` | `calcular` | Inicia cálculo funcional |
| `ABS` | `abs`, `Abs` | Valor absoluto en expresiones |

### Conectores lógicos

| Token | Lexemas válidos | Uso |
|-------|----------------|-----|
| `Y` | `y`, `Y` | Conjunction dentro de una condición |
| `O` | `o`, `O` | Disyunción dentro de una condición |
| `NO` | `no`, `No`, `NO` | Negación de la comparación que sigue |

> Son palabras reservadas, así que `y`, `o` y `no` dejan de poder usarse como
> nombres. Es el costo de escribir el lenguaje en castellano: en un programa de
> este dominio un identificador `y` o `o` sería más natural que casi cualquier
> otra cosa.

### Valores de estado del sistema

| Token | Lexemas válidos |
|-------|----------------|
| `ESTADO_SISTEMA` | `normal`, `pico`, `caida`, `inestable` |

### Operaciones de cálculo (dentro de `calcular`)

| Token | Lexemas válidos | Significado |
|-------|----------------|-------------|
| `SENO` | `SENO`, `seno`, `Seno` | Onda senoidal |
| `COSENO` | `COSENO`, `coseno` | Onda cosenoidal |
| `CUADRADA` | `CUADRADA`, `cuadrada` | Onda cuadrada |
| `PROMEDIO` | `PROMEDIO`, `promedio` | Promedio móvil |
| `MAXIMO` | `MAXIMO`, `maximo` | Valor máximo |
| `SUMA` | `SUMA`, `suma` | Suma de sensores |

### Parámetros de operaciones

| Token | Lexemas válidos | Significado |
|-------|----------------|-------------|
| `AMPLITUD` | `AMPLITUD`, `amplitud` | Amplitud de la onda |
| `FRECUENCIA` | `FRECUENCIA`, `frecuencia` | Frecuencia de la onda |
| `VENTANA` | `VENTANA`, `ventana` | Tamaño de ventana |
| `CON` | `CON`, `con` | Enlace a sensor |

### Tipos de sensor

Cada sensor **debe** declarar su tipo. El tipo aporta la unidad física, el
rango representable por el transductor, su frecuencia de muestreo y su banda.
Es lo que permite verificar que un umbral sea medible o que una frecuencia de
onda sea observable.

| Token | Lexemas válidos | Unidad | Rango | Muestreo | Banda | `PROMEDIO` mín. |
|-------|----------------|--------|-------|----------|-------|-----------------|
| `ELECTRICO` | `electrico` | V | 0 – 250 | 100 Hz | 50 Hz | 5 |
| `TERMICO` | `termico` | °C | −40 – 125 | 0.1 Hz | 0.05 Hz | 20 |
| `LUMINICO` | `luminico` | lux | 0 – 10 000 | 10 Hz | 5 Hz | 3 |
| `MECANICO` | `mecanico` | mm/s | 0 – 50 | 2000 Hz | 1000 Hz | 3 |
| `QUIMICO` | `quimico` | pH | 0 – 14 | 0.01 Hz | 0.005 Hz | 50 |
| `HIDRAULICO` | `hidraulico` | bar | 0 – 400 | 1 Hz | 0.5 Hz | 10 |

*Una señal derivada —el destino de `calcular`— hereda el tipo de su origen.*

El conjunto es **cerrado**: agregar un tipo obliga a tocar el generador de
código, que despacha sobre el enum `TipoSensor`.

---

## 5. Operadores

Los operadores **no son** palabras reservadas en el sentido tradicional — son símbolos que el AFD reconoce directamente por su carácter.

### Operadores aritméticos

| Token | Símbolo | Nombre | Uso |
|-------|---------|--------|-----|
| `MAS` | `+` | Suma | `a + b` |
| `MENOS` | `-` | Resta / negación | `a - b`, `-x` |
| `POR` | `*` | Multiplicación | `a * b` |
| `DIV` | `/` | División | `a / b` |

### Operadores relacionales

| Token | Símbolos | Nombre | Uso |
|-------|----------|--------|-----|
| `MAYOR` | `>` | Mayor que | `a > b` |
| `MENOR` | `<` | Menor que | `a < b` |
| `IGUAL_IGUAL` | `==` | Igualdad | `a == b` |
| `MAYOR_IGUAL` | `>=` | Mayor o igual | `a >= b` |
| `MENOR_IGUAL` | `<=` | Menor o igual | `a <= b` |
| `DIFERENTE` | `!=` o `<>` | Diferente | `a != b` |

### Operador de asignación

| Token | Símbolo | Nombre | Uso |
|-------|---------|--------|-----|
| `ASIGNACION` | `=` | Asignación | `umbral x = 10;`, `AMPLITUD=300` |

### Cómo los reconoce el AFD

```
'='  → Q_EQ  ── si sigue '=' → IGUAL_IGUAL (==)
               ── si no       → ASIGNACION (=)

'>'  → Q_GT  ── si sigue '=' → MAYOR_IGUAL (>=)
               ── si no       → MAYOR (>)

'<'  → Q_LT  ── si sigue '=' → MENOR_IGUAL (<=)
               ── si sigue '>' → DIFERENTE (<>)
               ── si no       → MENOR (<)

'!'  → Q_NOT ── si sigue '=' → DIFERENTE (!=)
               ── si no       → Error léxico
```

---

## 6. Identificadores y literales

### Identificadores (token `ID`)

- Empiezan con **letra** (`a-z`, `A-Z`) o `_`
- Siguen con **letras, dígitos o `_`**
- **No pueden ser** palabras reservadas
- Son **case-sensitive**: `voltaje` ≠ `Voltaje`
- Ejemplos: `voltaje`, `temp1`, `sensorA`, `_contador`

### Literales numéricos (token `NUMERO`)

- Enteros: `0`, `5`, `220`, `1000`
- Decimales: `0.1`, `3.14`, `0.05`
- No se acepta notación científica
- Los negativos se manejan con el operador `MENOS` aparte: `-10` = `MENOS NUMERO`

---

## 7. Signos de puntuación

| Token | Símbolo | Función |
|-------|---------|---------|
| `ASIGNACION` | `=` | Asignación de valor |
| `PUNTO_COMA` | `;` | Fin de sentencia |
| `COMA` | `,` | Separador de elementos |
| `DOS_PUNTOS` | `:` | Declara el tipo del sensor |
| `PAREN_IZQ` | `(` | Apertura de paréntesis |
| `PAREN_DER` | `)` | Cierre de paréntesis |

---

## 8. Gramática formal completa

### Notación BNF (Backus-Naur Form)

```
PROGRAMA → SENTENCIA PROGRAMA
         | ε
```

```
SENTENCIA → SENSOR ID : TIPO_MAGNITUD ;
          | UMBRAL ID : TIPO_MAGNITUD = VALOR_UMBRAL ;
          | SI CONDICION ENTONCES ESTADO = ESTADO_SISTEMA ;
          | CALCULAR ( ID , TIPO_OP , DESTINO_OP ) ;
          | ESTADO = ESTADO_SISTEMA ;
          | SINO = ESTADO_SISTEMA ;
          | MUESTREO = NUMERO ;
```

```
TIPO_MAGNITUD → ELECTRICO | TERMICO | LUMINICO
              | MECANICO | QUIMICO | HIDRAULICO
```

```
DESTINO_OP → COMA ID
           | ε
```

> **Canal y señal.** `calcular(origen, op, destino)` lee del canal `origen` y
> escribe en la señal derivada `destino`, dejando el canal físico intacto. Si
> se omite el destino, el cálculo sobrescribe el canal de origen.
>
> **`muestreo`.** Declara la duración de un tick, en segundos. Es el reloj del
> programa: sin ella no se puede generar el kernel de muestreo, y fija el límite
> de Nyquist contra el que se valida `FRECUENCIA=`. Se declara exactamente una
> vez y debe ser positiva.
>
> **`estado` y `sino`.** `estado = X;` fija el estado inicial de la máquina.
> `sino = X;` define la salida cuando ninguna regla se cumple.
>
> **Prioridad de las reglas.** Se evalúan en el orden del fuente y **gana la
> primera que se cumpla**. Si ninguna se cumple, queda el estado de `sino`. Es
> lo que determina qué reglas son alcanzables y cuáles quedan sombreadas.

```
VALOR_UMBRAL → NUMERO
             | - NUMERO
```

```
CONDICION → EXPRESION OP_REL EXPRESION
```

```
EXPRESION → TERMINO EXPRESION_SIG

EXPRESION_SIG → + TERMINO EXPRESION_SIG
              | - TERMINO EXPRESION_SIG
              | ε
```

```
TERMINO → FACTOR TERMINO_SIG

TERMINO_SIG → * FACTOR TERMINO_SIG
            | / FACTOR TERMINO_SIG
            | ε
```

```
FACTOR → NUMERO
       | ID
       | ABS ( EXPRESION )
       | - FACTOR
       | ( EXPRESION )
```

Los paréntesis agrupan: `(a + b) / 2` es una expresión válida. No producen un
nodo propio en el AST, porque la precedencia ya lo resuelve.

```
OP_REL → > | < | == | >= | <= | != | <>
```

```
TIPO_OP → SENO ( LISTA_PARAMS )
        | COSENO ( LISTA_PARAMS )
        | CUADRADA ( LISTA_PARAMS )
        | PROMEDIO ( LISTA_PARAMS )
        | MAXIMO ( LISTA_PARAMS )
        | SUMA ( LISTA_PARAMS )
        | EXPRESION
```

`TIPO_OP` admite **dos cosas distintas**. Las seis operaciones nombradas tienen
firma fija; `EXPRESION` es una transformación arbitraria:

```timotomata
calcular(voltaje, PROMEDIO(VENTANA=10), voltaje_prom);   // operación nombrada
calcular(voltaje, voltaje * 2, voltaje_x2);              // expresión arbitraria
calcular(voltaje, (voltaje + corriente) / 2, medio);    // con paréntesis
```

La segunda forma sustituye a las variables: da la misma expresividad sin
introducir almacenamiento mutable, ámbito ni una cuarta clase de símbolo en la
tabla de símbolos.

Ambas familias se distinguen con un solo token: las operaciones empiezan por su
palabra reservada (`SENO`…`SUMA`) y las expresiones por `ABS`, `ID`, `NUMERO`,
`-` o `(`. Los conjuntos son disjuntos, así que la decisión es LL(1).

```
CONDICION     → NEGACION COMPARACION CONDICION_SIG
NEGACION      → NO NEGACION
              | ε

COMPARACION   → EXPRESION OP_REL EXPRESION

CONDICION_SIG → CONECTOR NEGACION COMPARACION CONDICION_SIG
              | ε

CONECTOR      → Y
              | O

RAMA_SI_SINO  → SINO ESTADO ASIGNACION ESTADO_SISTEMA
              | ε
```

`Y` y `O` tienen la misma precedencia y se asocian a la izquierda, así que
`a y b o c` se lee `(a y b) o c`. `NO` liga más fuerte y se aplica solo a la
comparación que sigue; se puede encadenar (`no no a > 10`).

```timotomata
si voltaje > voltaje_max y temperatura > temp_max entonces estado = INESTABLE;
si voltaje < voltaje_min o voltaje > voltaje_max entonces estado = PICO;
si no voltaje > voltaje_max entonces estado = NORMAL;
si estado == PICO y voltaje < voltaje_min entonces estado = CAIDA;
si voltaje > voltaje_max entonces estado = PICO sino estado = NORMAL;
```

**No hay paréntesis en las condiciones.** Un paréntesis podría ser una expresión
agrupada —`(a + b) / 2`— o una condición agrupada, y los dos empiezan igual;
separarlos exigiría mirar dos tokens y la gramática dejaría de ser LL(1). Para
negar una conjunción se escribe su opuesta: `si a <= 10 o b >= 2`.

La rama `sino` de una regla es distinta del `sino = X;` global: aquel dice a qué
estado se vuelve el sistema cuando **ninguna** regla se cumple, este dice a qué
se vuelve cuando **esta** regla no.

`estado` también se puede leer dentro de una condición, y comparar un literal
de estado (`PICO`, `CAIDA`, `NORMAL`, `INESTABLE`) con `==` o `!=`.

`TIPO_OP` admite **dos cosas distintas**. Las seis operaciones nombradas tienen
firma fija; `EXPRESION` es una transformación arbitraria:

```timotomata
calcular(voltaje, PROMEDIO(VENTANA=10), voltaje_prom);   // operación nombrada
calcular(voltaje, voltaje * 2, voltaje_x2);              // expresión arbitraria
calcular(voltaje, (voltaje + corriente) / 2, medio);    // con paréntesis
```

La segunda forma sustituye a las variables: da la misma expresividad sin
introducir almacenamiento mutable, ámbito ni una cuarta clase de símbolo en la
tabla de símbolos.

Ambas familias se distinguen con un solo token: las operaciones empiezan por su
palabra reservada (`SENO`…`SUMA`) y las expresiones por `ABS`, `ID`, `NUMERO`,
`-` o `(`. Los conjuntos son disjuntos, así que la decisión es LL(1).

```
LISTA_PARAMS → PARAM LISTA_PARAMS_SIG
             | ε

LISTA_PARAMS_SIG → , LISTA_PARAMS
                  | ε
```

```
PARAM → AMPLITUD = NUMERO
      | FRECUENCIA = NUMERO
      | VENTANA = NUMERO
      | CON = ID
```

### No terminales de la gramática (16) — 54 producciones

| # | Nombre | Descripción |
|---|--------|-------------|
| 0 | `PROGRAMA` | Programa completo (lista de sentencias) |
| 1 | `SENTENCIA` | Una sentencia del lenguaje |
| 2 | `VALOR_UMBRAL` | Valor de umbral (positivo o negativo) |
| 3 | `CONDICION` | Condición booleana |
| 4 | `EXPRESION` | Expresión aritmética |
| 5 | `EXPRESION_SIG` | Continuación de expresión (+/-) |
| 6 | `TERMINO` | Término de expresión |
| 7 | `TERMINO_SIG` | Continuación de término (*//) |
| 8 | `FACTOR` | Factor atómico |
| 9 | `OP_REL` | Operador relacional |
| 10 | `TIPO_OP` | Tipo de operación de cálculo |
| 11 | `LISTA_PARAMS` | Lista de parámetros |
| 12 | `PARAM` | Un parámetro con nombre |
| 13 | `LISTA_PARAMS_SIG` | Continuación de lista de parámetros |
| 14 | `TIPO_MAGNITUD` | Tipo de sensor declarado |
| 15 | `DESTINO_OP` | Señal de destino del cálculo, o ε |

### Tabla de parsing LL(1)

La gramática es **LL(1)**: se puede parsear con un solo token de anticipación. La tabla de parsing se genera automáticamente en `Gramatica.java` calculando los conjuntos **FIRST** y **FOLLOW** de cada no terminal.

```java
// En Gramatica.java: consulta a la tabla
public static int obtenerProduccion(int noTerminal, TipoToken lookahead) {
    return TABLA[noTerminal][lookahead.ordinal()];
}
```

---

## 9. Arquitectura del código

### Flujo de ejecución

```
                    Main.java
                        │
                        ▼
              AppController.java
              (interfaz JavaFX)
                        │
                        ▼
                    Lexer.java
                 (texto → tokens)
                        │
                        ▼
                   Parser.java
            (tokens → AST + Árbol Derivación)
                        │
                        ▼
                 Gramatica.java
                  (tabla LL(1))
```

### Árbol de derivación

El parser construye simultáneamente:

1. **AST (Abstract Syntax Tree)**: estructura de datos para las fases siguientes (en `ast/`)
2. **Árbol de derivación**: árbol visual con todos los pasos de la gramática (clase `NodoDerivacion`)
3. **Tabla de símbolos** (`TablaSimbolos`): cada declaración con su clase, tipo de sensor, unidad, valor y línea

La tabla de símbolos es la **única fuente de verdad** de lo declarado: el
analizador semántico y la interfaz leen de ahí, en lugar de re-derivar las
declaraciones desde la lista de tokens.

El árbol de derivación se muestra en la UI mediante `PanelDerivacion.java`, que usa el algoritmo de layout con colapsado de nodos sintéticos (`EXPRESION_SIG`, `TERMINO_SIG`, `LISTA_PARAMS`, `PARAM`).

---

## 10. Ejemplos completos

Todos los ejemplos de esta sección pasan las tres fases con **0 errores**.

### Ejemplo 1: Sensor, umbral y reloj

```
sensor voltaje     : ELECTRICO;
sensor temperatura : TERMICO;
umbral maxTemp : TERMICO = 120;
muestreo = 0.01;
```

### Ejemplo 2: Estado inicial, reglas y salida por defecto

```
muestreo = 0.01;
umbral voltaje_max : ELECTRICO = 220;
umbral voltaje_min : ELECTRICO = 180;

estado = NORMAL;

si voltaje > voltaje_max entonces estado = pico;
si voltaje < voltaje_min entonces estado = caida;

sino = NORMAL;
```

### Ejemplo 3: Cálculos con señal derivada

```
sensor voltaje     : ELECTRICO;
sensor corriente   : ELECTRICO;
sensor temperatura : TERMICO;
muestreo = 0.01;

calcular(voltaje,     SENO(AMPLITUD=300, FRECUENCIA=0.1), voltaje_onda);
calcular(voltaje,     COSENO(AMPLITUD=100, FRECUENCIA=0.05), voltaje_fase);
calcular(temperatura, PROMEDIO(VENTANA=20), temp_prom);
calcular(voltaje,     MAXIMO(VENTANA=10), voltaje_pico);
calcular(corriente,   SUMA(CON=voltaje, CON=corriente), total);
```

### Ejemplo 4: Uso de abs() en condiciones

```
si abs(voltaje - 220) > 10 entonces estado = pico;
```

### Ejemplo 5: Programa completo

```
sensor voltaje     : ELECTRICO;
sensor corriente   : ELECTRICO;
sensor temperatura : TERMICO;

muestreo = 0.01;

umbral maxTemperatura : TERMICO   = 85;
umbral minVoltaje     : ELECTRICO = 200;

estado = NORMAL;

si voltaje < minVoltaje entonces estado = caida;
si temperatura > maxTemperatura entonces estado = inestable;
si abs(voltaje - 220) > 20 entonces estado = pico;

calcular(corriente, PROMEDIO(VENTANA=10), corriente_prom);
calcular(voltaje,   SENO(AMPLITUD=100, FRECUENCIA=0.1), voltaje_onda);
si voltaje_onda > 220 entonces estado = pico;

sino = NORMAL;
```

> `PROMEDIO(VENTANA=10)` cumple el mínimo de `ELECTRICO` (5 muestras).
> `PROMEDIO(VENTANA=20)` cumple el de `TERMICO` (20 muestras).
> `SENO(AMPLITUD=100, …)` entra dentro del rango de `ELECTRICO` (0–250).

### Ejemplo 6: Transformaciones encadenadas

Las señales derivadas se pueden calcular a partir de otras señales derivadas.
El tipo se hereda por la cadena, así que no hay que redeclararlo:

```
sensor voltaje     : ELECTRICO;
sensor corriente   : ELECTRICO;

umbral voltaje_max : ELECTRICO = 220;

muestreo = 0.01;
estado = NORMAL;

calcular(voltaje, voltaje * 2, voltaje_x2);
calcular(voltaje, PROMEDIO(VENTANA=10), voltaje_prom);
calcular(voltaje_prom, voltaje_prom * 1.1, voltaje_prom_ajustado);
calcular(voltaje, (voltaje + corriente) / 2, par_medio);

si voltaje_prom_ajustado > voltaje_max entonces estado = PICO;
si par_medio < 100 entonces estado = CAIDA;

sino = NORMAL;
```

Cada línea lee el resultado de las anteriores, en orden. Una señal no puede
usarse antes de su `calcular` (regla 21), porque los cálculos corren una vez por
tick en el orden en que están escritos.

El grafo de flujo de datos que el analizador construye para este programa es:

```
voltaje_x2           <- [voltaje]
voltaje_prom         <- [voltaje]
voltaje_prom_ajustado <- [voltaje_prom]
par_medio            <- [voltaje, corriente]
```

Y la memoria que el futuro generador de código tendría que reservar:

| Señal | Transformación | Memoria |
|:------|:---------------|:--------|
| `voltaje_x2` | expresión | ninguna |
| `voltaje_prom` | `PROMEDIO(VENTANA=10)` | buffer circular de 10 muestras |
| `voltaje_prom_ajustado` | expresión | ninguna |
| `par_medio` | expresión | ninguna |

---

## Resumen visual: todos los tokens del lenguaje

```
═══════════════════════════════════════════════════════════════
  PALABRAS RESERVADAS (case-insensitive)
═══════════════════════════════════════════════════════════════

  SENTENCIAS:       sensor  umbral  si  entonces  estado
                    calcular  abs  sino  muestreo

  ESTADOS:          normal  pico  caida  inestable

  OPERACIONES:      seno  coseno  cuadrada  promedio
                    maximo  suma

  PARÁMETROS:       amplitud  frecuencia  ventana  con

  TIPOS DE SENSOR:  electrico  termico  luminico
                    mecanico  quimico  hidraulico

═══════════════════════════════════════════════════════════════
  OPERADORES (símbolos)
═══════════════════════════════════════════════════════════════

  ARITMÉTICOS:      +   -   *   /
## Resumen visual: todos los tokens del lenguaje

```
═══════════════════════════════════════════════════════════════
  PALABRAS RESERVADAS (case-insensitive)
═══════════════════════════════════════════════════════════════

  SENTENCIAS:       sensor  umbral  si  entonces  estado
                    calcular  abs

  ESTADOS:          normal  pico  caida  inestable

  OPERACIONES:      seno  coseno  cuadrada  promedio
                    maximo  suma

  PARÁMETROS:       amplitud  frecuencia  ventana  con

  TIPOS DE SENSOR:  electrico  termico  luminico
                    mecanico  quimico  hidraulico

═══════════════════════════════════════════════════════════════
  OPERADORES (símbolos)
═══════════════════════════════════════════════════════════════

  ARITMÉTICOS:      +   -   *   /

  ASIGNACIÓN:       =

═══════════════════════════════════════════════════════════════
  SIGNOS
═══════════════════════════════════════════════════════════════

  ;   ,   (   )

═══════════════════════════════════════════════════════════════
  IDENTIFICADORES Y LITERALES
═══════════════════════════════════════════════════════════════

  ID:       [letra_][letra|dígito|_]*
            (no puede ser palabra reservada)

  NÚMERO:   [0-9]+(.[0-9]+)?
            ej: 220  0.1  5

═══════════════════════════════════════════════════════════════
  COMENTARIOS
═══════════════════════════════════════════════════════════════

  //  comentario de línea
  /*  comentario de bloque  */
```
