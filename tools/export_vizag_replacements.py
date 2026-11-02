"""Build compact native layer atlases for the five approved Settings replacements.

Usage: python tools/export_vizag_replacements.py OUTPUT [--install]
Requires Pillow, numpy, Playwright and Chromium. Only the explicit five-scene
allowlist is installed; the four retained scenes and their manifests stay intact.
"""
from pathlib import Path
import argparse,base64,hashlib,io,json,shutil
import numpy as np
from PIL import Image
from playwright.sync_api import sync_playwright

ROOT=Path(__file__).resolve().parents[1]
SOURCE=ROOT/'tools/vizag_replacements'
SCENES={'rk_beach':(66,354),'kailasagiri':(366,652),'dolphins_nose':(663,936),'tenneti_park':(945,1221),'rushikonda':(1255,1579)}
LABELS=[(0,122,215,43),(0,385,165,50),(0,685,280,50),(0,969,235,44),(0,1255,230,48)]


def ink(image,light=False):
    a=np.array(image.convert('RGBA'));rgb=a[:,:,:3].astype(float)
    high=rgb.max(axis=2);gold=(rgb[:,:,0]>rgb[:,:,2]*1.4)&(rgb[:,:,1]>rgb[:,:,2]*1.25)
    visible=(high>57)|(gold&(high>38))
    if light:
        grey=(rgb*np.array([.2126,.7152,.0722])).sum(axis=2)
        colour=np.clip(2*abs(rgb-grey[:,:,None]).sum(axis=2)/255-.16,0,1)
        rgb=.72*rgb*colour[:,:,None]+(255-grey[:,:,None])*(1-colour[:,:,None])
    a[:,:,:3]=np.clip(rgb,0,255).astype('uint8');a[:,:,3]=np.where(visible,a[:,:,3],0);a[a[:,:,3]==0]=0
    return Image.fromarray(a)


def pack(tiles,width=576):
    positions={};x=y=row=0
    for name,image in sorted(tiles.items(),key=lambda p:(-p[1].height,-p[1].width)):
        if x+image.width+2>width:x=0;y+=row+2;row=0
        positions[name]=[x,y,image.width,image.height];x+=image.width+2;row=max(row,image.height)
    atlas=Image.new('RGBA',(width,y+row))
    for name,box in positions.items():atlas.paste(tiles[name],box[:2])
    return atlas,positions


def build(output):
    output.mkdir(parents=True,exist_ok=True)
    source=(SOURCE/'scenes.js').read_text().replace('ATLAS_URI','data:image/webp;base64,'+base64.b64encode((SOURCE/'approved.webp').read_bytes()).decode())
    controls='<canvas id="art" width="995" height="1581"></canvas>'+''.join(f'<button id="{k}"></button>'for k in ['pause','reference','restart','theme','state'])+'<input id="compact" type="checkbox">'
    report={};manifest={}
    with sync_playwright() as p:
        browser=p.chromium.launch(executable_path='/usr/bin/chromium',args=['--no-sandbox']);page=browser.new_page();page.set_content(controls);page.add_script_tag(content=source);page.wait_for_function('window.ready===true')
        page.evaluate('''labels=>{manual=true;for(const[x,y,w,h]of labels){for(let yy=y;yy<y+h;yy++)for(let xx=x;xx<x+w;xx++){let i=(yy*W+xx)*4,bg=bgAt(xx,yy);for(let k=0;k<3;k++)cleanData.data[i+k]=bg[k];}protect.getContext('2d').clearRect(x,y,w,h);}bc.putImageData(cleanData,0,0);}''',LABELS)
        for name,(top,bottom)in SCENES.items():
            exported=page.evaluate('''({top,bottom,name})=>{
                const data={},meta={top,bottom,sprites:{},waves:[],people:[],boats:[],birds:[],cabins:[],cable:cableSamples};
                function add(id,c,x,y,w,h,mask=false){data[id]=c.toDataURL();meta.sprites[id]={bounds:[x,y,w,h],mask};}
                function tile(id,c){let out=make(W,bottom-top);out.getContext('2d').drawImage(c,0,-top);add(id,out,0,top,W,bottom-top);}
                tile('base',base);tile('protect',protect);tile('sea-mask',seaMask);meta.sprites['sea-mask'].mask=true;
                const ids={rk_beach:['rk-bus','rk-boat-0','rk-boat-1','rk-boat-2','rk-person-red','rk-person-blue','rk-vendor'],kailasagiri:['kailasa-cabin-up','kailasa-cabin-down'],dolphins_nose:['dolphin-boat','dolphin-person-red','dolphin-person-blue','lighthouse-beacon'],tenneti_park:['tenneti-grounded-ship','tenneti-person-red','tenneti-person-blue'],rushikonda:['rushi-blueboat-1','rushi-blueboat-2','rushi-person-red','rushi-person-blue','palm-crowns','windsurfer','paraglider','rushi-umbrella-canopy','rushi-foam']}[name];
                for(let id of ids){let l=layers.find(l=>l.id===id);add(id,l.c,l.x,l.y,l.w,l.h);}
                for(let l of waveParts){if(l.y<top||l.y>=bottom)continue;if(name==='rk_beach'&&!['rk-water-2','rk-water-3'].includes(l.id))continue;add(l.id,l.c,l.x,l.y,l.w,l.h);meta.waves.push({id:l.id,depth:l.depth,phase:l.phase,parent:l.parent});}
                for(let l of people)if(ids.includes(l.id))meta.people.push({id:l.id,walk:l.walk,scale:l.scale,phase:l.phase});
                for(let l of boats)if(ids.includes(l.id))meta.boats.push(l.id);
                for(let b of birds)if(b.y>=top&&b.y<bottom){let l=b.l;if(!data[l.id])add(l.id,l.c,l.x,l.y,l.w,l.h);meta.birds.push({id:l.id,x:b.x,y:b.y,speed:b.speed,phase:b.phase});}
                for(let l of cabins)if(ids.includes(l.id))meta.cabins.push({id:l.id,anchor:l.anchor,p0:l.p0,origin:l.origin,direction:l.direction});
                if(name==='rk_beach')add('props',props,0,220,520,140);
                renderAt(0);let poster=make(W,bottom-top);poster.getContext('2d').drawImage(main,0,-top);
                return {data,meta,poster:poster.toDataURL()};
            }''',{'top':top,'bottom':bottom,'name':name})
            meta=exported['meta'];originals={k:Image.open(io.BytesIO(base64.b64decode(v.split(',')[1]))).convert('RGBA')for k,v in exported['data'].items()}
            for theme in ['dark','light']:
                tiles={}
                for key,im in originals.items():
                    tile=im if meta['sprites'][key]['mask']else ink(im,theme=='light')
                    # Keep useful subpixel edge alpha. Lossless WebP stores the
                    # single layer only once instead of hundreds of rendered frames.
                    tile=tile.resize((max(1,round(im.width*576/995)),max(1,round(im.height*576/995))),Image.Resampling.LANCZOS)
                    tiles[key]=tile
                atlas,positions=pack(tiles)
                atlas.save(output/f'vizag_{name}_layers_{theme}.webp',lossless=True,method=6)
                if theme=='dark':
                    for key,box in positions.items():meta['sprites'][key]['source']=box
                    meta['atlas_size']=list(atlas.size)
                else:assert positions=={key:value['source']for key,value in meta['sprites'].items()}
                poster=Image.open(io.BytesIO(base64.b64decode(exported['poster'].split(',')[1]))).convert('RGBA')
                poster=ink(poster,theme=='light').resize((576,round((bottom-top)*576/995)),Image.Resampling.LANCZOS)
                suffix='_light'if theme=='light'else''
                poster.save(output/f'vizag_{name}{suffix}_poster.webp',lossless=True,method=6)
            manifest[name]=meta
            total=sum(f.stat().st_size for f in output.glob(f'vizag_{name}*.webp'))
            report[name]={'bytes':total,'atlas_size':meta['atlas_size'],'layers':len(meta['sprites']),'rendering':'native layered drawable; no stored animation frames'}
            print(name,report[name],flush=True)
        browser.close()
    (output/'vizag_replacement_layers.json').write_text(json.dumps(manifest,separators=(',',':'))+'\n')
    report['total_bytes']=sum(v['bytes']for v in report.values())+(output/'vizag_replacement_layers.json').stat().st_size
    (output/'report.json').write_text(json.dumps(report,indent=2)+'\n');print('Total bytes:',report['total_bytes'],flush=True)


def install(output):
    # Validate the complete staged set before touching packaged resources.
    for name in SCENES:
        for theme in ['dark','light']:
            assert (output/f'vizag_{name}_layers_{theme}.webp').is_file()
            suffix='_light'if theme=='light'else''
            assert (output/f'vizag_{name}{suffix}_poster.webp').is_file()
    assert (output/'vizag_replacement_layers.json').is_file()
    for name in SCENES:
        for theme in ['dark','light']:
            filename=f'vizag_{name}_layers_{theme}.webp';shutil.copy2(output/filename,ROOT/'app/src/main/res/drawable-nodpi'/filename)
            suffix='_light'if theme=='light'else'';filename=f'vizag_{name}{suffix}_poster.webp';shutil.copy2(output/filename,ROOT/'app/src/main/res/drawable-nodpi'/filename)
        (ROOT/'app/src/main/res/raw'/f'vizag_{name}.webp').unlink(missing_ok=True)
    shutil.copy2(output/'vizag_replacement_layers.json',ROOT/'app/src/main/res/raw/vizag_replacement_layers.json')
    shutil.copy2(output/'report.json',ROOT/'docs/implementation/VIZAG_REPLACEMENT_ASSETS.json')
    path=ROOT/'docs/implementation/VIZAG_FOOTER_ASSETS.json';data=json.loads(path.read_text());report=json.loads((output/'report.json').read_text())
    for name in SCENES:data[name]=report[name]
    path.write_text(json.dumps(data,indent=2)+'\n')


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('output',type=Path);parser.add_argument('--install',action='store_true');args=parser.parse_args()
    if args.install:install(args.output)
    else:build(args.output)
