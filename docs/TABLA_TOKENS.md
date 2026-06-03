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
| 19 | `fin` | `FIN` | Fin del programa |

## 2. Identificador

| # | Lexema(s) | TipoToken | Descripción |
|---|---|---|---|
| 20 | cualquier palabra no reservada | `ID` | Nombre de variable de usuario |

## 3. Números

| # | Lexema(s) | TipoToken | Descripción |
|---|---|---|---|
| 21 | `123`, `45.67`, `0.5`… | `NUMERO` | Valor numérico (entero o decimal) |

## 4. Signos directos (1 carácter)

| # | Carácter | TipoToken | Descripción |
|---|---|---|---|
| 22 | `+` | `MAS` | Suma / operador positivo |
| 23 | `-` | `MENOS` | Resta / operador negativo |
| 24 | `*` | `POR` | Multiplicación |
| 25 | `;` | `PUNTO_COMA` | Punto y coma |
| 26 | `(` | `PAREN_IZQ` | Paréntesis izquierdo |
| 27 | `)` | `PAREN_DER` | Paréntesis derecho |
| 28 | `,` | `COMA` | Coma |

## 5. Operadores compuestos (2 caracteres)

| # | Lexema | TipoToken | Descripción |
|---|---|---|---|
| 29 | `==` | `IGUAL_IGUAL` | Igual que |
| 30 | `>=` | `MAYOR_IGUAL` | Mayor o igual que |
| 31 | `<=` | `MENOR_IGUAL` | Menor o igual que |
| 32 | `<>` | `DIFERENTE` | Diferente |
| 33 | `!=` | `DIFERENTE` | Diferente |

## 6. Operadores unarios (1 carácter con lookahead)

| # | Carácter | TipoToken | Descripción |
|---|---|---|---|
| 34 | `=` (solo, sin `=` después) | `ASIGNACION` | Asignación |
| 35 | `>` (solo, sin `=` después) | `MAYOR` | Mayor que |
| 36 | `<` (solo, sin `=` ni `>` después) | `MENOR` | Menor que |
| 37 | `/` (solo, sin `/` ni `*` después) | `DIV` | División |

## 7. Token especial

| # | Lexema | TipoToken | Descripción |
|---|---|---|---|
| 38 | *(fin de archivo)* | `EOF` | Fin de archivo / entrada |
