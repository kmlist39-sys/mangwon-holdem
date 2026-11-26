const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const asset = 'app/src/main/assets/club.js';
const localEngine = path.join(__dirname, 'club', asset);
const source = fs.readFileSync(fs.existsSync(localEngine) ? localEngine : path.join(__dirname, '..', asset), 'utf8');

function client(data) {
  const context = vm.createContext({});
  vm.runInContext(source, context);
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

for (const [cost,tables,face] of [[50000,12,100000],[100000,24,200000]]) {
  let app=client();
  app.op('purchasePack',{id:'chips100',sequence:1});
  const offer=app.view.tournamentEntries[0].offers.find(row=>row.buyin===cost);
  assert.deepEqual(offer.winnerTicket,{pub:1,tables,buyin:face});
  const initial=enter(app,0,cost,4);
  const winner=settle(app,1,cost===50000?'surrender':'step');
  const id=winner.tournamentResult.ticketAwarded.id,payout=initial.g.payouts[0];
  assert.equal(winner.wallet,initial.wallet+payout);
  assert.equal(winner.career.prizes,payout);
  assert.equal(winner.career.buyins,cost);
  assert.deepEqual(winner.career.podium,[1,0,0]);
  const low=app.op('save');low.wallet=0;
  app=client(low);
  assert.equal(app.op('surrender',{token:initial.g.exitQuote.token}).wallet,0);
  for (const [pub,buy,size,pass] of [[0,cost,4,id],[1,face,tables===12?24:12,id],[1,face,tables,'old'],[1,500000,24,id],[2,200000,1,id]]) {
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
  assert.equal(archive.awards[1].tables,4);assert.equal(archive.awards[1].ticketAwarded.id,id);
  assert.deepEqual(client(app.op('save')).op('awards'),archive);
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
}
console.log('Tournament ticket issuance, exact redemption, cash exclusions, full-fee rebuys, sponsored pools, podium history, migration, pagination and recovery passed.');
