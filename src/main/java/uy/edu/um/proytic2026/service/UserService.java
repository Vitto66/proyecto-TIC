package uy.edu.um.proytic2026.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.proytic2026.dto.RegisterTouristRequest;
import uy.edu.um.proytic2026.dto.UserResponse;
import uy.edu.um.proytic2026.entity.Tourist;
import uy.edu.um.proytic2026.entity.User;
import uy.edu.um.proytic2026.exception.EmailAlreadyRegisteredException;
import uy.edu.um.proytic2026.exception.InvalidCredentialsException;
import uy.edu.um.proytic2026.exception.NationalIdAlreadyRegisteredException;
import uy.edu.um.proytic2026.repository.TouristRepository;
import uy.edu.um.proytic2026.repository.UserRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final TouristRepository touristRepository;
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
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        if (touristRepository.existsByNationalId(request.nationalId())) {
            throw new NationalIdAlreadyRegisteredException(request.nationalId());
        }

        User user = userRepository.findByEmailIgnoreCase(email)
                .map(existing -> reuseExistingAccount(existing, request.password()))
                .orElseGet(() -> userRepository.save(User.builder()
                        .email(email)
                        .passwordHash(passwordEncoder.encode(request.password()))
                        .phone(request.phone())
                        .build()));

        Tourist tourist = touristRepository.save(Tourist.builder()
                .user(user)
                .firstName(request.firstName().trim())
                .lastName(request.lastName().trim())
                .nationalId(request.nationalId())
                .build());
        user.setTourist(tourist);

        return toResponse(user);
    }

    private User reuseExistingAccount(User existing, String rawPassword) {
        if (existing.getTourist() != null) {
            throw new EmailAlreadyRegisteredException(existing.getEmail());
        }
        if (!passwordEncoder.matches(rawPassword, existing.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return existing;
    }

    private UserResponse toResponse(User user) {
        List<String> roles = new ArrayList<>();
        if (user.getTourist() != null) roles.add("TOURIST");
        if (user.getProvider() != null) roles.add("PROVIDER");
        if (user.getAdmin() != null) roles.add("ADMIN");

        Tourist tourist = user.getTourist();
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                tourist != null ? tourist.getFirstName() : null,
                tourist != null ? tourist.getLastName() : null,
                roles);
    }
}
