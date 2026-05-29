package timotomata.ui;

import javafx.application.Platform;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

/**
 * Columna de números de línea que se sincroniza con un TextArea.
 * Se coloca a la izquierda del editor dentro de un HBox.
 * 
 * Canvas redimensionable: override isResizable() = true y prefWidth()
 * devuelve un ancho calculado, no getWidth().
 */
public class LineNumberColumn extends Canvas {

    private static final Color FONDO = Color.web("#181825");
    private static final Color TEXTO_NORMAL = Color.web("#585b70");
    private static final Color TEXTO_ACTIVO = Color.web("#89b4fa");
    private static final Color BORDE_DERECHO = Color.web("#313244");

    private final TextArea textArea;
    private int lineaActual = 1;
    private double lineHeight = -1;
    private ScrollPane scrollPane;
    
    /** Ancho deseado de la columna, independiente del layout de JavaFX */
    private double anchoColumna = 48;

    public LineNumberColumn(TextArea textArea) {
        this.textArea = textArea;
        
        // Ancho inicial — CRUCIAL: si el Canvas tiene ancho 0, todo dibujo se recorta
        anchoColumna = 48;
        setWidth(48);
        setHeight(200);

        // Redibujar al cambiar el texto
        textArea.textProperty().addListener((obs, old, neu) -> {
            Platform.runLater(this::redibujar);
        });

        // Redibujar al cambiar la posición del cursor
        textArea.caretPositionProperty().addListener((obs, old, neu) -> {
            actualizarLineaActual();
            redibujar();
        });

        // Redibujar al cambiar la fuente
        textArea.fontProperty().addListener((obs, old, neu) -> {
            Platform.runLater(this::redibujar);
        });

        // Redibujar al cambiar altura
        textArea.heightProperty().addListener((obs, old, neu) -> {
            Platform.runLater(this::redibujar);
        });

        // Esperar a que el TextArea esté renderizado
        Platform.runLater(() -> {
            conectarScroll();
            redibujar();
        });
    }

    // ─── Redimensionable: CRUCIAL para que HBox respete el ancho ───

    @Override
    public boolean isResizable() {
        return true;
    }

    @Override
    public double prefWidth(double height) {
        return anchoColumna;
    }

    @Override
    public double minWidth(double height) {
        return 36;
    }

    @Override
    public double maxWidth(double height) {
        return 120;
    }

    // ─── Sincronización de scroll ───

    private void conectarScroll() {
        try {
            scrollPane = (ScrollPane) textArea.lookup(".scroll-pane");
            if (scrollPane != null) {
                scrollPane.vvalueProperty().addListener((obs, old, val) -> {
                    setTranslateY(-getScrollOffset());
                    redibujar();
                });
            }
        } catch (Exception e) {
            // Sin sincronización de scroll si no se puede conectar
        }
    }

    private double getScrollOffset() {
        if (scrollPane == null) return 0;
        double max = scrollPane.getVmax();
        double value = scrollPane.getVvalue();
        if (max == 0) return 0;
        double contentHeight = textArea.getHeight() * 1.5;
        return value * (contentHeight - textArea.getHeight());
    }

    private void actualizarLineaActual() {
        String texto = textArea.getText();
        int pos = textArea.getCaretPosition();
        if (texto.isEmpty() || pos > texto.length()) {
            lineaActual = 1;
            return;
        }
        int linea = 1;
        for (int i = 0; i < pos && i < texto.length(); i++) {
            if (texto.charAt(i) == '\n') linea++;
        }
        lineaActual = linea;
    }

    // ─── Dibujado ───

    public void redibujar() {
        GraphicsContext gc = getGraphicsContext2D();
        double h = textArea.getHeight();
        if (h <= 0) h = 200;

        // Contar líneas y recalcular ancho
        String texto = textArea.getText();
        String[] lineas = texto.isEmpty() ? new String[]{""} : texto.split("\n", -1);
        int numLineas = lineas.length;

        int digitos = String.valueOf(numLineas).length();
        double nuevoAncho = Math.max(40, digitos * 10 + 18);
        
        // Solo redimensionar si cambió el ancho (evita loops)
        if (Math.abs(nuevoAncho - anchoColumna) > 1) {
            anchoColumna = nuevoAncho;
            // Forzar relayout del HBox padre
            if (getParent() != null) {
                getParent().requestLayout();
            }
        }

        double w = anchoColumna;
        // Asegurar que el Canvas interno tenga el ancho correcto para el clip
        if (Math.abs(getWidth() - w) > 0.5) {
            setWidth(w);
        }
        if (Math.abs(getHeight() - h) > 0.5) {
            setHeight(h);
        }

        // Fondo
        gc.setFill(FONDO);
        gc.clearRect(0, 0, w, h);
        gc.fillRect(0, 0, w, h);

        // Línea separadora derecha
        gc.setStroke(BORDE_DERECHO);
        gc.setLineWidth(1);
        gc.strokeLine(w - 1, 0, w - 1, h);

        // Calcular altura de línea
        String fontName = textArea.getFont().getFamily();
        double fontSize = textArea.getFont().getSize();
        lineHeight = fontSize * 1.5;
        if (lineHeight < 18) lineHeight = 18;

        // Dibujar números
        gc.setTextAlign(TextAlignment.RIGHT);
        gc.setTextBaseline(VPos.TOP);

        for (int i = 0; i < numLineas; i++) {
            int numLinea = i + 1;
            double y = i * lineHeight + 4;

            if (numLinea == lineaActual) {
                gc.setFill(TEXTO_ACTIVO);
                gc.setFont(Font.font(fontName, 13));
            } else {
                gc.setFill(TEXTO_NORMAL);
                gc.setFont(Font.font(fontName, 12));
            }

            gc.fillText(String.valueOf(numLinea), w - 6, y);
        }
    }
}
