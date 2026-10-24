// Formulario del CU-08 Publicar una actividad.
// El departamento filtra las localidades, y se valida antes de enviar (incluido que haya al
// menos un tipo de actividad). Las validaciones definitivas las hace el servidor (ActivityService).
(function () {
    const form = document.getElementById('activity-form');
    if (!form) return;

    // ---------- Departamento → localidades ----------
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
        const current = locality.selectedOptions[0];
        if (current && current.disabled) locality.value = '';
        locality.disabled = selected === '' || available === 0;
        emptyHelp.hidden = !(selected !== '' && available === 0);
    }

    department.addEventListener('change', filterLocalities);

    // ---------- Al menos un tipo de actividad ----------
    const typesFieldset = document.getElementById('types-fieldset');
    const typeBoxes = Array.from(form.querySelectorAll('input[name="activityTypeIds"]'));

    function typesMissing() {
        return typeBoxes.length > 0 && !typeBoxes.some(box => box.checked);
    }

    typeBoxes.forEach(box => box.addEventListener('change', () => {
        typesFieldset.classList.toggle('is-missing', typesMissing());
    }));

    // Al tocar un campo marcado por el servidor, se saca la marca de error
    form.querySelectorAll('.is-invalid').forEach(field => {
        const clear = () => field.classList.remove('is-invalid');
        field.addEventListener('input', clear, { once: true });
        field.addEventListener('change', clear, { once: true });
    });

    form.addEventListener('submit', event => {
        const missing = typesMissing();
        typesFieldset.classList.toggle('is-missing', missing);
        if (!form.checkValidity() || missing) {
            event.preventDefault();
            event.stopPropagation();
            const firstInvalid = form.querySelector(':invalid') || (missing ? typeBoxes[0] : null);
            if (firstInvalid) firstInvalid.focus();
        }
        form.classList.add('was-validated');
    });

    filterLocalities();
})();
