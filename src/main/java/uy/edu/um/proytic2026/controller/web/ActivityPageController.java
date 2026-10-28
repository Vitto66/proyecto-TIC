package uy.edu.um.proytic2026.controller.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uy.edu.um.proytic2026.dto.ActivitySummary;
import uy.edu.um.proytic2026.dto.AuthenticatedUser;
import uy.edu.um.proytic2026.dto.CancelPreview;
import uy.edu.um.proytic2026.dto.CancelResult;
import uy.edu.um.proytic2026.dto.PublishActivityRequest;
import uy.edu.um.proytic2026.exception.ActivityNotFoundException;
import uy.edu.um.proytic2026.exception.ActivityNotPublishedException;
import uy.edu.um.proytic2026.exception.BusinessRequiredException;
import uy.edu.um.proytic2026.exception.InvalidLocationException;
import uy.edu.um.proytic2026.exception.InvalidPhotoException;
import uy.edu.um.proytic2026.exception.InvalidSelectionException;
import uy.edu.um.proytic2026.exception.ProviderNotApprovedException;
import uy.edu.um.proytic2026.service.ActivityCancellationService;
import uy.edu.um.proytic2026.service.ActivityPhotoService;
import uy.edu.um.proytic2026.service.ActivityPhotoService.PhotoUpload;
import uy.edu.um.proytic2026.service.ActivityService;
import uy.edu.um.proytic2026.service.ActivityService.Access;

/**
 * Páginas del CU-08 Publicar una actividad ("Mis actividades" y el formulario) y del CU-10
 * Modificar o dar de baja una actividad.
 * SecurityConfig ya restringe /provider/** al perfil PROVIDER; acá se controla además que
 * esté APROBADO y que tenga su emprendimiento registrado.
 */
@Controller
@RequestMapping("/provider/activities")
@RequiredArgsConstructor
public class ActivityPageController {

    static final String LIST_VIEW = "activities";
    static final String FORM_VIEW = "activity-form";
    static final String CANCEL_VIEW = "activity-cancel";

    private final ActivityService activityService;
    private final ActivityCancellationService cancellationService;
    private final ActivityPhotoService photoService;

    @ModelAttribute("currentUser")
    AuthenticatedUser currentUser(@AuthenticationPrincipal AuthenticatedUser user) {
        return user;
    }

    /** Paso 1: "Mis actividades". */
    @GetMapping
    public String list(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        Access access = activityService.access(user.userId());
        model.addAttribute("access", access);
        if (access == Access.ALLOWED) {
            model.addAttribute("activities", activityService.myActivities(user.userId()));
            // CU-10: devoluciones que quedaron pendientes al dar de baja una actividad
            model.addAttribute("pendingRefunds", cancellationService.pendingRefunds(user.userId()));
            model.addAttribute("photos", photoService.urls());
        }
        return LIST_VIEW;
    }

    /** Paso 1: elige publicar una nueva. */
    @GetMapping("/new")
    public String newForm(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        if (activityService.access(user.userId()) != Access.ALLOWED) {
            return "redirect:/provider/activities";
        }
        model.addAttribute("form", activityService.formDefaults(user.userId()));
        return formView(model);
    }

    /** Pasos 2 a 7, con la foto de portada (opcional). */
    @PostMapping
    public String publish(@AuthenticationPrincipal AuthenticatedUser user,
                          @Valid @ModelAttribute("form") PublishActivityRequest form, BindingResult result,
                          @RequestParam(name = "photo", required = false) MultipartFile photoFile,
                          Model model, RedirectAttributes redirect) {
        PhotoUpload photo = readPhoto(photoFile, model);
        // 6a. Faltan datos obligatorios (o la foto no sirve) → se marcan los campos
        if (result.hasErrors() || model.containsAttribute("photoError")) {
            photoLost(photoFile, model);
            return formView(model);
        }
        ActivitySummary published;
        try {
            published = activityService.publish(user.userId(), form);
        } catch (ProviderNotApprovedException | BusinessRequiredException ex) {
            return "redirect:/provider/activities";
        } catch (InvalidLocationException ex) {
            result.rejectValue("localityId", "invalid", "elegí una localidad del departamento seleccionado");
            photoLost(photoFile, model);
            return formView(model);
        } catch (InvalidSelectionException ex) {
            if ("equipment".equals(ex.getField())) {
                result.reject("equipment", "Elegí equipamiento de la lista.");
            } else {
                result.rejectValue(ex.getField(), "invalid", ex.getMessage());
            }
            photoLost(photoFile, model);
            return formView(model);
        }
        if (photo != null) {
            photoService.save(published.id(), photo);
        }
        // Post/Redirect/Get: si recarga la lista no se vuelve a publicar
        redirect.addFlashAttribute("published", published.name());
        return "redirect:/provider/activities";
    }

    // ---------- CU-10 Modificar ----------

    /** Pasos 1 y 2: elige una de sus actividades y la opción de modificarla. */
    @GetMapping("/{activityId}/edit")
    public String editForm(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long activityId,
                           Model model, RedirectAttributes redirect) {
        try {
            model.addAttribute("form", activityService.editForm(user.userId(), activityId));
            return editView(user, activityId, model);
        } catch (ActivityNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        } catch (ActivityNotPublishedException ex) {
            return notPublished(redirect);
        }
    }

    /** Pasos 3 y 4: valida los cambios y actualiza la actividad. */
    @PostMapping("/{activityId}")
    public String update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long activityId,
                         @Valid @ModelAttribute("form") PublishActivityRequest form, BindingResult result,
                         @RequestParam(name = "photo", required = false) MultipartFile photoFile,
                         @RequestParam(name = "removePhoto", defaultValue = "false") boolean removePhoto,
                         Model model, RedirectAttributes redirect) {
        try {
            PhotoUpload photo = readPhoto(photoFile, model);
            if (result.hasErrors() || model.containsAttribute("photoError")) {
                photoLost(photoFile, model);
                return editView(user, activityId, model);
            }
            ActivitySummary updated = activityService.update(user.userId(), activityId, form);
            // Una foto nueva reemplaza a la anterior; si no eligió otra, puede sacarla
            if (photo != null) {
                photoService.save(updated.id(), photo);
            } else if (removePhoto) {
                photoService.remove(updated.id());
            }
            redirect.addFlashAttribute("activityMessage", "Se guardaron los cambios de \"" + updated.name() + "\".");
            return "redirect:/provider/activities";
        } catch (ActivityNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        } catch (ActivityNotPublishedException ex) {
            return notPublished(redirect);
        } catch (InvalidLocationException ex) {
            result.rejectValue("localityId", "invalid", "elegí una localidad del departamento seleccionado");
            photoLost(photoFile, model);
            return editView(user, activityId, model);
        } catch (InvalidSelectionException ex) {
            if ("equipment".equals(ex.getField())) {
                result.reject("equipment", "Elegí equipamiento de la lista.");
            } else {
                result.rejectValue(ex.getField(), "invalid", ex.getMessage());
            }
            photoLost(photoFile, model);
            return editView(user, activityId, model);
        }
    }

    // ---------- CU-10 Dar de baja ----------

    /** Paso 2: elige darla de baja; 2a/2b: se informa qué reservas se cancelan y se pide confirmación. */
    @GetMapping("/{activityId}/cancel")
    public String cancelForm(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long activityId,
                             Model model, RedirectAttributes redirect) {
        try {
            CancelPreview preview = cancellationService.preview(user.userId(), activityId);
            model.addAttribute("preview", preview);
            return CANCEL_VIEW;
        } catch (ActivityNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        } catch (ActivityNotPublishedException ex) {
            return notPublished(redirect);
        }
    }

    /** Confirma: se retira del portal, se cancelan sus reservas futuras y se devuelven los pagos. */
    @PostMapping("/{activityId}/cancel")
    public String cancel(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long activityId,
                         RedirectAttributes redirect) {
        try {
            CancelResult result = cancellationService.cancel(user.userId(), activityId);
            redirect.addFlashAttribute("activityMessage", result.message());
            redirect.addFlashAttribute("activityLevel", result.refundsPending() > 0 ? "warning" : "success");
        } catch (ActivityNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        } catch (ActivityNotPublishedException ex) {
            return notPublished(redirect);
        }
        return "redirect:/provider/activities";
    }

    /** Reintenta las devoluciones que quedaron pendientes porque el servicio de pagos no respondió. */
    @PostMapping("/{activityId}/refunds")
    public String retryRefunds(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long activityId,
                               RedirectAttributes redirect) {
        CancelResult result;
        try {
            result = cancellationService.retryRefunds(user.userId(), activityId);
        } catch (ActivityNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        boolean done = result.refundsPending() == 0;
        redirect.addFlashAttribute("activityLevel", done ? "success" : "warning");
        redirect.addFlashAttribute("activityMessage", done
                ? "Listo: se devolvió el dinero de " + CancelPreview.reservations(result.refunded()) + "."
                : "El servicio de pagos sigue sin responder. Probá de nuevo en unos minutos.");
        return "redirect:/provider/activities";
    }

    private String editView(AuthenticatedUser user, Long activityId, Model model) {
        try {
            model.addAttribute("editingId", activityId);
            model.addAttribute("editingName", activityService.activityName(user.userId(), activityId));
            model.addAttribute("options", activityService.formOptions(user.userId(), activityId));
            model.addAttribute("currentPhotoUrl", photoService.url(activityId));
        } catch (ActivityNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return FORM_VIEW;
    }

    /** Revisa la foto elegida; si no sirve, deja el motivo en el modelo para mostrarlo junto al campo. */
    private PhotoUpload readPhoto(MultipartFile file, Model model) {
        try {
            return photoService.read(file).orElse(null);
        } catch (InvalidPhotoException ex) {
            model.addAttribute("photoError", ex.getMessage());
            return null;
        }
    }

    /** El navegador no vuelve a llenar el campo de archivo: si eligió una foto, se le pide que la elija de nuevo. */
    private static void photoLost(MultipartFile file, Model model) {
        if (file != null && !file.isEmpty() && !model.containsAttribute("photoError")) {
            model.addAttribute("photoLost", true);
        }
    }

    private static String notPublished(RedirectAttributes redirect) {
        redirect.addFlashAttribute("activityLevel", "warning");
        redirect.addFlashAttribute("activityMessage", "Esa actividad está dada de baja: ya no se puede modificar.");
        return "redirect:/provider/activities";
    }

    private String formView(Model model) {
        model.addAttribute("options", activityService.formOptions());
        return FORM_VIEW;
    }
}
