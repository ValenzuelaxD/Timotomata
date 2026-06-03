package timotomata.ui;

import java.util.*;
import javafx.geometry.Point2D;
import javafx.geometry.VPos;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.stage.Modality;
import javafx.stage.Stage;
import timotomata.parser.NodoDerivacion;

/**
 * Panel que dibuja el árbol de derivación sintáctica de forma gráfica.
 *
 * Cada nodo terminal se dibuja como DOS nodos:
 *   - Nodo azul: categoría gramatical (SENSOR, ID, PUNTO_COMA, etc.)
 *   → Nodo verde: lexema real (sensor, voltaje, ;, etc.)
 *
 * Los nodos sintéticos (EXPRESION_SIG, TERMINO_SIG) se colapsan automáticamente.
 */
public class PanelDerivacion {

    // ─── Constantes de dibujo ───
    private static final double RADIO = 12;
    private static final double ESPACIO_H = 75;
    private static final double ESPACIO_V = 75;
    private static final double PADDING_LATERAL = 50;
    private static final double PADDING_SUPERIOR = 20;
    private static final double MIN_CANVAS_W = 400;
    private static final double MIN_CANVAS_H = 200;

    private final NodoDerivacion raiz;
    private Map<NodoDerivacion, Point2D> posiciones;

    // ─── Paleta ───
    private static final Color COLOR_FONDO = Color.web("#1e1e2e");
    // Nodos categoría (no-terminales y terminales como SENSOR, ID)
    private static final Color CIRCULO_CATEGORIA = Color.web("#45475a");
    private static final Color BORDE_CATEGORIA = Color.web("#89b4fa");
    private static final Color TEXTO_CATEGORIA = Color.web("#cdd6f4");
    // Nodos lexema (el valor real: "sensor", "voltaje", ";")
    private static final Color CIRCULO_LEXEMA = Color.web("#313244");
    private static final Color BORDE_LEXEMA = Color.web("#a6e3a1");
    private static final Color TEXTO_LEXEMA = Color.web("#a6e3a1");
    // Líneas
    private static final Color LINEA_CATEGORIA = Color.web("#585b70");
    private static final Color LINEA_LEXEMA = Color.web("#45475a");
    // Raíz PROGRAMA
    private static final Color CIRCULO_PROGRAMA = Color.web("#2a2a3e");
    private static final Color BORDE_PROGRAMA = Color.web("#f9e2af");

    public PanelDerivacion(NodoDerivacion raiz) {
        this.raiz = raiz;
        this.posiciones = new HashMap<>();
        colapsarSinteticos(raiz);
    }

    // =============================================================
    //  DETECCIÓN DE TIPO DE NODO
    // =============================================================

    /** Nodo lexema: hoja verde con el valor real del token */
    private boolean esLexema(NodoDerivacion nodo) {
        return nodo.lexema != null && nodo.hijos.isEmpty();
    }

    // =============================================================
    //  COLAPSAR NODOS SINTÉTICOS
    // =============================================================
    private void colapsarSinteticos(NodoDerivacion nodo) {
        for (NodoDerivacion hijo : new ArrayList<>(nodo.hijos)) {
            colapsarSinteticos(hijo);
        }
        if (nodo.sintetico) return;

        List<NodoDerivacion> nuevosHijos = new ArrayList<>();
        for (NodoDerivacion hijo : nodo.hijos) {
            if (hijo.sintetico) {
                nuevosHijos.addAll(hijo.hijos);
            } else {
                nuevosHijos.add(hijo);
            }
        }
        nodo.hijos = nuevosHijos;
    }

    // =============================================================
    //  DIBUJO
    // =============================================================
    public void mostrarEnVentana() {
        double ancho = calcularAncho(raiz);
        double totalW = Math.max(ancho + PADDING_LATERAL * 2, MIN_CANVAS_W);
        double totalH = Math.max(calcularAltura(raiz) + PADDING_SUPERIOR * 2, MIN_CANVAS_H);

        posiciones.clear();
        calcularPosiciones(raiz, totalW / 2.0, PADDING_SUPERIOR);

        Pane pane = new Pane();
        pane.setPrefSize(totalW, totalH);
        pane.setStyle("-fx-background-color: #1e1e2e;");

        // 1. Líneas entre nodos (detrás de los círculos)
        for (Map.Entry<NodoDerivacion, Point2D> entry : posiciones.entrySet()) {
            NodoDerivacion nodo = entry.getKey();
            Point2D p = entry.getValue();
            for (NodoDerivacion hijo : nodo.hijos) {
                Point2D hp = posiciones.get(hijo);
                if (hp != null) {
                    double radioHijo = Math.max(RADIO,
                        (estimarAnchoTexto(hijo.valor) + 14) / 2);
                    Line line = new Line(
                        p.getX(), p.getY() + RADIO,
                        hp.getX(), hp.getY() - radioHijo
                    );
                    // Línea verde si el hijo es lexema, azul si es categoría
                    line.setStroke(esLexema(hijo) ? LINEA_LEXEMA : LINEA_CATEGORIA);
                    line.setStrokeWidth(2);
                    pane.getChildren().add(line);
                }
            }
        }

        // 2. Círculos + texto por cada nodo
        for (Map.Entry<NodoDerivacion, Point2D> entry : posiciones.entrySet()) {
            NodoDerivacion nodo = entry.getKey();
            Point2D p = entry.getValue();

            boolean esNodoLexema = esLexema(nodo);
            boolean esRaiz = nodo == raiz;

            Color colorFondo, colorBorde, colorTexto;
            double fontSize;

            if (esRaiz) {
                colorFondo = CIRCULO_PROGRAMA;
                colorBorde = BORDE_PROGRAMA;
                colorTexto = Color.web("#f9e2af");
                fontSize = 13;
            } else if (esNodoLexema) {
                // Nodo verde: el lexema real (sensor, voltaje, ;)
                colorFondo = CIRCULO_LEXEMA;
                colorBorde = BORDE_LEXEMA;
                colorTexto = TEXTO_LEXEMA;
                fontSize = 11;
            } else {
                // Nodo azul: categoría gramatical (SENSOR, ID, SENTENCIA, etc.)
                colorFondo = CIRCULO_CATEGORIA;
                colorBorde = BORDE_CATEGORIA;
                colorTexto = TEXTO_CATEGORIA;
                fontSize = 12;
            }

            String textoMostrar = nodo.valor;

            // Calcular radio según el texto
            double textoAncho = estimarAnchoTexto(textoMostrar);
            double radioNodo = Math.max(RADIO, (textoAncho + 14) / 2);

            // Sombra
            Circle sombra = new Circle(p.getX() + 2, p.getY() + 3, radioNodo);
            sombra.setFill(Color.web("#00000033"));
            pane.getChildren().add(sombra);

            // Círculo
            Circle circle = new Circle(p.getX(), p.getY(), radioNodo);
            circle.setFill(colorFondo);
            circle.setStroke(colorBorde);
            circle.setStrokeWidth(esRaiz ? 3 : (esNodoLexema ? 2 : 2.5));
            pane.getChildren().add(circle);

            // Texto
            Text text = new Text(textoMostrar);
            text.setFill(colorTexto);
            text.setFont(Font.font("Consolas", fontSize));
            text.setTextAlignment(TextAlignment.CENTER);
            text.setTextOrigin(VPos.CENTER);
            double textW = text.getBoundsInLocal().getWidth();
            text.setX(p.getX() - textW / 2);
            text.setY(p.getY());
            pane.getChildren().add(text);
        }

        // ─── ScrollPane ───
        ScrollPane scroll = new ScrollPane(pane);
        scroll.setStyle("-fx-background: #1e1e2e; -fx-background-color: #1e1e2e;");
        scroll.setPannable(true);
        scroll.setFitToWidth(false);
        scroll.setFitToHeight(false);

        Scene scene = new Scene(scroll, 900, 650);
        scene.setFill(COLOR_FONDO);

        Stage stage = new Stage();
        stage.setTitle("Arbol de Derivacion Sintactica");
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setScene(scene);

        stage.showingProperty().addListener((obs, antes, ahora) -> {
            if (ahora) {
                javafx.application.Platform.runLater(() -> {
                    scroll.setHvalue(0.5);
                    scroll.setVvalue(0.5);
                });
            }
        });

        stage.show();
    }

    // =============================================================
    //  CÁLCULO DE POSICIONES
    // =============================================================

    private double calcularAncho(NodoDerivacion nodo) {
        double anchoPropio = Math.max(RADIO * 2 + 20,
            estimarAnchoTexto(nodo.valor) + 10);
        if (nodo.hijos.isEmpty()) {
            return anchoPropio;
        }
        double total = 0;
        for (NodoDerivacion hijo : nodo.hijos) {
            total += calcularAncho(hijo);
        }
        total += (nodo.hijos.size() - 1) * ESPACIO_H;
        return Math.max(total, anchoPropio);
    }

    private double calcularAltura(NodoDerivacion nodo) {
        // Hojas (lexema o ε): solo su propio tamaño
        if (nodo.hijos.isEmpty()) {
            double ancho = estimarAnchoTexto(nodo.valor);
            double r = Math.max(RADIO, (ancho + 14) / 2);
            return r * 2 + 10;
        }
        double maxAltura = 0;
        for (NodoDerivacion hijo : nodo.hijos) {
            maxAltura = Math.max(maxAltura, calcularAltura(hijo));
        }
        // Espacio extra si algún hijo es lexema (cercano al padre)
        boolean tieneLexemas = false;
        for (NodoDerivacion hijo : nodo.hijos) {
            if (esLexema(hijo)) { tieneLexemas = true; break; }
        }
        return maxAltura + ESPACIO_V + (tieneLexemas ? 25 : 0);
    }

    private double calcularPosiciones(NodoDerivacion nodo, double x, double y) {
        posiciones.put(nodo, new Point2D(x, y));

        if (nodo.hijos.isEmpty()) {
            return x;
        }

        double anchoTotal = 0;
        List<Double> anchos = new ArrayList<>();
        for (NodoDerivacion hijo : nodo.hijos) {
            double w = calcularAncho(hijo);
            anchos.add(w);
            anchoTotal += w;
        }
        anchoTotal += (nodo.hijos.size() - 1) * ESPACIO_H;

        // Espacio vertical: más compacto si los hijos son lexemas
        boolean soloLexemas = true;
        for (NodoDerivacion hijo : nodo.hijos) {
            if (!esLexema(hijo)) { soloLexemas = false; break; }
        }
        double espacioV = ESPACIO_V + (soloLexemas ? 25 : 0);

        double startX = x - anchoTotal / 2.0;
        for (int i = 0; i < nodo.hijos.size(); i++) {
            double w = anchos.get(i);
            double hijoX = startX + w / 2.0;
            calcularPosiciones(nodo.hijos.get(i), hijoX, y + espacioV);
            startX += w + ESPACIO_H;
        }

        return x;
    }

    private double estimarAnchoTexto(String texto) {
        if (texto == null) return 20;
        return texto.length() * 8;
    }
}
