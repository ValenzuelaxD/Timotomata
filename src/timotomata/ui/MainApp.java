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

        // Cargar CSS desde el classpath (funciona desde cualquier directorio)
        try {
            var cssUrl = getClass().getResource("estilos.css");
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
            } else {
                // Fallback: buscar en sistema de archivos
                File cssFile = new File("src/timotomata/ui/estilos.css");
                if (cssFile.exists()) {
                    scene.getStylesheets().add(cssFile.toURI().toURL().toExternalForm());
                } else {
                    System.err.println("Advertencia: No se encontro estilos.css");
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
