(() => {
 let active=false,position=0,images=[];
 const stage=document.createElement('section');stage.id='courtyardPassage';stage.hidden=true;stage.setAttribute('aria-label','循石径入院');stage.style.cssText='position:fixed;inset:0;z-index:150;background:#bfd7cb';
 const art=document.createElement('img');art.alt='沿石径走向点香庭院';art.style.cssText='width:100%;height:100%;object-fit:cover';
 const hint=document.createElement('p');hint.textContent='向下滚动，沿石径前行 · 向上滚动回望';hint.style.cssText='position:absolute;bottom:22px;left:0;width:100%;text-align:center;color:#fff5df;text-shadow:0 2px 7px #193c2c;font-size:14px';
 const skip=document.createElement('button');skip.textContent='直接去点香 →';skip.style.cssText='position:absolute;right:24px;top:26px;padding:12px 18px;background:#29473666;color:#fff4dd;border:1px solid #efdbab80;border-radius:5px;backdrop-filter:blur(8px)';
 stage.append(art,hint,skip);document.body.append(stage);
 function draw(){art.src=images[Math.round(position)].src;}
 function end(){if(!active)return;active=false;stage.hidden=true;window.startContinuation();}
 function step(delta){position=Math.max(0,Math.min(121,position+delta/23));draw();if(position>=121)end();}
 skip.onclick=end;
 window.startCourtyardPassage=()=>{if(active)return;active=true;position=0;if(!images.length)images=Array.from({length:122},(_,i)=>{const im=new Image();im.src=`assets/courtyard-refined-frames/frame_${String(i+1).padStart(6,'0')}.webp`;return im;});draw();stage.hidden=false;skip.focus();};
 function connect(){const b=document.querySelector('#returnToGate');const replacement=b.cloneNode(true);replacement.textContent='沿石径，去点一炷香';b.replaceWith(replacement);replacement.onclick=window.startCourtyardPassage;}
 if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',connect);else connect();
 window.addEventListener('wheel',e=>{if(active){e.preventDefault();e.stopImmediatePropagation();step(e.deltaY);}},{capture:true,passive:false});
 let y=0;stage.addEventListener('touchstart',e=>y=e.touches[0].clientY,{passive:true});stage.addEventListener('touchmove',e=>{e.preventDefault();const next=e.touches[0].clientY;step((y-next)*3);y=next;},{passive:false});
 stage.addEventListener('keydown',e=>{if(['ArrowDown','ArrowUp','PageDown','PageUp'].includes(e.key)){e.preventDefault();step(e.key.includes('Down')?220:-220);}});
})();
