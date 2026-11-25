const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const asset = 'app/src/main/assets/club.js';
const localEngine = path.join(__dirname, 'club', asset);
const engine = fs.existsSync(localEngine) ? localEngine : path.join(__dirname, '..', asset);
const source = fs.readFileSync(process.argv[2] || engine, 'utf8');
const context = vm.createContext({assert, console});
vm.runInContext(source, context);

vm.runInContext(`
const cmd = (op, fields = {}) => JSON.parse(mobile(JSON.stringify({op, ...fields})));
const close = (actual, expected) => assert(Math.abs(actual - expected) < 1e-8, actual + ' vs ' + expected);
function setSpeed(value) { return cmd('option', {name: 'aiSpeed', value}).opt.aiSpeed; }

assert.equal(cmd('init').opt.aiSpeed, 'normal');
for (const legacy of [1, 2]) {
  const old = cmd('save');
  delete old.opt.aiSpeed;
  old.opt.speed = legacy;
  assert.equal(cmd('init', {data: JSON.stringify(old)}).opt.aiSpeed, legacy === 2 ? 'fast' : 'normal');
}
assert.equal(setSpeed('realistic'), 'realistic');
assert.equal(setSpeed('invalid'), 'realistic');
assert.equal(cmd('option', {name: 'speed'}).opt.aiSpeed, 'realistic');
assert.equal(cmd('init', {data: mobile('{"op":"save"}')}).opt.aiSpeed, 'realistic');

state.wallet = 1000000;
launch(2, false, 300000, 6);
const originalRandom = Math.random;
const originalAI = ai;
const template = JSON.stringify(g.ts[0]);
let decided;
function scenario(mode, opts = {}) {
  const {kind='call', call=200, target=600, pot=1200, stage=0, stack=20000,
    coveredBy=20000, alreadyActed=false, roll=.53, side=false, folded=false} = opts;
  const t=JSON.parse(template);
  g.ts=side?[JSON.parse(template),t]:[t];
  t.stage=stage; t.actor=1; t.bb=100; t.sbSeat=2; t.bbSeat=3; t.pending=[0,1,2,3,4,5];
  t.phase='bet'; t.boardHold=0; t.clock=null; t.price=call; t.raise=100;
  t.ps.forEach((p,i) => Object.assign(p,{id:i===0?-1:i,stack:coveredBy,paid:0,bet:0,fold:false,move:null,actedAt:null}));
  const actor=t.ps[t.actor];
  actor.stack=stack; actor.actedAt=alreadyActed?100:null;
  t.ps[0].fold=folded; t.ps[2].paid=pot; t.ps[2].bet=call;
  state.opt.aiSpeed=mode;
  Math.random=()=>roll;
  decided=0;
  ai=()=>{decided++;return kind==='raise'?['raise',target]:[kind]};
  const clock=decisionClock(t);
  assert.equal(clock.full,15); assert.equal(clock.left,15); assert.equal(decided,0);
  botActWhenDue(t,.01);
  assert.equal(decided,1); close(clock.left,14.99);
  return {wait:clock.full-clock.actAt,t,clock};
}

for (const room of ['cash','playing','folded','background']) {
  g.cash=room==='cash';
  for (const kind of ['call','raise','fold']) {
    const opts={kind,side:room==='background',folded:room==='folded'};
    const standard=scenario('normal',opts), fast=scenario('fast',opts);
    close(fast.wait,standard.wait/2);
    if(kind!=='fold'){
      const difficulty=1.65+.18+.53*1.55+200/1200*3.2;
      const old=room==='cash'?difficulty:room==='folded'?Math.max(.8,Math.min(1.65,difficulty*.34)):
        Math.max(.94,Math.min(5.2,difficulty*.76));
      close(standard.wait,old);
    }
  }
}

g.cash=true;
const snap=scenario('realistic',{alreadyActed:true,roll:.3}).wait;
const cold=scenario('realistic',{roll:.3}).wait;
assert(snap<1 && cold>snap+1);
const bigSpot={kind:'raise',call:2400,target:7500,pot:5000,stage:2};
const deep=scenario('realistic',bigSpot).wait;
const covered=scenario('realistic',{...bigSpot,coveredBy:300}).wait;
const normal=scenario('normal',bigSpot).wait;
assert(deep>covered+2 && deep>normal+2 && deep<15);
const single=scenario('realistic',{kind:'raise',call:100,target:300,pot:600,stage:2}).wait;
assert(deep>single+2);
const check=scenario('realistic',{call:0}).wait;
assert(check<2);

// A pause or mode change never resets the consumed part of the gauge or rerolls the decision.
const restoredSpot=scenario('normal',bigSpot);
const {clock}=restoredSpot;
const actor=restoredSpot.t.ps[restoredSpot.t.actor];
const actorId=actor.id;
const before=restoredSpot.wait;
assert.equal(setSpeed('fast'),'fast');
close(clock.full-clock.actAt,before/2); close(clock.left,14.99);
assert.equal(decided,1);
const saved=mobile('{"op":"save"}');
cmd('init',{data:saved});
const t=g.ts[0], c=decisionClock(t);
assert.equal(c.plan[0],'raise'); close(c.left,14.99); close(c.full-c.actAt,before/2);
assert(!('plan' in tableInfo(t)));
botActWhenDue(t,before/2-.011);
assert.equal(t.ps[t.actor].id,actorId);
botActWhenDue(t,.002);
assert.equal(t.ps.find(p=>p.id===actorId).move.kind,'raise');
assert.equal(decided,1);
ai=originalAI; Math.random=originalRandom;
console.log('AI speed modes verified across cash, tournaments, side tables, and restore.',{snap,cold,bigRaise:deep,normalRaise:normal,fastRaise:normal/2});
`, context);
