const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),assert=require('node:assert/strict');
const asset='app/src/main/assets/club.js',localEngine=path.join(__dirname,'club',asset);
const code=fs.readFileSync(fs.existsSync(localEngine)?localEngine:path.join(__dirname,'..',asset),'utf8');
for(let [pub,buy] of [[0,50000],[0,100000],[1,100000],[1,150000],[1,200000]]){
 let cx=vm.createContext({});vm.runInContext(code,cx);let op=(op,more={})=>JSON.parse(cx.mobile(JSON.stringify({op,...more})));
 const tables=pub===1?12:2,entrants=tables*9;
 op('init');for(let i=0;i<2;i++)op('purchasePack',{id:'chips100',sequence:i+1});let v=op('enter',{pub,buy});assert.equal(v.g.buyin,buy);assert.equal(v.g.pool,entrants*buy*.8);assert.equal(v.g.reveal,true);assert.equal(v.wallet,2050000-buy);
 let s=op('save');assert.deepEqual(s.g.ts.map(t=>t.ps.length),Array(tables).fill(9));assert.equal(s.g.ts[0].ps[0].start,50000);
 let hero=s.g.ts[0].ps.find(p=>p.id===-1);hero.stack=0;s.g.ts.forEach(t=>{t.phase='done';t.level=2});op('init',{data:JSON.stringify(s)});assert(op('next').g.rebuy);let r=op('re',{yes:true});assert.equal(r.wallet,2050000-buy-buy);assert.equal(r.g.pool,(entrants+1)*buy*.8);assert.equal(op('save').g.ts[0].ps.find(p=>p.id===-1).start,60000);
}console.log('Venue entry fees, 2×9 and 12×9 seating, 50K starting stacks, entry-priced rebuys with 60K chips, saved game and automatic reveal passed');
