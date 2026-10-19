package uy.edu.um.proytic2026.controller.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import uy.edu.um.proytic2026.dto.AuthenticatedUser;
import uy.edu.um.proytic2026.dto.LoginOptions;
import uy.edu.um.proytic2026.dto.LoginRequest;
import uy.edu.um.proytic2026.entity.Role;
import uy.edu.um.proytic2026.exception.InvalidCredentialsException;
import uy.edu.um.proytic2026.exception.ProviderRejectedException;
import uy.edu.um.proytic2026.service.AuthService;

import java.util.ArrayList;
import java.util.List;

/**
 * Páginas de inicio de sesión (CU-04): formulario de correo y contraseña y, cuando el correo
 * tiene más de un perfil, la pantalla para elegir con cuál ingresar.
 * <p>
 * Las reglas están en AuthService. Acá se resuelve lo que es propio de la web: la sesión,
 * la identidad de Spring Security y a dónde se redirige.
 */
@Controller
@RequiredArgsConstructor
public class LoginPageController {

    static final String LOGIN_VIEW = "login";
    static final String PROFILE_VIEW = "login-profile";

    /** Cuenta que ya puso bien la contraseña y todavía tiene que elegir perfil. */
    static final String PENDING_USER = "pendingLoginUserId";
    static final String PENDING_ROLES = "pendingLoginRoles";

    private final AuthService authService;
    private final SecurityContextRepository securityContextRepository;

    /** Una tarjeta de la pantalla "¿Con qué perfil querés ingresar?". */
    public record ProfileOption(String value, String title, String description) {
    }

    @GetMapping("/login")
    public String showForm(@ModelAttribute("form") LoginRequest form,
                           @AuthenticationPrincipal AuthenticatedUser current) {
        // Si ya tiene la sesión iniciada, va directo a su espacio
        return current != null ? "redirect:" + RoleHome.pathFor(current.role()) : LOGIN_VIEW;
    }

    @PostMapping("/login")
    public String submit(@Valid @ModelAttribute("form") LoginRequest form, BindingResult result,
                         HttpServletRequest request, HttpServletResponse response) {
        if (result.hasErrors()) {
            return LOGIN_VIEW;
        }

        LoginOptions options;
        try {
            options = authService.authenticate(form.getEmail(), form.getPassword());
        } catch (InvalidCredentialsException ex) {
            // 2a. Mensaje genérico: no dice si falló el correo o la contraseña
            result.reject("invalid", "Correo o contraseña incorrectos.");
            return LOGIN_VIEW;
        } catch (ProviderRejectedException ex) {
            // 2b. Prestador rechazado → se bloquea el acceso
            result.reject("rejected", ex.getMessage());
            return LOGIN_VIEW;
        }

        if (options.roles().size() == 1) {
            return signInAndRedirect(options.userId(), options.roles().get(0), request, response);
        }
        // 2c. El correo tiene más de un perfil → se pregunta con cuál ingresar
        HttpSession session = request.getSession(true);
        session.setAttribute(PENDING_USER, options.userId());
        session.setAttribute(PENDING_ROLES, new ArrayList<>(options.roles()));
        return "redirect:/login/profile";
    }

    @GetMapping("/login/profile")
    public String chooseProfile(HttpServletRequest request, Model model) {
        List<Role> roles = pendingRoles(request.getSession(false));
        if (roles == null) {
            return "redirect:/login";
        }
        model.addAttribute("options", optionsFor(roles));
        return PROFILE_VIEW;
    }

    @PostMapping("/login/profile")
    public String selectProfile(@RequestParam(name = "role", required = false) String roleParam,
                                HttpServletRequest request, HttpServletResponse response, Model model) {
        HttpSession session = request.getSession(false);
        List<Role> roles = pendingRoles(session);
        if (roles == null) {
            return "redirect:/login";
        }

        Role role = parseRole(roleParam);
        // El valor llega desde el navegador: solo se acepta uno de los perfiles ofrecidos
        if (role == null || !roles.contains(role)) {
            model.addAttribute("options", optionsFor(roles));
            model.addAttribute("error", "Elegí con qué perfil querés ingresar.");
            return PROFILE_VIEW;
        }

        Long userId = (Long) session.getAttribute(PENDING_USER);
        session.removeAttribute(PENDING_USER);
        session.removeAttribute(PENDING_ROLES);
        return signInAndRedirect(userId, role, request, response);
    }

    /** Crea la identidad con el perfil elegido, la guarda en la sesión y redirige a su espacio. */
    private String signInAndRedirect(Long userId, Role role,
                                     HttpServletRequest request, HttpServletResponse response) {
        AuthenticatedUser principal;
        try {
            principal = authService.principalFor(userId, role);
        } catch (InvalidCredentialsException ex) {
            return "redirect:/login";
        }

        // Identificador de sesión nuevo al ingresar: evita la fijación de sesión
        request.getSession(true);
        request.changeSessionId();

        SecurityContext context = SecurityContextHolder.getContextHolderStrategy().createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
        SecurityContextHolder.getContextHolderStrategy().setContext(context);
        securityContextRepository.saveContext(context, request, response);

        return "redirect:" + RoleHome.pathFor(role);
    }

    @SuppressWarnings("unchecked")
    private static List<Role> pendingRoles(HttpSession session) {
        if (session == null || session.getAttribute(PENDING_USER) == null) {
            return null;
        }
        return (List<Role>) session.getAttribute(PENDING_ROLES);
    }

    private static Role parseRole(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Role.valueOf(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static List<ProfileOption> optionsFor(List<Role> roles) {
        return roles.stream().map(role -> switch (role) {
            case TOURIST -> new ProfileOption("TOURIST", "Turista", "Explorá y reservá actividades.");
            case PROVIDER -> new ProfileOption("PROVIDER", "Prestador",
                    "Gestioná tu emprendimiento y tus actividades.");
            case ADMIN -> new ProfileOption("ADMIN", "Administrador",
                    "Revisá prestadores y mantené los datos generales.");
        }).toList();
    }
}
