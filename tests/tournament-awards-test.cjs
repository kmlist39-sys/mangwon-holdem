const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const asset = 'app/src/main/assets/club.js';
const localEngine = path.join(__dirname, 'club', asset);
const source = fs.readFileSync(fs.existsSync(localEngine) ? localEngine : path.join(__dirname, '..', asset), 'utf8');

function client(data, engine=source) {
  const context = vm.createContext({});
  vm.runInContext(engine, context);
  const op = (op, args = {}) => JSON.parse(context.mobile(JSON.stringify({op, ...args})));
  const run = code => vm.runInContext(code, context);
  const view = op('init', {data: data && JSON.stringify(data)});
  return {op, run, view};
}

// Use normal settlement/exit entry points, with the field reduced to the
// requested finishing position. The initial pool and the advertised payouts stand.
function settle(app, rank, exit = 'next') {
  app.run(`{
    const main=g.ts[0], hero=main.ps.find(p=>p.id===-1);
    const field=g.ts.flatMap(t=>t.ps).filter(p=>p.id!==-1);
    const live=field.slice(0,${rank}-1);
    g.rested=true;g.ts=[main];main.phase='done';main.accounted=true;main.level=9;
    main.ps=[hero,...live];main.btn=0;main.autoNextLeft=0;
    g.exits=field.slice(live.length).map(p=>p.id);
    if(${rank}>1){hero.stack=0;g.exits.push(-1);}
  }`);
  const q=app.op('peek').g.exitQuote;
  const view=app.op(exit,{dt:.1,token:q.token});
  assert.equal(view.tournamentFinished,true);
  assert.equal(view.tournamentResult.rank,rank);
  return view;
}

function enter(app, pub, buy, tables, extra = {}) {
  return app.op('enter',{pub,buy,tables,fromLobby:true,...extra});
}

const qualifyingEvents=[
  [2,50000,12,100000],[2,100000,12,150000],
  [3,50000,12,150000],[3,100000,12,200000],
  [4,50000,12,200000],[4,100000,24,500000]
];
for (const [field,cost,tables,face] of qualifyingEvents) {
  let app=client();
  app.op('purchasePack',{id:'chips100',sequence:1});
  const format=app.view.tournamentEntries[0].formats.find(row=>row.tables===field);
  const offer=format.offers.find(row=>row.buyin===cost);
  assert.deepEqual(offer.winnerTicket,{pub:1,tables,buyin:face});
  const initial=enter(app,0,cost,field);
  const winner=settle(app,1,cost===50000?'surrender':'step');
  const id=winner.tournamentResult.ticketAwarded.id,payout=initial.g.payouts[0];
  assert.equal(winner.wallet,initial.wallet+payout);
  assert.equal(winner.career.prizes,payout);
  assert.equal(winner.career.buyins,cost);
  assert.deepEqual(winner.career.podium,[1,0,0]);
  const low=app.op('save');low.wallet=0;
  app=client(low);
  assert.equal(app.op('surrender',{token:initial.g.exitQuote.token}).wallet,0);
  const differentFee=face===100000?200000:100000;
  for (const [pub,buy,size,pass] of [[0,cost,field,id],[1,face,tables===12?24:12,id],[1,face,tables,'old'],[1,differentFee,tables,id],[2,200000,1,id]]) {
    const denied=enter(app,pub,buy,size,{ticketId:pass});
    assert.equal(denied.venueChange,'denied');assert.equal(denied.wallet,0);
    assert.equal(denied.career.tickets[0].id,id);
  }
  assert.equal(enter(app,1,face,tables).venueChange,'denied');
  const free=enter(app,1,face,tables,{ticketId:id});
  assert.equal(free.venueChange,'entered');assert.equal(free.wallet,0);
  assert.equal(free.career.tickets.length,0);assert.equal(free.career.ticketValue,face);
  assert.equal(free.career.buyins,cost);assert.equal(free.career.entries,2);
  assert.equal(free.g.pool,tables*9*face*.8);assert.equal(free.g.exitQuote.fees,0);
  assert.equal(enter(app,1,face,tables,{ticketId:id}).venueChange,'resumed');
  app.op('parkVenue',{session:free.g.sessionId});app=client(app.op('save'));
  const again=app.op('resumeVenue',{pub:1,session:free.g.sessionId,fromLobby:true});
  assert.equal(again.g.exitQuote.fees,0);assert.equal(again.career.entries,2);

  // Fail the rebuy without points, then pay the event fee and restore 60k chips.
  app.run("{const t=g.ts[0];t.ps.find(p=>p.id===-1).stack=0;t.phase='done';t.accounted=false;}");
  assert(app.op('next').g.rebuy);
  assert(app.op('re',{yes:true,ticketId:id}).g.rebuy);
  app.op('purchasePack',{id:'chips100',sequence:2});
  const rebought=app.op('re',{yes:true});
  assert.equal(rebought.wallet,1000000-face);assert.equal(rebought.career.buyins,cost+face);
  assert.equal(rebought.g.exitQuote.fees,face);assert.equal(rebought.career.rebuys,1);
  assert.equal(rebought.g.pool,free.g.pool+face*.8);
  assert.equal(app.op('save').g.ts[0].ps.find(p=>p.id===-1).start,60000);
  const placed=settle(app,2);
  assert.equal(placed.career.prizes,payout+placed.tournamentResult.payout);
  assert.deepEqual(placed.career.podium,[1,1,0]);
  assert.equal(placed.tournamentResult.ticketAwarded,null);
  assert.equal(enter(app,1,face,tables,{ticketId:id}).venueChange,'denied');
  const archive=app.op('awards');assert.equal(archive.pages,1);
  assert.equal(archive.awards[0].fees,face);assert.equal(archive.awards[0].ticketSpent,face);
  assert.equal(archive.awards[1].tables,field);
  assert.deepEqual(archive.awards[1].ticketAwarded,{pub:1,tables,buyin:face,id});
  assert.deepEqual(client(app.op('save')).op('awards'),archive);
}

// One account can hold several distinct 12-table tickets and repeated passes
// from qualifying fields that share a destination.
{
  let app=client();app.op('purchasePack',{id:'chips100',sequence:1});
  for(const [tables,cost] of qualifyingEvents){enter(app,0,cost,tables);settle(app,1);}
  app=client(app.op('save'));
  const groups=app.view.career.tickets;
  assert.deepEqual(groups.map(t=>[t.tables,t.buyin,t.count]),[[12,100000,1],[12,150000,2],[12,200000,2],[24,500000,1]]);
  for(const wrong of [groups[0],groups[2],groups[3]]){
    const denied=enter(app,1,150000,12,{ticketId:wrong.id});
    assert.equal(denied.venueChange,'denied');
    assert.equal(denied.wallet,app.view.wallet);assert.deepEqual(denied.career.tickets,groups);
  }
  const used=enter(app,1,150000,12,{ticketId:groups[1].id});
  assert.equal(used.wallet,app.view.wallet);
  assert.deepEqual(used.career.tickets.map(t=>t.count),[1,1,2,1]);
  assert.notEqual(used.career.tickets[1].id,groups[1].id);
  for(const format of app.view.tournamentEntries[1].formats)assert(format.offers.every(o=>o.winnerTicket===null));
}

{
  const app=client();app.op('purchasePack',{id:'chips100',sequence:1});
  for (const rank of [1,1,2,3,4,4,5]) {enter(app,0,50000,2);settle(app,rank);}
  const newest=app.op('awards'),older=app.op('awards',{page:100});
  assert.deepEqual(newest.career.podium,[2,1,1]);assert.equal(newest.career.tickets[0].count,2);
  assert.equal(newest.career.awardCount,6);assert.equal(newest.career.entries,7);
  assert.equal(newest.career.buyins,350000);assert.equal(newest.pages,2);
  assert.deepEqual(newest.awards.map(row=>row.rank),[4,4,3,2,1]);
  assert.equal(older.page,1);assert.equal(older.awards[0].rank,1);
  // Paying points voluntarily keeps both tickets; rejoining a parked seat also keeps them.
  const paid=enter(app,1,100000,12);assert.equal(paid.career.tickets[0].count,2);
  enter(app,0,50000,2);const back=enter(app,1,100000,12,{ticketId:newest.career.tickets[0].id});
  assert.equal(back.venueChange,'resumed');assert.equal(back.career.tickets[0].count,2);
  const after=app.op('surrender',{token:back.g.exitQuote.token});
  assert.equal(after.career.awardCount,6);assert.equal(enter(app,2,200000,1).career.entries,9);
}

{
  const old=client();old.op('purchasePack',{id:'chips100',sequence:1});
  enter(old,1,150000,12);enter(old,0,50000,2);settle(old,1);
  const saved=old.op('save');delete saved.career;
  const result=saved.tournamentResult;
  for(const field of ['at','buyin','tables','ticketSpent','ticketAwarded'])delete result[field];
  const migrated=client(saved),award=migrated.op('awards');assert.equal(migrated.view.wallet,saved.wallet);
  assert.equal(award.career.entries,2);assert.equal(award.career.buyins,200000);
  assert.equal(award.awards[0].at,0);assert.equal(award.career.tickets[0].count,1);
  assert.deepEqual(client(migrated.op('save')).op('awards'),award);
  result.pool=987654;result.fees=100000;
  const ambiguous=client(saved).op('awards');assert.equal(ambiguous.career.tickets.length,0);
  assert.equal(ambiguous.awards[0].buyin,0);assert.equal(ambiguous.career.prizes,result.payout);
  result.buyin=50000;delete result.initial;
  const unknownSize=client(saved).op('awards');assert.equal(unknownSize.career.tickets.length,0);
  assert.equal(unknownSize.awards[0].tables,0);
}

function verifyLegacyInventory(save){
  const {wallet,career}=save,known=career.ticketIds['12'][0],older=career.ticketIds['24'][0];
  const app=client(save),history=app.op('awards');
  assert.deepEqual(history.career.tickets,[
    {pub:1,tables:12,buyin:100000,id:known,count:1},{pub:1,tables:24,buyin:200000,id:older,count:1}
  ]);
  assert.equal(history.career.prizes,career.prizes);
  assert.equal(history.career.ticketValue,career.ticketValue);
  assert.deepEqual(history.awards,career.awards.slice().reverse());
  assert.deepEqual(client(app.op('save')).op('awards'),history);
  assert.equal(enter(app,1,500000,24,{ticketId:older}).venueChange,'denied');
  assert.equal(enter(app,1,200000,12,{ticketId:older}).venueChange,'denied');
  const free=enter(app,1,200000,24,{ticketId:older});
  assert.equal(free.venueChange,'entered');assert.equal(free.wallet,wallet);
  assert.equal(free.career.tickets.length,1);assert.equal(free.career.buyins,career.buyins);
  assert.equal(free.g.pool,24*9*200000*.8);
}

{
  const old=client().op('save');
  const noLongerOwned={id:'already-spent',pub:1,tables:12,buyin:100000};
  old.career={entries:4,rebuys:0,buyins:200000,ticketValue:100000,prizes:648000,podium:[3,0,0],
    ticketIds:{12:['old-12'],24:['old-24']},awards:[
      {sessionId:'earlier',pub:0,tables:2,buyin:50000,rank:1,payout:324000,ticketAwarded:noLongerOwned},
      {sessionId:'another',pub:0,tables:4,buyin:100000,rank:1,payout:324000,ticketAwarded:{id:'old-24',pub:1,tables:24,buyin:200000}}
    ]};
  verifyLegacyInventory(old);
}
// Optional compatibility check using the actual rules from a previously released APK.
if(process.argv[2]){
  const old=client(undefined,fs.readFileSync(process.argv[2],'utf8'));
  old.op('purchasePack',{id:'chips100',sequence:1});
  enter(old,0,50000,2);const first=settle(old,1);
  const free=enter(old,1,100000,12,{ticketId:first.career.tickets[0].id});
  old.op('surrender',{token:free.g.exitQuote.token});
  enter(old,0,100000,4);settle(old,1);
  enter(old,0,50000,3);settle(old,1);
  verifyLegacyInventory(old.op('save'));
}
console.log('Six tournament pass tiers, exact redemption, balances, old inventory and recovery passed.');
