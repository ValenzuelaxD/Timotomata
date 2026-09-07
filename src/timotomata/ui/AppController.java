package timotomata.ui;

import java.io.*;
import java.util.*;
import java.util.stream.*;

import javafx.animation.PauseTransition;
import javafx.beans.property.*;
import javafx.collections.*;
import javafx.css.PseudoClass;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.function.IntFunction;

import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import timotomata.lexer.*;
import timotomata.parser.*;
import timotomata.parser.ast.*;

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
        private final StringProperty tipo = new SimpleStringProperty();
        private final IntegerProperty linea = new SimpleIntegerProperty();
        private final StringProperty valor = new SimpleStringProperty();
        private final StringProperty tipoNum = new SimpleStringProperty();

        public InfoSimbolo(String nombre, String tipo, int linea, String valor, String tipoNum) {
            this.nombre.set(nombre);
            this.tipo.set(tipo);
            this.linea.set(linea);
            this.valor.set(valor);
            this.tipoNum.set(tipoNum);
        }
        public StringProperty nombreProperty() { return nombre; }
        public StringProperty tipoProperty() { return tipo; }
        public IntegerProperty lineaProperty() { return linea; }
        public StringProperty valorProperty() { return valor; }
        public StringProperty tipoNumProperty() { return tipoNum; }
    }

    // ─── Componentes de la UI ───
    private BorderPane root;
    private CodeArea editor;
    private Label statusLabel;
    private ListView<String> errorList;
    private TitledPane erroresPane;
    private TableView<TokenInfo> tokenTable;
    private ObservableList<TokenInfo> tokenData = FXCollections.observableArrayList();
    private TitledPane tokensPane;
    private TreeView<String> astTree;
    private TitledPane astPane;
    private VBox derivacionPanel;
    private Button btnAbrirArbol;
    private PauseTransition debounce;

    // ─── Tabla de símbolos ───
    private TableView<InfoSimbolo> tablaSimbolos;
    private ObservableList<InfoSimbolo> simbolosData = FXCollections.observableArrayList();
    private TitledPane tablaPane;

    // ─── Palabras reservadas para sugerencias ───
    private static final String[] PALABRAS_RESERVADAS = {
        "sensor", "umbral", "si", "entonces", "estado",
        "abs", "calcular", "normal", "pico", "caida", "inestable",
        "seno", "coseno", "cuadrada", "promedio", "maximo", "suma",
        "amplitud", "frecuencia", "ventana", "con", "fin"
    };

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

        // ─── Panel central ───
        SplitPane split = new SplitPane();
        split.setDividerPositions(0.62);
        split.getItems().addAll(crearPanelEditor(), crearPanelInfo());
        SplitPane.setResizableWithParent(split.getItems().get(0), true);
        root.setCenter(split);

        // ─── Panel inferior: árbol de derivación + barra de estado ───
        VBox bottomArea = new VBox();
        bottomArea.getChildren().add(crearPanelDerivacion());
        bottomArea.getChildren().add(crearStatusBar());
        root.setBottom(bottomArea);

        // ─── Atajos de teclado (sin toolbar) ───
        root.setOnKeyReleased(e -> {
            if (e.isControlDown() && e.getCode() == KeyCode.N) {
                nuevoArchivo();
            } else if (e.isControlDown() && e.getCode() == KeyCode.O) {
                abrirArchivo();
            } else if (e.isControlDown() && e.getCode() == KeyCode.S) {
                guardarArchivo();
            } else if (e.getCode() == KeyCode.F5) {
                analizarCodigo();
            } else if (e.getCode() == KeyCode.F6) {
                abrirArbolDerivacion();
            }
        });
    }

    // ─── Panel del editor (con números de línea a la izquierda) ───
    private VBox crearPanelEditor() {
        VBox panel = new VBox(0);
        panel.setStyle("-fx-background-color: #1e1e2e;");

        editor = new CodeArea();
        editor.getStyleClass().add("syntax-editor");
        editor.setStyle("-fx-background-color: #1e1e2e;"
            + " -fx-highlight-fill: rgba(137, 180, 250, 0.22);"
            + " -fx-highlight-text-fill: #cdd6f4;"
            + " -fx-font-family: 'Consolas', 'Courier New', monospace;"
            + " -fx-font-size: 14px;"
            + " -fx-line-spacing: 2px;"
            + " -fx-border-color: transparent;"
            + " -fx-padding: 4px 8px 4px 0;"
            + " -fx-background-insets: 0;");
        editor.setWrapText(false);
        editor.useInitialStyleForInsertionProperty().set(false);
        editor.setTextInsertionStyle(Collections.singleton("tm-plain"));
        configurarNumerosDeLinea();

        // Solo los cambios de texto programan el análisis; mover el cursor o
        // seleccionar no reconstruye tokens ni estilos.
        editor.textProperty().addListener((obs, oldText, newText) -> {
            if (debounce != null) debounce.playFromStart();
        });
        editor.caretPositionProperty().addListener((obs, oldPos, newPos) ->
            actualizarStatusCursor());

        panel.getChildren().add(editor);
        VBox.setVgrow(editor, Priority.ALWAYS);
        return panel;
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
                            Color.web(activo ? "rgba(137, 180, 250, 0.22)" : "#181825"),
                            CornerRadii.EMPTY, Insets.EMPTY)));
                    }
                    numero.setTextFill(Color.web(conError ? "#f38ba8"
                        : (activo ? "#cdd6f4" : "#6c7086")));
                    numero.setStyle(conError ? "-fx-font-weight: bold;" : "");
                    numero.setGraphic(conError ? punto : null);
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
    private void actualizarIconosLinea(List<String> erroresLex, List<String> erroresSint) {
        Set<Integer> nuevas = new HashSet<>();
        Map<Integer, String> mensajes = new HashMap<>();
        List<String> todos = new ArrayList<>();
        if (erroresLex != null) todos.addAll(erroresLex);
        if (erroresSint != null) todos.addAll(erroresSint);
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
    private VBox crearPanelInfo() {
        VBox panel = new VBox(6);
        panel.setStyle("-fx-background-color: #11111b; -fx-padding: 6;");

        // Panel de Errores
        errorList = new ListView<>();
        errorList.setPrefHeight(140);
        errorList.setStyle("-fx-control-inner-background: #1e1e2e;"
            + " -fx-text-fill: #cdd6f4;"
            + " -fx-font-family: 'Consolas', monospace;"
            + " -fx-font-size: 11px;");
        erroresPane = new TitledPane("ERRORES", errorList);
        erroresPane.getStyleClass().add("titled-pane-custom");
        erroresPane.setCollapsible(false);

        // Panel de Tokens — 3 columnas: Tipo, Lexema, Linea
        tokenTable = new TableView<>(tokenData);
        tokenTable.setPrefHeight(180);
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

        TableColumn<TokenInfo, Number> colLinea = new TableColumn<>("Linea");
        colLinea.setCellValueFactory(new PropertyValueFactory<>("linea"));
        colLinea.setPrefWidth(55);
        colLinea.setStyle("-fx-alignment: CENTER-RIGHT;");

        tokenTable.getColumns().addAll(colTipo, colLexema, colLinea);
        tokenTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        tokensPane = new TitledPane("TOKENS", tokenTable);
        tokensPane.getStyleClass().add("titled-pane-custom");
        tokensPane.setCollapsible(false);

        // ─── Panel de la Tabla de Símbolos (5 columnas) ───
        tablaSimbolos = new TableView<>(simbolosData);
        tablaSimbolos.setPrefHeight(150);
        tablaSimbolos.setStyle("-fx-control-inner-background: #1e1e2e;"
            + " -fx-table-cell-border-color: #313244;"
            + " -fx-text-fill: #cdd6f4;"
            + " -fx-font-family: 'Consolas', monospace;"
            + " -fx-font-size: 11px;");

        TableColumn<InfoSimbolo, String> colSimbNombre = new TableColumn<>("Nombre");
        colSimbNombre.setCellValueFactory(new PropertyValueFactory<>("nombre"));
        colSimbNombre.setPrefWidth(110);

        TableColumn<InfoSimbolo, String> colSimbTipo = new TableColumn<>("Tipo");
        colSimbTipo.setCellValueFactory(new PropertyValueFactory<>("tipo"));
        colSimbTipo.setPrefWidth(80);

        TableColumn<InfoSimbolo, Number> colSimbLinea = new TableColumn<>("Linea");
        colSimbLinea.setCellValueFactory(new PropertyValueFactory<>("linea"));
        colSimbLinea.setPrefWidth(45);
        colSimbLinea.setStyle("-fx-alignment: CENTER-RIGHT;");

        TableColumn<InfoSimbolo, String> colSimbValor = new TableColumn<>("Valor");
        colSimbValor.setCellValueFactory(new PropertyValueFactory<>("valor"));
        colSimbValor.setPrefWidth(70);

        TableColumn<InfoSimbolo, String> colSimbTipoNum = new TableColumn<>("Tipo Num.");
        colSimbTipoNum.setCellValueFactory(new PropertyValueFactory<>("tipoNum"));
        colSimbTipoNum.setPrefWidth(80);

        tablaSimbolos.getColumns().addAll(colSimbNombre, colSimbTipo, colSimbLinea, colSimbValor, colSimbTipoNum);
        tablaSimbolos.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        tablaPane = new TitledPane("TABLA SIMBOLOS", tablaSimbolos);
        tablaPane.getStyleClass().add("titled-pane-custom");
        tablaPane.setCollapsible(false);

        // Panel del AST
        astTree = new TreeView<>();
        astTree.setPrefHeight(180);
        astTree.setStyle("-fx-control-inner-background: #1e1e2e;"
            + " -fx-text-fill: #cdd6f4;"
            + " -fx-font-family: 'Consolas', monospace;"
            + " -fx-font-size: 11px;"
            + " -fx-accent: #45475a;");
        astTree.setShowRoot(false);
        astPane = new TitledPane("AST (Arbol Sintactico)", astTree);
        astPane.getStyleClass().add("titled-pane-custom");
        astPane.setCollapsible(false);

        panel.getChildren().addAll(erroresPane, tokensPane, tablaPane, astPane);
        VBox.setVgrow(astPane, Priority.ALWAYS);
        return panel;
    }

    // ─── Panel inferior: árbol de derivación ───
    private VBox crearPanelDerivacion() {
        derivacionPanel = new VBox(6);
        derivacionPanel.setStyle("-fx-background-color: #181825; -fx-padding: 8 12;");

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);

        Label lblTitulo = new Label("ARBOL DE DERIVACION");
        lblTitulo.setStyle("-fx-text-fill: #89b4fa; -fx-font-weight: bold; -fx-font-size: 12;");

        btnAbrirArbol = new Button("Ver arbol de derivacion");
        btnAbrirArbol.setStyle("-fx-background-color: #89b4fa; -fx-text-fill: #11111b;"
            + " -fx-font-weight: bold; -fx-font-size: 12px;"
            + " -fx-background-radius: 6; -fx-padding: 4 14;"
            + " -fx-cursor: hand;");
        btnAbrirArbol.setOnAction(e -> abrirArbolDerivacion());
        btnAbrirArbol.setDisable(true);

        header.getChildren().addAll(lblTitulo, btnAbrirArbol);

        derivacionPanel.getChildren().add(header);
        return derivacionPanel;
    }

    // ─── Barra de estado inferior ───
    private HBox crearStatusBar() {
        HBox statusBar = new HBox(8);
        statusBar.setStyle("-fx-background-color: #181825;"
            + " -fx-padding: 4 12;"
            + " -fx-border-color: #313244 transparent transparent transparent;"
            + " -fx-border-width: 1;");

        Label lblArchivo = new Label("Sin archivo");
        lblArchivo.setStyle("-fx-text-fill: #a6adc8; -fx-font-size: 11px;"
            + " -fx-font-family: 'Segoe UI', sans-serif;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        statusLabel = new Label("  Ln 1, Col 1  |  SIN ANALIZAR");
        statusLabel.setStyle("-fx-text-fill: #a6adc8;"
            + " -fx-font-family: 'Consolas', monospace; -fx-font-size: 11;");

        statusBar.getChildren().addAll(lblArchivo, spacer, statusLabel);
        return statusBar;
    }

    // =============================================================
    //  CONFIGURACIÓN DEL DEBOUNCE (300ms)
    // =============================================================
    private void configurarDebounce() {
        debounce = new PauseTransition(Duration.millis(300));
        debounce.setOnFinished(e -> analizarCodigo());
    }

    // =============================================================
    //  ANÁLISIS EN TIEMPO REAL
    // =============================================================
    public void analizarCodigo() {
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
            detectarReservadaIncluida(tokens, erroresReservadas.keySet());
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

        // ─── Fase 3: Semántica DESACTIVADA por pedido ───
        // No se maneja análisis semántico de momento: ni identificadores no
        // declarados ni parámetros de calcular se reportan. Si algo vacío o
        // incompleto cae además en léxico/sintáctico, eso sí se marca arriba;
        // pero si SOLO sería semántico, se ignora. La validación de parámetros
        // (validarParametrosCalculo) se conserva abajo para reactivarla después.
        construirTablaSimbolos(programa, tokens);
        List<String> erroresNoDecl = Collections.emptyList();

        // ─── Fase 4: Sugerencias de palabras reservadas mal escritas ───
        List<String> sugerenciasReservadas = Collections.emptyList();

        // ─── Actualizar UI ───
        // Marcas en el código: subrayado rojo en tokens con error + iconos
        // en los números de línea. Se pinta al final porque necesita todos
        // los errores ya calculados (léxico + sintáctico).
        Set<String> lexemasConErrorLex = new HashSet<>(erroresReservadas.keySet());
        lexemasConErrorLex.addAll(erroresIncluidos.keySet());
        Set<String> clavesSintacticas = extraerClavesSintacticas(erroresSint);
        boolean[] charsConErrorLex = marcarCharsErrorLexico(codigo, lexer.getRangosErrorLexico());
        actualizarEstilosEditor(codigo, tokens, lexemasConErrorLex, clavesSintacticas, charsConErrorLex);
        actualizarIconosLinea(erroresLex, erroresSintParaUI);
        actualizarErrores(erroresLex, erroresSintParaUI, erroresNoDecl, sugerenciasReservadas);
        actualizarTokens(tokens);
        boolean codigoValido = erroresLex.isEmpty()
            && erroresSintParaUI.isEmpty()
            && erroresNoDecl.isEmpty();
        actualizarAST(codigoValido ? programa : null);
        actualizarDerivacion(codigoValido ? programa : null,
            codigoValido ? parser : null);
        actualizarStatus(tokens.size(), erroresLex.size(), erroresSintParaUI.size(),
            erroresNoDecl.size() + sugerenciasReservadas.size());
    }

    // =============================================================
    //  ACTUALIZACIÓN DE LA UI
    // =============================================================

    // ─── Errores ───
    private void actualizarErrores(List<String> erroresLex, List<String> erroresSint,
                                    List<String> erroresNoDecl, List<String> sugerencias) {
        ObservableList<String> items = errorList.getItems();
        items.clear();

        int totalErrores = 0;

        for (String err : erroresLex) {
            items.add("[ERROR LEXICO] " + err);
            totalErrores++;
        }

        for (String err : sugerencias) {
            items.add("[SUGERENCIA] " + err);
            totalErrores++;
        }

        for (String err : erroresSint) {
            items.add("[ERROR SINTACTICO] " + err);
            totalErrores++;
        }

        for (String err : erroresNoDecl) {
            items.add("[ERROR SEMANTICO] " + err);
            totalErrores++;
        }

        if (totalErrores == 0) {
            items.add("Sin errores.");
        }

        if (totalErrores > 0) {
            erroresPane.setText("ERRORES (" + totalErrores + ")");
            erroresPane.setStyle("-fx-text-fill: #f38ba8;");
        } else {
            erroresPane.setText("OK (0)");
            erroresPane.setStyle("-fx-text-fill: #a6e3a1;");
        }
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
        tokensPane.setText("\u25C6 TOKENS (" + tokenData.size() + ")");
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

    // ─── AST ───
    private void actualizarAST(Programa programa) {
        if (programa == null) {
            astTree.setRoot(null);
            astPane.setText("AST (bloqueado por errores)");
            return;
        }

        astPane.setText("AST (Arbol Sintactico)");

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
                String condStr = expresionToString(r.condicion);
                TreeItem<String> regla = new TreeItem<>(
                    "Regla " + (i + 1) + ": Si " + condStr);
                regla.getChildren().add(new TreeItem<>("\u2192 Estado: " + r.estado));
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
                TreeItem<String> calcItem = new TreeItem<>("CALCULAR " + c.sensor + " , " + c.operacion);
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
        derivacionPanel.getChildren().clear();

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);

        Label lblTitulo = new Label("ARBOL DE DERIVACION");
        lblTitulo.setStyle("-fx-text-fill: #89b4fa; -fx-font-weight: bold; -fx-font-size: 12;");

        btnAbrirArbol = new Button("Ver arbol de derivacion");
        btnAbrirArbol.setStyle("-fx-background-color: #89b4fa; -fx-text-fill: #11111b;"
            + " -fx-font-weight: bold; -fx-font-size: 12px;"
            + " -fx-background-radius: 6; -fx-padding: 4 14;"
            + " -fx-cursor: hand;");
        btnAbrirArbol.setOnAction(e -> abrirArbolDerivacion());

        header.getChildren().addAll(lblTitulo, btnAbrirArbol);
        derivacionPanel.getChildren().add(header);

        if (programa == null || parser == null || parser.arbolDerivacion == null) {
            btnAbrirArbol.setDisable(true);
            Label info = new Label("Arbol no disponible: corrige primero los errores del codigo.");
            info.setStyle("-fx-text-fill: #a6adc8; -fx-font-family: 'Consolas', monospace;"
                + " -fx-font-size: 12px; -fx-padding: 8 0;");
            derivacionPanel.getChildren().add(info);
            return;
        }

        btnAbrirArbol.setDisable(false);

        // Vista previa del árbol en texto (solo primeros niveles)
        String arbolTexto = arbolPreviewCompleto(parser.arbolDerivacion);
        TextArea arbolPreviewArea = new TextArea(arbolTexto);
        arbolPreviewArea.setEditable(false);
        arbolPreviewArea.setPrefHeight(130);
        arbolPreviewArea.setStyle("-fx-control-inner-background: #1e1e2e;"
            + " -fx-text-fill: #a6e3a1;"
            + " -fx-font-family: 'Consolas', monospace;"
            + " -fx-font-size: 11px;"
            + " -fx-border-color: #313244;"
            + " -fx-border-radius: 4;"
            + " -fx-padding: 4;");

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
            mostrarError("No hay \u00E1rbol de derivaci\u00F3n disponible. Analiza el c\u00F3digo primero.");
            return;
        }
        PanelDerivacion panel = new PanelDerivacion(ultimoParser.arbolDerivacion);
        panel.mostrarEnVentana();
    }

    // ─── Convertir expresión a string para el AST ───
    private String expresionToString(Expresion e) {
        if (e instanceof Numero n) return String.valueOf(n.valor);
        if (e instanceof Variable v) return v.nombre;
        if (e instanceof Binaria b) {
            return expresionToString(b.izquierda) + " "
                + b.operador + " " + expresionToString(b.derecha);
        }
        if (e instanceof Negacion n) return "-(" + expresionToString(n.expresion) + ")";
        if (e instanceof Abs a) return "abs(" + expresionToString(a.expresion) + ")";
        return "?";
    }

    // ─── Barra de estado ───
    private void actualizarStatus(int numTokens, int numErroresLex, int numErroresSint, int numErroresNoDecl) {
        int totalErrores = numErroresLex + numErroresSint + numErroresNoDecl;
        String color = totalErrores > 0 ? "#f38ba8" : "#a6e3a1";
        statusLabel.setStyle("-fx-text-fill: " + color
            + "; -fx-font-family: 'Consolas', monospace; -fx-font-size: 11;");
        statusLabel.setText("  Tokens: " + numTokens
            + "  |  Errores: " + totalErrores
            + "  |  " + (totalErrores > 0 ? "CON ERRORES" : "OK"));
    }

    private void actualizarStatusCursor() {
        String texto = editor.getText();
        int pos = editor.getCaretPosition();
        if (texto.isEmpty() || pos > texto.length()) return;

        int linea = 1, columna = 1;
        for (int i = 0; i < pos && i < texto.length(); i++) {
            if (texto.charAt(i) == '\n') {
                linea++;
                columna = 1;
            } else {
                columna++;
            }
        }
        String current = statusLabel.getText();
        if (current.contains("|  ")) {
            String analisis = current.substring(current.lastIndexOf("|"));
            statusLabel.setText("  Ln " + linea + ", Col " + columna + "  " + analisis);
        }
    }

    // =============================================================
    //  TABLA DE SÍMBOLOS Y VERIFICACIÓN DE IDs
    // =============================================================

    /**
     * Construye la tabla de símbolos a partir del AST y los tokens.
     * Análisis semántico DESACTIVADO: solo muestra lo declarado
     * (sensores y umbrales). No se agregan filas "NO DECLARADO".
     */
    private void construirTablaSimbolos(Programa programa, List<Token> tokens) {
        simbolosData.clear();
        if (programa == null) return;

        Set<String> declaradas = new HashSet<>();

        // 1. Sensores declarados
        for (String s : programa.sensores) {
            int linea = buscarLineaDeclaracion(s, tokens, TipoToken.SENSOR);
            simbolosData.add(new InfoSimbolo(s, "SENSOR", linea, "\u2014", "\u2014"));
            declaradas.add(s);
        }

        // 2. Umbrales declarados
        for (Map.Entry<String, Double> e : programa.umbrales.entrySet()) {
            int linea = buscarLineaDeclaracion(e.getKey(), tokens, TipoToken.UMBRAL);
            String valorStr = String.valueOf(e.getValue());
            // Determinar si es ENTERO o DECIMAL según si tiene punto decimal
            String tipoNum = esEntero(e.getValue()) ? "ENTERO" : "DECIMAL";
            simbolosData.add(new InfoSimbolo(e.getKey(), "UMBRAL", linea, valorStr, tipoNum));
            declaradas.add(e.getKey());
        }

        tablaPane.setText("\u25C6 TABLA SIMBOLOS (" + simbolosData.size() + ")");
    }

    /** Determina si un valor double es entero (sin parte decimal) */
    private boolean esEntero(double valor) {
        return valor == Math.floor(valor) && !Double.isInfinite(valor);
    }

    /** Busca la línea donde se declaró un sensor o umbral */
    private int buscarLineaDeclaracion(String nombre, List<Token> tokens, TipoToken tipoDecl) {
        for (int i = 0; i < tokens.size() - 1; i++) {
            Token t = tokens.get(i);
            Token next = tokens.get(i + 1);
            if (t.tipo == tipoDecl && next.tipo == TipoToken.ID && next.lexema.equals(nombre)) {
                return t.linea;
            }
        }
        return 0;
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
            extraerVariablesDeExpresion(r.condicion, vars);
        }
        for (Calculo c : programa.calculos) {
            vars.add(c.sensor);
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
     */
    private Map<String, List<String>> detectarReservadaIncluida(List<Token> tokens,
                                                                Set<String> lexemasYaReportados) {
        Map<String, List<String>> errores = new LinkedHashMap<>();
        for (Token token : tokens) {
            if (token.tipo != TipoToken.ID) continue;
            String lower = token.lexema.toLowerCase();
            if (lexemasYaReportados != null && lexemasYaReportados.contains(lower)) continue;

            String mejorReservada = null;
            String fragmentoHallado = null;
            boolean esReves = false;
            boolean conRelleno = false;
            boolean esIncompleta = false;

            // Solo-letras: quita dígitos y '_' para ver si la reservada está
            // partida por caracteres en medio (ej. CAL165CULAR → CALCULAR).
            String soloLetras = lower.replaceAll("[^a-z]", "");

            for (String reservada : PALABRAS_RESERVADAS) {
                String r = reservada.toLowerCase();
                if (r.length() < 4) continue;
                if (lower.contains(r)) {
                    if (mejorReservada == null || r.length() > mejorReservada.length()) {
                        mejorReservada = reservada;
                        fragmentoHallado = r;
                        esReves = false;
                        conRelleno = false;
                        esIncompleta = false;
                    }
                }
                String rev = new StringBuilder(r).reverse().toString();
                if (lower.contains(rev)) {
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
                if (!soloLetras.equals(lower)) {
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
            return new String[] { "sensor", "umbral", "si", "calcular", "fin" };
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
    //  ACCIONES
    // =============================================================

    private void nuevoArchivo() {
        editor.replaceText(0, editor.getLength(), "");
        archivoActual = null;
    }

    private void abrirArchivo() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Abrir archivo Timotomata");
        fc.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Archivos Timotomata", "*.txt", "*.tm"));
        fc.setInitialDirectory(new File("."));
        File file = fc.showOpenDialog(root.getScene().getWindow());
        if (file != null) {
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                editor.replaceText(0, editor.getLength(), br.lines().collect(Collectors.joining("\n")));
                archivoActual = file.getAbsolutePath();
            } catch (IOException ex) {
                mostrarError("No se pudo abrir el archivo: " + ex.getMessage());
            }
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
            new FileChooser.ExtensionFilter("Archivos Timotomata", "*.txt"));
        fc.setInitialFileName("programa.txt");
        File file = fc.showSaveDialog(root.getScene().getWindow());
        if (file != null) {
            guardarA(file);
            archivoActual = file.getAbsolutePath();
        }
    }

    private void guardarA(File file) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
            bw.write(editor.getText());
        } catch (IOException ex) {
            mostrarError("No se pudo guardar: " + ex.getMessage());
        }
    }

    private void cargarEjemploPorDefecto() {
        editor.replaceText(0, editor.getLength(),
            "sensor voltaje;\n"
            + "sensor temperatura;\n"
            + "umbral maxValor = 220;\n"
            + "umbral minValor = 100;\n"
            + "\n"
            + "si voltaje >= maxValor entonces estado = PICO;\n"
            + "si voltaje <= minValor entonces estado = CAIDA;\n"
            + "si temperatura > 80 entonces estado = INESTABLE;\n"
            + "\n"
            + "calcular(voltaje, SENO(AMPLITUD=300, FRECUENCIA=0.1));\n"
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
        alert.showAndWait();
    }
}
