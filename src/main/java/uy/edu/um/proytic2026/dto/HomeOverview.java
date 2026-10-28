package uy.edu.um.proytic2026.dto;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/**
 * Lo que muestra el inicio: el mapa con cuántas actividades hay en cada departamento, las
 * actividades de cada uno (el panel al lado del mapa), los tipos de actividad y las más
 * reservadas del mes.
 *
 * @param viewBox      viewBox del SVG del mapa.
 * @param mapWidth     ancho del viewBox, para ubicar las etiquetas en porcentaje.
 * @param mapHeight    alto del viewBox.
 * @param searchTypes  todos los tipos activos, para el buscador.
 * @param panels       por código ISO del departamento, sus mejores actividades (hasta 3).
 * @param featured     las actividades destacadas (hasta 4).
 * @param featuredByBookings true si las destacadas salen de las reservas del mes; false si,
 *                     como todavía no hay reservas, salen de las mejor valoradas.
 */
public record HomeOverview(String viewBox, double mapWidth, double mapHeight,
                           List<MapDepartment> departments, List<TypeCard> types, int totalActivities,
                           List<TypeCard> searchTypes, Map<String, List<HomeActivity>> panels,
                           List<HomeActivity> featured, boolean featuredByBookings,
                           List<UpcomingActivity> upcoming) {

    /** Los departamentos del buscador: los que están cargados en la base, por nombre. */
    public List<MapDepartment> searchDepartments() {
        return departments.stream().filter(d -> d.departmentId() != null)
                .sorted(Comparator.comparing(MapDepartment::name, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    /** El departamento que aparece elegido al entrar: el que tiene más actividades. */
    public String selectedIso() {
        return departments.stream().filter(MapDepartment::linkable)
                .max(Comparator.comparingLong(MapDepartment::activityCount)
                        .thenComparing(MapDepartment::name, Comparator.reverseOrder()))
                .map(MapDepartment::iso).orElse(null);
    }

    public List<HomeActivity> panel(String iso) {
        return panels.getOrDefault(iso, List.of());
    }

    /** Qué paisaje ilustra un tipo de actividad, según su ícono. */
    public static String sceneFor(String typeName) {
        return switch (TypeCard.iconFor(typeName)) {
            case "mountain" -> "sierra";
            case "waves" -> "rio";
            case "wine" -> "vino";
            case "horse", "food" -> "campo";
            case "leaf" -> "palmar";
            case "landmark" -> "costa";
            case "bike" -> "vino";
            default -> "sierra";
        };
    }

    public boolean hasActivities() {
        return totalActivities > 0;
    }

    public long departmentsWithActivities() {
        return departments.stream().filter(MapDepartment::hasActivities).count();
    }

    /** Las etiquetas del mapa: solo los departamentos que tienen actividades. */
    public List<MapDepartment> pins() {
        return departments.stream().filter(MapDepartment::hasActivities).toList();
    }

    /** "12 actividades en 5 departamentos". */
    public String summaryLabel() {
        long deps = departmentsWithActivities();
        return totalActivities + (totalActivities == 1 ? " actividad" : " actividades") + " en "
                + deps + (deps == 1 ? " departamento" : " departamentos");
    }

    /**
     * Un departamento del mapa.
     *
     * @param departmentId id en la tabla department, o null si no está cargado (por ejemplo en H2).
     */
    public record MapDepartment(String iso, String name, String path, Integer departmentId,
                                double labelX, double labelY, double mapWidth, double mapHeight,
                                long activityCount) {

        public boolean hasActivities() {
            return activityCount > 0;
        }

        /** El id del departamento, con el mismo nombre que en las demás opciones (menú principal). */
        public Integer id() {
            return departmentId;
        }

        public boolean linkable() {
            return departmentId != null && hasActivities();
        }

        /** "3 actividades". */
        public String countLabel() {
            return activityCount + (activityCount == 1 ? " actividad" : " actividades");
        }

        /** Posición de la etiqueta sobre el mapa, en porcentaje del ancho y del alto. */
        public String labelStyle() {
            return String.format(Locale.ROOT, "left:%.2f%%;top:%.2f%%",
                    labelX / mapWidth * 100, labelY / mapHeight * 100);
        }
    }

    /**
     * Una tarjeta de "Elegí tu forma de explorar".
     *
     * @param tone   índice de color de la tarjeta (0 a 5), para alternar la paleta.
     */
    public record TypeCard(Integer id, String name, long activityCount, int tone) {

        public String countLabel() {
            if (activityCount == 0) return "Próximamente";
            return activityCount + (activityCount == 1 ? " actividad" : " actividades");
        }

        /** Qué ícono le corresponde según el nombre del tipo. */
        public String icon() {
            return iconFor(name);
        }

        /** El paisaje que lo ilustra (scenes.js). */
        public String scene() {
            return sceneFor(name);
        }

        public static String iconFor(String name) {
            String n = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
            if (n.contains("trek") || n.contains("sender") || n.contains("montan") || n.contains("cerro")) return "mountain";
            if (n.contains("kayak") || n.contains("acuat") || n.contains("nautic") || n.contains("playa")
                    || n.contains("surf") || n.contains("pesca") || n.contains("rio")) return "waves";
            if (n.contains("vino") || n.contains("enoturismo") || n.contains("bodega")) return "wine";
            if (n.contains("gastro") || n.contains("comida") || n.contains("asado")) return "food";
            if (n.contains("histor") || n.contains("cultur") || n.contains("patrimon") || n.contains("museo")) return "landmark";
            if (n.contains("cabalg") || n.contains("caball") || n.contains("rural") || n.contains("estancia")) return "horse";
            if (n.contains("natur") || n.contains("ave") || n.contains("fauna") || n.contains("flora")) return "leaf";
            if (n.contains("bici") || n.contains("ciclo")) return "bike";
            return "compass";
        }
    }
}
