package uy.edu.um.proytic2026.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.proytic2026.dto.HomeOverview;
import uy.edu.um.proytic2026.dto.HomeOverview.MapDepartment;
import uy.edu.um.proytic2026.dto.HomeOverview.TypeCard;
import uy.edu.um.proytic2026.entity.Activity;
import uy.edu.um.proytic2026.entity.ActivityType;
import uy.edu.um.proytic2026.entity.Department;
import uy.edu.um.proytic2026.repository.ActivityRepository;
import uy.edu.um.proytic2026.repository.ActivityTypeRepository;
import uy.edu.um.proytic2026.repository.DepartmentRepository;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Datos del inicio: en qué departamentos hay actividades publicadas (para el mapa) y los tipos
 * de actividad con más propuestas (para las tarjetas). Cada etiqueta del mapa y cada tarjeta
 * llevan a la vidriera (CU-05) con el filtro correspondiente ya aplicado.
 */
@Service
@RequiredArgsConstructor
public class HomeService {

    /** Cuántas tarjetas de tipos se muestran. */
    static final int MAX_TYPE_CARDS = 6;

    private final ActivityRepository activityRepository;
    private final DepartmentRepository departmentRepository;
    private final ActivityTypeRepository activityTypeRepository;
    private final UruguayMap uruguayMap;

    @Transactional(readOnly = true)
    public HomeOverview overview() {
        List<Activity> published = activityRepository.findAllPublished();

        Map<String, Long> byDepartment = published.stream().collect(Collectors.groupingBy(
                a -> UruguayMap.normalize(a.getLocality().getDepartment().getName()), Collectors.counting()));
        Map<String, Integer> departmentIds = departmentRepository.findAll().stream()
                .collect(Collectors.toMap(d -> UruguayMap.normalize(d.getName()), Department::getId, (a, b) -> a));

        List<MapDepartment> departments = uruguayMap.shapes().stream()
                .map(s -> new MapDepartment(s.iso(), s.name(), s.path(), departmentIds.get(s.key()),
                        s.labelX(), s.labelY(), uruguayMap.width(), uruguayMap.height(),
                        byDepartment.getOrDefault(s.key(), 0L)))
                .toList();

        return new HomeOverview(uruguayMap.viewBox(), uruguayMap.width(), uruguayMap.height(),
                departments, typeCards(published), published.size());
    }

    /** Los tipos activos con más actividades primero; a igual cantidad, por nombre. */
    private List<TypeCard> typeCards(List<Activity> published) {
        Map<Integer, Long> byType = new HashMap<>();
        for (Activity a : published) {
            for (ActivityType t : a.getTypes()) {
                byType.merge(t.getId(), 1L, Long::sum);
            }
        }
        List<ActivityType> types = activityTypeRepository.findByActiveTrueOrderByNameAsc().stream()
                .sorted(Comparator.comparing((ActivityType t) -> byType.getOrDefault(t.getId(), 0L)).reversed()
                        .thenComparing(ActivityType::getName, String.CASE_INSENSITIVE_ORDER))
                .limit(MAX_TYPE_CARDS)
                .toList();
        // Cada tarjeta toma un color distinto de la paleta, en orden
        return IntStream.range(0, types.size())
                .mapToObj(i -> new TypeCard(types.get(i).getId(), types.get(i).getName(),
                        byType.getOrDefault(types.get(i).getId(), 0L), i % 6))
                .toList();
    }
}
