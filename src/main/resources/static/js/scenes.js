// Paisajes ilustrados para las tarjetas de actividades del inicio. Las actividades todavía no
// tienen fotos propias: cada tarjeta muestra un paisaje según su tipo (sierra, río, costa,
// campo, viñedo o palmar). Uso: <div data-scene="sierra"></div>
(function () {
const PAL = {
  day:   { s1: '#BFE3FA', s2: '#EAF6FD', sun: '#F6C445', sea: '#2F7FC1', sea2: '#1F5F99', l1: '#9CC98A', l2: '#6FA764', l3: '#3F7A45', sand: '#EBD8A8', ink: '#0E3A63' },
  dusk:  { s1: '#F5B97A', s2: '#FBE4C4', sun: '#FFF1C9', sea: '#3F6FA0', sea2: '#2A4F7A', l1: '#7E9C78', l2: '#5B7C5E', l3: '#33523F', sand: '#E5C79A', ink: '#0E3A63' },
  blue:  { s1: '#9FD0F2', s2: '#E2F1FB', sun: '#FFFFFF', sea: '#1F6FB8', sea2: '#0E3A63', l1: '#7CC3EC', l2: '#4E9DD9', l3: '#1F6FB8', sand: '#CFE6F7', ink: '#0E3A63' }
};

// Paisajes ilustrados que reemplazan a las fotos en las maquetas.
const scene = function (kind, tone) {
  const p = PAL[tone || 'day'];
  const id = 'g' + Math.random().toString(36).slice(2, 8);
  const sky = `<defs><linearGradient id="${id}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="${p.s1}"/><stop offset="1" stop-color="${p.s2}"/></linearGradient></defs><rect width="400" height="260" fill="url(#${id})"/>`;
  const sun = (x, y, r) => `<circle cx="${x}" cy="${y}" r="${r}" fill="${p.sun}" opacity=".95"/>`;
  let body = '';
  switch (kind) {
    case 'costa':
      body = sun(300, 70, 28) +
        `<rect y="150" width="400" height="110" fill="${p.sea}"/><path d="M0 170 Q100 160 200 172 T400 166 V260 H0Z" fill="${p.sea2}" opacity=".5"/>` +
        `<path d="M0 205 Q120 180 230 200 T400 190 V260 H0Z" fill="${p.sand}"/>` +
        `<path d="M80 205 L86 120 L98 120 L104 205Z" fill="#fff"/><rect x="83" y="140" width="18" height="8" fill="${p.ink}"/><rect x="84" y="165" width="16" height="8" fill="${p.ink}"/><path d="M82 120 h22 l-4 -12 h-14z" fill="${p.ink}"/>` +
        `<ellipse cx="300" cy="215" rx="40" ry="10" fill="#7A6A55" opacity=".7"/><ellipse cx="292" cy="210" rx="9" ry="4" fill="#4E4235"/><ellipse cx="312" cy="212" rx="8" ry="3.5" fill="#4E4235"/>`;
      break;
    case 'sierra': case 'sierra2':
      body = sun(kind === 'sierra' ? 90 : 320, 80, 24) +
        `<path d="M0 170 L70 95 L130 150 L200 80 L280 160 L340 110 L400 150 V260 H0Z" fill="${p.l1}"/>` +
        `<path d="M0 200 L90 140 L170 190 L250 130 L330 185 L400 160 V260 H0Z" fill="${p.l2}"/>` +
        `<path d="M0 230 Q120 200 220 225 T400 215 V260 H0Z" fill="${p.l3}"/>` +
        [40, 60, 300, 320, 345].map(x => `<path d="M${x} 232 l8 -26 l8 26z" fill="${p.l3}" opacity=".9"/>`).join('');
      break;
    case 'rio': case 'rio2':
      body = sun(kind === 'rio' ? 200 : 110, 120, 34) +
        `<path d="M0 150 Q100 140 200 150 T400 145 V260 H0Z" fill="${p.l2}"/>` +
        `<path d="M0 165 Q200 150 400 168 V260 H0Z" fill="${p.sea}"/>` +
        `<path d="M150 200 h60 M230 215 h50 M120 230 h40" stroke="#fff" stroke-width="3" stroke-linecap="round" opacity=".55"/>` +
        `<path d="M170 192 q30 8 60 0 l-6 6 h-48z" fill="${p.ink}"/><path d="M200 188 v-14" stroke="${p.ink}" stroke-width="3"/>` +
        `<path d="M0 248 Q200 236 400 250 V260 H0Z" fill="${p.l3}"/>`;
      break;
    case 'campo':
      body = sun(320, 70, 26) +
        `<path d="M0 160 Q90 120 180 150 T400 140 V260 H0Z" fill="${p.l1}"/>` +
        `<path d="M0 200 Q140 170 260 195 T400 185 V260 H0Z" fill="${p.l2}"/>` +
        `<g fill="${p.ink}"><path d="M150 178 q14 -10 32 -2 l4 -8 l4 3 l-3 9 q2 10 -6 14 l1 14 h-4 l-2 -12 h-18 l-2 12 h-4 l1 -14 q-6 -6 -3 -16z"/><circle cx="168" cy="160" r="6"/><path d="M164 166 l-2 14 h12 l-2 -14z"/></g>`;
      break;
    case 'vino':
      body = sun(80, 70, 24) +
        `<path d="M0 140 Q200 100 400 135 V260 H0Z" fill="${p.l1}"/>` +
        Array.from({ length: 9 }, (_, i) => `<path d="M${-40 + i * 55} 260 L${150 + i * 22} 140" stroke="${p.l3}" stroke-width="7" stroke-linecap="round" opacity=".85"/>`).join('') +
        `<rect x="290" y="112" width="60" height="30" fill="#fff"/><path d="M284 114 l36 -20 l36 20z" fill="${p.ink}"/>`;
      break;
    case 'palmar':
      body = sun(200, 150, 50) +
        `<path d="M0 190 Q200 175 400 192 V260 H0Z" fill="${p.l2}"/>` +
        [[60, 1], [130, .8], [270, 1.1], [340, .85]].map(([x, s]) =>
          `<g transform="translate(${x} 195) scale(${s})" fill="${p.ink}"><path d="M-3 0 q2 -50 6 -95 h3 q-2 45 0 95z"/>` +
          [-70, -40, -10, 20, 50].map(a => `<path d="M2 -95 q${30 * Math.cos((a - 90) * Math.PI / 180)} ${-6 + 30 * Math.sin((a - 90) * Math.PI / 180)} ${44 * Math.cos((a - 60) * Math.PI / 180)} ${30 + 44 * Math.sin((a - 60) * Math.PI / 180) - 30}" stroke="${p.ink}" stroke-width="4" fill="none"/>`).join('') + `</g>`).join('');
      break;
  }
  return `<svg viewBox="0 0 400 260" preserveAspectRatio="xMidYMid slice" aria-hidden="true">${sky}${body}</svg>`;
};

    document.querySelectorAll('[data-scene]').forEach(el => {
        el.insertAdjacentHTML('afterbegin', scene(el.dataset.scene, el.dataset.tone));
    });
})();
