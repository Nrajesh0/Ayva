"""Refine the approved animation gallery: calm motion and selective vehicle fills.
Usage: python tools/naturalize_vizag_motion.py living-gallery.html output-directory
"""
from pathlib import Path
import sys,re
root=Path(sys.argv[2]);root.mkdir(parents=True,exist_ok=True)
h=Path(sys.argv[1]).read_text()
# Add vehicle paint beneath the approved texture outlines, in the same moving group.
js=r'''
function svgNode(tag,attrs){const n=document.createElementNS('http://www.w3.org/2000/svg',tag);for(const[k,v]of Object.entries(attrs))n.setAttribute(k,v);return n;}
function prependShapes(group,shapes){for(const s of [...shapes].reverse())group.insertBefore(svgNode(s[0],s[1]),group.firstChild);}
const revised=[...document.querySelectorAll('[data-revised]')];
for(const g of revised){const d=JSON.parse(g.dataset.revised);if(d.id==='bus'){
 const matte=g.querySelector(':scope > path');if(matte)matte.setAttribute('fill','var(--bg)');
 const shapes=[['path',{d:'M192 195Q192 184 208 184H319L322 234L314 240H194Z',fill:'#d7d1be'}],['path',{d:'M192 216L322 211V234L314 240H194Z',fill:'#398f9e'}]];
 for(const x of [199,220,235,250,265,280,295])shapes.push(['rect',{x,y:189,width:x===199?17:11,height:21,rx:1,fill:'#14292e'}]);
 for(const x of [215,295])shapes.push(['circle',{cx:x,cy:242,r:9,fill:'#142327'}]);
 const texture=g.querySelector('g');for(const s of shapes)g.insertBefore(svgNode(s[0],s[1]),texture);
 }if(d.id==='auto'){
 const shapes=[['path',{d:'M117 225L123 201H173L178 227V244H115Z',fill:'#cf9b35'}],['path',{d:'M127 203H143V223H120ZM148 203H168L173 222H148Z',fill:'#1d292b'}]];
 for(const x of [128,162])shapes.push(['circle',{cx:x,cy:247,r:7,fill:'#172428'}]);
 const texture=g.querySelector('g');for(const s of shapes)g.insertBefore(svgNode(s[0],s[1]),texture);
 }}
q('#h-boat-left .backfill').style.fill='#ae5545';q('#h-boat-right .backfill').style.fill='#285951';
function underScene(i,shapes){prependShapes(q(`[data-scene="${i}"] .art svg`),shapes);}
underScene(5,[['path',{d:'M96 1086L625 1026L609 1056L104 1102Z',fill:'#29454b'}],['path',{d:'M104 1102L609 1056L582 1109L111 1114Z',fill:'#b8614d'}],['path',{d:'M110 1049L129 1046V1029H216V1072L110 1086Z',fill:'#2c484b'}]]);
underScene(4,[['path',{d:'M164 514Q274 490 388 491H622Q671 490 673 513Q674 535 625 535H245Z',fill:'#273e43'}]]);
underScene(7,[['path',{d:'M1181 705L1420 711L1407 730H1195Z',fill:'#29474a'}]]);
q('#life-dinghy-2 path').setAttribute('fill','#bd7157');
const para=svgNode('g',{id:'natural-paraglider',stroke:'var(--ink)','stroke-width':'1.4','stroke-linecap':'round',fill:'none'});
para.innerHTML='<path d="M-35 0Q0 -28 35 0Q0 -10 -35 0Z" fill="#c97155" stroke="#e5b576"/><path d="M-15 -9Q0 -20 15 -9" stroke="#e2b76d"/><path d="M-32 -1L-4 28M-15 -8L-4 28M15 -8L4 28M32 -1L4 28"/><circle cx="0" cy="24" r="2.2" fill="var(--ink)"/><path d="M0 27V34L-4 40M0 34L4 40M-4 28Q0 33 4 28"/>';
q('[data-scene="6"] .art svg').appendChild(para);
const priorNatural=draw;
function fadePath(p){return Math.min(1,p*10,(1-p)*10);}
draw=function(t){priorNatural(t);const p=t*Math.PI/4,progress=(t/8)%1;
 // Long-distance travel follows one direction; breathing and small wave lift stay local.
 for(let i=0;i<9;i++){
  const g=q('#life-birds-'+i),cfg=naturalBirds[i];if(!g)continue;const f=(progress+.23+i*.13)%1;
  g.setAttribute('transform',`translate(${cfg[0]+cfg[2]*f} ${cfg[1]+Math.sin(p)*1.5})`);
  g.style.opacity=fadePath(f);
  g.querySelectorAll('.wing').forEach((wing,j)=>{let lift=2.2*Math.sin(p*3+j*.6);wing.setAttribute('d',`M-9 ${lift}Q-4 -3 0 0Q4 -3 9 ${lift}`)});
 }
 // Short, unhurried walking distances match the figures' scale.
 for(const g of document.querySelectorAll('[data-motion]')){
  const d=JSON.parse(g.dataset.motion);if(d.motion!=='walk')continue;
  const f=(progress+(d.phase||0))%1,dir=d.b>d.a?1:-1,anchor=(d.a+d.b)/2-36*dir;
  g.setAttribute('transform',`translate(${anchor+72*dir*f} ${d.y})`);g.style.opacity=fadePath(f);
  const legs=g.querySelector('.legs');if(legs){let s=Math.sin(p*8)*1.7;legs.setAttribute('d',`M0 -3L${s} 1M0 -3L${-s} 1`)}
 }
 for(const i of [4,8]){const g=q('#life-walker-'+i),d=naturalWalkers[i],f=(progress+(i===4?.2:.6))%1;g.setAttribute('transform',`translate(${d[0]+64*f} ${d[1]})`);g.style.opacity=fadePath(f);const s=Math.sin(p*8)*1.7;g.querySelector('.legs').setAttribute('d',`M0 -4L${s} 0M0 -4L${-s} 0`);}
 for(const g of revised){const d=JSON.parse(g.dataset.revised);
  if(d.kind==='windsurf'){const f=(progress+.41)%1;g.setAttribute('transform',`translate(${-18+36*f} ${Math.sin(p)*.8}) rotate(${Math.sin(p)*.6} ${d.origin[0]} ${d.origin[1]})`);g.style.opacity=fadePath(f);}
  if(d.kind==='gondola'||d.kind==='hanger'){const f=(progress+(d.id==='cabin1'?.18:.54))%1;const dx=(f-.5)*42*(d.id==='cabin1'?1:-1);g.setAttribute('transform',`translate(${dx} ${dx*.175})`);g.style.opacity=fadePath(f);}
  if(d.kind==='visitor'){const f=(progress+.33)%1;g.setAttribute('transform',`translate(${-12+24*f} 0)`);g.style.opacity=fadePath(f);}
 }
 const boatProgress=(progress+.37)%1;const dinghy=q('#life-dinghy-2');dinghy.setAttribute('transform',`translate(${790+32*boatProgress} ${824+Math.sin(p)*.9}) rotate(${Math.sin(p)*.8})`);dinghy.style.opacity=fadePath(boatProgress);
 // A broad, steady coastal air current, rather than a pendulum-like reversal.
 const paraProgress=(progress+.14)%1;para.setAttribute('transform',`translate(${570+100*paraProgress} ${1230+10*paraProgress+Math.sin(p)*1.5}) rotate(-3)`);para.style.opacity=fadePath(paraProgress);
 // Wind is subtle at this scale; keep land, trees' trunks and buildings anchored.
 for(const[i,origin]of [[0,[155,76]],[5,[945,965]],[6,[157,1256]]])q('#life-palm-'+i+'-moving').setAttribute('transform',`rotate(${Math.sin(p)*3+Math.sin(p*2)*.5} ${origin[0]} ${origin[1]})`);
};nine.draw=draw;draw(0);
'''
configs={0:[420,65,170],1:[480,377,205],2:[210,679,240],3:[1080,245,200],4:[225,445,210],5:[330,958,310],6:[330,1237,240],7:[1170,623,180],8:[435,810,200]}
import json
js='const naturalBirds='+json.dumps(configs)+';const naturalWalkers={4:[345,558],8:[365,955]};'+js

filters='<filter id="naturalFoliage" color-interpolation-filters="sRGB"><feColorMatrix type="matrix" values="1 0 0 0 0 0 1 0 0 0 0 0 1 0 0 -3 4 -2 0 -.04"/></filter><filter id="naturalEraseFoliage" color-interpolation-filters="sRGB"><feColorMatrix type="matrix" values="0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 -3 4 -2 0 -.04"/></filter>'
h=h.replace('</defs></svg><main>',filters+'</defs></svg><main>',1)
h=re.sub(r'(<mask id="life-mask-.*?</mask>)',lambda m:m[0].replace('class="erase"','class="naturalEraseFoliage"'),h,flags=re.S)
h=re.sub(r'(<g id="life-palm-\d-moving">.*?</g></g>)',lambda m:m[0].replace('class="raster"','class="naturalFoliage"'),h,flags=re.S)
h=h.replace('</style>','.naturalFoliage{filter:url(#naturalFoliage)}.naturalEraseFoliage{filter:url(#naturalEraseFoliage)}body[data-mode=light] .naturalFoliage{filter:url(#naturalFoliage) invert(1) hue-rotate(180deg)}</style>')

h=h.replace('</script>',js+'</script>')
h=h.replace('Vizag · a little more life.','Vizag · a calmer coastal rhythm.').replace('Swaying palms, rolling surf, birds and everyday life. All nine approved places in motion.','Unhurried walkers, gliding birds, painted vehicles and paragliding over Rushikonda.')
(root/'index.html').write_text(h)
print('Prepared natural movement and painted vehicles')
