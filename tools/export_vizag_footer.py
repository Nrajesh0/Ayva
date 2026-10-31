"""Export the approved footer frames as transparent, native Android GIFs.

Usage: python tools/export_vizag_footer.py /path/to/recorded-frames
Input is the approved 1920x900 nine-scene gallery, 192 PNGs at 24 fps.
Captions are excluded; Android renders accessible, theme-aware text separately.
"""
import json,sys
from pathlib import Path
import numpy as np
from PIL import Image

root=Path(__file__).resolve().parents[1]
frames=Path(sys.argv[1])
names=['rk_beach','kailasagiri','dolphins_nose','fishing_harbour','ins_kursura','tenneti_park','rushikonda','vizag_port','simhachalam']
report={}
for i,name in enumerate(names):
 x=i%3*640;y=i//3*300
 shots=[];bounds=[]
 for n in range(0,192,2):
  with Image.open(frames/f'{n:03}.png') as sheet:
   tile=sheet.convert('RGB').crop((x,y,x+640,y+252))
  a=np.array(tile);ys,xs=np.nonzero(a.max(axis=2)>45)
  bounds.append((xs.min(),ys.min(),xs.max()+1,ys.max()+1));shots.append(tile)
 box=(max(0,min(b[0] for b in bounds)-8),max(0,min(b[1] for b in bounds)-8),min(640,max(b[2] for b in bounds)+8),min(252,max(b[3] for b in bounds)+8))
 shots=[s.crop(box) for s in shots]
 # Shared palette keeps colors stable; reserve index 255 for the background.
 swatch=Image.new('RGB',(shots[0].width,shots[0].height*12))
 for j in range(12):swatch.paste(shots[j*8],(0,j*shots[0].height))
 palette=swatch.quantize(colors=192)
 converted=[]
 for frame in shots:
  pix=np.array(frame)
  indexed=frame.quantize(palette=palette,dither=Image.Dither.NONE)
  indices=np.array(indexed);indices[pix.max(axis=2)<24]=255
  result=Image.fromarray(indices.astype('uint8'),'P');result.putpalette(palette.getpalette());result.info['transparency']=255
  converted.append(result)
 # GIF durations are stored in centiseconds: distribute rounding without drift.
 durations=[(round((n+1)*100/12)-round(n*100/12))*10 for n in range(96)]
 out=root/'app/src/main/res/raw'/f'vizag_{name}.gif'
 converted[0].save(out,save_all=True,append_images=converted[1:],duration=durations,loop=0,transparency=255,disposal=2,optimize=False)
 rgba=shots[0].convert('RGBA');a=np.array(rgba);a[:,:,3]=np.where(a[:,:,:3].max(axis=2)<24,0,255)
 Image.fromarray(a).save(root/'app/src/main/res/drawable-nodpi'/f'vizag_{name}_poster.webp',lossless=True)
 with Image.open(out) as check:
  assert check.n_frames==96
  check.seek(0);first=np.array(check.convert('RGBA'));check.seek(18);later=np.array(check.convert('RGBA'))
  changed=int((abs(first.astype(int)-later.astype(int)).max(axis=2)>20).sum());assert changed>0
  assert np.any(first[:,:,3]==0)
 report[name]={'frames':96,'duration_ms':sum(durations),'crop_box':list(map(int,box)),'size':list(shots[0].size),'changed_pixels':changed,'bytes':out.stat().st_size}
 print(name,report[name],flush=True)
(root/'docs/implementation/VIZAG_FOOTER_ASSETS.json').write_text(json.dumps(report,indent=2)+'\n')
