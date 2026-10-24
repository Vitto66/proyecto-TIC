// Vidriera del CU-05: el departamento filtra las localidades, y en el celular los filtros
// arrancan cerrados para que se vean primero las actividades (salvo que ya haya filtros puestos).
(function () {
    const form = document.getElementById('filters-form');
    if (!form) return;

    const department = document.getElementById('departmentId');
    const locality = document.getElementById('localityId');
    const options = locality ? Array.from(locality.querySelectorAll('option[data-department]')) : [];

    function filterLocalities() {
        if (!department || !locality) return;
        const selected = department.value;
        options.forEach(option => {
            // Sin departamento elegido se ofrecen todas las localidades
            const visible = selected === '' || option.dataset.department === selected;
            option.hidden = !visible;
            option.disabled = !visible;
        });
        const current = locality.selectedOptions[0];
        if (current && current.disabled) locality.value = '';
    }

    if (department) department.addEventListener('change', filterLocalities);
    filterLocalities();

    const panel = document.getElementById('filters-panel');
    const hasFilters = new URLSearchParams(window.location.search).toString() !== '';
    if (panel && window.matchMedia('(max-width: 991.98px)').matches && !hasFilters) {
        panel.open = false;
    }
})();
