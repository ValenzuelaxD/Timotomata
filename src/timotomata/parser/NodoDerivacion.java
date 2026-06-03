package timotomata.parser;

import java.util.*;

/**
 * Nodo del árbol de derivación sintáctico (no confundir con el AST).
 * Muestra paso a paso qué producciones de la gramática se usaron
 * durante el análisis, incluyendo todos los no-terminales y terminales.
 *
 * Similar a NodoArbol.java de MatrixCore.
 */
public class NodoDerivacion {
    public String valor;
    public String lexema;  // Solo para nodos terminales: el lexema real del token
    public List<NodoDerivacion> hijos = new ArrayList<>();
    public boolean sintetico = false; // true para nodos gramaticales internos (EXPRESION_SIG, etc.)

    public NodoDerivacion(String valor) {
        this.valor = valor;
    }

    public void agregarHijo(NodoDerivacion hijo) {
        if (hijo != null) {
            hijos.add(hijo);
        }
    }

    public String getValor() { return valor; }
    public List<NodoDerivacion> getHijos() { return hijos; }
}
