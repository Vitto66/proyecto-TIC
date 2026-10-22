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

    // Medio de pago opcional (CU-01 paso 3): si se completa un dato, se piden los tres
    const cardFields = ['cardNumber', 'cardExpiry', 'cardCvv']
        .map(id => document.getElementById(id))
        .filter(Boolean);

    function syncCardRequired() {
        const wantsCard = cardFields.some(input => input.value.trim() !== '');
        cardFields.forEach(input => { input.required = wantsCard; });
    }

    cardFields.forEach(input => input.addEventListener('input', syncCardRequired));

    // Al escribir en un campo marcado por el servidor, se saca la marca de error
    form.querySelectorAll('.is-invalid').forEach(input => {
        input.addEventListener('input', () => input.classList.remove('is-invalid'), { once: true });
    });

    form.addEventListener('submit', event => {
        checkPasswordsMatch();
        syncCardRequired();
        if (!form.checkValidity()) {
            event.preventDefault();
            event.stopPropagation();
            const firstInvalid = form.querySelector('input:invalid');
            if (firstInvalid) {
                // Si el error está en la sección de pago cerrada, se abre para mostrarlo
                const section = firstInvalid.closest('details');
                if (section) section.open = true;
                firstInvalid.focus();
            }
        }
        form.classList.add('was-validated');
    });
})();
