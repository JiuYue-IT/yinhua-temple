(() => {
 let sent=false;
 function next(){if(sent)return;sent=true;if(window.parent!==window)window.parent.postMessage({type:'incense-complete'},location.origin);else location.href='../chapters/wish/index.html?from=incense';}
 const observer=new MutationObserver(()=>{const ritual=document.querySelector('.ritual'),zone=document.querySelector('.sensor-zone');
  let ignite=document.querySelector('.incense-ignite');if(zone&&!ignite){ignite=document.createElement('button');ignite.className='ritual-advance incense-ignite';ignite.textContent='点燃心香';ignite.onclick=()=>window.dispatchEvent(new KeyboardEvent('keydown',{key:' ',code:'Space',bubbles:true}));document.body.append(ignite);}if(ignite)ignite.hidden=!zone;
  const rest=document.querySelector('.final-smoke-rest');if(!rest)return;const button=rest.querySelector('button');if(button&&!button.dataset.bound){button.dataset.bound='true';button.textContent='前往许愿池 →';button.addEventListener('click',e=>{e.preventDefault();e.stopImmediatePropagation();next();},true);}});
 observer.observe(document.body,{childList:true,subtree:true,attributes:true,attributeFilter:['data-state']});window.addEventListener('pagehide',()=>observer.disconnect());
})();
