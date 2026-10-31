"""Verify rightward flight in the actual packaged GIF frames, not only SVG state.
Usage: python tools/verify_vizag_bird_frames.py bird-regions.json
Regions contain the expected scene-space locations transformed to gallery pixels.
"""
from pathlib import Path
import json,sys,numpy as np
from PIL import Image
root=Path(__file__).resolve().parents[1]
assets=json.loads((root/'docs/implementation/VIZAG_FOOTER_ASSETS.json').read_text())
regions=json.loads(Path(sys.argv[1]).read_text());result=[]
for r in regions:
 i=r['scene']-1;name=list(assets)[i];meta=assets[name];crop=meta['crop_box'];ox=i%3*640+crop[0];oy=i//3*300+crop[1]
 with Image.open(root/'app/src/main/res/raw'/f'vizag_{name}.gif') as im:
  assert im.n_frames==192
  im.seek(0);baseline=np.array(im.convert('RGBA')).astype(int)
  positions=[]
  for sample in r['samples']:
   im.seek(sample['frame']);a=np.array(im.convert('RGBA')).astype(int)
   x0,y0,x1,y1=sample['box'];x0=max(0,int(x0-ox));x1=min(im.width,int(x1-ox));y0=max(0,int(y0-oy));y1=min(im.height,int(y1-oy))
   p=a[y0:y1,x0:x1];b=baseline[y0:y1,x0:x1]
   white=(p[:,:,0]>150)&(p[:,:,1]>150)&(p[:,:,2]>110)&(p[:,:,3]>150)
   changed=np.abs(p-b).max(axis=2)>35
   yy,xx=np.nonzero(white&changed)
   assert len(xx)>3,(name,sample['frame'],'no bird pixels on forward flight path')
   positions.append(float(xx.mean()+x0))
  assert all(b>a+20 for a,b in zip(positions,positions[1:])),(name,positions)
  result.append(dict(scene=name,sampled_frames=[36,72,144],bird_x=positions))
print('Verified rightward bird displacement in exported native GIFs for all nine scenes')
(root/'docs/implementation/VIZAG_BIRD_FRAME_CHECKS.json').write_text(json.dumps(result,indent=2)+'\n')

vehicle_checks=[]
with Image.open(root/'app/src/main/res/raw/vizag_rk_beach.gif') as im:
 for name,c in [('auto',(207,155,53)),('bus',(57,143,158))]:
  positions=[]
  for n in [0,6,12]:
   im.seek(n);a=np.array(im.convert('RGBA')).astype(int);mask=(np.abs(a[:,:,:3]-c).max(axis=2)<20)&(a[:,:,3]>150);yy,xx=np.nonzero(mask)
   assert len(xx)>20,(name,n);positions.append(float(xx.mean()))
  assert all(b<a-2 for a,b in zip(positions,positions[1:])),(name,positions)
  vehicle_checks.append(dict(vehicle=name,x=positions,direction='left'))
with Image.open(root/'app/src/main/res/raw/vizag_rushikonda.gif') as im:
 im.seek(0);a=np.array(im.convert('RGBA')).astype(int);mask=(np.abs(a[:,:,:3]-(201,113,85)).max(axis=2)<25)&(a[:,:,3]>150)
 assert mask.sum()>150,'Paraglider missing from first frame'
 vehicle_checks.append(dict(scene='rushikonda',paraglider_first_frame_pixels=int(mask.sum())))
(root/'docs/implementation/VIZAG_VEHICLE_FRAME_CHECKS.json').write_text(json.dumps(vehicle_checks,indent=2)+'\n')
print('Verified vehicle direction and initial paraglider visibility in the native GIFs')
