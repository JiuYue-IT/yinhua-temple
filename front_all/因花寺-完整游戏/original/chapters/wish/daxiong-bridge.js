(() => {
  'use strict';
  const bridge = document.querySelector('#sceneBridge');
  const video = document.querySelector('#sceneBridgeVideo');
  const facade = bridge.querySelector('.bridge-facade');
  const facadeImage = document.querySelector('#sceneBridgeEnd');
  const plaque = bridge.querySelector('.scene-bridge-plaque');
  const shade = bridge.querySelector('.bridge-facade-shade');
  const veil = bridge.querySelector('.bridge-veil');
  const vignette = bridge.querySelector('.bridge-vignette');
  const copy = bridge.querySelector('.scene-bridge-copy');
  const skip = bridge.querySelector('.bridge-skip');
  const reduced = matchMedia('(prefers-reduced-motion: reduce)').matches;
  const decode = img => img.decode ? img.decode().catch(() => {}) : Promise.resolve();
  const hallImage = new Image();
  hallImage.src = '../hall/assets/hall.jpg';
  let moving = false, ending = false, navigating = false, skipping = false;
  let timeline, watchdog;
  const fitFacade = () => {
    const ratio = 1672 / 941;
    const w = Math.max(innerWidth, innerHeight * ratio);
    facade.style.setProperty('--facade-width', `${w}px`);
    facade.style.setProperty('--facade-height', `${w / ratio}px`);
  };
  fitFacade();
  window.addEventListener('resize', fitFacade);
  const go = () => {
    if (navigating) return;
    navigating = true;
    clearInterval(watchdog);
    video.pause();
    if (window.parent !== window) window.parent.postMessage({type:'wish-complete'}, '*');
    else window.location.assign('../hall/index.html?from=wishing-pool');
  };
  const skipToHall = () => {
    if (!moving || navigating || skipping) return;
    skipping = true;
    ending = true;
    clearInterval(watchdog);
    timeline?.kill();
    video.pause();
    if (window.gsap) gsap.to(veil, { opacity: 1, duration: reduced ? .12 : .4, onComplete: go });
    else go();
  };
  const showEnd = async () => {
    if (!moving || ending || navigating) return;
    ending = true;
    clearInterval(watchdog);
    video.pause();
    await Promise.all([decode(facadeImage), decode(plaque)]);
    if (navigating || skipping) return;
    bridge.classList.add('seal-reveal');
    copy.textContent = '静心，入殿';
    if (reduced || !window.gsap) {
      facade.style.opacity = '1';
      plaque.style.opacity = '1';
      setTimeout(go, reduced ? 450 : 900);
      return;
    }
    // The plaque stays attached to the real facade. Move the same camera
    // from its sign to the central doorway instead of inserting a second gate.
    const portrait = innerWidth < innerHeight;
    timeline = gsap.timeline({ defaults: { ease: 'power2.inOut' }, onComplete: go });
    timeline.addLabel('plaque', 0)
      .to(facade, { opacity: 1, duration: .55 }, 'plaque')
      .to(facade, { scale: portrait ? 1.28 : 2.05, duration: 1.75, ease: 'power2.out' }, 'plaque')
      .to(plaque, { opacity: 1, duration: .55 }, 'plaque+=.25')
      .to(shade, { opacity: .3, duration: 1.0 }, 'plaque+=.35')
      .to(vignette, { opacity: .6, duration: 1.0 }, 'plaque+=.35')
      .to(plaque, { filter: 'drop-shadow(0 2px 3px #120905) drop-shadow(0 0 9px rgba(255,211,111,.65)) brightness(1.16)', duration: .7 }, 'plaque+=.45')
      .to(plaque, { filter: 'drop-shadow(0 2px 3px #120905) drop-shadow(0 0 3px rgba(255,211,111,.25)) brightness(1)', duration: .65 }, 'plaque+=1.25')
      .addLabel('enter', 2.15)
      .call(() => bridge.classList.add('threshold'), null, 'enter')
      .to(copy, { opacity: 0, duration: .5 }, 'enter')
      .to(shade, { opacity: .05, duration: .65 }, 'enter')
      .to(facade, { scale: portrait ? 4.8 : 7.2, y: () => -facade.offsetHeight * .13 * (portrait ? 4.8 : 7.2), duration: 2.15, ease: 'power2.in' }, 'enter')
      .to(vignette, { opacity: 1, duration: 1.7 }, 'enter+=.2')
      .to(veil, { opacity: 1, duration: .7, ease: 'power1.inOut' }, 'enter+=1.4');
  };
  const begin = async () => {
    if (moving) return;
    moving = true;
    document.body.classList.add('bridge-active');
    document.querySelector('#experience').inert = true;
    bridge.classList.add('show');
    bridge.setAttribute('aria-hidden', 'false');
    skip.focus({ preventScroll: true });
    decode(hallImage);
    if (reduced || video.error) { showEnd(); return; }
    video.currentTime = 0;
    let lastTime = -1, stalledSince = performance.now();
    watchdog = setInterval(() => {
      if (document.hidden) { stalledSince = performance.now(); return; }
      if (video.currentTime !== lastTime) { lastTime = video.currentTime; stalledSince = performance.now(); }
      if (performance.now() - stalledSince > 8000) showEnd();
    }, 1000);
    try { await video.play(); if (!ending) bridge.classList.add('playing'); }
    catch { showEnd(); }
  };
  window.addEventListener('wishing-pool:continue', event => { event.preventDefault(); begin(); });
  video.addEventListener('ended', showEnd);
  video.addEventListener('error', showEnd);
  skip.addEventListener('click', skipToHall);
  window.addEventListener('pagehide', () => { clearInterval(watchdog); timeline?.kill(); video.pause(); });
  // A shareable local preview of just this transition, without repeating a wish.
  if (new URLSearchParams(location.search).get('preview') === 'hall') requestAnimationFrame(begin);
})();
