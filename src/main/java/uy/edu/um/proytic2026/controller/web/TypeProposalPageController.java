package uy.edu.um.proytic2026.controller.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uy.edu.um.proytic2026.dto.AuthenticatedUser;
import uy.edu.um.proytic2026.dto.ProposalForm;
import uy.edu.um.proytic2026.dto.ProposalView;
import uy.edu.um.proytic2026.entity.TypeCategory;
import uy.edu.um.proytic2026.exception.ProposalAlreadyPendingException;
import uy.edu.um.proytic2026.exception.ProposalNotAllowedException;
import uy.edu.um.proytic2026.exception.TypeAlreadyExistsException;
import uy.edu.um.proytic2026.service.TypeProposalService;

/**
 * Página del CU-12 Proponer un tipo de actividad o de equipamiento.
 * SecurityConfig ya restringe /provider/** al perfil PROVIDER. Se llega desde el formulario de
 * publicar actividad (paso 1) y desde el espacio del prestador.
 */
@Controller
@RequestMapping("/provider/proposals")
@RequiredArgsConstructor
public class TypeProposalPageController {

    static final String VIEW = "provider-proposals";

    private final TypeProposalService proposalService;

    @ModelAttribute("currentUser")
    AuthenticatedUser currentUser(@AuthenticationPrincipal AuthenticatedUser user) {
        return user;
    }

    /** Paso 1: elige proponer un tipo. ?category=equipment preselecciona la lista. */
    @GetMapping
    public String show(@AuthenticationPrincipal AuthenticatedUser user,
                       @RequestParam(required = false) String category, Model model) {
        if (!model.containsAttribute("form")) {
            ProposalForm form = new ProposalForm();
            TypeCategory preselected = TypeCategory.fromSlug(category);
            form.setCategory(preselected != null ? preselected : TypeCategory.ACTIVITY);
            model.addAttribute("form", form);
        }
        return view(user, model);
    }

    /** Pasos 2 y 3. */
    @PostMapping
    public String propose(@AuthenticationPrincipal AuthenticatedUser user,
                          @Valid @ModelAttribute("form") ProposalForm form, BindingResult result,
                          Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            return view(user, model);
        }
        try {
            ProposalView proposal = proposalService.propose(user.userId(), form);
            redirect.addFlashAttribute("notice", "Enviaste la propuesta \"" + proposal.name()
                    + "\". Un administrador la va a revisar; el resultado lo vas a ver en esta página.");
            return "redirect:/provider/proposals";
        } catch (TypeAlreadyExistsException ex) {
            // 2a. Ya existe un tipo con ese nombre → se informa y se ofrece el existente
            model.addAttribute("existing", ex);
        } catch (ProposalAlreadyPendingException ex) {
            result.rejectValue("name", "pending", "ya propusiste este tipo y está pendiente de revisión");
        } catch (ProposalNotAllowedException ex) {
            result.reject("notAllowed", ex.getMessage());
        } catch (IllegalArgumentException ex) {
            result.rejectValue("name", "invalid", "es obligatorio");
        }
        return view(user, model);
    }

    private String view(AuthenticatedUser user, Model model) {
        model.addAttribute("allowed", proposalService.canPropose(user.userId()));
        model.addAttribute("categories", TypeCategory.values());
        model.addAttribute("proposals", proposalService.myProposals(user.userId()));
        return VIEW;
    }
}
