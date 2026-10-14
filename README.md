# 🧪 Compilador Timotomata

Lenguaje de programación para simulación de sensores con análisis léxico basado en **AFD** (Autómata Finito Determinista) y análisis sintáctico con **gramática LL(1)**.

## ✨ Características

- **Editor** con tema oscuro Catppuccin Mocha
- **Validación en tiempo real** (léxica y sintáctica)
- **Simulación interactiva** de sensores y umbrales
- **Visualización de tokens**, árbol AST y errores

---

## 📋 Requisitos

| Requisito | Versión |
|-----------|---------|
| **JDK** | 17 o superior (recomendado: JDK 23) |
| **Sistema** | Windows 10/11 (64-bit) |

> ⚠️ La GUI requiere **JavaFX**. El script lo descarga automáticamente si no está presente.

---

## 🚀 Cómo ejecutar

### Opción 1 — Un clic (recomendado)

Haz doble clic en el archivo:

```
compile_and_run_gui.bat
```

Esto hará todo automáticamente:
1. ✅ Detecta tu JDK instalado
2. ✅ Descarga JavaFX si no está presente
3. ✅ Compila todo el proyecto
4. ✅ Abre la ventana del compilador

Opción 2 — Desde VS Code

1. Abre la carpeta del proyecto en VS Code
2. Abre el panel **Run & Debug** (`Ctrl + Shift + D`)
3. Selecciona en el menú desplegable:
   - **`GUI - Timotomata (ventana)`** — para la interfaz gráfica

> Necesitas la extensión **"Extension Pack for Java"** de Microsoft instalada.

---

## 📝 Código de ejemplo

```timotomata
// Cada sensor declara su tipo: fija la unidad, el rango que el transductor
// puede medir y su banda de frecuencia.
sensor voltaje     : ELECTRICO;
sensor corriente   : ELECTRICO;
sensor temperatura : TERMICO;

umbral voltaje_max : ELECTRICO = 220;
umbral voltaje_min : ELECTRICO = 100;
umbral temp_max    : TERMICO   = 80;

// El reloj del programa: segundos por muestreo. Fija el límite de Nyquist.
muestreo = 0.01;

estado = NORMAL;

si voltaje > voltaje_max entonces estado = PICO;
si voltaje < voltaje_min entonces estado = CAIDA;
si temperatura > 80 entonces estado = INESTABLE;

// El cálculo escribe en una señal derivada: el canal físico queda intacto.
calcular(voltaje, PROMEDIO(VENTANA=10), voltaje_prom);
si voltaje_prom >= voltaje_max entonces estado = PICO;

si voltaje > voltaje_max y temperatura > temp_max entonces estado = INESTABLE;
si voltaje < voltaje_min o voltaje < voltaje_min entonces estado = CAIDA;
si no voltaje > voltaje_max entonces estado = NORMAL;
si estado == PICO y voltaje < voltaje_min entonces estado = CAIDA;
si voltaje > voltaje_max entonces estado = PICO sino estado = NORMAL;

// calcular también acepta cualquier expresión, que sustituye a las variables.
calcular(voltaje, abs(voltaje - 220), voltaje_desvio);
calcular(voltaje, voltaje * 2, voltaje_x2);
calcular(voltaje, (voltaje + corriente) / 2, medio);

si voltaje_desvio > 10 entonces estado = INESTABLE;

sino = NORMAL;
```

### Palabras reservadas

| Palabra | Uso |
|---------|-----|
| `sensor` | Declara un sensor con su tipo |
| `umbral` | Declara un umbral con su tipo y valor |
| `muestreo` | Declara la duración de un tick, en segundos |
| `si` / `entonces` | Condicional |
| `estado` | Estado inicial de la máquina |
| `sino` | Estado cuando ninguna regla se cumple |
| `y` / `o` / `no` | Conectores lógicos dentro de una condición |
| `calcular` | Cálculo sobre un sensor, con operación nombrada o expresión |
| `normal` / `pico` / `caida` / `inestable` | Estados del sistema |
| `abs()` | Función valor absoluto |
| `electrico` `termico` `luminico` `mecanico` `quimico` `hidraulico` | Tipos de sensor |

### Tipos de sensor

Cada tipo declara su ficha técnica, y el analizador semántico la usa para
verificar unidades, rangos y frecuencias:

| Tipo | Unidad | Rango | Muestreo | Banda | `PROMEDIO` mín. |
|------|--------|-------|----------|-------|-----------------|
| `ELECTRICO` | V | 0 – 250 | 100 Hz | 50 Hz | 5 muestras |
| `TERMICO` | °C | −40 – 125 | 0.1 Hz | 0.05 Hz | 20 muestras |
| `LUMINICO` | lux | 0 – 10 000 | 10 Hz | 5 Hz | 3 muestras |
| `MECANICO` | mm/s | 0 – 50 | 2000 Hz | 1000 Hz | 3 muestras |
| `QUIMICO` | pH | 0 – 14 | 0.01 Hz | 0.005 Hz | 50 muestras |
| `HIDRAULICO` | bar | 0 – 400 | 1 Hz | 0.5 Hz | 10 muestras |

### Operadores relacionales

`>` `<` `==` `>=` `<=` `!=` `<>`

### Operadores aritméticos

`+` `-` `*` `/`

---

## 🧱 Estructura del proyecto

```
Timotomata/
├── src/timotomata/
│   ├── lexer/                    ← Analizador léxico (AFD)
│   │   ├── Lexer.java
│   │   ├── Token.java
│   │   └── TipoToken.java
│   ├── parser/                   ← Analizador sintáctico (LL(1))
│   │   ├── Gramatica.java        ← Gramática formal + tabla LL(1) + detección de conflictos
│   │   ├── Parser.java           ← Parser recursivo descendente
│   │   ├── DumpTablas.java       ← Vuelca las tablas de la gramática en markdown
│   │   └── ast/                  ← Nodos del AST
│   │       ├── Programa.java     ← Raíz del AST + tabla de símbolos
│   │       ├── Sensor.java
│   │       ├── Regla.java
│   │       ├── Calculo.java      ← calcular(): operación, expresión, orígenes y memoria
│   │       ├── Expresion.java
│   │       ├── Numero.java
│   │       ├── Variable.java
│   │       ├── Binaria.java
│   │       ├── Negacion.java
│   │       └── Abs.java
│   ├── semantic/                 ← Analizador semántico
│   │   ├── AnalizadorSemantico.java  ← 25 reglas de verificación
│   │   └── TipoSensor.java       ← Ficha técnica de cada tipo de sensor
│   ├── simbolos/                 ← Tabla de símbolos y flujo de datos
│   │   ├── Simbolo.java          ← Símbolo con sus atributos y orígenes
│   │   └── TablaSimbolos.java    ← Registro único de declaraciones
│   ├── tools/                    ← Utilidades sin interfaz
│   │   └── CompiladorCLI.java    ← Corre las 3 fases y devuelve texto plano
│   └── ui/                       ← Interfaz gráfica (JavaFX)
│       ├── MainApp.java
│       ├── AppController.java
│       └── estilos.css
├── docs/                         ← Documentación técnica
│   ├── analisis_lexico_sintactico.md  ← AFD, gramática, FIRST/FOLLOW, tabla LL(1)
│   └── analisis_semantico.md         ← Las 23 reglas y el grafo de flujo de datos
├── tests/                        ← Archivos de prueba
├── compile_and_run_gui.bat       ← Compila y abre la interfaz
├── compile_and_run_cli.bat       ← Compila y analiza sin interfaz
└── README.md
```

---

## 📚 Documentación técnica

**[`docs/analisis_lexico_sintactico.md`](docs/analisis_lexico_sintactico.md)**
- Diagrama del **AFD del analizador léxico** (13 estados, 19 clases de caracteres)
- **Gramática BNF** completa (21 no terminales, 65 producciones, 48 tokens)
- **Tabla de parsing LL(1)** con conjuntos FIRST y FOLLOW
- Las dos formas de transformar en `calcular`: operación nombrada o expresión

**[`docs/analisis_semantico.md`](docs/analisis_semantico.md)**
- Las **23 reglas** de verificación, agrupadas por lo que hacen
- **Fichas técnicas** de los seis tipos de sensor y para qué sirve cada campo
- **Grafo de flujo de datos**: qué señal depende de cuál
- **Código muerto**: qué reglas nunca se ejecutan, y qué valores no disparan nada
- **Memoria** que cada cálculo necesita (buffer, fase, nada)
- Análisis de reglas por **intervalos**: contradicciones y código muerto

Las tres tablas de la segunda parte del documento léxico/sintáctico se
regeneran con `java timotomata.parser.DumpTablas tablas.md`.

---

## ⌨️ Compilador de línea de comandos

Para analizar sin abrir la interfaz:

```
compile_and_run_cli.bat                          → todos los archivos de tests\
compile_and_run_cli.bat tests\test_tipos.txt     → un archivo
compile_and_run_cli.bat -v tests\test_tipos.txt  → además tabla, flujo y memoria
```

Devuelve 0 si todo compila y 1 si hay errores, así que sirve como comprobación
automatizable: `compile_and_run_cli.bat || echo FALLO`.

---

## 🔧 Solución de problemas

| Problema | Solución |
|----------|----------|
| `'java' no se reconoce` | Instala JDK desde [adoptium.net](https://adoptium.net) |
| `JavaFX no encontrado` | El script lo descarga automáticamente, o descarga manual desde [gluonhq.com](https://gluonhq.com/products/javafx/) |
| `Error al compilar` | Asegúrate de tener JDK 17+ y ejecuta desde la raíz del proyecto |
| La ventana no se ve | Asegúrate de tener un monitor conectado (no funciona en SSH sin X11) |
