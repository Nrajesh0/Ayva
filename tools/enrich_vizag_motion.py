from pathlib import Path
import re,json,sys
# Usage: python tools/enrich_vizag_motion.py approved-gallery.html output-directory
ROOT=Path(sys.argv[2]);ROOT.mkdir(parents=True,exist_ok=True)
h=Path(sys.argv[1]).read_text()
extra_defs=[]; extra_js=[];inventory={}
# Coordinate systems are those of each approved source illustration.
configs={
0:dict(palm=(91,39,126,97,155,76),waves=[(600,985,268,7),(650,985,286,6)],birds=(420,65,170)),
1:dict(waves=[(745,981,599,4)],birds=(480,377,205)),
2:dict(waves=[(45,980,856,7),(70,980,876,5)],birds=(210,679,240),boat=(810,824)),
3:dict(waves=[(855,1420,380,3)],birds=(1080,245,200)),
4:dict(birds=(225,445,210),flag=(696,451,558),walker=(155,652,558)),
5:dict(palm=(906,936,86,80,945,965),waves=[(40,984,1137,6),(45,984,1150,4)],birds=(330,958,310)),
6:dict(palm=(88,1205,150,119,157,1256),waves=[(355,980,1400,6),(350,980,1430,6)],birds=(330,1237,240)),
7:dict(waves=[(830,1440,754,3),(840,1440,765,2)],birds=(1170,623,180),flag=(1374,658,694)),
8:dict(birds=(435,810,200),flag=(477,839,871),walker=(210,618,955))
}
for i,c in configs.items():
 pat=rf'(<article class="card" data-scene="{i}">.*?<svg\b[^>]*>)(.*?)(</svg>)'
 m=re.search(pat,h,re.S);assert m
 art=m[2]; new=[];features=[]
 if 'palm' in c:
  x,y,w,hh,px,py=c['palm'];cid=f'life-palm-{i}';mask=f'life-mask-{i}'
  # Remove the original crown before placing its exact source pixels in a swaying layer.
  extra_defs.append(f'<clipPath id="{cid}"><rect x="{x}" y="{y}" width="{w}" height="{hh}"/></clipPath><mask id="{mask}" maskUnits="userSpaceOnUse" x="0" y="0" width="1600" height="1600"><rect width="1600" height="1600" fill="white"/><g clip-path="url(#{cid})"><use href="#revisedTexture" class="erase"/></g></mask>')
  art=f'<g mask="url(#{mask})">{art}</g>'
  new.append(f'<g id="{cid}"><g clip-path="url(#{cid})"><use class="raster" href="#revisedTexture"/></g></g>')
  # ClipPath and animated group need distinct IDs.
  new[-1]=new[-1].replace(f'<g id="{cid}">',f'<g id="{cid}-moving">')
  extra_js.append(f"q('#{cid}-moving').setAttribute('transform',`rotate(${{Math.sin(p)*7.5+Math.sin(p*2)*1.5}} {px} {py})`);")
  features+=['swaying palm crown']
 for j,(a,b,y,amp) in enumerate(c.get('waves',[])):
  ident=f'life-wave-{i}-{j}';new.append(f'<path id="{ident}" fill="none" stroke="#04b8dc" stroke-width="{2.4 if i in [0,1,2,5,6] else 1.6}" stroke-linecap="round"/>')
  extra_js.append(f"q('#{ident}').setAttribute('d',wave({a},{b},{y},{amp},p*{2 if j==0 else -2}+{j}));")
  features+=['rolling surf '+str(j+1)]
 if 'birds' in c:
  x,y,travel=c['birds'];ident=f'life-birds-{i}'
  new.append(f'<g id="{ident}" fill="none" stroke="var(--ink)" stroke-width="{1.7 if i in [0,1,2,5,6] else 1.2}" stroke-linecap="round"><path class="wing"/><path class="wing" transform="translate(25 8) scale(.75)"/></g>')
  extra_js.append(f"{{const g=q('#{ident}');g.setAttribute('transform',`translate(${{{x}+{travel}*(.5-.5*Math.cos(p))}} ${{{y}+Math.sin(p*2)*4}})`);g.querySelectorAll('.wing').forEach(a=>a.setAttribute('d',`M-9 ${{-4*Math.sin(p*8)}}Q-4 -5 0 0Q4 -5 9 ${{-4*Math.sin(p*8)}}`));}}")
  features+=['gliding birds with flapping wings']
 if 'boat' in c:
  x,y=c['boat'];ident=f'life-dinghy-{i}'
  new.append(f'<g id="{ident}" fill="none" stroke-linecap="round" stroke-width="2"><path d="M-19 0L-12 6H12L19 0Z" stroke="#ff6650"/><path d="M0 0V-18L12 -5H0" stroke="var(--ink)"/></g>')
  extra_js.append(f"q('#{ident}').setAttribute('transform',`translate(${{{x}+Math.sin(p)*46}} ${{{y}+Math.sin(p*2)*3}}) rotate(${{Math.sin(p)*4}})`);")
  features+=['bobbing coastal sailboat']
 if 'flag' in c:
  x,y,base=c['flag'];ident=f'life-flag-{i}'
  new.append(f'<path d="M{x} {base}V{y}" fill="none" stroke="var(--ink)" stroke-width="1.2"/><g id="{ident}" fill="none" stroke-width="1.6" stroke-linecap="round"><path stroke="#f4ac62"/><path stroke="var(--ink)"/><path stroke="#46ae89"/></g>')
  extra_js.append(f"q('#{ident}').querySelectorAll('path').forEach((a,j)=>{{let yy={y}+j*3;a.setAttribute('d',`M{x} ${{yy}}Q{x+5} ${{yy+Math.sin(p*6)*2.5}} {x+10} ${{yy}}T{x+21} ${{yy+Math.sin(p*6+1)*2.5}}`);}});")
  features+=['fluttering flag']
 if 'walker' in c:
  a,b,y=c['walker'];ident=f'life-walker-{i}'
  new.append(f'<g id="{ident}" fill="none" stroke="var(--ink)" stroke-width="1.3" stroke-linecap="round"><circle cy="-12" r="2"/><path d="M0 -10V-4M0 -8L-4 -5M0 -8L4 -6"/><path class="legs"/></g>')
  extra_js.append(f"{{const g=q('#{ident}'),t0=(t/8)%1;g.setAttribute('transform',`translate(${{{a}+({b}-{a})*t0}} {y})`);g.style.opacity=Math.min(1,t0*14,(1-t0)*14);g.querySelector('.legs').setAttribute('d',`M0 -4L${{Math.sin(p*16)*3}} 0M0 -4L${{-Math.sin(p*16)*3}} 0`);}}")
  features+=['walking visitor']
 replacement=m[1]+art+''.join(new)+m[3]
 h=h[:m.start()]+replacement+h[m.end():];inventory[str(i+1)]=features
h=h.replace('</defs></svg><main>',''.join(extra_defs)+'</defs></svg><main>',1)
extra_js.append("q('#life-flag-8').querySelectorAll('path').forEach((a,j)=>{if(j){a.style.display='none';return;}a.setAttribute('stroke','#eda451');a.setAttribute('d',`M477 839Q485 ${839+Math.sin(p*6)*2} 498 ${842+Math.sin(p*6+1)*2}L477 849Z`);});")
js="""
const beforeLife=draw;
function wave(a,b,y,amp,phase){let d='';for(let x=a;x<=b;x+=8){const yy=y+amp*Math.sin((x-a)/(b-a)*Math.PI*3+phase);d+=(x===a?'M':'L')+x+' '+yy;}return d;}
draw=function(t){beforeLife(t);const p=t*Math.PI/4;
"""+''.join(extra_js)+"};nine.draw=draw;draw(0);"
h=h.replace('</script>',js+'</script>')
h=h.replace('Vizag · revised outline animations.','Vizag · a little more life.').replace('Five revised outline designs, alongside the four approved Originals. Choose a scene to review it up close.','Swaying palms, rolling surf, birds and everyday life. All nine approved places in motion.')
h=h.replace('not yet integrated into Ayva','Settings footer animation review')
(ROOT/'index.html').write_text(h)
(ROOT/'motion-inventory.json').write_text(json.dumps(inventory,indent=2))
print('Added independent environmental motion to all nine scenes')
