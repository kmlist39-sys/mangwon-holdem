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
function fixture({ hand = ['Qh', 'Qd'], button = 6, stacks = {}, seed = 34782, double = false } = {}, script = source) {
  const context = vm.createContext({ assert, setup: { hand, button, stacks, seed, double } });
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
  if (setup.double) state.wallet = 200000;
  phone({ op: 'enter', pub: setup.double ? 2 : 0, double: setup.double, buy: setup.double ? 200000 : 50000, seats: 9, tables: 2 });
  let t = g.ts[0];
  assert.equal(t.ps[0].id, -1);
  const defaultStacks = [465600, ...Array(5).fill(200000), 58000, 122000, 58000];
  t.ps.forEach((p, i) => { p.stack = setup.stacks[i] ?? defaultStacks[i]; });
  t.btn = (setup.button + 8) % 9;
  startHand(t, { double: setup.double, level: 5, sb: setup.double ? 30000 : 2000, bb: 4000, ante: 10000 });
  t.boardHold = 0;
  g.count = t.hand;
  g.elapsed = 5 * blindLevelSeconds(g.pub);
  const hero = t.ps[0];
  const piles = [...t.ps.map(p => p.hole), ...t.boards, t.dc];
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
    for (const stale of [{ hand: allowed.hand - 1 }, { pot: allowed.pot + 1000 }, { call: allowed.call + 1 }]) {
      assert.deepEqual(phone({ ...allowed, ...stale }), {});
    }
  }
  function advanceStreet(cards) {
    assert.equal(t.phase, 'street');
    if (cards) {
      const id = card => 'shcd'.indexOf(card[1]) * 13 + '23456789TJQKA'.indexOf(card[0]);
      const available = [...t.ps.slice(1).map(p => p.hole), t.dc];
      cards.forEach((name, i) => {
        const pile = available.find(cards => cards.includes(id(name)));
        const j = pile.indexOf(id(name));
        [t.dc[i], pile[j]] = [pile[j], t.dc[i]];
      });
    }
    street(t);
  }
  return { play, guide, toScreenshot, save, reload, checkPublicReads, advanceStreet, remaining: seat => t.ps[seat].stack };
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
  const { action, seats, commitment, sizing } = spot.detail;
  assert.match(spot.detail.mixReasonKo, /QQ.*프리미엄 페어.*JJ\/TT/);
  assert.match(spot.detail.mixReasonEn, /QQ.*premium pair.*JJ\/TT/);
  assert.match(sizing.reasonKo, /SB의 추가 콜은 24,000칩/);
  assert.match(sizing.reasonEn, /SB faces 24,000/);
  assert.equal(action.before, 0);
  assert.equal(action.after, 2);
  assert.equal(action.pending, 1);
  assert.equal(action.raiseThreats, 1);
  const [button, smallBlind] = seats;
  assert.equal(button.position, 'BTN');
  assert.equal(button.role, 'caller');
  assert.equal(button.difference, -407600);
  assert.equal(button.effective, 46000);
  assert(button.pending && button.canReraise);
  assert.equal(smallBlind.position, 'SB');
  assert.equal(smallBlind.role, 'raiser');
  assert.equal(smallBlind.difference, -351600);
  assert.equal(smallBlind.effective, 110000);
  assert(!smallBlind.pending && !smallBlind.canReraise);
  assert.equal(action.lastRaiserId, smallBlind.id);
  assert.equal(commitment.heroStack, 461600);
  assert.equal(commitment.startingStack, 465600);
  assert.equal(commitment.invested, 4000);
  assert.equal(commitment.investedPct, 0.9);
  assert.equal(commitment.committedAfterCallPct, 2.6);
  assert.equal(commitment.afterCall, 453600);
  assert.equal(commitment.eligibleAfterCall, 32000);
  assert.equal(commitment.effectiveAfterCall, 110000);
  assert.equal(commitment.spr, 3.44);
  assert.equal(sizing.target, spot.raiseTo);
  assert.equal(sizing.pay, sizing.target - 4000);
  assert.equal(sizing.increase, sizing.target - 12000);
  assert.equal(sizing.potMultiple, Math.round(100 * sizing.increase / 32000) / 100);
  for (const part of [action, commitment, sizing]) {
    assert(part.reasonKo && part.reasonEn && Math.max(part.reasonKo.length, part.reasonEn.length) < 260);
  }
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
  assert.equal(capped.detail.commitment.eligibleAfterCall, 21000);
  assert.equal(capped.detail.commitment.committedAfterCallPct, 100);
  assert.equal(capped.detail.commitment.spr, 0);
  assert.equal(capped.detail.action.raiseThreats, 0);
  const game = fixture({ stacks: { 2: 16000 } });
  game.play(0, 'raise', 12000);
  game.play(1, 'call');
  game.play(2, 'raise', 16000);
  for (let i = 3; i <= 8; i++) game.play(i, 'fold');
  const locked = game.guide();
  assert.equal(locked.call, 4000);
  assert(!locked.canRaise && !locked.allInCall);
  assert.equal(locked.detail.action.allIn, 1);
  assert.equal(locked.detail.action.pending, 1);
  assert.equal(locked.detail.action.raiseThreats, 0);
  assert.equal(locked.detail.seats.find(s => s.pending).canReraise, false);
  assert.match(locked.detail.sizing.reasonEn, /did not reopen/i);
});

test('cumulative short shoves reopen both the original raiser and the pending caller', () => {
  const game = fixture({ stacks: { 2: 16000, 3: 20000 } });
  game.play(0, 'raise', 12000);
  game.play(1, 'call');
  game.play(2, 'raise', 16000);
  game.play(3, 'raise', 20000);
  for (let i = 4; i <= 8; i++) game.play(i, 'fold');
  const result = game.guide();
  assert(result.canRaise);
  assert.equal(result.detail.action.pending, 1);
  assert.equal(result.detail.action.raiseThreats, 1);
  assert.equal(result.detail.action.allIn, 2);
  assert.equal(result.detail.sizing.minTo, 28000);
});

test('covering stacks still to act tighten a marginal opening decision', () => {
  const tiny = Object.fromEntries([1, 2, 3, 4, 5, 6, 8].map(i => [i, 4100]));
  let changed = false;
  for (const hand of [['As', '9h'], ['Qs', 'Jh'], ['Ks', 'Th'], ['As', '6s'], ['Js', 'Ts'], ['6s', '6h']]) {
    const shallow = fixture({ hand, stacks: tiny }).guide();
    const deep = fixture({ hand }).guide();
    assert.deepEqual(shallow.detail.action.pending, deep.detail.action.pending);
    if (deep.fold > shallow.fold) changed = true;
  }
  assert(changed, 'the same positions and price should reflect the effective stacks still to act');
});

test('calling all-in into a lone big blind closes the action even if BB has an unacted option', () => {
  const game = fixture({ button: 0, stacks: { 0: 4000 } });
  for (let i = 3; i <= 8; i++) game.play(i, 'fold');
  // With two opponents the SB can still choose a response, and the BB retains
  // its option against SB. Neither can raise the hero after this all-in call.
  const result = game.guide();
  assert.equal(result.detail.action.pending, 2);
  assert.equal(result.detail.action.raiseThreats, 0);
  assert.equal(result.detail.commitment.afterCall, 0);

  const two = fixture({ button: 8, stacks: { 0: 4000 } });
  for (let i = 2; i <= 8; i++) two.play(i, 'fold');
  const headsUp = two.guide();
  assert.equal(headsUp.detail.action.pending, 0);
  assert.equal(headsUp.detail.seats[0].position, 'BB');
});

test('a live opponent acts before the button on the flop, with a rounded one-third-pot dry-board bet', () => {
  const game = fixture({ button: 0, hand: ['Ah', 'Ad'] });
  for (let i = 3; i <= 8; i++) game.play(i, 'fold');
  game.play(0, 'call');
  game.play(1, 'fold');
  game.play(2, 'call');
  game.advanceStreet(['Ks', '7h', '2d']);
  game.play(2, 'call');
  const dry = game.guide(), { seats, action, sizing } = dry.detail;
  assert.equal(dry.opening, true);
  assert.match(dry.detail.mixReasonKo, /오버페어.*가치 베팅/);
  assert.match(dry.detail.mixReasonEn, /Overpair.*value bet/);
  assert.equal(dry.fold, 0);
  assert.equal(action.before, 1);
  assert.equal(action.after, 0);
  assert.equal(action.pending, 0);
  assert.equal(seats[0].position, 'BB');
  assert.equal(seats[0].side, 'before');
  assert.equal(sizing.target, 4000); // 10,000 pot, rounded and clamped to a legal BB.
  assert.equal(sizing.potMultiple, 0.4);
  assert.equal(sizing.pay, sizing.target);
  game.checkPublicReads();
});

test('a short all-in only contests the main pot; side-pot equity is weighted by chips', () => {
  const game = fixture({ button: 0, stacks: { 1: 12000 }, hand: ['9d', '9c'] });
  for (let i = 3; i <= 8; i++) game.play(i, 'fold');
  game.play(0, 'raise', 12000);
  game.play(1, 'call');
  game.play(2, 'call');
  game.advanceStreet(['Ks', '7h', '2d']);
  game.play(2, 'raise', 32000);
  const result = game.guide(), [main, side] = result.detail.potLayers;
  assert.equal(main.amount, 36000);
  assert.equal(main.opponents, 2);
  assert.equal(side.amount, 64000);
  assert.equal(side.opponents, 1);
  assert(side.equity > main.equity);
  assert(Math.abs(result.equity - (main.equity * 0.36 + side.equity * 0.64)) <= 0.55);
  assert.equal(result.odds, 32);
  game.checkPublicReads();
});

test('double-board first action follows the live small blind and includes all dead antes', () => {
  const game = fixture({ button: 7, double: true, hand: ['As', 'Kh', 'Qd'] });
  const result = game.guide(), { action, seats, sizing } = result.detail;
  assert.equal(result.detail.double, true);
  assert.match(result.detail.mixReasonKo, /위 보드.*아래 보드/);
  assert.equal(result.detail.cash, true);
  assert.equal(result.pot, 120000);
  assert.equal(result.call, 30000);
  assert.equal(sizing.potAfterCall, 150000);
  assert.equal(action.before, 0);
  assert.equal(action.after, 8);
  assert.equal(action.pending, 8);
  assert.equal(seats[seats.length - 1].position, 'SB');
  game.checkPublicReads();
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
