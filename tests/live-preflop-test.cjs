const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { test } = require('node:test');
const vm = require('node:vm');

const asset = 'app/src/main/assets/club.js';
const localEngine = path.join(__dirname, 'club', asset);
const engine = fs.existsSync(localEngine) ? localEngine : path.join(__dirname, '..', asset);
const source = fs.readFileSync(process.argv[2] || engine, 'utf8');

// Run the real phone bridge in a fresh game. Only the deal, seats and stacks
// are fixed. Blind posting, action order, price changes and snapshots use the engine.
function fixture({ hand = ['Qh', 'Qd'], button = 6, stacks = {}, seed = 34782 } = {}, script = source) {
  const context = vm.createContext({ assert, setup: { hand, button, stacks, seed } });
  vm.runInContext(script, context);
  return vm.runInContext('(' + gameFixture.toString() + ')()', context);
}

function gameFixture() {
  let seed = setup.seed;
  Math.random = () => {
    seed = (Math.imul(1664525, seed) + 1013904223) >>> 0;
    return seed / 4294967296;
  };
  const phone = data => JSON.parse(mobile(JSON.stringify(data)));
  phone({ op: 'enter', pub: 0, double: false, buy: 50000, seats: 9, tables: 2 });
  let t = g.ts[0];
  assert.equal(t.ps[0].id, -1);
  const defaultStacks = [465600, ...Array(5).fill(200000), 58000, 122000, 58000];
  t.ps.forEach((p, i) => { p.stack = setup.stacks[i] ?? defaultStacks[i]; });
  t.btn = (setup.button + 8) % 9;
  startHand(t, { double: false, level: 5, sb: 2000, bb: 4000 });
  g.count = t.hand;
  g.elapsed = 5 * blindLevelSeconds(g.pub);
  const hero = t.ps[0];
  const piles = [...t.ps.map(p => p.hole), t.dc];
  setup.hand.forEach((name, i) => {
    const id = 'shcd'.indexOf(name[1]) * 13 + '23456789TJQKA'.indexOf(name[0]);
    const pile = piles.find(cards => cards.includes(id));
    const match = pile.indexOf(id);
    [hero.hole[i], pile[match]] = [pile[match], hero.hole[i]];
  });
  assert.equal(new Set(piles.flat()).size, 52);
  phone({ op: 'peek' });

  function play(seat, kind, target) {
    assert.equal(t.actor, seat, 'unexpected actor before ' + kind);
    if (seat === 0) {
      const frame = phone({ op: 'act', session: g.exitId, hand: g.count, kind, amount: target });
      assert.equal(frame.g.lastHero?.kind, kind);
    } else {
      assert(act(t, kind, target));
    }
  }
  function request() {
    const { g: frame } = phone({ op: 'peek' });
    assert(frame.own);
    return { op: 'liveGuide', session: frame.sessionId, hand: frame.hand,
      street: frame.street, pot: frame.pot, call: frame.call };
  }
  function guide() {
    const result = phone(request());
    assert.equal(result.fold + result.passive + result.raise, 100);
    for (const n of [result.fold, result.passive, result.raise]) {
      assert(Number.isInteger(n) && n >= 0 && n <= 100);
    }
    assert.equal(result.canRaise, canRaise(t, t.ps[0]));
    if (result.canRaise) {
      const max = t.ps[0].stack + t.ps[0].bet;
      assert(result.raiseTo >= Math.min(t.price + t.raise, max) && result.raiseTo <= max);
    } else {
      assert.equal(result.raise, 0);
      assert.equal(result.raiseTo, 0);
    }
    return result;
  }
  function toScreenshot() {
    play(0, 'call');
    for (let i = 1; i <= 5; i++) play(i, 'fold');
    play(6, 'call');
    play(7, 'raise', 12000);
    play(8, 'fold');
    return guide();
  }
  const save = () => mobile(JSON.stringify({ op: 'save' }));
  function reload(data = save()) {
    phone({ op: 'init', data });
    t = g.ts[0];
  }
  function checkPublicReads() {
    const allowed = request();
    const expected = phone(allowed);
    const noRead = () => { throw Error('guide read private information or advanced game RNG'); };
    for (const other of t.ps.slice(1)) Object.defineProperty(other, 'hole', { get: noRead });
    Object.defineProperty(t, 'dc', { get: noRead });
    Math.random = noRead;
    assert.deepEqual(phone(allowed), expected);
    for (const stale of [{ hand: allowed.hand - 1 }, { pot: allowed.pot + 1000 }, { call: 0 }]) {
      assert.deepEqual(phone({ ...allowed, ...stale }), {});
    }
  }
  return { play, guide, toScreenshot, save, reload, checkPublicReads, remaining: seat => t.ps[seat].stack };
}

test('nine-handed QQ opens across deal seeds and suit permutations', () => {
  for (const suits of ['sh', 'sc', 'sd', 'hc', 'hd', 'cd', 'dh']) {
    const hand = [...suits].map(suit => 'Q' + suit);
    for (const seed of [1, 34782, 0xbbcc]) {
      const spot = fixture({ hand, seed }).guide();
      assert.equal(spot.pot, 6000);
      assert.equal(spot.call, 4000);
      assert(spot.fold <= 5 && spot.raise >= 70, JSON.stringify(spot));
    }
  }
  const offsuit = fixture({ hand: ['7s', '2h'] }).guide();
  assert(offsuit.fold >= 80, JSON.stringify(offsuit));
});

test('hero and button limp, SB raises to 12k, folded BB stays in the pot', testCase => {
  const game = fixture();
  const first = game.guide();
  const spot = game.toScreenshot();
  for (const [label, result] of [['QQ first in', first], ['QQ vs SB 12k', spot]]) {
    testCase.diagnostic(label + ': pot=' + result.pot + ', call=' + result.call +
      ', fold/call/raise=' + [result.fold, result.passive, result.raise].join('/') +
      ', raise to=' + result.raiseTo + ', equity=' + result.equity + '%');
  }
  assert.equal(game.remaining(0), 461600);
  assert.equal(game.remaining(6), 54000);
  assert.equal(game.remaining(7), 110000);
  assert.equal(spot.pot, 24000);
  assert.equal(spot.eligible, 24000);
  assert.equal(spot.call, 8000);
  assert.equal(spot.odds, 25);
  assert(spot.fold <= 10 && spot.raise > 0, JSON.stringify(spot));
  game.reload();
  assert.deepEqual(game.guide(), spot);
  game.checkPublicReads();
  const weak = fixture({ hand: ['7s', '2h'] }).toScreenshot();
  assert(weak.fold > spot.fold && weak.raise < spot.raise);
});

test('a BB facing an open and then a four-bet distinguishes the second decision', () => {
  const game = fixture({ hand: ['As', 'Jh'], button: 7 });
  game.play(1, 'raise', 12000);
  for (let i = 2; i <= 8; i++) game.play(i, 'fold');
  const vsOpen = game.guide();
  game.play(0, 'raise', 36000);
  game.play(1, 'raise', 84000);
  const vsFourBet = game.guide();
  assert.equal(vsOpen.call, 8000);
  assert.equal(vsFourBet.call, 48000);
  assert(vsFourBet.fold > vsOpen.fold, JSON.stringify({ vsOpen, vsFourBet }));
});

test('premium and weak hands receive different recommendations facing a three-bet', () => {
  function facingThreeBet(hand) {
    const game = fixture({ hand });
    game.play(0, 'raise', 12000);
    game.play(1, 'raise', 36000);
    for (let i = 2; i <= 8; i++) game.play(i, 'fold');
    return game.guide();
  }
  const aces = facingThreeBet(['As', 'Ad']);
  const weak = facingThreeBet(['7s', '2h']);
  assert(aces.fold <= 5 && weak.fold > aces.fold && aces.raise > weak.raise);
});

test('a deep 125 BB five-bet applies much more pressure to QQ than a 3 BB open', () => {
  function facingFiveBet(hand) {
    const game = fixture({ hand, stacks: { 0: 1200000, 1: 1200000 } });
    game.play(0, 'raise', 12000);
    game.play(1, 'raise', 36000);
    for (let i = 2; i <= 8; i++) game.play(i, 'fold');
    game.play(0, 'raise', 100000);
    game.play(1, 'raise', 500000);
    return game.guide();
  }
  const aces = facingFiveBet(['As', 'Ad']);
  const queens = facingFiveBet(['Qh', 'Qd']);
  const offsuit = facingFiveBet(['7s', '2h']);
  assert.equal(queens.pot, 606000);
  assert.equal(queens.call, 400000);
  assert(queens.fold > fixture().toScreenshot().fold, JSON.stringify(queens));
  assert(aces.fold < queens.fold && queens.fold <= offsuit.fold);
  assert(aces.fold <= 5 && offsuit.fold >= 85, JSON.stringify({ aces, queens, offsuit }));
});

test('pot odds distinguish a nearly free all-in call from one for 300 BB', () => {
  function blindVsShove(hand, heroStack) {
    const game = fixture({ hand, button: 7, stacks: { 0: heroStack, 1: 1200000 } });
    game.play(1, 'raise', 1200000);
    for (let i = 2; i <= 8; i++) game.play(i, 'fold');
    return game.guide();
  }
  for (const hand of [['7s', '2h'], ['As', 'Ad']]) {
    const cheap = blindVsShove(hand, 4100);
    assert.equal(cheap.call, 100);
    assert.equal(cheap.eligible, 10100);
    assert.equal(cheap.odds, 1);
    assert(cheap.allInCall && !cheap.canRaise && cheap.passive >= 95, JSON.stringify(cheap));
  }
  const weak = blindVsShove(['7s', '2h'], 1200000);
  const aces = blindVsShove(['As', 'Ad'], 1200000);
  assert(weak.odds > 49 && weak.fold >= 90, JSON.stringify(weak));
  assert(aces.passive >= 95 && aces.allInCall, JSON.stringify(aces));
});

test('short all-in calls use eligible chips, and incomplete shoves cannot reopen raises', () => {
  const onlyRaise = fixture({ hand: ['As', 'Ad'], stacks: { 0: 6000 } }).guide();
  assert.equal(onlyRaise.raiseTo, 6000);
  assert(onlyRaise.canRaise && onlyRaise.allInRaise && !onlyRaise.allInCall);
  const capped = fixture({ stacks: { 0: 6500 } }).toScreenshot();
  assert.equal(capped.call, 2500);
  assert.equal(capped.pot, 24000);
  assert.equal(capped.eligible, 18500);
  assert.equal(capped.odds, 11.9);
  assert(capped.allInCall && !capped.canRaise);
  const game = fixture({ stacks: { 2: 16000 } });
  game.play(0, 'raise', 12000);
  game.play(1, 'call');
  game.play(2, 'raise', 16000);
  for (let i = 3; i <= 8; i++) game.play(i, 'fold');
  const locked = game.guide();
  assert.equal(locked.call, 4000);
  assert(!locked.canRaise && !locked.allInCall);
});

test('big blind option can always check for free', () => {
  const game = fixture({ hand: ['7s', '2h'], button: 7 });
  game.play(1, 'call');
  for (let i = 2; i <= 7; i++) game.play(i, 'fold');
  game.play(8, 'call');
  const free = game.guide();
  assert.equal(free.call, 0);
  assert.equal(free.fold, 0);
});

// Supplying an older APK's assets/club.js as the second path exercises its actual
// serialized game on the current bridge, including action history across upgrades.
if (process.argv[3]) {
  const previous = fs.readFileSync(process.argv[3], 'utf8');
  test('a saved hand from the older engine resumes directly into live preflop advice', () => {
    const prior = fixture({}, previous);
    const updated = fixture();
    updated.reload(prior.save());
    assert(updated.guide().fold <= 5);
    prior.toScreenshot();
    updated.reload(prior.save());
    const spot = updated.guide();
    assert.equal(spot.pot, 24000);
    assert.equal(spot.call, 8000);
    assert(spot.fold <= 10 && spot.raise > 0);
    updated.checkPublicReads();
  });
}
