package uy.edu.um.proytic2026.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.proytic2026.dto.ActivityFormOptions;
import uy.edu.um.proytic2026.dto.ActivityFormOptions.Option;
import uy.edu.um.proytic2026.dto.ActivitySummary;
import uy.edu.um.proytic2026.dto.EquipmentSource;
import uy.edu.um.proytic2026.dto.PublishActivityRequest;
import uy.edu.um.proytic2026.entity.Activity;
import uy.edu.um.proytic2026.entity.ActivityType;
import uy.edu.um.proytic2026.entity.Business;
import uy.edu.um.proytic2026.entity.EquipmentType;
import uy.edu.um.proytic2026.entity.Locality;
import uy.edu.um.proytic2026.entity.ProviderStatus;
import uy.edu.um.proytic2026.exception.BusinessRequiredException;
import uy.edu.um.proytic2026.exception.InvalidSelectionException;
import uy.edu.um.proytic2026.exception.ProviderNotApprovedException;
import uy.edu.um.proytic2026.repository.ActivityRepository;
import uy.edu.um.proytic2026.repository.ActivityTypeRepository;
import uy.edu.um.proytic2026.repository.BusinessRepository;
import uy.edu.um.proytic2026.repository.EquipmentTypeRepository;
import uy.edu.um.proytic2026.repository.ProviderRepository;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * CU-08 Publicar una actividad: reglas de negocio.
 * <p>
 * Precondición: el prestador está APROBADO y tiene su emprendimiento registrado (CU-07).
 * La actividad queda publicada (active = true) y todavía sin fechas: esas se cargan en CU-09.
 */
@Service
@RequiredArgsConstructor
public class ActivityService {

    /** Si el prestador puede gestionar actividades y, si no, por qué. */
    public enum Access {
        /** Su cuenta todavía no está aprobada. */
        NOT_APPROVED,
        /** Está aprobado pero no registró su emprendimiento. */
        NO_BUSINESS,
        ALLOWED
    }

    private final ActivityRepository activityRepository;
    private final ActivityTypeRepository activityTypeRepository;
    private final EquipmentTypeRepository equipmentTypeRepository;
    private final BusinessRepository businessRepository;
    private final ProviderRepository providerRepository;
    private final LocationService locationService;

    @Transactional(readOnly = true)
    public Access access(Long providerId) {
        boolean approved = providerRepository.findById(providerId)
                .map(p -> p.getStatus() == ProviderStatus.APPROVED)
                .orElse(false);
        if (!approved) return Access.NOT_APPROVED;
        return businessRepository.existsByProviderId(providerId) ? Access.ALLOWED : Access.NO_BUSINESS;
    }

    /** Paso 1: "Mis actividades", las del emprendimiento del prestador, por nombre. */
    @Transactional(readOnly = true)
    public List<ActivitySummary> myActivities(Long providerId) {
        return activityRepository.findAllByProviderId(providerId).stream()
                .map(ActivitySummary::from)
                .toList();
    }

    /** Lo que ofrece el formulario: ubicaciones y los tipos activos definidos por el administrador. */
    @Transactional(readOnly = true)
    public ActivityFormOptions formOptions() {
        return new ActivityFormOptions(
                locationService.options(),
                activityTypeRepository.findByActiveTrueOrderByNameAsc().stream()
                        .map(t -> new Option(t.getId(), t.getName())).toList(),
                equipmentTypeRepository.findByActiveTrueOrderByNameAsc().stream()
                        .map(t -> new Option(t.getId(), t.getName())).toList());
    }

    /**
     * Valores iniciales del formulario: la ubicación arranca con la del emprendimiento y los
     * minutos de cierre en 0 (se puede reservar hasta la hora de comienzo).
     */
    @Transactional(readOnly = true)
    public PublishActivityRequest formDefaults(Long providerId) {
        Business business = requireBusiness(providerId);
        PublishActivityRequest form = new PublishActivityRequest();
        form.setDepartmentId(business.getLocality().getDepartment().getId());
        form.setLocalityId(business.getLocality().getId());
        form.setMinAge(0);
        form.setBookingCutoffMinutes(0);
        return form;
    }

    /**
     * Pasos 6 y 7: valida y registra la actividad asociada al emprendimiento, ya publicada.
     * Se espera que el request ya haya pasado por las validaciones de las anotaciones (6a).
     *
     * @throws ProviderNotApprovedException el prestador no está APROBADO.
     * @throws BusinessRequiredException    no registró su emprendimiento.
     * @throws InvalidSelectionException    un tipo o equipamiento no existe o está inactivo.
     */
    @Transactional
    public ActivitySummary publish(Long providerId, PublishActivityRequest request) {
        Business business = requireBusiness(providerId);
        Locality locality = locationService.locality(request.getDepartmentId(), request.getLocalityId());

        Activity activity = Activity.builder()
                .business(business)
                .locality(locality)
                .name(request.getName().trim())
                .description(request.getDescription().trim())
                .durationMinutes(request.getDurationMinutes())
                .price(request.getPrice())
                .minAge(request.getMinAge())
                .bookingCutoffMinutes(request.getBookingCutoffMinutes())
                .active(true)
                .build();

        // Paso 3: uno o más tipos, todos de la lista activa del administrador
        activity.getTypes().addAll(activeActivityTypes(request.getActivityTypeIds()));

        // Paso 4: cada equipamiento con quién lo pone
        Map<Integer, EquipmentSource> chosen = request.getEquipment() == null ? Map.of()
                : request.getEquipment().entrySet().stream()
                        .filter(e -> e.getKey() != null && e.getValue() != null)
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        activeEquipmentTypes(chosen.keySet()).stream()
                .sorted(Comparator.comparing(EquipmentType::getName))
                .forEach(type -> activity.addEquipment(type, chosen.get(type.getId()) == EquipmentSource.PROVIDER));

        return ActivitySummary.from(activityRepository.save(activity));
    }

    private Business requireBusiness(Long providerId) {
        providerRepository.findById(providerId)
                .filter(p -> p.getStatus() == ProviderStatus.APPROVED)
                .orElseThrow(ProviderNotApprovedException::new);
        return businessRepository.findByProviderId(providerId).orElseThrow(BusinessRequiredException::new);
    }

    private List<ActivityType> activeActivityTypes(List<Integer> ids) {
        Set<Integer> wanted = ids == null ? Set.of()
                : ids.stream().filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        if (wanted.isEmpty()) {
            throw new InvalidSelectionException("activityTypeIds", "elegí al menos un tipo de actividad");
        }
        List<ActivityType> found = activityTypeRepository.findAllById(wanted);
        if (found.size() != wanted.size() || found.stream().anyMatch(t -> !t.isActive())) {
            throw new InvalidSelectionException("activityTypeIds", "elegí tipos de actividad de la lista");
        }
        return found;
    }

    private List<EquipmentType> activeEquipmentTypes(Set<Integer> ids) {
        if (ids.isEmpty()) return List.of();
        List<EquipmentType> found = equipmentTypeRepository.findAllById(ids);
        if (found.size() != ids.size() || found.stream().anyMatch(t -> !t.isActive())) {
            throw new InvalidSelectionException("equipment", "elegí equipamiento de la lista");
        }
        return found;
    }
}
