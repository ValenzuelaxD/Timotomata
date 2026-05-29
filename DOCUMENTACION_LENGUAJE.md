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

El compilador se divide en 2 fases principales, más una capa de interfaz gráfica:

```
Código fuente
     ↓
[ LEXER ]  →  genera tokens
     ↓
[ PARSER ] →  genera AST + Árbol de Derivación
     ↓
  Resultado
```

| Fase | Archivo principal | Función |
|------|------------------|---------|
| **Lexer** | `src/timotomata/lexer/Lexer.java` | Convierte texto → tokens |
| **Parser** | `src/timotomata/parser/Parser.java` | Convierte tokens → AST |
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
| `SENSOR` | `sensor`, `Sensor`, `SENSOR` | Declara un sensor: `sensor voltaje;` |
| `UMBRAL` | `umbral`, `Umbral` | Declara un umbral: `umbral max = 250;` |
| `SI` | `si`, `SI` | Inicia regla: `si voltaje > 220 entonces ...` |
| `ENTONCES` | `entonces` | Separa condición de acción |
| `ESTADO` | `estado` | Asigna estado del sistema |
| `CALCULAR` | `calcular` | Inicia cálculo funcional |
| `ABS` | `abs`, `Abs` | Valor absoluto en expresiones |

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
SENTENCIA → SENSOR ID ;
          | UMBRAL ID = VALOR_UMBRAL ;
          | SI CONDICION ENTONCES ESTADO = ESTADO_SISTEMA ;
          | CALCULAR ( ID , TIPO_OP ) ;
```

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
```

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
```

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

### No terminales de la gramática (14)

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

1. **AST (Abstract Syntax Tree)**: estructura de datos para ejecución (en `ast/`)
2. **Árbol de derivación**: árbol visual con todos los pasos de la gramática (clase `NodoDerivacion`)

El árbol de derivación se muestra en la UI mediante `PanelDerivacion.java`, que usa el algoritmo de layout con colapsado de nodos sintéticos (`EXPRESION_SIG`, `TERMINO_SIG`, `LISTA_PARAMS`, `PARAM`).

---

## 10. Ejemplos completos

### Ejemplo 1: Sensor y umbral básico

```
sensor voltaje;
sensor temperatura;
umbral maxTemp = 250;
```

### Ejemplo 2: Reglas condicionales

```
si voltaje > 220 entonces estado = pico;
si voltaje < 180 entonces estado = caida;
si temperatura >= 100 entonces estado = inestable;
```

### Ejemplo 3: Cálculos funcionales

```
calcular(voltaje, SENO(AMPLITUD=300, FRECUENCIA=0.1));
calcular(voltaje, COSENO(AMPLITUD=100, FRECUENCIA=0.05));
calcular(voltaje, CUADRADA(AMPLITUD=5, FRECUENCIA=0.5));
calcular(temperatura, PROMEDIO(VENTANA=5));
calcular(voltaje, MAXIMO());
calcular(mezcla, SUMA(CON=voltaje, CON=temperatura));
```

### Ejemplo 4: Uso de abs() en condiciones

```
si abs(voltaje - 220) > 10 entonces estado = pico;
```

### Ejemplo 5: Programa completo

```
sensor voltaje;
sensor corriente;
sensor temperatura;

umbral maxTemperatura = 85;
umbral minVoltaje = 200;

si voltaje < minVoltaje entonces estado = caida;
si temperatura > maxTemperatura entonces estado = inestable;
si abs(voltaje - 220) > 20 entonces estado = pico;

calcular(voltaje, SENO(AMPLITUD=300, FRECUENCIA=0.1));
calcular(corriente, PROMEDIO(VENTANA=10));
calcular(eficiencia, SUMA(CON=voltaje, CON=corriente));
```

---

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

═══════════════════════════════════════════════════════════════
  OPERADORES (símbolos)
═══════════════════════════════════════════════════════════════

  ARITMÉTICOS:      +   -   *   /

  RELACIONALES:     >   <   ==   >=   <=   !=   <>

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
