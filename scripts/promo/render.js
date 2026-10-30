// Renders promo.html frame by frame: node render.js <out-dir> [scale] (scale 3 = 3840x2160)
const {chromium}=require('playwright');const path=require('path');
(async()=>{const out=process.argv[2],scale=+process.argv[3]||1,fps=30;
const b=await chromium.launch({args:['--allow-file-access-from-files']});
const p=await b.newPage({viewport:{width:1280,height:720},deviceScaleFactor:scale});
await p.goto('file://'+path.resolve(__dirname,'promo.html'));await p.evaluate(()=>document.fonts.ready);
const dur=await p.evaluate(()=>DURATION);
for(let i=0;i<Math.round(fps*dur);i++){await p.evaluate(t=>render(t),i/fps);
await p.screenshot({path:`${out}/f${String(i).padStart(4,'0')}.png`})}await b.close()})();
