// Formulario del CU-09: valida antes de enviar. Que la fecha sea futura y que no se superponga
// con otra lo controla el servidor (ActivityDateService), con la hora de Uruguay.
(function () {
    const form = document.getElementById('date-form');
    if (!form) return;

    form.querySelectorAll('.is-invalid').forEach(field => {
        const clear = () => field.classList.remove('is-invalid');
        field.addEventListener('input', clear, { once: true });
        field.addEventListener('change', clear, { once: true });
    });

    form.addEventListener('submit', event => {
        if (!form.checkValidity()) {
            event.preventDefault();
            event.stopPropagation();
            const firstInvalid = form.querySelector(':invalid');
            if (firstInvalid) firstInvalid.focus();
        }
        form.classList.add('was-validated');
    });
})();
