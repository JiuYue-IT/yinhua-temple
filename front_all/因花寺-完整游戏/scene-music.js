(()=>{
'use strict';const audio=new Audio('music-preview/菩提庭径.mp3');audio.dataset.templeMusic='true';audio.loop=true;audio.preload='metadata';audio.volume=.18;let enabled=false,wanted=false,fade=0,started=false;
function notify(){window.dispatchEvent(new Event('temple-music-change'));}
function ramp(target){cancelAnimationFrame(fade);const start=performance.now(),from=audio.volume;function tick(t){audio.volume=from+(target-from)*Math.max(0,Math.min(1,(t-start)/900));if(t-start<900)fade=requestAnimationFrame(tick);else if(target===0)audio.pause();}fade=requestAnimationFrame(tick);}
async function start(){if(document.hidden)return;audio.muted=false;audio.volume=0;try{await audio.play();if(!wanted){audio.pause();return;}enabled=true;ramp(.18);}catch{enabled=false;}notify();}
function stop(){enabled=false;ramp(0);notify();}
window.TempleMusic={get enabled(){return wanted;},get audio(){return audio;},toggle(){started=true;wanted=!wanted;if(wanted)start();else stop();}};
const intro=document.querySelector('#prologue');
wanted=true;started=true;start();
const introObserver=new MutationObserver(()=>{if(intro.hidden){introObserver.disconnect();if(wanted&&!enabled)start();}});
introObserver.observe(intro,{attributes:true,attributeFilter:['hidden']});
document.addEventListener('visibilitychange',()=>{if(document.hidden)stop();else if(wanted)start();});
function resumeOnGesture(e){if(wanted&&!enabled&&!e.target.closest('#navSound'))start();}
document.addEventListener('pointerdown',resumeOnGesture);
document.addEventListener('keydown',resumeOnGesture);
window.addEventListener('pagehide',()=>{cancelAnimationFrame(fade);audio.pause();});
})();
