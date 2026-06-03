package timotomata.ui;

import javafx.application.Platform;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Region;

/**
 * Columna de números de línea como un TextArea de solo lectura.
 * Usa el mismo font, padding y line-spacing que el editor,
 * por lo que las líneas se alinean perfectamente sin necesidad
 * de calcular alturas de línea manualmente.
 */
public class LineNumberColumn extends TextArea {

    private final TextArea editor;
    private ScrollPane editorScroll;
    private ScrollPane miScroll;
    private boolean sincronizandoScroll = false;

    public LineNumberColumn(TextArea editor) {
        this.editor = editor;

        // Configuración básica: solo lectura, sin foco
        setEditable(false);
        setFocusTraversable(false);

        // Mismo estilo CSS que el editor (heredar font, line-spacing, fondo)
        setStyle(editor.getStyle());

        // Mismo padding que el editor (4px arriba/abajo, 8px derecha, 0 izquierda)
        setPadding(editor.getPadding());

        // Color de texto: gris tenue para los números
        setStyle(getStyle()
            + " -fx-text-fill: #585b70;"
            + " -fx-highlight-fill: transparent;"
            + " -fx-highlight-text-fill: #585b70;");

        // Ancho inicial
        setPrefWidth(48);
        setMaxWidth(48);

        // Sincronizar contenido: cuando el texto del editor cambia,
        // actualizar los números de línea
        editor.textProperty().addListener((obs, old, neu) ->
            Platform.runLater(this::actualizarNumeros));

        // Sincronizar línea activa (cursor)
        editor.caretPositionProperty().addListener((obs, old, neu) ->
            actualizarLineaActiva());

        // Si el editor cambia de fuente, sincronizar
        editor.fontProperty().addListener((obs, old, neu) -> {
            setFont(neu);
            Platform.runLater(this::actualizarNumeros);
        });

        // Sincronizar scroll cuando ambos estén renderizados
        Platform.runLater(() -> {
            conectarScroll();
            ocultarScrollBars();
            actualizarNumeros();
        });
    }

    /**
     * Actualiza el contenido del TextArea con los números de línea
     * y ajusta el ancho según la cantidad de dígitos.
     */
    private void actualizarNumeros() {
        String texto = editor.getText();
        int numLineas = texto.isEmpty() ? 1 : texto.split("\n", -1).length;

        // Construir el texto con los números de línea
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= numLineas; i++) {
            sb.append(i).append("\n");
        }
        setText(sb.toString());

        // Ajustar ancho según dígitos
        int digitos = String.valueOf(numLineas).length();
        double ancho = Math.max(40, digitos * 10 + 18);
        setPrefWidth(ancho);
        setMaxWidth(ancho);
    }

    /**
     * Busca y resalta la línea activa donde está el cursor del editor.
     * Lo hace posicionando el cursor en la misma línea en este TextArea.
     */
    private void actualizarLineaActiva() {
        String texto = editor.getText();
        int pos = editor.getCaretPosition();
        if (texto.isEmpty() || pos > texto.length()) {
            positionCaret(0);
            return;
        }
        // Calcular en qué línea está el cursor
        int linea = 0;
        for (int i = 0; i < pos && i < texto.length(); i++) {
            if (texto.charAt(i) == '\n') linea++;
        }
        // Posicionar el caret en la misma línea de los números
        // Simplemente contamos saltos de línea en este TextArea hasta llegar
        // a la línea correspondiente
        String miTexto = getText();
        int miPos = 0;
        int count = 0;
        for (int i = 0; i < miTexto.length() && count < linea; i++) {
            if (miTexto.charAt(i) == '\n') {
                count++;
                miPos = i + 1;
            }
        }
        positionCaret(miPos);
    }

    /**
     * Conecta el scroll de este TextArea con el del editor,
     * sincronización bidireccional.
     */
    private void conectarScroll() {
        try {
            editorScroll = (ScrollPane) editor.lookup(".scroll-pane");
            miScroll = (ScrollPane) this.lookup(".scroll-pane");

            if (editorScroll != null && miScroll != null) {
                // Cuando el editor scrollea, los números scrollean igual
                editorScroll.vvalueProperty().addListener((obs, old, val) -> {
                    if (!sincronizandoScroll) {
                        sincronizandoScroll = true;
                        miScroll.setVvalue(val.doubleValue());
                        sincronizandoScroll = false;
                    }
                });

                // Cuando los números scrollean, el editor scrollea igual
                miScroll.vvalueProperty().addListener((obs, old, val) -> {
                    if (!sincronizandoScroll) {
                        sincronizandoScroll = true;
                        editorScroll.setVvalue(val.doubleValue());
                        sincronizandoScroll = false;
                    }
                });
            }
        } catch (Exception e) {
            // Si falla la conexión, intentar de nuevo más tarde
            Platform.runLater(this::conectarScroll);
        }
    }

    /**
     * Oculta las barras de scroll de este TextArea de números
     * configurando las políticas del ScrollPane interno.
     */
    private int reintentosOcultarScroll = 0;

    private void ocultarScrollBars() {
        if (reintentosOcultarScroll > 5) return;
        reintentosOcultarScroll++;
        try {
            ScrollPane sp = (ScrollPane) lookup(".scroll-pane");
            if (sp != null) {
                sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
                sp.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            } else if (reintentosOcultarScroll <= 5) {
                Platform.runLater(this::ocultarScrollBars);
            }
        } catch (Exception e) {
            if (reintentosOcultarScroll <= 5) {
                Platform.runLater(this::ocultarScrollBars);
            }
        }
    }
}
