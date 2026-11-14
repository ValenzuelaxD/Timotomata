package timotomata.ui;

import java.util.*;
import javafx.geometry.Point2D;
import javafx.geometry.VPos;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import timotomata.parser.NodoDerivacion;

/**
 * Panel que dibuja el árbol de derivación sintáctica de forma gráfica.
 * Los nodos sintéticos (CONDICION_SIG, EXPRESION_SIG, TERMINO_SIG,
 * LISTA_PARAMS, PARAM) se colapsan automáticamente promoviendo sus hijos.
 */
public class PanelDerivacion {

    // ─── Constantes de dibujo ───
    private static final double ALTO_NODO = 26;
    private static final double RADIO_V = ALTO_NODO / 2.0;
    private static final double ANCHO_MIN_NODO = 28;
    private static final double ESPACIO_H = 28;
    private static final double ESPACIO_V = 68;
    private static final double PADDING_LATERAL = 50;
    private static final double PADDING_SUPERIOR = 40;
    private static final double MIN_CANVAS_W = 500;
    private static final double MIN_CANVAS_H = 260;
    private static final double ESPACIO_V_HOJA = 16;

    private final NodoDerivacion raiz;
    private final Map<NodoDerivacion, Point2D> posiciones;

    // ─── Paleta (Catppuccin Mocha) ───
    private static final Color COLOR_FONDO = Color.web("#1e1e2e");
    private static final Color CIRCULO_INTERNO = Color.web("#45475a");
    private static final Color BORDE_INTERNO = Color.web("#89b4fa");
    private static final Color TEXTO_INTERNO = Color.web("#cdd6f4");
    private static final Color CIRCULO_HOJA = Color.web("#313244");
    private static final Color BORDE_HOJA = Color.web("#a6e3a1");
    private static final Color TEXTO_HOJA = Color.web("#a6e3a1");
    private static final Color LINEA_INTERNA = Color.web("#585b70");
    private static final Color LINEA_HOJA = Color.web("#45475a");
    private static final Color CIRCULO_PROGRAMA = Color.web("#313244");
    private static final Color BORDE_PROGRAMA = Color.web("#f9e2af");
    private static final Color TEXTO_PROGRAMA = Color.web("#f9e2af");

    public PanelDerivacion(NodoDerivacion raizOriginal) {
        this.raiz = copiarSinSinteticos(raizOriginal);
        this.posiciones = new HashMap<>();
    }

    /**
     * Crea una copia del árbol colapsando los nodos sintéticos
     * (CONDICION_SIG, EXPRESION_SIG, TERMINO_SIG, LISTA_PARAMS, PARAM)
     * sin mutar el árbol original del parser.
     */
    public static NodoDerivacion copiarSinSinteticos(NodoDerivacion nodo) {
        if (nodo == null) return null;
        NodoDerivacion copia = new NodoDerivacion(nodo.valor);
        copia.sintetico = nodo.sintetico;
        for (NodoDerivacion hijo : nodo.hijos) {
            NodoDerivacion hijoCopia = copiarSinSinteticos(hijo);
            if (hijoCopia == null) continue;
            if (hijoCopia.sintetico) {
                copia.hijos.addAll(hijoCopia.hijos);
            } else {
                copia.hijos.add(hijoCopia);
            }
        }
        return copia;
    }

    /** Abre una ventana modal mostrando el árbol de derivación. */
    public void mostrarEnVentana() {
        mostrarEnVentana(null);
    }

    /**
     * Abre una ventana modal asociada a la ventana principal mostrando
     * el árbol de derivación, con soporte de paneo y zoom (Ctrl + rueda o +/-/0).
     */
    public void mostrarEnVentana(Window owner) {
        double ancho = calcularAncho(raiz);
        double totalW = Math.max(ancho + PADDING_LATERAL * 2, MIN_CANVAS_W);
        double totalH = Math.max(calcularAltura(raiz) + PADDING_SUPERIOR * 2, MIN_CANVAS_H);

        posiciones.clear();
        calcularPosiciones(raiz, totalW / 2.0, PADDING_SUPERIOR);

        // ─── Construir el árbol con nodos del scene graph ───
        Pane pane = new Pane();
        pane.setPrefSize(totalW, totalH);
        pane.setStyle("-fx-background-color: #1e1e2e;");

        // 1. Líneas entre nodos (detrás de las cápsulas)
        for (Map.Entry<NodoDerivacion, Point2D> entry : posiciones.entrySet()) {
            NodoDerivacion nodo = entry.getKey();
            Point2D p = entry.getValue();
            for (NodoDerivacion hijo : nodo.hijos) {
                Point2D hp = posiciones.get(hijo);
                if (hp != null) {
                    Line line = new Line(
                        p.getX(), p.getY() + RADIO_V,
                        hp.getX(), hp.getY() - RADIO_V
                    );
                    line.setStroke(hijo.hijos.isEmpty() ? LINEA_HOJA : LINEA_INTERNA);
                    line.setStrokeWidth(1.8);
                    pane.getChildren().add(line);
                }
            }
        }

        // 2. Cápsulas + texto por cada nodo (altura constante para no solapar niveles)
        for (Map.Entry<NodoDerivacion, Point2D> entry : posiciones.entrySet()) {
            NodoDerivacion nodo = entry.getKey();
            Point2D p = entry.getValue();

            boolean esTerminal = nodo.hijos.isEmpty();
            boolean esRaiz = nodo == raiz;

            Color colorFondo, colorBorde, colorTexto;
            double fontSize;

            if (esRaiz) {
                colorFondo = CIRCULO_PROGRAMA;
                colorBorde = BORDE_PROGRAMA;
                colorTexto = TEXTO_PROGRAMA;
                fontSize = 13;
            } else if (esTerminal) {
                colorFondo = CIRCULO_HOJA;
                colorBorde = BORDE_HOJA;
                colorTexto = TEXTO_HOJA;
                fontSize = 11;
            } else {
                colorFondo = CIRCULO_INTERNO;
                colorBorde = BORDE_INTERNO;
                colorTexto = TEXTO_INTERNO;
                fontSize = 11.5;
            }

            // Para terminales: mostrar solo el lexema (extraer de "TIPO(lexema)")
            String textoMostrar = esTerminal ? extraerLexema(nodo.valor) : nodo.valor;

            // Texto medido antes de crear la cápsula para ajustar el ancho exacto
            Text text = new Text(textoMostrar);
            text.setFill(colorTexto);
            text.setFont(Font.font("Consolas", fontSize));
            text.setTextAlignment(TextAlignment.CENTER);
            text.setTextOrigin(VPos.CENTER);
            double textW = text.getLayoutBounds().getWidth();

            double anchoNodo = Math.max(ANCHO_MIN_NODO,
                Math.max(estimarAnchoTexto(textoMostrar), textW) + 18);
            double xIzq = p.getX() - anchoNodo / 2.0;
            double ySup = p.getY() - RADIO_V;

            // Sombra sutil del nodo en el lienzo
            Rectangle sombra = new Rectangle(xIzq + 2, ySup + 3, anchoNodo, ALTO_NODO);
            sombra.setArcWidth(ALTO_NODO);
            sombra.setArcHeight(ALTO_NODO);
            sombra.setFill(Color.web("#11111b88"));
            pane.getChildren().add(sombra);

            // Cápsula del nodo
            Rectangle capsula = new Rectangle(xIzq, ySup, anchoNodo, ALTO_NODO);
            capsula.setArcWidth(ALTO_NODO);
            capsula.setArcHeight(ALTO_NODO);
            capsula.setFill(colorFondo);
            capsula.setStroke(colorBorde);
            capsula.setStrokeWidth(esRaiz ? 2.5 : (esTerminal ? 1.8 : 2.0));
            pane.getChildren().add(capsula);

            text.setX(p.getX() - textW / 2.0);
            text.setY(p.getY());
            pane.getChildren().add(text);
        }

        Group zoomGroup = new Group(pane);

        // ─── ScrollPane con el árbol como contenido ───
        ScrollPane scroll = new ScrollPane(zoomGroup);
        scroll.setStyle("-fx-background: #1e1e2e; -fx-background-color: #1e1e2e;");
        scroll.setPannable(true);
        scroll.setFitToWidth(false);
        scroll.setFitToHeight(false);

        final double[] escala = { 1.0 };
        Runnable aplicarZoom = () -> {
            pane.setScaleX(escala[0]);
            pane.setScaleY(escala[0]);
        };

        scroll.setOnScroll(e -> {
            if (e.isControlDown()) {
                e.consume();
                double factor = e.getDeltaY() > 0 ? 1.12 : (1.0 / 1.12);
                escala[0] = Math.max(0.25, Math.min(2.5, escala[0] * factor));
                aplicarZoom.run();
            }
        });

        Scene scene = new Scene(scroll, 960, 680);
        scene.setFill(COLOR_FONDO);

        var css = getClass().getResource("estilos.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());

        Stage stage = new Stage();
        stage.setTitle("Árbol de derivación sintáctica  ·  Ctrl + rueda o + / - / 0 para zoom");
        if (owner != null) {
            stage.initOwner(owner);
            stage.initModality(Modality.WINDOW_MODAL);
        }
        Iconos.aplicar(stage);
        stage.setScene(scene);
        stage.centerOnScreen();

        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                stage.close();
            } else if (e.getCode() == KeyCode.PLUS || e.getCode() == KeyCode.EQUALS
                    || e.getCode() == KeyCode.ADD) {
                escala[0] = Math.min(2.5, escala[0] * 1.15);
                aplicarZoom.run();
            } else if (e.getCode() == KeyCode.MINUS || e.getCode() == KeyCode.SUBTRACT) {
                escala[0] = Math.max(0.25, escala[0] / 1.15);
                aplicarZoom.run();
            } else if (e.getCode() == KeyCode.DIGIT0 || e.getCode() == KeyCode.NUMPAD0) {
                escala[0] = 1.0;
                aplicarZoom.run();
            }
        });

        stage.showingProperty().addListener((obs, antes, ahora) -> {
            if (ahora) {
                javafx.application.Platform.runLater(() -> {
                    scroll.setHvalue(0.5);
                    scroll.setVvalue(0.0);
                });
            }
        });

        stage.show();
    }

    // =============================================================
    //  CÁLCULO DE POSICIONES
    // =============================================================

    private double calcularAncho(NodoDerivacion nodo) {
        String texto = nodo.hijos.isEmpty() ? extraerLexema(nodo.valor) : nodo.valor;
        double anchoPropio = Math.max(ANCHO_MIN_NODO, estimarAnchoTexto(texto) + 18);
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
        if (nodo.hijos.isEmpty()) {
            return ALTO_NODO + 12;
        }
        double maxAltura = 0;
        boolean tieneHijosTerminales = false;
        for (NodoDerivacion hijo : nodo.hijos) {
            maxAltura = Math.max(maxAltura, calcularAltura(hijo));
            if (hijo.hijos.isEmpty()) {
                tieneHijosTerminales = true;
            }
        }
        return maxAltura + ESPACIO_V + (tieneHijosTerminales ? ESPACIO_V_HOJA : 0);
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

        boolean tieneHijosTerminales = false;
        for (NodoDerivacion hijo : nodo.hijos) {
            if (hijo.hijos.isEmpty()) {
                tieneHijosTerminales = true;
                break;
            }
        }
        double espacioV = ESPACIO_V + (tieneHijosTerminales ? ESPACIO_V_HOJA : 0);

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
        return texto.length() * 7.6;
    }

    private static String extraerLexema(String valor) {
        if (valor == null) return "";
        int idx = valor.indexOf('(');
        if (idx > 0 && valor.endsWith(")")) {
            return valor.substring(idx + 1, valor.length() - 1);
        }
        return valor;
    }
}
