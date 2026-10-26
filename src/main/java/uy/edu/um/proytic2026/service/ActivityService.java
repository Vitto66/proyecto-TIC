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
import uy.edu.um.proytic2026.entity.ActivityEquipment;
import uy.edu.um.proytic2026.entity.ActivityType;
import uy.edu.um.proytic2026.entity.Business;
import uy.edu.um.proytic2026.entity.EquipmentType;
import uy.edu.um.proytic2026.entity.Locality;
import uy.edu.um.proytic2026.entity.ProviderStatus;
import uy.edu.um.proytic2026.exception.ActivityNotFoundException;
import uy.edu.um.proytic2026.exception.ActivityNotPublishedException;
import uy.edu.um.proytic2026.exception.BusinessRequiredException;
import uy.edu.um.proytic2026.exception.InvalidSelectionException;
import uy.edu.um.proytic2026.exception.ProviderNotApprovedException;
import uy.edu.um.proytic2026.repository.ActivityRepository;
import uy.edu.um.proytic2026.repository.ActivityTypeRepository;
import uy.edu.um.proytic2026.repository.BusinessRepository;
import uy.edu.um.proytic2026.repository.EquipmentTypeRepository;
import uy.edu.um.proytic2026.repository.ProviderRepository;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * CU-08 Publicar una actividad y CU-10 Modificarla: reglas de negocio. Darla de baja está en
 * {@link ActivityCancellationService}.
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

    // ---------- CU-10 Modificar una actividad ----------

    /**
     * El formulario con los datos actuales de la actividad.
     *
     * @throws ActivityNotFoundException     no existe o no es del prestador.
     * @throws ActivityNotPublishedException está dada de baja.
     */
    @Transactional(readOnly = true)
    public PublishActivityRequest editForm(Long providerId, Long activityId) {
        Activity a = ownedActive(providerId, activityId);
        Map<Integer, EquipmentSource> equipment = new HashMap<>();
        for (ActivityEquipment e : a.getEquipment()) {
            equipment.put(e.getEquipmentType().getId(),
                    e.isProvidedByProvider() ? EquipmentSource.PROVIDER : EquipmentSource.TOURIST);
        }
        return PublishActivityRequest.builder()
                .name(a.getName())
                .description(a.getDescription())
                .departmentId(a.getLocality().getDepartment().getId())
                .localityId(a.getLocality().getId())
                .durationMinutes(a.getDurationMinutes())
                .price(a.getPrice())
                .minAge(a.getMinAge())
                .bookingCutoffMinutes(a.getBookingCutoffMinutes())
                .activityTypeIds(new java.util.ArrayList<>(a.getTypes().stream().map(ActivityType::getId).toList()))
                .equipment(equipment)
                .build();
    }

    /** El nombre de la actividad, para el título del formulario de modificación. */
    @Transactional(readOnly = true)
    public String activityName(Long providerId, Long activityId) {
        return ownedActive(providerId, activityId).getName();
    }

    /**
     * Las opciones del formulario de modificación: las activas, más los tipos que la actividad ya
     * tiene aunque el administrador los haya desactivado (si no, se perderían al guardar).
     */
    @Transactional(readOnly = true)
    public ActivityFormOptions formOptions(Long providerId, Long activityId) {
        Activity a = ownedActive(providerId, activityId);
        ActivityFormOptions base = formOptions();
        List<Option> types = merge(base.activityTypes(),
                a.getTypes().stream().map(t -> new Option(t.getId(), t.getName())).toList());
        List<Option> equipment = merge(base.equipmentTypes(),
                a.getEquipment().stream().map(e -> new Option(e.getEquipmentType().getId(),
                        e.getEquipmentType().getName())).toList());
        return new ActivityFormOptions(base.locations(), types, equipment);
    }

    /**
     * Pasos 3 y 4: valida los cambios y actualiza la actividad. 3a: si cambia el precio, rige solo
     * para reservas nuevas, porque cada reserva guarda su precio total al crearse.
     *
     * @throws ActivityNotFoundException     no existe o no es del prestador.
     * @throws ActivityNotPublishedException está dada de baja.
     * @throws InvalidSelectionException     un tipo o equipamiento no existe o está inactivo.
     */
    @Transactional
    public ActivitySummary update(Long providerId, Long activityId, PublishActivityRequest request) {
        Activity activity = ownedActive(providerId, activityId);
        Locality locality = locationService.locality(request.getDepartmentId(), request.getLocalityId());

        // Se aceptan los activos y los que la actividad ya tenía
        Set<Integer> currentTypes = activity.getTypes().stream().map(ActivityType::getId).collect(Collectors.toSet());
        List<ActivityType> types = activityTypes(request.getActivityTypeIds(), currentTypes);
        Map<Integer, EquipmentSource> chosen = chosenEquipment(request);
        Set<Integer> currentEquipment = activity.getEquipment().stream()
                .map(e -> e.getEquipmentType().getId()).collect(Collectors.toSet());
        List<EquipmentType> equipmentTypes = equipmentTypes(chosen.keySet(), currentEquipment);

        activity.setLocality(locality);
        activity.setName(request.getName().trim());
        activity.setDescription(request.getDescription().trim());
        activity.setDurationMinutes(request.getDurationMinutes());
        activity.setPrice(request.getPrice());
        activity.setMinAge(request.getMinAge());
        activity.setBookingCutoffMinutes(request.getBookingCutoffMinutes());

        activity.getTypes().retainAll(types);
        activity.getTypes().addAll(types);

        // El equipamiento se actualiza en el lugar: se sacan los que ya no van, se cambia quién pone
        // los que siguen y se agregan los nuevos (borrar y volver a crear chocaría con la clave)
        activity.getEquipment().removeIf(e -> !chosen.containsKey(e.getEquipmentType().getId()));
        for (ActivityEquipment e : activity.getEquipment()) {
            e.setProvidedByProvider(chosen.get(e.getEquipmentType().getId()) == EquipmentSource.PROVIDER);
        }
        Set<Integer> kept = activity.getEquipment().stream().map(e -> e.getEquipmentType().getId())
                .collect(Collectors.toSet());
        equipmentTypes.stream()
                .filter(t -> !kept.contains(t.getId()))
                .sorted(Comparator.comparing(EquipmentType::getName))
                .forEach(t -> activity.addEquipment(t, chosen.get(t.getId()) == EquipmentSource.PROVIDER));

        return ActivitySummary.from(activity);
    }

    private Activity ownedActive(Long providerId, Long activityId) {
        Activity activity = activityRepository.findById(activityId)
                .filter(a -> a.getBusiness().getProvider().getId().equals(providerId))
                .orElseThrow(ActivityNotFoundException::new);
        if (!activity.isActive()) {
            throw new ActivityNotPublishedException();
        }
        return activity;
    }

    private static List<Option> merge(List<Option> active, List<Option> current) {
        Set<Integer> ids = active.stream().map(Option::id).collect(Collectors.toSet());
        return Stream.concat(active.stream(), current.stream().filter(o -> !ids.contains(o.id())))
                .sorted(Comparator.comparing(Option::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static Map<Integer, EquipmentSource> chosenEquipment(PublishActivityRequest request) {
        return request.getEquipment() == null ? Map.of()
                : request.getEquipment().entrySet().stream()
                        .filter(e -> e.getKey() != null && e.getValue() != null)
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Business requireBusiness(Long providerId) {
        providerRepository.findById(providerId)
                .filter(p -> p.getStatus() == ProviderStatus.APPROVED)
                .orElseThrow(ProviderNotApprovedException::new);
        return businessRepository.findByProviderId(providerId).orElseThrow(BusinessRequiredException::new);
    }

    private List<ActivityType> activeActivityTypes(List<Integer> ids) {
        return activityTypes(ids, Set.of());
    }

    /** Los tipos elegidos: tienen que existir y estar activos, salvo los que la actividad ya tenía. */
    private List<ActivityType> activityTypes(List<Integer> ids, Collection<Integer> alreadyAssigned) {
        Set<Integer> wanted = ids == null ? Set.of()
                : ids.stream().filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        if (wanted.isEmpty()) {
            throw new InvalidSelectionException("activityTypeIds", "elegí al menos un tipo de actividad");
        }
        List<ActivityType> found = activityTypeRepository.findAllById(wanted);
        if (found.size() != wanted.size()
                || found.stream().anyMatch(t -> !t.isActive() && !alreadyAssigned.contains(t.getId()))) {
            throw new InvalidSelectionException("activityTypeIds", "elegí tipos de actividad de la lista");
        }
        return found;
    }

    private List<EquipmentType> activeEquipmentTypes(Set<Integer> ids) {
        return equipmentTypes(ids, Set.of());
    }

    private List<EquipmentType> equipmentTypes(Set<Integer> ids, Collection<Integer> alreadyAssigned) {
        if (ids.isEmpty()) return List.of();
        List<EquipmentType> found = equipmentTypeRepository.findAllById(ids);
        if (found.size() != ids.size()
                || found.stream().anyMatch(t -> !t.isActive() && !alreadyAssigned.contains(t.getId()))) {
            throw new InvalidSelectionException("equipment", "elegí equipamiento de la lista");
        }
        return found;
    }
}
