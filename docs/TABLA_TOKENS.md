# Tabla completa de tokens — Timotomata

## 1. Palabras reservadas

| # | Lexema(s) | TipoToken | Descripción |
|---|---|---|---|
| 1 | `sensor` | `SENSOR` | Declaración de sensor |
| 2 | `umbral` | `UMBRAL` | Declaración de umbral |
| 3 | `si` | `SI` | Inicio de regla condicional |
| 4 | `entonces` | `ENTONCES` | Conector de regla |
| 5 | `estado` | `ESTADO` | Estado inicial de la máquina |
| 6 | `sino` | `SINO` | Estado cuando ninguna regla se cumple |
| 7 | `muestreo` | `MUESTREO` | Duración de un tick, en segundos |
| 8 | `abs` | `ABS` | Función valor absoluto |
| 9 | `calcular` | `CALCULAR` | Declaración de cálculo |
| 10 | `normal`, `pico`, `caida`, `inestable` | `ESTADO_SISTEMA` | Valores de estado del sistema |
| 11 | `seno` | `SENO` | Onda senoidal |
| 12 | `coseno` | `COSENO` | Onda cosenoidal |
| 13 | `cuadrada` | `CUADRADA` | Onda cuadrada |
| 14 | `promedio` | `PROMEDIO` | Promedio móvil |
| 15 | `maximo` | `MAXIMO` | Máximo sobre una ventana |
| 16 | `suma` | `SUMA` | Suma de sensores |
| 17 | `amplitud` | `AMPLITUD` | Parámetro de operación |
| 18 | `frecuencia` | `FRECUENCIA` | Parámetro de operación |
| 19 | `ventana` | `VENTANA` | Parámetro de operación |
| 20 | `con` | `CON` | Parámetro de operación |
| 21 | `electrico` | `ELECTRICO` | Tipo de sensor: tensión, corriente, potencia |
| 22 | `termico` | `TERMICO` | Tipo de sensor: temperatura |
| 23 | `luminico` | `LUMINICO` | Tipo de sensor: iluminación |
| 24 | `mecanico` | `MECANICO` | Tipo de sensor: vibración, velocidad, RPM |
| 25 | `quimico` | `QUIMICO` | Tipo de sensor: pH, humedad, concentración |
| 26 | `hidraulico` | `HIDRAULICO` | Tipo de sensor: presión, caudal, nivel |

> `ESTADO_SISTEMA` agrupa los cuatro valores de estado en una sola fila. Total
> del lenguaje: **45 tokens** (44 + `EOF`).

## 2. Identificador

| # | Lexema(s) | TipoToken | Descripción |
|---|---|---|---|
| 27 | cualquier palabra no reservada | `ID` | Nombre de sensor, señal o umbral |

## 3. Números

| # | Lexema(s) | TipoToken | Descripción |
|---|---|---|---|
| 28 | `123`, `45.67`, `0.5`… | `NUMERO` | Valor numérico (entero o decimal) |

## 4. Signos directos (1 carácter)

| # | Carácter | TipoToken | Descripción |
|---|---|---|---|
| 29 | `+` | `MAS` | Suma / operador positivo |
| 30 | `-` | `MENOS` | Resta / operador negativo |
| 31 | `*` | `POR` | Multiplicación |
| 32 | `;` | `PUNTO_COMA` | Punto y coma |
| 33 | `(` | `PAREN_IZQ` | Paréntesis izquierdo |
| 34 | `)` | `PAREN_DER` | Paréntesis derecho |
| 35 | `,` | `COMA` | Separador de argumentos |
| 36 | `:` | `DOS_PUNTOS` | Declara el tipo de sensor |

## 5. Operadores compuestos (2 caracteres)

| # | Lexema | TipoToken | Descripción |
|---|---|---|---|
| 37 | `==` | `IGUAL_IGUAL` | Igual que |
| 38 | `>=` | `MAYOR_IGUAL` | Mayor o igual que |
| 39 | `<=` | `MENOR_IGUAL` | Menor o igual que |
| 40 | `<>` | `DIFERENTE` | Diferente |
| 41 | `!=` | `DIFERENTE` | Diferente |

## 6. Operadores unarios (1 carácter con lookahead)

| # | Carácter | TipoToken | Descripción |
|---|---|---|---|
| 42 | `=` (solo, sin `=` después) | `ASIGNACION` | Asignación |
| 43 | `>` (solo, sin `=` después) | `MAYOR` | Mayor que |
| 44 | `<` (solo, sin `=` ni `>` después) | `MENOR` | Menor que |
| 45 | `/` (solo, sin `/` ni `*` después) | `DIV` | División |

## 7. Token especial

| # | Lexema | TipoToken | Descripción |
|---|---|---|---|
| 46 | *(fin de archivo)* | `EOF` | Fin de archivo / entrada |