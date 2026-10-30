const {chromium}=require('playwright');const path=require('path');
(async()=>{const fps=30,dur=+process.argv[3]||25,out=process.argv[2];
const b=await chromium.launch({args:['--allow-file-access-from-files']});const p=await b.newPage({viewport:{width:1280,height:720}});
await p.goto('file://'+path.resolve(__dirname,'promo.html'));await p.waitForTimeout(800);
for(let i=0;i<fps*dur;i++){await p.evaluate(t=>render(t),i/fps);
await p.screenshot({path:`${out}/f${String(i).padStart(4,'0')}.png`})}await b.close()})();
