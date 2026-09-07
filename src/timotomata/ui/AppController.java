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

    /** Reutiliza LineNumberFactory y sincroniza el color con el párrafo activo. */
    private void configurarNumerosDeLinea() {
        IntFunction<Node> numerosBase = LineNumberFactory.get(editor);
        PseudoClass lineaActual = PseudoClass.getPseudoClass("current-line");
        var parrafoActual = editor.getCaretSelectionBind().paragraphIndexProperty();

        editor.setParagraphGraphicFactory(indice -> {
            Node nodo = numerosBase.apply(indice);
            if (nodo instanceof Label numero) {
                numero.getStyleClass().add("tm-line-number");
                Runnable actualizar = () -> {
                    boolean activo = parrafoActual.getValue() == indice;
                    numero.pseudoClassStateChanged(lineaActual, activo);
                    numero.setBackground(new Background(new BackgroundFill(
                        Color.web(activo ? "rgba(137, 180, 250, 0.22)" : "#181825"),
                        CornerRadii.EMPTY, Insets.EMPTY)));
                    numero.setTextFill(Color.web(activo ? "#cdd6f4" : "#6c7086"));
                };
                parrafoActual.addListener((obs, anterior, nuevo) -> actualizar.run());
                actualizar.run();
            }
            return nodo;
        });
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

        actualizarEstilosEditor(codigo, tokens);

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

        // Mostrar TODOS los errores: léxicos + sintácticos + semánticos.
        // Solo se oculta el sintáctico que es duplicado exacto de un typo
        // ya reportado como léxico (mismo lexema), para no contar dos veces
        // el mismo problema. El parser ya usa recuperación panic-mode y
        // recorre todo el código, así que erroresSint trae todos los del archivo.
        List<String> erroresSintParaUI = filtrarSintacticosDuplicados(erroresSint, erroresReservadas);

        ultimoPrograma = programa;
        ultimoParser = parser;

        // ─── Fase 3: Tabla de símbolos y detección de IDs no declarados ───
        construirTablaSimbolos(programa, tokens);
        List<String> erroresNoDecl = detectarNoDeclarados(programa, tokens);

        // ─── Fase 4: Sugerencias de palabras reservadas mal escritas ───
        List<String> sugerenciasReservadas = Collections.emptyList();

        // ─── Actualizar UI ───
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

    /** Aplica estilos por rango al mismo CodeArea que recibe la edición. */
    private void actualizarEstilosEditor(String codigo, List<Token> tokens) {
        StyleSpansBuilder<Collection<String>> estilos = new StyleSpansBuilder<>();
        int cursor = 0;

        for (Token token : tokens) {
            if (token.tipo == TipoToken.EOF || token.lexema.isEmpty()) continue;

            int inicio = buscarInicioToken(codigo, token, cursor);
            if (inicio < cursor) inicio = codigo.indexOf(token.lexema, cursor);
            if (inicio < 0) continue;

            agregarEstilosDeTexto(codigo.substring(cursor, inicio), estilos);
            estilos.add(Collections.singleton(claseParaToken(token.tipo.name())), token.lexema.length());
            cursor = inicio + token.lexema.length();
        }
        agregarEstilosDeTexto(codigo.substring(Math.min(cursor, codigo.length())), estilos);
        editor.setStyleSpans(0, estilos.create());
    }

    /** Conserva comentarios como una categoría visual aunque el lexer los descarte. */
    private void agregarEstilosDeTexto(String texto,
                                      StyleSpansBuilder<Collection<String>> estilos) {
        int cursor = 0;
        while (cursor < texto.length()) {
            int linea = texto.indexOf("//", cursor);
            int bloque = texto.indexOf("/*", cursor);
            int inicioComentario;
            boolean esLinea;

            if (linea < 0) {
                inicioComentario = bloque;
                esLinea = false;
            } else if (bloque < 0 || linea < bloque) {
                inicioComentario = linea;
                esLinea = true;
            } else {
                inicioComentario = bloque;
                esLinea = false;
            }

            if (inicioComentario < 0) {
                estilos.add(Collections.emptyList(), texto.length() - cursor);
                return;
            }
            if (inicioComentario > cursor) {
                estilos.add(Collections.emptyList(), inicioComentario - cursor);
            }

            int fin;
            if (esLinea) {
                fin = texto.indexOf('\n', inicioComentario);
                if (fin < 0) fin = texto.length();
            } else {
                fin = texto.indexOf("*/", inicioComentario + 2);
                fin = fin < 0 ? texto.length() : fin + 2;
            }
            estilos.add(Collections.singleton("tm-comment"), fin - inicioComentario);
            cursor = fin;
        }
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
     * Sin análisis semántico: solo recopila información ya disponible
     * del parser (sensores declarados, umbrales) y detecta referencias
     * a variables no declaradas.
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

        // 3. Referencias a identificadores en expresiones
        Set<String> referenciadas = extraerVariablesReferenciadas(programa);
        for (String ref : referenciadas) {
            if (!declaradas.contains(ref)) {
                int linea = buscarLineaPrimerUso(ref, tokens);
                simbolosData.add(new InfoSimbolo(ref, "NO DECLARADO", linea, "\u2014", "\u2014"));
            }
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
     * Detecta identificadores usados pero no declarados y genera errores
     * con sugerencias ortográficas usando distancia de Levenshtein.
     */
    private List<String> detectarNoDeclarados(Programa programa, List<Token> tokens) {
        List<String> errores = new ArrayList<>();
        if (programa == null) return errores;

        Set<String> declaradas = new HashSet<>();
        declaradas.addAll(programa.sensores);
        declaradas.addAll(programa.umbrales.keySet());

        Set<String> referenciadas = extraerVariablesReferenciadas(programa);
        for (String ref : referenciadas) {
            if (!declaradas.contains(ref)) {
                int linea = buscarLineaPrimerUso(ref, tokens);
                StringBuilder sb = new StringBuilder();
                sb.append("Error semantico en linea ").append(linea)
                    .append(": El identificador '").append(ref)
                    .append("' no ha sido declarado.");

                // Sugerencia 1: comparar con sensores/umbrales declarados
                String sugerencia = buscarSugerencia(ref, declaradas.toArray(new String[0]));

                if (sugerencia != null) {
                    sb.append(" [SUGERENCIA: \u00BFQuisiste decir '").append(sugerencia).append("'?]");
                }
                errores.add(sb.toString());
            }
        }
        return errores;
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
            if (sugerencia != null && !sugerencia.equalsIgnoreCase(token.lexema)) {
                errores.putIfAbsent(token.lexema.toLowerCase(),
                    "Error lexico en linea " + token.linea + ": La palabra '"
                    + token.lexema + "' parece una palabra reservada mal escrita."
                    + " \u00BFQuisiste decir '" + sugerencia + "'?");
            }
        }
        return errores;
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
     * Filtra solo los errores sintácticos duplicados de un typo léxico.
     * Si un error sintáctico menciona entre comillas un lexema que ya fue
     * reportado como palabra reservada mal escrita (clave en minúsculas),
     * se oculta ese sintáctico para no duplicar. Todos los demás
     * sintácticos se conservan: el análisis recorre todo el código.
     */
    private List<String> filtrarSintacticosDuplicados(List<String> erroresSint,
                                                      Map<String, String> typosLexicos) {
        if (erroresSint.isEmpty() || typosLexicos.isEmpty()) {
            return new ArrayList<>(erroresSint);
        }
        List<String> filtrados = new ArrayList<>();
        for (String error : erroresSint) {
            String errorLower = error.toLowerCase();
            boolean esDuplicado = false;
            for (String lexemaTypo : typosLexicos.keySet()) {
                if (errorLower.contains("'" + lexemaTypo.toLowerCase() + "'")) {
                    esDuplicado = true;
                    break;
                }
            }
            if (!esDuplicado) {
                filtrados.add(error);
            }
        }
        return filtrados;
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
