// Inicio: fotos que rotan, menús desplegables, buscador y mapa con el panel del departamento.
// Sin JavaScript la página igual funciona: el mapa y los menús son enlaces comunes.

// Fotos de la portada: rotan solas y se pueden elegir con los selectores.
(function () {
    const slides = document.querySelectorAll('.hero-slide');
    const dots = document.querySelectorAll('.hero-dots button');
    const caption = document.getElementById('hero-caption');
    if (slides.length < 2) return;

    const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
    let current = 0;
    let timer = null;

    function show(index) {
        current = (index + slides.length) % slides.length;
        slides.forEach((slide, i) => slide.classList.toggle('is-active', i === current));
        dots.forEach((dot, i) => dot.setAttribute('aria-current', i === current ? 'true' : 'false'));
        if (caption) caption.textContent = slides[current].dataset.caption || '';
    }
    function stop() { if (timer) clearInterval(timer); timer = null; }
    function start() { stop(); if (!reducedMotion.matches) timer = setInterval(() => show(current + 1), 7000); }

    dots.forEach((dot, i) => dot.addEventListener('click', () => { show(i); start(); }));
    document.addEventListener('visibilitychange', () => (document.hidden ? stop() : start()));
    show(0);
    start();
})();

// Menús desplegables: con el mouse se abren solos; en el celular o con teclado, con un toque.
(function () {
    const drops = document.querySelectorAll('.drop');
    function closeAll(except) {
        drops.forEach(d => {
            if (d === except) return;
            d.classList.remove('is-open');
            d.querySelector('.drop-toggle').setAttribute('aria-expanded', 'false');
        });
    }
    drops.forEach(drop => {
        const toggle = drop.querySelector('.drop-toggle');
        toggle.addEventListener('click', event => {
            event.stopPropagation();
            const open = !drop.classList.contains('is-open');
            closeAll(drop);
            drop.classList.toggle('is-open', open);
            toggle.setAttribute('aria-expanded', String(open));
        });
    });
    document.addEventListener('click', () => closeAll());
    document.addEventListener('keydown', event => { if (event.key === 'Escape') closeAll(); });
})();

// Buscador: no manda los campos vacíos, así la dirección de la búsqueda queda limpia.
(function () {
    const form = document.getElementById('finder');
    if (!form) return;
    form.addEventListener('submit', () => {
        form.querySelectorAll('select, input').forEach(field => { field.disabled = !field.value; });
        // Si vuelve con el botón Atrás, los campos tienen que estar habilitados
        setTimeout(() => form.querySelectorAll('select, input').forEach(field => { field.disabled = false; }), 0);
    });
})();

// Mapa: tocar un departamento (o su etiqueta) muestra sus actividades en el panel de al lado.
// El enlace del panel lleva a la vidriera filtrada por ese departamento.
(function () {
    const figure = document.querySelector('.map-figure');
    const panels = document.querySelectorAll('.dept-panel');
    if (!figure || panels.length === 0) return;

    function highlight(iso, on) {
        figure.querySelectorAll('[data-iso="' + iso + '"]').forEach(el => el.classList.toggle('is-highlighted', on));
    }

    function select(iso) {
        let found = false;
        panels.forEach(panel => {
            const match = panel.dataset.iso === iso;
            panel.hidden = !match;
            found = found || match;
        });
        if (!found) return false;
        figure.querySelectorAll('.is-selected').forEach(el => el.classList.remove('is-selected'));
        figure.querySelectorAll('.map-pin[data-iso="' + iso + '"], .dep[data-iso="' + iso + '"]')
            .forEach(el => el.classList.add('is-selected'));
        return true;
    }

    figure.querySelectorAll('a[data-iso], .map-pin[data-iso]').forEach(el => {
        const iso = el.dataset.iso;
        ['mouseenter', 'focus'].forEach(evt => el.addEventListener(evt, () => highlight(iso, true)));
        ['mouseleave', 'blur'].forEach(evt => el.addEventListener(evt, () => highlight(iso, false)));
        el.addEventListener('click', event => {
            if (select(iso)) {
                event.preventDefault();
                // En el celular el panel queda abajo del mapa: se lleva la vista hasta él
                if (window.matchMedia('(max-width: 991.98px)').matches) {
                    document.querySelector('.dept-panel[data-iso="' + iso + '"]')
                        .scrollIntoView({ behavior: 'smooth', block: 'start' });
                }
            }
        });
    });
})();
