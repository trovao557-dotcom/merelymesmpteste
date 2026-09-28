import {existsSync, mkdirSync, rmSync, readdirSync, readFileSync, statSync} from 'node:fs';
import {join, basename} from 'node:path';
import {execFileSync} from 'node:child_process';
const base='.tmp-live'; const out=join(base,'candidate-extracted');
if(existsSync(out)) rmSync(out,{recursive:true,force:true}); mkdirSync(out,{recursive:true});
const jar=join(process.cwd(),'jdk17','jdk-17.0.20.1+1','bin','jar.exe');
const re=/InventoryClickEvent|InventoryDragEvent|COLLECT_TO_CURSOR|DOUBLE_CLICK|PlayerSwapHandItemsEvent|InventoryMoveItemEvent|EntityPickupItemEvent|PlayerDropItemEvent|setItemStack/i;
for(const n of readdirSync(join(base,'candidate-jars'))){if(!n.endsWith('.jar'))continue; const src=join(process.cwd(),base,'candidate-jars',n), d=join(process.cwd(),out,n.replace(/\.jar$/,'')); mkdirSync(d,{recursive:true}); execFileSync(jar,['xf',src],{cwd:d});}
function walk(d){for(const n of readdirSync(d)){const p=join(d,n),s=statSync(p); if(s.isDirectory()) walk(p); else {let b; try{b=readFileSync(p)}catch{continue} if(re.test(b.toString('latin1'))) console.log(p);}}}
walk(out);
