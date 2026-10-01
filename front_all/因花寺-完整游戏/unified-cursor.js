(() => {
 const base=new URL('assets/',document.baseURI).href;
 const pictures={point:base+'cursor-point.png',fist:base+'cursor-fist.png',palm:base+'cursor-palm.png'};
 const installed=new WeakSet();
 function install(doc){
  if(!doc?.body||installed.has(doc))return;installed.add(doc);
  const win=doc.defaultView;if(!win.matchMedia('(pointer:fine)').matches)return;
  const style=doc.createElement('style');style.textContent=`html,body,body *{cursor:none!important}#handCursor,#hand{display:none!important}.unified-hand{position:fixed;left:0;top:0;width:82px;height:82px;object-fit:contain;pointer-events:none!important;z-index:2147483647;display:none;filter:drop-shadow(0 3px 3px #30221626);transform:translate(-12%,-15%)}.unified-hand.touching{filter:drop-shadow(0 0 7px #fff0bd) brightness(1.1)}.unified-hand.fist{transform:translate(-35%,-25%)}.unified-hand.palm{transform:translate(-38%,-40%)}`;
  doc.head.append(style);
  const hand=doc.createElement('img');hand.className='unified-hand';hand.alt='';hand.setAttribute('aria-hidden','true');hand.src=pictures.point;doc.body.append(hand);
  let pressed=false,mode='point';
  function setMode(next){if(mode===next)return;mode=next;hand.src=pictures[next];hand.className='unified-hand '+next;}
  function choose(target){
   if(pressed||target?.closest('.knock-target'))return 'fist';
   if(target?.closest('#fruit,#palm,#handLayer,.fruit,.palm')||doc.body.dataset.state==='receiving')return 'palm';
   return 'point';
  }
  doc.addEventListener('pointermove',e=>{if(e.pointerType==='touch')return;hand.style.display='block';hand.style.left=e.clientX+'px';hand.style.top=e.clientY+'px';setMode(choose(e.target));hand.classList.toggle('touching',!!e.target.closest('button,a,[role=button]'));},{passive:true});
  doc.addEventListener('pointerdown',e=>{if(e.pointerType==='touch')return;pressed=true;setMode('fist');},{passive:true});
  doc.addEventListener('pointerup',e=>{pressed=false;setMode(choose(e.target));},{passive:true});
  doc.addEventListener('pointercancel',()=>{pressed=false;setMode('point');});
  doc.documentElement.addEventListener('pointerleave',()=>{hand.style.display='none';pressed=false;setMode('point');});
  win.addEventListener('blur',()=>{hand.style.display='none';pressed=false;setMode('point');});
  function frames(){doc.querySelectorAll('iframe').forEach(frame=>{if(!frame.dataset.unifiedCursorBound){frame.dataset.unifiedCursorBound='true';frame.addEventListener('load',()=>{hand.style.display='none';try{install(frame.contentDocument);}catch{}});}try{install(frame.contentDocument);}catch{}});}
  frames();new MutationObserver(frames).observe(doc.body,{childList:true,subtree:true});
 }
 install(document);
})();
