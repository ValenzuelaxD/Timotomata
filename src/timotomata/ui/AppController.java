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
import javafx.stage.FileChooser;
import javafx.util.Duration;

import timotomata.lexer.*;
import timotomata.parser.*;
import timotomata.parser.ast.*;

/**
 * Controlador principal de la interfaz gráfica.
 * Gestiona el editor de código, el análisis en tiempo real,
 * la visualización de tokens/AST y la simulación.
 */
public class AppController {

    // ─── Modelo de datos para la tabla de tokens ───
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

    // ─── Componentes de la UI ───
    private BorderPane root;
    private TextArea editor;
    private Label statusLabel;
    private ListView<String> errorList;
    private TitledPane erroresPane;
    private TableView<TokenInfo> tokenTable;
    private ObservableList<TokenInfo> tokenData = FXCollections.observableArrayList();
    private TitledPane tokensPane;
    private TreeView<String> astTree;
    private VBox simPanel;
    private Label resultadoLabel;
    private PauseTransition debounce;

    // ─── Estado interno ───
    private Programa ultimoPrograma;
    private String archivoActual = null;

    // =============================================================
    //  CONSTRUCTOR
    // =============================================================
    public AppController() {
        crearUI();
        configurarDebounce();
        cargarEjemploPorDefecto();
    }

    public BorderPane getRoot() { return root; }

    // =============================================================
    //  CONSTRUCCIÓN DE LA INTERFAZ
    // =============================================================
    private void crearUI() {
        root = new BorderPane();
        root.getStyleClass().add("root");

        // ─── Menú superior ───
        root.setTop(crearMenuBar());

        // ─── Panel central ───
        SplitPane split = new SplitPane();
        split.setDividerPositions(0.62);
        split.getItems().addAll(crearPanelEditor(), crearPanelInfo());
        SplitPane.setResizableWithParent(split.getItems().get(0), true);
        root.setCenter(split);

        // ─── Panel inferior: simulación ───
        root.setBottom(crearPanelSimulacion());
    }

    // ─── Barra de menú ───
    private MenuBar crearMenuBar() {
        MenuBar menuBar = new MenuBar();
        menuBar.getStyleClass().add("menu-bar");

        // Archivo
        Menu menuArchivo = new Menu("Archivo");
        MenuItem itemNuevo = new MenuItem("Nuevo");
        itemNuevo.setOnAction(e -> nuevoArchivo());
        MenuItem itemAbrir = new MenuItem("Abrir...");
        itemAbrir.setOnAction(e -> abrirArchivo());
        MenuItem itemGuardar = new MenuItem("Guardar");
        itemGuardar.setOnAction(e -> guardarArchivo());
        MenuItem itemGuardarComo = new MenuItem("Guardar como...");
        itemGuardarComo.setOnAction(e -> guardarArchivoComo());
        menuArchivo.getItems().addAll(itemNuevo, itemAbrir,
            new SeparatorMenuItem(), itemGuardar, itemGuardarComo);

        // Ejecutar
        Menu menuEjecutar = new Menu("Ejecutar");
        MenuItem itemAnalizar = new MenuItem("Analizar (F5)");
        itemAnalizar.setOnAction(e -> analizarCodigo());
        MenuItem itemSimular = new MenuItem("Simular (F6)");
        itemSimular.setOnAction(e -> ejecutarSimulacion());
        menuEjecutar.getItems().addAll(itemAnalizar, itemSimular);

        // Ayuda
        Menu menuAyuda = new Menu("Ayuda");
        MenuItem itemAcerca = new MenuItem("Acerca de");
        itemAcerca.setOnAction(e -> mostrarAcerca());
        menuAyuda.getItems().add(itemAcerca);

        menuBar.getMenus().addAll(menuArchivo, menuEjecutar, menuAyuda);
        return menuBar;
    }

    // ─── Panel del editor ───
    private VBox crearPanelEditor() {
        VBox panel = new VBox(0);
        panel.setStyle("-fx-background-color: #1e1e2e;");

        Label lblEditor = new Label("  EDITOR DE CÓDIGO");
        lblEditor.getStyleClass().add("panel-titulo");
        lblEditor.setStyle("-fx-text-fill: #cdd6f4; -fx-background-color: #181825;"
            + " -fx-padding: 6 12; -fx-font-weight: bold; -fx-font-size: 12;");

        editor = new TextArea();
        editor.setStyle("-fx-control-inner-background: #1e1e2e;"
            + " -fx-text-fill: #cdd6f4;"
            + " -fx-highlight-fill: #45475a;"
            + " -fx-highlight-text-fill: #cdd6f4;"
            + " -fx-font-family: 'Consolas', 'Courier New', monospace;"
            + " -fx-font-size: 13px;"
            + " -fx-line-spacing: 2px;"
            + " -fx-border-color: transparent;"
            + " -fx-padding: 8;");
        editor.setPromptText("Escribe tu código Timotomata aquí...");

        // Atajos de teclado
        editor.setOnKeyReleased(e -> {
            if (e.getCode() == KeyCode.F5) {
                analizarCodigo();
            } else if (e.getCode() == KeyCode.F6) {
                ejecutarSimulacion();
            } else {
                debounce.playFromStart();
            }
        });

        statusLabel = new Label("  Ln 1, Col 1  |  SIN ANALIZAR");
        statusLabel.setStyle("-fx-text-fill: #a6adc8; -fx-background-color: #181825;"
            + " -fx-padding: 4 12; -fx-font-family: 'Consolas', monospace; -fx-font-size: 11;");

        // Actualizar posición del cursor
        editor.caretPositionProperty().addListener((obs, oldPos, newPos) -> {
            actualizarStatusCursor();
        });

        panel.getChildren().addAll(lblEditor, editor, statusLabel);
        VBox.setVgrow(editor, Priority.ALWAYS);
        return panel;
    }

    // ─── Panel derecho con pestañas −──
    private VBox crearPanelInfo() {
        VBox panel = new VBox(6);
        panel.setStyle("-fx-background-color: #11111b; -fx-padding: 6;");

        // Panel de Errores
        errorList = new ListView<>();
        errorList.setPrefHeight(160);
        errorList.setStyle("-fx-control-inner-background: #1e1e2e;"
            + " -fx-text-fill: #cdd6f4;"
            + " -fx-font-family: 'Consolas', monospace;"
            + " -fx-font-size: 11px;");
        erroresPane = new TitledPane("❌ ERRORES", errorList);
        erroresPane.getStyleClass().add("titled-pane-custom");
        erroresPane.setCollapsible(false);

        // Panel de Tokens
        tokenTable = new TableView<>(tokenData);
        tokenTable.setPrefHeight(200);
        tokenTable.setStyle("-fx-control-inner-background: #1e1e2e;"
            + " -fx-table-cell-border-color: #313244;"
            + " -fx-text-fill: #cdd6f4;"
            + " -fx-font-family: 'Consolas', monospace;"
            + " -fx-font-size: 11px;");

        TableColumn<TokenInfo, String> colTipo = new TableColumn<>("Tipo");
        colTipo.setCellValueFactory(new PropertyValueFactory<>("tipo"));
        colTipo.setPrefWidth(100);

        TableColumn<TokenInfo, String> colLexema = new TableColumn<>("Lexema");
        colLexema.setCellValueFactory(new PropertyValueFactory<>("lexema"));
        colLexema.setPrefWidth(130);

        TableColumn<TokenInfo, Number> colLinea = new TableColumn<>("Ln");
        colLinea.setCellValueFactory(new PropertyValueFactory<>("linea"));
        colLinea.setPrefWidth(45);

        tokenTable.getColumns().addAll(colTipo, colLexema, colLinea);

        tokensPane = new TitledPane("◆ TOKENS", tokenTable);
        tokensPane.getStyleClass().add("titled-pane-custom");
        tokensPane.setCollapsible(false);

        // Panel del AST
        astTree = new TreeView<>();
        astTree.setPrefHeight(200);
        astTree.setStyle("-fx-control-inner-background: #1e1e2e;"
            + " -fx-text-fill: #cdd6f4;"
            + " -fx-font-family: 'Consolas', monospace;"
            + " -fx-font-size: 11px;"
            + " -fx-accent: #45475a;");
        astTree.setShowRoot(false);
        TitledPane astPane = new TitledPane("🌳 AST (Árbol Sintáctico)", astTree);
        astPane.getStyleClass().add("titled-pane-custom");
        astPane.setCollapsible(false);

        panel.getChildren().addAll(erroresPane, tokensPane, astPane);
        VBox.setVgrow(astPane, Priority.ALWAYS);
        return panel;
    }

    // ─── Panel de simulación ───
    private VBox crearPanelSimulacion() {
        simPanel = new VBox(6);
        simPanel.setStyle("-fx-background-color: #181825; -fx-padding: 8 12;");

        Label lblSim = new Label("SIMULACIÓN");
        lblSim.setStyle("-fx-text-fill: #a6e3a1; -fx-font-weight: bold; -fx-font-size: 12;");

        resultadoLabel = new Label("Analiza el código para habilitar la simulación.");
        resultadoLabel.setStyle("-fx-text-fill: #a6adc8; -fx-font-family: 'Consolas', monospace;"
            + " -fx-font-size: 12px; -fx-padding: 4 0;");

        simPanel.getChildren().addAll(lblSim, resultadoLabel);
        return simPanel;
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

        // ─── Fase 2: Análisis Sintáctico ───
        Parser parser = new Parser(tokens);
        Programa programa = null;
        String errorSintactico = null;

        try {
            programa = parser.parsear();
        } catch (RuntimeException e) {
            errorSintactico = e.getMessage();
        }

        ultimoPrograma = programa;

        // ─── Actualizar UI ───
        actualizarErrores(erroresLex, errorSintactico);
        actualizarTokens(tokens);
        actualizarAST(programa);
        actualizarSimulacion(programa);
        actualizarStatus(tokens.size(), erroresLex.size(), errorSintactico);
    }

    // =============================================================
    //  ACTUALIZACIÓN DE LA UI
    // =============================================================

    // ─── Errores ───
    private void actualizarErrores(List<String> erroresLex, String errorSint) {
        ObservableList<String> items = errorList.getItems();
        items.clear();

        int totalErrores = 0;

        // Errores léxicos
        for (String err : erroresLex) {
            items.add("⚠ [LÉXICO] " + err);
            totalErrores++;
        }

        // Error sintáctico
        if (errorSint != null) {
            items.add("✖ [SINTÁCTICO] " + errorSint);
            totalErrores++;
        }

        // Si no hay errores
        if (totalErrores == 0) {
            items.add("✅ Sin errores.");
        }

        // Color del título del panel según haya o no errores
        if (totalErrores > 0) {
            erroresPane.setText("❌ ERRORES (" + totalErrores + ")");
            erroresPane.setStyle("-fx-text-fill: #f38ba8;");
        } else {
            erroresPane.setText("✅ ERRORES (0)");
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
        // Actualizar título del panel
        tokensPane.setText("◆ TOKENS (" + tokenData.size() + ")");
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
                regla.getChildren().add(new TreeItem<>("→ Estado: " + r.estado));
                reglas.getChildren().add(regla);
            }
            raiz.getChildren().add(reglas);
        }

        astTree.setRoot(raiz);
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

    // ─── Panel de simulación ───
    private void actualizarSimulacion(Programa programa) {
        simPanel.getChildren().clear();
        Label simLabel = new Label("SIMULACIÓN");
        simLabel.setStyle("-fx-text-fill: #a6e3a1; -fx-font-weight: bold; -fx-font-size: 12;");
        simPanel.getChildren().add(simLabel);

        if (programa == null || programa.sensores.isEmpty()) {
            resultadoLabel = new Label("Define al menos un sensor para simular.");
            resultadoLabel.setStyle("-fx-text-fill: #a6adc8; -fx-font-family: 'Consolas', monospace;"
                + " -fx-font-size: 12px;");
            simPanel.getChildren().add(resultadoLabel);
            return;
        }

        // Crear campos para cada sensor
        HBox inputs = new HBox(8);
        inputs.setPadding(new Insets(4, 0, 4, 0));
        Map<String, TextField> campos = new HashMap<>();

        for (String sensor : programa.sensores) {
            VBox campo = new VBox(2);
            Label lbl = new Label(sensor);
            lbl.setStyle("-fx-text-fill: #89b4fa; -fx-font-family: 'Consolas', monospace;"
                + " -fx-font-size: 11px;");
            TextField tf = new TextField();
            tf.setPromptText("valor");
            tf.setPrefWidth(80);
            tf.setStyle("-fx-background-color: #313244; -fx-text-fill: #cdd6f4;"
                + " -fx-font-family: 'Consolas', monospace; -fx-font-size: 12px;"
                + " -fx-border-color: #45475a; -fx-border-radius: 4;"
                + " -fx-padding: 4 6;");
            campo.getChildren().addAll(lbl, tf);
            inputs.getChildren().add(campo);
            campos.put(sensor, tf);
        }

        // Botón Simular
        Button btnSimular = new Button("▶ Simular");
        btnSimular.setStyle("-fx-background-color: #a6e3a1; -fx-text-fill: #11111b;"
            + " -fx-font-weight: bold; -fx-font-size: 12px;"
            + " -fx-background-radius: 6; -fx-padding: 6 16;"
            + " -fx-cursor: hand;");
        btnSimular.setOnAction(e -> {
            Map<String, Double> valores = new HashMap<>();
            StringBuilder errores = new StringBuilder();
            for (Map.Entry<String, TextField> entry : campos.entrySet()) {
                String texto = entry.getValue().getText().trim();
                if (texto.isEmpty()) {
                    errores.append("⚠ Ingresa un valor para '").append(entry.getKey()).append("'\n");
                } else {
                    try {
                        valores.put(entry.getKey(), Double.parseDouble(texto));
                    } catch (NumberFormatException ex) {
                        errores.append("⚠ '").append(entry.getKey())
                            .append("' debe ser un número válido\n");
                    }
                }
            }

            if (errores.length() > 0) {
                resultadoLabel.setText(errores.toString().trim());
                resultadoLabel.setStyle("-fx-text-fill: #f38ba8; -fx-font-family: 'Consolas', monospace;"
                    + " -fx-font-size: 12px;");
                return;
            }

            // Agregar umbrales a la memoria
            programa.umbrales.forEach(valores::putIfAbsent);

            // Evaluar reglas
            StringBuilder res = new StringBuilder();
            res.append("📊 RESULTADOS:\n");
            for (Regla r : programa.reglas) {
                boolean cumple = evaluarCondicion(r.condicion, valores);
                String icono = cumple ? "🔥" : "  ";
                res.append(icono).append(" Si ").append(expresionToString(r.condicion))
                    .append(cumple ? " → " : " → (no)").append("\n");
            }

            // Último estado que se cumple
            String ultimoEstado = "NORMAL";
            for (Regla r : programa.reglas) {
                if (evaluarCondicion(r.condicion, valores)) {
                    ultimoEstado = r.estado;
                }
            }
            res.append("\n▶ Estado resultante: ").append(ultimoEstado.toUpperCase());

            resultadoLabel.setText(res.toString());
            resultadoLabel.setStyle("-fx-text-fill: #a6e3a1; -fx-font-family: 'Consolas', monospace;"
                + " -fx-font-size: 12px; -fx-line-spacing: 4px;");
        });

        HBox botonBox = new HBox(btnSimular);
        botonBox.setAlignment(Pos.CENTER_LEFT);

        resultadoLabel = new Label("Ingresa valores y presiona Simular.");
        resultadoLabel.setStyle("-fx-text-fill: #a6adc8; -fx-font-family: 'Consolas', monospace;"
            + " -fx-font-size: 12px;");

        simPanel.getChildren().addAll(inputs, botonBox, resultadoLabel);
    }

    // ─── Evaluar condición (expresión relacional) ───
    private boolean evaluarCondicion(Expresion cond, Map<String, Double> memoria) {
        if (cond instanceof Binaria b) {
            String op = b.operador;
            double izq = evaluarNumero(b.izquierda, memoria);
            double der = evaluarNumero(b.derecha, memoria);
            return switch (op) {
                case ">"  -> izq > der;
                case "<"  -> izq < der;
                case "==" -> Math.abs(izq - der) < 1e-9;
                case ">=" -> izq >= der;
                case "<=" -> izq <= der;
                case "!=" -> Math.abs(izq - der) >= 1e-9;
                default   -> false;
            };
        }
        return false;
    }

    // ─── Evaluar expresión numérica ───
    private double evaluarNumero(Expresion e, Map<String, Double> memoria) {
        if (e instanceof Numero n) return n.valor;
        if (e instanceof Variable v) {
            Double val = memoria.get(v.nombre);
            if (val == null) return 0.0;
            return val;
        }
        if (e instanceof Binaria b) {
            double izq = evaluarNumero(b.izquierda, memoria);
            double der = evaluarNumero(b.derecha, memoria);
            return switch (b.operador) {
                case "+" -> izq + der;
                case "-" -> izq - der;
                case "*" -> izq * der;
                case "/" -> der != 0 ? izq / der : 0;
                default  -> 0;
            };
        }
        if (e instanceof Negacion n) return -evaluarNumero(n.expresion, memoria);
        if (e instanceof Abs a) return Math.abs(evaluarNumero(a.expresion, memoria));
        return 0.0;
    }

    // ─── Barra de estado ───
    private void actualizarStatus(int numTokens, int numErroresLex, String errorSint) {
        int totalErrores = numErroresLex + (errorSint != null ? 1 : 0);
        String color = totalErrores > 0 ? "#f38ba8" : "#a6e3a1";
        statusLabel.setStyle("-fx-text-fill: " + color
            + "; -fx-background-color: #181825;"
            + " -fx-padding: 4 12; -fx-font-family: 'Consolas', monospace; -fx-font-size: 11;");
        statusLabel.setText("  Tokens: " + numTokens
            + "  |  Errores: " + totalErrores
            + "  |  " + (errorSint != null ? "✖ CON ERRORES" : "✅ OK"));
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
        // No sobreescribir el estado de análisis
        String current = statusLabel.getText();
        if (current.contains("|  ")) {
            String analisis = current.substring(current.lastIndexOf("|"));
            statusLabel.setText("  Ln " + linea + ", Col " + columna + "  " + analisis);
        }
    }

    // =============================================================
    //  ACCIONES DE MENÚ
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
                editor.setText(br.lines().collect(Collectors.joining("\n")));
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

    private void mostrarAcerca() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Acerca de Timotomata");
        alert.setHeaderText("Compilador Timotomata v1.0");
        alert.setContentText("Lenguaje de programación para simulación de sensores.\n\n"
            + "Características:\n"
            + "• Análisis léxico basado en AFD\n"
            + "• Análisis sintáctico con gramática LL(1)\n"
            + "• Validación en tiempo real\n"
            + "• Simulación interactiva de sensores");
        alert.showAndWait();
    }

    private void cargarEjemploPorDefecto() {
        editor.setText(
            "sensor voltaje;\n"
            + "sensor temperatura;\n"
            + "umbral maximo = 220;\n"
            + "umbral minimo = 100;\n"
            + "\n"
            + "si voltaje >= maximo entonces estado = PICO;\n"
            + "si voltaje <= minimo entonces estado = CAIDA;\n"
            + "si temperatura > 80 entonces estado = INESTABLE;\n"
            + "\n"
            + "FIN\n"
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

    // =============================================================
    //  SIMULACIÓN (invocada desde menú)
    // =============================================================
    private void ejecutarSimulacion() {
        // El panel de simulación se actualiza automáticamente con cada análisis
        analizarCodigo();
        // Hacer scroll al panel de simulación (no es posible directamente,
        // pero podemos mostrar un mensaje)
        resultadoLabel.setText("✅ Simulación lista. Ingresa valores arriba y presiona '▶ Simular'.");
        resultadoLabel.setStyle("-fx-text-fill: #a6e3a1; -fx-font-family: 'Consolas', monospace;"
            + " -fx-font-size: 12px;");
    }
}
