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
sensor voltaje;
sensor temperatura;
umbral maximo = 220;

si voltaje >= maximo entonces estado = PICO;

fin;
```

### Palabras reservadas

| Palabra | Uso |
|---------|-----|
| `sensor` | Declara un sensor |
| `umbral` | Declara un umbral con valor |
| `si` / `entonces` | Condicional |
| `estado` | Asignación de estado |
| `normal` / `pico` / `caida` / `inestable` | Estados del sistema |
| `abs()` | Función valor absoluto |
| `fin;` | Marca el final del programa |

### Operadores relacionales

`>` `<` `==` `>=` `<=` `!=`

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
- Diagrama del **AFD del analizador léxico** (13 estados, 17 clases de caracteres)
- **Gramática BNF** completa (10 no-terminales, 26 producciones)
- **Tabla de parsing LL(1)** con conjuntos FIRST y FOLLOW

---

## 🔧 Solución de problemas

| Problema | Solución |
|----------|----------|
| `'java' no se reconoce` | Instala JDK desde [adoptium.net](https://adoptium.net) |
| `JavaFX no encontrado` | El script lo descarga automáticamente, o descarga manual desde [gluonhq.com](https://gluonhq.com/products/javafx/) |
| `Error al compilar` | Asegúrate de tener JDK 17+ y ejecuta desde la raíz del proyecto |
| La ventana no se ve | Asegúrate de tener un monitor conectado (no funciona en SSH sin X11) |
