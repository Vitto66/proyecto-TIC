package uy.edu.um.proytic2026.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.proytic2026.dto.RegisterProviderRequest;
import uy.edu.um.proytic2026.dto.RegisterTouristRequest;
import uy.edu.um.proytic2026.dto.UserResponse;
import uy.edu.um.proytic2026.entity.PersonType;
import uy.edu.um.proytic2026.entity.Provider;
import uy.edu.um.proytic2026.entity.ProviderStatus;
import uy.edu.um.proytic2026.entity.Tourist;
import uy.edu.um.proytic2026.entity.User;
import uy.edu.um.proytic2026.exception.DocumentAlreadyRegisteredException;
import uy.edu.um.proytic2026.exception.EmailAlreadyRegisteredException;
import uy.edu.um.proytic2026.exception.InvalidCredentialsException;
import uy.edu.um.proytic2026.exception.NationalIdAlreadyRegisteredException;
import uy.edu.um.proytic2026.repository.ProviderRepository;
import uy.edu.um.proytic2026.repository.TouristRepository;
import uy.edu.um.proytic2026.repository.UserRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final TouristRepository touristRepository;
    private final ProviderRepository providerRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * CU-01 Registrarse como turista.
     * <p>
     * Si el correo es nuevo, se crea la cuenta y el perfil de turista.
     * Si el correo ya existe como prestador, se le agrega el perfil de turista
     * a la misma cuenta, siempre que la contraseña coincida.
     */
    @Transactional
    public UserResponse registerTourist(RegisterTouristRequest request) {
        String email = normalizeEmail(request.getEmail());

        if (touristRepository.existsByNationalId(request.getNationalId())) {
            throw new NationalIdAlreadyRegisteredException(request.getNationalId());
        }

        User user = userRepository.findByEmailIgnoreCase(email)
                .map(existing -> {
                    if (existing.getTourist() != null) {
                        throw new EmailAlreadyRegisteredException(existing.getEmail(), "turista");
                    }
                    return checkPassword(existing, request.getPassword());
                })
                .orElseGet(() -> createAccount(email, request.getPassword(), blankToNull(trim(request.getPhone()))));

        Tourist tourist = touristRepository.save(Tourist.builder()
                .user(user)
                .firstName(trim(request.getFirstName()))
                .lastName(trim(request.getLastName()))
                .nationalId(request.getNationalId())
                .build());
        user.setTourist(tourist);

        return toResponse(user);
    }

    /**
     * CU-02 Registrarse como prestador de servicio.
     * <p>
     * El prestador queda en estado PENDING hasta que un administrador lo apruebe (CU-03).
     * Igual que en CU-01, si el correo ya existe como turista se le agrega el perfil de
     * prestador a la misma cuenta, siempre que la contraseña coincida.
     * Se espera que el request ya haya pasado por ProviderRegistrationValidator.
     */
    @Transactional
    public UserResponse registerProvider(RegisterProviderRequest request) {
        String email = normalizeEmail(request.getEmail());
        PersonType type = request.getPersonType();

        // 5a. Correo ya usado como prestador
        Optional<User> existing = userRepository.findByEmailIgnoreCase(email);
        if (existing.isPresent() && existing.get().getProvider() != null) {
            throw new EmailAlreadyRegisteredException(existing.get().getEmail(), "prestador");
        }

        String document = trim(request.getDocument());
        String rut = trim(request.getRut());
        if (type == PersonType.PHYSICAL && providerRepository.existsByDocument(document)) {
            throw new DocumentAlreadyRegisteredException("document",
                    "Ya hay un prestador registrado con el documento " + document);
        }
        if (type == PersonType.LEGAL && providerRepository.existsByRut(rut)) {
            throw new DocumentAlreadyRegisteredException("rut",
                    "Ya hay un prestador registrado con el RUT " + rut);
        }

        User user = existing
                .map(u -> checkPassword(u, request.getPassword()))
                .orElseGet(() -> createAccount(email, request.getPassword(), null));

        Provider.ProviderBuilder provider = Provider.builder()
                .user(user)
                .personType(type)
                .status(ProviderStatus.PENDING)
                .address(trim(request.getAddress()));
        // Solo se guardan los datos del tipo de persona elegido
        if (type == PersonType.PHYSICAL) {
            provider.firstName(trim(request.getFirstName()))
                    .lastName(trim(request.getLastName()))
                    .document(document);
        } else {
            provider.rut(rut)
                    .legalName(trim(request.getLegalName()))
                    .businessName(trim(request.getBusinessName()));
        }

        user.setProvider(providerRepository.save(provider.build()));
        return toResponse(user);
    }

    private User createAccount(String email, String rawPassword, String phone) {
        return userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .phone(phone)
                .build());
    }

    /** Para sumar un perfil a una cuenta existente hay que demostrar que es tuya. */
    private User checkPassword(User existing, String rawPassword) {
        if (!passwordEncoder.matches(rawPassword, existing.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return existing;
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    /** El formulario web manda "" en los campos opcionales que quedan vacíos. */
    private static String blankToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private UserResponse toResponse(User user) {
        List<String> roles = new ArrayList<>();
        if (user.getTourist() != null) roles.add("TOURIST");
        if (user.getProvider() != null) roles.add("PROVIDER");
        if (user.getAdmin() != null) roles.add("ADMIN");

        Tourist tourist = user.getTourist();
        Provider provider = user.getProvider();
        String firstName = tourist != null ? tourist.getFirstName() : provider != null ? provider.getFirstName() : null;
        String lastName = tourist != null ? tourist.getLastName() : provider != null ? provider.getLastName() : null;

        return new UserResponse(
                user.getId(),
                user.getEmail(),
                firstName,
                lastName,
                roles,
                provider != null ? provider.getStatus().name() : null);
    }
}
