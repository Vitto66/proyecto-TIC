// Formulario de registro de turistas (CU-01).
// Valida antes de enviar; las validaciones definitivas las hace el servidor.
(function () {
    const form = document.getElementById('tourist-form');
    if (!form) return;

    const password = document.getElementById('password');
    const confirm = document.getElementById('confirmPassword');

    function checkPasswordsMatch() {
        confirm.setCustomValidity(confirm.value === password.value ? '' : 'mismatch');
    }

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
            const firstInvalid = form.querySelector(':invalid');
            if (firstInvalid) firstInvalid.focus();
        }
        form.classList.add('was-validated');
    });
})();
