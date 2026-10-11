(()=>{
'use strict';
const style=document.createElement('style');style.textContent='.official-courtyard-copy{position:absolute;left:50%;top:18%;transform:translateX(-50%);width:85%;text-align:center;color:#fff9e9;font:600 clamp(18px,2vw,27px)/1.9 var(--temple-font);text-shadow:0 2px 3px #153026a8,0 5px 16px #1530267a;pointer-events:none}.official-courtyard-copy small{display:block;font:400 clamp(14px,1.3vw,18px)/2 var(--temple-ui)}';document.head.append(style);
const courtyard=document.querySelector('#courtyard');const copy=document.createElement('div');copy.className='official-courtyard-copy';copy.innerHTML='慢一点。<small>把刚才想到的事情，<br>再带在心里走一段。</small>';courtyard.append(copy);
const notice=document.querySelector('#notice');window.addEventListener('temple-stage',e=>{if(e.detail===2){notice.textContent='门已为你打开。有些问题，不必急着在门外想明白。';setTimeout(()=>{if(notice.textContent.startsWith('门已为你打开'))notice.textContent='';},5000);}});
})();
