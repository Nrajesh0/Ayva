from pathlib import Path
import json,numpy as np
from PIL import Image
root=Path(__file__).resolve().parents[1]
assets=json.loads((root/'docs/implementation/VIZAG_FOOTER_ASSETS.json').read_text())
regions=json.loads((root/'docs/implementation/VIZAG_MOTION_SAMPLES.json').read_text())
results=[]
for r in regions:
 i=r['scene']-1;name=list(assets)[i];info=assets[name];crop=info['crop_box'];x=i%3*640+crop[0];y=i//3*300+crop[1]
 box=[r['box'][0]-x,r['box'][1]-y,r['box'][2]-x,r['box'][3]-y]
 with Image.open(root/'app/src/main/res/raw'/f'vizag_{name}.gif') as im:
  im.seek(0);a=np.array(im.convert('RGBA').crop(box)).astype(int);im.seek(18);b=np.array(im.convert('RGBA').crop(box)).astype(int)
  changed=int((np.abs(a-b).max(axis=2)>25).sum())
 assert changed>5,(name,r['part'],changed)
 results.append(dict(scene=r['scene'],part=r['part'],changed_pixels=changed))
 print(name,r['part'],changed)
for i in range(1,10):assert len(set(r['part'] for r in results if r['scene']==i))>=2
(root/'docs/implementation/VIZAG_MOTION_REGIONS.json').write_text(json.dumps(results,indent=2)+'\n')
print('Verified at least two independent environmental movements in every exported native animation')
