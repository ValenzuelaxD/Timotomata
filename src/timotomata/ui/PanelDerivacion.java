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
 * Los nodos sintéticos (EXPRESION_SIG, TERMINO_SIG, LISTA_PARAMS, PARAM)
 * se colapsan automáticamente promoviendo sus hijos.
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
    private static final double ESPACIO_V_HOJA = 30;

    private final NodoDerivacion raiz;
    private Map<NodoDerivacion, Point2D> posiciones;

    // ─── Paleta ───
    private static final Color COLOR_FONDO = Color.web("#1e1e2e");
    private static final Color CIRCULO_INTERNO = Color.web("#45475a");
    private static final Color BORDE_INTERNO = Color.web("#89b4fa");
    private static final Color TEXTO_INTERNO = Color.web("#cdd6f4");
    private static final Color CIRCULO_HOJA = Color.web("#313244");
    private static final Color BORDE_HOJA = Color.web("#a6e3a1");
    private static final Color TEXTO_HOJA = Color.web("#a6e3a1");
    private static final Color LINEA_INTERNA = Color.web("#585b70");
    private static final Color LINEA_HOJA = Color.web("#45475a");
    private static final Color CIRCULO_PROGRAMA = Color.web("#2a2a3e");
    private static final Color BORDE_PROGRAMA = Color.web("#f9e2af");

    public PanelDerivacion(NodoDerivacion raiz) {
        this.raiz = raiz;
        this.posiciones = new HashMap<>();
        // Colapsar nodos sintéticos al construir
        colapsarSinteticos(raiz);
    }

    // =============================================================
    //  COLAPSAR NODOS SINTÉTICOS (EXPRESION_SIG, TERMINO_SIG, etc.)
    //  Promueve los hijos del nodo sintético al abuelo.
    // =============================================================
    private void colapsarSinteticos(NodoDerivacion nodo) {
        // Primero colapsar recursivamente los hijos (bottom-up)
        for (NodoDerivacion hijo : new ArrayList<>(nodo.hijos)) {
            colapsarSinteticos(hijo);
        }

        // Si este nodo es sintético, sus hijos ya fueron procesados
        // (el padre se encargará de promoverlos)
        if (nodo.sintetico) {
            return;
        }

        // Reemplazar hijos sintéticos por sus propios hijos
        List<NodoDerivacion> nuevosHijos = new ArrayList<>();
        for (NodoDerivacion hijo : nodo.hijos) {
            if (hijo.sintetico) {
                // Promover todos los hijos del nodo sintético
                nuevosHijos.addAll(hijo.hijos);
            } else {
                nuevosHijos.add(hijo);
            }
        }
        nodo.hijos = nuevosHijos;
    }

    /**
     * Abre una ventana modal mostrando el arbol de derivacion.
     */
    public void mostrarEnVentana() {
        double ancho = calcularAncho(raiz);
        double totalW = Math.max(ancho + PADDING_LATERAL * 2, MIN_CANVAS_W);
        double totalH = Math.max(calcularAltura(raiz) + PADDING_SUPERIOR * 2, MIN_CANVAS_H);

        posiciones.clear();
        calcularPosiciones(raiz, totalW / 2.0, PADDING_SUPERIOR);

        // ─── Construir el árbol con nodos del scene graph ───
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
                    // Calcular el radio real del hijo para que la linea llegue hasta el borde
                    String textoHijo = hijo.hijos.isEmpty() ? extraerLexema(hijo.valor) : hijo.valor;
                    double anchoHijo = estimarAnchoTexto(textoHijo);
                    double radioHijo = Math.max(RADIO, (anchoHijo + 14) / 2);
                    Line line = new Line(
                        p.getX(), p.getY() + RADIO,
                        hp.getX(), hp.getY() - radioHijo
                    );
                    line.setStroke(hijo.hijos.isEmpty() ? LINEA_HOJA : LINEA_INTERNA);
                    line.setStrokeWidth(2);
                    pane.getChildren().add(line);
                }
            }
        }

        // 2. Círculos + texto por cada nodo
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
                colorTexto = Color.web("#f9e2af");
                fontSize = 13;
            } else if (esTerminal) {
                colorFondo = CIRCULO_HOJA;
                colorBorde = BORDE_HOJA;
                colorTexto = Color.web("#a6e3a1");
                fontSize = 11;
            } else {
                colorFondo = CIRCULO_INTERNO;
                colorBorde = BORDE_INTERNO;
                colorTexto = Color.web("#cdd6f4");
                fontSize = 12;
            }

            // Para terminales: mostrar solo el lexema (extraer de "TIPO(lexema)")
            String textoMostrar = esTerminal ? extraerLexema(nodo.valor) : nodo.valor;

            // Calcular radio según el texto que realmente se mostrará
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
            circle.setStrokeWidth(esRaiz ? 3 : (esTerminal ? 2 : 2.5));
            pane.getChildren().add(circle);

            // Texto (centrado vertical y horizontalmente)
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

        // ─── ScrollPane con el Pane como contenido ───
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
        // Para terminales usar solo el lexema extraido, no el formato "TIPO(lexema)"
        String texto = nodo.hijos.isEmpty() ? extraerLexema(nodo.valor) : nodo.valor;
        double anchoPropio = Math.max(RADIO * 2 + 20, estimarAnchoTexto(texto) + 10);
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
            String texto = extraerLexema(nodo.valor);
            double ancho = estimarAnchoTexto(texto);
            double r = Math.max(RADIO, (ancho + 14) / 2);
            return r * 2 + 10;
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
        return texto.length() * 8;
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
