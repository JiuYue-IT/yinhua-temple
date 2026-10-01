(() => {
  const root = document.documentElement;
  const veil = document.querySelector('#arrivalTransition');
  if (!root.classList.contains('arrival-pending')) {
    veil?.remove();
    return;
  }

  const experience = document.querySelector('#experience');
  const scene = document.querySelector('#scene');
  const curtain = veil?.querySelector('.arrival-curtain');
  const edge = veil?.querySelector('.arrival-edge');
  const firstShot = scene?.querySelector('img.active');
  if (!experience || !scene || !curtain || !edge || !firstShot) {
    root.classList.remove('arrival-pending');
    veil?.remove();
    return;
  }

  const reduced = matchMedia('(prefers-reduced-motion: reduce)').matches;
  const originalInert = experience.inert;
  const originalBusy = experience.getAttribute('aria-busy');
  const inputGuard = new AbortController();
  const animations = [];
  let finished = false;
  experience.inert = true;
  experience.setAttribute('aria-busy', 'true');

  // Capturing also blocks the existing global wheel and keyboard handlers.
  const blockInput = event => {
    event.preventDefault();
    event.stopImmediatePropagation();
  };
  for (const type of ['wheel', 'touchstart', 'touchmove', 'pointerdown', 'pointerup', 'click', 'keydown']) {
    window.addEventListener(type, blockInput, {capture: true, passive: false, signal: inputGuard.signal});
  }

  function finish() {
    if (finished) return;
    finished = true;
    // Cancel filled animations so subsequent ritual transforms remain writable.
    root.classList.remove('arrival-pending');
    animations.forEach(animation => animation.cancel());
    veil.remove();
    experience.inert = originalInert;
    if (originalBusy === null) experience.removeAttribute('aria-busy');
    else experience.setAttribute('aria-busy', originalBusy);
    inputGuard.abort();
  }

  const animate = (target, keyframes, options) => {
    const animation = target.animate(keyframes, {...options, fill: 'both'});
    animations.push(animation);
    return animation;
  };

  async function enter() {
    try {
      if (typeof firstShot.decode === 'function') await firstShot.decode();
      else if (!firstShot.complete) await new Promise((resolve, reject) => {
        firstShot.addEventListener('load', resolve, {once: true});
        firstShot.addEventListener('error', reject, {once: true});
      });
      if (!firstShot.naturalWidth) throw new Error('Hall image is unavailable');
      await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)));
      const duration = reduced ? 260 : 1400;
      const restTransform = scene.style.transform || 'scale(1) translateY(0%)';
      const restFilter = scene.style.filter || 'brightness(1) saturate(1)';
      animate(scene, [
        {opacity: 1, transform: reduced ? restTransform : 'scale(.965) translateY(.7%)', filter: 'brightness(.62) saturate(.84)'},
        {opacity: 1, transform: restTransform, filter: restFilter}
      ], {duration, easing: 'cubic-bezier(.2,.7,.25,1)'});
      animate(curtain, [
        {opacity: 1, offset: 0},
        {opacity: .55, offset: .22},
        {opacity: 0, offset: .82},
        {opacity: 0, offset: 1}
      ], {duration, easing: 'ease-out'});
      animate(edge, [
        {opacity: 0, offset: 0},
        {opacity: .7, offset: .25},
        {opacity: 0, offset: 1}
      ], {duration, easing: 'ease-out'});
      for (const element of document.querySelectorAll('.topbar, .guide, .hint, .progress')) {
        animate(element, [{opacity: 0}, {opacity: 1}], {
          duration: reduced ? 160 : 500,
          delay: reduced ? 100 : 900,
          easing: 'ease-out'
        });
      }
      await Promise.all(animations.map(animation => animation.finished));
    } catch (error) {
      // Missing images or unsupported animation APIs must never trap the user.
      console.warn('Hall arrival skipped:', error);
    } finally {
      finish();
    }
  }
  window.addEventListener('pagehide', finish, {once: true});
  enter();
})();
