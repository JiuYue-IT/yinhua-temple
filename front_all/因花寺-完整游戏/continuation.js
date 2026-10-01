(() => {
 const overlay=document.createElement('section');overlay.id='continuation';overlay.hidden=true;overlay.setAttribute('aria-label','银花寺后续旅程');
 overlay.style.cssText='position:fixed;inset:0;z-index:120;background:#213d3c';
 const frame=document.createElement('iframe');frame.title='点香仪式';frame.style.cssText='width:100%;height:100%;border:0;display:block';
 // Microphone is requested only by the chapter when the player chooses voice input.
 frame.allow='autoplay; microphone';overlay.append(frame);document.body.append(overlay);
 const chapters=[{title:'点香仪式',src:'original/incense/index.html',done:'incense-complete'},
 {title:'许愿池 · 寄愿',src:'original/chapters/wish/index.html?from=incense',done:'wish-complete'},
 {title:'大雄宝殿 · 礼佛与抽签',src:'original/chapters/hall/index.html?from=wishing-pool',done:'hall-complete'},
 {title:'菩提树下',src:'original/chapters/bodhi/index.html?from=daxiong-baodian&v=palm-catch-aligned',done:'bodhi-complete'}];
 let current=-1,wasSound=false;
 document.querySelector('#replay').addEventListener('click',()=>window.TempleFlow.reset().catch(()=>{}));
 function show(i){window.dispatchEvent(new CustomEvent('temple-stage',{detail:i+3}));current=i;overlay.hidden=false;frame.title=chapters[i].title;frame.src=chapters[i].src;frame.focus();}
 window.startContinuation=()=>{
  if(current>=0)return;wasSound=window.templeAudio?.().enabled||false;
  if(wasSound)window.toggleTempleAmbience();
  document.body.classList.remove('hand-cursor');show(0);
 };
 window.closeContinuation=()=>{current=-1;frame.src='about:blank';overlay.hidden=true;document.querySelector('#journey-end')?.remove();};
 window.visitContinuation=i=>{window.closeContinuation();wasSound=window.templeAudio?.().enabled||false;if(wasSound)window.toggleTempleAmbience();document.body.classList.remove('hand-cursor');show(i);};
 const courtyardButton=document.querySelector('#returnToGate');
 courtyardButton.textContent='继续入寺 · 点一炷香';
 courtyardButton.onclick=()=>window.startContinuation();
 courtyardButton.style.cssText='position:absolute;bottom:max(40px,6vh);left:50%;transform:translateX(-50%);min-height:58px;padding:16px 30px;color:#fff9eb;background:rgba(34,57,45,.35);border:1px solid #f4dfb6a0;border-radius:6px;backdrop-filter:blur(8px);font-size:18px;letter-spacing:.1em;text-shadow:none;box-shadow:0 5px 24px #18352838;z-index:10';
 function finish(){
  frame.src='about:blank';current=-1;
  const ending=document.createElement('div');ending.id='journey-end';ending.style.cssText='position:absolute;inset:0;display:grid;place-content:center;text-align:center;gap:24px;padding:30px;color:#fff5df;background:#213d3c;font-family:SimSun,serif';
  const title=document.createElement('h2');title.textContent='果已得，路仍长。';
  const copy=document.createElement('p');copy.textContent='带着那一小步，回到日常里。风会记得你曾来过。';
  const restart=document.createElement('button');restart.textContent='再走一遍';restart.style.cssText='background:transparent;border:1px solid #fff5df80;padding:14px;color:inherit';
  restart.onclick=()=>{ending.remove();overlay.hidden=true;document.querySelector('#replay').click();if(wasSound)window.toggleTempleAmbience();};
  ending.append(title,copy,restart);overlay.append(ending);restart.focus();
 }
 window.addEventListener('message',event=>{
  if(event.origin!==location.origin||event.source!==frame.contentWindow||current<0)return;
  if(event.data?.type!==chapters[current].done)return;
  if(current===chapters.length-1)finish();else show(current+1);
 });
})();
