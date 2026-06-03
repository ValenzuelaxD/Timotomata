package timotomata.ui;

import java.util.*;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import timotomata.lexer.*;

public class SyntaxEditor extends StackPane {

    private final CodeArea codeArea;

    private static final Set<TipoToken> KEYWORDS = Set.of(
        TipoToken.SENSOR, TipoToken.UMBRAL, TipoToken.ABS, TipoToken.FIN
    );
    private static final Set<TipoToken> CONTROL = Set.of(
        TipoToken.SI, TipoToken.ENTONCES, TipoToken.ESTADO
    );
    private static final Set<TipoToken> STATES = Set.of(
        TipoToken.ESTADO_SISTEMA
    );
    private static final Set<TipoToken> FUNCTIONS = Set.of(
        TipoToken.CALCULAR, TipoToken.SENO, TipoToken.COSENO,
        TipoToken.CUADRADA, TipoToken.PROMEDIO, TipoToken.MAXIMO, TipoToken.SUMA
    );
    private static final Set<TipoToken> PARAMS = Set.of(
        TipoToken.AMPLITUD, TipoToken.FRECUENCIA, TipoToken.VENTANA, TipoToken.CON
    );
    private static final Set<TipoToken> OPERATORS = Set.of(
        TipoToken.MAS, TipoToken.MENOS, TipoToken.POR, TipoToken.DIV,
        TipoToken.MAYOR, TipoToken.MENOR, TipoToken.IGUAL_IGUAL,
        TipoToken.MAYOR_IGUAL, TipoToken.MENOR_IGUAL, TipoToken.DIFERENTE,
        TipoToken.ASIGNACION
    );
    private static final Set<TipoToken> SYMBOLS = Set.of(
        TipoToken.PUNTO_COMA, TipoToken.PAREN_IZQ, TipoToken.PAREN_DER, TipoToken.COMA
    );

    private int[] offsetsDeLinea = null;
    private String ultimoCodigoOffsets = null;

    public SyntaxEditor() {
        codeArea = new CodeArea();
        codeArea.getStylesheets().addAll(
            getClass().getResource("estilos.css").toExternalForm()
        );
        codeArea.setStyle("-fx-background-color: #1e1e2e;");

        codeArea.setParagraphGraphicFactory(paragraphIndex -> {
    Text lineNo = new Text(Integer.toString(paragraphIndex + 1));
    lineNo.setFill(Color.web("#585b70"));
    lineNo.setFont(Font.font("Consolas", 14));
    return lineNo;
});

        codeArea.multiPlainChanges().subscribe(changes -> {
            String text = codeArea.getText();
            if (!text.isEmpty()) {
                try {
                    Lexer lexer = new Lexer(text);
                    List<Token> tokens = lexer.escanear();
                    StyleSpans<Collection<String>> spans = computeStyleSpans(text, tokens, null);
                    codeArea.setStyleSpans(0, spans);
                } catch (Exception e) {
                    // ignorar errores del lexer durante resaltado inline
                }
            }
        });

        getChildren().add(codeArea);
    }

    public CodeArea getCodeArea() { return codeArea; }

    public String getText() { return codeArea.getText(); }

    public void setText(String text) {
        codeArea.replaceText(0, codeArea.getText().length(), text);
    }

    public int getCaretPosition() { return codeArea.getCaretPosition(); }

    public ObservableValue<Integer> caretPositionProperty() {
        return codeArea.caretPositionProperty();
    }

    public ObservableValue<String> textProperty() { return codeArea.textProperty(); }

    public void clear() { codeArea.replaceText(""); }

    public void setEditorOnKeyReleased(EventHandler<? super KeyEvent> handler) {
        codeArea.setOnKeyReleased(handler);
    }

    public void aplicarResaltado(String codigo, List<Token> tokens,
                                  List<Lexer.ErrorLexico> erroresLex,
                                  List<timotomata.parser.ErrorSintacticoDetalle> erroresSint) {
        Map<Integer, String> errorClasses = new HashMap<>();
        if (erroresLex != null) {
            for (Lexer.ErrorLexico err : erroresLex) {
                int offset = offsetDesdeLineaColumna(codigo, err.linea, err.columna);
                if (offset >= 0 && offset < codigo.length()) {
                    errorClasses.put(offset, "error-lexico");
                }
            }
        }
        if (erroresSint != null) {
            for (timotomata.parser.ErrorSintacticoDetalle err : erroresSint) {
                int offset = offsetDesdeLineaColumna(codigo, err.linea, err.columna);
                if (offset >= 0 && offset < codigo.length() && !errorClasses.containsKey(offset)) {
                    int finLinea = codigo.indexOf('\n', offset);
                    if (finLinea == -1) finLinea = codigo.length();
                    for (int i = offset; i < Math.min(finLinea, offset + 20); i++) {
                        errorClasses.put(i, "error-sintactico");
                    }
                }
            }
        }
        try {
            StyleSpans<Collection<String>> spans = computeStyleSpans(codigo, tokens, errorClasses);
            codeArea.setStyleSpans(0, spans);
        } catch (Exception e) {
            // ignorar errores de resaltado
        }
    }

    private StyleSpans<Collection<String>> computeStyleSpans(String text, List<Token> tokens,
            Map<Integer, String> errorClasses) {
        if (text == null || text.isEmpty()) {
            return new StyleSpansBuilder<Collection<String>>().create();
        }
        if (tokens == null || tokens.isEmpty()) {
            StyleSpansBuilder<Collection<String>> builder = new StyleSpansBuilder<>();
            builder.add(Collections.singleton("token-default"), text.length());
            return builder.create();
        }

        StyleSpansBuilder<Collection<String>> builder = new StyleSpansBuilder<>();
        int lastEnd = 0;

        for (Token token : tokens) {
            if (token.tipo == TipoToken.EOF) continue;

            int start = offsetDesdeLineaColumna(text, token.linea, token.columna);
            int end = start + token.lexema.length();
            if (start < 0 || start > text.length()) continue;

            if (start > lastEnd) {
                builder.add(Collections.singleton("token-default"), start - lastEnd);
            }

            List<String> classes = new ArrayList<>();
            classes.add(classForToken(token.tipo));
            if (errorClasses != null) {
                for (int i = start; i < end; i++) {
                    String err = errorClasses.get(i);
                    if (err != null) {
                        classes.add(err);
                        break;
                    }
                }
            }
            builder.add(classes, end - start);
            lastEnd = end;
        }

        if (lastEnd < text.length()) {
            builder.add(Collections.singleton("token-default"), text.length() - lastEnd);
        }

        return builder.create();
    }

    private String classForToken(TipoToken tipo) {
        if (KEYWORDS.contains(tipo)) return "token-keyword";
        if (CONTROL.contains(tipo)) return "token-control";
        if (STATES.contains(tipo)) return "token-state";
        if (FUNCTIONS.contains(tipo)) return "token-function";
        if (PARAMS.contains(tipo)) return "token-param";
        if (OPERATORS.contains(tipo)) return "token-operator";
        if (SYMBOLS.contains(tipo)) return "token-symbol";
        if (tipo == TipoToken.NUMERO) return "token-number";
        if (tipo == TipoToken.ID) return "token-identifier";
        return "token-default";
    }

    private void computarOffsetsDeLinea(String codigo) {
        if (codigo == null) {
            offsetsDeLinea = new int[]{0};
            return;
        }
        if (codigo == ultimoCodigoOffsets) return;
        ultimoCodigoOffsets = codigo;
        int numLineas = 1;
        for (int i = 0; i < codigo.length(); i++) {
            if (codigo.charAt(i) == '\n') numLineas++;
        }
        offsetsDeLinea = new int[numLineas + 1];
        int lineaIdx = 1;
        for (int i = 0; i < codigo.length(); i++) {
            if (codigo.charAt(i) == '\n') {
                offsetsDeLinea[lineaIdx + 1] = i + 1;
                lineaIdx++;
            }
        }
        offsetsDeLinea[numLineas] = codigo.length();
    }

    private int offsetDesdeLineaColumna(String codigo, int linea, int columna) {
        if (codigo == null || codigo.isEmpty()) return 0;
        computarOffsetsDeLinea(codigo);
        if (linea <= 0 || linea >= offsetsDeLinea.length) return codigo.length();
        int offset = offsetsDeLinea[linea];
        return Math.min(offset + columna - 1, codigo.length());
    }
}
