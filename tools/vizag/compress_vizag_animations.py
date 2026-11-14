"""Losslessly replace packaged GIFs with animated WebP; validate every frame.
Usage: python tools/vizag/compress_vizag_animations.py [source-gif-directory]
"""
from pathlib import Path
from PIL import Image
import numpy as np,json,sys,hashlib
root=Path(__file__).resolve().parents[2]
src=Path(sys.argv[1]) if len(sys.argv)>1 else root/'app/src/main/res/raw'
raw=root/'app/src/main/res/raw';report={};digests={}
for source in sorted(src.glob('vizag_*.gif')):
 frames=[];durations=[];hashes=[]
 with Image.open(source) as im:
  for i in range(im.n_frames):
   im.seek(i);frame=im.convert('RGBA');frames.append(frame);durations.append(im.info['duration'])
   a=np.array(frame);a[a[:,:,3]==0]=0;argb=a[:,:,[3,0,1,2]];hashes.append(hashlib.sha256(argb.tobytes()).hexdigest())
 dest=raw/(source.stem+'.webp')
 frames[0].save(dest,save_all=True,append_images=frames[1:],duration=durations,loop=0,lossless=True,method=6,minimize_size=True,allow_mixed=False)
 with Image.open(dest) as result:
  assert result.n_frames==len(frames)
  actual=[]
  for i,frame in enumerate(frames):
   result.seek(i);got=np.array(result.convert('RGBA'));want=np.array(frame)
   assert np.array_equal(got[:,:,3],want[:,:,3]),(source.name,i,'alpha')
   visible=want[:,:,3]>0
   assert np.array_equal(got[:,:,:3][visible],want[:,:,:3][visible]),(source.name,i,'color')
   actual.append(result.info['duration'])
  assert actual==durations
  assert result.info.get('loop')==0
 report[source.stem]={'gif_bytes':source.stat().st_size,'webp_bytes':dest.stat().st_size,'frames':len(frames),'duration_ms':sum(durations),'size':list(frames[0].size),'all_visible_pixels_and_alpha_equal':True,'all_frame_durations_equal':True}
 digests[source.stem]=hashes
 print(source.stem,source.stat().st_size,'->',dest.stat().st_size,flush=True)
 if source.parent.resolve()==raw.resolve():source.unlink()
(root/'docs/implementation/VIZAG_LOSSLESS_COMPRESSION.json').write_text(json.dumps(report,indent=2)+'\n')
tests=root/'app/src/test/resources';tests.mkdir(parents=True,exist_ok=True)
(tests/'vizag-frame-digests.json').write_text(json.dumps(digests)+'\n')
print('Total bytes:',sum(v['gif_bytes'] for v in report.values()),'->',sum(v['webp_bytes'] for v in report.values()),flush=True)
