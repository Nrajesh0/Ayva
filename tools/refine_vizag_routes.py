"""Make vehicle fronts and direction unambiguous; use the entire Kailasagiri cable.
Usage: python tools/refine_vizag_routes.py corrected-gallery.html output-directory
"""
from pathlib import Path
import sys
root=Path(sys.argv[2]);root.mkdir(parents=True,exist_ok=True)
h=Path(sys.argv[1]).read_text()
js=r'''
for(const g of vehicleNodes){const d=JSON.parse(g.dataset.revised),origin=d.origin[0];
 const shape=svgNode('g',{transform:`translate(${2*origin} 0) scale(-1 1)`});
 while(g.firstChild)shape.appendChild(g.firstChild);g.appendChild(shape);g.dataset.facing='right';
}
const priorRoute=draw;
draw=function(t){priorRoute(t);
 for(const g of vehicleNodes){const d=JSON.parse(g.dataset.revised),v=(t/16+d.phase)%1,dx=(v-.45)*d.range;
  g.setAttribute('transform',`translate(${dx} ${-dx*.065})`);g.style.opacity=fadePath(v);
 }
 for(const g of revised){const d=JSON.parse(g.dataset.revised);if(d.kind!=='gondola'&&d.kind!=='hanger')continue;
  const progress=(t/16+(d.id==='cabin1'?.1:.6))%1;
  const x=d.id==='cabin1'?319+576*progress:895-576*progress;
  const y=-0.000071157139*x*x+0.272223970628*x+316.170524477112;
  g.setAttribute('transform',`translate(${x-d.origin[0]} ${y-d.origin[1]})`);
  g.style.opacity=Math.min(1,progress*80,(1-progress)*80);
 }
};nine.draw=draw;draw(0);
'''
h=h.replace('</script>',js+'</script>')
h=h.replace('RK Beach promenade','RK Beach').replace('Dolphin’s Nose Lighthouse','Dolphin’s Nose').replace('Vizag Fishing Harbour','Fishing Harbour').replace('INS Kursura · S20','INS Kursura').replace('Tenneti Park · MV Maa','Tenneti Park')
(root/'index.html').write_text(h)
