package timotomata.ui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.io.File;

/**
 * Punto de entrada de la interfaz gráfica del compilador Timotomata.
 * Inicia la ventana principal con el editor de código y las herramientas
 * de análisis en tiempo real.
 */
public class MainApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        AppController controller = new AppController();

        Scene scene = new Scene(controller.getRoot(), 1200, 750);

        // Cargar CSS desde el sistema de archivos
        try {
            File cssFile = new File("src/timotomata/ui/estilos.css");
            if (cssFile.exists()) {
                scene.getStylesheets().add(cssFile.toURI().toURL().toExternalForm());
            } else {
                // Fallback: buscar en el directorio de trabajo actual
                cssFile = new File("out/timotomata/ui/estilos.css");
                if (cssFile.exists()) {
                    scene.getStylesheets().add(cssFile.toURI().toURL().toExternalForm());
                }
            }
        } catch (Exception e) {
            System.err.println("Advertencia: No se pudo cargar estilos.css");
        }

        primaryStage.setTitle("Compilador Timotomata — Análisis Léxico y Sintáctico");
        primaryStage.setScene(scene);
        primaryStage.show();

        // Ejecutar análisis inicial con el código de ejemplo
        controller.analizarCodigo();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
