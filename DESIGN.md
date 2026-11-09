---
name: Compilador Timotomata
description: Banco de trabajo de escritorio para escribir, validar e inspeccionar programas de sensores.
colors:
  crust: "#11111b"
  mantle: "#181825"
  base: "#1e1e2e"
  surface0: "#313244"
  surface1: "#45475a"
  surface2: "#585b70"
  overlay0: "#6c7086"
  overlay1: "#9399b2"
  subtext1: "#a6adc8"
  text: "#cdd6f4"
  blue: "#89b4fa"
  blue-hover: "#a3c4fc"
  green: "#a6e3a1"
  red: "#f38ba8"
  yellow: "#f9e2af"
  mauve: "#cba6f7"
  sky: "#89dceb"
  peach: "#fab387"
  pink: "#f5c2e7"
typography:
  body:
    fontFamily: "Segoe UI, Helvetica Neue, sans-serif"
    fontSize: "12px"
    fontWeight: 400
  body-small:
    fontFamily: "Segoe UI, Helvetica Neue, sans-serif"
    fontSize: "11px"
    fontWeight: 400
  label:
    fontFamily: "Segoe UI, sans-serif"
    fontSize: "10px"
    fontWeight: 700
  code:
    fontFamily: "Consolas, Courier New, monospace"
    fontSize: "14px"
    fontWeight: 400
  code-data:
    fontFamily: "Consolas, monospace"
    fontSize: "11px"
    fontWeight: 400
  code-micro:
    fontFamily: "Consolas, monospace"
    fontSize: "10px"
    fontWeight: 700
rounded:
  xs: "4px"
  sm: "5px"
  md: "6px"
  lg: "8px"
  pill: "9px"
spacing:
  hairline: "1px"
  xs: "4px"
  sm: "6px"
  md: "8px"
  lg: "12px"
  xl: "16px"
components:
  button-primary:
    backgroundColor: "{colors.blue}"
    textColor: "{colors.crust}"
    rounded: "{rounded.sm}"
    padding: "4px 10px"
    typography: "{typography.body-small}"
  button-primary-hover:
    backgroundColor: "{colors.blue-hover}"
    textColor: "{colors.crust}"
  button-primary-disabled:
    backgroundColor: "{colors.surface0}"
    textColor: "{colors.subtext1}"
  button-ghost:
    backgroundColor: "transparent"
    textColor: "{colors.text}"
    rounded: "{rounded.sm}"
    padding: "4px 10px"
    typography: "{typography.body-small}"
  button-ghost-hover:
    backgroundColor: "{colors.surface0}"
    textColor: "{colors.text}"
  chip:
    backgroundColor: "transparent"
    textColor: "{colors.overlay1}"
    rounded: "{rounded.pill}"
    padding: "0px 8px"
    typography: "{typography.label}"
  chip-selected:
    backgroundColor: "rgba(137, 180, 250, 0.16)"
    textColor: "{colors.blue}"
    rounded: "{rounded.pill}"
  chip-disabled:
    backgroundColor: "transparent"
    textColor: "{colors.overlay0}"
    rounded: "{rounded.pill}"
    padding: "0px 8px"
    typography: "{typography.label}"
  input:
    backgroundColor: "{colors.surface0}"
    textColor: "{colors.text}"
    rounded: "{rounded.xs}"
    padding: "4px 6px"
    typography: "{typography.code-data}"
  find-bar:
    backgroundColor: "{colors.mantle}"
    textColor: "{colors.overlay1}"
    padding: "4px 12px"
  find-field:
    backgroundColor: "{colors.surface0}"
    textColor: "{colors.text}"
    rounded: "{rounded.xs}"
    padding: "3px 6px"
    typography: "{typography.code-data}"
  tab:
    backgroundColor: "{colors.mantle}"
    textColor: "{colors.overlay1}"
    padding: "4px 8px"
    typography: "{typography.code-micro}"
  tab-selected:
    backgroundColor: "{colors.base}"
    textColor: "{colors.text}"
    typography: "{typography.code-micro}"
---

# Design System: Compilador Timotomata

## Overview

**Creative North Star: "El banco de instrumentación"**

Timotomata se lee como un banco de instrumentos: el editor es la superficie de trabajo, la columna derecha es el juego de medidores y la barra de estado es la tira de luces indicadoras. Cada fase del compilador tiene su propio instrumento y su propia lectura; nada existe solo para decorar.

La personalidad es sobria y de instrumento. La pantalla es densa por necesidad, no por descuido: la separación entre zonas se resuelve con líneas de 1 px y cuatro pasos de fondo, nunca con sombras ni con aire generoso. El color es información: aparece cuando reporta un estado (pasa, falla, fase, selección) y se retira cuando no tiene nada que decir.

La paleta es Catppuccin Mocha usada como panel de instrumentos, no como tema de marketing. Los neutros fríos construyen la profundidad y los acentos quedan reservados para la acción primaria, la selección y las fases de error. La barra de título de la ventana es la nativa de Windows y queda fuera de este sistema.

**Key Characteristics:**
- Fondo oscuro en cuatro pasos tonales, sin sombras.
- Acento azul único para acción, selección y foco.
- Datos siempre en Consolas; lenguaje de interfaz en Segoe UI.
- Bordes de 1 px como única herramienta de separación.
- Superficies estructurales rectas; radios pequeños solo en lo interactivo.
- Color por fase de error, estable y no negociable.

## Colors

Catppuccin Mocha leída como panel de instrumentos: neutros fríos de fondo y acentos que solo existen para reportar estado.

### Primary

- **Mocha Blue** (#89b4fa): la señal de acción. Relleno del botón primario (Abrir vista completa), subrayado de la pestaña activa, borde de foco de los campos, encabezados de columna, títulos de diálogo y etiquetas de enlace. Es el único color que puede significar "esto es lo principal".
- **Mocha Blue Hover** (#a3c4fc): estado hover del botón primario; no se usa en ningún otro lugar.

### Secondary

- **Mocha Green** (#a6e3a1): el paso. Estado "Sin errores", contador en cero, delimitadores en el código y texto del árbol de derivación.
- **Mocha Red** (#f38ba8): el fallo. Mensajes de error, contador de errores, subrayado del editor, `si` / `entonces` / `sino` y los valores de estado.

### Tertiary

- **Mocha Yellow** (#f9e2af): fase léxica y operadores aritméticos y relacionales.
- **Mocha Mauve** (#cba6f7): fase semántica y palabras reservadas.
- **Mocha Sky** (#89dceb): identificadores.
- **Mocha Peach** (#fab387): literales numéricos.
- **Mocha Pink** (#f5c2e7): cursor de texto.

### Neutral

- **Mocha Crust** (#11111b): fondo de la aplicación, detrás de todo y entre divisores.
- **Mocha Mantle** (#181825): cromo de la herramienta — menú, toolbar, barra de estado, tira de pestañas, cabecera de problemas, filas alternas.
- **Mocha Base** (#1e1e2e): superficie de trabajo — editor, tablas, árbol, paneles, diálogos, pestaña activa.
- **Mocha Surface0** (#313244): bordes, separadores, relleno de controles, contorno de chips y filas seleccionadas.
- **Mocha Surface1** (#45475a): relleno presionado, selección de listas y pulgar de scrollbar.
- **Mocha Surface2** (#585b70): borde hover de los chips.
- **Mocha Overlay0** (#6c7086): comentarios del código.
- **Mocha Overlay1** (#9399b2): texto secundario, etiqueta de pestaña inactiva, pistas y etiqueta de línea.
- **Mocha Subtext1** (#a6adc8): barra de estado, estados vacíos y texto deshabilitado.
- **Mocha Text** (#cdd6f4): texto de lectura principal y código sin resaltar.

### Named Rules

**The One Signal Rule.** El azul es el único color que puede significar "acción primaria, seleccionado o enfocado". Nunca decora una superficie ni compite con otro azul en la misma pantalla.

**The Phase Color Rule.** Cada fase de error tiene un acento fijo: amarillo léxico, rojo sintáctico, lila semántico, azul sugerencia. No se recolorea una insignia por variedad ni se intercambian los papeles.

**The Tonal Depth Rule.** La profundidad sale de cuatro pasos de fondo (Crust → Mantle → Base → Surface0) y de bordes de 1 px. Si hace falta una sombra para explicar la jerarquía, la jerarquía está mal.

## Typography

**Display Font:** Segoe UI (con Helvetica Neue, sans-serif)
**Body Font:** Segoe UI (misma pila)
**Label/Mono Font:** Consolas (con Courier New, monospace)

**Character:** Dos voces: habla y mide. Segoe UI lleva la voz de la interfaz; Consolas lleva todo valor que se pueda comparar carácter por carácter.

### Hierarchy

- **Código** (400, 14 px, Consolas): el programa en el editor. Es el único texto a 14 px; nada más compite con él.
- **Dato** (400, 11 px, Consolas): tokens, símbolos, cuádruplos, AST, mensajes de problema y celdas de lista.
- **Microetiqueta** (700, 10–10.5 px, Consolas o Segoe UI): pestañas, barra de estado, chips, insignias y contadores. Mayúsculas solo cuando la cadena es una etiqueta fija.
- **Cuerpo de interfaz** (400, 11.5–12 px, Segoe UI): menús, botones de toolbar y texto de diálogo.
- **Titular de diálogo** (700, 13 px, Segoe UI, Blue): solo la cabecera de un diálogo.

### Named Rules

**The Measure Rule.** Si el usuario compara caracteres — identificadores, números, líneas, unidades — va en Consolas. Si el usuario lee una frase, va en Segoe UI. No hay excepciones decorativas.

## Layout

Banco de trabajo de escritorio fijo, sin objetivo móvil ni responsivo. La raíz es un `BorderPane`:

- **Arriba:** barra de menú (Archivo, Editar, Ver, Ayuda) y toolbar compacta, ambas sobre Mantle con un borde inferior de 1 px. La toolbar solo contiene acciones fantasma: Nuevo, Abrir, Guardar y Árbol.
- **Centro:** `SplitPane` vertical — área de trabajo arriba, panel de problemas abajo.
- **Abajo:** barra de estado de 24 px.

La barra de búsqueda (Ctrl+F) se despliega dentro del panel del editor, entre la toolbar y el código: etiqueta BUSCAR, campo fijo de 320 px, contador a la derecha y botones Anterior / Siguiente / Cerrar. Ocupa espacio solo mientras está visible; Esc la retira y devuelve el foco al código.

El área de trabajo es un `SplitPane` horizontal al 64 %: editor a la izquierda (crece con la ventana) y columna de información a la derecha (ancho fijo, ≈435 px). La columna derecha es un único `TabPane` plano de cinco pestañas de 26 px: TOKENS, SÍMBOLOS, AST, DERIVACIÓN, CUÁDRUPLOS.

El panel de problemas se abre al 74 % cuando hay errores y colapsa a una franja de ≈34 px —solo su cabecera— cuando no hay ninguno. La cabecera nunca desaparece: siempre dice cuántos problemas hay y permite filtrar.

Ritmo de espaciado: 4, 6, 8, 12 y 16 px. Los paneles usan 8 px de relleno, la cabecera de problemas 4/12, la barra de estado 3/12 y el gutter del editor 51 px fijos. La densidad es deliberada: no hay separaciones grandes ni tarjetas flotantes.

La ventana abre maximizada; su tamaño de referencia es 1200×750 y su mínimo 1024×680. Por debajo de eso el diseño no se adapta; no se inventan breakpoints.

## Elevation & Depth

Sistema plano por capas tonales. No hay sombras en CSS, ni desenfoques, ni degradados decorativos. La profundidad se comunica con cuatro pasos de fondo y un borde de 1 px:

1. **Crust** es el vacío entre zonas y el fondo de la aplicación.
2. **Mantle** es el cromo: todo lo que enmarca y no se edita.
3. **Base** es la superficie de trabajo.
4. **Surface0** es el borde o el relleno de un control que se apoya sobre Base.

Los diálogos se apoyan sobre Base con un borde de 1 px de Surface0 y sin sombra. El único degradado del producto es el corte duro del fondo del editor, que pinta la franja del gutter a la izquierda y el área de código a la derecha; no es decoración, es el borde entre dos superficies.

La única sombra real está dentro del lienzo del árbol de derivación (un círculo desplazado detrás de cada nodo) y pertenece al dibujo, no a las fichas de elevación de la interfaz.

### Named Rules

**The No-Shadow Rule.** Ninguna superficie de interfaz lleva sombra. Si dos capas necesitan separarse, se separan con un paso tonal o con una línea de 1 px.

## Shapes

Lenguaje de formas recto con radios pequeños y funcionales:

- **Radio 0:** superficies estructurales — editor, pestañas, paneles, divisores, barra de estado, lista de problemas.
- **Radio 4:** campos de texto, tooltips e insignias de fase.
- **Radio 5:** botones (toolbar y diálogo) y pulgar de scrollbar.
- **Radio 6:** menús contextuales y vista previa de derivación.
- **Radio 8:** contador de problemas.
- **Radio 9 (píldora):** chips de filtro.

Los bordes son siempre de 1 px. La selección de una pestaña se marca con un subrayado inferior de 2 px, no con una píldora rellena. No hay esquinas grandes, tarjetas redondeadas ni recortes.

## Components

### Buttons

- **Shape:** radio de 5 px, relleno de 4×10 px, Segoe UI 11.5 px.
- **Primary:** relleno Blue, texto Crust, negrita. Solo existe uno por pantalla: **Abrir vista completa**, en el panel DERIVACIÓN. Analizar no es un botón ni una acción manual: ocurre solo tras una pausa de 300 ms desde el último cambio.
- **Hover / Focus:** el primario pasa a Blue Hover; el fantasma gana relleno Surface0; presionado usa Surface1.
- **Ghost:** fondo transparente y texto Text; es el único tipo de botón de la toolbar (Nuevo, Abrir, Guardar, Árbol) y de la barra de búsqueda (Anterior, Siguiente, Cerrar).
- **Disabled:** relleno Surface0 y texto Subtext1, sin cambiar la opacidad; un botón deshabilitado sigue siendo legible.
- **Dialog buttons:** relleno Surface0, 4×16 px, 12 px; el botón por defecto repite el esquema primario (Blue / Crust).

### Chips

- **Style:** píldora de 9 px, borde de 1 px Surface1, fondo transparente, texto Overlay1 en 10 px negrita.
- **State:** seleccionado usa relleno Blue al 16 %, borde y texto Blue. Hover sube el borde a Surface2. Un chip no cambia de tamaño al seleccionarse.
- **Disabled:** cuando la fase no tiene problemas, el chip queda con borde Surface0 y texto Overlay0, sin opacidad reducida. No desaparece: la cabecera sigue mostrando las tres fases y el chip apagado dice que no hay nada que filtrar.

### Tabs

- **Style:** 26 px de alto, fondo Mantle, borde inferior transparente de 2 px, etiqueta Consolas 10 px negrita en Overlay1.
- **Selected:** fondo Base, etiqueta Text y borde inferior Blue de 2 px.
- **Hover:** fondo Base sin subrayado.
- **Behavior:** siempre cinco pestañas visibles; las etiquetas no cambian de texto para anunciar estados. El estado bloqueado se explica dentro del panel, no en la pestaña.

### Tables / Trees

- **Corner Style:** 0.
- **Background:** Base; filas alternas Mantle.
- **Border:** líneas de 1 px Surface0 entre celdas y bajo la cabecera.
- **Header:** Mantle, etiquetas Blue de 11 px en negrita.
- **Cells:** Consolas 11 px en Text; selección Surface1.
- **Empty state:** texto Subtext1 en Consolas 11.5 px, sin ilustración ni icono.

### Problems List

- **Background:** Mantle, sin rayado de filas.
- **Row:** insignia de fase (radio 4, relleno del acento al 14 %, texto del acento, Segoe UI 9 px negrita) + etiqueta de línea (Overlay1 10.5 px, mínimo 30 px) + mensaje con ajuste de texto (Text, Consolas 11.5 px).
- **Hover / Selected:** hover Base; seleccionado Surface0.
- **Header:** título PROBLEMAS en 10.5 px negrita, contador en píldora de 8 px (Surface0; Green cuando es cero), chips de filtro a la izquierda y pista alineada a la derecha.

### Status Bar

- **Style:** 24 px de alto, Mantle, relleno 3×12 px, borde superior de 1 px.
- **Items:** 11 px Subtext1; el nombre del archivo va en Text negrita; números y posición en Consolas; el estado va en 10 px negrita, Green en reposo y Red con errores.
- **Estado del análisis:** mientras hay un cambio en cola o se está ejecutando dice `ANALIZANDO…` en neutro; al terminar dice `ANALIZADO` durante 1,6 s y luego vuelve al veredicto estable `OK` (Green) o `CON ERRORES` (Red). El aviso intermedio nunca usa color de veredicto.
- **Dividers:** barras de 1×12 px en Surface0 entre grupos.

### Find Bar

- **Style:** franja Mantle de 4×12 px, borde inferior de 1 px Surface0, entre la toolbar y el editor.
- **Field:** 320 px fijos, fondo Surface0, borde Surface1 de 1 px, radio 4, Consolas 11.5 px; el foco cambia el borde a Blue sin resplandor.
- **Count:** Consolas 10.5 px Overlay1 alineado a la derecha, ancho mínimo 86 px, para que «1 de 2» y «Sin coincidencias» no desplacen los botones.
- **Layout:** etiqueta BUSCAR, campo, espaciador flexible, contador y botones fantasma Anterior / Siguiente / Cerrar.

### Editor & Gutter

- **Code:** Consolas 14 px sobre Base; el gutter ocupa 51 px fijos con el mismo Base, pintado como corte duro del degradado de fondo.
- **Syntax:** palabra reservada Mauve, control y estado Red, identificador Sky, número Peach, operador Yellow, delimitador Green, comentario Overlay0, texto plano Text.
- **Errors:** subrayado discontinuo de 1.5 px en Red con lavado del 15 %, más un punto Red en el número de línea. El ancho del gutter no cambia al marcar una línea.

### Dialogs

- **Style:** fondo Base, borde de 1 px Surface0, sin sombra.
- **Header:** panel Mantle con título Blue de 13 px negrita.
- **Body:** Consolas 12 px en Text.
- **Buttons:** Surface0; el botón por defecto en Blue con texto Crust.

## Do's and Don'ts

### Do:

- **Do** asignar cada superficie a uno de los cuatro pasos tonales y no inventar un quinto fondo.
- **Do** reservar Blue para acción primaria, selección y foco; una sola acción primaria por pantalla.
- **Do** dejar que el análisis ocurra solo (debounce de 300 ms) y usar el estado de la barra para contar qué está pasando; no hay acción manual en la interfaz.
- **Do** separar con líneas de 1 px Surface0 antes que con espacio en blanco.
- **Do** poner el programa en Consolas 14 px y todo dato comparable en Consolas 11 px.
- **Do** mantener el mapeo de color por fase: Yellow léxico, Red sintáctico, Mauve semántico, Blue sugerencia.
- **Do** colapsar el panel de problemas a su cabecera de ≈34 px cuando el contador es cero.
- **Do** mantener el gutter de 51 px fijo para que marcar un error no desplace el código.

### Don't:

- **Don't** añadir sombras, resplandores, desenfoques ni degradados decorativos; el único degradado es el corte duro del editor.
- **Don't** redondear superficies estructurales; por encima de 6 px solo existen chips (9) y el contador (8).
- **Don't** usar Blue como decoración ni como segundo acento de sintaxis.
- **Don't** añadir un botón ni un ítem de menú para analizar: duplicaría el análisis automático y competiría con la única acción primaria.
- **Don't** poner prosa en Consolas ni datos en Segoe UI.
- **Don't** introducir acentos fuera de Catppuccin Mocha.
- **Don't** animar cambios de estado: el producto no tiene tokens de movimiento y las transiciones son instantáneas.
- **Don't** cambiar el texto de una pestaña para anunciar que está bloqueada; el aviso va en el panel.
