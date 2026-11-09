package timotomata.ui;

import java.io.*;
import java.util.*;
import java.util.stream.*;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.*;
import javafx.css.PseudoClass;
import javafx.geometry.*;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

import java.util.function.IntFunction;

import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import timotomata.intermedio.Cuadruplo;
import timotomata.intermedio.GeneradorCuadruplos;
import timotomata.intermedio.TablaCuadruplos;
import timotomata.lexer.*;
import timotomata.parser.*;
import timotomata.parser.ast.*;
import timotomata.semantic.AnalizadorSemantico;
import timotomata.simbolos.Simbolo;

/**
 * Controlador principal de la interfaz gráfica.
 * Gestiona el editor de código, el análisis en tiempo real,
 * la visualización de tokens/AST y el árbol de derivación.
 */
public class AppController {

    // ─── Modelo de datos para la tabla de tokens (3 columnas) ───
    public static class TokenInfo {
        private final StringProperty tipo = new SimpleStringProperty();
        private final StringProperty lexema = new SimpleStringProperty();
        private final IntegerProperty linea = new SimpleIntegerProperty();

        public TokenInfo(String tipo, String lexema, int linea) {
            this.tipo.set(tipo);
            this.lexema.set(lexema);
            this.linea.set(linea);
        }
        public StringProperty tipoProperty() { return tipo; }
        public StringProperty lexemaProperty() { return lexema; }
        public IntegerProperty lineaProperty() { return linea; }
    }

    // ─── Modelo de datos para la tabla de símbolos ───
    public static class InfoSimbolo {
        private final StringProperty nombre = new SimpleStringProperty();
        private final StringProperty clase = new SimpleStringProperty();
        private final StringProperty tipo = new SimpleStringProperty();
        private final StringProperty unidad = new SimpleStringProperty();
        private final StringProperty valor = new SimpleStringProperty();
        private final IntegerProperty linea = new SimpleIntegerProperty();

        public InfoSimbolo(String nombre, String clase, String tipo,
                           String unidad, String valor, int linea) {
            this.nombre.set(nombre);
            this.clase.set(clase);
            this.tipo.set(tipo);
            this.unidad.set(unidad);
            this.valor.set(valor);
            this.linea.set(linea);
        }
        public StringProperty nombreProperty() { return nombre; }
        public StringProperty claseProperty() { return clase; }
        public StringProperty tipoProperty() { return tipo; }
        public StringProperty unidadProperty() { return unidad; }
        public StringProperty valorProperty() { return valor; }
        public IntegerProperty lineaProperty() { return linea; }
    }

    // ─── Modelo de datos para el panel de problemas ───
    public static class ErrorItem {
        public final String fase;
        public final int linea;
        public final String mensaje;

        public ErrorItem(String fase, int linea, String mensaje) {
            this.fase = fase;
            this.linea = linea;
            this.mensaje = mensaje;
        }
    }

    // ─── Componentes de la UI ───
    private BorderPane root;
    private Stage stage;
    private CodeArea editor;
    private Label lblArchivo;
    private Label lblPosicion;
    private Label lblTokens;
    private Label lblErrores;
    private Label lblEstado;
    private SplitPane splitVertical;
    private ListView<ErrorItem> errorList;
    private VBox problemasPanel;
    private Label problemasTitulo;
    private Label problemasCount;
    private final ObservableList<ErrorItem> erroresData = FXCollections.observableArrayList();
    private final List<ErrorItem> erroresTodos = new ArrayList<>();
    private ToggleButton filtroLex;
    private ToggleButton filtroSint;
    private ToggleButton filtroSem;
    private Boolean ultimoHayProblemas = null;
    private Label errorEmpty;
    private MenuItem itemArbol;
    private Button botonArbol;
    private TableView<TokenInfo> tokenTable;
    private ObservableList<TokenInfo> tokenData = FXCollections.observableArrayList();
    private TreeView<String> astTree;
    private StackPane astContenedor;
    private Label astEmpty;
    private VBox derivacionPanel;
    private PauseTransition debounce;
    private PauseTransition avisoAnalizado;
    private boolean ultimoConErrores = false;

    // ─── Buscador del editor ───
    private HBox barraBusqueda;
    private TextField campoBusqueda;
    private Label lblBusqueda;

    // ─── Tabla de símbolos ───
    private TableView<InfoSimbolo> tablaSimbolos;
    private ObservableList<InfoSimbolo> simbolosData = FXCollections.observableArrayList();

    // ─── Cuádruplos (código intermedio) ───
    private TableView<Cuadruplo> tablaCuadruplos;
    private ObservableList<Cuadruplo> cuadruplosData = FXCollections.observableArrayList();

    // ─── Estado del documento ───
    private boolean dirty = false;
    private boolean cargandoDocumento = false;

    // ─── Palabras reservadas para sugerencias ───
    private static final String[] PALABRAS_RESERVADAS = {
        "sensor", "umbral", "si", "entonces", "estado", "sino", "muestreo",
        "abs", "calcular", "normal", "pico", "caida", "inestable",
        "seno", "coseno", "cuadrada", "promedio", "maximo", "suma",
        "amplitud", "frecuencia", "ventana", "con",
        "electrico", "termico", "luminico", "mecanico", "quimico", "hidraulico"
    };

    // ─── Margen del gutter (números de línea) ───
    // El ancho total del gutter vive en estilos.css (.tm-line-number) para que
    // el número quede siempre a la misma distancia del código. Aquí solo se
    // fija el ancho del punto, que debe reservarse aunque esté invisible.
    private static final double ANCHO_PUNTO = 9;

    /** Ancho total de la franja del gutter. DEBE coincidir con el
     *  -fx-pref-width de `.tm-line-number` en estilos.css: es el punto donde
     *  termina la franja y arranca el área de código. */
    private static final double ANCHO_GUTTER = 51;

    /** Fondo de los números de línea sin error (franja del gutter). */
    private static final String COLOR_GUTTER = "#181825";
    /** Fondo del área de código. */
    private static final String COLOR_CODIGO = "#1e1e2e";

    /** Líneas mínimas que se muestran en el gutter. Los números existen
     *  únicamente para párrafos reales: si el programa tiene 11 líneas, solo
     *  hay 11 números. Para que la columna se vea siempre igual de larga se
     *  rellena el documento con líneas vacías hasta llegar a este mínimo.
     *  El relleno es solo visual: se recorta al guardar (ver textoAGuardar).
     *
     *  <p>Es un mínimo, no un tope: un programa más largo que esto genera los
     *  párrafos que haga falta y el gutter crece con él. El valor solo decide
     *  cuánto hueco se ve por debajo del código antes de empezar a escribir. */
    private static final int MIN_LINEAS_GUTTER = 100;

    /** Cuántas líneas tenía el contenido real la última vez que se cargó.
     *  Todo lo que haya por debajo y esté vacío es relleno. */
    private int lineasReales = 1;

    // ─── Estado interno ───
    private Programa ultimoPrograma;
    private Parser ultimoParser;
    private String archivoActual = null;

    // ─── Marcas de error en el editor ───
    private final Set<Integer> lineasConError = new HashSet<>();
    private final Map<Integer, String> mensajeErrorPorLinea = new HashMap<>();

    // =============================================================
    //  CONSTRUCTOR
    // =============================================================
    public AppController() {
        crearUI();
        configurarDebounce();
        cargarEjemploPorDefecto();
        // Análisis inicial automático
        analizarCodigo();
    }

    public BorderPane getRoot() { return root; }

    // =============================================================
    //  CONSTRUCCIÓN DE LA INTERFAZ
    // =============================================================
    private void crearUI() {
        root = new BorderPane();
        root.getStyleClass().add("root");

        // El editor se crea antes que el menú porque el menú Editar enlaza sus
        // estados de deshacer/rehacer y de selección a este CodeArea.
        VBox panelEditor = crearPanelEditor();

        // ─── Cabecera: menú + toolbar ───
        VBox cabecera = new VBox(0, crearMenuBar(), crearToolBar());
        root.setTop(cabecera);

        // ─── Centro: editor + paneles, con problemas redimensionables ───
        SplitPane splitPrincipal = new SplitPane();
        splitPrincipal.getItems().addAll(panelEditor, crearPanelInfo());
        splitPrincipal.setDividerPositions(0.64);
        SplitPane.setResizableWithParent(splitPrincipal.getItems().get(0), true);
        SplitPane.setResizableWithParent(splitPrincipal.getItems().get(1), false);

        splitVertical = new SplitPane();
        splitVertical.setOrientation(Orientation.VERTICAL);
        splitVertical.getItems().addAll(splitPrincipal, crearPanelErrores());
        splitVertical.setDividerPositions(0.80);
        SplitPane.setResizableWithParent(splitPrincipal, true);

        root.setCenter(splitVertical);
        root.setBottom(crearStatusBar());
    }

    // ─── Menú superior ───
    private MenuBar crearMenuBar() {
        MenuBar bar = new MenuBar();

        Menu archivo = new Menu("Archivo");
        MenuItem nuevo = itemMenu("Nuevo", "Shortcut+N", this::nuevoArchivo);
        MenuItem abrir = itemMenu("Abrir…", "Shortcut+O", this::abrirArchivo);
        MenuItem guardar = itemMenu("Guardar", "Shortcut+S", this::guardarArchivo);
        MenuItem guardarComo = itemMenu("Guardar como…", "Shortcut+Shift+S", this::guardarArchivoComo);
        MenuItem salir = itemMenu("Salir", null, this::salir);
        archivo.getItems().addAll(nuevo, abrir, guardar, guardarComo,
            new SeparatorMenuItem(), salir);

        Menu editar = new Menu("Editar");
        MenuItem deshacer = itemMenu("Deshacer", "Shortcut+Z", () -> editor.undo());
        deshacer.disableProperty().bind(Bindings.createBooleanBinding(
            () -> !editor.isUndoAvailable(), editor.getUndoManager().undoAvailableProperty()));
        MenuItem rehacer = itemMenu("Rehacer", "Shortcut+Y", () -> editor.redo());
        rehacer.disableProperty().bind(Bindings.createBooleanBinding(
            () -> !editor.isRedoAvailable(), editor.getUndoManager().redoAvailableProperty()));
        MenuItem cortar = itemMenu("Cortar", "Shortcut+X", () -> editor.cut());
        cortar.disableProperty().bind(Bindings.createBooleanBinding(
            () -> editor.getSelectedText().isEmpty(), editor.selectedTextProperty()));
        MenuItem copiar = itemMenu("Copiar", "Shortcut+C", () -> editor.copy());
        copiar.disableProperty().bind(Bindings.createBooleanBinding(
            () -> editor.getSelectedText().isEmpty(), editor.selectedTextProperty()));
        MenuItem pegar = itemMenu("Pegar", "Shortcut+V", () -> editor.paste());
        MenuItem seleccionarTodo = itemMenu("Seleccionar todo", "Shortcut+A", () -> editor.selectAll());
        MenuItem buscar = itemMenu("Buscar…", "Shortcut+F", this::mostrarBusqueda);
        editar.getItems().addAll(deshacer, rehacer,
            new SeparatorMenuItem(), cortar, copiar, pegar,
            new SeparatorMenuItem(), seleccionarTodo,
            new SeparatorMenuItem(), buscar);

        Menu ver = new Menu("Ver");
        MenuItem enfocarEditor = itemMenu("Enfocar editor", "Shortcut+1", () -> editor.requestFocus());
        MenuItem problemas = itemMenu("Panel de problemas", "Shortcut+E", this::enfocarProblemas);
        itemArbol = itemMenu("Ver árbol de derivación", "F6", this::abrirArbolDerivacion);
        ver.getItems().addAll(enfocarEditor, problemas,
            new SeparatorMenuItem(), itemArbol);

        Menu ayuda = new Menu("Ayuda");
        MenuItem atajos = itemMenu("Atajos de teclado", "F1", this::mostrarAtajos);
        MenuItem acerca = itemMenu("Acerca de Timotomata", null, this::mostrarAcercaDe);
        ayuda.getItems().addAll(atajos, acerca);

        bar.getMenus().addAll(archivo, editar, ver, ayuda);
        return bar;
    }

    private MenuItem itemMenu(String texto, String atajo, Runnable accion) {
        MenuItem item = new MenuItem(texto);
        if (atajo != null) {
            item.setAccelerator(KeyCombination.keyCombination(atajo));
        }
        item.setOnAction(e -> accion.run());
        return item;
    }

    // ─── Toolbar compacta ───
    private ToolBar crearToolBar() {
        ToolBar bar = new ToolBar();
        bar.getStyleClass().add("tm-toolbar");

        Button nuevo = botonBarra("Nuevo", "Ctrl+N", this::nuevoArchivo);
        Button abrir = botonBarra("Abrir", "Ctrl+O", this::abrirArchivo);
        Button guardar = botonBarra("Guardar", "Ctrl+S", this::guardarArchivo);
        botonArbol = botonBarra("Árbol", "F6", this::abrirArbolDerivacion);

        bar.getItems().addAll(nuevo, abrir, guardar,
            new Separator(Orientation.VERTICAL), botonArbol);
        return bar;
    }

    private Button botonBarra(String texto, String atajo, Runnable accion) {
        Button boton = new Button(texto);
        boton.getStyleClass().add("tool-button");
        boton.setTooltip(new Tooltip(texto + "  ·  " + atajo));
        boton.setOnAction(e -> accion.run());
        boton.setFocusTraversable(false);
        return boton;
    }

    // ─── Panel del editor (con números de línea a la izquierda) ───
    private VBox crearPanelEditor() {
        VBox panel = new VBox(0);
        panel.setStyle("-fx-background-color: " + COLOR_CODIGO + ";");

        editor = new CodeArea();
        editor.getStyleClass().add("syntax-editor");
        // El fondo se pinta como un degradado con un corte duro a ANCHO_GUTTER:
        // así la franja oscura del gutter llega hasta el borde inferior de la
        // ventana en vez de terminar en la última línea con código, que es lo
        // que producía el escalón de color. El degradado vive en el CodeArea
        // porque los contenedores internos de RichTextFX/Flowless están
        // transparentizados en estilos.css y dejan ver este fondo.
        editor.setStyle("-fx-background-color: linear-gradient(to right,"
            + " " + COLOR_GUTTER + " 0px,"
            + " " + COLOR_GUTTER + " " + ANCHO_GUTTER + "px,"
            + " " + COLOR_CODIGO + " " + ANCHO_GUTTER + "px,"
            + " " + COLOR_CODIGO + " 100%);"
            + " -fx-highlight-fill: rgba(137, 180, 250, 0.22);"
            + " -fx-highlight-text-fill: #cdd6f4;"
            + " -fx-font-family: 'Consolas', 'Courier New', monospace;"
            + " -fx-font-size: 14px;"
            + " -fx-line-spacing: 2px;"
            + " -fx-border-color: transparent;"
            + " -fx-padding: 4px 8px 4px 0;"
            + " -fx-background-insets: 0;"
            + " -fx-background-radius: 0;");
        editor.setWrapText(false);
        editor.useInitialStyleForInsertionProperty().set(false);
        editor.setTextInsertionStyle(Collections.singleton("tm-plain"));
        configurarNumerosDeLinea();

        // Solo los cambios de texto programan el análisis; mover el cursor o
        // seleccionar no reconstruye tokens ni estilos.
        editor.textProperty().addListener((obs, oldText, newText) -> {
            if (barraBusqueda != null && barraBusqueda.isVisible()) {
                actualizarContadorBusqueda();
            }
            if (debounce != null) {
                marcarAnalizando();
                debounce.playFromStart();
            }
        });
        editor.caretPositionProperty().addListener((obs, oldPos, newPos) ->
            actualizarStatusCursor());

        // El estado sucio se deriva de la posición del UndoManager, no de un
        // booleano que solo se apaga al guardar: deshacer hasta el punto de
        // guardado vuelve a dejar el documento limpio.
        editor.getUndoManager().atMarkedPositionProperty().addListener((obs, antes, ahora) -> {
            if (cargandoDocumento) return;
            boolean nuevoDirty = !ahora;
            if (nuevoDirty != dirty) {
                dirty = nuevoDirty;
                actualizarEtiquetaArchivo();
            }
        });

        // RichTextFX usa Ctrl+Shift+Z para rehacer; el menú anuncia Ctrl+Y.
        // Se aceptan las dos combinaciones.
        editor.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.isShortcutDown() && e.isShiftDown() && e.getCode() == KeyCode.Z) {
                editor.redo();
                e.consume();
            }
        });

        panel.getChildren().addAll(crearBarraBusqueda(), editor);
        VBox.setVgrow(editor, Priority.ALWAYS);
        return panel;
    }

    /**
     * Barra de búsqueda del editor. Vive sobre el código y solo ocupa espacio
     * cuando está visible; Ctrl+F la muestra, Esc la cierra.
     */
    private HBox crearBarraBusqueda() {
        barraBusqueda = new HBox(8);
        barraBusqueda.getStyleClass().add("tm-find-bar");
        barraBusqueda.setAlignment(Pos.CENTER_LEFT);
        barraBusqueda.setVisible(false);
        barraBusqueda.setManaged(false);

        Label etiqueta = new Label("BUSCAR");
        etiqueta.getStyleClass().add("tm-find-label");

        campoBusqueda = new TextField();
        campoBusqueda.getStyleClass().add("tm-find-field");
        campoBusqueda.setPromptText("Texto en el código");
        campoBusqueda.setPrefWidth(320);
        campoBusqueda.setMaxWidth(320);
        campoBusqueda.textProperty().addListener((obs, antes, ahora) ->
            actualizarContadorBusqueda());
        campoBusqueda.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                ocultarBusqueda();
                e.consume();
            } else if (e.getCode() == KeyCode.ENTER) {
                buscarSiguiente(!e.isShiftDown());
                e.consume();
            }
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        lblBusqueda = new Label();
        lblBusqueda.getStyleClass().add("tm-find-count");

        Button anterior = botonBarra("Anterior", "Shift+Enter", () -> buscarSiguiente(false));
        Button siguiente = botonBarra("Siguiente", "Enter", () -> buscarSiguiente(true));
        Button cerrar = botonBarra("Cerrar", "Esc", this::ocultarBusqueda);

        barraBusqueda.getChildren().addAll(etiqueta, campoBusqueda, spacer,
            lblBusqueda, anterior, siguiente, cerrar);
        return barraBusqueda;
    }

    private void mostrarBusqueda() {
        if (barraBusqueda == null) return;
        String seleccion = editor.getSelectedText();
        if (seleccion != null && !seleccion.isEmpty() && !seleccion.contains("\n")) {
            campoBusqueda.setText(seleccion);
        }
        barraBusqueda.setVisible(true);
        barraBusqueda.setManaged(true);
        campoBusqueda.requestFocus();
        campoBusqueda.selectAll();
        if (campoBusqueda.getText().isEmpty()) {
            lblBusqueda.setText("");
        } else {
            actualizarContadorBusqueda();
        }
    }

    private void ocultarBusqueda() {
        if (barraBusqueda == null) return;
        barraBusqueda.setVisible(false);
        barraBusqueda.setManaged(false);
        editor.requestFocus();
    }

    /** Recalcula el contador y resalta la primera coincidencia desde el cursor. */
    private void actualizarContadorBusqueda() {
        if (editor == null || campoBusqueda == null || lblBusqueda == null) return;
        String consulta = campoBusqueda.getText();
        if (consulta == null || consulta.isEmpty()) {
            lblBusqueda.setText("");
            return;
        }
        String texto = editor.getText();
        int total = contarCoincidencias(texto, consulta);
        if (total == 0) {
            lblBusqueda.setText("Sin coincidencias");
            return;
        }
        int desde = Math.max(0, editor.getSelection().getStart());
        int pos = indiceDe(texto, consulta, desde, true);
        if (pos < 0) pos = indiceDe(texto, consulta, 0, true);
        editor.selectRange(pos, pos + consulta.length());
        editor.requestFollowCaret();
        lblBusqueda.setText((indiceCoincidencia(texto, consulta, pos) + 1)
            + " de " + total);
    }

    private void buscarSiguiente(boolean adelante) {
        if (editor == null || campoBusqueda == null) return;
        String consulta = campoBusqueda.getText();
        if (consulta == null || consulta.isEmpty()) return;

        String texto = editor.getText();
        int total = contarCoincidencias(texto, consulta);
        if (total == 0) {
            lblBusqueda.setText("Sin coincidencias");
            return;
        }

        int pos;
        if (adelante) {
            int desde = editor.getSelection().getEnd();
            pos = indiceDe(texto, consulta, desde, true);
            if (pos < 0) pos = indiceDe(texto, consulta, 0, true);
        } else {
            int hasta = editor.getSelection().getStart();
            pos = indiceDe(texto, consulta, hasta, false);
            if (pos < 0) pos = indiceDe(texto, consulta, texto.length(), false);
        }
        editor.selectRange(pos, pos + consulta.length());
        editor.requestFollowCaret();
        lblBusqueda.setText((indiceCoincidencia(texto, consulta, pos) + 1)
            + " de " + total);
    }

    private static int indiceDe(String texto, String consulta, int desde, boolean adelante) {
        String t = texto.toLowerCase();
        String q = consulta.toLowerCase();
        if (adelante) return t.indexOf(q, Math.max(0, desde));
        return t.lastIndexOf(q, Math.max(0, desde - 1));
    }

    private static int contarCoincidencias(String texto, String consulta) {
        if (texto == null || consulta == null || consulta.isEmpty()) return 0;
        String t = texto.toLowerCase();
        String q = consulta.toLowerCase();
        int total = 0;
        int i = t.indexOf(q);
        while (i >= 0) {
            total++;
            i = t.indexOf(q, i + q.length());
        }
        return total;
    }

    private static int indiceCoincidencia(String texto, String consulta, int posicion) {
        String t = texto.toLowerCase();
        String q = consulta.toLowerCase();
        int indice = 0;
        int i = t.indexOf(q);
        while (i >= 0 && i < posicion) {
            indice++;
            i = t.indexOf(q, i + q.length());
        }
        return indice;
    }

    /** Reutiliza LineNumberFactory y sincroniza el color con el párrafo activo.
     * Además marca con icono rojo las líneas que tienen errores (ver
     * actualizarIconosLinea): el número sale en rojo con fondo tenue y tooltip
     * con el primer mensaje de error de esa línea. */
    private void configurarNumerosDeLinea() {
        IntFunction<Node> numerosBase = LineNumberFactory.get(editor);
        PseudoClass lineaActual = PseudoClass.getPseudoClass("current-line");
        var parrafoActual = editor.getCaretSelectionBind().paragraphIndexProperty();

        editor.setParagraphGraphicFactory(indice -> {
            Node nodo = numerosBase.apply(indice);
            if (nodo instanceof Label numero) {
                numero.getStyleClass().add("tm-line-number");
                Label punto = new Label("●");
                punto.setTextFill(Color.web("#f38ba8"));
                punto.setStyle("-fx-font-size: 9px;");
                // Ancho fijo: el punto debe reservar su hueco aunque no se vea.
                punto.setPadding(Insets.EMPTY);
                punto.setMinWidth(ANCHO_PUNTO);
                punto.setPrefWidth(ANCHO_PUNTO);
                punto.setAlignment(Pos.CENTER);
                // El gráfico se asigna una sola vez y NUNCA se quita. Si se
                // hiciese setGraphic(null) el Label encogería, RichTextFX
                // reservaría menos margen y el código de esa línea se
                // desplazaría hacia la derecha. La visibilidad se controla
                // con opacidad, que sí conserva el espacio en el layout.
                numero.setGraphic(punto);
                Runnable actualizar = () -> {
                    boolean activo = parrafoActual.getValue() == indice;
                    int linea = indice + 1;
                    boolean conError = lineasConError.contains(linea);
                    numero.pseudoClassStateChanged(lineaActual, activo);
                    if (conError && !activo) {
                        numero.setBackground(new Background(new BackgroundFill(
                            Color.web("rgba(243, 139, 168, 0.16)"),
                            CornerRadii.EMPTY, Insets.EMPTY)));
} else {
                        numero.setBackground(new Background(new BackgroundFill(
                            Color.web(activo ? "rgba(137, 180, 250, 0.22)"
                                : COLOR_GUTTER),
                            CornerRadii.EMPTY, Insets.EMPTY)));
                    }
                    numero.setTextFill(Color.web(conError ? "#f38ba8"
                        : (activo ? "#cdd6f4" : "#6c7086")));
                    numero.setStyle(conError ? "-fx-font-weight: bold;" : "");
                    punto.setOpacity(conError ? 1.0 : 0.0);
                    String detalle = mensajeErrorPorLinea.get(linea);
                    numero.setTooltip(conError && detalle != null
                        ? new Tooltip("⚠ " + detalle) : null);
                };
                parrafoActual.addListener((obs, anterior, nuevo) -> actualizar.run());
                actualizar.run();
            }
            return nodo;
        });
    }

    /**
     * Recalcula qué líneas tienen errores (para los iconos del gutter) a partir
     * de los mensajes "en linea N". Solo re-crea los gráficos si el conjunto
     * cambió, para no acumular listeners en cada análisis.
     */
    private void actualizarIconosLinea(List<String> erroresLex, List<String> erroresSint,
                                       List<String> erroresSemanticos) {
        Set<Integer> nuevas = new HashSet<>();
        Map<Integer, String> mensajes = new HashMap<>();
        List<String> todos = new ArrayList<>();
        if (erroresLex != null) todos.addAll(erroresLex);
        if (erroresSint != null) todos.addAll(erroresSint);
        if (erroresSemanticos != null) todos.addAll(erroresSemanticos);
        for (String err : todos) {
            String lower = err.toLowerCase();
            int idx = lower.indexOf("en linea ");
            if (idx < 0) continue;
            int ini = idx + "en linea ".length();
            int fin = ini;
            while (fin < err.length() && Character.isDigit(err.charAt(fin))) fin++;
            if (fin == ini) continue;
            int linea;
            try {
                linea = Integer.parseInt(err.substring(ini, fin));
            } catch (NumberFormatException ex) {
                continue;
            }
            nuevas.add(linea);
            mensajes.putIfAbsent(linea, err);
        }
        if (nuevas.equals(lineasConError)) {
            mensajeErrorPorLinea.clear();
            mensajeErrorPorLinea.putAll(mensajes);
            return;
        }
        lineasConError.clear();
        lineasConError.addAll(nuevas);
        mensajeErrorPorLinea.clear();
        mensajeErrorPorLinea.putAll(mensajes);
        var fabrica = editor.getParagraphGraphicFactory();
        editor.setParagraphGraphicFactory(null);
        editor.setParagraphGraphicFactory(fabrica);
    }

    // ─── Panel derecho con pestañas ───
    private TabPane crearPanelInfo() {
        // Panel de Tokens — 3 columnas: Tipo, Lexema, Línea
        tokenTable = new TableView<>(tokenData);
        tokenTable.setPlaceholder(placeholder("Sin tokens"));
        tokenTable.setStyle("-fx-control-inner-background: #1e1e2e;"
            + " -fx-table-cell-border-color: #313244;"
            + " -fx-text-fill: #cdd6f4;"
            + " -fx-font-family: 'Consolas', monospace;"
            + " -fx-font-size: 11px;");

        TableColumn<TokenInfo, String> colTipo = new TableColumn<>("Tipo");
        colTipo.setCellValueFactory(new PropertyValueFactory<>("tipo"));
        colTipo.setPrefWidth(130);
        colTipo.setCellFactory(col -> new TableCell<TokenInfo, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                setTextFill(empty ? Color.TRANSPARENT : Color.web(colorParaToken(item)));
            }
        });

        TableColumn<TokenInfo, String> colLexema = new TableColumn<>("Lexema");
        colLexema.setCellValueFactory(new PropertyValueFactory<>("lexema"));
        colLexema.setPrefWidth(130);
        colLexema.setCellFactory(col -> new TableCell<TokenInfo, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                    setTextFill(Color.TRANSPARENT);
                } else {
                    String tipo = getTableView().getItems().get(getIndex()).tipoProperty().get();
                    setTextFill(Color.web(colorParaToken(tipo)));
                }
            }
        });

        TableColumn<TokenInfo, Number> colLinea = new TableColumn<>("Línea");
        colLinea.setCellValueFactory(new PropertyValueFactory<>("linea"));
        colLinea.setPrefWidth(55);
        colLinea.setStyle("-fx-alignment: CENTER-RIGHT;");

        tokenTable.getColumns().addAll(colTipo, colLexema, colLinea);
        tokenTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        // ─── Tabla de Símbolos (6 columnas) ───
        tablaSimbolos = new TableView<>(simbolosData);
        tablaSimbolos.setPlaceholder(placeholder("Sin símbolos"));
        tablaSimbolos.setStyle("-fx-control-inner-background: #1e1e2e;"
            + " -fx-table-cell-border-color: #313244;"
            + " -fx-text-fill: #cdd6f4;"
            + " -fx-font-family: 'Consolas', monospace;"
            + " -fx-font-size: 11px;");

        TableColumn<InfoSimbolo, String> colSimbNombre = new TableColumn<>("Nombre");
        colSimbNombre.setCellValueFactory(new PropertyValueFactory<>("nombre"));
        colSimbNombre.setPrefWidth(96);

        TableColumn<InfoSimbolo, String> colSimbClase = new TableColumn<>("Clase");
        colSimbClase.setCellValueFactory(new PropertyValueFactory<>("clase"));
        colSimbClase.setPrefWidth(60);

        TableColumn<InfoSimbolo, String> colSimbTipo = new TableColumn<>("Tipo");
        colSimbTipo.setCellValueFactory(new PropertyValueFactory<>("tipo"));
        colSimbTipo.setPrefWidth(80);

        TableColumn<InfoSimbolo, String> colSimbUnidad = new TableColumn<>("Unidad");
        colSimbUnidad.setCellValueFactory(new PropertyValueFactory<>("unidad"));
        colSimbUnidad.setPrefWidth(54);

        TableColumn<InfoSimbolo, Number> colSimbLinea = new TableColumn<>("Línea");
        colSimbLinea.setCellValueFactory(new PropertyValueFactory<>("linea"));
        colSimbLinea.setPrefWidth(48);
        colSimbLinea.setMinWidth(48);
        colSimbLinea.setStyle("-fx-alignment: CENTER-RIGHT;");

        TableColumn<InfoSimbolo, String> colSimbValor = new TableColumn<>("Valor");
        colSimbValor.setCellValueFactory(new PropertyValueFactory<>("valor"));
        colSimbValor.setPrefWidth(52);

        tablaSimbolos.getColumns().addAll(colSimbNombre, colSimbClase,
            colSimbTipo, colSimbUnidad, colSimbValor, colSimbLinea);
        tablaSimbolos.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        // ─── AST ───
        astTree = new TreeView<>();
        astTree.setStyle("-fx-control-inner-background: #1e1e2e;"
            + " -fx-text-fill: #cdd6f4;"
            + " -fx-font-family: 'Consolas', monospace;"
            + " -fx-font-size: 11px;"
            + " -fx-accent: #45475a;");
        astTree.setShowRoot(false);

        astEmpty = new Label("El AST aparece cuando el código compila");
        astEmpty.getStyleClass().add("tm-empty");
        astEmpty.setMouseTransparent(true);
        astContenedor = new StackPane(astTree, astEmpty);
        StackPane.setAlignment(astEmpty, Pos.CENTER);

        // ─── Árbol de derivación ───
        crearPanelDerivacion();

        // ─── Cuádruplos (código intermedio) ───
        tablaCuadruplos = new TableView<>(cuadruplosData);
        tablaCuadruplos.setPlaceholder(placeholder("Los cuádruplos se generan cuando el código compila"));
        tablaCuadruplos.setStyle("-fx-control-inner-background: #1e1e2e;"
            + " -fx-table-cell-border-color: #313244;"
            + " -fx-text-fill: #cdd6f4;"
            + " -fx-font-family: 'Consolas', monospace;"
            + " -fx-font-size: 11px;");

        TableColumn<Cuadruplo, Number> colCuadIndice = new TableColumn<>("#");
        colCuadIndice.setCellValueFactory(
            cd -> new ReadOnlyIntegerWrapper(cd.getValue().indice));
        colCuadIndice.setPrefWidth(45);
        colCuadIndice.setStyle("-fx-alignment: CENTER-RIGHT;");

        TableColumn<Cuadruplo, String> colCuadOp = new TableColumn<>("Op");
        colCuadOp.setCellValueFactory(
            cd -> new ReadOnlyStringWrapper(cd.getValue().getOp()));
        colCuadOp.setPrefWidth(80);

        TableColumn<Cuadruplo, String> colCuadArg1 = new TableColumn<>("Arg1");
        colCuadArg1.setCellValueFactory(
            cd -> new ReadOnlyStringWrapper(cd.getValue().getArg1()));
        colCuadArg1.setPrefWidth(90);

        TableColumn<Cuadruplo, String> colCuadArg2 = new TableColumn<>("Arg2");
        colCuadArg2.setCellValueFactory(
            cd -> new ReadOnlyStringWrapper(cd.getValue().getArg2()));
        colCuadArg2.setPrefWidth(90);

        TableColumn<Cuadruplo, String> colCuadResultado = new TableColumn<>("Resultado");
        colCuadResultado.setCellValueFactory(
            cd -> new ReadOnlyStringWrapper(cd.getValue().getResultado()));
        colCuadResultado.setPrefWidth(90);

        TableColumn<Cuadruplo, Number> colCuadLinea = new TableColumn<>("Línea");
        colCuadLinea.setCellValueFactory(
            cd -> new ReadOnlyIntegerWrapper(cd.getValue().linea));
        colCuadLinea.setPrefWidth(55);
        colCuadLinea.setStyle("-fx-alignment: CENTER-RIGHT;");
        // Las instrucciones sintetizadas (etiquetas, fin de programa) no
        // pertenecen a ninguna línea del fuente: se muestran como «—» en vez
        // de un 0 que parece un número de línea real.
        colCuadLinea.setCellFactory(col -> new TableCell<Cuadruplo, Number>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                int linea = item.intValue();
                setText(linea > 0 ? String.valueOf(linea) : "—");
                setTextFill(Color.web(linea > 0 ? "#cdd6f4" : "#6c7086"));
            }
        });

        tablaCuadruplos.getColumns().addAll(colCuadIndice, colCuadOp, colCuadArg1,
            colCuadArg2, colCuadResultado, colCuadLinea);
        tablaCuadruplos.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        // ─── Una sola columna de pestañas planas para todo el panel derecho ───
        TabPane pestanas = new TabPane(
            new Tab("TOKENS", envolver(tokenTable)),
            new Tab("SÍMBOLOS", envolver(tablaSimbolos)),
            new Tab("AST", envolver(astContenedor)),
            new Tab("DERIVACIÓN", envolver(derivacionPanel)),
            new Tab("CUÁDRUPLOS", envolver(tablaCuadruplos))
        );
        pestanas.getStyleClass().add("tm-tab-pane");
        pestanas.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        return pestanas;
    }

    /** Envuelve el contenido de una pestaña con el relleno común del panel. */
    private VBox envolver(Region contenido) {
        VBox caja = new VBox(contenido);
        caja.getStyleClass().add("tm-panel-tab");
        VBox.setVgrow(contenido, Priority.ALWAYS);
        return caja;
    }

    // ─── Panel de problemas (franja inferior redimensionable) ───
    private VBox crearPanelErrores() {
        problemasPanel = new VBox(0);
        problemasPanel.getStyleClass().add("tm-problems");

        problemasTitulo = new Label("PROBLEMAS");
        problemasTitulo.getStyleClass().add("tm-problems-title");

        problemasCount = new Label("0");
        problemasCount.getStyleClass().add("tm-problems-count");

        filtroLex = chip("LÉXICO");
        filtroSint = chip("SINTÁCTICO");
        filtroSem = chip("SEMÁNTICO");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label ayuda = new Label("Doble clic en un problema para ir a la línea");
        ayuda.getStyleClass().add("tm-problems-hint");

        HBox cabecera = new HBox(8, problemasTitulo, problemasCount,
            filtroLex, filtroSint, filtroSem, spacer, ayuda);
        cabecera.setAlignment(Pos.CENTER_LEFT);
        cabecera.getStyleClass().add("tm-problems-header");

        errorList = new ListView<>(erroresData);
        errorList.getStyleClass().add("tm-error-list");
        errorEmpty = placeholder("Sin problemas");
        errorList.setPlaceholder(errorEmpty);
        errorList.setCellFactory(lv -> celdaError());
        errorList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) irAlSeleccionado();
        });
        errorList.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) irAlSeleccionado();
        });
        VBox.setVgrow(errorList, Priority.ALWAYS);

        problemasPanel.getChildren().addAll(cabecera, errorList);
        return problemasPanel;
    }

    private ToggleButton chip(String texto) {
        ToggleButton chip = new ToggleButton(texto);
        chip.getStyleClass().add("tm-chip");
        chip.setSelected(true);
        chip.setFocusTraversable(false);
        chip.selectedProperty().addListener((obs, antes, ahora) -> aplicarFiltroErrores());
        return chip;
    }

    private ListCell<ErrorItem> celdaError() {
        return new ListCell<>() {
            private final Label insignia = new Label();
            private final Label linea = new Label();
            private final Label mensaje = new Label();
            private final HBox caja = new HBox(10, insignia, linea, mensaje);

            {
                insignia.getStyleClass().add("tm-error-badge");
                insignia.setMinWidth(Region.USE_PREF_SIZE);
                insignia.setMaxWidth(Region.USE_PREF_SIZE);
                linea.getStyleClass().add("tm-error-line");
                linea.setMinWidth(Region.USE_PREF_SIZE);
                mensaje.getStyleClass().add("tm-error-message");
                mensaje.setWrapText(true);
                mensaje.setMinWidth(0);
                mensaje.setMaxWidth(Double.MAX_VALUE);
                caja.setAlignment(Pos.CENTER_LEFT);
                HBox.setHgrow(mensaje, Priority.ALWAYS);
                // Ancho 0: la celda se estira al ancho del ListView y el
                // mensaje envuelve en vez de provocar scroll horizontal.
                setPrefWidth(0);
                getStyleClass().add("tm-error-cell");
            }

            @Override
            protected void updateItem(ErrorItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                insignia.setText(item.fase);
                insignia.getStyleClass().removeAll("fase-lexico", "fase-sintactico",
                    "fase-semantico", "fase-sugerencia");
                insignia.getStyleClass().add(claseFase(item.fase));
                linea.setText(item.linea > 0 ? "L" + item.linea : "");
                mensaje.setText(item.mensaje);
                setGraphic(caja);
            }
        };
    }

    private static String claseFase(String fase) {
        return switch (fase) {
            case "LÉXICO" -> "fase-lexico";
            case "SINTÁCTICO" -> "fase-sintactico";
            case "SEMÁNTICO" -> "fase-semantico";
            default -> "fase-sugerencia";
        };
    }

    // ─── Panel del árbol de derivación (pestaña derecha) ───
    private VBox crearPanelDerivacion() {
        derivacionPanel = new VBox(10);
        derivacionPanel.getStyleClass().add("tm-derivacion");
        derivacionPanel.setPadding(new Insets(12));
        return derivacionPanel;
    }

    // ─── Barra de estado inferior ───
    private HBox crearStatusBar() {
        HBox statusBar = new HBox(10);
        statusBar.getStyleClass().add("tm-status-bar");
        statusBar.setAlignment(Pos.CENTER_LEFT);

        lblArchivo = new Label("Sin archivo");
        lblArchivo.getStyleClass().addAll("tm-status-item", "tm-status-file");

        lblPosicion = new Label("Ln 1, Col 1");
        lblPosicion.getStyleClass().addAll("tm-status-item", "tm-status-mono");

        lblTokens = new Label("0 tokens");
        lblTokens.getStyleClass().addAll("tm-status-item", "tm-status-mono");

        lblErrores = new Label("Sin analizar");
        lblErrores.getStyleClass().addAll("tm-status-item", "tm-status-mono");

        lblEstado = new Label("LISTO");
        lblEstado.getStyleClass().addAll("tm-status-item", "tm-status-state");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        statusBar.getChildren().addAll(lblArchivo, divisorStatus(), spacer,
            lblPosicion, divisorStatus(), lblTokens, divisorStatus(),
            lblErrores, divisorStatus(), lblEstado);
        return statusBar;
    }

    private Region divisorStatus() {
        Region divisor = new Region();
        divisor.getStyleClass().add("tm-status-divider");
        return divisor;
    }

    // =============================================================
    //  CONFIGURACIÓN DEL DEBOUNCE (300ms)
    // =============================================================
    private void configurarDebounce() {
        debounce = new PauseTransition(Duration.millis(300));
        debounce.setOnFinished(e -> analizarCodigo());
    }

    /**
     * Anuncia que hay un análisis en cola. El debounce tarda 300 ms; sin este
     * aviso la barra de estado sigue mostrando el veredicto del análisis
     * anterior y el usuario no sabe si el cambio ya se evaluó.
     */
    private void marcarAnalizando() {
        if (lblEstado == null) return;
        lblEstado.setText("ANALIZANDO…");
        marcarEstado(lblEstado, false);
        if (avisoAnalizado != null) avisoAnalizado.stop();
    }

    /** Deja «ANALIZADO» un momento y luego vuelve al veredicto estable. */
    private void programarFinAvisoAnalisis() {
        if (avisoAnalizado == null) {
            avisoAnalizado = new PauseTransition(Duration.seconds(1.6));
            avisoAnalizado.setOnFinished(e -> {
                if (lblEstado == null) return;
                lblEstado.setText(ultimoConErrores ? "CON ERRORES" : "OK");
                marcarEstado(lblEstado, ultimoConErrores);
            });
        }
        avisoAnalizado.playFromStart();
    }

    // =============================================================
    //  ANÁLISIS EN TIEMPO REAL
    // =============================================================
    public void analizarCodigo() {
        marcarAnalizando();
        String codigo = editor.getText();

        // ─── Fase 1: Análisis Léxico ───
        Lexer lexer = new Lexer(codigo);
        List<Token> tokens;
        try {
            tokens = lexer.escanear();
        } catch (Exception e) {
            mostrarError("Error en análisis léxico: " + e.getMessage());
            return;
        }

        List<String> erroresLex = new ArrayList<>(lexer.getErroresLexicos());

        // El lexer clasifica una palabra desconocida como ID. Esta segunda
        // comprobación léxica detecta únicamente typos en posiciones donde
        // la gramática esperaba una palabra reservada.
        Map<String, String> erroresReservadas = detectarReservadasMalEscritas(tokens);
        erroresLex.addAll(erroresReservadas.values());

        // ─── Fase 2: Análisis Sintáctico (con recuperación de errores) ───
        Parser parser = new Parser(tokens);
        Programa programa = parser.parsear();
        List<String> erroresSint = parser.getErroresSintacticos();

        // Algunos typos solo pueden confirmarse con el contexto que el
        // parser esperaba. Se reclasifican como léxicos y se filtra solo
        // el error sintáctico duplicado que menciona ese mismo lexema.
        // Los demás errores sintácticos SIEMPRE se muestran (recorrido completo).
        Map<String, String> erroresReservadasPost =
            detectarReservadasDesdeErrores(tokens, erroresSint);
        for (Map.Entry<String, String> entry : erroresReservadasPost.entrySet()) {
            if (!erroresReservadas.containsKey(entry.getKey())) {
                erroresReservadas.put(entry.getKey(), entry.getValue());
                erroresLex.add(entry.getValue());
            }
        }

        // Reservada incluida o invertida dentro de un ID largo
        // (ej. 'seno1356515asdasdasad' contiene 'seno',
        // 'asdasdones6465465' contiene 'ones' = 'seno' al revés).
        // Un error léxico por cada ocurrencia.
        Map<String, List<String>> erroresIncluidos =
            detectarReservadaIncluida(tokens, erroresReservadas.keySet(),
                                      nombresDeclarados(programa));
        for (List<String> lista : erroresIncluidos.values()) {
            erroresLex.addAll(lista);
        }

        // Mostrar TODOS los errores sin duplicar lo redundante.
        // - Caso redundante (solo LÉXICO): la gramática no se completa SOLO por
        //   el typo, ej. 'estdao' donde se esperaba ESTADO. Al corregir la palabra
        //   todo parsea bien, así que el sintáctico sobra y se oculta.
        // - Caso parámetros (AMBOS): ej. SENO(AMPLTU) tiene léxico (palabra mal
        //   escrita) Y sintáctico (faltan los 2 parámetros bien definidos, pues
        //   aun corregida la palabra la estructura sigue incompleta).
        // El parser ya usa recuperación panic-mode y recorre todo el código.
        Set<String> lexemasLexicos = new HashSet<>(erroresReservadas.keySet());
        lexemasLexicos.addAll(erroresIncluidos.keySet());
        List<String> erroresSintParaUI = filtrarSintacticosDuplicados(erroresSint, lexemasLexicos, tokens);

        ultimoPrograma = programa;
        ultimoParser = parser;

        // ─── Fase 3: Análisis Semántico ───
        // Colisiones de nombres, duplicados, sensor/umbral confundidos, rangos
        // inválidos, estados incoherentes, reglas contradictorias, parámetros de
        // simulación, flujo de datos y magnitudes mezcladas.
        construirTablaSimbolos(programa, tokens);
        List<String> erroresSemanticos;
        try {
            erroresSemanticos = new AnalizadorSemantico()
                .analizar(programa, tokens);
        } catch (Exception e) {
            erroresSemanticos = new ArrayList<>();
            mostrarError("Error en análisis semántico: " + e.getMessage());
        }

        // ─── Fase 4: Sugerencias de palabras reservadas mal escritas ───
        List<String> sugerenciasReservadas = Collections.emptyList();

        // ─── Actualizar UI ───
        // Marcas en el código: subrayado rojo en tokens con error + iconos
        // en los números de línea. Se pinta al final porque necesita todos
        // los errores ya calculados (léxico + sintáctico + semántico).
        Set<String> lexemasConErrorLex = new HashSet<>(erroresReservadas.keySet());
        lexemasConErrorLex.addAll(erroresIncluidos.keySet());
        Set<String> clavesSintacticas = extraerClavesSintacticas(erroresSint);
        boolean[] charsConErrorLex = marcarCharsErrorLexico(codigo, lexer.getRangosErrorLexico());
        actualizarEstilosEditor(codigo, tokens, lexemasConErrorLex, clavesSintacticas, charsConErrorLex);
        actualizarIconosLinea(erroresLex, erroresSintParaUI, erroresSemanticos);
        actualizarErrores(erroresLex, erroresSintParaUI, erroresSemanticos, sugerenciasReservadas);
        actualizarTokens(tokens);
        boolean codigoValido = erroresLex.isEmpty()
            && erroresSintParaUI.isEmpty()
            && erroresSemanticos.isEmpty();
        actualizarAST(codigoValido ? programa : null);
        actualizarCuadruplos(codigoValido ? programa : null);
        actualizarDerivacion(codigoValido ? programa : null,
            codigoValido ? parser : null);
        int tokensReales = 0;
        for (Token t : tokens) {
            if (t.tipo != TipoToken.EOF) tokensReales++;
        }
        actualizarStatus(tokensReales, erroresLex.size(), erroresSintParaUI.size(),
            erroresSemanticos.size() + sugerenciasReservadas.size());
    }

    // =============================================================
    //  ACTUALIZACIÓN DE LA UI
    // =============================================================

    // ─── Errores ───
    private void actualizarErrores(List<String> erroresLex, List<String> erroresSint,
                                    List<String> erroresSemanticos, List<String> sugerencias) {
        erroresTodos.clear();
        agregarErrores(erroresLex, "LÉXICO");
        agregarErrores(sugerencias, "SUGERENCIA");
        agregarErrores(erroresSint, "SINTÁCTICO");
        agregarErrores(erroresSemanticos, "SEMÁNTICO");

        // Un filtro sin problemas de su fase no es una acción: se apaga para
        // que la cabecera diga la verdad sobre lo que se puede filtrar.
        int nLex = 0, nSint = 0, nSem = 0;
        for (ErrorItem item : erroresTodos) {
            switch (item.fase) {
                case "LÉXICO", "SUGERENCIA" -> nLex++;
                case "SINTÁCTICO" -> nSint++;
                case "SEMÁNTICO" -> nSem++;
                default -> { }
            }
        }
        filtroLex.setDisable(nLex == 0);
        filtroSint.setDisable(nSint == 0);
        filtroSem.setDisable(nSem == 0);

        aplicarFiltroErrores();
    }

    private void agregarErrores(List<String> mensajes, String fase) {
        if (mensajes == null) return;
        for (String mensaje : mensajes) {
            erroresTodos.add(new ErrorItem(fase, lineaDe(mensaje), limpiarMensaje(mensaje)));
        }
    }

    /** Extrae el número de línea de mensajes del tipo "… en linea 12: …". */
    private static int lineaDe(String mensaje) {
        if (mensaje == null) return 0;
        String lower = mensaje.toLowerCase();
        int idx = lower.indexOf("en linea ");
        if (idx < 0) return 0;
        int ini = idx + "en linea ".length();
        int fin = ini;
        while (fin < mensaje.length() && Character.isDigit(mensaje.charAt(fin))) fin++;
        if (fin == ini) return 0;
        try {
            return Integer.parseInt(mensaje.substring(ini, fin));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    /** Quita el prefijo "Error léxico en linea N:" que ya muestran la insignia y la línea. */
    private static String limpiarMensaje(String mensaje) {
        if (mensaje == null) return "";
        int idx = mensaje.indexOf(": ");
        if (idx > 0 && idx < 80) {
            String prefijo = mensaje.substring(0, idx).toLowerCase();
            if (prefijo.contains("error") || prefijo.contains("sugerencia")) {
                return mensaje.substring(idx + 2).trim();
            }
        }
        return mensaje;
    }

    private boolean pasaFiltro(ErrorItem item) {
        return switch (item.fase) {
            case "LÉXICO", "SUGERENCIA" -> filtroLex.isSelected();
            case "SINTÁCTICO" -> filtroSint.isSelected();
            case "SEMÁNTICO" -> filtroSem.isSelected();
            default -> true;
        };
    }

    private void aplicarFiltroErrores() {
        if (errorList == null || filtroLex == null) return;

        List<ErrorItem> visibles = new ArrayList<>();
        for (ErrorItem item : erroresTodos) {
            if (pasaFiltro(item)) visibles.add(item);
        }
        erroresData.setAll(visibles);

        int total = erroresTodos.size();
        int mostrados = visibles.size();
        problemasCount.setText(mostrados == total
            ? String.valueOf(total) : mostrados + " de " + total);
        if (errorEmpty != null) {
            errorEmpty.setText(total == 0
                ? "Sin problemas"
                : "Ningún problema coincide con los filtros");
        }

        problemasPanel.pseudoClassStateChanged(PseudoClass.getPseudoClass("ok"), total == 0);
        ajustarPanelProblemas(total > 0);
    }

    /**
     * Abre o colapsa la franja de problemas. Solo actúa cuando el estado
     * cambia (hay ↔ no hay) para no pelearse con el divisor que arrastra
     * el usuario en cada análisis.
     */
    private void ajustarPanelProblemas(boolean hay) {
        if (splitVertical == null || errorList == null) return;
        if (ultimoHayProblemas != null && ultimoHayProblemas == hay) return;
        ultimoHayProblemas = hay;

        errorList.setVisible(hay);
        errorList.setManaged(hay);

        Platform.runLater(() -> {
            if (hay) {
                splitVertical.setDividerPositions(0.74);
                return;
            }
            double alto = splitVertical.getHeight();
            double franja = 34.0;
            splitVertical.setDividerPositions(alto > 0
                ? Math.max(0.5, 1 - franja / alto) : 0.96);
        });
    }

    private void irAlSeleccionado() {
        ErrorItem item = errorList.getSelectionModel().getSelectedItem();
        if (item != null) irALinea(item.linea);
    }

    private void irALinea(int linea) {
        if (linea <= 0) return;
        int indice = Math.max(0, Math.min(linea - 1, editor.getParagraphs().size() - 1));
        editor.moveTo(indice, 0);
        // Seleccionar la línea completa hace visible el destino del salto; solo
        // mover el cursor deja al usuario sin saber dónde aterrizó.
        editor.selectParagraph();
        editor.requestFollowCaret();
        editor.requestFocus();
    }

    private void enfocarProblemas() {
        if (errorList == null || !errorList.isVisible()) return;
        if (errorList.getSelectionModel().isEmpty() && !erroresData.isEmpty()) {
            errorList.getSelectionModel().selectFirst();
        }
        errorList.requestFocus();
    }

    // ─── Tokens (con columna de línea) ───
    private void actualizarTokens(List<Token> tokens) {
        tokenData.clear();
        for (Token t : tokens) {
            if (t.tipo == TipoToken.EOF) continue;
            tokenData.add(new TokenInfo(
                t.tipo.name(),
                t.lexema,
                t.linea
            ));
        }
    }

    /**
     * Aplica estilos por rango al mismo CodeArea que recibe la edición.
     * Además del color por tipo de token, suma la clase "tm-error" (subrayado
     * rojo) a los tokens con error léxico (typos) o sintáctico, y a los
     * caracteres con error léxico nativo (ej. '@', '!' suelto).
     */
    private void actualizarEstilosEditor(String codigo, List<Token> tokens,
                                         Set<String> lexemasConErrorLex,
                                         Set<String> clavesSintacticas,
                                         boolean[] charsConErrorLex) {
        StyleSpansBuilder<Collection<String>> estilos = new StyleSpansBuilder<>();
        int cursor = 0;

        for (Token token : tokens) {
            if (token.tipo == TipoToken.EOF || token.lexema.isEmpty()) continue;

            int inicio = buscarInicioToken(codigo, token, cursor);
            if (inicio < cursor) inicio = codigo.indexOf(token.lexema, cursor);
            if (inicio < 0) continue;

            agregarEstilosDeTexto(codigo.substring(cursor, inicio), estilos,
                cursor, charsConErrorLex);
            String base = claseParaToken(token.tipo.name());
            boolean conError = (token.tipo == TipoToken.ID
                    && lexemasConErrorLex.contains(token.lexema.toLowerCase()))
                || clavesSintacticas.contains(token.linea + "\0" + token.lexema);
            if (conError) {
                estilos.add(new HashSet<>(Arrays.asList(base, "tm-error")), token.lexema.length());
            } else {
                estilos.add(Collections.singleton(base), token.lexema.length());
            }
            cursor = inicio + token.lexema.length();
        }
        agregarEstilosDeTexto(codigo.substring(Math.min(cursor, codigo.length())), estilos,
            Math.min(cursor, codigo.length()), charsConErrorLex);
        editor.setStyleSpans(0, estilos.create());
    }

    /** Marca los rangos de error léxico nativo como arreglo por carácter. */
    private boolean[] marcarCharsErrorLexico(String codigo, List<int[]> rangos) {
        boolean[] marcado = new boolean[codigo.length()];
        if (rangos == null) return marcado;
        for (int[] r : rangos) {
            if (r == null || r.length < 2) continue;
            int desde = Math.max(0, r[0]);
            int hasta = Math.min(codigo.length(), r[1]);
            for (int i = desde; i < hasta; i++) marcado[i] = true;
        }
        return marcado;
    }

    /**
     * Extrae claves "linea + lexema encontrado" de cada error sintáctico.
     * Solo se usa el lexema después de "pero se encontró" (el token real con
     * problema), no el del contexto ("después de 'X'").
     */
    private Set<String> extraerClavesSintacticas(List<String> erroresSint) {
        Set<String> claves = new HashSet<>();
        if (erroresSint == null) return claves;
        for (String err : erroresSint) {
            String lower = err.toLowerCase();
            if (!lower.contains("pero se encontr")) continue;
            int iniLinea = lower.indexOf("en linea ");
            if (iniLinea < 0) continue;
            int iniNum = iniLinea + "en linea ".length();
            int finNum = iniNum;
            while (finNum < err.length() && Character.isDigit(err.charAt(finNum))) finNum++;
            if (finNum == iniNum) continue;
            int linea;
            try {
                linea = Integer.parseInt(err.substring(iniNum, finNum));
            } catch (NumberFormatException ex) {
                continue;
            }
            int ultComilla = err.lastIndexOf('\'');
            int penComilla = ultComilla > 0 ? err.lastIndexOf('\'', ultComilla - 1) : -1;
            if (penComilla < 0 || ultComilla <= penComilla) continue;
            String lexema = err.substring(penComilla + 1, ultComilla);
            if (!lexema.isEmpty()) claves.add(linea + "\0" + lexema);
        }
        return claves;
    }

    /** Emite un tramo de texto plano/comentario partido por error léxico. */
    private void agregarEstilosDeTexto(String texto,
                                       StyleSpansBuilder<Collection<String>> estilos,
                                       int offsetBase, boolean[] charsConErrorLex) {
        if (texto.isEmpty()) {
            estilos.add(Collections.emptyList(), 0);
            return;
        }
        agregarRegionConErrores(texto, 0, texto.length(), Collections.emptyList(),
            estilos, offsetBase, charsConErrorLex);
    }

    /** Emite el tramo [desde, hasta) partido por comentario y por error léxico. */
    private void agregarRegionConErrores(String texto, int desde, int hasta,
                                         Collection<String> base,
                                         StyleSpansBuilder<Collection<String>> estilos,
                                         int offsetBase, boolean[] charsConErrorLex) {
        int cursor = desde;
        while (cursor < hasta) {
            int linea = texto.indexOf("//", cursor);
            int bloque = texto.indexOf("/*", cursor);
            if ((linea < 0 || linea >= hasta) && (bloque < 0 || bloque >= hasta)) {
                emitirTramo(texto, cursor, hasta, base, estilos, offsetBase, charsConErrorLex);
                return;
            }
            int inicioComentario;
            boolean esLinea;
            if (linea < 0 || linea >= hasta) {
                inicioComentario = bloque;
                esLinea = false;
            } else if (bloque < 0 || bloque >= hasta || linea < bloque) {
                inicioComentario = linea;
                esLinea = true;
            } else {
                inicioComentario = bloque;
                esLinea = false;
            }
            if (inicioComentario > cursor) {
                emitirTramo(texto, cursor, inicioComentario, base, estilos, offsetBase, charsConErrorLex);
            }
            int fin;
            if (esLinea) {
                fin = texto.indexOf('\n', inicioComentario);
                if (fin < 0 || fin > hasta) fin = hasta;
            } else {
                fin = texto.indexOf("*/", inicioComentario + 2);
                fin = (fin < 0 || fin + 2 > hasta) ? hasta : fin + 2;
            }
            emitirTramo(texto, inicioComentario, fin, Collections.singleton("tm-comment"),
                estilos, offsetBase, charsConErrorLex);
            cursor = fin;
        }
    }

    /** Emite el tramo partido solo por error léxico (subrayado rojo). */
    private void emitirTramo(String texto, int desde, int hasta, Collection<String> base,
                             StyleSpansBuilder<Collection<String>> estilos,
                             int offsetBase, boolean[] charsConErrorLex) {
        int i = desde;
        while (i < hasta) {
            boolean conError = tieneErrorLex(offsetBase + i, charsConErrorLex);
            int j = i + 1;
            while (j < hasta && tieneErrorLex(offsetBase + j, charsConErrorLex) == conError) j++;
            if (conError) {
                HashSet<String> clases = new HashSet<>(base);
                clases.add("tm-error");
                estilos.add(clases, j - i);
            } else if (base.isEmpty()) {
                estilos.add(Collections.emptyList(), j - i);
            } else {
                estilos.add(base, j - i);
            }
            i = j;
        }
    }

    private boolean tieneErrorLex(int offset, boolean[] charsConErrorLex) {
        return charsConErrorLex != null && offset >= 0
            && offset < charsConErrorLex.length && charsConErrorLex[offset];
    }

    /** Busca el token por su línea/columna y verifica el lexema antes de usarlo. */
    private int buscarInicioToken(String codigo, Token token, int desde) {
        int linea = 1;
        int inicioLinea = 0;
        for (int i = 0; i < codigo.length() && linea < token.linea; i++) {
            if (codigo.charAt(i) == '\n') {
                linea++;
                inicioLinea = i + 1;
            }
        }

        int candidato = inicioLinea + Math.max(0, token.columna - 1);
        if (candidato >= desde
                && candidato + token.lexema.length() <= codigo.length()
                && codigo.startsWith(token.lexema, candidato)) {
            return candidato;
        }
        return codigo.indexOf(token.lexema, desde);
    }

    /**
     * Clasificación visual basada exclusivamente en TipoToken.
     * El lexer y el parser no dependen de estos colores.
     */
    private String colorParaToken(String tipo) {
        if (tipo == null) return "#cdd6f4";
        if (tipo.equals("ID")) return "#89dceb";
        if (tipo.equals("NUMERO")) return "#fab387";
        if (tipo.equals("ESTADO_SISTEMA")) return "#f38ba8";
        if (tipo.equals("SI") || tipo.equals("ENTONCES") || tipo.equals("ESTADO")) {
            return "#f38ba8";
        }
        if (tipo.equals("MAYOR") || tipo.equals("MENOR")
                || tipo.equals("IGUAL_IGUAL") || tipo.equals("MAYOR_IGUAL")
                || tipo.equals("MENOR_IGUAL") || tipo.equals("DIFERENTE")
                || tipo.equals("MAS") || tipo.equals("MENOS")
                || tipo.equals("POR") || tipo.equals("DIV")
                || tipo.equals("ASIGNACION")) {
            return "#f9e2af";
        }
        if (tipo.equals("PUNTO_COMA") || tipo.equals("COMA")
                || tipo.equals("PAREN_IZQ") || tipo.equals("PAREN_DER")) {
            return "#a6e3a1";
        }
        return "#cba6f7";
    }

    private String claseParaToken(String tipo) {
        if ("ID".equals(tipo)) return "tm-identifier";
        if ("NUMERO".equals(tipo)) return "tm-number";
        if ("ESTADO_SISTEMA".equals(tipo)) return "tm-state";
        if (tipo.equals("SI") || tipo.equals("ENTONCES") || tipo.equals("ESTADO")) {
            return "tm-control";
        }
        if (tipo.equals("MAYOR") || tipo.equals("MENOR")
                || tipo.equals("IGUAL_IGUAL") || tipo.equals("MAYOR_IGUAL")
                || tipo.equals("MENOR_IGUAL") || tipo.equals("DIFERENTE")
                || tipo.equals("MAS") || tipo.equals("MENOS")
                || tipo.equals("POR") || tipo.equals("DIV")
                || tipo.equals("ASIGNACION")) {
            return "tm-operator";
        }
        if (tipo.equals("PUNTO_COMA") || tipo.equals("COMA")
                || tipo.equals("PAREN_IZQ") || tipo.equals("PAREN_DER")) {
            return "tm-delimiter";
        }
        return "tm-keyword";
    }

    // ─── Cuádruplos ───
    /**
     * Vuelca la tabla de cuadruplos del programa.
     *
     * <p>Solo se genera si el programa compiló limpio: un AST con errores
     * semánticos puede tener construcciones incompletas y la tabla mostraría
     * instrucciones que el programa real nunca ejecutaría.
     */
    private void actualizarCuadruplos(Programa programa) {
        if (programa == null) {
            cuadruplosData.clear();
            tablaCuadruplos.setPlaceholder(placeholder("Los cuádruplos se generan cuando el código compila"));
            return;
        }
        try {
            TablaCuadruplos tabla = new GeneradorCuadruplos().generar(programa);
            cuadruplosData.setAll(tabla.cuadruplos());
            tablaCuadruplos.setPlaceholder(placeholder("Sin cuadruplos"));
        } catch (RuntimeException e) {
            // Esto corre en cada debounce: nada de dialogos modales. El aviso
            // va en el placeholder de la tabla, que es donde se mira.
            cuadruplosData.clear();
            tablaCuadruplos.setPlaceholder(placeholder("No se pudieron generar los cuádruplos"));
        }
    }

    private static Label placeholder(String texto) {
        Label etiqueta = new Label(texto);
        etiqueta.getStyleClass().add("tm-empty");
        return etiqueta;
    }

    // ─── AST ───
    private void actualizarAST(Programa programa) {
        if (programa == null) {
            astTree.setRoot(null);
            astEmpty.setText("El AST se bloquea mientras haya errores");
            astEmpty.setVisible(true);
            return;
        }

        astEmpty.setVisible(false);

        TreeItem<String> raiz = new TreeItem<>("Programa");
        raiz.setExpanded(true);

        // Sensores
        TreeItem<String> sensores = new TreeItem<>("Sensores: "
            + (programa.sensores.isEmpty() ? "(ninguno)"
                : String.join(", ", programa.sensores)));
        sensores.setExpanded(true);
        raiz.getChildren().add(sensores);

        // Umbrales
        if (programa.umbrales.isEmpty()) {
            raiz.getChildren().add(new TreeItem<>("Umbrales: (ninguno)"));
        } else {
            TreeItem<String> umbrales = new TreeItem<>("Umbrales");
            umbrales.setExpanded(true);
            programa.umbrales.forEach((nom, val) ->
                umbrales.getChildren().add(new TreeItem<>(nom + " = " + val)));
            raiz.getChildren().add(umbrales);
        }

        // Reglas
        if (programa.reglas.isEmpty()) {
            raiz.getChildren().add(new TreeItem<>("Reglas: (ninguna)"));
        } else {
            TreeItem<String> reglas = new TreeItem<>("Reglas (" + programa.reglas.size() + ")");
            reglas.setExpanded(true);
            for (int i = 0; i < programa.reglas.size(); i++) {
                Regla r = programa.reglas.get(i);
                String condStr = condicionToString(r.condicion);
                TreeItem<String> regla = new TreeItem<>(
                    "Regla " + (i + 1) + ": Si " + condStr);
                regla.getChildren().add(new TreeItem<>("\u2192 Estado: " + r.estado));
                if (r.tieneRamaSino()) {
                    regla.getChildren().add(new TreeItem<>("\u2192 Sino: " + r.estadoSino));
                }
                reglas.getChildren().add(regla);
            }
            raiz.getChildren().add(reglas);
        }

        // Cálculos
        if (programa.calculos.isEmpty()) {
            raiz.getChildren().add(new TreeItem<>("Calculos: (ninguno)"));
        } else {
            TreeItem<String> calculos = new TreeItem<>("Calculos (" + programa.calculos.size() + ")");
            calculos.setExpanded(true);
            for (Calculo c : programa.calculos) {
                TreeItem<String> calcItem = new TreeItem<>(
                    c.esExpresion()
                        ? "CALCULAR (" + c.expresion + ") -> " + c.destino
                        : "CALCULAR " + c.operacion + " -> " + c.destino);
                for (Parametro p : c.parametros) {
                    calcItem.getChildren().add(new TreeItem<>("  " + p.nombre + " = " + p.valor));
                }
                calculos.getChildren().add(calcItem);
            }
            raiz.getChildren().add(calculos);
        }

        astTree.setRoot(raiz);
    }

    // ─── Panel de derivación ───
    private void actualizarDerivacion(Programa programa, Parser parser) {
        if (derivacionPanel == null) return;
        derivacionPanel.getChildren().clear();

        boolean disponible = programa != null && parser != null
            && parser.arbolDerivacion != null;
        if (itemArbol != null) itemArbol.setDisable(!disponible);
        if (botonArbol != null) botonArbol.setDisable(!disponible);

        Label titulo = new Label("Árbol de derivación");
        titulo.getStyleClass().add("tm-panel-heading");

        Button abrir = new Button("Abrir vista completa");
        abrir.getStyleClass().addAll("tool-button", "tool-primary");
        abrir.setFocusTraversable(false);
        abrir.setOnAction(e -> abrirArbolDerivacion());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(10, titulo, spacer, abrir);
        header.setAlignment(Pos.CENTER_LEFT);
        derivacionPanel.getChildren().add(header);

        if (!disponible) {
            abrir.setDisable(true);
            Label info = new Label("Corrige los errores del código para generar el árbol.");
            info.getStyleClass().add("tm-empty");
            info.setWrapText(true);
            derivacionPanel.getChildren().add(info);
            return;
        }

        abrir.setDisable(false);

        TextArea arbolPreviewArea = new TextArea(arbolPreviewCompleto(parser.arbolDerivacion));
        arbolPreviewArea.setEditable(false);
        arbolPreviewArea.setWrapText(false);
        arbolPreviewArea.getStyleClass().add("tm-derivacion-preview");
        VBox.setVgrow(arbolPreviewArea, Priority.ALWAYS);
        derivacionPanel.getChildren().add(arbolPreviewArea);
    }

    /**
     * Convierte el árbol de derivación completo a texto indentado (sin límite de profundidad).
     * Usa caracteres ASCII para compatibilidad con todas las fuentes.
     */
    private String arbolPreviewCompleto(NodoDerivacion nodo) {
        return arbolPreviewCompleto(nodo, "", true);
    }

    private String arbolPreviewCompleto(NodoDerivacion nodo, String prefijo, boolean esUltimo) {
        StringBuilder sb = new StringBuilder();
        sb.append(prefijo);
        sb.append(esUltimo ? "+-- " : "+-- ");
        sb.append(nodo.valor).append("\n");

        String nuevoPrefijo = prefijo + (esUltimo ? "    " : "|   ");

        for (int i = 0; i < nodo.hijos.size(); i++) {
            sb.append(arbolPreviewCompleto(nodo.hijos.get(i), nuevoPrefijo,
                i == nodo.hijos.size() - 1));
        }

        return sb.toString();
    }

    // ─── Abrir ventana gráfica del árbol de derivación ───
    private void abrirArbolDerivacion() {
        if (ultimoParser == null || ultimoParser.arbolDerivacion == null) {
            mostrarError("No hay árbol de derivación disponible. Corrige los errores del código para generarlo.");
            return;
        }
        PanelDerivacion panel = new PanelDerivacion(ultimoParser.arbolDerivacion);
        panel.mostrarEnVentana();
    }

    // ─── Convertir expresión a string para el AST ───
    private String expresionToString(Expresion e) {
        if (e instanceof Numero n) return String.valueOf(n.valor);
        if (e instanceof Variable v) return v.nombre;
        if (e instanceof EstadoActual) return "estado";
        if (e instanceof LiteralEstado l) return l.nombre;
        if (e instanceof Binaria b) {
            return expresionToString(b.izquierda) + " "
                + b.operador + " " + expresionToString(b.derecha);
        }
        if (e instanceof Negacion n) return "-(" + expresionToString(n.expresion) + ")";
        if (e instanceof Abs a) return "abs(" + expresionToString(a.expresion) + ")";
        return "?";
    }

    /**
     * Convierte una condición a texto para el árbol del AST.
     *
     * <p>Delega en el {@code toString()} de cada nodo, que ya sabe imposing
     * los paréntesis de los conectores: el árbol del editor tiene que mostrar
     * el agrupamiento real, o un `a y b o c` se lee como `a y (b o c)`.
     */
    private String condicionToString(Condicion c) {
        if (c instanceof Comparacion comp) {
            return expresionToString(comp.izquierda) + " "
                + comp.operador + " " + expresionToString(comp.derecha);
        }
        return c == null ? "?" : c.toString();
    }

    // ─── Barra de estado ───
    private void actualizarStatus(int numTokens, int numErroresLex, int numErroresSint, int numErroresNoDecl) {
        int totalErrores = numErroresLex + numErroresSint + numErroresNoDecl;
        ultimoConErrores = totalErrores > 0;

        lblTokens.setText(numTokens + (numTokens == 1 ? " token" : " tokens"));
        lblErrores.setText(totalErrores == 0
            ? "Sin errores"
            : totalErrores + (totalErrores == 1 ? " error" : " errores"));
        lblEstado.setText("ANALIZADO");

        marcarEstado(lblErrores, totalErrores > 0);
        marcarEstado(lblEstado, totalErrores > 0);
        programarFinAvisoAnalisis();
    }

    private static void marcarEstado(Label etiqueta, boolean conError) {
        etiqueta.getStyleClass().remove("tm-status-error");
        if (conError) etiqueta.getStyleClass().add("tm-status-error");
    }

    private void actualizarStatusCursor() {
        if (lblPosicion == null || editor == null) return;
        String texto = editor.getText();
        int pos = Math.min(Math.max(editor.getCaretPosition(), 0), texto.length());

        int linea = 1, columna = 1;
        for (int i = 0; i < pos; i++) {
            if (texto.charAt(i) == '\n') {
                linea++;
                columna = 1;
            } else {
                columna++;
            }
        }
        lblPosicion.setText("Ln " + linea + ", Col " + columna);
    }

    // =============================================================
    //  TABLA DE SÍMBOLOS Y VERIFICACIÓN DE IDs
    // =============================================================

    /**
     * Vuelca la tabla de símbolos que construyó el parser.
     *
     * Antes esta tabla se armaba aquí, re-escaneando la lista de tokens para
     * recuperar la línea de cada declaración —una segunda fuente de verdad que
     * podía desincronizarse de la del analizador semántico. Ahora se lee la
     * misma estructura que usaron las verificaciones.
     */
    private void construirTablaSimbolos(Programa programa, List<Token> tokens) {
        simbolosData.clear();
        if (programa == null) return;

        for (Simbolo s : programa.simbolos.todos()) {
            simbolosData.add(new InfoSimbolo(
                s.nombre(),
                s.clase().name(),
                s.nombreTipo(),
                s.unidad(),
                s.valorTexto(),
                s.linea()));
        }
    }

    /** Busca la primera línea donde aparece una referencia a un identificador */
    private int buscarLineaPrimerUso(String nombre, List<Token> tokens) {
        for (Token t : tokens) {
            if (t.tipo == TipoToken.ID && t.lexema.equals(nombre)) {
                return t.linea;
            }
        }
        return 0;
    }

    /**
     * Extrae todas las variables referenciadas en condiciones y expresiones
     * del AST (Reglas, Cálculos).
     */
    private Set<String> extraerVariablesReferenciadas(Programa programa) {
        Set<String> vars = new HashSet<>();
        for (Regla r : programa.reglas) {
            vars.addAll(r.condicion.variables());
        }
        for (Calculo c : programa.calculos) {
            if (c.esExpresion()) {
                extraerVariablesDeExpresion(c.expresion, vars);
            }
            for (Parametro p : c.parametros) {
                if ("con".equals(p.nombre)) {
                    vars.add(p.valor); // con = identificador
                }
            }
        }
        return vars;
    }

    /** Recorre recursivamente una expresión para extraer Variables */
    private void extraerVariablesDeExpresion(Expresion e, Set<String> vars) {
        if (e instanceof Variable v) {
            vars.add(v.nombre);
        } else if (e instanceof Binaria b) {
            extraerVariablesDeExpresion(b.izquierda, vars);
            extraerVariablesDeExpresion(b.derecha, vars);
        } else if (e instanceof Negacion n) {
            extraerVariablesDeExpresion(n.expresion, vars);
        } else if (e instanceof Abs a) {
            extraerVariablesDeExpresion(a.expresion, vars);
        }
        // Numero no tiene variables
    }

    /**
     * Análisis SEMÁNTICO DESACTIVADO (por pedido, se redefinirá después).
     * Se conserva el método para no romper la estructura, pero siempre
     * retorna lista vacía: los identificadores no declarados NO se reportan
     * ni se reasignan a léxico/sintáctico.
     */
    private List<String> detectarNoDeclarados(Programa programa, List<Token> tokens) {
        return new ArrayList<>();
    }

    /**
     * Valida que cada calcular() use los parámetros con sentido según su operación.
     * DESACTIVADO por pedido (no se llama de momento; se reactivará después).
     * Es error SEMÁNTICO (la forma ya pasó el parser): un error por cada calcular.
     * SENO/COSENO/CUADRADA: exactamente AMPLITUD y FRECUENCIA (1 vez c/u).
     * PROMEDIO: exactamente VENTANA (1 vez). MAXIMO: vacío. SUMA: 2+ CON.
     */
    private List<String> validarParametrosCalculo(Programa programa) {
        List<String> errores = new ArrayList<>();
        if (programa == null) return errores;

        for (Calculo c : programa.calculos) {
            String op = c.operacion == null ? "" : c.operacion.toLowerCase();
            Map<String, Integer> conteo = new HashMap<>();
            for (Parametro p : c.parametros) {
                String n = p.nombre == null ? "" : p.nombre.toLowerCase();
                conteo.put(n, conteo.getOrDefault(n, 0) + 1);
            }
            int linea = c.linea;

            if (op.equals("seno") || op.equals("coseno") || op.equals("cuadrada")) {
                String opMay = op.toUpperCase();
                boolean ok = c.parametros.size() == 2
                    && conteo.getOrDefault("amplitud", 0) == 1
                    && conteo.getOrDefault("frecuencia", 0) == 1;
                if (!ok) {
                    errores.add("Error semantico en linea " + linea + ": " + opMay
                        + " requiere AMPLITUD y FRECUENCIA (2 parametros)."
                        + " Se encontró: " + describirParams(c) + ".");
                }
            } else if (op.equals("promedio")) {
                boolean ok = c.parametros.size() == 1
                    && conteo.getOrDefault("ventana", 0) == 1;
                if (!ok) {
                    errores.add("Error semantico en linea " + linea + ": PROMEDIO"
                        + " requiere VENTANA (1 parametro)."
                        + " Se encontró: " + describirParams(c) + ".");
                }
            } else if (op.equals("maximo")) {
                if (!c.parametros.isEmpty()) {
                    errores.add("Error semantico en linea " + linea + ": MAXIMO"
                        + " no lleva parametros ()."
                        + " Se encontró: " + describirParams(c) + ".");
                }
            } else if (op.equals("suma")) {
                boolean soloCon = conteo.keySet().stream().allMatch(k -> k.equals("con"));
                int nCon = conteo.getOrDefault("con", 0);
                if (!soloCon || nCon < 2) {
                    errores.add("Error semantico en linea " + linea + ": SUMA"
                        + " requiere 2 o más CON (ej. SUMA(CON=a, CON=b))."
                        + " Se encontró: " + describirParams(c) + ".");
                }
            }
        }
        return errores;
    }

    /** Describe los parámetros para el mensaje semántico (vacío → "vacío"). */
    private String describirParams(Calculo c) {
        if (c.parametros.isEmpty()) return "vacío";
        List<String> partes = new ArrayList<>();
        for (Parametro p : c.parametros) {
            partes.add(p.nombre.toUpperCase() + "=" + p.valor);
        }
        return String.join(", ", partes);
    }

    /**
     * Detecta palabras reservadas incluidas dentro de un ID largo, en orden
     * normal o al revés, aunque tengan caracteres en medio.
     * Un error léxico por cada ocurrencia.
     * Ej. 'seno1356515asdasdasad' contiene 'seno' → sugiere 'seno'.
     * Ej. 'asdasdones6465465' contiene 'ones' ('seno' al revés) → sugiere 'seno'.
     * Ej. 'estado123' / 'miestadox' contienen 'estado' → sugieren 'estado'.
     * Ej. 'CAL165151651CULAR' son solo letras 'calcular' con dígitos en medio
     * → sugiere 'calcular'.
     * Solo se consideran reservadas de 4+ letras para no marcar 'si', 'con',
     * 'fin', 'abs' dentro de cualquier palabra. Los lexemas ya reportados
     * como typo (Levenshtein) se omiten para no duplicar.
     * Retorna mapa lexema-minúsculas → lista de errores por ocurrencia.
     *
     * <p>Solo se examinan los identificadores que están en una posición donde
     * la gramática esperaba una palabra reservada. En el resto —el nombre de un
     * sensor, el destino de un `calcular`, una referencia dentro de una
     * condición— un identificador compuesto es normal y no se toca. Sin esa
     * guarda, una señal legítima como `voltaje_prom` se reportaba como error
     * léxico por contener el fragmento `prom` de `promedio`.
     *
     * @param declarados nombres declarados en el programa, en minúsculas
     */
    /**
     * Nombres que el programa declara de forma explícita: sensores, umbrales y
     * señales derivadas de `calcular(operacion, destino)`.
     *
     * <p>Un identificador declarado es legítimo por construcción —el usuario
     * le puso ese nombre—, así que el detector de typos no debe suspectar de
     * él.
     */
    private static Set<String> nombresDeclarados(Programa programa) {
        Set<String> nombres = new HashSet<>();
        if (programa == null) return nombres;
        nombres.addAll(programa.sensores);
        nombres.addAll(programa.umbrales.keySet());
        for (Simbolo s : programa.simbolos.todos()) nombres.add(s.nombre());
        for (Calculo c : programa.calculos) {
            if (c.destino != null) nombres.add(c.destino);
        }
        return nombres;
    }

    private Map<String, List<String>> detectarReservadaIncluida(
            List<Token> tokens, Set<String> lexemasYaReportados,
            Set<String> declarados) {
        Map<String, List<String>> errores = new LinkedHashMap<>();
        for (Token token : tokens) {
            if (token.tipo != TipoToken.ID) continue;
            String lower = token.lexema.toLowerCase();
            if (lexemasYaReportados != null && lexemasYaReportados.contains(lower)) continue;
            if (declarados != null && declarados.contains(lower)) continue;

            // El lenguaje compone nombres con '_' (sensor_atributo). Una
            // palabra reservada en el SUFIJO es un segmento legítimo —
            // voltaje_prom, voltaje_pico, temp_prom — así que solo se examina
            // el segmento base, donde sí puede ser una palabra mal escrita
            // (estdao_sensor, CAL165CULAR).
            int corte = lower.indexOf('_');
            String base = corte >= 0 ? lower.substring(0, corte) : lower;

            String mejorReservada = null;
            String fragmentoHallado = null;
            boolean esReves = false;
            boolean conRelleno = false;
            boolean esIncompleta = false;

            // Solo-letras: quita dígitos y '_' para ver si la reservada está
            // partida por caracteres en medio (ej. CAL165CULAR → CALCULAR).
            String soloLetras = base.replaceAll("[^a-z]", "");

            for (String reservada : PALABRAS_RESERVADAS) {
                String r = reservada.toLowerCase();
                if (r.length() < 4) continue;
                if (base.contains(r)) {
                    if (mejorReservada == null || r.length() > mejorReservada.length()) {
                        mejorReservada = reservada;
                        fragmentoHallado = r;
                        esReves = false;
                        conRelleno = false;
                        esIncompleta = false;
                    }
                }
                String rev = new StringBuilder(r).reverse().toString();
                if (base.contains(rev)) {
                    if (mejorReservada == null || r.length() > mejorReservada.length()) {
                        mejorReservada = reservada;
                        fragmentoHallado = rev;
                        esReves = true;
                        conRelleno = false;
                        esIncompleta = false;
                    }
                }
                // Con caracteres en medio: la reservada aparece en soloLetras
                // pero NO contigua en el texto original.
                if (!soloLetras.equals(base)) {
                    if (soloLetras.contains(r)) {
                        if (mejorReservada == null || r.length() > mejorReservada.length()) {
                            mejorReservada = reservada;
                            fragmentoHallado = r;
                            esReves = false;
                            conRelleno = true;
                        }
                    }
                    if (soloLetras.contains(rev)) {
                        if (mejorReservada == null || r.length() > mejorReservada.length()) {
                            mejorReservada = reservada;
                            fragmentoHallado = rev;
                            esReves = true;
                            conRelleno = true;
                        }
                    }
                }

                // Fragmento incompleto dentro de una combinación de caracteres:
                // SADASDFRECUEN646... contiene el inicio de FRECUENCIA.
                // Se exige un fragmento de al menos 4 letras para no marcar
                // coincidencias accidentales demasiado cortas.
                if (mejorReservada == null || r.length() >= mejorReservada.length()) {
                    String prefijo = mayorPrefijoReservadaContenido(soloLetras, r);
                    String prefijoReves = mayorPrefijoReservadaContenido(soloLetras, rev);
                    if (prefijo != null && prefijo.length() < r.length()
                            && (fragmentoHallado == null || prefijo.length() > fragmentoHallado.length())) {
                        mejorReservada = reservada;
                        fragmentoHallado = prefijo;
                        esReves = false;
                        conRelleno = true;
                        esIncompleta = true;
                    }
                    if (prefijoReves != null && prefijoReves.length() < r.length()
                            && (fragmentoHallado == null || prefijoReves.length() > fragmentoHallado.length())) {
                        mejorReservada = reservada;
                        fragmentoHallado = prefijoReves;
                        esReves = true;
                        conRelleno = true;
                        esIncompleta = true;
                    }
                }
            }

            if (mejorReservada != null) {
                String mensaje;
                if (esIncompleta && esReves) {
                    mensaje = "Error lexico en linea " + token.linea + ": La palabra '"
                        + token.lexema + "' contiene el fragmento '" + fragmentoHallado
                        + "' de la palabra reservada '" + mejorReservada + "' al revés."
                        + " \u00BFQuisiste decir '" + mejorReservada + "'?";
                } else if (esIncompleta) {
                    mensaje = "Error lexico en linea " + token.linea + ": La palabra '"
                        + token.lexema + "' contiene el fragmento '" + fragmentoHallado
                        + "' de la palabra reservada '" + mejorReservada + "'."
                        + " \u00BFQuisiste decir '" + mejorReservada + "'?";
                } else if (conRelleno && esReves) {
                    mensaje = "Error lexico en linea " + token.linea + ": La palabra '"
                        + token.lexema + "' contiene '" + fragmentoHallado
                        + "' (la palabra '" + mejorReservada + "' al rev\u00E9s)"
                        + " con caracteres en medio."
                        + " \u00BFQuisiste decir '" + mejorReservada + "'?";
                } else if (conRelleno) {
                    mensaje = "Error lexico en linea " + token.linea + ": La palabra '"
                        + token.lexema + "' contiene la palabra reservada '"
                        + fragmentoHallado + "' con caracteres en medio."
                        + " \u00BFQuisiste decir '" + mejorReservada + "'?";
                } else if (esReves) {
                    mensaje = "Error lexico en linea " + token.linea + ": La palabra '"
                        + token.lexema + "' contiene '" + fragmentoHallado
                        + "' (la palabra '" + mejorReservada + "' al rev\u00E9s)."
                        + " \u00BFQuisiste decir '" + mejorReservada + "'?";
                } else {
                    mensaje = "Error lexico en linea " + token.linea + ": La palabra '"
                        + token.lexema + "' contiene la palabra reservada '"
                        + fragmentoHallado + "'."
                        + " \u00BFQuisiste decir '" + mejorReservada + "'?";
                }
                errores.computeIfAbsent(lower, k -> new ArrayList<>()).add(mensaje);
            }
        }
        return errores;
    }

    /** Retorna el prefijo más largo (mínimo 4 letras) contenido en un ID. */
    private String mayorPrefijoReservadaContenido(String texto, String reservada) {
        if (texto == null || reservada == null) return null;
        String lowerTexto = texto.toLowerCase();
        String lowerReservada = reservada.toLowerCase();
        int maximo = lowerReservada.length() - 1;
        for (int longitud = maximo; longitud >= 4; longitud--) {
            String prefijo = lowerReservada.substring(0, longitud);
            if (lowerTexto.contains(prefijo)) return prefijo;
        }
        return null;
    }

    /** Detecta typos de palabras reservadas antes de entregar el flujo al parser. */
    private Map<String, String> detectarReservadasMalEscritas(List<Token> tokens) {
        Map<String, String> errores = new LinkedHashMap<>();

        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            if (token.tipo != TipoToken.ID) continue;

            String[] candidatos = candidatosReservadosEnContexto(tokens, i);
            if (candidatos.length == 0) continue;

            String sugerencia = buscarSugerenciaReservada(token.lexema.toLowerCase(), candidatos);
            if (sugerencia == null) {
                // Palabra reservada sin terminar (ej. 'FRECU' de 'FRECUENCIA').
                sugerencia = buscarReservadaIncompleta(token.lexema, candidatos);
            }
            if (sugerencia == null) {
                // También reconoce una reservada dentro de una combinación,
                // incluidas las cortas: siSOLO -> si, sensor123 -> sensor.
                sugerencia = buscarReservadaEnCombinacion(token.lexema, candidatos);
            }
            if (sugerencia != null && !sugerencia.equalsIgnoreCase(token.lexema)) {
                errores.putIfAbsent(token.lexema.toLowerCase(),
                    "Error lexico en linea " + token.linea + ": La palabra '"
                    + token.lexema + "' parece una palabra reservada mal escrita."
                    + " \u00BFQuisiste decir '" + sugerencia + "'?");
            }
        }
        return errores;
    }

    /**
     * Detecta una reservada incompleta: el lexema es prefijo de la candidata
     * (ej. 'FRECU' → 'FRECUENCIA', 'AMPLI' → 'AMPLITUD'). Mínimo 3 letras para
     * no confundir variables cortas. Si varias candidatas encajan, sugiere la
     * más cercana (menos letras faltantes).
     */
    private String buscarReservadaIncompleta(String lexema, String[] candidatos) {
        if (lexema == null || lexema.length() < 3) return null;
        String lower = lexema.toLowerCase();
        String mejor = null;
        int menosFaltantes = Integer.MAX_VALUE;
        for (String c : candidatos) {
            if (c.toLowerCase().startsWith(lower)) {
                int faltantes = c.length() - lexema.length();
                if (faltantes > 0 && faltantes < menosFaltantes) {
                    menosFaltantes = faltantes;
                    mejor = c;
                }
            }
        }
        return mejor;
    }

    /** Recupera un typo reservado cuando el contexto esperado solo apareció en el error del parser. */
    private Map<String, String> detectarReservadasDesdeErrores(List<Token> tokens,
                                                               List<String> erroresSintacticos) {
        Map<String, String> errores = new LinkedHashMap<>();
        for (Token token : tokens) {
            if (token.tipo != TipoToken.ID) continue;

            for (String error : erroresSintacticos) {
                if (!error.contains("'" + token.lexema + "'")) continue;
                Set<String> candidatos = candidatosEsperados(error);
                String sugerencia = buscarSugerenciaReservada(token.lexema.toLowerCase(),
                    candidatos.toArray(new String[0]));
                if (sugerencia == null) {
                    sugerencia = buscarReservadaIncompleta(token.lexema,
                        candidatos.toArray(new String[0]));
                }
                if (sugerencia != null && !sugerencia.equalsIgnoreCase(token.lexema)) {
                    errores.putIfAbsent(token.lexema.toLowerCase(),
                        "Error lexico en linea " + token.linea + ": La palabra '"
                        + token.lexema + "' parece una palabra reservada mal escrita."
                        + " \u00BFQuisiste decir '" + sugerencia + "'?");
                }
            }
        }
        return errores;
    }

    /**
     * Filtra solo el sintáctico REDUNDANTE: el que existe únicamente porque una
     * palabra está mal escrita. Dos formas:
     * 1) El parser esperaba UNA sola palabra reservada y encontró su typo
     *    (ej. esperaba ESTADO y vino 'estdao'): al corregir la palabra todo
     *    parsea, así que se queda solo el léxico.
     * 2) "Token inesperado" por un typo al inicio de sentencia (ej. 'sensro'
     *    donde va SENSOR): si la sugerencia es válida justo en esa posición,
     *    corregirla resuelve todo y también queda solo el léxico.
     * En cambio NO se filtra cuando lo esperado son varias opciones (ej. en
     * parámetros se esperaba AMPLITUD, FRECUENCIA... y vino 'AMPLTU'): ahí hay
     * dos problemas reales —palabra mal escrita y estructura incompleta— y salen
     * ambos errores. Tampoco se tocan los sintácticos de lexemas sin typo léxico.
     */
    private List<String> filtrarSintacticosDuplicados(List<String> erroresSint,
                                                      Set<String> lexemasLexicos,
                                                      List<Token> tokens) {
        if (erroresSint.isEmpty() || lexemasLexicos.isEmpty()) {
            return new ArrayList<>(erroresSint);
        }
        List<String> filtrados = new ArrayList<>();
        for (String error : erroresSint) {
            if (!esSintacticoRedundante(error, lexemasLexicos, tokens)) {
                filtrados.add(error);
            }
        }
        return filtrados;
    }

    /** Dice si el sintáctico sobra porque es puro efecto del typo léxico. */
    private boolean esSintacticoRedundante(String error, Set<String> lexemasLexicos,
                                           List<Token> tokens) {
        String lower = error.toLowerCase();
        if (!lower.contains("pero se encontr")) return false;
        int ultComilla = error.lastIndexOf('\'');
        int penComilla = ultComilla > 0 ? error.lastIndexOf('\'', ultComilla - 1) : -1;
        if (penComilla < 0 || ultComilla <= penComilla) return false;
        String lexema = error.substring(penComilla + 1, ultComilla);
        if (lexema.isEmpty() || !lexemasLexicos.contains(lexema.toLowerCase())) return false;
        int lineaError = extraerLinea(error);

        Set<String> esperados = candidatosEsperados(error);
        if (esperados.size() == 1) {
            // Caso 1: una sola palabra esperada y el lexema es su typo.
            String esperado = esperados.iterator().next();
            if (lexema.equalsIgnoreCase(esperado)) return false;
        String l = lexema.toLowerCase();
        String s = esperado.toLowerCase();
        if (levenshtein(l, s) <= 2) return true;
        if (tienenLasMismasLetras(l, s)) return true;
        String revS = new StringBuilder(s).reverse().toString();
        String soloLetras = l.replaceAll("[^a-z]", "");
        // Incluida al derecho o al revés, y reservada sin terminar (prefijo).
        return l.contains(s) || soloLetras.contains(s) || s.contains(l)
            || l.contains(revS) || soloLetras.contains(revS);
        }
        if (!esperados.isEmpty()) {
            // Si la palabra reservada está mal escrita pero el resto de la
            // producción ya está completo, el sintáctico es redundante.
            // Ejemplos: OCIP; -> PICO; y FRECUENC=0.1 -> FRECUENCIA=0.1.
            // Si además falta la estructura (AMPLTU sin '=valor'), se dejan
            // los dos errores porque son problemas diferentes.
            return esEstructuraCompletaConTypo(lexema, lineaError, tokens, esperados);
        }

        // Caso 2: "Token inesperado" sin lista de esperados. Solo sobra si la
        // sugerencia del typo es válida justo en la posición del token (al
        // corregirla, la sentencia completa parsea bien).
        int linea = extraerLinea(error);
        int indice = -1;
        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (t.lexema.equals(lexema) && t.linea == linea) { indice = i; break; }
        }
        if (indice < 0) {
            for (int i = 0; i < tokens.size(); i++) {
                if (tokens.get(i).lexema.equals(lexema)) { indice = i; break; }
            }
        }
        if (indice < 0) return false;
        String[] candidatos = candidatosReservadosEnContexto(tokens, indice);
        if (candidatos.length == 0) return false;
        String sugerencia = buscarSugerenciaReservada(lexema.toLowerCase(), candidatos);
        if (sugerencia == null) {
            sugerencia = buscarReservadaEnCombinacion(lexema, candidatos);
        }
        return sugerencia != null && !sugerencia.equalsIgnoreCase(lexema);
    }

    /**
     * Comprueba si un parámetro mal escrito conserva la estructura
     * PARAM -> NOMBRE ASIGNACION VALOR. Solo en ese caso el error sintáctico
     * procede exclusivamente del nombre reservado incompleto.
     */
    private boolean esParametroCompletoConTypo(String lexema, int linea,
                                               List<Token> tokens,
                                               Set<String> esperados) {
        int indice = -1;
        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            if (token.linea == linea && token.lexema.equals(lexema)) {
                indice = i;
                break;
            }
        }
        if (indice < 0 || indice + 2 >= tokens.size()) return false;

        Token asignacion = tokens.get(indice + 1);
        Token valor = tokens.get(indice + 2);
        if (asignacion.tipo != TipoToken.ASIGNACION) return false;

        String sugerencia = buscarSugerenciaReservada(lexema.toLowerCase(),
            esperados.toArray(new String[0]));
        if (sugerencia == null) {
            sugerencia = buscarReservadaIncompleta(lexema,
                esperados.toArray(new String[0]));
        }
        if (sugerencia == null) {
            sugerencia = buscarReservadaEnCombinacion(lexema,
                esperados.toArray(new String[0]));
        }
        if (sugerencia == null) return false;

        // AMPLITUD/FRECUENCIA/VENTANA reciben números; CON recibe un ID.
        if (sugerencia.equalsIgnoreCase("con")) {
            return valor.tipo == TipoToken.ID;
        }
        return valor.tipo == TipoToken.NUMERO;
    }

    /**
     * Determina si el resto de la producción ya está completo después del
     * lexema reservado mal escrito. Incluye parámetros, valores de estado y
     * nombres de operación.
     */
    private boolean esEstructuraCompletaConTypo(String lexema, int linea,
                                                List<Token> tokens,
                                                Set<String> esperados) {
        if (esParametroCompletoConTypo(lexema, linea, tokens, esperados)) {
            return true;
        }

        int indice = -1;
        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            if (token.linea == linea && token.lexema.equals(lexema)) {
                indice = i;
                break;
            }
        }
        if (indice < 0) return false;

        String[] candidatos = esperados.toArray(new String[0]);
        String sugerencia = buscarSugerenciaReservada(lexema.toLowerCase(), candidatos);
        if (sugerencia == null) {
            sugerencia = buscarReservadaIncompleta(lexema, candidatos);
        }
        if (sugerencia == null) {
            sugerencia = buscarReservadaEnCombinacion(lexema, candidatos);
        }
        if (sugerencia == null) return false;

        Token anterior = indice > 0 ? tokens.get(indice - 1) : null;
        Token siguiente = indice + 1 < tokens.size() ? tokens.get(indice + 1) : null;

        // estado = PICO; / estado = CAIDA; / etc.
        boolean esValorEstado = esperados.contains("normal")
            || esperados.contains("pico")
            || esperados.contains("caida")
            || esperados.contains("inestable");
        if (esValorEstado && anterior != null
                && anterior.tipo == TipoToken.ASIGNACION
                && siguiente != null && siguiente.tipo == TipoToken.PUNTO_COMA) {
            return true;
        }

        // calcular(sensor, SENO(...)); / COSENO(...); etc.
        boolean esOperacion = esperados.contains("seno")
            || esperados.contains("coseno")
            || esperados.contains("cuadrada")
            || esperados.contains("promedio")
            || esperados.contains("maximo")
            || esperados.contains("suma");
        return esOperacion && siguiente != null
            && siguiente.tipo == TipoToken.PAREN_IZQ;
    }

    /** Busca una reservada completa, invertida o incompleta dentro de un ID. */
    private String buscarReservadaEnCombinacion(String lexema, String[] candidatos) {
        String lower = lexema.toLowerCase();
        String soloLetras = lower.replaceAll("[^a-z]", "");
        String mejor = null;
        int mejorLongitud = 0;
        for (String candidato : candidatos) {
            String r = candidato.toLowerCase();
            String rev = new StringBuilder(r).reverse().toString();
            String fragmento = r.length() >= 4
                ? mayorPrefijoReservadaContenido(soloLetras, r) : null;
            String fragmentoReves = r.length() >= 4
                ? mayorPrefijoReservadaContenido(soloLetras, rev) : null;
            boolean coincide = lower.contains(r) || lower.contains(rev)
                || soloLetras.contains(r) || soloLetras.contains(rev)
                || fragmento != null || fragmentoReves != null;
            if (coincide && r.length() > mejorLongitud) {
                mejor = candidato;
                mejorLongitud = r.length();
            }
        }
        return mejor;
    }

    /** Extrae el número después de "en linea N" (-1 si no hay). */
    private int extraerLinea(String error) {
        String lower = error.toLowerCase();
        int idx = lower.indexOf("en linea ");
        if (idx < 0) return -1;
        int ini = idx + "en linea ".length();
        int fin = ini;
        while (fin < error.length() && Character.isDigit(error.charAt(fin))) fin++;
        if (fin == ini) return -1;
        try {
            return Integer.parseInt(error.substring(ini, fin));
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    /** Obtiene candidatos solo para el lugar sintáctico que ocupa el ID. */
    private String[] candidatosReservadosEnContexto(List<Token> tokens, int indice) {
        Token anterior = indice > 0 ? tokens.get(indice - 1) : null;
        Token siguiente = indice + 1 < tokens.size() ? tokens.get(indice + 1) : null;

        if (anterior == null || anterior.tipo == TipoToken.PUNTO_COMA) {
            return new String[] { "sensor", "umbral", "si", "calcular", "estado",
                                 "sino", "muestreo" };
        }
        // Tras `sensor ID =` o `umbral ID =` se espera un tipo de sensor.
        if (anterior.tipo == TipoToken.ASIGNACION && indice > 1) {
            TipoToken previo = tokens.get(indice - 2).tipo;
            if (previo == TipoToken.ID) {
                boolean trasNombre = (indice > 2
                        && (tokens.get(indice - 3).tipo == TipoToken.SENSOR
                            || tokens.get(indice - 3).tipo == TipoToken.UMBRAL));
                if (trasNombre) return tiposReservados();
            }
        }
        if (anterior.tipo == TipoToken.ENTONCES) {
            return new String[] { "estado" };
        }
        if (anterior.tipo == TipoToken.ASIGNACION
                && indice > 1 && tokens.get(indice - 2).tipo == TipoToken.ESTADO) {
            return new String[] { "normal", "pico", "caida", "inestable" };
        }
        if (siguiente != null && siguiente.tipo == TipoToken.ESTADO
                && (anterior.tipo == TipoToken.ID || anterior.tipo == TipoToken.NUMERO
                    || anterior.tipo == TipoToken.PAREN_DER)) {
            return new String[] { "entonces" };
        }

        if (anterior != null && anterior.tipo == TipoToken.COMA) {
            int antesDeComa = indice - 2;
            if (antesDeComa >= 1
                    && tokens.get(antesDeComa).tipo == TipoToken.ID
                    && tokens.get(antesDeComa - 1).tipo == TipoToken.PAREN_IZQ) {
                return new String[] { "seno", "coseno", "cuadrada", "promedio", "maximo", "suma" };
            }
            if (estaEnParametros(tokens, indice)) return parametrosReservados();
        }

        if (anterior != null && anterior.tipo == TipoToken.PAREN_IZQ
                && indice > 1 && esOperacion(tokens.get(indice - 2).tipo)) {
            return parametrosReservados();
        }

        if (siguiente != null && siguiente.tipo == TipoToken.ASIGNACION
                && estaEnParametros(tokens, indice)) {
            return parametrosReservados();
        }
        return new String[0];
    }

    private boolean estaEnParametros(List<Token> tokens, int indice) {
        for (int i = indice - 1; i >= 0; i--) {
            TipoToken tipo = tokens.get(i).tipo;
            if (tipo == TipoToken.PUNTO_COMA) return false;
            if (tipo == TipoToken.PAREN_IZQ) {
                return i > 0 && esOperacion(tokens.get(i - 1).tipo);
            }
        }
        return false;
    }

    private boolean esOperacion(TipoToken tipo) {
        return tipo == TipoToken.SENO || tipo == TipoToken.COSENO
            || tipo == TipoToken.CUADRADA || tipo == TipoToken.PROMEDIO
            || tipo == TipoToken.MAXIMO || tipo == TipoToken.SUMA;
    }

    private String[] parametrosReservados() {
        return new String[] { "amplitud", "frecuencia", "ventana", "con" };
    }

    /** Los seis tipos de sensor declarables. */
    private String[] tiposReservados() {
        return new String[] { "electrico", "termico", "luminico",
                             "mecanico", "quimico", "hidraulico" };
    }

    /**
     * Escanea TODOS los tokens ID del código y los compara con las
     * palabras reservadas del lenguaje usando distancia de Levenshtein.
     * Solo sugiere un ID cuando el parser también lo mencionó en un error.
     * Así no se confunden identificadores válidos con palabras reservadas.
     */
    private List<String> sugerirIDsMalEscritos(List<Token> tokens, Programa programa,
                                               List<String> erroresSintacticos) {
        List<String> sugerencias = new ArrayList<>();
        Set<String> yaSugeridos = new HashSet<>(); // evita duplicados

        // Identificadores declarados (sensores, umbrales) — no sugerir sobre ellos
        Set<String> declaradas = new HashSet<>();
        if (programa != null) {
            declaradas.addAll(programa.sensores);
            for (String k : programa.umbrales.keySet()) {
                declaradas.add(k);
            }
        }

        // Recorrer todos los tokens buscando IDs
        for (Token t : tokens) {
            if (t.tipo != TipoToken.ID) continue;
            String lexema = t.lexema;
            String idLower = lexema.toLowerCase();

            // Si ya está declarado o ya sugerimos para este ID, ignorar
            if (declaradas.contains(lexema)) continue;
            if (yaSugeridos.contains(idLower)) continue;

            Set<String> candidatos = new LinkedHashSet<>();
            for (String error : erroresSintacticos) {
                if (error.contains("'" + lexema + "'")) {
                    candidatos.addAll(candidatosEsperados(error));
                }
            }
            if (candidatos.isEmpty()) continue;

            boolean esReservadaExacta = false;
            for (String r : PALABRAS_RESERVADAS) {
                if (idLower.equals(r)) { esReservadaExacta = true; break; }
            }
            if (esReservadaExacta) continue;

            // Comparar solo con palabras que el parser esperaba en ese punto.
            String sugerencia = buscarSugerencia(idLower, candidatos.toArray(new String[0]));
            if (sugerencia != null) {
                yaSugeridos.add(idLower);
                sugerencias.add("Linea " + t.linea + ": '" + lexema
                    + "' se parece a la palabra reservada '" + sugerencia + "'."
                    + " \u00BFQuisiste decir '" + sugerencia + "'?");
            }
        }

        return sugerencias;
    }

    /** Extrae solo las palabras que el parser esperaba en ese punto. */
    private Set<String> candidatosEsperados(String error) {
        String esperado = error.toLowerCase();
        int inicio = esperado.indexOf("se esperaba");
        if (inicio < 0) return Collections.emptySet();

        esperado = esperado.substring(inicio + "se esperaba".length());
        int fin = esperado.indexOf(" despues");
        if (fin < 0) fin = esperado.indexOf(" pero se");
        if (fin >= 0) esperado = esperado.substring(0, fin);
        esperado = " " + esperado.replaceAll("[^a-z0-9áéíóúñ]+", " ") + " ";

        Set<String> candidatos = new LinkedHashSet<>();
        for (String reservada : PALABRAS_RESERVADAS) {
            if (esperado.contains(" " + reservada + " ")) {
                candidatos.add(reservada);
            }
        }
        return candidatos;
    }

    /**
     * Distancia de Levenshtein entre dos cadenas.
     */
    private int levenshtein(String a, String b) {
        int m = a.length(), n = b.length();
        int[][] dp = new int[m + 1][n + 1];
        for (int i = 0; i <= m; i++) dp[i][0] = i;
        for (int j = 0; j <= n; j++) dp[0][j] = j;
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost);
            }
        }
        return dp[m][n];
    }

    /**
     * Busca la palabra más cercana (distancia ≤ 2) entre los candidatos.
     * Retorna null si no hay ninguna lo suficientemente parecida.
     */
    private String buscarSugerencia(String texto, String[] candidatos) {
        String mejor = null;
        int menorDist = Integer.MAX_VALUE;
        String tLower = texto.toLowerCase();
        for (String c : candidatos) {
            int dist = levenshtein(tLower, c.toLowerCase());
            if (dist < menorDist && dist <= 2) {
                menorDist = dist;
                mejor = c;
            }
        }
        return mejor;
    }

    /** Compara primero ediciones cercanas y después anagramas exactos. */
    private String buscarSugerenciaReservada(String texto, String[] candidatos) {
        String sugerencia = buscarSugerencia(texto, candidatos);
        if (sugerencia != null) return sugerencia;

        for (String candidato : candidatos) {
            if (tienenLasMismasLetras(texto, candidato)) return candidato;
        }
        return null;
    }

    /** Determina si dos palabras tienen exactamente las mismas letras. */
    private boolean tienenLasMismasLetras(String primera, String segunda) {
        String a = primera.toLowerCase();
        String b = segunda.toLowerCase();
        if (a.length() != b.length()) return false;

        Map<Character, Integer> frecuencias = new HashMap<>();
        for (int i = 0; i < a.length(); i++) {
            char caracter = a.charAt(i);
            frecuencias.put(caracter, frecuencias.getOrDefault(caracter, 0) + 1);
        }
        for (int i = 0; i < b.length(); i++) {
            char caracter = b.charAt(i);
            Integer cantidad = frecuencias.get(caracter);
            if (cantidad == null || cantidad == 0) return false;
            if (cantidad == 1) frecuencias.remove(caracter);
            else frecuencias.put(caracter, cantidad - 1);
        }
        return frecuencias.isEmpty();
    }

    // =============================================================
    //  RELLENO DEL GUTTER
    // =============================================================

    /**
     * Coloca un texto en el editor y lo completa con líneas vacías hasta
     * {@link #MIN_LINEAS_GUTTER} para que la columna de números tenga siempre
     * la misma altura. Se recuerda qué parte es contenido real.
     */
    private void setTextoConRelleno(String texto) {
        String limpio = texto == null ? "" : texto;
        lineasReales = contarLineas(limpio);
        cargandoDocumento = true;
        try {
            editor.replaceText(0, editor.getLength(),
                completarConRelleno(limpio, MIN_LINEAS_GUTTER));
        } finally {
            cargandoDocumento = false;
        }
        // Cargar un documento no es una edición que el usuario pueda deshacer:
        // se borra el historial para que Ctrl+Z no devuelva el archivo anterior.
        editor.getUndoManager().forgetHistory();
        editor.getUndoManager().mark();
        dirty = false;
        actualizarEtiquetaArchivo();
        // replaceText no mueve el cursor: lo deja donde estuviera, y si estaba
        // al final pasa a apuntar al final del texto nuevo. Como el texto nuevo
        // son los párrafos reales más el relleno, el cursor aterriza sobre la
        // última línea de relleno y el usuario empieza a escribir ahí, en la
        // línea 100. Se devuelve al final del código, que es donde se quiere
        // seguir escribiendo.
        editor.getCaretSelectionBind().moveTo(finDelContenido(limpio));
        // El scroll va en el siguiente pulso: durante la construcción de la
        // ventana el CodeArea todavía no tiene skin montada y requestFollowCaret
        // no hace nada, así que el cursor quedaría en su sitio pero la vista
        // arriba del todo.
        Platform.runLater(editor::requestFollowCaret);
    }

    /**
     * Offset del último carácter con contenido, para dejar el cursor al final
     * del código y no sobre el relleno. Los saltos de línea y espacios del
     * final se saltan: si el archivo termina en {@code "sino = NORMAL;\n"}, el
     * cursor va detrás del punto y coma y no en la línea en blanco de debajo.
     */
    private static int finDelContenido(String texto) {
        int fin = texto.length();
        while (fin > 0 && Character.isWhitespace(texto.charAt(fin - 1))) fin--;
        return fin;
    }

    /** Texto a escribir en disco: el documento sin el relleno. */
    private String textoAGuardar() {
        return quitarRelleno(editor.getText(), lineasReales);
    }

    /**
     * Añade líneas vacías al final hasta llegar a {@code minimo} líneas.
     * No toca el contenido si ya hay suficientes.
     */
    static String completarConRelleno(String texto, int minimo) {
        int faltan = minimo - contarLineas(texto);
        return faltan > 0 ? texto + "\n".repeat(faltan) : texto;
    }

    /**
     * Quita el relleno de líneas vacías que {@link #completarConRelleno} añadió
     * por debajo de {@code lineasReales}. Solo recorta si esas líneas siguen
     * vacías; si el usuario escribió ahí, se respeta su contenido y se devuelve
     * el texto tal cual.
     */
    static String quitarRelleno(String texto, int lineasReales) {
        int sobra = contarLineas(texto) - lineasReales;
        if (sobra <= 0) return texto;

        int fin = texto.length();
        for (int i = 0; i < sobra; i++) {
            int salto = texto.lastIndexOf('\n', fin - 1);
            if (salto < 0) break;
            if (!texto.substring(salto + 1, fin).isBlank()) return texto;
            fin = salto;
        }
        return texto.substring(0, fin);
    }

    /** Cuántas líneas tiene un texto: un texto vacío tiene 1 línea. */
    static int contarLineas(String texto) {
        int lineas = 1;
        for (int i = 0; i < texto.length(); i++) {
            if (texto.charAt(i) == '\n') lineas++;
        }
        return lineas;
    }

    // =============================================================
    //  ACCIONES
    // =============================================================

    private void nuevoArchivo() {
        if (!confirmarDescartar()) return;
        setTextoConRelleno("");
        archivoActual = null;
        actualizarEtiquetaArchivo();
        editor.requestFocus();
    }

    private void abrirArchivo() {
        if (!confirmarDescartar()) return;

        FileChooser fc = new FileChooser();
        fc.setTitle("Abrir archivo Timotomata");
        fc.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Archivos Timotomata", "*.tm", "*.txt"));
        fc.setInitialDirectory(new File("."));
        File file = fc.showOpenDialog(ventana());
        if (file == null) return;

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            setTextoConRelleno(br.lines().collect(Collectors.joining("\n")));
            archivoActual = file.getAbsolutePath();
            actualizarEtiquetaArchivo();
        } catch (IOException ex) {
            mostrarError("No se pudo abrir el archivo: " + ex.getMessage());
        }
    }

    private void guardarArchivo() {
        if (archivoActual != null) {
            guardarA(new File(archivoActual));
        } else {
            guardarArchivoComo();
        }
    }

    private void guardarArchivoComo() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Guardar archivo Timotomata");
        fc.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Archivos Timotomata", "*.tm", "*.txt"));
        fc.setInitialFileName("programa.tm");
        File file = fc.showSaveDialog(ventana());
        if (file != null) {
            guardarA(file);
            archivoActual = file.getAbsolutePath();
            actualizarEtiquetaArchivo();
        }
    }

    private void guardarA(File file) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
            bw.write(textoAGuardar());
            // El punto de guardado pasa a ser el estado limpio del documento:
            // deshacer hasta aquí vuelve a quitar la marca de cambios.
            editor.getUndoManager().mark();
            dirty = false;
            actualizarEtiquetaArchivo();
        } catch (IOException ex) {
            mostrarError("No se pudo guardar: " + ex.getMessage());
        }
    }

    private void salir() {
        if (confirmarDescartar()) Platform.exit();
    }

    /**
     * Pregunta qué hacer si hay cambios sin guardar.
     * @return true si se puede continuar (guardado, descartado o sin cambios)
     */
    private boolean confirmarDescartar() {
        if (!dirty) return true;

        Alert alerta = new Alert(Alert.AlertType.CONFIRMATION);
        alerta.setTitle("Cambios sin guardar");
        alerta.setHeaderText("Hay cambios sin guardar.");
        alerta.setContentText("¿Quieres guardarlos antes de continuar?");

        ButtonType guardar = new ButtonType("Guardar", ButtonBar.ButtonData.YES);
        ButtonType descartar = new ButtonType("Descartar", ButtonBar.ButtonData.NO);
        ButtonType cancelar = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        alerta.getButtonTypes().setAll(guardar, descartar, cancelar);

        estilizarDialogo(alerta);
        Optional<ButtonType> eleccion = alerta.showAndWait();
        if (eleccion.isEmpty() || eleccion.get() == cancelar) return false;
        if (eleccion.get() == descartar) return true;

        guardarArchivo();
        return !dirty;
    }

    /** Devuelve true si la ventana puede cerrarse. */
    public boolean confirmarSalida() {
        return confirmarDescartar();
    }

    private Window ventana() {
        return stage != null ? stage : root.getScene().getWindow();
    }

    public void setStage(Stage stage) {
        this.stage = stage;
        actualizarEtiquetaArchivo();
    }

    private void actualizarEtiquetaArchivo() {
        String nombre = archivoActual == null
            ? "Sin archivo" : new File(archivoActual).getName();

        if (lblArchivo != null) {
            lblArchivo.setText(dirty ? nombre + " •" : nombre);
        }
        if (stage != null) {
            String base = "Compilador Timotomata";
            stage.setTitle((dirty ? "• " : "")
                + (archivoActual == null ? base
                    : nombre + " — " + base));
        }
    }

    // ─── Diálogos ───
    private void estilizarDialogo(Dialog<?> dialogo) {
        DialogPane pane = dialogo.getDialogPane();
        var css = getClass().getResource("estilos.css");
        if (css != null) pane.getStylesheets().add(css.toExternalForm());
        pane.getStyleClass().add("tm-dialog");

        // El diálogo hereda el icono del producto y se abre sobre la ventana
        // principal, no como una ventana huérfana con el icono de JavaFX.
        if (stage != null) {
            dialogo.initOwner(stage);
        }
        dialogo.setOnShown(e -> Iconos.aplicar(dialogo));
    }

    private void mostrarAtajos() {
        Alert alerta = new Alert(Alert.AlertType.INFORMATION);
        alerta.setTitle("Atajos de teclado");
        alerta.setHeaderText("Atajos de Timotomata");
        alerta.setContentText("""
            Ctrl+N   Nuevo archivo
            Ctrl+O   Abrir archivo
            Ctrl+S   Guardar
            Ctrl+Shift+S   Guardar como
            Ctrl+Z   Deshacer
            Ctrl+Y   Rehacer
            Ctrl+F   Buscar en el código
            F6       Árbol de derivación
            Ctrl+E   Ir al panel de problemas
            Ctrl+1   Volver al editor
            F1       Esta ayuda""");
        estilizarDialogo(alerta);
        alerta.showAndWait();
    }

    private void mostrarAcercaDe() {
        Alert alerta = new Alert(Alert.AlertType.INFORMATION);
        alerta.setTitle("Acerca de Timotomata");
        alerta.setHeaderText("Compilador Timotomata");
        alerta.setContentText("Editor y analizador para el lenguaje Timotomata.\n"
            + "Análisis léxico, sintáctico, semántico, AST y cuádruplos en tiempo real.");
        estilizarDialogo(alerta);
        alerta.showAndWait();
    }

    private void cargarEjemploPorDefecto() {
        setTextoConRelleno(
            // Cada sensor declara su tipo: define la unidad, el rango que el
            // transductor puede medir y su banda de frecuencia.
            "sensor voltaje     : ELECTRICO;\n"
            + "sensor temperatura : TERMICO;\n"
            + "\n"
            + "// El reloj del programa: segundos por muestreo. Define el limite de\n"
            + "// Nyquist contra el que se validan las frecuencias.\n"
            + "muestreo = 0.01;\n"
            + "\n"
            + "umbral voltaje_max : ELECTRICO = 220;\n"
            + "umbral voltaje_min : ELECTRICO = 100;\n"
            + "umbral temp_max    : TERMICO   = 80;\n"
            + "umbral temp_min    : TERMICO   = 0;\n"
            + "\n"
            + "// Estado inicial de la maquina.\n"
            + "estado = NORMAL;\n"
            + "\n"
            + "// Las reglas se evaluan en orden: gana la primera que se cumpla.\n"
            + "si voltaje     >= voltaje_max entonces estado = PICO;\n"
            + "si voltaje     <= voltaje_min entonces estado = CAIDA;\n"
            + "si temperatura >  temp_max    entonces estado = INESTABLE;\n"
            + "si temperatura <  temp_min    entonces estado = CAIDA;\n"
            + "\n"
            + "// El calculo escribe en una senal derivada: el canal fisico\n"
            + "// de voltaje queda intacto. El canal de entrada es el\n"
            + "// parametro CON= de la operacion, no un argumento suelto.\n"
            + "calcular(PROMEDIO(CON=voltaje, VENTANA=10), voltaje_prom);\n"
            + "calcular(MAXIMO(CON=temperatura, VENTANA=20), temp_maximo);\n"
            + "si voltaje_prom >= voltaje_max entonces estado = PICO;\n"
            + "\n"
            + "// Si ninguna regla se cumple.\n"
            + "sino = NORMAL;\n"
        );
    }

    // =============================================================
    //  UTILERÍAS
    // =============================================================

    private void mostrarError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        estilizarDialogo(alert);
        alert.showAndWait();
    }
}
