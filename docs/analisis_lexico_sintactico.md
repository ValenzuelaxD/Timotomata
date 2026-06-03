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
   - [Símbolos](#22-símbolos)
   - [Producciones](#23-producciones)
   - [Conjuntos FIRST](#24-conjuntos-first)
   - [Conjuntos FOLLOW](#25-conjuntos-follow)
   - [Tabla de Parsing LL(1)](#26-tabla-de-parsing-ll1)
   - [Algoritmo de Parsing](#27-algoritmo-de-parsing)

---

# 1. Analizador Léxico (AFD)

El analizador léxico está implementado como un **Autómata Finito Determinista (AFD)** con **13 estados** y **17 clases de caracteres** en su alfabeto.

## 1.1 Alfabeto (clases de caracteres)

El alfabeto de entrada se divide en **17 clases**. Cada carácter se clasifica en una única clase, y la transición se determina por `TABLA_TRANS[estado][clase]`.

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
| 16 | `OTRO` | cualquier otro | Caracteres no válidos (`@`, `#`, `$`, etc.) |

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

SENTENCIA     → SENSOR ID PUNTO_COMA
              | UMBRAL ID ASIGNACION VALOR_UMBRAL PUNTO_COMA
              | SI CONDICION ENTONCES ESTADO ASIGNACION ESTADO_SISTEMA PUNTO_COMA

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

OP_REL        → MAYOR | MENOR | IGUAL_IGUAL
              | MAYOR_IGUAL | MENOR_IGUAL | DIFERENTE
```

### Propiedades de la gramática

- **Factorizada por la izquierda**: No hay producciones con un prefijo común para el mismo no-terminal.
- **Sin recursión izquierda directa**: `EXPRESION` no deriva en `EXPRESION + ...` — usa `EXPRESION_SIG` para manejar la recursión por la derecha.
- **LL(1)**: Es posible determinar qué producción usar con solo mirar el siguiente token (lookahead).

## 2.2 Símbolos

### No terminales (10)

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

### Terminales (24 tokens)

| Token | Cómo se escribe |
|-------|:---------------:|
| `SENSOR` | `sensor` |
| `UMBRAL` | `umbral` |
| `SI` | `si` |
| `ENTONCES` | `entonces` |
| `ESTADO` | `estado` |
| `ABS` | `abs` |
| `ID` | cualquier identificador |
| `NUMERO` | cualquier número |
| `ESTADO_SISTEMA` | `normal`, `pico`, `caida`, `inestable` |
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
| `PAREN_IZQ` | `(` |
| `PAREN_DER` | `)` |
| `EOF` | fin de archivo |

## 2.3 Producciones

Total: **26 producciones**.

| ID | Producción |
|:--:|------------|
| 0 | `PROGRAMA → SENTENCIA PROGRAMA` |
| 1 | `PROGRAMA → ε` |
| 2 | `SENTENCIA → SENSOR ID PUNTO_COMA` |
| 3 | `SENTENCIA → UMBRAL ID ASIGNACION VALOR_UMBRAL PUNTO_COMA` |
| 4 | `SENTENCIA → SI CONDICION ENTONCES ESTADO ASIGNACION ESTADO_SISTEMA PUNTO_COMA` |
| 5 | `VALOR_UMBRAL → MENOS NUMERO` |
| 6 | `VALOR_UMBRAL → NUMERO` |
| 7 | `CONDICION → EXPRESION OP_REL EXPRESION` |
| 8 | `EXPRESION → TERMINO EXPRESION_SIG` |
| 9 | `EXPRESION_SIG → MAS TERMINO EXPRESION_SIG` |
| 10 | `EXPRESION_SIG → MENOS TERMINO EXPRESION_SIG` |
| 11 | `EXPRESION_SIG → ε` |
| 12 | `TERMINO → FACTOR TERMINO_SIG` |
| 13 | `TERMINO_SIG → POR FACTOR TERMINO_SIG` |
| 14 | `TERMINO_SIG → DIV FACTOR TERMINO_SIG` |
| 15 | `TERMINO_SIG → ε` |
| 16 | `FACTOR → NUMERO` |
| 17 | `FACTOR → ID` |
| 18 | `FACTOR → ABS PAREN_IZQ EXPRESION PAREN_DER` |
| 19 | `FACTOR → MENOS FACTOR` |
| 20 | `OP_REL → MAYOR` |
| 21 | `OP_REL → MENOR` |
| 22 | `OP_REL → IGUAL_IGUAL` |
| 23 | `OP_REL → MAYOR_IGUAL` |
| 24 | `OP_REL → MENOR_IGUAL` |
| 25 | `OP_REL → DIFERENTE` |

## 2.4 Conjuntos FIRST

Los conjuntos FIRST contienen los terminales que pueden aparecer al inicio de una derivación de cada no-terminal. ε indica que el no-terminal puede derivar la cadena vacía.

| No terminal | FIRST | ¿ε? |
|:-----------:|-------|:---:|
| `PROGRAMA` | `{ SENSOR, UMBRAL, SI }` | ✅ |
| `SENTENCIA` | `{ SENSOR, UMBRAL, SI }` | ❌ |
| `VALOR_UMBRAL` | `{ MENOS, NUMERO }` | ❌ |
| `CONDICION` | `{ NUMERO, ID, ABS, MENOS }` | ❌ |
| `EXPRESION` | `{ NUMERO, ID, ABS, MENOS }` | ❌ |
| `EXPRESION_SIG` | `{ MAS, MENOS }` | ✅ |
| `TERMINO` | `{ NUMERO, ID, ABS, MENOS }` | ❌ |
| `TERMINO_SIG` | `{ POR, DIV }` | ✅ |
| `FACTOR` | `{ NUMERO, ID, ABS, MENOS }` | ❌ |
| `OP_REL` | `{ MAYOR, MENOR, IGUAL_IGUAL, MAYOR_IGUAL, MENOR_IGUAL, DIFERENTE }` | ❌ |

### FIRST calculado por producción

| Producción | Cuerpo | FIRST(cuerpo) |
|:----------:|--------|:-------------:|
| 0 | `SENTENCIA PROGRAMA` | `{ SENSOR, UMBRAL, SI }` |
| 1 | `ε` | `{ ε }` |
| 2 | `SENSOR ID PUNTO_COMA` | `{ SENSOR }` |
| 3 | `UMBRAL ID ASIGNACION VALOR_UMBRAL PUNTO_COMA` | `{ UMBRAL }` |
| 4 | `SI CONDICION ENTONCES ESTADO ASIGNACION ESTADO_SISTEMA PUNTO_COMA` | `{ SI }` |
| 5 | `MENOS NUMERO` | `{ MENOS }` |
| 6 | `NUMERO` | `{ NUMERO }` |
| 7 | `EXPRESION OP_REL EXPRESION` | `{ NUMERO, ID, ABS, MENOS }` |
| 8 | `TERMINO EXPRESION_SIG` | `{ NUMERO, ID, ABS, MENOS }` |
| 9 | `MAS TERMINO EXPRESION_SIG` | `{ MAS }` |
| 10 | `MENOS TERMINO EXPRESION_SIG` | `{ MENOS }` |
| 11 | `ε` | `{ ε }` |
| 12 | `FACTOR TERMINO_SIG` | `{ NUMERO, ID, ABS, MENOS }` |
| 13 | `POR FACTOR TERMINO_SIG` | `{ POR }` |
| 14 | `DIV FACTOR TERMINO_SIG` | `{ DIV }` |
| 15 | `ε` | `{ ε }` |
| 16 | `NUMERO` | `{ NUMERO }` |
| 17 | `ID` | `{ ID }` |
| 18 | `ABS PAREN_IZQ EXPRESION PAREN_DER` | `{ ABS }` |
| 19 | `MENOS FACTOR` | `{ MENOS }` |
| 20–25 | `MAYOR`, `MENOR`, `IGUAL_IGUAL`, `MAYOR_IGUAL`, `MENOR_IGUAL`, `DIFERENTE` | cada uno su respectivo terminal |

## 2.5 Conjuntos FOLLOW

Los conjuntos FOLLOW contienen los terminales que pueden aparecer inmediatamente después de cada no-terminal. `EOF` representa el fin del archivo.

| No terminal | FOLLOW |
|:-----------:|--------|
| `PROGRAMA` | `{ EOF }` |
| `SENTENCIA` | `{ SENSOR, UMBRAL, SI, EOF }` |
| `VALOR_UMBRAL` | `{ PUNTO_COMA }` |
| `CONDICION` | `{ ENTONCES }` |
| `EXPRESION` | `{ MAYOR, MENOR, IGUAL_IGUAL, MAYOR_IGUAL, MENOR_IGUAL, DIFERENTE, PUNTO_COMA, ENTONCES, PAREN_DER }` |
| `EXPRESION_SIG` | `{ MAYOR, MENOR, IGUAL_IGUAL, MAYOR_IGUAL, MENOR_IGUAL, DIFERENTE, PUNTO_COMA, ENTONCES, PAREN_DER }` |
| `TERMINO` | `{ MAS, MENOS, MAYOR, MENOR, IGUAL_IGUAL, MAYOR_IGUAL, MENOR_IGUAL, DIFERENTE, PUNTO_COMA, ENTONCES, PAREN_DER }` |
| `TERMINO_SIG` | `{ MAS, MENOS, MAYOR, MENOR, IGUAL_IGUAL, MAYOR_IGUAL, MENOR_IGUAL, DIFERENTE, PUNTO_COMA, ENTONCES, PAREN_DER }` |
| `FACTOR` | `{ POR, DIV, MAS, MENOS, MAYOR, MENOR, IGUAL_IGUAL, MAYOR_IGUAL, MENOR_IGUAL, DIFERENTE, PUNTO_COMA, ENTONCES, PAREN_DER }` |
| `OP_REL` | `{ NUMERO, ID, ABS, MENOS }` |

## 2.6 Tabla de Parsing LL(1)

La tabla `TABLA[no_terminal][terminal]` indica qué producción aplicar (`-1` = error sintáctico).

| NT \ Terminal | SENSOR | UMBRAL | SI | ENTONCES | ESTADO | ABS | ID | NUMERO | ESTADO_SISTEMA | MAYOR | MENOR | IGUAL_IGUAL | MAYOR_IGUAL | MENOR_IGUAL | DIFERENTE | MAS | MENOS | POR | DIV | ASIGNACION | PUNTO_COMA | PAREN_IZQ | PAREN_DER | EOF |
|:-------------:|:------:|:------:|:--:|:--------:|:------:|:---:|:--:|:------:|:--------------:|:-----:|:-----:|:-----------:|:-----------:|:-----------:|:---------:|:---:|:-----:|:---:|:---:|:----------:|:----------:|:---------:|:---------:|:---:|
| **PROGRAMA** | 0 | 0 | 0 | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | 1 |
| **SENTENCIA** | 2 | 3 | 4 | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| **VALOR_UMBRAL** | — | — | — | — | — | — | — | 6 | — | — | — | — | — | — | — | — | 5 | — | — | — | — | — | — | — |
| **CONDICION** | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | 7 | — | — | — | — | — | — | — |
| | | | | | | 7 | 7 | 7 | | | | | | | | | 7 | | | | | 7 | 7 | |
| **EXPRESION** | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| | | | | | | 8 | 8 | 8 | | | | | | | | | 8 | | | | | 8 | 8 | |
| **EXPRESION_SIG** | — | — | — | 11 | — | — | — | — | — | 11 | 11 | 11 | 11 | 11 | 11 | 9 | 10 | — | — | — | 11 | 11 | 11 | — |
| **TERMINO** | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| | | | | | | 12 | 12 | 12 | | | | | | | | | 12 | | | | | 12 | 12 | |
| **TERMINO_SIG** | — | — | — | 15 | — | — | — | — | — | 15 | 15 | 15 | 15 | 15 | 15 | 15 | 15 | 13 | 14 | — | 15 | 15 | 15 | — |
| **FACTOR** | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| | | | | | | 18 | 17 | 16 | | | | | | | | | 19 | | | | | 18 | 18 | |
| **OP_REL** | — | — | — | — | — | — | — | — | — | 20 | 21 | 22 | 23 | 24 | 25 | — | — | — | — | — | — | — | — | — |

*Nota: Las celdas con `—` indican `-1` (error sintáctico).*

### Ejemplo de uso de la tabla

Para parsear `sensor voltaje;`:

1. `PROGRAMA`, lookahead = `SENSOR` → producción 0 (`PROGRAMA → SENTENCIA PROGRAMA`)
2. `SENTENCIA`, lookahead = `SENSOR` → producción 2 (`SENTENCIA → SENSOR ID PUNTO_COMA`)
3. Se consumen `SENSOR`, `ID`, `PUNTO_COMA`
4. `PROGRAMA`, lookahead = `FIN` (EOF) → producción 1 (`PROGRAMA → ε`)
5. Parseo completado.

## 2.7 Algoritmo de Parsing

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
| 0 `PROGRAMA → SENTENCIA PROGRAMA` | Se agregan sensores, umbrales y reglas al `Programa` |
| 2 `SENTENCIA → SENSOR ID ;` | `programa.sensores.add(nombre)` |
| 3 `SENTENCIA → UMBRAL ID = VALOR_UMBRAL ;` | `programa.umbrales.put(nombre, valor)` |
| 4 `SENTENCIA → SI CONDICION ENTONCES ESTADO = ESTADO_SISTEMA ;` | `programa.reglas.add(new Regla(cond, estado))` |
| 7 `CONDICION → EXPRESION OP_REL EXPRESION` | `new Binaria(izq, op, der)` |
| 9–10 `EXPRESION_SIG → [+/-] TERMINO EXPRESION_SIG` | `new Binaria(izquierda, op, der)` |
| 13–14 `TERMINO_SIG → [*/] FACTOR TERMINO_SIG` | `new Binaria(izquierda, op, der)` |
| 16 `FACTOR → NUMERO` | `new Numero(valor)` |
| 17 `FACTOR → ID` | `new Variable(nombre)` |
| 18 `FACTOR → ABS ( EXPRESION )` | `new Abs(expr)` |
| 19 `FACTOR → MENOS FACTOR` | `new Negacion(expr)` |

---

*Documentación generada a partir del código fuente del compilador Timotomata.*
