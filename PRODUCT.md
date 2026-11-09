# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Stack

Aplicación de escritorio JavaFX (no hay build web ni objetivo de navegador):
Java 23 + JavaFX 23.0.2, editor `CodeArea` de RichTextFX, hoja de estilos CSS
propia (`src/timotomata/ui/estilos.css`). Se ejecuta en Windows 10/11 con JDK 17+
(23 recomendado) mediante `compile_and_run_gui.bat`, o desde VS Code
(`GUI - Timotomata (ventana)`). Existe además una variante de consola
(`compile_and_run_cli.bat` / `timotomata.tools.CompiladorCLI`).

El campo `Platform` exige `web`, `ios`, `android` o `adaptive`. Al ser una app
nativa de escritorio que no es iOS ni Android, `web` es el valor de esquema más
cercano; no implica una superficie de navegador ni trabajo responsivo.

## Users

Dos audiencias confirmadas, con peso equivalente:

- **El equipo/estudiante que lo construye y lo presenta.** Lo usa para demostrar
  que el compilador funciona y para explicar cada fase en una entrega académica.
  Le importa que las tres fases, el AST, la derivación y los cuádruplos se vean
  y se entiendan.
- **El docente que lo evalúa o lo demuestra en clase.** Lo usa para escribir
  programas de ejemplo, provocar errores a propósito y mostrar cómo el
  analizador los detecta y los explica.

No se ha confirmado una audiencia de usuarios finales que programen sensores
reales fuera del contexto del curso.

## Product Purpose

Timotomata es un compilador y banco de trabajo de escritorio para un lenguaje
de dominio específico: programas que declaran sensores, umbrales, un reloj de
muestreo, cálculos de señales y reglas de estado.

Existe para que escribir, validar y corregir un programa de sensores sea rápido,
y para que el resultado del análisis sea inspeccionable fase por fase en lugar
de un simple "compila / no compila".

El éxito significa dos cosas a la vez: la herramienta resulta cómoda para
escribir y depurar programas, y sirve como evidencia académica verificable de
las fases léxica, sintáctica y semántica.

## Positioning

Lo que un "demo de compilador" genérico no podría copiar con veracidad es la
semántica del dominio: el lenguaje modela magnitudes físicas con unidad, rango
y frecuencia de muestreo, y el analizador verifica unidades, Nyquist, reglas
contradictorias, código muerto, flujo de datos entre señales y la memoria que
necesita cada cálculo. Esa ficha técnica por tipo de sensor es el mecanismo
propio del producto; la GUI solo lo hace visible.

## Operating Context

- Curso universitario de compiladores (construcción de las fases y su
  documentación). El código y los documentos son parte de la entrega.
- Windows 10/11 de 64 bits, pantalla típica de portátil (la ventana abre
  maximizada; tamaño de referencia 1200×750 y mínimo 1024×680).
- Flujo habitual: abrir o escribir un programa, ver el análisis en tiempo real,
  saltar al error, corregir, revisar tokens/símbolos/AST/derivación/cuádruplos.
- Los archivos de prueba viven en `tests/` y el análisis por lotes se hace con
  la CLI, que devuelve 0 o 1 y sirve como comprobación automatizable.
- La interfaz, la documentación y los mensajes de error están en español.

## Capabilities and Constraints

**Implementado y verificado**

- **Léxico:** AFD de 13 estados con escaneo maximal-munch; detecta typos en
  palabras reservadas y palabras reservadas incrustadas o invertidas dentro de
  identificadores largos.
- **Sintáctico:** gramática LL(1) con parser recursivo descendente y
  recuperación panic-mode que recorre todo el código; tabla de parsing,
  FIRST/FOLLOW y árbol de derivación.
- **Semántico:** 23 reglas agrupadas por función (colisiones y duplicados,
  sensor/umbral confundidos, rangos, coherencia de estados, reglas
  contradictorias, parámetros de simulación, flujo de datos, mezcla de
  magnitudes), con fichas técnicas de seis tipos de sensor.
- **Intermedio:** tabla de cuádruplos generada solo cuando el programa compila
  limpio.
- **GUI:** análisis automático con debounce (sin acción manual) y aviso de estado (ANALIZANDO… / ANALIZADO), resaltado
  por tipo de token, subrayado de errores, panel de problemas con filtros por fase
  y salto a la línea, pestañas planas TOKENS / SÍMBOLOS / AST / DERIVACIÓN /
  CUÁDRUPLOS, barra de menú (Archivo, Editar, Ver, Ayuda), barra de
  herramientas, búsqueda en el código con contador y navegación, deshacer/rehacer
  con estado sucio derivado del historial, guardado y atajos de teclado.
- **CLI:** corre las tres fases sobre uno o todos los archivos de `tests/`,
  con modo `-v` para tabla de símbolos, flujo y memoria.

**No implementado**

- **Simulación interactiva de sensores y umbrales:** el README la anuncia, pero
  no existe en el código. Es una aspiración futura confirmada, no una capacidad
  actual; no debe presentarse como hecha.

**Discrepancias conocidas de documentación (no resolver por invención)**

- El tamaño de la gramática no coincide entre documentos: el README habla de
  21 no terminales y 65 producciones; `DOCUMENTACION_LENGUAJE.md` habla de 16 no
  terminales y 54 producciones. La fuente autoritativa es `Gramatica.java` y
  `DumpTablas`, no el README.
- El README promete "simulación interactiva" (ver arriba).
- Algunas comprobaciones heurísticas adicionales del controlador están
  desactivadas a propósito (por ejemplo, detección de no declarados); el
  análisis semántico activo es el de `AnalizadorSemantico`.

## Brand Commitments

- Nombre del producto y del lenguaje: **Timotomata**.
- Idioma de producto: español (interfaz, mensajes, documentación).
- Tema visual vinculante: **Catppuccin Mocha** sobre fondo oscuro, con
  **Consolas** para código y datos monoespaciados. El usuario pidió preservarlo
  explícitamente en cualquier trabajo de diseño.

## Evidence on Hand

- `docs/analisis_lexico_sintactico.md` — AFD, gramática BNF, FIRST/FOLLOW y
  tabla LL(1).
- `docs/analisis_semantico.md` — las 23 reglas, fichas técnicas, grafo de flujo,
  código muerto e intervalos.
- `docs/TABLA_TOKENS.md`, `DOCUMENTACION_LENGUAJE.md` — referencia del lenguaje.
- `tests/` — 19 programas de prueba (válidos, errores, tipos, flujo, simulación).
- `README.md` — ejemplo completo de programa y guía de ejecución.
- `presentacion-semantico.html`, `Revision.md`,
  `Cambios_preEliminar_LYA2.md`, `EJEMPLOSSEMANTICOS.txt` — material de apoyo y
  revisión del curso.

No hay testimonios, métricas de uso, estudios de usuario ni capturas oficiales.
No inventar ninguno.

## Product Principles

1. **La evidencia manda sobre el veredicto.** Cada fase debe poder inspeccionarse
   (tokens, símbolos, AST, derivación, cuádruplos), no solo aprobar o reprobar.
2. **La corrección es del dominio.** Una unidad incompatible, un rango excedido o
   un muestreo que viola Nyquist son errores del programa, no detalles.
3. **El error se convierte en acción.** Todo mensaje debe decir qué pasó, dónde y
   llevar a la línea; el usuario no debe buscar el fallo a mano.
4. **La herramienta y su documentación son la entrega.** Cambios de producto no
   pueden romper la demostrabilidad de las tres fases ni contradecir los
   documentos técnicos.
5. **No prometer lo que no existe.** Las capacidades anunciadas (hoy, la
   simulación) se implementan antes de presentarse como disponibles.
