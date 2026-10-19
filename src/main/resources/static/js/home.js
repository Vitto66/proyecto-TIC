// Menú inicial: rota los fondos de paisaje y deja elegir uno con los selectores.
// Si la persona pidió menos movimiento, queda fijo el primero. Sin JavaScript también
// se ve bien: el primer paisaje ya viene activo en el HTML.
(function () {
    const slides = document.querySelectorAll('.hero-slide');
    const dots = document.querySelectorAll('.hero-dots button');
    const caption = document.getElementById('hero-caption');
    if (slides.length < 2) return;

    const INTERVAL_MS = 7000;
    let current = 0;
    let timer = null;

    function show(index) {
        current = (index + slides.length) % slides.length;
        slides.forEach((slide, i) => slide.classList.toggle('is-active', i === current));
        dots.forEach((dot, i) => dot.setAttribute('aria-current', i === current ? 'true' : 'false'));
        if (caption) caption.textContent = slides[current].dataset.caption || '';
    }

    function start() {
        stop();
        timer = setInterval(() => show(current + 1), INTERVAL_MS);
    }

    function stop() {
        if (timer) clearInterval(timer);
        timer = null;
    }

    dots.forEach((dot, i) => {
        dot.addEventListener('click', () => {
            show(i);
            if (!reducedMotion.matches) start();
        });
    });

    const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
    show(0);
    if (!reducedMotion.matches) start();

    // No gasta recursos con la pestaña en segundo plano
    document.addEventListener('visibilitychange', () => {
        if (document.hidden) stop();
        else if (!reducedMotion.matches) start();
    });
})();
