package uy.edu.um.proytic2026.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * La forma de los 19 departamentos para el mapa del inicio. Los trazos salen de Natural Earth
 * (dominio público) y están en src/main/resources/map/uruguay-departments.txt; se leen una vez
 * al arrancar.
 */
@Component
public class UruguayMap {

    static final String RESOURCE = "map/uruguay-departments.txt";

    /** Un departamento: su trazo SVG y dónde va la etiqueta, en unidades del viewBox. */
    public record Shape(String iso, String name, double labelX, double labelY, String path) {

        /** Clave para cruzarlo con la tabla department sin importar tildes ni mayúsculas. */
        public String key() {
            return normalize(name);
        }
    }

    private final String viewBox;
    private final double width;
    private final double height;
    private final List<Shape> shapes;

    public UruguayMap() {
        String box = null;
        List<Shape> list = new ArrayList<>();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(
                new ClassPathResource(RESOURCE).getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith("# viewBox|")) {
                    box = line.substring("# viewBox|".length()).trim();
                } else if (!line.isBlank() && !line.startsWith("#")) {
                    String[] p = line.split("\\|", 5);
                    list.add(new Shape(p[0], p[1], Double.parseDouble(p[2]), Double.parseDouble(p[3]), p[4]));
                }
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("No se pudo leer " + RESOURCE, ex);
        }
        if (box == null) {
            throw new IllegalStateException(RESOURCE + " no tiene la línea del viewBox");
        }
        String[] parts = box.split("\\s+");
        this.viewBox = box;
        this.width = Double.parseDouble(parts[2]);
        this.height = Double.parseDouble(parts[3]);
        this.shapes = List.copyOf(list);
    }

    public String viewBox() {
        return viewBox;
    }

    public double width() {
        return width;
    }

    public double height() {
        return height;
    }

    public List<Shape> shapes() {
        return shapes;
    }

    /** "Paysandú" y "PAYSANDU" dan lo mismo. */
    public static String normalize(String name) {
        if (name == null) return "";
        String noAccents = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return noAccents.trim().toLowerCase(Locale.ROOT);
    }
}
