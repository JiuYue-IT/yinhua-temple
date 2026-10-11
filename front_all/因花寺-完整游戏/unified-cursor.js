(() => {
 'use strict';
 if(window.TempleCursor||!matchMedia('(pointer:fine)').matches)return;
 const base=new URL('assets/',document.baseURI).href;
 const pictures={point:base+'cursor-point.png',fist:base+'cursor-fist.png',palm:base+'cursor-palm.png'};
 Object.values(pictures).forEach(src=>{const im=new Image();im.src=src;im.decode().catch(()=>{});});
 const hand=document.createElement('img');hand.className='unified-hand';hand.alt='';hand.setAttribute('aria-hidden','true');hand.src=pictures.point;document.body.append(hand);
 const installed=new WeakMap(),bindings=new Set(),frameBindings=new WeakMap();
 let x=0,y=0,mode='point',pressed=false,visible=false,raf=0,touching=false,owner=document;
 const offsets={point:[-10,-12],fist:[-29,-21],palm:[-31,-33]};
 function hide(){visible=false;pressed=false;hand.style.display='none';}
 function draw(){raf=0;if(!visible)return;const [dx,dy]=offsets[mode];hand.style.transform=`translate3d(${x+dx}px,${y+dy}px,0)`;hand.style.display='block';}
 function schedule(){if(!raf)raf=requestAnimationFrame(draw);}
 function pose(next,glow){if(mode!==next){mode=next;hand.src=pictures[next];}if(touching!==glow){touching=glow;hand.classList.toggle('touching',glow);}}
 function coordinates(doc,e){let px=e.clientX,py=e.clientY,w=doc.defaultView;while(w!==window){const f=w.frameElement;if(!f)return null;const r=f.getBoundingClientRect();px=r.left+px*r.width/w.innerWidth;py=r.top+py*r.height/w.innerHeight;w=w.parent;}return [px,py];}
 function move(doc,e){if(e.pointerType==='touch'){hide();return;}const p=coordinates(doc,e);if(!p)return;const topTarget=document.elementFromPoint(...p);if(doc!==document&&topTarget!==doc.defaultView.frameElement){hide();return;}if(owner!==doc){pressed=false;owner=doc;}[x,y]=p;visible=true;const t=e.target;pose(pressed||t.closest?.('.knock-target')?'fist':t.closest?.('#fruit,#palm,#handLayer,.fruit,.palm')?'palm':'point',!!t.closest?.('button,a,[role=button]'));schedule();}
 const css=`html,body,body *{cursor:none!important}#handCursor,#hand:not([src]),.cursor-guide{display:none!important}.unified-hand{position:fixed;left:0;top:0;width:82px;height:82px;object-fit:contain;pointer-events:none!important;z-index:2147483647;display:none;will-change:transform}.unified-hand.touching{filter:drop-shadow(0 0 5px #fff0bd)}`;
 function install(doc){if(!doc?.body||installed.has(doc))return;const controller=new AbortController(),win=doc.defaultView;installed.set(doc,controller);bindings.add(controller);const style=doc.createElement('style');style.textContent=css;doc.head.append(style);const on=(target,type,fn,options={})=>target.addEventListener(type,fn,{...options,signal:controller.signal});
  on(doc,'pointermove',e=>move(doc,e),{passive:true});on(doc,'pointerdown',e=>{move(doc,e);if(e.pointerType!=='touch'){pressed=true;pose('fist',touching);schedule();}},{passive:true});on(win,'pointerup',e=>{pressed=false;move(doc,e);},{passive:true});on(win,'pointercancel',hide);on(win,'blur',hide);on(doc.documentElement,'pointerleave',hide);on(doc,'keydown',e=>{if(e.key==='Tab')hide();});on(doc,'visibilitychange',()=>{if(doc.hidden)hide();});
  function bindFrame(frame){if(frameBindings.has(frame))return;const guard=new AbortController();const binding={guard,doc:null};frameBindings.set(frame,binding);
   function attach(){hide();try{if(binding.doc&&binding.doc!==frame.contentDocument)installed.get(binding.doc)?.();binding.doc=frame.contentDocument;install(binding.doc);}catch{}}
   frame.addEventListener('load',attach,{signal:guard.signal});attach();
  }
  function detach(frame){const binding=frameBindings.get(frame);if(!binding)return;binding.guard.abort();if(binding.doc)installed.get(binding.doc)?.();frameBindings.delete(frame);hide();}

  doc.querySelectorAll('iframe').forEach(bindFrame);
  const observer=new MutationObserver(records=>{for(const r of records){for(const n of r.addedNodes){if(n.nodeType!==1)continue;if(n.matches('iframe'))bindFrame(n);n.querySelectorAll('iframe').forEach(bindFrame);}for(const n of r.removedNodes){if(n.nodeType===1){if(n.matches('iframe'))detach(n);n.querySelectorAll('iframe').forEach(detach);}}}});observer.observe(doc.body,{childList:true,subtree:true});
  const cleanup=()=>{observer.disconnect();doc.querySelectorAll('iframe').forEach(detach);controller.abort();bindings.delete(controller);installed.delete(doc);hide();};installed.set(doc,cleanup);on(win,'pagehide',cleanup,{once:true});
 }
 install(document);window.addEventListener('temple-stage',hide);window.addEventListener('temple-cursor-reset',hide);
 window.TempleCursor={hide,get state(){return {visible,mode,pressed};},dispose(){hide();cancelAnimationFrame(raf);bindings.forEach(c=>c.abort());hand.remove();}};
})();
