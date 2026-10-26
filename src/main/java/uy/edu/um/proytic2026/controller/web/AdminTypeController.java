package uy.edu.um.proytic2026.controller.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uy.edu.um.proytic2026.dto.AuthenticatedUser;
import uy.edu.um.proytic2026.dto.TypeForm;
import uy.edu.um.proytic2026.entity.TypeCategory;
import uy.edu.um.proytic2026.exception.TypeInUseException;
import uy.edu.um.proytic2026.exception.TypeNameTakenException;
import uy.edu.um.proytic2026.exception.TypeNotFoundException;
import uy.edu.um.proytic2026.service.TypeManagementService;

/**
 * Páginas del administrador para el CU-13 Gestionar tipos de actividad y de equipamiento.
 * SecurityConfig ya restringe /admin/** al perfil ADMIN.
 * <p>
 * Cada acción vuelve a la lista con un mensaje (Post/Redirect/Get), salvo los errores del
 * formulario de alta, que se muestran en el mismo formulario.
 */
@Controller
@RequestMapping("/admin/types")
@RequiredArgsConstructor
public class AdminTypeController {

    static final String VIEW = "admin-types";

    private final TypeManagementService typeService;

    @ModelAttribute("currentUser")
    AuthenticatedUser currentUser(@AuthenticationPrincipal AuthenticatedUser user) {
        return user;
    }

    /** Paso 1: la gestión de datos generales abre en tipos de actividad. */
    @GetMapping
    public String index() {
        return "redirect:/admin/types/" + TypeCategory.ACTIVITY.slug();
    }

    /** Pasos 2 y 3: elige la lista y la consulta. */
    @GetMapping("/{category}")
    public String list(@PathVariable String category, Model model) {
        TypeCategory c = categoryOr404(category);
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new TypeForm());
        }
        return show(c, model);
    }

    /** Paso 3: alta. */
    @PostMapping("/{category}")
    public String create(@PathVariable String category, @Valid @ModelAttribute("form") TypeForm form,
                         BindingResult result, Model model, RedirectAttributes redirect) {
        TypeCategory c = categoryOr404(category);
        if (!result.hasErrors()) {
            try {
                String name = typeService.create(c, form.getName());
                redirect.addFlashAttribute("notice", "Se agregó \"" + name + "\". Ya lo pueden usar los prestadores.");
                return redirectTo(c);
            } catch (TypeNameTakenException ex) {
                // 3b. Nombre ya existente → se informa el duplicado
                result.rejectValue("name", "taken", "ya existe un " + c.singular() + " con ese nombre");
            } catch (IllegalArgumentException ex) {
                result.rejectValue("name", "invalid", "es obligatorio");
            }
        }
        return show(c, model);
    }

    /** Paso 3: modificación del nombre. */
    @PostMapping("/{category}/{id}/rename")
    public String rename(@PathVariable String category, @PathVariable Integer id,
                         @RequestParam(defaultValue = "") String name, RedirectAttributes redirect) {
        TypeCategory c = categoryOr404(category);
        try {
            String newName = typeService.rename(c, id, name);
            redirect.addFlashAttribute("notice", "Se cambió el nombre a \"" + newName + "\".");
        } catch (TypeNameTakenException ex) {
            redirect.addFlashAttribute("error", "No se cambió el nombre: ya existe un " + c.singular()
                    + " llamado \"" + name.strip() + "\".");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", "No se cambió el nombre: el nombre no puede quedar vacío "
                    + "ni tener más de 100 caracteres.");
        } catch (TypeNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return redirectTo(c);
    }

    /** Paso 3: eliminación. 3a: si está en uso no se elimina y se ofrece desactivarlo. */
    @PostMapping("/{category}/{id}/delete")
    public String delete(@PathVariable String category, @PathVariable Integer id, RedirectAttributes redirect) {
        TypeCategory c = categoryOr404(category);
        try {
            String name = typeService.delete(c, id);
            redirect.addFlashAttribute("notice", "Se eliminó \"" + name + "\".");
        } catch (TypeInUseException ex) {
            redirect.addFlashAttribute("inUse", ex);
        } catch (DataIntegrityViolationException ex) {
            // Alguien lo empezó a usar entre la verificación y el borrado: la base lo frena
            redirect.addFlashAttribute("error", "No se pudo eliminar porque ya lo usa alguna actividad. "
                    + "Podés desactivarlo para que no se use en actividades nuevas.");
        } catch (TypeNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return redirectTo(c);
    }

    /** 3a: desactivar. Deja de ofrecerse al publicar actividades nuevas. */
    @PostMapping("/{category}/{id}/deactivate")
    public String deactivate(@PathVariable String category, @PathVariable Integer id, RedirectAttributes redirect) {
        return setActive(category, id, false, redirect);
    }

    @PostMapping("/{category}/{id}/activate")
    public String activate(@PathVariable String category, @PathVariable Integer id, RedirectAttributes redirect) {
        return setActive(category, id, true, redirect);
    }

    private String setActive(String category, Integer id, boolean active, RedirectAttributes redirect) {
        TypeCategory c = categoryOr404(category);
        try {
            String name = typeService.setActive(c, id, active);
            redirect.addFlashAttribute("notice", active
                    ? "\"" + name + "\" está activo otra vez: se ofrece al publicar actividades."
                    : "\"" + name + "\" quedó desactivado: no se ofrece en actividades nuevas, "
                      + "pero las que ya lo tienen lo conservan.");
        } catch (TypeNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return redirectTo(c);
    }

    private String show(TypeCategory category, Model model) {
        model.addAttribute("category", category);
        model.addAttribute("categories", TypeCategory.values());
        model.addAttribute("types", typeService.list(category));
        return VIEW;
    }

    private static String redirectTo(TypeCategory category) {
        return "redirect:/admin/types/" + category.slug();
    }

    private static TypeCategory categoryOr404(String slug) {
        TypeCategory category = TypeCategory.fromSlug(slug);
        if (category == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return category;
    }
}
