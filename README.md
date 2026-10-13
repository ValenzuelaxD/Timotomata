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
sensor temperatura : TERMICO;

umbral voltaje_max : ELECTRICO = 220;
umbral voltaje_min : ELECTRICO = 100;

// El reloj del programa: segundos por muestreo. Fija el límite de Nyquist.
muestreo = 0.01;

estado = NORMAL;

si voltaje > voltaje_max entonces estado = PICO;
si voltaje < voltaje_min entonces estado = CAIDA;
si temperatura > 80 entonces estado = INESTABLE;

// El cálculo escribe en una señal derivada: el canal físico queda intacto.
calcular(voltaje, PROMEDIO(VENTANA=10), voltaje_prom);
si voltaje_prom >= voltaje_max entonces estado = PICO;

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
| `calcular` | Cálculo sobre un sensor |
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
│   │   ├── Gramatica.java        ← Gramática formal + tabla LL(1)
│   │   ├── Parser.java           ← Parser recursivo descendente
│   │   └── ast/                  ← Nodos del AST
│   │       ├── Programa.java
│   │       ├── Regla.java
│   │       ├── Expresion.java
│   │       ├── Numero.java
│   │       ├── Variable.java
│   │       ├── Binaria.java
│   │       ├── Negacion.java
│   │       └── Abs.java
│   ├── semantic/                 ← Analizador semántico
│   │   ├── AnalizadorSemantico.java  ← 20 reglas de verificación
│   │   └── TipoSensor.java       ← Ficha técnica de cada tipo de sensor
│   ├── simbolos/                 ← Tabla de símbolos
│   │   ├── Simbolo.java          ← Símbolo con sus atributos
│   │   └── TablaSimbolos.java    ← Registro único de declaraciones
│   └── ui/                       ← Interfaz gráfica (JavaFX)
│       ├── MainApp.java
│       ├── AppController.java
│       └── estilos.css
├── docs/                         ← Documentación técnica
│   └── analisis_lexico_sintactico.md
├── tests/                        ← Archivos de prueba
├── compile_and_run_gui.bat       ← Script para compilar y ejecutar
└── README.md
```

---

## 📚 Documentación técnica

Consulta [`docs/analisis_lexico_sintactico.md`](docs/analisis_lexico_sintactico.md) para:
- Diagrama del **AFD del analizador léxico** (13 estados, 19 clases de caracteres)
- **Gramática BNF** completa (16 no terminales, 52 producciones, 45 tokens)
- **Tabla de parsing LL(1)** con conjuntos FIRST y FOLLOW

---

## 🔧 Solución de problemas

| Problema | Solución |
|----------|----------|
| `'java' no se reconoce` | Instala JDK desde [adoptium.net](https://adoptium.net) |
| `JavaFX no encontrado` | El script lo descarga automáticamente, o descarga manual desde [gluonhq.com](https://gluonhq.com/products/javafx/) |
| `Error al compilar` | Asegúrate de tener JDK 17+ y ejecuta desde la raíz del proyecto |
| La ventana no se ve | Asegúrate de tener un monitor conectado (no funciona en SSH sin X11) |
