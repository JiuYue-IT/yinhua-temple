(() => {
 'use strict';
 const total=122,limit=8,cache=new Map();
 let active=false,position=0,displayed=0,run=0,raf=0,busy=false,peak=0,peakBytes=0;
 const stage=document.createElement('section');stage.id='courtyardPassage';stage.hidden=true;stage.setAttribute('aria-label','循石径入院');stage.style.cssText='position:fixed;inset:0;z-index:150;background:#bfd7cb';
 const art=document.createElement('img');art.alt='沿石径走向点香庭院';art.style.cssText='width:100%;height:100%;object-fit:cover';
 const hint=document.createElement('p');hint.className='passage-hint';showWheel();
 function showWheel(){hint.innerHTML='<span class="passage-wheel-icon" aria-hidden="true"><span class="passage-mouse"><i></i></span><span class="passage-chevrons"><i></i><i></i></span></span>';hint.setAttribute('aria-label','向下滚动沿石径前行，向上滚动回望');}
 const controls=document.createElement('div');controls.className='passage-controls';const skip=document.createElement('button');skip.textContent='直接去点香 →';controls.append(skip);stage.append(art,hint,controls);document.body.append(stage);
 function trim(center){const start=Math.max(0,Math.min(total-limit,center-3));const keep=new Set(Array.from({length:limit},(_,i)=>start+i));for(const [i,item] of cache)if(!keep.has(i)){item.image.src='';cache.delete(i);}}
 function load(i){i=Math.max(0,Math.min(total-1,i));if(cache.has(i))return cache.get(i).promise;const image=new Image(),item={image,ready:false};cache.set(i,item);item.promise=(async()=>{image.src=`assets/courtyard-refined-frames/frame_${String(i+1).padStart(6,'0')}.webp`;try{await image.decode();item.ready=true;peakBytes=Math.max(peakBytes,[...cache.values()].reduce((s,x)=>s+x.image.naturalWidth*x.image.naturalHeight*4,0));}catch{item.failed=true;}return item;})();peak=Math.max(peak,cache.size);return item.promise;}
 function stop(){run++;active=false;busy=false;cancelAnimationFrame(raf);raf=0;stage.hidden=true;art.removeAttribute('src');for(const item of cache.values())item.image.src='';cache.clear();}
 function end(){if(!active)return;stop();window.startContinuation();}
 function schedule(){if(active&&!raf&&!busy)raf=requestAnimationFrame(draw);}
 async function draw(){raf=0;if(!active)return;busy=true;const id=run,target=Math.round(position);trim(target);const item=await load(target);if(id!==run)return;busy=false;
  if(target!==Math.round(position)){schedule();return;}
  if(item.ready){art.src=item.image.src;displayed=target;showWheel();if(target===total-1){end();return;}for(let i=Math.max(0,target-3);i<=Math.min(total-1,target+3);i++)load(i);}
  else {cache.delete(target);hint.textContent='石径影像暂不可用，继续滚动重试或直接去点香';}
 }
 function step(delta){if(!active)return;position=Math.max(0,Math.min(total-1,position+delta/23));schedule();}
 window.addEventListener('wheel',e=>{if(active){e.preventDefault();e.stopImmediatePropagation();step(e.deltaY);}},{capture:true,passive:false});
 let touchY=0;stage.addEventListener('touchstart',e=>{touchY=e.touches[0].clientY;},{passive:true});
 stage.addEventListener('touchmove',e=>{e.preventDefault();const y=e.touches[0].clientY;step((touchY-y)*3);touchY=y;},{passive:false});
 stage.addEventListener('keydown',e=>{if(['ArrowDown','ArrowUp','PageDown','PageUp'].includes(e.key)){e.preventDefault();step(e.key.includes('Down')?220:-220);}});
 skip.onclick=end;
 window.startCourtyardPassage=async()=>{if(active)return;active=true;const id=++run;position=displayed=0;busy=true;const first=await load(0);if(id!==run)return;busy=false;stage.hidden=false;showWheel();if(first.ready)art.src=first.image.src;else hint.textContent='石径影像暂不可用，可滚动重试或直接去点香';skip.focus({preventScroll:true});schedule();};
 function connect(){const b=document.querySelector('#returnToGate');const replacement=b.cloneNode(true);replacement.textContent='沿石径，去点一炷香';b.replaceWith(replacement);replacement.onclick=window.startCourtyardPassage;}
 if(document.readyState!=='complete')document.addEventListener('DOMContentLoaded',connect);else connect();
 window.addEventListener('temple-stage',()=>{if(active)stop();});window.addEventListener('pagehide',stop);
 window.CourtyardPassage={stop,get stats(){return {active,position,displayed,cached:cache.size,peak,estimatedDecodedPeakMiB:Math.round(peakBytes/1048576*10)/10};}};
})();
