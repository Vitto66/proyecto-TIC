package uy.edu.um.proytic2026.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** El mapa del inicio tiene los 19 departamentos, con un trazo y la etiqueta adentro del dibujo. */
class UruguayMapTest {

    private final UruguayMap map = new UruguayMap();

    @Test
    void hasTheNineteenDepartments() {
        assertThat(map.shapes()).hasSize(19);
        assertThat(map.shapes()).extracting(UruguayMap.Shape::name)
                .contains("Montevideo", "Paysandú", "Treinta y Tres", "Río Negro", "Rocha");
    }

    @Test
    void everyShapeHasAPathAndALabelInsideTheViewBox() {
        assertThat(map.viewBox()).startsWith("0 0 ");
        assertThat(map.shapes()).allSatisfy(s -> {
            assertThat(s.path()).startsWith("M").endsWith("Z");
            assertThat(s.labelX()).isBetween(0.0, map.width());
            assertThat(s.labelY()).isBetween(0.0, map.height());
        });
    }

    @Test
    void normalize_ignoresAccentsAndCase() {
        assertThat(UruguayMap.normalize(" Paysandú ")).isEqualTo(UruguayMap.normalize("PAYSANDU"));
        assertThat(UruguayMap.normalize("San José")).isEqualTo("san jose");
    }
}
