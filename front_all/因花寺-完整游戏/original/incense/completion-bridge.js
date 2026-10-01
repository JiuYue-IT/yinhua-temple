(() => {
 let sent=false;
 const observer=new MutationObserver(()=>{
  const rest=document.querySelector('.final-smoke-rest');if(!rest||sent)return;
  sent=true;observer.disconnect();const button=rest.querySelector('button');
  function next(){if(window.parent!==window)window.parent.postMessage({type:'incense-complete'},location.origin);else location.href='../chapters/wish/index.html?from=incense';}
  if(button){button.textContent='前往许愿池 →';button.onclick=next;}
  setTimeout(next,1200);
 });observer.observe(document.body,{childList:true,subtree:true});
})();
