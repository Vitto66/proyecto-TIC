// Vidriera del CU-05: el departamento filtra las localidades, los botones de tipo buscan al
// tocarlos y "Más filtros" se cierra al tocar afuera. Sin JavaScript, todo se aplica con "Buscar".
(function () {
    const form = document.getElementById('filters-form');
    if (!form) return;

    // Localidades del departamento elegido
    const department = document.getElementById('departmentId');
    const locality = document.getElementById('localityId');
    const options = locality ? Array.from(locality.querySelectorAll('option[data-department]')) : [];
    function filterLocalities() {
        if (!department || !locality) return;
        const selected = department.value;
        options.forEach(option => {
            const visible = selected === '' || option.dataset.department === selected;
            option.hidden = !visible;
            option.disabled = !visible;
        });
        const current = locality.selectedOptions[0];
        if (current && current.disabled) locality.value = '';
    }
    if (department) department.addEventListener('change', filterLocalities);
    filterLocalities();

    // Tocar un tipo busca enseguida
    form.querySelectorAll('input[name="typeId"]').forEach(radio => {
        radio.addEventListener('change', () => form.requestSubmit ? form.requestSubmit() : form.submit());
    });

    // Los filtros vacíos no se mandan, así la dirección de la búsqueda queda limpia
    form.addEventListener('submit', () => {
        form.querySelectorAll('input, select').forEach(field => {
            if (field.type !== 'radio' && !field.value) field.disabled = true;
        });
        const checked = form.querySelector('input[name="typeId"]:checked');
        if (checked && !checked.value) checked.disabled = true;
        setTimeout(() => form.querySelectorAll('input, select').forEach(field => {
            if (!(field.tagName === 'OPTION')) field.disabled = false;
        }), 0);
        setTimeout(filterLocalities, 0);
    });

    // "Más filtros": se cierra al tocar afuera o con Escape
    const panel = document.getElementById('filters-panel');
    if (panel) {
        document.addEventListener('click', event => {
            if (panel.open && !panel.contains(event.target)) panel.open = false;
        });
        document.addEventListener('keydown', event => {
            if (event.key === 'Escape') panel.open = false;
        });
    }
})();
