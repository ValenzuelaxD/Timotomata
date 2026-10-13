# Tabla completa de tokens — Timotomata

## 1. Palabras reservadas

| # | Lexema(s) | TipoToken | Descripción |
|---|---|---|---|
| 1 | `sensor` | `SENSOR` | Declaración de sensor |
| 2 | `umbral` | `UMBRAL` | Declaración de umbral |
| 3 | `si` | `SI` | Inicio de regla condicional |
| 4 | `entonces` | `ENTONCES` | Conector de regla |
| 5 | `estado` | `ESTADO` | Asignación de estado |
| 6 | `abs` | `ABS` | Función valor absoluto |
| 7 | `calcular` | `CALCULAR` | Declaración de cálculo |
| 8 | `normal`, `pico`, `caida`, `inestable` | `ESTADO_SISTEMA` | Valores de estado del sistema |
| 9 | `seno` | `SENO` | Onda senoidal |
| 10 | `coseno` | `COSENO` | Onda cosenoidal |
| 11 | `cuadrada` | `CUADRADA` | Onda cuadrada |
| 12 | `promedio` | `PROMEDIO` | Función promedio |
| 13 | `maximo` | `MAXIMO` | Función máximo |
| 14 | `suma` | `SUMA` | Función suma |
| 15 | `amplitud` | `AMPLITUD` | Parámetro de operación |
| 16 | `frecuencia` | `FRECUENCIA` | Parámetro de operación |
| 17 | `ventana` | `VENTANA` | Parámetro de operación |
| 18 | `con` | `CON` | Parámetro de operación |

> La lista tiene 18 entradas pero cubre 18 lexemas: `ESTADO_SISTEMA`
> agrupa los cuatro valores de estado en una sola fila, y en el enumeración
> `TipoToken` son 21 constantes de palabra reservada. Total del lenguaje:
> **36 tokens** (35 + `EOF`).

## 2. Identificador

| # | Lexema(s) | TipoToken | Descripción |
|---|---|---|---|
| 19 | cualquier palabra no reservada | `ID` | Nombre de variable de usuario |

## 3. Números

| # | Lexema(s) | TipoToken | Descripción |
|---|---|---|---|
| 20 | `123`, `45.67`, `0.5`… | `NUMERO` | Valor numérico (entero o decimal) |

## 4. Signos directos (1 carácter)

| # | Carácter | TipoToken | Descripción |
|---|---|---|---|
| 21 | `+` | `MAS` | Suma / operador positivo |
| 22 | `-` | `MENOS` | Resta / operador negativo |
| 23 | `*` | `POR` | Multiplicación |
| 24 | `;` | `PUNTO_COMA` | Punto y coma |
| 25 | `(` | `PAREN_IZQ` | Paréntesis izquierdo |
| 26 | `)` | `PAREN_DER` | Paréntesis derecho |
| 27 | `,` | `COMA` | Coma |

## 5. Operadores compuestos (2 caracteres)

| # | Lexema | TipoToken | Descripción |
|---|---|---|---|
| 28 | `==` | `IGUAL_IGUAL` | Igual que |
| 29 | `>=` | `MAYOR_IGUAL` | Mayor o igual que |
| 30 | `<=` | `MENOR_IGUAL` | Menor o igual que |
| 31 | `<>` | `DIFERENTE` | Diferente |
| 32 | `!=` | `DIFERENTE` | Diferente |

## 6. Operadores unarios (1 carácter con lookahead)

| # | Carácter | TipoToken | Descripción |
|---|---|---|---|
| 33 | `=` (solo, sin `=` después) | `ASIGNACION` | Asignación |
| 34 | `>` (solo, sin `=` después) | `MAYOR` | Mayor que |
| 35 | `<` (solo, sin `=` ni `>` después) | `MENOR` | Menor que |
| 36 | `/` (solo, sin `/` ni `*` después) | `DIV` | División |

## 7. Token especial

| # | Lexema | TipoToken | Descripción |
|---|---|---|---|
| 37 | *(fin de archivo)* | `EOF` | Fin de archivo / entrada |