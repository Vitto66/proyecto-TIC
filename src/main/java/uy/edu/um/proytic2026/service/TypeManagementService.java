package uy.edu.um.proytic2026.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.proytic2026.dto.TypeRow;
import uy.edu.um.proytic2026.entity.ActivityType;
import uy.edu.um.proytic2026.entity.EquipmentType;
import uy.edu.um.proytic2026.entity.TypeCategory;
import uy.edu.um.proytic2026.exception.TypeInUseException;
import uy.edu.um.proytic2026.exception.TypeNameTakenException;
import uy.edu.um.proytic2026.exception.TypeNotFoundException;
import uy.edu.um.proytic2026.repository.ActivityRepository;
import uy.edu.um.proytic2026.repository.ActivityTypeRepository;
import uy.edu.um.proytic2026.repository.EquipmentTypeRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CU-13 Gestionar tipos de actividad y de equipamiento.
 * <p>
 * El administrador da de alta, consulta, modifica y elimina tipos de las dos listas. Un tipo
 * que usan actividades no se puede eliminar (3a): se desactiva, y así deja de ofrecerse al
 * publicar actividades nuevas (CU-08) sin romper las que ya lo tienen. No puede haber dos
 * tipos con el mismo nombre en una lista, sin distinguir mayúsculas (3b).
 */
@Service
@RequiredArgsConstructor
public class TypeManagementService {

    static final int MAX_NAME_LENGTH = 100;

    private final ActivityTypeRepository activityTypeRepository;
    private final EquipmentTypeRepository equipmentTypeRepository;
    private final ActivityRepository activityRepository;

    /** Paso 3: consulta. Todos los tipos de la lista, ordenados por nombre, con su uso. */
    @Transactional(readOnly = true)
    public List<TypeRow> list(TypeCategory category) {
        Map<Integer, Long> usage = usageByType(category);
        return switch (category) {
            case ACTIVITY -> activityTypeRepository.findAllByOrderByNameAsc().stream()
                    .map(t -> new TypeRow(t.getId(), t.getName(), t.isActive(), usage.getOrDefault(t.getId(), 0L)))
                    .toList();
            case EQUIPMENT -> equipmentTypeRepository.findAllByOrderByNameAsc().stream()
                    .map(t -> new TypeRow(t.getId(), t.getName(), t.isActive(), usage.getOrDefault(t.getId(), 0L)))
                    .toList();
        };
    }

    /** Paso 3: alta. El tipo nuevo queda activo y disponible para todos los prestadores. */
    @Transactional
    public String create(TypeCategory category, String rawName) {
        String name = normalize(rawName);
        if (nameTaken(category, name, null)) {
            throw new TypeNameTakenException(name);
        }
        switch (category) {
            case ACTIVITY -> activityTypeRepository.save(ActivityType.builder().name(name).build());
            case EQUIPMENT -> equipmentTypeRepository.save(EquipmentType.builder().name(name).build());
        }
        return name;
    }

    /** Paso 3: modificación del nombre. Las actividades que lo usan pasan a mostrar el nombre nuevo. */
    @Transactional
    public String rename(TypeCategory category, Integer id, String rawName) {
        String name = normalize(rawName);
        if (nameTaken(category, name, id)) {
            throw new TypeNameTakenException(name);
        }
        switch (category) {
            case ACTIVITY -> findActivityType(id).setName(name);
            case EQUIPMENT -> findEquipmentType(id).setName(name);
        }
        return name;
    }

    /** Paso 3: eliminación. 3a: si lo usan actividades, no se elimina. Devuelve el nombre eliminado. */
    @Transactional
    public String delete(TypeCategory category, Integer id) {
        return switch (category) {
            case ACTIVITY -> {
                ActivityType type = findActivityType(id);
                long usage = activityRepository.countUsingActivityType(id);
                if (usage > 0) {
                    throw new TypeInUseException(id, type.getName(), usage);
                }
                activityTypeRepository.delete(type);
                yield type.getName();
            }
            case EQUIPMENT -> {
                EquipmentType type = findEquipmentType(id);
                long usage = activityRepository.countUsingEquipmentType(id);
                if (usage > 0) {
                    throw new TypeInUseException(id, type.getName(), usage);
                }
                equipmentTypeRepository.delete(type);
                yield type.getName();
            }
        };
    }

    /**
     * 3a: activar o desactivar. Uno inactivo no se ofrece al publicar actividades nuevas, pero
     * las actividades que ya lo tienen lo conservan. Devuelve el nombre del tipo.
     */
    @Transactional
    public String setActive(TypeCategory category, Integer id, boolean active) {
        return switch (category) {
            case ACTIVITY -> {
                ActivityType type = findActivityType(id);
                type.setActive(active);
                yield type.getName();
            }
            case EQUIPMENT -> {
                EquipmentType type = findEquipmentType(id);
                type.setActive(active);
                yield type.getName();
            }
        };
    }

    /** Saca los espacios de los extremos y deja uno solo entre palabras: "  Kayak   doble " → "Kayak doble". */
    static String normalize(String rawName) {
        String name = rawName == null ? "" : rawName.strip().replaceAll("\\s+", " ");
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("El nombre debe tener entre 1 y " + MAX_NAME_LENGTH + " caracteres");
        }
        return name;
    }

    private boolean nameTaken(TypeCategory category, String name, Integer exceptId) {
        return switch (category) {
            case ACTIVITY -> exceptId == null
                    ? activityTypeRepository.existsByNameIgnoreCase(name)
                    : activityTypeRepository.existsByNameIgnoreCaseAndIdNot(name, exceptId);
            case EQUIPMENT -> exceptId == null
                    ? equipmentTypeRepository.existsByNameIgnoreCase(name)
                    : equipmentTypeRepository.existsByNameIgnoreCaseAndIdNot(name, exceptId);
        };
    }

    private Map<Integer, Long> usageByType(TypeCategory category) {
        List<Object[]> rows = category == TypeCategory.ACTIVITY
                ? activityRepository.countByActivityType()
                : activityRepository.countByEquipmentType();
        Map<Integer, Long> usage = new HashMap<>();
        for (Object[] row : rows) {
            usage.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue());
        }
        return usage;
    }

    private ActivityType findActivityType(Integer id) {
        return activityTypeRepository.findById(id).orElseThrow(() -> new TypeNotFoundException(id));
    }

    private EquipmentType findEquipmentType(Integer id) {
        return equipmentTypeRepository.findById(id).orElseThrow(() -> new TypeNotFoundException(id));
    }
}
