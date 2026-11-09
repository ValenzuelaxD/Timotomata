package timotomata.ui;

import javafx.scene.control.Dialog;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Carga y aplica el icono del producto a la ventana principal y a los
 * diálogos. Los PNG se cargan una sola vez y se reutilizan: un diálogo nuevo
 * no debe volver a leer el disco ni mostrar el icono genérico de JavaFX.
 */
final class Iconos {

    private static final String[] NOMBRES = {
        "logo-512.png", "logo-128.png", "logo-48.png", "logo-32.png"
    };

    private static List<Image> cache;

    private Iconos() {
    }

    static void aplicar(Stage stage) {
        if (stage == null) return;
        List<Image> iconos = imagenes();
        if (!iconos.isEmpty()) {
            stage.getIcons().setAll(iconos);
        }
    }

    /**
     * Aplica el icono al diálogo cuando su ventana ya existe. Se llama desde
     * el evento {@code onShown}, no antes: hasta que el diálogo se muestra no
     * hay un {@link Stage} al que asignarle la lista de iconos.
     */
    static void aplicar(Dialog<?> dialogo) {
        if (dialogo == null || dialogo.getDialogPane().getScene() == null) return;
        if (dialogo.getDialogPane().getScene().getWindow() instanceof Stage stage) {
            aplicar(stage);
        }
    }

    private static List<Image> imagenes() {
        if (cache != null) return cache;
        List<Image> lista = new ArrayList<>();
        for (String nombre : NOMBRES) {
            Image icono = cargar(nombre);
            if (icono != null && !icono.isError()) {
                lista.add(icono);
            }
        }
        cache = lista;
        return lista;
    }

    private static Image cargar(String nombre) {
        var url = Iconos.class.getResource(nombre);
        if (url != null) {
            return new Image(url.toExternalForm());
        }
        // Fallback para ejecuciones desde VS Code, donde los recursos del
        // paquete no siempre se copian al directorio de clases.
        File archivo = new File("src/timotomata/ui/" + nombre);
        return archivo.exists() ? new Image(archivo.toURI().toString()) : null;
    }
}
