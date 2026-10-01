if(new URLSearchParams(location.search).get('from')==='incense'){
 const veil=document.createElement('div');veil.className='pool-arrival-smoke';veil.setAttribute('aria-hidden','true');document.body.append(veil);
 const reveal=()=>{requestAnimationFrame(()=>requestAnimationFrame(()=>veil.classList.add('reveal')));veil.addEventListener('transitionend',()=>veil.remove(),{once:true});};
 if(document.readyState==='complete')reveal();else window.addEventListener('load',reveal,{once:true});
}
