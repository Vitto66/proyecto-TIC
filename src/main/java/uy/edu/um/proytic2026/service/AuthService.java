package uy.edu.um.proytic2026.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.proytic2026.dto.AuthenticatedUser;
import uy.edu.um.proytic2026.dto.LoginOptions;
import uy.edu.um.proytic2026.entity.PersonType;
import uy.edu.um.proytic2026.entity.Provider;
import uy.edu.um.proytic2026.entity.ProviderStatus;
import uy.edu.um.proytic2026.entity.Role;
import uy.edu.um.proytic2026.entity.User;
import uy.edu.um.proytic2026.exception.InvalidCredentialsException;
import uy.edu.um.proytic2026.exception.ProviderRejectedException;
import uy.edu.um.proytic2026.repository.UserRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * CU-04 Iniciar sesión: reglas de negocio. No conoce HTTP ni sesiones; eso lo resuelve
 * LoginPageController junto con Spring Security.
 * <p>
 * El login tiene dos pasos porque un mismo correo puede tener varios perfiles:
 * {@link #authenticate} valida las credenciales y devuelve con qué perfiles puede entrar;
 * {@link #principalFor} arma la identidad del perfil elegido.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    /**
     * Hash de una contraseña cualquiera. Se compara cuando el correo no existe, para que
     * esa respuesta tarde parecido a la de una contraseña incorrecta.
     */
    private static final String DUMMY_HASH = new BCryptPasswordEncoder().encode("no-es-una-contrasena");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Valida correo y contraseña y devuelve la cuenta junto con los perfiles con los que puede
     * ingresar, en este orden: turista, prestador, administrador.
     *
     * @throws InvalidCredentialsException 2a: correo o contraseña incorrectos. Es el mismo error
     *                                     en ambos casos, para no revelar cuál falló.
     * @throws ProviderRejectedException   2b: la cuenta solo tiene un perfil de prestador RECHAZADO.
     */
    @Transactional(readOnly = true)
    public LoginOptions authenticate(String rawEmail, String rawPassword) {
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);

        if (user == null) {
            passwordEncoder.matches(rawPassword, DUMMY_HASH);
            throw new InvalidCredentialsException();
        }
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        List<Role> roles = availableRoles(user);
        if (roles.isEmpty()) {
            if (isRejectedProvider(user)) {
                throw new ProviderRejectedException();
            }
            // Cuenta sin ningún perfil: no debería existir, se trata como credenciales inválidas.
            throw new InvalidCredentialsException();
        }
        return new LoginOptions(user.getId(), roles);
    }

    /**
     * Arma la identidad con la que se inicia sesión para el perfil elegido. Se vuelve a
     * comprobar que la cuenta tenga ese perfil: el valor llega desde el navegador.
     */
    @Transactional(readOnly = true)
    public AuthenticatedUser principalFor(Long userId, Role role) {
        User user = userRepository.findById(userId).orElseThrow(InvalidCredentialsException::new);
        if (!availableRoles(user).contains(role)) {
            throw new InvalidCredentialsException();
        }
        return new AuthenticatedUser(user.getId(), user.getEmail(), role, displayName(user, role));
    }

    /** Estado de validación del prestador, o vacío si la cuenta no tiene perfil de prestador. */
    @Transactional(readOnly = true)
    public Optional<ProviderStatus> providerStatus(Long userId) {
        return userRepository.findById(userId).map(User::getProvider).map(Provider::getStatus);
    }

    /** Un prestador RECHAZADO no cuenta como perfil disponible, pero el turista de la misma cuenta sí. */
    private List<Role> availableRoles(User user) {
        List<Role> roles = new ArrayList<>();
        if (user.getTourist() != null) roles.add(Role.TOURIST);
        if (user.getProvider() != null && !isRejectedProvider(user)) roles.add(Role.PROVIDER);
        if (user.getAdmin() != null) roles.add(Role.ADMIN);
        return roles;
    }

    private static boolean isRejectedProvider(User user) {
        return user.getProvider() != null && user.getProvider().getStatus() == ProviderStatus.REJECTED;
    }

    private static String displayName(User user, Role role) {
        String name = switch (role) {
            case TOURIST -> user.getTourist().getFirstName();
            case PROVIDER -> {
                Provider provider = user.getProvider();
                yield provider.getPersonType() == PersonType.LEGAL
                        ? provider.getBusinessName()
                        : provider.getFirstName();
            }
            case ADMIN -> user.getAdmin().getName();
        };
        return name == null || name.isBlank() ? user.getEmail() : name;
    }
}
