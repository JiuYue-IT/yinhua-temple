(() => {
 'use strict';
 const overlay=document.createElement('section');overlay.id='continuation';overlay.hidden=true;overlay.setAttribute('aria-label','因花寺后续旅程');overlay.style.cssText='position:fixed;inset:0;z-index:120;background:#213d3c';
 const veil=document.createElement('div');veil.className='chapter-veil';veil.hidden=true;veil.setAttribute('role','status');
 const message=document.createElement('p');message.textContent='静候片刻，前路渐明';const retry=document.createElement('button');retry.textContent='重新准备';retry.hidden=true;veil.append(message,retry);overlay.append(veil);document.body.append(overlay);
 const chapters=[{title:'点香仪式',src:'original/incense/index.html',done:'incense-complete'}, {title:'许愿池 · 寄愿',src:'original/chapters/wish/index.html?from=incense',done:'wish-complete'}, {title:'无问殿 · 礼佛与抽签',src:'original/chapters/hall/index.html?from=wishing-pool',done:'hall-complete'}, {title:'菩提树下',src:'original/chapters/bodhi/index.html?from=daxiong-baodian&v=palm-catch-aligned',done:'bodhi-complete'}];
 let current=-1,wasSound=false,frame=null,pending=null,epoch=0,busy=false;
 const warmImages=new Map();
 async function warm(i){if(!chapters[i])return;try{const url=new URL(chapters[i].src,document.baseURI);const html=await fetch(url).then(r=>{if(!r.ok)throw Error('page');return r.text();});const doc=new DOMParser().parseFromString(html,'text/html');const urls=[...doc.querySelectorAll('img[src]')].slice(0,4).map(im=>new URL(im.getAttribute('src'),url).href);if(i===0)urls.push(new URL('assets/temple.png',url).href);for(const src of urls){if(warmImages.has(src))continue;const image=new Image();image.src=src;warmImages.set(src,image);image.decode().catch(()=>{});}for(const el of doc.querySelectorAll('script[src],link[rel=stylesheet]'))fetch(new URL(el.getAttribute('src')||el.getAttribute('href'),url)).catch(()=>{});}catch{} }
 function releaseWarm(){for(const im of warmImages.values())im.src='';warmImages.clear();}
 function cursorReset(){window.dispatchEvent(new Event('temple-cursor-reset'));}
 async function show(i){if(!chapters[i]||busy)return;busy=true;const id=++epoch;cursorReset();overlay.hidden=false;veil.hidden=false;veil.classList.remove('revealing');retry.hidden=true;message.textContent='静候片刻，前路渐明';
  const next=document.createElement('iframe');pending=next;next.title=chapters[i].title;next.allow='autoplay; microphone';next.style.cssText='position:absolute;inset:0;width:100%;height:100%;border:0;display:block;visibility:hidden';next.inert=true;
  let timeout;
  try{await new Promise((resolve,reject)=>{timeout=setTimeout(()=>reject(Error('页面准备超时')),15000);next.addEventListener('load',async()=>{try{const doc=next.contentDocument;if(!doc?.body||!doc.querySelector('script'))throw Error('页面未准备好');await Promise.all([...doc.images].slice(0,4).map(im=>im.decode().catch(()=>{})));resolve();}catch(e){reject(e);}},{once:true});next.src=chapters[i].src;overlay.insertBefore(next,veil);});
   if(id!==epoch)return;const old=frame;frame=next;pending=null;current=i;next.style.visibility='visible';next.inert=false;old?.remove();releaseWarm();busy=false;window.dispatchEvent(new CustomEvent('temple-stage',{detail:i+3}));window.dispatchEvent(new CustomEvent('temple-chapter-ready',{detail:{frame:next,index:i}}));
   await new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve)));if(id!==epoch)return;veil.classList.add('revealing');setTimeout(()=>{if(id===epoch){veil.hidden=true;next.focus({preventScroll:true});}},matchMedia('(prefers-reduced-motion:reduce)').matches?100:650);warm(i+1);
  }catch(error){if(id!==epoch)return;next.remove();pending=null;busy=false;message.textContent='前路暂未准备好，请再试一次';retry.hidden=false;retry.onclick=()=>show(i);}finally{clearTimeout(timeout);}
 }
 document.querySelector('#replay').addEventListener('click',()=>window.TempleFlow.reset().catch(()=>{}));
 window.startContinuation=()=>{if(current>=0||busy)return;wasSound=window.templeAudio?.().enabled||false;if(wasSound)window.toggleTempleAmbience();show(0);};
 window.closeContinuation=()=>{epoch++;busy=false;current=-1;pending?.remove();pending=null;frame?.remove();frame=null;overlay.hidden=true;veil.hidden=true;releaseWarm();document.querySelector('#journey-end')?.remove();cursorReset();};
 window.visitContinuation=i=>{window.closeContinuation();wasSound=window.templeAudio?.().enabled||false;if(wasSound)window.toggleTempleAmbience();show(i);};
 const courtyardButton=document.querySelector('#returnToGate');courtyardButton.textContent='继续入寺 · 点一炷香';courtyardButton.onclick=window.startContinuation;
 function finish(){frame?.remove();frame=null;current=-1;const ending=document.createElement('div');ending.id='journey-end';ending.className='journey-end';const title=document.createElement('h2');title.textContent='果已得，路仍长。';const copy=document.createElement('p');copy.textContent='带着那一小步，回到日常里。风会记得你曾来过。';const restart=document.createElement('button');restart.textContent='再走一遍';restart.onclick=()=>{window.closeContinuation();document.querySelector('#replay').click();if(wasSound)window.toggleTempleAmbience();};ending.append(title,copy,restart);overlay.append(ending);restart.focus();cursorReset();releaseWarm();}
 window.addEventListener('message',event=>{if(event.origin!==location.origin||event.source!==frame?.contentWindow||current<0||busy)return;if(event.data?.type!==chapters[current].done)return;if(current===chapters.length-1)finish();else show(current+1);});
 window.addEventListener('pagehide',window.closeContinuation);warm(0);
})();
