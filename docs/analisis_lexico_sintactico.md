# Análisis Léxico y Sintáctico — Lenguaje Timotomata

## Índice

1. [Analizador Léxico (AFD)](#1-analizador-léxico-afd)
   - [Alfabeto](#11-alfabeto-clases-de-caracteres)
   - [Estados](#12-estados-del-afd)
   - [Tabla de Transiciones](#13-tabla-de-transiciones)
   - [Diagrama de Transiciones](#14-diagrama-de-transiciones)
   - [Algoritmo Maximal Munch](#15-algoritmo-maximal-munch)
   - [Emisión de Tokens](#16-emisión-de-tokens)
   - [Manejo de Errores Léxicos](#17-manejo-de-errores-léxicos)
2. [Analizador Sintáctico (Gramática LL(1))](#2-analizador-sintáctico-gramática-ll1)
   - [Gramática en BNF](#21-gramática-en-bnf)
   - [Las dos formas de transformar en `calcular`](#las-dos-formas-de-transformar-en-calcular)
   - [Símbolos](#22-símbolos)
   - [Producciones](#23-producciones)
   - [Conjuntos FIRST y FOLLOW](#24-conjuntos-first-y-follow)
   - [Tabla de Parsing LL(1)](#25-tabla-de-parsing-ll1)
   - [Algoritmo de Parsing](#26-algoritmo-de-parsing)

> La tercera fase, el **análisis semántico**, tiene su propio documento:
> [`analisis_semantico.md`](analisis_semantico.md).

---

# 1. Analizador Léxico (AFD)

El analizador léxico está implementado como un **Autómata Finito Determinista (AFD)** con **13 estados** y **19 clases de caracteres** en su alfabeto.

## 1.1 Alfabeto (clases de caracteres)

El alfabeto de entrada se divide en **19 clases**. Cada carácter se clasifica en una única clase, y la transición se determina por `TABLA_TRANS[estado][clase]`.

| # | Clase | Caracteres que incluye | Símbolo en código |
|---|-------|----------------------|-------------------|
| 0 | `LETRA` | `a-z`, `A-Z`, `_` | Caracteres alfabéticos y guion bajo |
| 1 | `DIGITO` | `0-9` | Dígitos decimales |
| 2 | `PUNTO` | `.` | Punto decimal |
| 3 | `ESP` | `' '`, `'\t'`, `'\r'` | Espacios, tabuladores, retorno de carro |
| 4 | `NL` | `'\n'` | Salto de línea |
| 5 | `MAS` | `+` | Suma |
| 6 | `MENOS` | `-` | Resta / Negación |
| 7 | `POR` | `*` | Multiplicación / Cierre de comentario `*/` |
| 8 | `DIV` | `/` | División / Inicio de comentario `//` o `/*` |
| 9 | `IGUAL` | `=` | Asignación / `==` |
| 10 | `MAYOR_` | `>` | Mayor que / `>=` |
| 11 | `MENOR_` | `<` | Menor que / `<=` |
| 12 | `EXCL` | `!` | Negación / `!=` |
| 13 | `PUNTOCOMA` | `;` | Fin de sentencia |
| 14 | `PIZQ` | `(` | Paréntesis izquierdo |
| 15 | `PDER` | `)` | Paréntesis derecho |
| 16 | `COMA_` | `,` | Separador de argumentos |
| 17 | `DOSPUNTOS` | `:` | Declarador de tipo de sensor |
| 18 | `OTRO` | cualquier otro | Caracteres no válidos (`@`, `#`, `$`, etc.) |

## 1.2 Estados del AFD

| # | Estado | Descripción | ¿Aceptación? |
|---|--------|-------------|:------------:|
| 0 | `Q0` | Estado **inicial**. Desde aquí se clasifica el primer carácter. También se usa como destino de tokens completados. | ❌ |
| 1 | `Q_ID` | Leyendo un **identificador** o **palabra reservada** (`sensor`, `umbral`, `voltaje`, etc.) | ✅ |
| 2 | `Q_NUM` | Leyendo la **parte entera** de un número (`100`, `3`) | ✅ |
| 3 | `Q_NUM_PUNTO` | Acabamos de ver un `.` en un número; esperando la parte decimal | ❌ |
| 4 | `Q_NUM_DEC` | Leyendo la **parte decimal** de un número (`3.14`) | ✅ |
| 5 | `Q_EQ` | Vimos `=`, esperando ver si es `==` o solo `=` | ❌ |
| 6 | `Q_GT` | Vimos `>`, esperando ver si es `>=` o solo `>` | ❌ |
| 7 | `Q_LT` | Vimos `<`, esperando ver si es `<=`, `<>` o solo `<` | ❌ |
| 8 | `Q_NOT` | Vimos `!`, esperando ver si es `!=` | ❌ |
| 9 | `Q_DIV` | Vimos `/`, esperando ver si es `/`, `//` o `/*` | ❌ |
| 10 | `Q_COM_LINEA` | Dentro de un comentario de línea (`// ...`) | ❌ |
| 11 | `Q_COM_BLOQ` | Dentro de un comentario de bloque (`/* ... */`) | ❌ |
| 12 | `Q_COM_BLOQ_FIN` | Vimos `*` dentro de `/*`, esperando `/` para cerrar | ❌ |

**Total: 13 estados**, de los cuales **3 son de aceptación**: `Q_ID`, `Q_NUM`, `Q_NUM_DEC`.

## 1.3 Tabla de Transiciones

La función de transición `δ: Q × Σ → Q` se define mediante `TABLA_TRANS[estado][clase]`. Una entrada con valor `-1` (`SIN_TRANS`) significa que no hay transición definida (estado muerto).

A continuación, la tabla completa. Las celdas vacías indican `SIN_TRANS` (transición no definida).

| Desde ↓ \ Clase → | LETRA | DIGITO | PUNTO | ESP | NL | MAS | MENOS | POR | DIV | IGUAL | MAYOR\_ | MENOR\_ | EXCL | PTOCOMA | PIZQ | PDER | OTRO |
|:-----------------:|:-----:|:------:|:-----:|:---:|:--:|:---:|:-----:|:---:|:---:|:-----:|:-------:|:-------:|:----:|:-------:|:----:|:----:|:----:|
| **Q0** (inicial) | Q_ID | Q_NUM | | Q0 | Q0 | Q0 | Q0 | Q0 | Q_DIV | Q_EQ | Q_GT | Q_LT | Q_NOT | Q0 | Q0 | Q0 | |
| **Q_ID** | Q_ID | Q_ID | | | | | | | | | | | | | | | |
| **Q_NUM** | | Q_NUM | Q_NUM_PUNTO | | | | | | | | | | | | | | |
| **Q_NUM_PUNTO** | | | | | | | | | | | | | | | | | |
| **Q_NUM_DEC** | | Q_NUM_DEC | | | | | | | | | | | | | | | |
| **Q_EQ** | | | | | | | | | | Q0 | | | | | | | |
| **Q_GT** | | | | | | | | | | Q0 | | | | | | | |
| **Q_LT** | | | | | | | | | | Q0 | Q0 | | | | | | |
| **Q_NOT** | | | | | | | | | | Q0 | | | | | | | |
| **Q_DIV** | | | | | | | | | Q_COM_LINEA | | | | | | | | |
| | | | | | | | | POR → Q_COM_BLOQ | | | | | | | | | |
| **Q_COM_LINEA** | Q_COM_LINEA | Q_COM_LINEA | Q_COM_LINEA | Q_COM_LINEA | **Q0** | Q_COM_LINEA | Q_COM_LINEA | Q_COM_LINEA | Q_COM_LINEA | Q_COM_LINEA | Q_COM_LINEA | Q_COM_LINEA | Q_COM_LINEA | Q_COM_LINEA | Q_COM_LINEA | Q_COM_LINEA | Q_COM_LINEA |
| **Q_COM_BLOQ** | Q_COM_BLOQ | Q_COM_BLOQ | Q_COM_BLOQ | Q_COM_BLOQ | Q_COM_BLOQ | Q_COM_BLOQ | Q_COM_BLOQ | **Q_COM_BLOQ_FIN** | Q_COM_BLOQ | Q_COM_BLOQ | Q_COM_BLOQ | Q_COM_BLOQ | Q_COM_BLOQ | Q_COM_BLOQ | Q_COM_BLOQ | Q_COM_BLOQ | Q_COM_BLOQ |
| **Q_COM_BLOQ_FIN** | Q_COM_BLOQ | Q_COM_BLOQ | Q_COM_BLOQ | Q_COM_BLOQ | Q_COM_BLOQ | Q_COM_BLOQ | Q_COM_BLOQ | **Q_COM_BLOQ_FIN** | **Q0** | Q_COM_BLOQ | Q_COM_BLOQ | Q_COM_BLOQ | Q_COM_BLOQ | Q_COM_BLOQ | Q_COM_BLOQ | Q_COM_BLOQ | Q_COM_BLOQ |

### Transiciones destacables

- **Q0 → Q_DIV con DIV**: Al ver `/`, se pasa a `Q_DIV` porque podría ser:
  - `/` → división (retrocede 1 carácter y emite DIV)
  - `//` → comentario de línea (todo hasta `\n`)
  - `/*` → comentario de bloque (todo hasta `*/`)

- **Q_DIV → Q_COM_LINEA con DIV**: `//` inicia comentario de línea
- **Q_DIV → Q_COM_BLOQ con POR**: `/*` inicia comentario de bloque

- **Q_COM_BLOQ_FIN → Q0 con DIV**: `*/` cierra comentario de bloque
- **Q_COM_BLOQ_FIN → Q_COM_BLOQ_FIN con POR**: `**` dentro de `/*` — se mantiene esperando `/`

- **Q_EQ → Q0 con IGUAL**: `==` es el token IGUAL_IGUAL
- **Q_GT → Q0 con IGUAL**: `>=` es MAYOR_IGUAL
- **Q_LT → Q0 con IGUAL**: `<=` es MENOR_IGUAL
- **Q_LT → Q0 con MAYOR\_**: `<>` es DIFERENTE
- **Q_NOT → Q0 con IGUAL**: `!=` es DIFERENTE

## 1.4 Diagrama de Transiciones

```
                    ┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
                    │                                                  LETRA, DIGITO                                    │
                    │                                                                                                  ▼
                    │                           ┌─────────────────────────────────────────────────┐               ╔═════════╗
                    │                           │       cualquier clase excepto NL                │               ║ Q_ID    ║
                    │                           ▼                                                 │               ║ (acept.)║
                    │                    ╔════════════╗     NL      ╔══════════════╗               │               ╚═════════╝
                    │                    ║ Q_COM_LINEA║───────────►║     Q0      ║               │                    │
                    │                    ╚════════════╝            ╚══════════════╝               │                    │ LETRA, DIGITO
                    │                         ▲                      │  ▲   ▲   ▲   ▲   ▲         │                    │
                    │                         │ DIV                  │  │   │   │   │   │         │                    ▼
                    │                         │                      │  │   │   │   │   │         │              ┌──────────┐
                    │                    ╔════════╗    ┌─────────┐   │  │   │   │   │   │         │              │          │
      ┌─────────┐   DIV (//)            ║ Q_DIV  ║    │ Q_EQ    │───┘  │   │   │   │   │         │              │  Q_NUM   │───► DIGITO
      │         │◄──────────────────────║        ║    ╚═════════╝      │   │   │   │   │         │              │ (acept.) │
      │  OTRO   │                       ╚════════╝                     │   │   │   │   │         │              └──────────┘
      │ (error) │                           │ POR (/*)                 │   │   │   │   │         │                   │
      └─────────┘                           ▼                          │   │   │   │   │         │                   │ PUNTO
                                            ╔══════════════╗           │   │   │   │   │         │                   ▼
      ┌─────────┐    ESP, NL               ║ Q_COM_BLOQ  ║           │   │   │   │   │         │              ┌────────────┐
      │         │◄─────────────────────────║              ║           │   │   │   │   │         │              │ Q_NUM_PUNTO│
      │   Q0    │                           ╚══════════════╝           │   │   │   │   │         │              └────────────┘
      │ (inicial)│                              │  POR (*)            │   │   │   │   │         │                   │
      └─────────┘                              ▼                      │   │   │   │   │         │                   │ DIGITO
       │  ▲  ▲  ▲  ▲  ▲  ▲  ▲  ▲     ╔══════════════╗   DIV (/)      │   │   │   │   │         │                   ▼
       │  │  │  │  │  │  │  │  │     ║Q_COM_BLOQ_FIN║───────────────►│   │   │   │   │         │              ┌────────────┐
       │  │  │  │  │  │  │  │  │     ╚══════════════╝                │   │   │   │   │         │              │ Q_NUM_DEC  │───► DIGITO
       │  │  │  │  │  │  │  │  │          │  │                       │   │   │   │   │         │              │ (acept.)   │
       │  │  │  │  │  │  │  │  │          │  └── POR (**) ──► (loop)│   │   │   │   │         │              └────────────┘
       │  │  │  │  │  │  │  │  │          │                          │   │   │   │   │         │
       │  │  │  │  │  │  │  │  │          └─ cualquier otra clase ──►│   │   │   │   │         │
       │  │  │  │  │  │  │  │  │                                     │   │   │   │   │         │
       │  │  │  │  │  │  │  │  │          ┌─────────┐    ┌─────────┐ │   │   │   │   │         │
  MAS, MENOS, POR, PUNTOCOMA,              │Q_GT     │    │ Q_LT    │─┘   │   │   │   │
  PIZQ, PDER (tokens directos)             ╚═════════╝    ╚═════════╝     │   │   │   │
       │  │  │  │  │  │                                       IGUAL      │   │   │   │
       │  │  │  │  │  │                                        y         │   │   │   │
       │  │  │  │  │  │                                      MAYOR_      │   │   │   │
       │  │  │  │  │  │                                        │         │   │   │   │
       │  │  │  │  │  │                                  ╔══════════════╗│   │   │   │
       │  │  │  │  │  │                                  ║   Q_NOT      ║┘   │   │   │
       │  │  │  │  │  │                                  ╚══════════════╝    │   │   │
       │  │  │  │  │  │                                        │ IGUAL      │   │   │
       │  │  │  │  │  │                                        ▼            │   │   │
       │  │  │  │  │  │                                    ═══════         │   │   │
       │  │  │  │  │  │                                   ║  !=   ║        │   │   │
       │  │  │  │  │  │                                   ║ DIF.  ║        │   │   │
       │  │  │  │  │  │                                   ═══════         │   │   │
       │  │  │  │  │  │                                                    │   │   │
  IGUAL (=) │  │  │  │  │            IGUAL (==) ◄─────────────────────────┘   │   │
       │    │  │  │  │  │            IGUAL (>=) ◄─────────────────────────────┘   │
       │    │  │  │  │  │            IGUAL (<=) ◄─────────────────────────────────┘
       │    │  │  │  │  │            MAYOR_ (<>) ◄────────────────────────────────┘
       │    │  │  │  │  │
       │    │  │  │  │  └── LETRA ──► Q_ID (palabras reservadas: sensor, umbral, si, ...)
       │    │  │  │  └───── DIGITO ──► Q_NUM
       │    │  │  └──────── . ────────► Q_NUM_PUNTO
       │    │  └─────────── / ─────────► Q_DIV
       │    └────────────── = ─────────► Q_EQ
       └─────────────────── ! ─────────► Q_NOT
```

## 1.5 Algoritmo Maximal Munch

El autómata implementa la política **Maximal Munch** (la coincidencia más larga posible):

```
1. Arrancar en Q0, marcar inicio del lexema.
2. Mientras haya transición definida:
   a. Consumir el carácter, seguir al siguiente estado.
   b. Si el nuevo estado es de aceptación, recordar (estado, posición).
   c. Si el nuevo estado es Q0 y vino de otro estado, es token compuesto (==, >=, etc.).
   d. Si el nuevo estado es Q0 y solo consumió 1 carácter, es token directo (+, ;, etc.).
3. Si no hay transición:
   a. Si hay un último estado de aceptación → emitir ese token, retroceder.
   b. Si no → error léxico (carácter no válido).
```

## 1.6 Emisión de Tokens

El lexer produce tokens a través de cuatro mecanismos distintos:

### Token directo (1 carácter desde Q0 → Q0)
Cuando desde Q0 se consume un carácter que lleva de vuelta a Q0 en un solo paso.

| Clase | Token | Lexema |
|:-----:|-------|:------:|
| `MAS` | `MAS` | `+` |
| `MENOS` | `MENOS` | `-` |
| `POR` | `POR` | `*` |
| `PUNTOCOMA` | `PUNTO_COMA` | `;` |
| `PIZQ` | `PAREN_IZQ` | `(` |
| `PDER` | `PAREN_DER` | `)` |
| `ESP`, `NL` | *(se ignora)* | |

### Token compuesto (2 caracteres)
Cuando desde un estado intermedio (Q_EQ, Q_GT, Q_LT, Q_NOT) se ve un segundo carácter que completa un operador de 2 caracteres.

| Estado origen | Clase vista | Token | Lexema |
|:-------------:|:-----------:|-------|:------:|
| `Q_EQ` | `IGUAL` | `IGUAL_IGUAL` | `==` |
| `Q_GT` | `IGUAL` | `MAYOR_IGUAL` | `>=` |
| `Q_LT` | `IGUAL` | `MENOR_IGUAL` | `<=` |
| `Q_LT` | `MAYOR_` | `DIFERENTE` | `<>` |
| `Q_NOT` | `IGUAL` | `DIFERENTE` | `!=` |

### Token unario (1 carácter desde estado intermedio)
Cuando desde Q_EQ, Q_GT, Q_LT, Q_DIV el siguiente carácter NO completa un operador compuesto. Se retrocede 1 posición y se emite el token de 1 carácter.

| Estado origen | Token | Lexema |
|:-------------:|-------|:------:|
| `Q_EQ` | `ASIGNACION` | `=` |
| `Q_GT` | `MAYOR` | `>` |
| `Q_LT` | `MENOR` | `<` |
| `Q_DIV` | `DIV` | `/` |
| `Q_NOT` | *(error léxico)* | `!` debe ir seguido de `=` |

### Identificadores, palabras reservadas y números
Cuando el autómata termina en un estado de aceptación:

| Estado de aceptación | Token generado |
|:-------------------:|----------------|
| `Q_ID` | Según el switch de `emitirToken()` (ver abajo) |
| `Q_NUM` | `NUMERO` |
| `Q_NUM_DEC` | `NUMERO` |

#### Resolución de `Q_ID`

El lexema se convierte a minúsculas para la comparación (case-insensitive), pero el token conserva el lexema original:

| Lexema (minúsculas) | Token |
|:-------------------:|-------|
| `sensor` | `SENSOR` |
| `umbral` | `UMBRAL` |
| `si` | `SI` |
| `entonces` | `ENTONCES` |
| `estado` | `ESTADO` |
| `abs` | `ABS` |
| `normal`, `pico`, `caida`, `inestable` | `ESTADO_SISTEMA` |
| cualquier otro | `ID` |

## 1.7 Manejo de Errores Léxicos

Los errores se **acumulan** en una lista separada y no detienen el análisis. Se muestran todos al final antes que los tokens.

| Situación | Mensaje de error |
|-----------|-----------------|
| Carácter sin transición desde Q0 (`@`, `#`, `$`, etc.) | `caracter no válido '@'` |
| Segundo punto decimal (`3.14.15`) | `caracter no válido '.'` |
| `!` sin `=` después | `'!' debe ir seguido de '=' para formar !=` |
| `/*` sin `*/` de cierre | `comentario de bloque no cerrado` |

---

# 2. Analizador Sintáctico (Gramática LL(1))

El analizador sintáctico es un **parser recursivo descendente basado en una tabla LL(1)**. La gramática se define explícitamente como estructura de datos en la clase `Gramatica.java`, que computa los conjuntos FIRST, FOLLOW y la tabla de parsing automáticamente.

## 2.1 Gramática en BNF

```
PROGRAMA      → SENTENCIA PROGRAMA
              | ε

SENTENCIA     → SENSOR ID DOS_PUNTOS TIPO_MAGNITUD PUNTO_COMA
              | UMBRAL ID DOS_PUNTOS TIPO_MAGNITUD ASIGNACION VALOR_UMBRAL PUNTO_COMA
              | SI CONDICION ENTONCES ESTADO ASIGNACION ESTADO_SISTEMA PUNTO_COMA
              | CALCULAR PAREN_IZQ ID COMA TIPO_OP DESTINO_OP PAREN_DER PUNTO_COMA
              | ESTADO ASIGNACION ESTADO_SISTEMA PUNTO_COMA
              | SINO ASIGNACION ESTADO_SISTEMA PUNTO_COMA
              | MUESTREO ASIGNACION NUMERO PUNTO_COMA

TIPO_MAGNITUD → ELECTRICO | TERMICO | LUMINICO
              | MECANICO | QUIMICO | HIDRAULICO

DESTINO_OP    → COMA ID
              | ε

VALOR_UMBRAL  → MENOS NUMERO
              | NUMERO

CONDICION     → EXPRESION OP_REL EXPRESION

EXPRESION     → TERMINO EXPRESION_SIG
EXPRESION_SIG → MAS TERMINO EXPRESION_SIG
              | MENOS TERMINO EXPRESION_SIG
              | ε

TERMINO       → FACTOR TERMINO_SIG
TERMINO_SIG   → POR FACTOR TERMINO_SIG
              | DIV FACTOR TERMINO_SIG
              | ε

FACTOR        → NUMERO
              | ID
              | ABS PAREN_IZQ EXPRESION PAREN_DER
              | MENOS FACTOR
              | PAREN_IZQ EXPRESION PAREN_DER

TIPO_OP       → SENO PAREN_IZQ LISTA_PARAMS PAREN_DER
              | COSENO PAREN_IZQ LISTA_PARAMS PAREN_DER
              | CUADRADA PAREN_IZQ LISTA_PARAMS PAREN_DER
              | PROMEDIO PAREN_IZQ LISTA_PARAMS PAREN_DER
              | MAXIMO PAREN_IZQ LISTA_PARAMS PAREN_DER
              | SUMA PAREN_IZQ LISTA_PARAMS PAREN_DER
              | EXPRESION

OP_REL        → MAYOR | MENOR | IGUAL_IGUAL
              | MAYOR_IGUAL | MENOR_IGUAL | DIFERENTE
```

### Las dos formas de transformar en `calcular`

`TIPO_OP` admite una operación nombrada de las seis, o una expresión arbitraria:

```timotomata
calcular(voltaje, PROMEDIO(VENTANA=10), voltaje_prom);   // operación nombrada
calcular(voltaje, voltaje * 2, voltaje_x2);              // expresión arbitraria
```

La segunda forma **sustituye a las variables**: da la misma expresividad sin
introducir almacenamiento mutable, ámbito ni una cuarta clase de símbolo.

Las dos familias se distinguen con un solo token de anticipo —las operaciones
empiezan por su palabra reservada (`SENO`, `COSENO`, `CUADRADA`, `PROMEDIO`,
`MAXIMO`, `SUMA`) y las expresiones por `ABS`, `ID`, `NUMERO`, `MENOS` o
`PAREN_IZQ`—, así que `Parser.tipoOp()` decide sin retroceder.

La producción 53 agrupa una subexpresión sin producir un nodo propio del
AST: `(a + b) / 2` es exactamente `Binaria(Binaria(a,+,b), /, 2)`. El agrupamiento
solo cambia el orden de construcción, que el parser ya respeta por precedencia.

### Propiedades de la gramática

- **Factorizada por la izquierda**: No hay producciones con un prefijo común para el mismo no-terminal.
- **Sin recursión izquierda directa**: `EXPRESION` no deriva en `EXPRESION + ...` — usa `EXPRESION_SIG` para manejar la recursión por la derecha.
- **LL(1)**: Es posible determinar qué producción usar con solo mirar el siguiente token (lookahead). La propiedad no se supone: `Gramatica.construirTabla()` detecta las celdas donde dos producciones del mismo no terminal reclaman el mismo token y las registra en `Gramatica.conflictos()`, que hoy está vacío.

## 2.2 Símbolos

### No terminales (16) - 54 producciones

| ID | Nombre | Significado |
|:--:|--------|-------------|
| 0 | `PROGRAMA` | Programa completo (lista de sentencias) |
| 1 | `SENTENCIA` | Una sentencia del lenguaje |
| 2 | `VALOR_UMBRAL` | Valor numérico de un umbral (con signo opcional) |
| 3 | `CONDICION` | Condición de una regla (expresión operador expresión) |
| 4 | `EXPRESION` | Expresión aritmética |
| 5 | `EXPRESION_SIG` | Continuación de expresión (recursión derecha para + y -) |
| 6 | `TERMINO` | Término en una expresión (multiplicación/división) |
| 7 | `TERMINO_SIG` | Continuación de término (recursión derecha para * y /) |
| 8 | `FACTOR` | Unidad básica de expresión (número, variable, abs, negación) |
| 9 | `OP_REL` | Operador relacional |
| 10 | `TIPO_OP` | Operación de cálculo (seno, promedio, suma…) |
| 11 | `LISTA_PARAMS` | Lista de parámetros de la operación |
| 12 | `PARAM` | Un parámetro con nombre |
| 13 | `LISTA_PARAMS_SIG` | Continuación de la lista de parámetros |
| 14 | `TIPO_MAGNITUD` | Tipo de sensor declarado |
| 15 | `DESTINO_OP` | Señal de destino del cálculo, o ε |

### Terminales (45 tokens)

| Token | Cómo se escribe |
|-------|:---------------:|
| `SENSOR` | `sensor` |
| `UMBRAL` | `umbral` |
| `SI` | `si` |
| `ENTONCES` | `entonces` |
| `ESTADO` | `estado` |
| `SINO` | `sino` |
| `MUESTREO` | `muestreo` |
| `DOS_PUNTOS` | `:` |
| `ABS` | `abs` |
| `ID` | cualquier identificador |
| `NUMERO` | cualquier número |
| `ESTADO_SISTEMA` | `normal`, `pico`, `caida`, `inestable` |
| `CALCULAR` | `calcular` |
| `SENO` `COSENO` `CUADRADA` `PROMEDIO` `MAXIMO` `SUMA` | nombres de operación |
| `AMPLITUD` `FRECUENCIA` `VENTANA` `CON` | nombres de parámetro |
| `ELECTRICO` `TERMICO` `LUMINICO` `MECANICO` `QUIMICO` `HIDRAULICO` | tipos de sensor |
| `MAYOR` | `>` |
| `MENOR` | `<` |
| `IGUAL_IGUAL` | `==` |
| `MAYOR_IGUAL` | `>=` |
| `MENOR_IGUAL` | `<=` |
| `DIFERENTE` | `!=` |
| `MAS` | `+` |
| `MENOS` | `-` |
| `POR` | `*` |
| `DIV` | `/` |
| `ASIGNACION` | `=` |
| `PUNTO_COMA` | `;` |
| `COMA` | `,` |
| `PAREN_IZQ` | `(` |
| `PAREN_DER` | `)` |
| `DOS_PUNTOS` | `:` |
| `EOF` | fin de archivo |

## 2.3 Producciones

| No | Produccion |
|:--:|------------|
| 0 | `PROGRAMA → SENTENCIA PROGRAMA` |
| 1 | `PROGRAMA → ε` |
| 2 | `SENTENCIA → SENSOR ID DOS_PUNTOS TIPO_MAGNITUD PUNTO_COMA` |
| 3 | `SENTENCIA → UMBRAL ID DOS_PUNTOS TIPO_MAGNITUD ASIGNACION VALOR_UMBRAL PUNTO_COMA` |
| 4 | `SENTENCIA → SI CONDICION ENTONCES ESTADO ASIGNACION ESTADO_SISTEMA PUNTO_COMA` |
| 5 | `SENTENCIA → CALCULAR PAREN_IZQ ID COMA TIPO_OP DESTINO_OP PAREN_DER PUNTO_COMA` |
| 6 | `VALOR_UMBRAL → MENOS NUMERO` |
| 7 | `VALOR_UMBRAL → NUMERO` |
| 8 | `CONDICION → EXPRESION OP_REL EXPRESION` |
| 9 | `EXPRESION → TERMINO EXPRESION_SIG` |
| 10 | `EXPRESION_SIG → MAS TERMINO EXPRESION_SIG` |
| 11 | `EXPRESION_SIG → MENOS TERMINO EXPRESION_SIG` |
| 12 | `EXPRESION_SIG → ε` |
| 13 | `TERMINO → FACTOR TERMINO_SIG` |
| 14 | `TERMINO_SIG → POR FACTOR TERMINO_SIG` |
| 15 | `TERMINO_SIG → DIV FACTOR TERMINO_SIG` |
| 16 | `TERMINO_SIG → ε` |
| 17 | `FACTOR → NUMERO` |
| 18 | `FACTOR → ID` |
| 19 | `FACTOR → ABS PAREN_IZQ EXPRESION PAREN_DER` |
| 20 | `FACTOR → MENOS FACTOR` |
| 21 | `OP_REL → MAYOR` |
| 22 | `OP_REL → MENOR` |
| 23 | `OP_REL → IGUAL_IGUAL` |
| 24 | `OP_REL → MAYOR_IGUAL` |
| 25 | `OP_REL → MENOR_IGUAL` |
| 26 | `OP_REL → DIFERENTE` |
| 27 | `TIPO_OP → SENO PAREN_IZQ LISTA_PARAMS PAREN_DER` |
| 28 | `TIPO_OP → COSENO PAREN_IZQ LISTA_PARAMS PAREN_DER` |
| 29 | `TIPO_OP → CUADRADA PAREN_IZQ LISTA_PARAMS PAREN_DER` |
| 30 | `TIPO_OP → PROMEDIO PAREN_IZQ LISTA_PARAMS PAREN_DER` |
| 31 | `TIPO_OP → MAXIMO PAREN_IZQ LISTA_PARAMS PAREN_DER` |
| 32 | `TIPO_OP → SUMA PAREN_IZQ LISTA_PARAMS PAREN_DER` |
| 33 | `LISTA_PARAMS → PARAM LISTA_PARAMS_SIG` |
| 34 | `LISTA_PARAMS → ε` |
| 35 | `LISTA_PARAMS_SIG → COMA LISTA_PARAMS` |
| 36 | `LISTA_PARAMS_SIG → ε` |
| 37 | `PARAM → AMPLITUD ASIGNACION NUMERO` |
| 38 | `PARAM → FRECUENCIA ASIGNACION NUMERO` |
| 39 | `PARAM → VENTANA ASIGNACION NUMERO` |
| 40 | `PARAM → CON ASIGNACION ID` |
| 41 | `TIPO_MAGNITUD → ELECTRICO` |
| 42 | `TIPO_MAGNITUD → TERMICO` |
| 43 | `TIPO_MAGNITUD → LUMINICO` |
| 44 | `TIPO_MAGNITUD → MECANICO` |
| 45 | `TIPO_MAGNITUD → QUIMICO` |
| 46 | `TIPO_MAGNITUD → HIDRAULICO` |
| 47 | `DESTINO_OP → COMA ID` |
| 48 | `DESTINO_OP → ε` |
| 49 | `SENTENCIA → ESTADO ASIGNACION ESTADO_SISTEMA PUNTO_COMA` |
| 50 | `SENTENCIA → MUESTREO ASIGNACION NUMERO PUNTO_COMA` |
| 51 | `SENTENCIA → SINO ASIGNACION ESTADO_SISTEMA PUNTO_COMA` |
| 52 | `TIPO_OP → EXPRESION` |
| 53 | `FACTOR → PAREN_IZQ EXPRESION PAREN_DER` |


| ID | Producción |
|:--:|------------|
| 0 | `PROGRAMA → SENTENCIA PROGRAMA` |
| 1 | `PROGRAMA → ε` |
| 2 | `SENTENCIA → SENSOR ID DOS_PUNTOS TIPO_MAGNITUD PUNTO_COMA` |
| 3 | `SENTENCIA → UMBRAL ID DOS_PUNTOS TIPO_MAGNITUD ASIGNACION VALOR_UMBRAL PUNTO_COMA` |
| 4 | `SENTENCIA → SI CONDICION ENTONCES ESTADO ASIGNACION ESTADO_SISTEMA PUNTO_COMA` |
| 5 | `SENTENCIA → CALCULAR PAREN_IZQ ID COMA TIPO_OP DESTINO_OP PAREN_DER PUNTO_COMA` |
| 6 | `VALOR_UMBRAL → MENOS NUMERO` |
| 7 | `VALOR_UMBRAL → NUMERO` |
| 8 | `CONDICION → EXPRESION OP_REL EXPRESION` |
| 9 | `EXPRESION → TERMINO EXPRESION_SIG` |
| 10 | `EXPRESION_SIG → MAS TERMINO EXPRESION_SIG` |
| 11 | `EXPRESION_SIG → MENOS TERMINO EXPRESION_SIG` |
| 12 | `EXPRESION_SIG → ε` |
| 13 | `TERMINO → FACTOR TERMINO_SIG` |
| 14 | `TERMINO_SIG → POR FACTOR TERMINO_SIG` |
| 15 | `TERMINO_SIG → DIV FACTOR TERMINO_SIG` |
| 16 | `TERMINO_SIG → ε` |
| 17 | `FACTOR → NUMERO` |
| 18 | `FACTOR → ID` |
| 19 | `FACTOR → ABS PAREN_IZQ EXPRESION PAREN_DER` |
| 20 | `FACTOR → MENOS FACTOR` |
| 21 | `OP_REL → MAYOR` |
| 22 | `OP_REL → MENOR` |
| 23 | `OP_REL → IGUAL_IGUAL` |
| 24 | `OP_REL → MAYOR_IGUAL` |
| 25 | `OP_REL → MENOR_IGUAL` |
| 26 | `OP_REL → DIFERENTE` |
| 27 | `TIPO_OP → SENO PAREN_IZQ LISTA_PARAMS PAREN_DER` |
| 28 | `TIPO_OP → COSENO PAREN_IZQ LISTA_PARAMS PAREN_DER` |
| 29 | `TIPO_OP → CUADRADA PAREN_IZQ LISTA_PARAMS PAREN_DER` |
| 30 | `TIPO_OP → PROMEDIO PAREN_IZQ LISTA_PARAMS PAREN_DER` |
| 31 | `TIPO_OP → MAXIMO PAREN_IZQ LISTA_PARAMS PAREN_DER` |
| 32 | `TIPO_OP → SUMA PAREN_IZQ LISTA_PARAMS PAREN_DER` |
| 33 | `LISTA_PARAMS → PARAM LISTA_PARAMS_SIG` |
| 34 | `LISTA_PARAMS → ε` |
| 35 | `LISTA_PARAMS_SIG → COMA LISTA_PARAMS` |
| 36 | `LISTA_PARAMS_SIG → ε` |
| 37 | `PARAM → AMPLITUD ASIGNACION NUMERO` |
| 38 | `PARAM → FRECUENCIA ASIGNACION NUMERO` |
| 39 | `PARAM → VENTANA ASIGNACION NUMERO` |
| 40 | `PARAM → CON ASIGNACION ID` |
| 41 | `TIPO_MAGNITUD → ELECTRICO` |
| 42 | `TIPO_MAGNITUD → TERMICO` |
| 43 | `TIPO_MAGNITUD → LUMINICO` |
| 44 | `TIPO_MAGNITUD → MECANICO` |
| 45 | `TIPO_MAGNITUD → QUIMICO` |
| 46 | `TIPO_MAGNITUD → HIDRAULICO` |
| 47 | `DESTINO_OP → COMA ID` |
| 48 | `DESTINO_OP → ε` |
| 49 | `SENTENCIA → ESTADO ASIGNACION ESTADO_SISTEMA PUNTO_COMA` |
| 50 | `SENTENCIA → MUESTREO ASIGNACION NUMERO PUNTO_COMA` |
| 51 | `SENTENCIA → SINO ASIGNACION ESTADO_SISTEMA PUNTO_COMA` |
| 52 | `TIPO_OP → EXPRESION` |
| 53 | `FACTOR → PAREN_IZQ EXPRESION PAREN_DER` |

> Generada desde `Gramatica.PRODUCCIONES`; los identificadores coinciden con
> las constantes `P_*` del código.

## 2.4 Conjuntos FIRST y FOLLOW

FIRST contiene los terminales que pueden iniciar una derivación; FOLLOW, los
que pueden aparecer inmediatamente después.

| No terminal | FIRST | epsilon | FOLLOW |
|:------------:|:------|:-------:|:-------|
| `PROGRAMA` | SENSOR, UMBRAL, SI, ESTADO, CALCULAR, SINO, MUESTREO | si | EOF |
| `SENTENCIA` | SENSOR, UMBRAL, SI, ESTADO, CALCULAR, SINO, MUESTREO | no | SENSOR, UMBRAL, SI, ESTADO, CALCULAR, SINO, MUESTREO, EOF |
| `VALOR_UMBRAL` | NUMERO, MENOS | no | PUNTO_COMA |
| `CONDICION` | ABS, ID, NUMERO, MENOS, PAREN_IZQ | no | ENTONCES |
| `EXPRESION` | ABS, ID, NUMERO, MENOS, PAREN_IZQ | no | ENTONCES, MAYOR, MENOR, IGUAL_IGUAL, MAYOR_IGUAL, MENOR_IGUAL, DIFERENTE, PAREN_DER, COMA |
| `EXPRESION_SIG` | MAS, MENOS | si | ENTONCES, MAYOR, MENOR, IGUAL_IGUAL, MAYOR_IGUAL, MENOR_IGUAL, DIFERENTE, PAREN_DER, COMA |
| `TERMINO` | ABS, ID, NUMERO, MENOS, PAREN_IZQ | no | ENTONCES, MAYOR, MENOR, IGUAL_IGUAL, MAYOR_IGUAL, MENOR_IGUAL, DIFERENTE, MAS, MENOS, PAREN_DER, COMA |
| `TERMINO_SIG` | POR, DIV | si | ENTONCES, MAYOR, MENOR, IGUAL_IGUAL, MAYOR_IGUAL, MENOR_IGUAL, DIFERENTE, MAS, MENOS, PAREN_DER, COMA |
| `FACTOR` | ABS, ID, NUMERO, MENOS, PAREN_IZQ | no | ENTONCES, MAYOR, MENOR, IGUAL_IGUAL, MAYOR_IGUAL, MENOR_IGUAL, DIFERENTE, MAS, MENOS, POR, DIV, PAREN_DER, COMA |
| `OP_REL` | MAYOR, MENOR, IGUAL_IGUAL, MAYOR_IGUAL, MENOR_IGUAL, DIFERENTE | no | ABS, ID, NUMERO, MENOS, PAREN_IZQ |
| `TIPO_OP` | ABS, ID, NUMERO, MENOS, PAREN_IZQ, SENO, COSENO, CUADRADA, PROMEDIO, MAXIMO, SUMA | no | PAREN_DER, COMA |
| `LISTA_PARAMS` | AMPLITUD, FRECUENCIA, VENTANA, CON | si | PAREN_DER |
| `PARAM` | AMPLITUD, FRECUENCIA, VENTANA, CON | no | PAREN_DER, COMA |
| `LISTA_PARAMS_SIG` | COMA | si | PAREN_DER |
| `TIPO_MAGNITUD` | ELECTRICO, TERMICO, LUMINICO, MECANICO, QUIMICO, HIDRAULICO | no | ASIGNACION, PUNTO_COMA |
| `DESTINO_OP` | COMA | si | PAREN_DER |

## 2.5 Tabla de Parsing LL(1)

`TABLA[no_terminal][terminal]` indica qué producción aplicar (`-1` = error
sintáctico). Se lista solo lo no vacío; **toda pareja no listada vale `-1`**.

| No terminal | Terminal → produccion |
|:-----------:|:---------------------|
| `PROGRAMA` | `SENSOR`→0, `UMBRAL`→0, `SI`→0, `ESTADO`→0, `CALCULAR`→0, `SINO`→0, `MUESTREO`→0, `EOF`→1 |
| `SENTENCIA` | `SENSOR`→2, `UMBRAL`→3, `SI`→4, `ESTADO`→49, `CALCULAR`→5, `SINO`→51, `MUESTREO`→50 |
| `VALOR_UMBRAL` | `NUMERO`→7, `MENOS`→6 |
| `CONDICION` | `ABS`→8, `ID`→8, `NUMERO`→8, `MENOS`→8, `PAREN_IZQ`→8 |
| `EXPRESION` | `ABS`→9, `ID`→9, `NUMERO`→9, `MENOS`→9, `PAREN_IZQ`→9 |
| `EXPRESION_SIG` | `ENTONCES`→12, `MAYOR`→12, `MENOR`→12, `IGUAL_IGUAL`→12, `MAYOR_IGUAL`→12, `MENOR_IGUAL`→12, `DIFERENTE`→12, `MAS`→10, `MENOS`→11, `PAREN_DER`→12, `COMA`→12 |
| `TERMINO` | `ABS`→13, `ID`→13, `NUMERO`→13, `MENOS`→13, `PAREN_IZQ`→13 |
| `TERMINO_SIG` | `ENTONCES`→16, `MAYOR`→16, `MENOR`→16, `IGUAL_IGUAL`→16, `MAYOR_IGUAL`→16, `MENOR_IGUAL`→16, `DIFERENTE`→16, `MAS`→16, `MENOS`→16, `POR`→14, `DIV`→15, `PAREN_DER`→16, `COMA`→16 |
| `FACTOR` | `ABS`→19, `ID`→18, `NUMERO`→17, `MENOS`→20, `PAREN_IZQ`→53 |
| `OP_REL` | `MAYOR`→21, `MENOR`→22, `IGUAL_IGUAL`→23, `MAYOR_IGUAL`→24, `MENOR_IGUAL`→25, `DIFERENTE`→26 |
| `TIPO_OP` | `ABS`→52, `ID`→52, `NUMERO`→52, `MENOS`→52, `PAREN_IZQ`→52, `SENO`→27, `COSENO`→28, `CUADRADA`→29, `PROMEDIO`→30, `MAXIMO`→31, `SUMA`→32 |
| `LISTA_PARAMS` | `PAREN_DER`→34, `AMPLITUD`→33, `FRECUENCIA`→33, `VENTANA`→33, `CON`→33 |
| `PARAM` | `AMPLITUD`→37, `FRECUENCIA`→38, `VENTANA`→39, `CON`→40 |
| `LISTA_PARAMS_SIG` | `PAREN_DER`→36, `COMA`→35 |
| `TIPO_MAGNITUD` | `ELECTRICO`→41, `TERMICO`→42, `LUMINICO`→43, `MECANICO`→44, `QUIMICO`→45, `HIDRAULICO`→46 |
| `DESTINO_OP` | `PAREN_DER`→48, `COMA`→47 |

No hay ninguna celda con dos producciones para el mismo terminal: **la
gramática es LL(1) efectiva.** No es una aserción: `construirTabla()` delega en
`anotar()`, que registra en `Gramatica.conflictos()` toda celda donde la
producción nueva pise a una anterior. La lista está vacía, y se puede comprobar
sin abrir la interfaz con `compile_and_run_cli.bat -v tests\test_tipos.txt`.

Los conjuntos se calculan por punto fijo en `Gramatica.calcularFIRST()` /
`calcularFOLLOW()`, y la tabla se arma en `construirTabla()`, así que no pueden
desviarse de la gramática a mano. Las tres tablas de esta sección se pueden
regenerar con `java timotomata.parser.DumpTablas tablas.md`.

### Ejemplo de uso de la tabla

Para parsear `sensor voltaje : ELECTRICO;`:

1. `PROGRAMA`, lookahead = `SENSOR` → producción 0 (`PROGRAMA → SENTENCIA PROGRAMA`)
2. `SENTENCIA`, lookahead = `SENSOR` → producción 2 (`SENTENCIA → SENSOR ID DOS_PUNTOS TIPO_MAGNITUD PUNTO_COMA`)
3. Se consumen `SENSOR`, `ID`, `DOS_PUNTOS`
4. `TIPO_MAGNITUD`, lookahead = `ELECTRICO` → producción 41
5. Se consumen `PUNTO_COMA`
6. `PROGRAMA`, lookahead = `EOF` → producción 1 (`PROGRAMA → ε`)
7. Parseo completado.

## 2.6 Algoritmo de Parsing

El parser implementa un **recursivo descendente table-driven**. Cada no-terminal es un método que:

1. Consulta la tabla: `int prod = Gramatica.obtenerProduccion(noTerminal, ver().tipo)`
2. Si `prod == -1` → lanza error sintáctico
3. Aplica la producción mediante un `switch (prod)` con las acciones semánticas correspondientes (construcción del AST)

### Manejo de asociatividad izquierda

La gramática usa recursión por la derecha para evitar recursión izquierda directa. Para que las expresiones sean **left-associative** (evaluación de izquierda a derecha: `1 + 2 + 3` = `(1 + 2) + 3`), los métodos `expresionSig()` y `terminoSig()` reciben un parámetro "izquierda acumulada":

```java
// 1 + 2 + 3 se parsea como:
expresion()
  → termino() → factor() → Numero(1)
  → expresionSig(Numero(1))
    → MAS → termino() → factor() → Numero(2)
    → expresionSig(Binaria(1, "+", 2))
      → MAS → termino() → factor() → Numero(3)
      → expresionSig(Binaria(Binaria(1, "+", 2), "+", 3))
        → ε → devuelve Binaria(Binaria(1, "+", 2), "+", 3)  ✓ izquierda
```

### Construcción del AST

Cada producción construye los nodos correspondientes del Árbol de Sintaxis Abstracta:

| Producción | Nodo(s) creado(s) |
|:----------:|-------------------|
| 0 `PROGRAMA → SENTENCIA PROGRAMA` | Se agregan sensores, umbrales, reglas y cálculos al `Programa` |
| 2 `SENTENCIA → SENSOR ID DOS_PUNTOS TIPO_MAGNITUD ;` | `new Sensor(nombre, tipo)` + `simbolos.declararSensor(...)` |
| 3 `SENTENCIA → UMBRAL ID DOS_PUNTOS TIPO_MAGNITUD = VALOR_UMBRAL ;` | `simbolos.declararUmbral(nombre, tipo, valor, línea)` |
| 4 `SENTENCIA → SI CONDICION ENTONCES ESTADO = ESTADO_SISTEMA ;` | `programa.reglas.add(new Regla(cond, estado, línea))` |
| 5 `SENTENCIA → CALCULAR ( ID , TIPO_OP DESTINO_OP ) ;` | `new Calculo(origen, …)`, `simbolos.declararSenal(...)` y la arista de flujo de datos |
| 6 `SENTENCIA → ESTADO = ESTADO_SISTEMA ;` | `programa.estadoInicial` |
| 8 `CONDICION → EXPRESION OP_REL EXPRESION` | `new Binaria(izq, op, der)` |
| 10–11 `EXPRESION_SIG → MAS/MENOS TERMINO EXPRESION_SIG` | `new Binaria(izquierda, op, der)` |
| 14–15 `TERMINO_SIG → POR/DIV FACTOR TERMINO_SIG` | `new Binaria(izquierda, op, der)` |
| 17 `FACTOR → NUMERO` | `new Numero(valor)` |
| 18 `FACTOR → ID` | `new Variable(nombre)` + `simbolos.registrarUso(...)` |
| 19 `FACTOR → ABS ( EXPRESION )` | `new Abs(expr)` |
| 20 `FACTOR → MENOS FACTOR` | `new Negacion(expr)` |
| 52 `TIPO_OP → EXPRESION` | `calculo.expresion`; `calculo.operacion` queda en `null` |
| 53 `FACTOR → ( EXPRESION )` | ninguno: el agrupamiento devuelve la expresión interior sin envolverla |

La producción 53 no produce un nodo porque `(a + b) / 2` y `a / 2 + b / 2`
comparten la misma forma de árbol; el agrupamiento solo restringe el orden en que
el parser arma los operandos, y eso ya lo resuelve la precedencia de
`EXPRESION_SIG` y `TERMINO_SIG`. Un nodo `Agrupamiento` sería información que el
AST no puede usar y que el generador de código tendría que atravessar sin
razón.

---

*Documentación generada a partir del código fuente del compilador Timotomata.*
