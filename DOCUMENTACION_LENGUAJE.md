# Documentación Completa del Lenguaje Timotomata (Extendida)

## Índice

1. [Estructura del compilador](#1-estructura-del-compilador)
2. [El Autómata Finito Determinista (AFD)](#2-el-autómata-finito-determinista-afd)
3. [Tabla completa de tokens](#3-tabla-completa-de-tokens)
4. [Palabras reservadas](#4-palabras-reservadas)
5. [Operadores y Delimitadores](#5-operadores-y-delimitadores)
6. [Identificadores y literales](#6-identificadores-y-literales)
7. [Signos de puntuación](#7-signos-de-puntuación)
8. [Gramática formal completa](#8-gramática-formal-completa)
9. [Arquitectura del código](#9-arquitectura-del-código)
10. [Resaltado de Sintaxis y Marcado de Errores](#10-resaltado-de-sintaxis-y-marcado-de-errores)
11. [Simulación y Evaluación de Reglas (Intérprete del AST)](#11-simulación-y-evaluación-de-reglas-intérprete-del-ast)
12. [Ejemplos completos](#12-ejemplos-completos)

---

## 1. Estructura del compilador

El compilador se divide en 3 fases principales, más una capa de simulación e interfaz gráfica:

```
Código fuente
     ↓
[ LEXER ]      →  Resaltado de Sintaxis, Detección de Comentarios y Cadenas
     ↓
[ PARSER ]     →  Árbol de Derivación LL(1) y Árbol de Sintaxis Abstracta (AST)
     ↓
[ SEMÁNTICO ]  →  Detección de IDs No Declarados / Tipos
     ↓
[ SIMULADOR ]  →  Evaluación del AST en tiempo de ejecución sobre señales simuladas
```

| Fase | Archivo principal | Función |
|------|------------------|---------|
| **Lexer** | `src/timotomata/lexer/Lexer.java` | Convierte texto → tokens e identifica comentarios/cadenas |
| **Parser** | `src/timotomata/parser/Parser.java` | Valida la estructura mediante LL(1) y genera el AST |
| **AST** | `src/timotomata/parser/ast/*` | Representa la estructura semántica abstracta del programa |
| **Simulador** | `src/timotomata/ui/SimuladorSensores.java` | Genera señales y evalúa las condiciones del AST |
| **UI** | `src/timotomata/ui/AppController.java` | Interfaz gráfica y editor interactivo JavaFX |

---

## 2. El Autómata Finito Determinista (AFD)

El AFD en `Lexer.java` reconoce todas las palabras clave y estructuras léxicas del lenguaje.

### Estados del AFD (14 estados)
```
Q0 = 0             Estado inicial
Q_ID = 1           Leyendo identificador / palabra reservada
Q_NUM = 2          Leyendo parte entera de un número
Q_NUM_PUNTO = 3    Acabamos de ver '.' en un número
Q_NUM_DEC = 4      Leyendo parte decimal
Q_EQ = 5           Vimos '=', esperando ver si es '==' o '='
Q_GT = 6           Vimos '>', esperando ver si es '>='
Q_LT = 7           Vimos '<', esperando ver si es '<=', '<>' o '<'
Q_NOT = 8          Vimos '!', esperando ver si es '!='
Q_DIV = 9          Vimos '/', esperando ver si es '//', '/*' o '/'
Q_COM_LINEA = 10   Dentro de comentario //
Q_COM_BLOQ = 11    Dentro de comentario /* */
Q_COM_BLOQ_FIN = 12 Vimos '*' dentro de /*, esperando '/' para cerrar
Q_CADENA = 13      Dentro de literal de cadena "..."
```

---

## 3. Tabla completa de tokens

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
| 19 | `TIPO` | Palabra reservada | `tipo` |
| 20 | `ELECTRICO` | Palabra reservada | `electrico` |
| 21 | `TERMICO` | Palabra reservada | `termico` |
| 22 | `RANGO` | Palabra reservada | `rango` |
| 23 | `MINIMO` | Palabra reservada | `minimo` |
| 24 | `MAXIMO` | Palabra reservada | `maximo` |
| 25 | `Y` | Palabra reservada | `y` |
| 26 | `O` | Palabra reservada | `o` |
| 27 | `ALERTA` | Palabra reservada | `alerta` |
| 28 | `FLUCTUACION` | Palabra reservada | `fluctuacion` |
| 29 | `COMENTARIO` | Comentario capturado | `// comentario` o `/* bloque */` |
| 30 | `DESCONOCIDO` | Símbolo inválido | `@`, `$`, `=>` |
| 31 | `CADENA` | Literal de texto | `"Peligro"` |
| 32 | `LLAVE_IZQ` | Símbolo | `{` |
| 33 | `LLAVE_DER` | Símbolo | `}` |
| 34 | `ID` | Identificador | `voltaje`, `temperatura` |
| 35 | `NUMERO` | Literal numérico | `220`, `0.1` |
| 36 | `MAS`, `MENOS`, `POR`, `DIV` | Operadores | `+`, `-`, `*`, `/` |
| 37 | `ASIGNACION` | Símbolo | `=` |
| 38 | `PUNTO_COMA` | Símbolo | `;` |
| 39 | `PAREN_IZQ`, `PAREN_DER` | Símbolos | `(`, `)` |
| 40 | `EOF` | Fin de archivo | `""` |

---

## 4. Palabras reservadas

Todas las palabras reservadas son **case-insensitive**.

### Nuevas palabras reservadas (Gramática Extendida)
* **`tipo`**: Define el tipo de un sensor (`sensor voltaje tipo electrico;`).
* **`electrico`, `termico`**: Predefinidos para clasificar sensores.
* **`rango`, `minimo`, `maximo`**: Define límites seguros (`rango temp minimo = 15 maximo = 60;`).
* **`y`, `o`**: Operadores lógicos compuestos para condiciones.
* **`alerta`**: Permite asignar mensajes personalizados en reglas condicionales (`alerta = "Voltaje alto";`).
* **`fluctuacion`**: Nueva función de análisis para calcular la variación aleatoria del sensor.

---

## 5. Operadores y Delimitadores

* **Lógicos**: `y`, `o`.
* **Aritméticos**: `+`, `-`, `*`, `/`.
* **Relacionales**: `>`, `<`, `==`, `>=`, `<=`, `!=`, `<>`.
* **Asignación**: `=`.
* **Bloques**: `{` y `}`.

---

## 8. Gramática formal completa

### Notación BNF (Backus-Naur Form Extendida)

```
PROGRAMA      → SENTENCIA PROGRAMA
              | ε

SENTENCIA     → SENSOR ID TIPO_OPC ;
              | UMBRAL ID = VALOR_UMBRAL ;
              | RANGO ID MINIMO = NUMERO MAXIMO = NUMERO ;
              | SI CONDICION ENTONCES CONSECUENCIA
              | CALCULAR AUX_CALCULAR

TIPO_OPC      → TIPO TIPO_VAL
              | ε

TIPO_VAL      → ELECTRICO
              | TERMICO

VALOR_UMBRAL  → NUMERO
              | - NUMERO

AUX_CALCULAR  → ( ID , TIPO_OP ) ;
              | FUNC_ANALISIS ( ID PARAMS_ANALISIS ) ;

FUNC_ANALISIS → PROMEDIO
              | MAXIMO
              | FLUCTUACION

PARAMS_ANALISIS → , VENTANA = NUMERO
                | ε

CONSECUENCIA  → ACCION
              | { ACCIONES }

ACCION        → ESTADO = ESTADO_SISTEMA ;
              | ALERTA = CADENA ;

ACCIONES      → ACCION ACCIONES_REST

ACCIONES_REST → ACCION ACCIONES_REST
              | ε

CONDICION     → COND_SIMPLE COND_COMPUESTA

COND_SIMPLE   → EXPRESION OP_REL EXPRESION

COND_COMPUESTA → LOG_OP COND_SIMPLE COND_COMPUESTA
               | ε

LOG_OP        → Y
              | O

EXPRESION     → TERMINO EXPRESION_SIG

EXPRESION_SIG → + TERMINO EXPRESION_SIG
              | - TERMINO EXPRESION_SIG
              | ε

TERMINO       → FACTOR TERMINO_SIG

TERMINO_SIG   → * FACTOR TERMINO_SIG
              | / FACTOR TERMINO_SIG
              | ε

FACTOR        → NUMERO
              | ID
              | ABS ( EXPRESION )
              | - FACTOR
```

---

## 10. Resaltado de Sintaxis y Marcado de Errores

El editor interactivo de la UI implementa un coloreado completo:
1. **Palabras clave**: Púrpura (`#cba6f7`).
2. **Nombres de variables/sensores**: Azul (`#89b4fa`).
3. **Números**: Naranja (`#fab387`).
4. **Cadenas de texto**: Verde (`#a6e3a1`).
5. **Comentarios**: Gris (`#585b70`).
6. **Errores léxicos/sintácticos**: Se remarcan con subrayado ondulado rojo (`#f38ba8`).

---

## 11. Simulación y Evaluación de Reglas (Intérprete del AST)

Cuando el usuario hace clic en el botón **"Ejecutar Simulación"** y el programa compila sin errores:
1. Se evalúa el AST del programa.
2. Se genera un flujo de datos dinámico de 50 muestras. Si hay una sentencia `calcular` declarada, se genera la forma de onda correspondiente (senoidal, cosenoidal, cuadrada, promedio móvil, etc.).
3. Se evalúan recursivamente las expresiones y condiciones del AST sobre cada muestra.
4. Si la condición de la regla se cumple, el sistema asigna el nuevo estado y/o la alerta correspondiente.
5. Se muestra una gráfica interactiva de JavaFX con los límites seguros (`minimo` y `maximo`) y los puntos de anomalías coloreados según el estado evaluado.

---

## 12. Ejemplos completos

### Ejemplo de Programa Completo con la Nueva Gramática

```txt
// Monitoreo de subestación eléctrica
sensor voltaje tipo electrico;
sensor temperatura tipo termico;

rango voltaje minimo = 110 maximo = 127;
rango temperatura minimo = -10 maximo = 80;

umbral maxVolt = 127;
umbral minVolt = 110;

si voltaje >= maxVolt o temperatura > 80 entonces {
    estado = PICO;
    alerta = "Voltaje o temperatura excesivamente alta";
}

si voltaje <= minVolt entonces estado = CAIDA;

calcular promedio(voltaje, ventana = 10);
calcular fluctuacion(temperatura);
```
