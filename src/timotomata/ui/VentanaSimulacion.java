package timotomata.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import timotomata.parser.ast.Programa;
import java.util.List;

public class VentanaSimulacion {
    private final Stage stage;
    private final Programa programa;
    private final ComboBox<String> comboSensores;
    private final LineChart<Number, Number> chart;
    private final TableView<SimuladorSensores.Muestra> tabla;
    private final ObservableList<SimuladorSensores.Muestra> datosTabla = FXCollections.observableArrayList();

    public VentanaSimulacion(Stage parentStage, Programa programa) {
        this.programa = programa;
        this.stage = new Stage();
        this.stage.initOwner(parentStage);
        this.stage.initModality(Modality.WINDOW_MODAL);
        this.stage.setTitle("Simulación de Sensores y Evaluación de Reglas");

        // 1. Panel Superior: Selección de Sensor
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(12));
        header.setStyle("-fx-background-color: #181825; -fx-border-color: transparent transparent #313244 transparent; -fx-border-width: 1;");

        Label lblSensor = new Label("Sensor a simular:");
        lblSensor.setStyle("-fx-text-fill: #cdd6f4; -fx-font-weight: bold; -fx-font-size: 13px;");

        comboSensores = new ComboBox<>();
        comboSensores.setStyle("-fx-background-color: #313244; -fx-text-fill: #cdd6f4;");
        if (programa.sensores.isEmpty()) {
            comboSensores.getItems().add("(Ninguno declarado)");
            comboSensores.getSelectionModel().select(0);
        } else {
            comboSensores.getItems().addAll(programa.sensores);
            comboSensores.getSelectionModel().select(0);
        }

        Button btnSimular = new Button("Regenerar Simulación");
        btnSimular.setStyle("-fx-background-color: #a6e3a1; -fx-text-fill: #11111b; -fx-font-weight: bold; -fx-background-radius: 6; -fx-padding: 6 16;");
        btnSimular.setOnAction(e -> ejecutarSimulacion());

        header.getChildren().addAll(lblSensor, comboSensores, btnSimular);

        // 2. Gráfico de Líneas (LineChart)
        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("Tiempo (Muestras)");
        xAxis.setAutoRanging(false);
        xAxis.setLowerBound(1);
        xAxis.setUpperBound(50);
        xAxis.setTickUnit(5);

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Valor Medido");
        yAxis.setForceZeroInRange(false);

        chart = new LineChart<>(xAxis, yAxis);
        chart.setTitle("Variación en el Tiempo");
        chart.setCreateSymbols(true);
        chart.setLegendVisible(true);
        chart.setStyle("-fx-background-color: #1e1e2e;");

        // Estilos del gráfico en CSS
        xAxis.setStyle("-fx-tick-label-fill: #a6adc8; -fx-axis-label-fill: #cdd6f4;");
        yAxis.setStyle("-fx-tick-label-fill: #a6adc8; -fx-axis-label-fill: #cdd6f4;");
        chart.lookup(".chart-plot-background").setStyle("-fx-background-color: #11111b;");
        chart.lookup(".chart-title").setStyle("-fx-text-fill: #89b4fa; -fx-font-weight: bold; -fx-font-size: 14px;");

        // 3. Tabla de Resultados (TableView)
        tabla = new TableView<>(datosTabla);
        tabla.setStyle("-fx-control-inner-background: #1e1e2e;"
            + " -fx-table-cell-border-color: #313244;"
            + " -fx-text-fill: #cdd6f4;"
            + " -fx-font-family: 'Consolas', monospace;"
            + " -fx-font-size: 11px;");

        TableColumn<SimuladorSensores.Muestra, Number> colTiempo = new TableColumn<>("Tiempo");
        colTiempo.setCellValueFactory(new PropertyValueFactory<>("tiempo"));
        colTiempo.setPrefWidth(60);
        colTiempo.setStyle("-fx-alignment: CENTER;");

        TableColumn<SimuladorSensores.Muestra, Number> colValor = new TableColumn<>("Valor");
        colValor.setCellValueFactory(new PropertyValueFactory<>("valor"));
        colValor.setPrefWidth(120);
        colValor.setStyle("-fx-alignment: CENTER-RIGHT;");

        TableColumn<SimuladorSensores.Muestra, String> colEstado = new TableColumn<>("Estado");
        colEstado.setCellValueFactory(new PropertyValueFactory<>("estado"));
        colEstado.setPrefWidth(120);
        colEstado.setStyle("-fx-alignment: CENTER; -fx-font-weight: bold;");

        // Colorear dinámicamente la celda de estado
        colEstado.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    switch (item.toUpperCase()) {
                        case "PICO":
                            setStyle("-fx-text-fill: #f38ba8;"); // Rojo
                            break;
                        case "CAIDA":
                            setStyle("-fx-text-fill: #fab387;"); // Naranja
                            break;
                        case "INESTABLE":
                            setStyle("-fx-text-fill: #f9e2af;"); // Amarillo
                            break;
                        case "NORMAL":
                        default:
                            setStyle("-fx-text-fill: #a6e3a1;"); // Verde
                            break;
                    }
                }
            }
        });

        TableColumn<SimuladorSensores.Muestra, String> colAlerta = new TableColumn<>("Alerta / Mensaje");
        colAlerta.setCellValueFactory(new PropertyValueFactory<>("alerta"));
        colAlerta.setPrefWidth(300);

        tabla.getColumns().addAll(colTiempo, colValor, colEstado, colAlerta);

        // 4. Panel central con SplitPane
        SplitPane split = new SplitPane();
        split.setDividerPositions(0.6);
        split.getItems().addAll(chart, tabla);
        split.setStyle("-fx-background-color: #11111b;");

        VBox layout = new VBox(split);
        VBox.setVgrow(split, Priority.ALWAYS);

        BorderPane mainPane = new BorderPane();
        mainPane.setTop(header);
        mainPane.setCenter(layout);
        mainPane.setStyle("-fx-background-color: #11111b;");

        Scene scene = new Scene(mainPane, 900, 600);
        
        // Copiar los estilos generales
        try {
            var cssUrl = getClass().getResource("estilos.css");
            if (cssUrl != null) scene.getStylesheets().add(cssUrl.toExternalForm());
        } catch (Exception ignored) {}

        stage.setScene(scene);

        // Si hay sensores, ejecutar simulación inicial
        if (!programa.sensores.isEmpty()) {
            ejecutarSimulacion();
        }
    }

    public void mostrar() {
        stage.show();
    }

    private void ejecutarSimulacion() {
        String sensor = comboSensores.getValue();
        if (sensor == null || sensor.equals("(Ninguno declarado)")) return;

        // Limpiar
        chart.getData().clear();
        datosTabla.clear();

        // 1. Generar Muestras
        List<SimuladorSensores.Muestra> muestras = SimuladorSensores.generarSimulacion(programa, sensor, 50);
        datosTabla.addAll(muestras);

        // 2. Crear Series para el Gráfico
        XYChart.Series<Number, Number> seriesMedido = new XYChart.Series<>();
        seriesMedido.setName("Valor Medido: " + sensor);

        for (SimuladorSensores.Muestra m : muestras) {
            XYChart.Data<Number, Number> data = new XYChart.Data<>(m.tiempo, m.valor);
            seriesMedido.getData().add(data);
        }

        // Agregar la serie al gráfico
        chart.getData().add(seriesMedido);

        // Aplicar estilos a la serie del sensor (Color Azul)
        seriesMedido.getNode().setStyle("-fx-stroke: #89b4fa; -fx-stroke-width: 2px;");

        // Colorear los puntos de datos según su estado
        for (XYChart.Data<Number, Number> data : seriesMedido.getData()) {
            int idx = data.getXValue().intValue() - 1;
            if (idx >= 0 && idx < muestras.size()) {
                SimuladorSensores.Muestra m = muestras.get(idx);
                if (data.getNode() != null) {
                    String color = switch (m.estado.toUpperCase()) {
                        case "PICO" -> "#f38ba8";
                        case "CAIDA" -> "#fab387";
                        case "INESTABLE" -> "#f9e2af";
                        default -> "#a6e3a1";
                    };
                    data.getNode().setStyle("-fx-background-color: " + color + ", white; -fx-background-radius: 5px;");
                    
                    // Tooltip con información en cada punto
                    Tooltip t = new Tooltip(String.format("Tiempo: %d\nValor: %.2f\nEstado: %s%s",
                        m.tiempo, m.valor, m.estado, m.alerta != null ? "\nAlerta: " + m.alerta : ""));
                    t.setStyle("-fx-background-color: #313244; -fx-text-fill: #cdd6f4; -fx-font-size: 11px;");
                    Tooltip.install(data.getNode(), t);
                }
            }
        }

        // 3. Agregar líneas de umbral si existen en los rangos seguros
        if (programa.rangos.containsKey(sensor)) {
            Programa.RangoSeguro r = programa.rangos.get(sensor);

            XYChart.Series<Number, Number> seriesMax = new XYChart.Series<>();
            seriesMax.setName("Límite Máx Seguro (" + r.maximo + ")");
            seriesMax.getData().add(new XYChart.Data<>(1, r.maximo));
            seriesMax.getData().add(new XYChart.Data<>(50, r.maximo));

            XYChart.Series<Number, Number> seriesMin = new XYChart.Series<>();
            seriesMin.setName("Límite Mín Seguro (" + r.minimo + ")");
            seriesMin.getData().add(new XYChart.Data<>(1, r.minimo));
            seriesMin.getData().add(new XYChart.Data<>(50, r.minimo));

            chart.getData().addAll(seriesMax, seriesMin);

            // Estilizar líneas de límite en Rojo
            seriesMax.getNode().setStyle("-fx-stroke: #f38ba8; -fx-stroke-dash-array: 6 6; -fx-stroke-width: 1.5px;");
            seriesMin.getNode().setStyle("-fx-stroke: #f38ba8; -fx-stroke-dash-array: 6 6; -fx-stroke-width: 1.5px;");

            // Ocultar símbolos para las líneas de referencia
            for (XYChart.Data<Number, Number> d : seriesMax.getData()) {
                if (d.getNode() != null) d.getNode().setVisible(false);
            }
            for (XYChart.Data<Number, Number> d : seriesMin.getData()) {
                if (d.getNode() != null) d.getNode().setVisible(false);
            }
        }
    }
}
