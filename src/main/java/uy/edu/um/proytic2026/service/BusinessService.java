package uy.edu.um.proytic2026.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.proytic2026.dto.BusinessSummary;
import uy.edu.um.proytic2026.dto.LocationOptions;
import uy.edu.um.proytic2026.dto.LocationOptions.DepartmentOption;
import uy.edu.um.proytic2026.dto.LocationOptions.LocalityOption;
import uy.edu.um.proytic2026.dto.RegisterBusinessRequest;
import uy.edu.um.proytic2026.entity.Business;
import uy.edu.um.proytic2026.entity.Locality;
import uy.edu.um.proytic2026.entity.PersonType;
import uy.edu.um.proytic2026.entity.Provider;
import uy.edu.um.proytic2026.entity.ProviderStatus;
import uy.edu.um.proytic2026.exception.BusinessAlreadyRegisteredException;
import uy.edu.um.proytic2026.exception.InvalidLocationException;
import uy.edu.um.proytic2026.exception.ProviderNotApprovedException;
import uy.edu.um.proytic2026.repository.BusinessRepository;
import uy.edu.um.proytic2026.repository.DepartmentRepository;
import uy.edu.um.proytic2026.repository.LocalityRepository;
import uy.edu.um.proytic2026.repository.ProviderRepository;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * CU-07 Registrar el emprendimiento: reglas de negocio.
 * <p>
 * Precondición: el prestador está APROBADO (CU-03). Cada prestador tiene un único
 * emprendimiento (1a); la base también lo garantiza con UNIQUE (provider_id).
 */
@Service
@RequiredArgsConstructor
public class BusinessService {

    private final BusinessRepository businessRepository;
    private final ProviderRepository providerRepository;
    private final DepartmentRepository departmentRepository;
    private final LocalityRepository localityRepository;

    /** El emprendimiento del prestador, si ya lo registró. */
    @Transactional(readOnly = true)
    public Optional<BusinessSummary> findForProvider(Long providerId) {
        return businessRepository.findByProviderId(providerId).map(BusinessSummary::from);
    }

    /** Si el prestador puede registrar su emprendimiento: tiene que estar APROBADO. */
    @Transactional(readOnly = true)
    public boolean isApproved(Long providerId) {
        return providerRepository.findById(providerId)
                .map(p -> p.getStatus() == ProviderStatus.APPROVED)
                .orElse(false);
    }

    /**
     * Valores iniciales del formulario. Paso 2: si el prestador se registró como persona
     * jurídica, el nombre del emprendimiento aparece precargado. Como ayuda, el correo y el
     * teléfono de contacto arrancan con los de la cuenta; se pueden cambiar.
     */
    @Transactional(readOnly = true)
    public RegisterBusinessRequest formDefaults(Long providerId) {
        Provider provider = providerRepository.findById(providerId).orElseThrow(ProviderNotApprovedException::new);
        RegisterBusinessRequest form = new RegisterBusinessRequest();
        if (provider.getPersonType() == PersonType.LEGAL) {
            form.setName(provider.getBusinessName());
        }
        form.setEmail(provider.getUser().getEmail());
        // Solo los números: una cuenta vieja puede tener el teléfono guardado con espacios
        String phone = provider.getUser().getPhone();
        form.setPhone(phone == null ? null : phone.replaceAll("\\D", ""));
        return form;
    }

    /** Departamentos y localidades para el selector de ubicación, ordenados por nombre. */
    @Transactional(readOnly = true)
    public LocationOptions locationOptions() {
        return new LocationOptions(
                departmentRepository.findAllByOrderByNameAsc().stream()
                        .map(d -> new DepartmentOption(d.getId(), d.getName()))
                        .toList(),
                localityRepository.findAllWithDepartment().stream()
                        .map(l -> new LocalityOption(l.getId(), l.getDepartment().getId(), l.getName()))
                        .toList());
    }

    /**
     * Pasos 3 y 4: valida y registra el emprendimiento asociado al prestador.
     * Se espera que el request ya haya pasado por las validaciones de las anotaciones (3a).
     *
     * @throws ProviderNotApprovedException       el prestador no está APROBADO.
     * @throws BusinessAlreadyRegisteredException 1a: ya tiene su emprendimiento registrado.
     * @throws InvalidLocationException           la localidad no existe o no es de ese departamento.
     */
    @Transactional
    public BusinessSummary register(Long providerId, RegisterBusinessRequest request) {
        Provider provider = providerRepository.findById(providerId)
                .filter(p -> p.getStatus() == ProviderStatus.APPROVED)
                .orElseThrow(ProviderNotApprovedException::new);

        if (businessRepository.existsByProviderId(providerId)) {
            throw new BusinessAlreadyRegisteredException();
        }

        Locality locality = localityRepository.findById(request.getLocalityId())
                .orElseThrow(InvalidLocationException::new);
        if (!Objects.equals(locality.getDepartment().getId(), request.getDepartmentId())) {
            throw new InvalidLocationException();
        }

        Business business = Business.builder()
                .provider(provider)
                .locality(locality)
                .name(request.getName().trim())
                .description(request.getDescription().trim())
                .address(request.getAddress().trim())
                .phone(request.getPhone().trim())
                .email(request.getEmail().trim().toLowerCase(Locale.ROOT))
                .build();
        try {
            // saveAndFlush: si dos pedidos llegan a la vez, la restricción UNIQUE (provider_id)
            // salta acá y se informa como 1a en vez de como un error de la base.
            business = businessRepository.saveAndFlush(business);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessAlreadyRegisteredException();
        }
        return BusinessSummary.from(business);
    }
}
