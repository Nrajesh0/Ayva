/* Original approved pixels are the artwork. This compositor only moves their layers. */
const ATLAS='ATLAS_URI';
const W=995,H=1581;
const main=document.getElementById('art'),ctx=main.getContext('2d',{willReadFrequently:true});
const make=(w,h)=>{let c=document.createElement('canvas');c.width=w;c.height=h;return c;};
const original=make(W,H),base=make(W,H),baseline=make(W,H),audit=make(W,H);
const oc=original.getContext('2d',{willReadFrequently:true}),bc=base.getContext('2d',{willReadFrequently:true});
let pixels,bgGrid,cleanData,backgroundData,referenceMode=false,manual=false,running=!matchMedia('(prefers-reduced-motion: reduce)').matches,elapsed=0,start=performance.now(),last=0;
const layers=[],people=[],boats=[],waves=[],birds=[];let protect,waveParts=[],seaMask,waterSurface,waterCtx;
const clamp=(v,a,b)=>Math.max(a,Math.min(b,v));
const rgb=(x,y)=>{let i=(Math.round(y)*W+Math.round(x))*4;return [pixels[i],pixels[i+1],pixels[i+2]];};
const isBackground=(r,g,b)=>r<38&&g<53&&b<70&&b>=r&&b>=g*.95;
const red=(r,g,b)=>r>45&&r>g*1.3&&r>b*1.25;
const blue=(r,g,b)=>b>40&&g>27&&b>r*1.35&&g>r*1.2;
const white=(r,g,b)=>Math.min(r,g,b)>110&&Math.max(r,g,b)-Math.min(r,g,b)<75;
const green=(r,g,b)=>g>60&&g>r*1.25&&g>b*1.04;
const gold=(r,g,b)=>r>70&&g>48&&r>g*.95&&g>b*1.3;
function buildBackground(){
 const step=16,nx=Math.ceil(W/step)+1,ny=Math.ceil(H/step)+1;const g=[];
 for(let yy=0;yy<ny;yy++)for(let xx=0;xx<nx;xx++){
  let x=clamp(xx*step,0,W-1),y=clamp(yy*step,0,H-1),r=0,gg=0,b=0,n=0;
  for(let dy=-12;dy<=12;dy+=2)for(let dx=-12;dx<=12;dx+=2){let [rr,gr,br]=rgb(clamp(x+dx,0,W-1),clamp(y+dy,0,H-1));if(isBackground(rr,gr,br)){r+=rr;gg+=gr;b+=br;n++;}}
  g.push(n?[r/n,gg/n,b/n]:null);
 }
 for(let i=0;i<g.length;i++)if(!g[i]){let x=i%nx,y=Math.floor(i/nx),sum=[0,0,0],n=0;for(let rad=1;rad<8&&!n;rad++)for(let dy=-rad;dy<=rad;dy++)for(let dx=-rad;dx<=rad;dx++){let xx=x+dx,yy=y+dy;if(xx>=0&&xx<nx&&yy>=0&&yy<ny&&g[yy*nx+xx]){g[yy*nx+xx].forEach((v,j)=>sum[j]+=v);n++;}}g[i]=n?sum.map(v=>v/n):[8,20,30];}
 bgGrid={g,nx,ny,step};
}
function bgAt(x,y){const {g,nx,ny,step}=bgGrid;let gx=clamp(x/step,0,nx-1.001),gy=clamp(y/step,0,ny-1.001),ix=Math.floor(gx),iy=Math.floor(gy),a=g[iy*nx+ix],b=g[iy*nx+ix+1],c=g[(iy+1)*nx+ix],d=g[(iy+1)*nx+ix+1],fx=gx-ix,fy=gy-iy;return a.map((v,j)=>Math.round((v*(1-fx)+b[j]*fx)*(1-fy)+(c[j]*(1-fx)+d[j]*fx)*fy));}
function fg(r,g,b,x,y){let c=bgAt(x,y);return Math.max(Math.abs(r-c[0]),Math.abs(g-c[1]),Math.abs(b-c[2]))>14;}
function localBackground(rect,x,y){
 const [rx,ry,w,h]=rect;
 function sample(cx,cy){let sum=[0,0,0],n=0;for(let dy=-4;dy<=4;dy+=2)for(let dx=-4;dx<=4;dx+=2){let v=rgb(clamp(cx+dx,0,W-1),clamp(cy+dy,0,H-1));if(isBackground(...v)){v.forEach((c,i)=>sum[i]+=c);n++;}}return n?sum.map(v=>v/n):bgAt(cx,cy);}
 const a=sample(rx-5,ry-5),b=sample(rx+w+5,ry-5),c=sample(rx-5,ry+h+5),d=sample(rx+w+5,ry+h+5),fx=clamp((x-rx)/w,0,1),fy=clamp((y-ry)/h,0,1);
 return a.map((v,i)=>Math.round((v*(1-fx)+b[i]*fx)*(1-fy)+(c[i]*(1-fx)+d[i]*fx)*fy));
}
function extract(id,rect,predicate=fg,options={}){
 const [x,y,w,h]=rect,c=make(w,h),cc=c.getContext('2d'),im=cc.createImageData(w,h),mask=new Uint8Array(w*h);const corners=options.clearWhole?[localBackground(rect,x,y),localBackground(rect,x+w,y),localBackground(rect,x,y+h),localBackground(rect,x+w,y+h)]:null;
 for(let yy=0;yy<h;yy++)for(let xx=0;xx<w;xx++){
  let si=((y+yy)*W+x+xx)*4,mi=yy*w+xx;let r=pixels[si],g=pixels[si+1],b=pixels[si+2];
  const filled=options.fillInside&&options.fillInside(xx,yy);
  if(options.clearWhole){
   const fx=xx/w,fy=yy/h,bg=corners[0].map((v,j)=>Math.round((v*(1-fx)+corners[1][j]*fx)*(1-fy)+(corners[2][j]*(1-fx)+corners[3][j]*fx)*fy));
   if(!(options.preserve&&options.preserve(r,g,b,x+xx,y+yy))){cleanData.data[si]=bg[0];cleanData.data[si+1]=bg[1];cleanData.data[si+2]=bg[2];audit.data[si]=255;audit.data[si+3]=255;}
  }
  if(filled||predicate(r,g,b,x+xx,y+yy,xx,yy)){
   mask[mi]=1;let di=mi*4;im.data[di]=r;im.data[di+1]=g;im.data[di+2]=b;im.data[di+3]=255;
   if(options.clear!==false&&!options.clearWhole){let bg=bgAt(x+xx,y+yy);cleanData.data[si]=bg[0];cleanData.data[si+1]=bg[1];cleanData.data[si+2]=bg[2];audit.data[si]=255;audit.data[si+3]=255;}
  }
 }
 cc.putImageData(im,0,0);const l={id,c,x,y,w,h,mask,options};layers.push(l);return l;
}
function draw(l,dx=0,dy=0,scale=1,angle=0,pivot=null){ctx.save();let px=pivot?pivot[0]:l.x+l.w/2,py=pivot?pivot[1]:l.y+l.h;ctx.translate(px+dx,py+dy);ctx.rotate(angle);ctx.scale(scale,scale);ctx.drawImage(l.c,l.x-px,l.y-py);ctx.restore();}
function componentPredicate(rect,color){
 const [x,y,w,h]=rect,mark=new Uint8Array(w*h),seen=new Uint8Array(w*h);let best=[];
 for(let yy=0;yy<h;yy++)for(let xx=0;xx<w;xx++){let i=((y+yy)*W+x+xx)*4;if(color(pixels[i],pixels[i+1],pixels[i+2]))mark[yy*w+xx]=1;}
 for(let seed=0;seed<mark.length;seed++){if(!mark[seed]||seen[seed])continue;let queue=[seed],head=0;seen[seed]=1;while(head<queue.length){let v=queue[head++],vx=v%w,vy=Math.floor(v/w);for(let dy=-1;dy<=1;dy++)for(let dx=-1;dx<=1;dx++){let xx=vx+dx,yy=vy+dy;if(xx<0||yy<0||xx>=w||yy>=h)continue;let j=yy*w+xx;if(mark[j]&&!seen[j]){seen[j]=1;queue.push(j);}}}if(queue.length>best.length)best=queue;}
 const chosen=new Uint8Array(w*h);best.forEach(i=>chosen[i]=1);
 return (r,g,b,xx,yy)=>{let rx=xx-x,ry=yy-y;return rx>=0&&ry>=0&&rx<w&&ry<h&&chosen[ry*w+rx]===1;};
}
function splitWaves(l){
 const a=l.c.getContext('2d').getImageData(0,0,l.w,l.h),seen=new Uint8Array(l.w*l.h),groups=[];
 for(let seed=0;seed<seen.length;seed++){if(seen[seed]||!a.data[seed*4+3])continue;let queue=[seed],head=0;seen[seed]=1;while(head<queue.length){let i=queue[head++],x=i%l.w,y=Math.floor(i/l.w);for(let dy=-1;dy<=1;dy++)for(let dx=-1;dx<=1;dx++){let xx=x+dx,yy=y+dy,j=yy*l.w+xx;if(xx<0||yy<0||xx>=l.w||yy>=l.h||seen[j]||!a.data[j*4+3])continue;seen[j]=1;queue.push(j);}}groups.push(queue);}
 const big=groups.filter(g=>g.length>80),small=groups.filter(g=>g.length<=80);if(!big.length)return [l];
 const centers=big.map(g=>{let x=0,y=0;for(let i of g){x+=i%l.w;y+=Math.floor(i/l.w);}return{x:x/g.length,y:y/g.length};});
 for(const g of small){let i=g[0],x=i%l.w,y=Math.floor(i/l.w),best=0,d=Infinity;centers.forEach((p,j)=>{let v=(p.x-x)**2+(p.y-y)**2;if(v<d){d=v;best=j;}});big[best].push(...g);}
 return big.map((g,j)=>{let x1=l.w,y1=l.h,x2=0,y2=0;for(let i of g){let x=i%l.w,y=Math.floor(i/l.w);x1=Math.min(x1,x);x2=Math.max(x2,x);y1=Math.min(y1,y);y2=Math.max(y2,y);}let w=x2-x1+1,h=y2-y1+1,c=make(w,h),cc=c.getContext('2d'),out=cc.createImageData(w,h);for(let i of g){let x=i%l.w,y=Math.floor(i/l.w),k=((y-y1)*w+x-x1)*4;out.data.set(a.data.subarray(i*4,i*4+4),k);}cc.putImageData(out,0,0);return {id:l.id+'-'+j,parent:l.id,c,x:l.x+x1,y:l.y+y1,w,h,depth:clamp((y2+1)/l.h,.2,1),phase:j*.65};});
}
// Crests travel toward the viewer/shore instead of bending back and forth.
// Two softly overlapping phases let new crests emerge offshore without a reset.
function shorelineWash(t,phase=0){
 const smooth=v=>v*v*(3-2*v);
 const value=time=>{const p=((time/7.8+phase)%1+1)%1;return p<.4?smooth(p/.4):1-smooth((p-.4)/.6);};
 return value(t)-value(0);
}
function crestState(l,t,copy=0){
 const depth=l.depth??.8,period=7.6+(.9-depth)*.8;
 const p=((.5+t/period+copy*.5)%1+1)%1,progress=2*p-1;
 const shore=l.parent==='rk-water'||l.parent==='rushi-water';
 const smooth=v=>{v=clamp(v,0,1);return v*v*(3-2*v);};
 return {dx:(shore?-32:-11)*depth*progress,dy:22*depth*progress,alpha:smooth(p/.18)*smooth((1-p)/.28)};
}
function offshoreOffset(l,x,t){
 const depth=l.depth??.8,phase=(l.x+x)/125+(l.phase||0)*.45;
 const edge=clamp(Math.min(x,l.w-1-x)/30,0,1);
 const amplitude=3.2+2.4*depth;
 return edge*(amplitude*(Math.sin(phase-t*.88)-Math.sin(phase))+.3*(Math.sin(phase*2-t*1.2)-Math.sin(phase*2)));
}
function offshoreWave(l,t){
 // One continuous surface line: passing swells, without copied surf fronts.
 if(t===0){waterCtx.drawImage(l.c,l.x,l.y);return;}
 for(let x=0;x<l.w;x++){
  const dy=offshoreOffset(l,x,t);
  waterCtx.save();waterCtx.beginPath();waterCtx.rect(l.x+x,l.y-14,1,l.h+28);waterCtx.clip();
  waterCtx.drawImage(l.c,l.x,l.y+dy);waterCtx.restore();
 }
}
function rollingWave(l,t){
 if(l.parent==='dolphin-water'||l.parent==='tenneti-water'){offshoreWave(l,t);return;}
 if(l.parent==='rk-water'){
  // Keep only the two main bay curves, and one copy of each.
  if(l.id!=='rk-water-2'&&l.id!=='rk-water-3')return;
  const depth=l.depth??.8,period=8.2,p0=l.id==='rk-water-2'?.35:.65,phase=((p0+t/period)%1+1)%1;
  const smooth=v=>{v=clamp(v,0,1);return v*v*(3-2*v);};
  const distance=2*(phase-p0),alpha=smooth(phase/.17)*smooth((1-phase)/.22);
  waterCtx.save();waterCtx.globalAlpha=alpha;
  waterCtx.drawImage(l.c,l.x-29*depth*distance,l.y+18*depth*distance);waterCtx.restore();return;
 }
 // Rushikonda retains the accepted paired travelling crests, unchanged.
 if(t===0){waterCtx.drawImage(l.c,l.x,l.y);return;}
 for(let copy=0;copy<2;copy++){
  const {dx,dy,alpha}=crestState(l,t,copy);
  waterCtx.save();waterCtx.globalAlpha=alpha;
  waterCtx.drawImage(l.c,l.x+dx,l.y+dy);waterCtx.restore();
 }
}
function buildSeaMask(){
 seaMask=make(W,H);const mc=seaMask.getContext('2d'),im=mc.createImageData(W,H);
 const scenes=[{y1:130,y2:354,shore:182},{y1:823,y2:937},{y1:1134,y2:1221},{y1:1385,y2:1577,shore:1416}];
 for(const area of scenes){
  let lastBoundary=0;
  const shoreInk=area.shore?componentPredicate([0,area.y1,W,area.y2-area.y1],gold):null;
  for(let y=area.y1;y<area.y2;y++){
   let boundary=0;
   if(area.shore&&y>=area.shore){
    for(let x=100;x<W-10;x++){let i=(y*W+x)*4;if(shoreInk(pixels[i],pixels[i+1],pixels[i+2],x,y))boundary=x;}
    if(boundary)lastBoundary=boundary;else boundary=lastBoundary;
   }
   for(let x=0;x<W;x++){
    const edge=Math.min(y-area.y1,area.y2-1-y),shoreFade=area.shore&&y>=area.shore?clamp((x-boundary-1)/5,0,1):1;
    let i=(y*W+x)*4;im.data[i]=im.data[i+1]=im.data[i+2]=255;im.data[i+3]=Math.round(255*shoreFade*clamp(edge/5,0,1));
   }
  }
 }
 mc.putImageData(im,0,0);waterSurface=make(W,H);waterCtx=waterSurface.getContext('2d');
}
function paintSea(t){
 waterCtx.clearRect(0,0,W,H);
 for(const l of waveParts)rollingWave(l,t);
 // A small independent wash along the existing white shore line.
 const wash=shorelineWash(t),dx=-6*wash,dy=4*wash;
 waterCtx.drawImage(foam.c,foam.x+dx,foam.y+dy);
 waterCtx.save();waterCtx.globalCompositeOperation='destination-in';waterCtx.drawImage(seaMask,0,0);waterCtx.restore();
 ctx.drawImage(waterSurface,0,0);
}
function addPerson(id,box,color,walk,scale=1,phase=0){let pick=componentPredicate(box,color);let l=extract(id,box,pick,{clearWhole:true,preserve:(r,g,b,x,y)=>!pick(r,g,b,x,y)&&fg(r,g,b,x,y)});l.walk=walk;l.scale=scale;l.phase=phase;people.push(l);return l;}
function paintPerson(l,t){if(t===0){draw(l,0,0,l.scale);return;}
 const distance=l.walk*2.5;const dx=l.id.startsWith('rushi')?-distance*(1-Math.cos(t*.45))/2:distance*Math.sin(t*.45);let bob=l.walk?Math.abs(Math.sin(t*4.1))*.8:Math.sin(t*.9)*.45;
 const mid=l.w*.5,hip=l.h*.6,feetX=l.x+mid,feetY=l.y+l.h;
 ctx.save();ctx.translate(feetX+dx,feetY+bob);ctx.scale(l.scale,l.scale);ctx.translate(-mid,-l.h);
 // The original upper body and legs remain the same pixels, articulated at the hips.
 ctx.drawImage(l.c,0,0,l.w,hip,0,0,l.w,hip);
 let swing=(l.walk?.36:.08)*Math.sin(t*4.1+l.phase)*Math.sin(Math.min(1,t)*Math.PI/2);
 for(let k=0;k<2;k++){let sx=k?mid:0,sw=k?l.w-mid:mid;ctx.save();ctx.translate(mid,hip);ctx.rotate(k?swing:-swing);ctx.drawImage(l.c,sx,hip,sw,l.h-hip,sx-mid,0,sw,l.h-hip);ctx.restore();}
 ctx.restore();
}
function repairLine(x1,y1,x2,y2,predicate,tolerance=1){
 // Restore an occluded line only where a moving object was removed.
 const dx=x2-x1,dy=y2-y1,steps=Math.ceil(Math.hypot(dx,dy)*2);for(let k=0;k<=steps;k++){let t=k/steps,x=x1+dx*t,y=y1+dy*t;for(let yy=-tolerance;yy<=tolerance;yy++)for(let xx=-tolerance;xx<=tolerance;xx++){let px=Math.round(x)+xx,py=Math.round(y)+yy,i=(py*W+px)*4;if(px<0||py<0||px>=W||py>=H||!audit.data[i+3])continue;const r=pixels[i],g=pixels[i+1],b=pixels[i+2];if(predicate(r,g,b)){cleanData.data[i]=207;cleanData.data[i+1]=218;cleanData.data[i+2]=221;}}}
}
const cableSamples=[[405,469],[440,481.5],[470,492],[500,502.5],[540,515],[600,534.5],[660,552.5],[720,568.5],[760,578.5],[790,586],[830,593],[880,600.5],[920,604],[937,605]];
const route=[];let routeLength=0;
for(let i=0;i<cableSamples.length-1;i++){let a=cableSamples[i],b=cableSamples[i+1];for(let j=0;j<20;j++){let f=j/20,x=a[0]+(b[0]-a[0])*f,y=a[1]+(b[1]-a[1])*f;if(route.length){let p=route.at(-1);routeLength+=Math.hypot(x-p.x,y-p.y);}route.push({x,y,d:routeLength});}}
{const p=cableSamples.at(-1),a=route.at(-1);routeLength+=Math.hypot(p[0]-a.x,p[1]-a.y);route.push({x:p[0],y:p[1],d:routeLength});}
function routePoint(p){let target=clamp(p,0,1)*routeLength,i=route.findIndex(v=>v.d>=target);i=Math.max(1,i);let a=route[i-1],b=route[i],f=(target-a.d)/(b.d-a.d);return {x:a.x+(b.x-a.x)*f,y:a.y+(b.y-a.y)*f};}
function routeRatio(x){let best=route.reduce((a,b)=>Math.abs(b.x-x)<Math.abs(a.x-x)?b:a);return best.d/routeLength;}
let bus,props,cabins,palm,surfer,paraglider,beacon,foam,umbrella,groundedShip;
function colourInteriors(l,shapes){
 // Colour the empty interior of the existing silhouettes, retaining their ink,
 // windows, rails and other original details at exactly the same coordinates.
 const paint=make(l.w,l.h),pc=paint.getContext('2d');pc.translate(-l.x,-l.y);
 for(const [colour,points]of shapes){pc.fillStyle=colour;pc.beginPath();points.forEach(([x,y],i)=>i?pc.lineTo(x,y):pc.moveTo(x,y));pc.closePath();pc.fill();}
 const cc=l.c.getContext('2d'),im=cc.getImageData(0,0,l.w,l.h),ink=paint.getContext('2d').getImageData(0,0,l.w,l.h);
 for(let i=0;i<im.data.length;i+=4){let a=ink.data[i+3]/255;if(!a||im.data[i+3]&&Math.max(im.data[i],im.data[i+1],im.data[i+2])>85)continue;
 for(let k=0;k<3;k++)im.data[i+k]=ink.data[i+k]*a+im.data[i+k]*(1-a);im.data[i+3]=255;
 const v=i/4,world=((l.y+Math.floor(v/l.w))*W+l.x+v%l.w)*4;
 // Do not repaint old navy background fragments over the new solid colour.
 audit.data[world]=255;audit.data[world+3]=255;}
 cc.putImageData(im,0,0);return l;
}
function initialize(){
 pixels=oc.getImageData(0,0,W,H).data;buildBackground();cleanData=oc.getImageData(0,0,W,H);audit.width=W;audit.height=H;const ac=audit.getContext('2d');audit.data=ac.createImageData(W,H).data;
 waves.push(extract('rk-water',[350,135,630,175],blue));
 waves.push(extract('dolphin-water',[565,848,415,78],blue));
 waves.push(extract('tenneti-water',[10,1140,665,75],blue));
 waves.push(extract('rushi-water',[230,1390,745,165],(r,g,b,x,y)=>blue(r,g,b)&&!(x>=241&&x<=425&&y>=1505)));
 // Keep the original antialiasing as transparency instead of moving opaque
 // navy edge pixels with the crests, which made fine lines look speckled.
 for(const l of waves){const cc=l.c.getContext('2d'),im=cc.getImageData(0,0,l.w,l.h);
 for(let y=0;y<l.h;y++)for(let x=0;x<l.w;x++){let j=(y*l.w+x)*4;if(!im.data[j+3])continue;let i=((l.y+y)*W+l.x+x)*4,bg=[cleanData.data[i],cleanData.data[i+1],cleanData.data[i+2]],alpha=clamp((im.data[j+2]-bg[2])/(255-bg[2]),0,1);
 if(alpha){for(let k=0;k<3;k++)im.data[j+k]=clamp(bg[k]+(im.data[j+k]-bg[k])/alpha,0,255);im.data[j+3]=Math.round(alpha*255);}else im.data[j+3]=0;
 }cc.putImageData(im,0,0);}
 // Remove old raster wave-edge fragments from these three water fields.
 // Otherwise the moving crests reveal dark stationary outlines beneath them.
 for(const l of waves.filter(v=>v.id!=='rushi-water'))for(let y=l.y;y<l.y+l.h;y++)for(let x=l.x;x<l.x+l.w;x++){
  const i=(y*W+x)*4,r=pixels[i],g=pixels[i+1],b=pixels[i+2];
  const rock=Math.min(r,g,b)>60&&Math.max(r,g,b)-Math.min(r,g,b)<40;
  if(red(r,g,b)||gold(r,g,b)||white(r,g,b)||green(r,g,b)||rock)continue;
  const bg=bgAt(x,y);for(let k=0;k<3;k++)cleanData.data[i+k]=bg[k];audit.data[i]=255;audit.data[i+3]=255;
 }
 waveParts=waves.flatMap(splitWaves);
 foam=extract('rushi-foam',[240,1425,305,82],(r,g,b)=>white(r,g,b)&&Math.min(r,g,b)>160);
 buildSeaMask();
 bus=extract('rk-bus',[35,299,106,51],(r,g,b)=>blue(r,g,b),{clearWhole:true,preserve:white,fillInside:(x,y)=>(x>=2&&x<104&&y>=3&&y<=43)||Math.hypot(x-21,y-43)<7||Math.hypot(x-82,y-43)<7});
 colourInteriors(bus,[
 ['#248e9d',[[39,305],[132,305],[136,309],[136,338],[40,336]]],
 ['#12394e',[[44,310],[115,310],[115,326],[44,326]]],
 ['#163f52',[[122,311],[133,311],[133,337],[122,337]]]
 ]);
 for(let i=0;i<3;i++){const x=[716,807,912][i],y=[108,106,106][i];boats.push(extract('rk-boat-'+i,[x,y,32,16],fg,{clearWhole:true}));}
 boats.push(extract('dolphin-boat',[830,797,110,49],(r,g,b,x,y)=>fg(r,g,b,x,y)&&!blue(r,g,b),{clearWhole:true,preserve:blue}));
 colourInteriors(boats[3],[
 ['#ac4940',[[838,833],[932,832],[928,844],[840,844]]],
 ['#487f8a',[[849,817],[872,817],[872,831],[849,831]]]
 ]);
 groundedShip=extract('tenneti-grounded-ship',[147,1020,375,122],fg,{clear:false});
 colourInteriors(groundedShip,[
 ['#a13e36',[[154,1103],[215,1104],[226,1108],[413,1104],[438,1093],[509,1082],[494,1125],[160,1125]]],
 ['#3c697b',[[220,1063],[260,1063],[260,1104],[220,1104]]],
 ['#527e87',[[192,1064],[215,1064],[215,1100],[176,1100],[176,1086],[192,1086]]],
 ['#bf5143',[[209,1043],[224,1043],[224,1061],[204,1061]]]
 ]);
 addPerson('rk-person-red',[282,302,29,49],red,12,1,0);
 addPerson('rk-person-blue',[304,309,22,43],blue,12,1,.45);
 addPerson('rk-vendor',[189,303,18,23],blue,0,1,0);
 addPerson('dolphin-person-red',[201,769,24,40],red,6,1,0);
 addPerson('dolphin-person-blue',[222,771,24,39],blue,6,1,.5);
 addPerson('tenneti-person-red',[845,977,20,43],red,4,.55,0);
 addPerson('tenneti-person-blue',[860,979,20,41],blue,4,.55,.55);
 addPerson('rushi-person-red',[220,1486,22,40],red,6,1,0);
 addPerson('rushi-person-blue',[240,1486,23,39],blue,6,1,.5);
 cabins=[extract('kailasa-cabin-up',[493,499,48,58],(r,g,b,x,y)=>red(r,g,b),{clearWhole:true,preserve:white,fillInside:(x,y)=>x>=8&&x<=40&&y>=28&&y<=51}),extract('kailasa-cabin-down',[778,580,48,59],(r,g,b,x,y)=>red(r,g,b),{clearWhole:true,preserve:white,fillInside:(x,y)=>x>=7&&x<=40&&y>=29&&y<=51})];
 cabins.forEach((l,i)=>{l.anchor=[i?802:517,i?588:507];l.p0=routeRatio(l.anchor[0]);l.origin=routePoint(l.p0);l.direction=i?1:-1;});
 palm=extract('palm-crowns',[52,1307,168,137],green);
 surfer=extract('windsurfer',[732,1404,73,82],(r,g,b)=>red(r,g,b)||gold(r,g,b));
 paraglider=extract('paraglider',[838,1278,96,78],fg,{clearWhole:true});
 beacon=extract('lighthouse-beacon',[350,675,645,115],()=>true,{clearWhole:true});
 // Unmix the original soft beam from its navy background so its full feathered
 // gradient can rotate without a rectangular crop or stationary dark fragments.
 {const cc=beacon.c.getContext('2d'),im=cc.getImageData(0,0,beacon.w,beacon.h);
 for(let y=0;y<beacon.h;y++)for(let x=0;x<beacon.w;x++){const j=(y*beacon.w+x)*4,i=((beacon.y+y)*W+beacon.x+x)*4,bg=[cleanData.data[i],cleanData.data[i+1],cleanData.data[i+2]],diff=im.data[j]-bg[0],alpha=diff>1?clamp(diff/(255-bg[0]),0,1):0;
 if(alpha){for(let k=0;k<3;k++)im.data[j+k]=clamp(bg[k]+(im.data[j+k]-bg[k])/alpha,0,255);im.data[j+3]=Math.round(alpha*255);}else im.data[j+3]=0;
 }cc.putImageData(im,0,0);}

 const b1=extract('bird-original-1',[539,1318,39,20],white,{clearWhole:true}),b2=extract('bird-original-2',[579,1337,33,18],white,{clearWhole:true});
 // Clone the approved bird artwork for the other scenes, never draw replacement birds.
 birds.push({l:b1,x:b1.x,y:b1.y,speed:17,phase:0},{l:b2,x:b2.x,y:b2.y,speed:15,phase:1});
 for(const y of [83,379,672,957]){birds.push({l:b1,x:280,y,speed:17,phase:.3},{l:b2,x:680,y:y+7,speed:15,phase:1.2});}
 umbrella=extract('rushi-umbrella-canopy',[168,1463,58,33],red);
 for(const [id,box] of [['rushi-blueboat-1',[244,1507,88,42]],['rushi-blueboat-2',[331,1504,93,43]]]){let pick=componentPredicate(box,blue);boats.push(extract(id,box,pick));}
 colourInteriors(boats[4],[
 ['#288ba4',[[251,1527],[279,1528],[322,1518],[313,1536],[260,1540]]],
 ['#56b2bf',[[252,1527],[301,1520],[310,1516],[321,1520],[280,1532]]]
 ]);
 colourInteriors(boats[5],[
 ['#2a759e',[[339,1514],[373,1525],[414,1525],[404,1538],[348,1532]]],
 ['#59aebd',[[340,1514],[357,1515],[373,1520],[414,1525],[381,1530],[350,1520]]]
 ]);
 // Only the pixels previously covered by these objects are repaired.
 repairLine(0,315,515,337,()=>true,1);repairLine(0,335,515,353,()=>true,1);
 repairLine(826,999,905,999,()=>true,1);
 for(const l of cabins){
  function topY(x){let j=cableSamples.findIndex(p=>p[0]>=x);j=clamp(j,1,cableSamples.length-1);let a=cableSamples[j-1],b=cableSamples[j];return a[1]+(b[1]-a[1])*(x-a[0])/(b[0]-a[0]);}
  const sx=l.x+l.w+6,sy=topY(sx);
  function peak(y){let best={y:Math.round(y),v:0};for(let yy=Math.round(y)-3;yy<=Math.round(y)+3;yy++){let v=rgb(sx,yy);if(white(...v)&&v[0]+v[1]+v[2]>best.v)best={y:yy,v:v[0]+v[1]+v[2]};}return best.y;}
  const sourceYs=[peak(sy),peak(sy+11.5)];
  for(let x=l.x;x<l.x+l.w;x++)for(let track=0;track<2;track++){
   let targetY=Math.round(topY(x)+(track?11.5:0));
   for(let dy=-3;dy<=3;dy++){let y=targetY+dy;if(y<l.y||y>=l.y+l.h)continue;let i=(y*W+x)*4;if(!audit.data[i+3])continue;let v=rgb(sx,sourceYs[track]+dy);cleanData.data[i]=v[0];cleanData.data[i+1]=v[1];cleanData.data[i+2]=v[2];}
  }
 }
 // Reuse a neighboring railing segment behind the bus's original position.
 for(let x=35;x<141;x++)for(let sy=320;sy<344;sy++){
  const sx=142+(x-35)%24,v=rgb(sx,sy);if(!white(...v))continue;let y=Math.round(sy+(x-sx)*.035),i=(y*W+x)*4;
  if(y<299||y>=350||!audit.data[i+3])continue;cleanData.data[i]=v[0];cleanData.data[i+1]=v[1];cleanData.data[i+2]=v[2];
 }
 // The thin far end of RK's sandy arc must not continue under open water.
 // Retain the foreground beach; fade only the distant, almost-horizontal tail.
 const rkShoreInk=componentPredicate([0,130,W,224],gold);
 for(let y=135;y<191;y++)for(let x=575;x<W;x++){
  const i=(y*W+x)*4;if(!rkShoreInk(pixels[i],pixels[i+1],pixels[i+2],x,y))continue;
  let fade=clamp((x-575)/190,0,1);fade=fade*fade*(3-2*fade);const bg=bgAt(x,y);
  for(let k=0;k<3;k++)cleanData.data[i+k]=pixels[i+k]*(1-fade)+bg[k]*fade;
  audit.data[i]=255;audit.data[i+3]=255;
 }
 bc.putImageData(cleanData,0,0);
 protect=make(W,H);let protectCtx=protect.getContext('2d'),protectData=protectCtx.createImageData(W,H);
 const fixedAreas=[[0,135,995,221],[0,356,995,298],[0,655,995,283],[0,939,995,285],[0,1250,995,330]];
 for(const [x,y,w,h] of fixedAreas)for(let yy=y;yy<y+h;yy++)for(let xx=x;xx<x+w;xx++){let i=(yy*W+xx)*4;if(!audit.data[i+3]&&fg(pixels[i],pixels[i+1],pixels[i+2],xx,yy)){protectData.data[i]=pixels[i];protectData.data[i+1]=pixels[i+1];protectData.data[i+2]=pixels[i+2];protectData.data[i+3]=255;}}
 protectCtx.putImageData(protectData,0,0);
 // Original foreground props cover the passing bus in their original draw order.
 props=make(520,140);let pc=props.getContext('2d'),pd=pc.createImageData(520,140);
 for(let y=220;y<360;y++)for(let x=0;x<520;x++){let i=(y*W+x)*4,j=((y-220)*520+x)*4;if(!audit.data[i+3]&&fg(pixels[i],pixels[i+1],pixels[i+2],x,y)){pd.data[j]=pixels[i];pd.data[j+1]=pixels[i+1];pd.data[j+2]=pixels[i+2];pd.data[j+3]=255;}}
 pc.putImageData(pd,0,0);
 window.layerInfo=layers.map(l=>({id:l.id,x:l.x,y:l.y,w:l.w,h:l.h}));
 drawFrame(0);baseline.getContext('2d').drawImage(main,0,0);
 document.getElementById('pause').textContent=running?'Pause':'Play';window.ready=true;document.getElementById('state').textContent=(running?'Playing: travelling sea crests, walking visitors and flapping birds':'Reduced motion is enabled. Press Play to animate.');
 requestAnimationFrame(tick);
}
function paintBird(b,t){
 const x=((b.x+80+b.speed*t)%(W+160))-80,dy=1.8*(Math.sin(t*.55+b.phase)-Math.sin(b.phase)),l=b.l;
 ctx.save();ctx.translate(x+l.w/2,b.y+dy+l.h*.65);
 const flap=.82*Math.sin(t*6.6+b.phase)*Math.sin(Math.min(t,1)*Math.PI/2),mid=Math.floor(l.w/2);
 ctx.save();ctx.rotate(flap);ctx.drawImage(l.c,0,0,mid,l.h,-l.w/2,-l.h*.65,mid,l.h);ctx.restore();
 ctx.save();ctx.rotate(-flap);ctx.drawImage(l.c,mid,0,l.w-mid,l.h,mid-l.w/2,-l.h*.65,l.w-mid,l.h);ctx.restore();ctx.restore();
}
function drawFrame(t){
 ctx.clearRect(0,0,W,H);
 if(referenceMode){ctx.drawImage(original,0,0);return;}
 ctx.drawImage(base,0,0);
 paintSea(t);
 const busX=((37+130+13.5*t)%680)-130;ctx.save();ctx.beginPath();ctx.rect(0,288,515,68);ctx.clip();draw(bus,busX-37,0);ctx.restore();
 ctx.drawImage(props,0,220);
 for(let i=0;i<3;i++){let l=boats[i];draw(l,14*(Math.sin(t*.20+i)-Math.sin(i)),1.6*(Math.sin(t*1.1+i)-Math.sin(i)));}
 draw(boats[3],-95*(1-Math.cos(t*.19))/2,3*Math.sin(t*1.15));
 // These boats rest on sand, so their position and rotation remain fixed.
 for(let i=4;i<boats.length;i++)draw(boats[i]);
 draw(groundedShip);
 for(const l of cabins){let angle=Math.acos(1-2*l.p0)*l.direction+t*(2*Math.PI/72),p=(1-Math.cos(angle))/2,pos=routePoint(p);draw(l,pos.x-l.origin.x,pos.y-l.origin.y,1,.007*Math.sin(t*.75),l.anchor);}
 for(const l of people)paintPerson(l,t);
 draw(palm,0,0,1,.025*Math.sin(t*.65),[118,1355]);
 draw(umbrella,0,0,1,.009*Math.sin(t*.7),[196,1493]);
 draw(surfer,-180*(1-Math.cos(t*.23))/2,3*Math.sin(t*1.5),1,.06*Math.sin(t*.8),[773,1475]);
 draw(paraglider,-280*(1-Math.cos(t*.19))/2,6*Math.sin(t*.45),1,.025*Math.sin(t*.55),[884,1335]);
 ctx.save();ctx.globalAlpha=.7+.3*(.5+.5*Math.cos(t*.8));draw(beacon,0,0,1,.10*Math.sin(t*.5),[351,714]);ctx.restore();
 for(const b of birds)paintBird(b,t);ctx.drawImage(protect,0,0);
}
function tick(now){if(running&&!manual&&!document.hidden&&now-last>1000/30){elapsed=(now-start)/1000;drawFrame(elapsed);last=now;}requestAnimationFrame(tick);}
window.renderAt=t=>{manual=true;elapsed=t;referenceMode=false;document.getElementById('reference').textContent='View approved still';drawFrame(t);document.getElementById('pause').textContent='Play';};
window.setTheme=t=>{document.documentElement.dataset.theme=t;document.getElementById('theme').textContent=t==='light'?'Dark mode':'Light mode';};
document.getElementById('pause').onclick=()=>{referenceMode=false;document.getElementById('reference').textContent='View approved still';running=manual?true:!running;manual=false;start=performance.now()-elapsed*1000;document.getElementById('pause').textContent=running?'Pause':'Play';document.getElementById('state').textContent=running?'Playing: travelling sea crests, walking visitors and flapping birds':'Paused';};
document.getElementById('restart').onclick=()=>{elapsed=0;start=performance.now();referenceMode=false;document.getElementById('reference').textContent='View approved still';drawFrame(0);};
document.getElementById('reference').onclick=()=>{referenceMode=!referenceMode;document.getElementById('reference').textContent=referenceMode?'Return to animation':'View approved still';drawFrame(elapsed);};
document.getElementById('compact').onchange=e=>document.body.classList.toggle('compact',e.target.checked);
let hiddenAt=null;document.addEventListener('visibilitychange',()=>{if(document.hidden)hiddenAt=performance.now();else if(hiddenAt!==null){start+=performance.now()-hiddenAt;hiddenAt=null;}});
window.verifyArtwork=()=>{
 const current=ctx.getImageData(0,0,W,H).data,orig=oc.getImageData(0,0,W,H).data;
 // Restrict comparison to static-landmark rectangles, excluding nearby moving pixels.
 const areas={statues:[165,420,185,126],lighthouse:[299,686,46,91],cliff:[5,817,135,93],stairs:[788,1052,65,89],groundedShip:[150,1034,365,105]};
 const result={};for(const [name,[x,y,w,h]] of Object.entries(areas)){let different=0,total=0;for(let yy=y;yy<y+h;yy++)for(let xx=x;xx<x+w;xx++){let i=(yy*W+xx)*4;if(audit.data[i+3]||!fg(orig[i],orig[i+1],orig[i+2],xx,yy))continue;total++;if(current[i]!==orig[i]||current[i+1]!==orig[i+1]||current[i+2]!==orig[i+2])different++;}result[name]={compared:total,different};}
 return result;
};
const image=new Image();image.onload=()=>{oc.drawImage(image,0,0);initialize();};image.onerror=()=>document.getElementById('state').textContent='Artwork failed to load';image.src=ATLAS;
