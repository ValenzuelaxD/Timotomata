package timotomata.ui;

import java.io.*;
import java.util.*;
import java.util.stream.*;

import javafx.animation.PauseTransition;
import javafx.beans.property.*;
import javafx.collections.*;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import timotomata.lexer.*;
import timotomata.parser.*;
import timotomata.parser.ast.*;

/**
 * Controlador principal de la interfaz gráfica.
 * Gestiona el editor de código basado en RichTextFX, el análisis en tiempo real,
 * la visualización de tokens/AST, la tabla de símbolos y la simulación.
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
        "amplitud", "frecuencia", "ventana", "con", "fin",
        "tipo", "electrico", "termico", "rango", "minimo", "maximo", "y", "o", "alerta", "fluctuacion"
    };

    // ─── Estado interno ───
    private Programa ultimoPrograma;
    private Parser ultimoParser;
    private String archivoActual = null;
    private Set<String> idsConSugerencia = new HashSet<>();

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

        // ─── Atajos de teclado ───
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

    // ─── Panel del editor (con números de línea nativos de RichTextFX) ───
    private VBox crearPanelEditor() {
        VBox panel = new VBox(0);
        panel.setStyle("-fx-background-color: #1e1e2e;");

        HBox editorRow = new HBox(0);
        editorRow.setStyle("-fx-background-color: #1e1e2e;");

        // Instanciar CodeArea de RichTextFX
        editor = new CodeArea();
        
        // Habilitar números de línea nativos
        editor.setParagraphGraphicFactory(LineNumberFactory.get(editor));
        
        editor.setStyle("-fx-font-family: 'Consolas', 'Courier New', monospace;"
            + " -fx-font-size: 14px;"
            + " -fx-line-spacing: 2px;");
        editor.getStyleClass().add("styled-text-area");

        editorRow.getChildren().add(editor);
        HBox.setHgrow(editor, Priority.ALWAYS);

        // Teclado: F5/F6 los maneja root, el resto dispara debounce
        editor.setOnKeyReleased(e -> {
            if (e.getCode() == KeyCode.F5 || e.getCode() == KeyCode.F6) {
                // Los maneja root.setOnKeyReleased
            } else {
                debounce.playFromStart();
            }
        });

        // Actualizar cursor en barra de estado
        editor.caretPositionProperty().addListener((obs, oldPos, newPos) -> {
            actualizarStatusCursor();
        });

        panel.getChildren().add(editorRow);
        VBox.setVgrow(editorRow, Priority.ALWAYS);
        return panel;
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
        tokenTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tokenTable.setPrefHeight(180);
        tokenTable.setStyle("-fx-control-inner-background: #1e1e2e;"
            + " -fx-table-cell-border-color: #313244;"
            + " -fx-text-fill: #cdd6f4;"
            + " -fx-font-family: 'Consolas', monospace;"
            + " -fx-font-size: 11px;");

        TableColumn<TokenInfo, String> colTipo = new TableColumn<>("Tipo");
        colTipo.setCellValueFactory(new PropertyValueFactory<>("tipo"));
        colTipo.setPrefWidth(130);

        TableColumn<TokenInfo, String> colLexema = new TableColumn<>("Lexema");
        colLexema.setCellValueFactory(new PropertyValueFactory<>("lexema"));
        colLexema.setPrefWidth(130);

        TableColumn<TokenInfo, Number> colLinea = new TableColumn<>("Linea");
        colLinea.setCellValueFactory(new PropertyValueFactory<>("linea"));
        colLinea.setPrefWidth(55);
        colLinea.setStyle("-fx-alignment: CENTER-RIGHT;");

        tokenTable.getColumns().addAll(colTipo, colLexema, colLinea);

        tokensPane = new TitledPane("TOKENS", tokenTable);
        tokensPane.getStyleClass().add("titled-pane-custom");
        tokensPane.setCollapsible(false);

        // ─── Panel de la Tabla de Símbolos (5 columnas) ───
        tablaSimbolos = new TableView<>(simbolosData);
        tablaSimbolos.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
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
        TitledPane astPane = new TitledPane("AST (Arbol Sintactico)", astTree);
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

        List<String> erroresLex = lexer.getErroresLexicos();

        // ─── Fase 2: Análisis Sintáctico (con recuperación de errores) ───
        Parser parser = new Parser(tokens);
        Programa programa = parser.parsear();
        List<String> erroresSint = parser.getErroresSintacticos();

        ultimoPrograma = programa;
        ultimoParser = parser;

        // ─── Fase 3: Tabla de símbolos y detección de IDs no declarados ───
        construirTablaSimbolos(programa, tokens);
        List<String> erroresNoDecl = detectarNoDeclarados(programa, tokens);

        // ─── Fase 4: Sugerencias de palabras reservadas mal escritas ───
        List<String> sugerenciasReservadas = sugerirIDsMalEscritos(tokens, programa);

        // ─── Filtrar errores sintácticos que duplican sugerencias ───
        List<String> erroresSintFiltrados = new ArrayList<>();
        for (String err : erroresSint) {
            boolean duplicado = false;
            String errLower = err.toLowerCase();
            for (String id : idsConSugerencia) {
                if (errLower.contains("'" + id + "'")) {
                    duplicado = true;
                    break;
                }
            }
            if (!duplicado) {
                erroresSintFiltrados.add(err);
            }
        }

        // ─── Actualizar UI ───
        actualizarErrores(erroresLex, erroresSintFiltrados, erroresNoDecl, sugerenciasReservadas);
        actualizarTokens(tokens);
        actualizarAST(programa);
        actualizarDerivacion(programa, parser);
        actualizarStatus(tokens.size(), erroresLex.size(), erroresSintFiltrados.size(),
            erroresNoDecl.size() + sugerenciasReservadas.size());

        // Colorear el editor usando RichTextFX StyleSpans
        colorearEditor(tokens, codigo);
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
            items.add("[LEXICO] " + err);
            totalErrores++;
        }

        for (String err : sugerencias) {
            items.add("[LEXICO] " + err);
            totalErrores++;
        }

        for (String err : erroresSint) {
            items.add("[SINTACTICO] " + err);
            totalErrores++;
        }

        for (String err : erroresNoDecl) {
            items.add("[ERROR] " + err);
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

    // ─── Tokens ───
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

    // ─── AST ───
    private void actualizarAST(Programa programa) {
        if (programa == null) {
            astTree.setRoot(null);
            return;
        }

        TreeItem<String> raiz = new TreeItem<>("Programa");
        raiz.setExpanded(true);

        // Sensores
        TreeItem<String> sensores = new TreeItem<>("Sensores: "
            + (programa.sensores.isEmpty() ? "(ninguno)"
                : String.join(", ", programa.sensores)));
        sensores.setExpanded(true);
        raiz.getChildren().add(sensores);

        // Tipos de Sensores
        if (!programa.tiposSensores.isEmpty()) {
            TreeItem<String> tipos = new TreeItem<>("Tipos de Sensores");
            tipos.setExpanded(true);
            programa.tiposSensores.forEach((sensor, tipo) -> 
                tipos.getChildren().add(new TreeItem<>(sensor + " -> " + tipo.toUpperCase()))
            );
            raiz.getChildren().add(tipos);
        }

        // Rangos seguros
        if (!programa.rangos.isEmpty()) {
            TreeItem<String> rangos = new TreeItem<>("Rangos de Seguridad");
            rangos.setExpanded(true);
            programa.rangos.forEach((sensor, rango) -> 
                rangos.getChildren().add(new TreeItem<>(sensor + " -> Mín: " + rango.minimo + ", Máx: " + rango.maximo))
            );
            raiz.getChildren().add(rangos);
        }

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
                TreeItem<String> regla = new TreeItem<>("Regla " + (i + 1) + ": Si " + condStr);
                if (r.estado != null) {
                    regla.getChildren().add(new TreeItem<>("\u2192 Estado: " + r.estado));
                }
                if (r.alerta != null) {
                    regla.getChildren().add(new TreeItem<>("\u2192 Alerta: \"" + r.alerta + "\""));
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
            Label info = new Label("Analiza el c\u00F3digo para generar el \u00E1rbol de derivaci\u00F3n.");
            info.setStyle("-fx-text-fill: #a6adc8; -fx-font-family: 'Consolas', monospace;"
                + " -fx-font-size: 12px; -fx-padding: 8 0;");
            derivacionPanel.getChildren().add(info);
            return;
        }

        btnAbrirArbol.setDisable(false);

        // Vista previa del árbol en texto
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

    private void abrirArbolDerivacion() {
        if (ultimoParser == null || ultimoParser.arbolDerivacion == null) {
            mostrarError("No hay \u00E1rbol de derivaci\u00F3n disponible. Analiza el c\u00F3digo primero.");
            return;
        }
        PanelDerivacion panel = new PanelDerivacion(ultimoParser.arbolDerivacion);
        panel.mostrarEnVentana();
    }



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

    private void construirTablaSimbolos(Programa programa, List<Token> tokens) {
        simbolosData.clear();
        if (programa == null) return;

        Set<String> declaradas = new HashSet<>();

        // 1. Sensores declarados (mostrando el tipo específico si existe)
        for (String s : programa.sensores) {
            int linea = buscarLineaDeclaracion(s, tokens, TipoToken.SENSOR);
            String tipoStr = "SENSOR";
            if (programa.tiposSensores.containsKey(s)) {
                tipoStr = "SENSOR (" + programa.tiposSensores.get(s).toUpperCase() + ")";
            }
            simbolosData.add(new InfoSimbolo(s, tipoStr, linea, "\u2014", "\u2014"));
            declaradas.add(s);
        }

        // 2. Umbrales declarados
        for (Map.Entry<String, Double> e : programa.umbrales.entrySet()) {
            int linea = buscarLineaDeclaracion(e.getKey(), tokens, TipoToken.UMBRAL);
            String valorStr = String.valueOf(e.getValue());
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

    private boolean esEntero(double valor) {
        return valor == Math.floor(valor) && !Double.isInfinite(valor);
    }

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

    private int buscarLineaPrimerUso(String nombre, List<Token> tokens) {
        for (Token t : tokens) {
            if (t.tipo == TipoToken.ID && t.lexema.equals(nombre)) {
                return t.linea;
            }
        }
        return 0;
    }

    private Set<String> extraerVariablesReferenciadas(Programa programa) {
        Set<String> vars = new HashSet<>();
        for (Regla r : programa.reglas) {
            extraerVariablesDeExpresion(r.condicion, vars);
        }
        for (Calculo c : programa.calculos) {
            vars.add(c.sensor);
            for (Parametro p : c.parametros) {
                if ("con".equals(p.nombre)) {
                    vars.add(p.valor);
                }
            }
        }
        return vars;
    }

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
    }

    private List<String> detectarNoDeclarados(Programa programa, List<Token> tokens) {
        List<String> errores = new ArrayList<>();
        if (programa == null) return errores;

        Set<String> declaradas = new HashSet<>();
        declaradas.addAll(programa.sensores);
        declaradas.addAll(programa.umbrales.keySet());

        Set<String> referenciadas = extraerVariablesReferenciadas(programa);
        for (String ref : referenciadas) {
            if (!declaradas.contains(ref)) {
                // Marcar el token ID en la lista con error para subrayarlo
                for (Token t : tokens) {
                    if (t.tipo == TipoToken.ID && t.lexema.equals(ref)) {
                        t.tieneError = true;
                    }
                }

                StringBuilder sb = new StringBuilder();
                sb.append("El identificador '").append(ref).append("' no ha sido declarado.");

                // Sugerencia 1: comparar con sensores/umbrales declarados
                String sugerencia = buscarSugerencia(ref, declaradas.toArray(new String[0]));
                if (sugerencia == null) {
                    // Sugerencia 2: comparar con palabras reservadas
                    sugerencia = buscarSugerencia(ref, PALABRAS_RESERVADAS);
                }

                if (sugerencia != null) {
                    sb.append(" [SUGERENCIA: \u00BFQuisiste decir '").append(sugerencia).append("'?]");
                }
                errores.add(sb.toString());
            }
        }
        return errores;
    }

    private List<String> sugerirIDsMalEscritos(List<Token> tokens, Programa programa) {
        List<String> sugerencias = new ArrayList<>();
        idsConSugerencia.clear();
        Set<String> yaSugeridos = new HashSet<>();

        Set<String> declaradas = new HashSet<>();
        if (programa != null) {
            declaradas.addAll(programa.sensores);
            for (String k : programa.umbrales.keySet()) {
                declaradas.add(k);
            }
        }

        for (Token t : tokens) {
            if (t.tipo != TipoToken.ID) continue;
            String lexema = t.lexema;
            String idLower = lexema.toLowerCase();

            if (declaradas.contains(idLower)) continue;
            if (yaSugeridos.contains(idLower)) continue;
            boolean esReservadaExacta = false;
            for (String r : PALABRAS_RESERVADAS) {
                if (idLower.equals(r)) { esReservadaExacta = true; break; }
            }
            if (esReservadaExacta) continue;

            String sugerencia = buscarSugerencia(idLower, PALABRAS_RESERVADAS);
            if (sugerencia != null) {
                yaSugeridos.add(idLower);
                idsConSugerencia.add(idLower);
                t.tieneError = true; // Marcar token con error
                sugerencias.add("Error lexico en linea " + t.linea
                    + ": La palabra '" + lexema + "' no es una palabra reservada del lenguaje."
                    + " \u00BFQuisiste decir '" + sugerencia + "'?");
            }
        }

        return sugerencias;
    }

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

    // =============================================================
    //  COLOREADO DE EDITOR NATIVO CON RICHTEXTFX
    // =============================================================
    private void colorearEditor(List<Token> tokens, String fuente) {
        if (editor == null || fuente.isEmpty()) return;

        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        int pos = 0;

        for (Token t : tokens) {
            if (t.tipo == TipoToken.EOF) continue;

            int tokenStart = fuente.indexOf(t.lexema, pos);
            if (tokenStart < pos) {
                tokenStart = fuente.indexOf(t.lexema, 0);
            }

            if (tokenStart >= pos) {
                // Texto intermedio (espacios, saltos de línea)
                if (tokenStart > pos) {
                    spansBuilder.add(Collections.singleton("texto-normal"), tokenStart - pos);
                }

                // Determinar clase de estilo según token
                String styleClass = obtenerClaseEstiloToken(t);
                spansBuilder.add(Collections.singleton(styleClass), t.lexema.length());
                pos = tokenStart + t.lexema.length();
            } else {
                spansBuilder.add(Collections.singleton("texto-normal"), t.lexema.length());
            }
        }

        if (pos < fuente.length()) {
            spansBuilder.add(Collections.singleton("texto-normal"), fuente.length() - pos);
        }

        try {
            editor.setStyleSpans(0, spansBuilder.create());
        } catch (Exception e) {
            // Prevenir excepciones por modificaciones concurrentes rápidas
        }
    }

    private String obtenerClaseEstiloToken(Token t) {
        if (t.tieneError || t.tipo == TipoToken.DESCONOCIDO) {
            return "token-error";
        }
        
        TipoToken tipo = t.tipo;

        if (tipo == TipoToken.COMENTARIO) return "token-comentario";
        if (tipo == TipoToken.CADENA) return "token-cadena";

        if (tipo == TipoToken.SENSOR || tipo == TipoToken.UMBRAL || tipo == TipoToken.SI ||
            tipo == TipoToken.ENTONCES || tipo == TipoToken.ESTADO || tipo == TipoToken.CALCULAR ||
            tipo == TipoToken.FIN || tipo == TipoToken.TIPO || tipo == TipoToken.RANGO ||
            tipo == TipoToken.MINIMO || tipo == TipoToken.MAXIMO || tipo == TipoToken.ALERTA ||
            tipo == TipoToken.Y || tipo == TipoToken.O) {
            return "token-palabra-clave";
        }

        if (tipo == TipoToken.ELECTRICO || tipo == TipoToken.TERMICO) {
            return "token-tipo-sensor";
        }

        if (tipo == TipoToken.ABS || tipo == TipoToken.SENO || tipo == TipoToken.COSENO ||
            tipo == TipoToken.CUADRADA || tipo == TipoToken.PROMEDIO || tipo == TipoToken.MAXIMO ||
            tipo == TipoToken.SUMA || tipo == TipoToken.FLUCTUACION) {
            return "token-funcion";
        }

        if (tipo == TipoToken.ESTADO_SISTEMA) return "token-estado-sistema";

        if (tipo == TipoToken.AMPLITUD || tipo == TipoToken.FRECUENCIA || tipo == TipoToken.VENTANA ||
            tipo == TipoToken.CON) {
            return "token-parametro";
        }

        if (tipo == TipoToken.NUMERO) return "token-numero";

        if (tipo == TipoToken.MAYOR || tipo == TipoToken.MENOR || tipo == TipoToken.IGUAL_IGUAL ||
            tipo == TipoToken.MAYOR_IGUAL || tipo == TipoToken.MENOR_IGUAL || tipo == TipoToken.DIFERENTE) {
            return "token-operador-relacional";
        }

        if (tipo == TipoToken.MAS || tipo == TipoToken.MENOS || tipo == TipoToken.POR || tipo == TipoToken.DIV) {
            return "token-operador-aritmetico";
        }

        if (tipo == TipoToken.ID) return "token-identificador";

        return "texto-normal";
    }

    // =============================================================
    //  ACCIONES
    // =============================================================

    private void nuevoArchivo() {
        editor.clear();
        archivoActual = null;
        analizarCodigo();
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
                editor.clear();
                editor.appendText(br.lines().collect(Collectors.joining("\n")));
                archivoActual = file.getAbsolutePath();
                analizarCodigo();
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
        editor.clear();
        editor.appendText(
            "// Monitoreo de subestacion electrica\n"
            + "sensor voltaje tipo electrico;\n"
            + "sensor temperatura tipo termico;\n"
            + "\n"
            + "rango voltaje minimo = 110 maximo = 127;\n"
            + "rango temperatura minimo = -10 maximo = 80;\n"
            + "\n"
            + "umbral maxVolt = 127;\n"
            + "umbral minVolt = 110;\n"
            + "\n"
            + "si voltaje >= maxVolt o temperatura > 80 entonces {\n"
            + "    estado = PICO;\n"
            + "    alerta = \"Voltaje o temperatura elevada\";\n"
            + "}\n"
            + "si voltaje <= minVolt entonces estado = CAIDA;\n"
            + "\n"
            + "calcular promedio(voltaje, ventana = 10);\n"
            + "calcular fluctuacion(temperatura);\n"
        );
    }

    private void mostrarError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}
