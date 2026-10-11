(()=>{
  const intro=document.querySelector('#prologue');
  const audio=new Audio('assets/sfx/mixkit-magic-sparkle-2350.mp3');
  audio.dataset.templeSfx='true';audio.preload='auto';audio.volume=0;
  let raf=0,generation=0;
  function stop(){generation++;cancelAnimationFrame(raf);audio.pause();audio.volume=0;}
  window.addEventListener('temple-prologue-dissolve',async()=>{
    if(document.hidden||intro.hidden||intro.classList.contains('leaving'))return;
    stop();const token=generation;audio.currentTime=0;audio.volume=.09;
    try{await audio.play();}catch{return;}
    if(token!==generation){audio.pause();return;}
    const start=performance.now();
    function tick(now){
      if(document.hidden||intro.hidden||intro.classList.contains('leaving')){stop();return;}
      const t=(now-start)/1000;
      // Begin immediately at low volume, then soften only the ending.
      audio.volume=.09*Math.max(0,Math.min(1,(2.8-t)/1.1));
      if(t<2.8)raf=requestAnimationFrame(tick);else stop();
    }
    raf=requestAnimationFrame(tick);
  });
  new MutationObserver(()=>{if(intro.hidden||intro.classList.contains('leaving'))stop();}).observe(intro,{attributes:true,attributeFilter:['hidden','class']});
  document.addEventListener('visibilitychange',()=>{if(document.hidden)stop();});
  window.addEventListener('pagehide',stop);
})();
