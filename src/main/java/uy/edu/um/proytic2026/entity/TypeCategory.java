package uy.edu.um.proytic2026.entity;

/**
 * Las dos listas de datos generales que mantiene el administrador (CU-13):
 * tipos de actividad y tipos de equipamiento.
 */
public enum TypeCategory {

    ACTIVITY("activity", "Tipos de actividad", "tipo de actividad"),
    EQUIPMENT("equipment", "Tipos de equipamiento", "tipo de equipamiento");

    private final String slug;
    private final String title;
    private final String singular;

    TypeCategory(String slug, String title, String singular) {
        this.slug = slug;
        this.title = title;
        this.singular = singular;
    }

    /** Lo que va en la URL: /admin/types/activity o /admin/types/equipment. */
    public String slug() {
        return slug;
    }

    public String title() {
        return title;
    }

    /** "tipo de actividad" / "tipo de equipamiento", para armar mensajes. */
    public String singular() {
        return singular;
    }

    /** null si el slug no corresponde a ninguna lista. */
    public static TypeCategory fromSlug(String slug) {
        for (TypeCategory category : values()) {
            if (category.slug.equals(slug)) {
                return category;
            }
        }
        return null;
    }
}
