// Formulario del CU-07 Registrar el emprendimiento.
// El departamento filtra las localidades, y se valida antes de enviar.
// Las validaciones definitivas las hace el servidor (BusinessService).
(function () {
    const form = document.getElementById('business-form');
    if (!form) return;

    const department = document.getElementById('departmentId');
    const locality = document.getElementById('localityId');
    const emptyHelp = document.getElementById('locality-help');
    const localityOptions = Array.from(locality.querySelectorAll('option[data-department]'));

    function filterLocalities() {
        const selected = department.value;
        let available = 0;
        localityOptions.forEach(option => {
            const matches = selected !== '' && option.dataset.department === selected;
            option.hidden = !matches;
            option.disabled = !matches;
            if (matches) available++;
        });
        // Si la localidad elegida no es del departamento nuevo, se borra la elección
        const current = locality.selectedOptions[0];
        if (current && current.disabled) locality.value = '';
        locality.disabled = selected === '' || available === 0;
        emptyHelp.hidden = !(selected !== '' && available === 0);
    }

    department.addEventListener('change', filterLocalities);

    // Al escribir en un campo marcado por el servidor, se saca la marca de error
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

    filterLocalities();
})();
