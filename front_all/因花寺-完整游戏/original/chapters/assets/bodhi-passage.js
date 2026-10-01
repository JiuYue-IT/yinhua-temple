(() => {
  'use strict';
  const root = document.documentElement;
  const incoming = root.classList.contains('bodhi-arriving');
  const next = document.querySelector('#bodhiNext');
  if (!incoming && !next) return;
  const reduced = matchMedia('(prefers-reduced-motion: reduce)').matches;
  const leaf = '<svg viewBox="0 0 104 144" fill="none" aria-hidden="true"><path d="M52 9C43 32 15 25 12 57C8 93 45 113 52 133C59 113 96 93 92 57C89 25 61 32 52 9Z" fill="#e8c780" stroke="#fff0bd" stroke-width="1.2"/><path d="M52 19V132M52 68L26 49M52 87L22 67M52 105L32 88M52 68L78 49M52 87L82 67M52 105L72 88" stroke="#977340" stroke-width="1.2" opacity=".7"/></svg>';
  let active = false;
  let cleanup = () => {};

  function makePassage(mode) {
    const veil = document.createElement('div');
    veil.className = `bodhi-passage ${mode}`;
    veil.setAttribute('role','status');
    veil.setAttribute('aria-label',mode === 'incoming' ? '来到菩提树下' : '携这一念，去菩提树下');
    veil.innerHTML = '<div class="bodhi-passage-shade" aria-hidden="true"></div><div class="bodhi-passage-paper" aria-hidden="true"><span>一念</span></div><div class="bodhi-passage-leaf" aria-hidden="true">' + leaf + '</div><p class="bodhi-passage-copy">携这一念，去树下坐坐。</p><div class="bodhi-passage-light" aria-hidden="true"></div>';
    document.body.appendChild(veil);
    return veil;
  }

  function guard(veil) {
    const controller = new AbortController();
    const main = document.querySelector('main');
    const wasInert = main?.inert;
    if (main) main.inert = true;
    for (const type of ['wheel','touchstart','touchmove','touchend','pointerdown','pointerup','click','keydown']) {
      window.addEventListener(type, event => {
        if (event.target instanceof Element && event.target.closest('.bodhi-passage-skip')) return;
        if (event.type === 'keydown' && (event.key === 'Tab' || event.ctrlKey || event.metaKey || event.altKey)) return;
        event.preventDefault(); event.stopImmediatePropagation();
      }, {capture:true,passive:false,signal:controller.signal});
    }
    return () => { controller.abort(); if (main) main.inert = wasInert; veil.remove(); };
  }

  function motion(target,frames,duration,delay=0) {
    return target.animate(frames,{duration,delay,fill:'both',easing:frames.length > 2 ? 'linear' : 'cubic-bezier(.25,.7,.3,1)'});
  }

  async function arrive() {
    const veil = makePassage('incoming');
    const release = guard(veil);
    let finished = false;
    const finish = () => {
      if (finished) return;
      finished = true; clearTimeout(watchdog);
      root.classList.remove('bodhi-arriving','bodhi-cover');
      release();
      window.dispatchEvent(new Event('bodhi:ready'));
    };
    const watchdog = setTimeout(finish,5200);
    cleanup = finish;
    try {
      const firstImage = document.querySelector('#scene');
      if (firstImage?.decode) await Promise.race([firstImage.decode().catch(()=>{}),new Promise(resolve=>setTimeout(resolve,2200))]);
      if (finished) return;
      root.classList.remove('bodhi-cover');
      const duration = reduced ? 240 : 1400;
      motion(veil.querySelector('.bodhi-passage-leaf'),[
        {opacity:reduced ? 0 : .45,transform:'translate(0,-115px) rotate(-18deg) scale(.65)'},
        {opacity:0,transform:'translate(22px,-180px) rotate(-30deg) scale(.5)'}
      ],duration);
      await motion(veil.querySelector('.bodhi-passage-light'),[{opacity:1},{opacity:0}],duration).finished;
    } catch (error) {
      console.warn('Tree arrival skipped:',error);
    } finally { finish(); }
  }

  async function depart(event) {
    if (event.ctrlKey || event.metaKey || event.shiftKey || event.altKey || event.button > 0) return;
    event.preventDefault();
    if (active || !document.querySelector('#actionOverlay.done')) return;
    active = true;
    const veil = makePassage('outgoing');
    const skip = document.createElement('a');
    skip.className = 'bodhi-passage-skip'; skip.href = next.href; skip.textContent = '直接到树下';
    veil.appendChild(skip);
    const release = guard(veil);
    let navigating = false;
    const navigate = () => {
      if (navigating || !active) return;
      navigating = true;
      if (window.parent !== window) window.parent.postMessage({type:'hall-complete'}, '*');
      else location.assign(next.href);
    };
    skip.addEventListener('click', event => { event.preventDefault(); navigate(); });
    const watchdog = setTimeout(navigate,4800);
    cleanup = () => { clearTimeout(watchdog); release(); active = false; };
    skip.focus({preventScroll:true});
    try {
      const shade = veil.querySelector('.bodhi-passage-shade');
      const paper = veil.querySelector('.bodhi-passage-paper');
      const sprout = veil.querySelector('.bodhi-passage-leaf');
      const caption = veil.querySelector('.bodhi-passage-copy');
      const light = veil.querySelector('.bodhi-passage-light');
      if (reduced) {
        await motion(light,[{opacity:0},{opacity:1}],280).finished;
      } else {
        motion(shade,[{opacity:0},{opacity:1}],550);
        motion(paper,[
          {opacity:0,transform:'translateY(26px) rotate(-4deg) scale(.92)',offset:0},
          {opacity:1,transform:'translateY(0) rotate(0) scale(1)',offset:.3},
          {opacity:1,transform:'translateY(0) rotate(0) scale(1)',offset:.5},
          {opacity:0,transform:'translateY(-12px) rotateY(76deg) scale(.6)',offset:1}
        ],1500);
        motion(sprout,[
          {opacity:0,transform:'translate(0,0) rotate(9deg) scale(.55)',offset:0},
          {opacity:1,transform:'translate(0,-12px) rotate(-5deg) scale(1)',offset:.35},
          {opacity:.45,transform:'translate(0,-115px) rotate(-18deg) scale(.65)',offset:1}
        ],1950,1000);
        motion(caption,[{opacity:0},{opacity:1,offset:.25},{opacity:1,offset:.6},{opacity:0}],2250,250);
        motion(skip,[{opacity:1},{opacity:0}],500,2150);
        await motion(light,[{opacity:0},{opacity:1}],1000,2050).finished;
      }
    } catch(error) {
      console.warn('Tree departure skipped:',error);
    } finally { navigate(); }
  }

  // Returning via browser history must not leave a frozen outgoing cover.
  window.addEventListener('pagehide',()=>cleanup());
  if (incoming) arrive();
  else {
    next.addEventListener('click',depart);
    const prefetch = new Image();
    prefetch.src = new URL('../bodhi/assets/bodhi-scene-pink.png',location.href).href;
  }
})();
