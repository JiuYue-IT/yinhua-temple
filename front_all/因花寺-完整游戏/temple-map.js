(() => {
 const nodes=[{name:'桃花水院',x:48,y:96,stage:0,go:'home'},{name:'山门',x:53,y:89,stage:1,go:'gate'},{name:'天王殿',x:53,y:70,stage:2,go:'hall'},{name:'香炉 · 点香',x:53,y:55,stage:3,chapter:0},{name:'许愿池',x:65,y:48,stage:4,chapter:1},{name:'正殿 · 礼佛抽签',x:53,y:34,stage:5,chapter:2},{name:'菩提树',x:59,y:17,stage:6,chapter:3}];
 let furthest=Number(sessionStorage.getItem('temple-map-progress')||0),current=0;
 const style=document.createElement('style');style.textContent=`
 #mapToggle{position:fixed;left:42px;top:91px;z-index:350;padding:9px 13px;background:#2542378c;border:1px solid #f5e4bd70;border-radius:5px;color:#fff6de;font-size:13px;backdrop-filter:blur(8px)}
 #templeMap{position:fixed;inset:0;z-index:400;background:#12291ee8;display:grid;place-items:center;padding:24px}#templeMap[hidden]{display:none}
 .map-art{position:relative;width:min(94vw,1300px);aspect-ratio:1664/941}.map-art>img{width:100%;height:100%;border-radius:8px}
 .map-point{position:absolute;transform:translate(-50%,-50%);width:24px;height:24px;border:2px solid #fff1b4;border-radius:50%;background:#dab970;box-shadow:0 0 18px #ffe1a5;cursor:pointer}
 .map-point:disabled{background:#607267;border-color:#afb9a5;box-shadow:none;opacity:.6;cursor:default}.map-point[aria-current=true]{outline:5px solid #fff1b455;box-shadow:0 0 25px 8px #ffda88}
 .map-point span{display:none;position:absolute;left:50%;top:29px;transform:translateX(-50%);white-space:nowrap;padding:4px 8px;border-radius:4px;background:#21372de0;color:#fff5d6;font-size:12px}
 #mapToggle{transition:background .25s,box-shadow .25s,transform .25s}#mapToggle:hover,#mapToggle:focus-visible{background:#385e49c9;box-shadow:0 0 18px #f3d99d55;transform:translateY(-2px)}
 .map-point{transition:background .25s,box-shadow .25s,scale .25s}.map-point:hover,.map-point:focus-visible{background:#fff2bf;box-shadow:0 0 28px 10px #ffe3a58c;scale:1.18}.map-point.chosen{background:#fff8de;box-shadow:0 0 45px 20px #ffe6a9a0;scale:1.45}.map-point::after{content:'';position:absolute;inset:-6px;border:1px solid #ffe8af80;border-radius:50%;opacity:0}.map-point:hover::after{opacity:1}.map-point.chosen::after{animation:mapRipple .45s ease-out}#templeMap{transition:opacity .42s,transform .42s}#templeMap.departing{opacity:0;transform:scale(1.025);pointer-events:none}.map-arrival-veil{position:fixed;inset:0;background:#eef0e2;z-index:390;pointer-events:none;animation:mapReveal .65s ease-out forwards}@keyframes mapReveal{to{opacity:0}}@keyframes mapRipple{from{opacity:1;transform:scale(1)}to{opacity:0;transform:scale(3)}}@media(prefers-reduced-motion:reduce){#templeMap,.map-point,#mapToggle{transition:none}.map-arrival-veil{animation-duration:.1s}}
 #mapClose{position:absolute;right:24px;top:20px;background:#284338;border:1px solid #f0dfb980;color:#fff6dc;padding:10px 16px;border-radius:5px}
 .map-caption{position:absolute;left:24px;top:12px;color:#fff4d8;font-size:14px}.map-open #handCursor{display:none!important}
 @media(max-width:650px){#mapToggle{left:22px;top:70px}.map-point{width:19px;height:19px}.map-point span{display:none;font-size:10px;top:22px}.map-art{width:96vw}#templeMap{padding:8px}}

 #mapToggle{top:32px;left:330px;width:54px;height:54px;padding:5px;background:transparent;border:0;border-left:1px solid #49696140;border-radius:0;color:#42645c;backdrop-filter:none;margin-left:16px;overflow:visible}
 #mapToggle[hidden]{display:none!important}
 #mapToggle svg{display:block;width:44px;height:44px;margin-left:9px;transition:filter .3s,transform .3s}
 #mapToggle:hover,#mapToggle:focus-visible{background:transparent;box-shadow:none;transform:none}#mapToggle:hover svg,#mapToggle:focus-visible svg{filter:drop-shadow(0 0 7px #f8d890);transform:translateY(-1px)}
 .map-tooltip{position:absolute;top:62px;left:60%;transform:translateX(-50%) translateY(-3px);white-space:nowrap;background:#fff3dbe8;color:#395b51;padding:3px 13px;border:1px solid #e6d1a6;border-radius:18px;font:13px/1.7 SimSun,serif;opacity:0;pointer-events:none;transition:opacity .2s,transform .2s}
 #mapToggle:hover .map-tooltip,#mapToggle:focus-visible .map-tooltip{opacity:1;transform:translateX(-50%) translateY(0)}
 @media(max-width:650px){#mapToggle{width:44px;height:44px;margin-left:8px}#mapToggle svg{width:34px;height:34px;margin-left:4px}.map-tooltip{top:48px}}

 #mapToggle .map-scroll-art{position:absolute;left:8px;top:-5px;width:64px;height:64px;object-fit:contain;transform:none;transform-origin:50% 50%;transition:filter .3s,translate .3s}
 #mapToggle:hover .map-scroll-art,#mapToggle:focus-visible .map-scroll-art{filter:drop-shadow(0 0 5px #f8d890);translate:0 -1px}
 @media(max-width:650px){#mapToggle .map-scroll-art{width:52px;height:52px;left:4px;top:-2px}}

 #mapToggle .map-scroll-art{width:48px;height:48px;left:8px;top:3px}
 @media(max-width:650px){#mapToggle .map-scroll-art{width:40px;height:40px;left:4px;top:2px}}
 `;document.head.append(style);
 const toggle=document.createElement('button');toggle.id='mapToggle';toggle.innerHTML='<img class="map-scroll-art" src="assets/map-scroll-art.png" alt="" aria-hidden="true"><span class="map-tooltip">展开地图</span>';toggle.setAttribute('aria-label','打开寺院地图');toggle.setAttribute('aria-expanded','false');
 const modal=document.createElement('section');modal.id='templeMap';modal.hidden=true;modal.setAttribute('role','dialog');modal.setAttribute('aria-modal','true');modal.setAttribute('aria-label','寺院地图');
 const art=document.createElement('div');art.className='map-art';const img=document.createElement('img');img.src='assets/temple-map.png';img.alt='因花寺地图';art.append(img);
 const close=document.createElement('button');close.id='mapClose';close.textContent='收起地图 ×';
 const caption=document.createElement('p');caption.className='map-caption';caption.textContent='点击任意光点，前往对应场景';
 const buttons=nodes.map(n=>{const b=document.createElement('button');b.className='map-point';b.style.left=n.x+'%';b.style.top=n.y+'%';b.setAttribute('aria-label',n.name);const label=document.createElement('span');label.textContent=n.name;b.append(label);b.onclick=()=>{if(modal.classList.contains('departing'))return;b.classList.add('chosen');modal.classList.add('departing');setTimeout(()=>{closeMap();if(n.go)window.templeNavigate(n.go);else window.visitContinuation(n.chapter);modal.classList.remove('departing');b.classList.remove('chosen');const veil=document.createElement('div');veil.className='map-arrival-veil';document.body.append(veil);setTimeout(()=>veil.remove(),700);},420);};art.append(b);return b;});
 function render(){toggle.hidden=current===0;buttons.forEach((b,i)=>{b.disabled=false;b.setAttribute('aria-current',String(nodes[i].stage===current));});}
 function closeMap(){modal.hidden=true;document.body.classList.remove('map-open');toggle.setAttribute('aria-expanded','false');toggle.focus();}
 toggle.onclick=()=>{render();modal.hidden=false;document.body.classList.add('map-open');toggle.setAttribute('aria-expanded','true');close.focus();};close.onclick=closeMap;
 modal.addEventListener('keydown',e=>{if(e.key==='Escape'){e.stopPropagation();closeMap();}if(e.key==='Tab'){const focusable=[...buttons.filter(b=>!b.disabled),close];const i=focusable.indexOf(document.activeElement);if(e.shiftKey&&i<=0){e.preventDefault();close.focus();}else if(!e.shiftKey&&i===focusable.length-1){e.preventDefault();focusable[0].focus();}}});
 modal.append(art,close,caption);document.body.append(toggle,modal);
 window.addEventListener('temple-stage',e=>{current=e.detail;furthest=Math.max(furthest,current);sessionStorage.setItem('temple-map-progress',furthest);render();});
 const courtyard=document.querySelector('#courtyard');new MutationObserver(()=>{if(!courtyard.hidden){current=3;furthest=Math.max(furthest,3);sessionStorage.setItem('temple-map-progress',furthest);render();}}).observe(courtyard,{attributes:true,attributeFilter:['hidden']});
 function syncMapVisibility(){const intro=document.querySelector('#prologue');const question=document.querySelector('#question');const later=document.body.classList.contains('in-hall')||!document.querySelector('#continuation').hidden;const atGate=question&&Number(getComputedStyle(question).opacity)>.8&&window.scrollY>innerHeight*.8;const hide=!intro.hidden||(!later&&!atGate);if(toggle.hidden!==hide)toggle.hidden=hide;}
 new MutationObserver(syncMapVisibility).observe(document.body,{attributes:true,subtree:true,attributeFilter:['hidden','class','style']});window.addEventListener('scroll',syncMapVisibility,{passive:true});syncMapVisibility();
 const chapter=document.querySelector('.chapter');
 function alignToggle(){const r=chapter.getBoundingClientRect();toggle.style.left=r.right+'px';const height=matchMedia('(max-width:650px)').matches?44:54;toggle.style.top=(r.top+(r.height-height)/2)+'px';}
 new ResizeObserver(alignToggle).observe(chapter);window.addEventListener('resize',alignToggle);alignToggle();
 render();
})();
