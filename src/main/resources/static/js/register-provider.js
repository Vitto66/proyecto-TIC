// Formulario de registro de prestadores (CU-02).
// Muestra los campos del tipo de persona elegido y valida antes de enviar.
// Las validaciones definitivas las hace el servidor (ProviderRegistrationValidator).
(function () {
    const form = document.getElementById('provider-form');
    if (!form) return;

    const radios = form.querySelectorAll('input[name="personType"]');
    const sections = form.querySelectorAll('.person-section');
    const common = form.querySelector('.common-section');
    const addressLabel = document.getElementById('address-label');
    const password = document.getElementById('password');
    const confirm = document.getElementById('confirmPassword');

    function selectedType() {
        const checked = form.querySelector('input[name="personType"]:checked');
        return checked ? checked.value : null;
    }

    function applyType() {
        const type = selectedType();
        sections.forEach(section => {
            const active = section.dataset.person === type;
            section.hidden = !active;
            // Los campos ocultos se deshabilitan: así no se validan ni se envían
            section.querySelectorAll('input').forEach(input => { input.disabled = !active; });
        });
        common.hidden = type === null;
        addressLabel.textContent = type === 'LEGAL' ? 'Dirección' : 'Domicilio';
    }

    function checkPasswordsMatch() {
        confirm.setCustomValidity(confirm.value === password.value ? '' : 'mismatch');
    }

    radios.forEach(radio => radio.addEventListener('change', applyType));
    password.addEventListener('input', checkPasswordsMatch);
    confirm.addEventListener('input', checkPasswordsMatch);

    // Al escribir en un campo marcado por el servidor, se saca la marca de error
    form.querySelectorAll('.is-invalid').forEach(input => {
        input.addEventListener('input', () => input.classList.remove('is-invalid'), { once: true });
    });

    form.addEventListener('submit', event => {
        checkPasswordsMatch();
        if (!form.checkValidity()) {
            event.preventDefault();
            event.stopPropagation();
            const firstInvalid = form.querySelector(':invalid:not(fieldset)');
            if (firstInvalid) firstInvalid.focus();
        }
        form.classList.add('was-validated');
    });

    applyType();
})();
