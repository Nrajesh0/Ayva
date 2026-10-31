"""Isolate vehicles and make bird flights leave the viewport before looping.
Usage: python tools/fix_vizag_layers.py natural-gallery.html output-directory
"""
from pathlib import Path
import re,sys
root=Path(sys.argv[2]);root.mkdir(parents=True,exist_ok=True)
h=Path(sys.argv[1]).read_text()
js=r'''
// Vehicle sprites contain only vehicle geometry: no pixels from streetlights or railings.
const vehicleNodes=[...document.querySelectorAll('[data-revised]')].filter(g=>['bus','auto'].includes(JSON.parse(g.dataset.revised).id));
for(const g of vehicleNodes){const d=JSON.parse(g.dataset.revised);g.dataset.isolatedVehicle=d.id;
 if(d.id==='bus'){
  g.innerHTML='<path d="M192 195Q192 184 208 184H319L322 234L314 240H194Z" fill="#d7d1be" stroke="#73c6cf" stroke-width="2"/><path d="M192 216L322 211V234L314 240H194Z" fill="#398f9e" stroke="#73c6cf" stroke-width="1.4"/>';
  for(const x of[199,220,235,250,265,280,295])g.appendChild(svgNode('rect',{x,y:189,width:x===199?17:11,height:21,rx:1,fill:'#14292e',stroke:'#d7d1be','stroke-width':'1'}));
  for(const x of[215,295]){g.appendChild(svgNode('circle',{cx:x,cy:240,r:9,fill:'#142327',stroke:'#83cbd0','stroke-width':2}));g.appendChild(svgNode('circle',{cx:x,cy:240,r:4,fill:'#b7c7bf'}));}
  g.appendChild(svgNode('path',{d:'M307 188V232M195 223H201M194 232H202M313 229H318',fill:'none',stroke:'#e9d6aa','stroke-width':2}));
 }else{
  g.innerHTML='<path d="M117 225L123 201H173L178 227V244H115Z" fill="#cf9b35" stroke="#e9bf61" stroke-width="2"/><path d="M127 204H142V222H120ZM148 204H168L173 222H148Z" fill="#1d292b" stroke="#e9bf61" stroke-width="1.2"/><path d="M146 224V241M118 232H123M169 230H174" fill="none" stroke="#e9bf61" stroke-width="1.5"/>';
  for(const x of[128,162]){g.appendChild(svgNode('circle',{cx:x,cy:245,r:7,fill:'#172428',stroke:'#e9bf61','stroke-width':2}));g.appendChild(svgNode('circle',{cx:x,cy:245,r:2.5,fill:'#c1b383'}));}
 }
}
// Remove every earlier bird layer, including the original temple's bird animation.
for(const g of document.querySelectorAll('[data-motion]'))if(JSON.parse(g.dataset.motion).motion==='birds')g.remove();
const flights=[];
for(let i=0;i<9;i++){
 const bird=q('#life-birds-'+i),svg=bird.ownerSVGElement,b=svg.viewBox.baseVal;
 const id='continuous-flight-'+i,defs=svgNode('defs',{}),clip=svgNode('clipPath',{id});
 clip.appendChild(svgNode('rect',{x:b.x,y:b.y,width:b.width,height:b.height}));defs.appendChild(clip);svg.insertBefore(defs,svg.firstChild);
 const wrapper=svgNode('g',{'clip-path':`url(#${id})`});bird.parentNode.insertBefore(wrapper,bird);wrapper.appendChild(bird);
 flights.push({bird,x:b.x,width:b.width,y:naturalBirds[i][1]});
}
const rushi=q('[data-scene="6"] .art svg');rushi.setAttribute('viewBox','24 1158 980 298');
const paraglider=q('#natural-paraglider');paraglider.dataset.prominentParaglider='true';
const priorCorrection=draw;
draw=function(t){priorCorrection(t);const f=((t%16)+16)%16/16,p=t*Math.PI/4;
 for(const {bird,x,width,y}of flights){
  // The entire flock starts outside the left edge and exits beyond the right.
  // Both sides of the loop seam are invisible; nothing flies backwards to reset.
  const bx=x-80+(width+160)*f;
  bird.setAttribute('transform',`translate(${bx} ${y+Math.sin(p/2)*1.2})`);
  bird.style.opacity=(bx+50<x||bx-10>x+width)?'0':'1';
 }
 for(const g of vehicleNodes){const d=JSON.parse(g.dataset.revised),v=(t/16+d.phase)%1,dx=(.55-v)*d.range;
  // Both approved side profiles face LEFT: velocity must have a negative x sign.
  g.setAttribute('transform',`translate(${dx} ${-dx*.065})`);g.style.opacity=fadePath(v);
 }
 const pf=(f+.25)%1;paraglider.setAttribute('transform',`translate(${600+160*pf} ${1208+Math.sin(p/2)*1.2}) rotate(-2) scale(1.5)`);paraglider.style.opacity=fadePath(pf);
};nine.draw=draw;nine.loopSeconds=16;draw(0);
'''
h=h.replace('</script>',js+'</script>')
h=h.replace('Vizag · a calmer coastal rhythm.','Vizag · corrected flight and vehicle layers.')
(root/'index.html').write_text(h)
print('Clean vehicles, full-width one-way bird flight and prominent Rushikonda paraglider')
