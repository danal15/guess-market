'use strict';

/**
 * The Guess Market web client.
 *
 * It talks to the very same endpoints the JavaFX client of exercise 3 uses, and
 * shows the same things on the same two screens. Everything on screen arrives
 * from one request a second: the server gathers a whole screen's worth under a
 * single lock, so what is shown always describes one moment in the market
 * rather than a balance from before a trade beside holdings from after it.
 *
 * Uploading a file of events is the one thing from exercise 3 that is not here,
 * because the exercise says to leave it out. Everything else a user can do -
 * creating an event, opening it, trading in it, closing it, loading funds and
 * the chat - is.
 *
 * Nothing from the server is ever put into the page as markup. Event names,
 * option names, descriptions, user names and chat lines all come from somebody's
 * typing or from an uploaded file, and the server passes them through untouched;
 * building every node with createTextNode means a name with a tag in it stays a
 * name instead of becoming part of the page.
 */

const API = '/guess-market';
const PULL_MS = 1000;
const TIMEOUT_MS = 5000;

// --------------------------------------------------------------- talking

/** What the servlets answer with when they refuse something on purpose. */
class Refused extends Error {
  constructor(message, type) {
    super(message);
    this.type = type;
  }
}

/**
 * Gives up after a while.
 *
 * fetch waits for ever by default. A server that has stopped answering would
 * leave the poll in flight and, because only one poll is allowed out at a time,
 * the screen would quietly freeze instead of saying so.
 *
 * AbortSignal.timeout is only in browsers from 2022 onwards. On an older one it
 * is simply missing, and every single call would throw before it was sent - so
 * the whole client would look like a dead server. The long way round costs two
 * lines and works everywhere.
 */
function givingUpAfter(ms) {
  if (typeof AbortSignal !== 'undefined' && AbortSignal.timeout) {
    return AbortSignal.timeout(ms);
  }
  const control = new AbortController();
  setTimeout(() => control.abort(), ms);
  return control.signal;
}

async function call(path, options) {
  let answer;
  try {
    answer = await fetch(API + path, {
      credentials: 'same-origin',
      signal: givingUpAfter(TIMEOUT_MS),
      ...options,
    });
  } catch (networkError) {
    throw new Refused(
      'The server could not be reached. Make sure Tomcat is running.',
      'Unreachable'
    );
  }

  const text = await answer.text();
  let body = null;
  if (text) {
    try {
      body = JSON.parse(text);
    } catch (notJson) {
      // Tomcat answers a wrong method, or a wrong context path, with an HTML
      // page rather than one of the servlets' refusals.
      throw new Refused('The server sent back something unexpected.', 'BadAnswer');
    }
  }

  if (!answer.ok) {
    const message = body && body.message
      ? body.message
      : 'The server answered ' + answer.status + '.';
    throw new Refused(message, (body && body.type) || 'Error');
  }
  return body;
}

/**
 * A POST body the servlets can read.
 *
 * It has to be form encoded: every parameter on the server goes through
 * request.getParameter, which reads a query string or a form encoded body and
 * nothing else. FormData would send the same fields as multipart and every one
 * of them would arrive as missing.
 */
function form(fields) {
  const body = new URLSearchParams();
  for (const [name, value] of Object.entries(fields)) {
    if (value !== null && value !== undefined) {
      body.set(name, String(value));
    }
  }
  return {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },
    body,
  };
}

function query(fields) {
  const parts = new URLSearchParams();
  for (const [name, value] of Object.entries(fields)) {
    if (value !== null && value !== undefined && value !== '') {
      parts.set(name, String(value));
    }
  }
  const text = parts.toString();
  return text ? '?' + text : '';
}

function jsonBody(value) {
  return {
    method: 'POST',
    headers: { 'Content-Type': 'application/json;charset=UTF-8' },
    body: JSON.stringify(value),
  };
}

const api = {
  login: (name) => call('/login', form({ name })),
  sync: (eventId, chatFrom) => call('/sync' + query({ eventId, chatFrom })),
  loadFunds: (amount) => call('/funds', form({ amount })),
  createEvent: (request) => call('/event/new', jsonBody(request)),
  openEvent: (eventId) => call('/event/open', form({ eventId })),
  closeEvent: (eventId, winningOption) => call('/event/close', form({ eventId, winningOption })),
  quoteLmsr: (eventId, option, quantity) =>
    call('/trade/lmsr-quote' + query({ eventId, option, quantity })),
  buyLmsr: (eventId, option, quantity) =>
    call('/trade/lmsr-buy', form({ eventId, option, quantity })),
  quoteOrder: (eventId, option, side, price, quantity) =>
    call('/trade/order-quote' + query({ eventId, option, side, price, quantity })),
  placeOrder: (eventId, option, side, price, quantity) =>
    call('/trade/order', form({ eventId, option, side, price, quantity })),
  chat: (said) => call('/chat', form({ text: said })),
};

// -------------------------------------------------------------- numbers

/** The same rules the desktop application formats by, so the two never disagree. */
const fmt = {
  money(value) {
    return value <= -0.005
      ? '-$' + Math.abs(value).toFixed(2)
      : '$' + Math.max(value, 0).toFixed(2);
  },
  /** A price that is not defined yet is a dash, never a misleading zero. */
  price(value) {
    return value === null || value === undefined ? '-' : fmt.money(value);
  },
  signed(value) {
    // Anything under half a cent reads as no change at all, so it gets no sign.
    if (Math.abs(value) < 0.005) return '$0.00';
    return (value >= 0 ? '+' : '-') + '$' + Math.abs(value).toFixed(2);
  },
  percent: (value) => value + '%',
  yesNo: (value) => (value ? 'Yes' : 'No'),
  count: (value) => String(value),
  plural: (count, word) => count + ' ' + word + (count === 1 ? '' : 's'),
};

// ----------------------------------------------------------------- state

const state = {
  me: null,
  snapshot: null,
  selectedEventId: null,
  tab: 'events',
  filters: { orderBook: null, status: null, commission: null },
  sort: { column: null, descending: false },
  polling: false,
  refreshWanted: false,
  timer: null,
  offline: false,
  chatLines: [],
  // What each part of the page was last built from, so a poll that brings no
  // news leaves the page alone. Rebuilding every second would otherwise throw
  // away a half-scrolled table and the text somebody was in the middle of
  // selecting, once a second, for ever.
  prints: {},
};

const $ = (id) => document.getElementById(id);

function text(value) {
  return String(value === null || value === undefined ? '' : value);
}

/** True when this part of the page would now show something different. */
function changed(key, value) {
  const print = JSON.stringify(value);
  if (state.prints[key] === print) return false;
  state.prints[key] = print;
  return true;
}

// ------------------------------------------------------------------ nodes

/** Builds an element. Children are set as text, never as markup. */
function el(tag, attributes, children) {
  const node = document.createElement(tag);
  for (const [name, value] of Object.entries(attributes || {})) {
    if (value === null || value === undefined || value === false) continue;
    if (name === 'class') node.className = value;
    else if (name === 'onclick') node.addEventListener('click', value);
    else node.setAttribute(name, value === true ? '' : String(value));
  }
  for (const child of [].concat(children === undefined ? [] : children)) {
    if (child === null || child === undefined || child === false) continue;
    node.append(child instanceof Node ? child : document.createTextNode(String(child)));
  }
  return node;
}

function svg(tag, attributes, children) {
  const node = document.createElementNS('http://www.w3.org/2000/svg', tag);
  for (const [name, value] of Object.entries(attributes || {})) {
    if (value === null || value === undefined || value === false) continue;
    node.setAttribute(name, String(value));
  }
  for (const child of [].concat(children === undefined ? [] : children)) {
    if (child === null || child === undefined || child === false) continue;
    node.append(child instanceof Node ? child : document.createTextNode(String(child)));
  }
  return node;
}

function clear(node) {
  while (node.firstChild) node.removeChild(node.firstChild);
}

function fill(node, children) {
  clear(node);
  [].concat(children).forEach((c) => { if (c) node.append(c); });
}

function table(headings, rows, emptyMessage) {
  const wrap = el('div', { class: 'table-wrap' });
  const t = el('table');
  t.append(el('thead', {}, el('tr', {},
    headings.map((h) => el('th', { class: h.num ? 'num' : null }, h.label || h)))));
  const body = el('tbody');
  if (!rows.length) {
    body.append(el('tr', {}, el('td', { class: 'empty', colspan: headings.length }, emptyMessage)));
  } else {
    rows.forEach((r) => body.append(r));
  }
  t.append(body);
  wrap.append(t);
  return wrap;
}

function pairs(entries) {
  const dl = el('dl', { class: 'kv' });
  entries.forEach((entry) => {
    if (!entry) return;
    const [label, value, strong] = entry;
    dl.append(el('dt', {}, label), el('dd', { class: strong ? 'section-title' : null }, value));
  });
  return dl;
}

// ------------------------------------------------------------------ charts

const SERIES_ONE = '#6c5ce7';
const SERIES_TWO = '#0e9fc4';

/**
 * A line chart, drawn as plain SVG.
 *
 * The desktop client has JavaFX's own chart; this is the same picture by hand,
 * which keeps the client free of any library to download.
 */
function lineChart(options) {
  const width = 460;
  const height = 230;
  const left = 52;
  const bottom = 30;
  const top = 10;
  const right = 10;

  const xMax = Math.max(1, options.xMax);
  const yMin = options.yMin;
  const yMax = options.yMax > yMin ? options.yMax : yMin + 1;

  const plotWidth = width - left - right;
  const plotHeight = height - top - bottom;
  const xAt = (x) => left + (x / xMax) * plotWidth;
  const yAt = (y) => top + plotHeight - ((y - yMin) / (yMax - yMin)) * plotHeight;

  const parts = [
    svg('rect', { x: left, y: top, width: plotWidth, height: plotHeight, class: 'plot' }),
  ];

  // Four guides, labelled, so a value can be read off the picture.
  for (let i = 0; i <= 4; i += 1) {
    const value = yMin + ((yMax - yMin) * i) / 4;
    const y = yAt(value);
    parts.push(svg('line', { x1: left, y1: y, x2: left + plotWidth, y2: y, class: 'grid' }));
    parts.push(svg('text', { x: left - 6, y: y + 3.5, class: 'tick end' }, options.yTick(value)));
  }

  // Whole numbers only along the bottom: these are counts, and a tick reading
  // 2.5 trades would be nonsense.
  const step = Math.max(1, Math.ceil(xMax / 6));
  for (let x = 0; x <= xMax; x += step) {
    parts.push(svg('text', { x: xAt(x), y: height - bottom + 16, class: 'tick middle' }, String(x)));
  }

  options.series.forEach((series) => {
    const points = series.points.map((p) => xAt(p.x) + ',' + yAt(p.y)).join(' ');
    parts.push(svg('polyline', {
      points, fill: 'none', stroke: series.colour, 'stroke-width': 2.5,
    }));
    // With a handful of readings the points matter more than the line, and a
    // single reading draws no line at all.
    if (series.points.length <= 25) {
      series.points.forEach((p) => {
        parts.push(svg('circle', { cx: xAt(p.x), cy: yAt(p.y), r: 3, fill: series.colour }));
      });
    }
  });

  parts.push(svg('text', { x: left + plotWidth / 2, y: height - 2, class: 'axis middle' },
    options.xLabel));
  parts.push(svg('text', {
    x: 12, y: top + plotHeight / 2, class: 'axis middle',
    transform: 'rotate(-90 12 ' + (top + plotHeight / 2) + ')',
  }, options.yLabel));

  const holder = el('div', { class: 'chart' });
  holder.append(svg('svg', {
    viewBox: '0 0 ' + width + ' ' + height, role: 'img',
    'aria-label': options.yLabel + ' against ' + options.xLabel,
  }, parts));

  if (options.series.length > 1) {
    holder.append(el('div', { class: 'legend' }, options.series.map((s) =>
      el('span', { class: 'key' }, [
        el('span', { class: 'swatch', style: 'background:' + s.colour }), s.name,
      ]))));
  }
  return holder;
}

/**
 * The price of both options over the event's life.
 *
 * The axis runs the whole range a price can take rather than whatever the
 * readings happen to span, so the two options read as shares of one whole.
 */
function priceChart(history, option1Name, option2Name, maxPrice) {
  return lineChart({
    xLabel: 'Trades made',
    yLabel: 'Price',
    xMax: history.length - 1,
    yMin: 0,
    yMax: maxPrice,
    yTick: (v) => fmt.money(v),
    series: [
      { name: option1Name,
        colour: SERIES_ONE,
        points: history.map((p) => ({ x: p.index, y: p.option1Price })) },
      { name: option2Name,
        colour: SERIES_TWO,
        points: history.map((p) => ({ x: p.index, y: p.option2Price })) },
    ],
  });
}

// ----------------------------------------------------------------- dialog

function dialog(title, message, fields, buttons) {
  const box = $('dialog');
  $('dialogTitle').textContent = title;
  const messageNode = $('dialogMessage');
  messageNode.textContent = message || '';
  messageNode.hidden = !message;
  fill($('dialogFields'), fields || []);
  const foot = $('dialogFoot');
  clear(foot);

  return new Promise((resolve) => {
    let answered = false;
    const finish = (value) => {
      if (answered) return;
      answered = true;
      resolve(value);
    };
    (buttons || [{ label: 'OK', value: true }]).forEach((b) => {
      foot.append(el('button', {
        class: 'action' + (b.quiet ? ' quiet' : ''),
        type: 'button',
        onclick: () => { box.close(); finish(b.value); },
      }, b.label));
    });
    // Escape closes the dialog without pressing anything; without noticing that,
    // the promise would never settle and the action would hang for ever.
    box.addEventListener('close', () => finish(null), { once: true });
    box.showModal();
  });
}

const say = (title, message) => dialog(title, message, [], [{ label: 'OK', value: true }]);

const cancelAnd = (label) => [
  { label: 'Cancel', value: false, quiet: true }, { label, value: true },
];

function fieldRow(labelText, input) {
  return el('div', { class: 'row', style: 'margin-top:10px' }, [
    el('label', { class: 'field-label' }, labelText), input,
  ]);
}

/** Runs an action, reports a refusal the way the desktop client does, refreshes. */
async function run(work) {
  try {
    await work();
  } catch (error) {
    if (error instanceof Refused) {
      await say('Action refused', error.message);
    } else {
      await say('Something went wrong', String(error && error.message ? error.message : error));
    }
  }
  await refresh();
}

// ------------------------------------------------------------------ login

function showScreen(which) {
  $('openingScreen').hidden = which !== 'opening';
  $('loginScreen').hidden = which !== 'login';
  $('marketScreen').hidden = which !== 'market';
}

function enterMarket(user) {
  state.me = user;
  $('userChip').textContent = user.name;
  $('userChip').title = 'You are acting as ' + user.name + '. Every action here is yours.';
  document.title = 'Guess Market - ' + user.name;
  showScreen('market');
  if (!state.timer) state.timer = setInterval(poll, PULL_MS);
}

function backToLogin(why) {
  if (state.timer) {
    clearInterval(state.timer);
    state.timer = null;
  }
  state.me = null;
  state.snapshot = null;
  state.selectedEventId = null;
  state.chatLines = [];
  state.prints = {};
  const message = $('loginMessage');
  message.textContent = why || '';
  message.hidden = !why;
  showScreen('login');
  $('loginName').focus();
}

/**
 * Works out whether we are already logged in before showing anything.
 *
 * A browser keeps its session cookie across a reload, and the server never
 * gives a name back once it is taken. So pressing F5 on a working market and
 * then logging in again would be told the name is in use - locking somebody out
 * of their own account until the server restarts. Asking the server first costs
 * one request and avoids that completely.
 */
async function openingMove() {
  showScreen('opening');
  try {
    const snapshot = await api.sync(null, 0);
    state.snapshot = snapshot;
    state.chatLines = snapshot.newChatMessages.slice();
    enterMarket(snapshot.user);
    renderAll();
  } catch (error) {
    backToLogin('');
  }
}

async function doLogin() {
  const field = $('loginName');
  const name = field.value.trim();
  const message = $('loginMessage');
  const button = $('loginButton');

  if (!name) {
    message.textContent = 'Enter a name first.';
    message.hidden = false;
    return;
  }

  button.disabled = true;
  field.disabled = true;
  button.textContent = 'Logging in...';
  message.hidden = true;

  try {
    enterMarket(await api.login(name));
    await refresh();
  } catch (error) {
    showScreen('login');
    message.textContent = error.message;
    message.hidden = false;
    field.select();
    field.focus();
  } finally {
    button.disabled = false;
    field.disabled = false;
    button.textContent = 'Log in';
  }
}

// ------------------------------------------------------------------- poll

async function poll() {
  if (!state.me) return;
  if (state.polling) {
    // Asked for while one was already out. Remembered rather than dropped, or
    // the screen would keep showing the market as it was before the action that
    // was just taken.
    state.refreshWanted = true;
    return;
  }
  state.polling = true;
  try {
    state.snapshot = await api.sync(state.selectedEventId, state.chatLines.length);
    if (state.offline) {
      state.offline = false;
      $('statusLine').textContent = '';
      $('statusLine').title = '';
    }
    renderAll();
  } catch (error) {
    if (error.type === 'NotLoggedIn') {
      backToLogin(error.message);
      return;
    }
    // Said once, quietly, in the header. A dialog for every failed poll would
    // bury the window within half a minute. A server that cannot be reached and
    // a server that answered "no" are different problems, so they read
    // differently rather than both looking like a dead network.
    state.offline = true;
    $('statusLine').textContent = error.type === 'Unreachable'
      ? 'Not connected to the server - retrying'
      : error.message || 'The server refused the request';
    $('statusLine').title = error.message;
  } finally {
    state.polling = false;
    if (state.refreshWanted) {
      state.refreshWanted = false;
      poll();
    }
  }
}

const refresh = () => poll();

// ----------------------------------------------------------------- render

function renderAll() {
  const snap = state.snapshot;
  if (!snap) return;

  const me = snap.user;
  $('balanceChip').textContent = 'Balance: ' + fmt.money(me.balance);
  $('ownBalance').textContent = 'Balance: ' + fmt.money(me.balance);
  $('blockedChip').hidden = !me.blocked;

  renderEventsTable();
  renderEventDetail();
  renderMovements();
  renderBalanceChart();
  renderUsers();
  renderMyEvents();
  renderActions();
  renderInvolvement();
  renderChat();
}

function filtered(events) {
  const f = state.filters;
  return events.filter((e) =>
    (f.orderBook === null || e.orderBook === f.orderBook) &&
    (f.status === null || e.statusLabel === f.status) &&
    (f.commission === null || e.commissionTypeLabel === f.commission));
}

function selectedEvent() {
  const snap = state.snapshot;
  if (!snap) return null;
  return snap.events.find((e) => e.id === state.selectedEventId) || null;
}

function selectEvent(id) {
  state.selectedEventId = id;
  renderEventsTable();
  renderMyEvents();
  renderEventDetail();
  renderActions();
  renderInvolvement();
  refresh();
}

function eventRow(e, columns) {
  const row = el('tr', { class: 'selectable' + (e.id === state.selectedEventId ? ' selected' : '') },
    columns.map((c) => el('td', { class: c.num ? 'num' : null }, c.value)));
  row.addEventListener('click', () => selectEvent(e.id));
  return row;
}

const NOTHING_YET = 'No events yet. Create one from the Account tab, or upload a file of '
  + 'them from the desktop client.';
const NOTHING_YET_HERE = 'No events yet. Create one with the button above, or upload a file '
  + 'of them from the desktop client.';

/**
 * The six columns of the events table, each knowing how to show itself and what
 * to sort on.
 *
 * The two are deliberately separate. A money column sorts on the number behind
 * it, not on the text, or $9.00 would come after $10.00 the way words do.
 */
const EVENT_COLUMNS = [
  { label: 'Event', show: (e) => e.name, by: (e) => e.name },
  { label: 'Status', show: (e) => e.statusLabel, by: (e) => e.statusLabel },
  { label: 'Method', show: (e) => e.methodLabel, by: (e) => e.methodLabel },
  { label: 'Commission', by: (e) => e.commissionPercent,
    show: (e) => fmt.percent(e.commissionPercent) + ' ' + e.commissionTypeLabel },
  { label: 'Event account', num: true,
    show: (e) => fmt.money(e.accountBalance), by: (e) => e.accountBalance },
  { label: 'Market maker', show: (e) => e.marketMakerName, by: (e) => e.marketMakerName },
];

function inSortOrder(events) {
  const column = EVENT_COLUMNS[state.sort.column];
  if (!column) return events;
  // A copy, so the snapshot stays as the server sent it.
  return events.slice().sort((a, b) => {
    const x = column.by(a);
    const y = column.by(b);
    const order = typeof x === 'number' && typeof y === 'number'
      ? x - y
      : String(x).localeCompare(String(y));
    return state.sort.descending ? -order : order;
  });
}

function buildEventsHead() {
  const row = $('eventsHead');
  clear(row);
  EVENT_COLUMNS.forEach((column, index) => {
    const chosen = state.sort.column === index;
    const arrow = chosen ? (state.sort.descending ? ' ▾' : ' ▴') : '';
    const th = el('th', {
      class: 'sortable' + (column.num ? ' num' : ''),
      'aria-sort': chosen ? (state.sort.descending ? 'descending' : 'ascending') : 'none',
      title: 'Sort by ' + column.label.toLowerCase(),
    }, column.label + arrow);
    th.addEventListener('click', () => {
      // Clicking the column it is already sorted by turns the order round.
      if (chosen) state.sort.descending = !state.sort.descending;
      else state.sort = { column: index, descending: false };
      buildEventsHead();
      renderEventsTable();
    });
    row.append(th);
  });
}

function renderEventsTable() {
  const snap = state.snapshot;
  const shown = inSortOrder(filtered(snap.events));
  if (!changed('eventsTable', [shown, state.selectedEventId, snap.totalEventCount])) return;

  const body = $('eventsBody');
  clear(body);
  if (!shown.length) {
    body.append(el('tr', {}, el('td', { class: 'empty', colspan: EVENT_COLUMNS.length },
      snap.totalEventCount === 0 ? NOTHING_YET : 'No events match the current filters.')));
    return;
  }
  shown.forEach((e) => body.append(eventRow(e,
    EVENT_COLUMNS.map((column) => ({ value: column.show(e), num: column.num })))));
}

function statsList(stats) {
  return pairs([
    ['Last trade', fmt.price(stats.lastTradePrice)],
    ['Best bid', fmt.price(stats.bestBid)],
    ['Best ask', fmt.price(stats.bestAsk)],
    ['Mid price', fmt.price(stats.midPrice)],
    ['Spread', fmt.price(stats.spread)],
  ]);
}

function ordersTable(orders, emptyMessage) {
  return table(
    [{ label: 'User' }, { label: 'Quantity', num: true }, { label: 'Price', num: true }],
    orders.map((o) => el('tr', {}, [
      el('td', {}, o.userName),
      el('td', { class: 'num' }, fmt.count(o.quantity)),
      el('td', { class: 'num' }, fmt.money(o.price)),
    ])),
    emptyMessage);
}

function bookPane(side) {
  return el('div', { class: 'book' }, [
    el('h3', { class: 'section-title' }, side.optionName),
    el('p', { class: 'hint' }, 'Shares outstanding: ' + fmt.count(side.sharesOutstanding)),
    statsList(side.stats),
    el('p', { class: 'hint' }, 'Bids (buy)'),
    ordersTable(side.bids, 'No bids.'),
    el('p', { class: 'hint', style: 'margin-top:8px' }, 'Asks (sell)'),
    ordersTable(side.asks, 'No asks.'),
  ]);
}

function tradesTable(trades) {
  return table(
    [{ label: 'User' }, { label: 'Option' }, { label: 'Quantity', num: true },
     { label: 'Price paid', num: true }, { label: 'Commission', num: true }],
    trades.map((t) => el('tr', {}, [
      el('td', {}, t.userName),
      el('td', {}, t.optionName),
      el('td', { class: 'num' }, fmt.count(t.quantity)),
      el('td', { class: 'num' }, fmt.money(t.sharesCost)),
      el('td', { class: 'num' }, fmt.money(t.commissionPaid)),
    ])),
    'No trades in this event yet.');
}

/**
 * The whole of what is known about one event. The same picture is used by the
 * Events screen and, folded away, by the Account screen, so the two can never
 * drift into telling different stories.
 */
function buildEventDetail(selected) {
  const snap = state.snapshot;
  const book = snap.orderBookState;
  const lmsr = snap.lmsrState;

  if (book && book.event.id === selected.id) {
    const hasHistory = book.priceHistory.length > 1;
    return [
      el('h3', { class: 'section-title' }, book.event.name + '  -  Order Book'),
      book.event.description ? el('p', {}, book.event.description) : null,
      el('p', { class: 'hint' },
        'Base value: ' + fmt.money(book.baseValue) +
        '   |   Minting ' + (book.allowMint ? 'allowed' : 'not allowed')),
      el('p', {}, 'Event account: ' + fmt.money(book.accountBalance)),
      el('p', {}, 'Commission collected: ' + fmt.money(book.totalCommissionCollected)),
      book.winningOptionName
        ? el('h3', { class: 'section-title' }, 'Closed - winning option: ' + book.winningOptionName)
        : null,
      el('div', { class: 'books' }, [bookPane(book.option1Book), bookPane(book.option2Book)]),
      el('h3', { class: 'section-title', style: 'margin-top:10px' }, 'Participants'),
      table(
        [{ label: 'User' },
         { label: book.option1Book.optionName + ' qty', num: true },
         { label: book.option1Book.optionName + ' value', num: true },
         { label: book.option2Book.optionName + ' qty', num: true },
         { label: book.option2Book.optionName + ' value', num: true },
         { label: 'Open orders' }],
        book.participants.map((p) => el('tr', {}, [
          el('td', {}, p.userName),
          el('td', { class: 'num' }, fmt.count(p.option1Quantity)),
          el('td', { class: 'num' }, fmt.money(p.option1Value)),
          el('td', { class: 'num' }, fmt.count(p.option2Quantity)),
          el('td', { class: 'num' }, fmt.money(p.option2Value)),
          el('td', {}, fmt.yesNo(p.hasRestingOrders)),
        ])),
        'Nobody has taken part in this event yet.'),
      hasHistory
        ? el('h3', { class: 'section-title', style: 'margin-top:10px' }, 'Price history') : null,
      hasHistory
        ? priceChart(book.priceHistory, book.option1Book.optionName,
            book.option2Book.optionName, book.baseValue) : null,
    ];
  }

  if (lmsr && lmsr.event.id === selected.id) {
    const hasHistory = lmsr.priceHistory.length > 1;
    const optionRow = (s) => el('tr', {}, [
      el('td', {}, s.name),
      el('td', { class: 'num' }, fmt.money(s.price)),
      el('td', { class: 'num' }, fmt.count(s.sharesOutstanding)),
    ]);
    return [
      el('h3', { class: 'section-title' }, lmsr.event.name + '  -  LMSR'),
      lmsr.event.description ? el('p', {}, lmsr.event.description) : null,
      table([{ label: 'Option' }, { label: 'Price', num: true },
             { label: 'Shares bought', num: true }],
        [optionRow(lmsr.option1State), optionRow(lmsr.option2State)], ''),
      el('p', { style: 'margin-top:8px' }, 'Event account: ' + fmt.money(lmsr.accountBalance)),
      el('p', {}, 'Commission collected: ' + fmt.money(lmsr.totalCommissionCollected)),
      lmsr.winningOptionName
        ? el('h3', { class: 'section-title' }, 'Closed - winning option: ' + lmsr.winningOptionName)
        : null,
      el('h3', { class: 'section-title', style: 'margin-top:10px' }, 'Trade history (newest first)'),
      tradesTable(lmsr.tradesNewestFirst),
      hasHistory
        ? el('h3', { class: 'section-title', style: 'margin-top:10px' }, 'Price history') : null,
      hasHistory
        ? priceChart(lmsr.priceHistory, lmsr.option1State.name,
            lmsr.option2State.name, 1.0) : null,
    ];
  }

  // The poll that is in flight was sent before the click, so its detail is
  // still the previous event's.
  return [el('p', { class: 'hint' }, "Loading '" + selected.name + "'...")];
}

/** What the detail of the selected event is built from, for the change check. */
function detailPrint(selected) {
  const snap = state.snapshot;
  if (!selected) return [null, snap.totalEventCount];
  return [selected.id, snap.lmsrState, snap.orderBookState];
}

function renderEventDetail() {
  const snap = state.snapshot;
  const selected = selectedEvent();
  $('eventsHint').hidden = !selected;

  if (!changed('eventDetail', detailPrint(selected))) return;
  const holder = $('eventDetail');

  if (!selected) {
    fill(holder, el('p', { class: 'hint' },
      snap.totalEventCount === 0 ? NOTHING_YET : 'Select an event to see its details.'));
    return;
  }
  fill(holder, buildEventDetail(selected));
}

// -------------------------------------------------------------- account

function renderMovements() {
  const rows = state.snapshot.movements;
  if (!changed('movements', rows)) return;

  const body = $('movementsBody');
  clear(body);
  if (!rows.length) {
    body.append(el('tr', {}, el('td', { class: 'empty', colspan: 5 },
      'Nothing has moved in this account yet.')));
    return;
  }
  rows.forEach((m) => body.append(el('tr', {}, [
    el('td', { class: 'num' }, fmt.count(m.index)),
    el('td', {}, m.reason),
    el('td', {}, text(m.eventName)),
    el('td', { class: Math.abs(m.amount) < 0.005 ? null
      : m.amount < 0 ? 'money neg' : 'money pos' }, fmt.signed(m.amount)),
    el('td', { class: 'num' }, fmt.money(m.balanceAfter)),
  ])));
}

function renderBalanceChart() {
  // Only worth building while it is open, and only worth rebuilding when the
  // balance has actually moved.
  if (!$('chartPanel').open) return;
  const history = state.snapshot.balanceHistory;
  if (!changed('balanceChart', history)) return;

  const holder = $('balanceChart');
  if (history.length < 2) {
    fill(holder, el('p', { class: 'hint' },
      'This account has not moved yet, so there is nothing to plot.'));
    return;
  }
  // The axis is not forced to include zero: once an account is blocked its
  // balance is negative, and a chart that always started at zero would squash
  // the part somebody wants to look at.
  const low = Math.min(...history);
  const high = Math.max(...history);
  const pad = Math.max(1, (high - low) * 0.1);
  fill(holder, lineChart({
    xLabel: 'Money movements',
    yLabel: 'Balance',
    xMax: history.length - 1,
    yMin: low - pad,
    yMax: high + pad,
    yTick: (v) => fmt.money(v),
    series: [{ name: 'Balance',
      colour: SERIES_ONE,
      points: history.map((b, i) => ({ x: i, y: b })) }],
  }));
}

function renderUsers() {
  const snap = state.snapshot;
  const others = snap.users.filter((u) => u.name !== snap.user.name);
  if (!changed('users', others)) return;

  const body = $('usersBody');
  clear(body);
  if (!others.length) {
    body.append(el('tr', {}, el('td', { class: 'empty', colspan: 3 }, 'Nobody else is logged in.')));
    return;
  }
  others.forEach((u) => body.append(el('tr', {}, [
    el('td', {}, u.name),
    el('td', { class: 'num' }, fmt.money(u.balance)),
    el('td', {}, fmt.yesNo(u.marketMaker)),
  ])));
}

function roleIn(e) {
  const snap = state.snapshot;
  if (e.marketMakerName === snap.user.name) return 'market maker';
  return snap.participatingEventIds.includes(e.id) ? 'taking part' : 'not involved yet';
}

function renderMyEvents() {
  const snap = state.snapshot;
  const rows = snap.events.map((e) => [e.id, e.name, e.statusLabel, e.methodLabel, roleIn(e)]);
  if (!changed('myEvents', [rows, state.selectedEventId])) return;

  const body = $('myEventsBody');
  clear(body);
  if (!snap.events.length) {
    body.append(el('tr', {}, el('td', { class: 'empty', colspan: 4 }, NOTHING_YET_HERE)));
    return;
  }
  snap.events.forEach((e) => body.append(eventRow(e, [
    { value: e.name }, { value: e.statusLabel }, { value: e.methodLabel }, { value: roleIn(e) },
  ])));
}

const BLOCKED_REASON = 'This account is blocked and cannot act.';

function renderActions() {
  const snap = state.snapshot;
  const event = selectedEvent();
  const me = snap.user;

  $('createEventButton').disabled = me.blocked;
  $('createEventButton').title = me.blocked
    ? 'This account is blocked and cannot create events.'
    : 'Create a new event with you as its market maker.';
  // Loading funds is the cure for a block, so it stays available.
  $('loadFundsButton').title = me.blocked
    ? 'Load funds to cover the account and lift the block.'
    : 'Put money into your own account.';

  if (!changed('actions', [event, me.balance, me.blocked, me.name])) return;
  const bar = $('actionBar');
  clear(bar);
  if (!event) return;

  const isMm = event.marketMakerName === me.name;
  const notStarted = event.statusLabel === 'Not started';
  const active = event.statusLabel === 'Active';

  if (event.statusLabel === 'Closed') {
    bar.append(el('span', { class: 'hint' },
      'This event is closed. Its final result is shown below.'));
    return;
  }

  // All three buttons are always here. One that cannot be used right now is
  // disabled and says why, rather than vanishing and leaving somebody to guess.
  const opening = event.requiredOpeningFunds;
  const canAfford = me.balance + 1e-9 >= opening;

  bar.append(el('button', {
    class: 'action',
    title: me.blocked ? BLOCKED_REASON
      : !isMm ? 'Only ' + event.marketMakerName + ' can open this event.'
      : !notStarted ? 'This event has already been opened.'
      : !canAfford ? 'Opening costs ' + fmt.money(opening) + ' but the account holds '
          + fmt.money(me.balance) + '. Load funds first.'
      : 'Pay ' + fmt.money(opening) + ' to start trading in this event.',
    disabled: me.blocked || !isMm || !notStarted || !canAfford,
    onclick: () => confirmOpen(event, opening),
  }, 'Open event'));

  bar.append(el('button', {
    class: 'action quiet',
    title: me.blocked ? BLOCKED_REASON
      : !isMm ? 'Only ' + event.marketMakerName + ' can close this event.'
      : !active ? 'The event has not been opened yet.'
      : 'Decide the winning option and pay the winners.',
    disabled: me.blocked || !isMm || !active,
    onclick: () => askClose(event),
  }, 'Close event...'));

  // Open to anybody but a blocked account: a market maker may trade in an event
  // of their own.
  bar.append(el('button', {
    class: 'action quiet',
    title: me.blocked ? BLOCKED_REASON
      : !active ? 'Trading opens once ' + event.marketMakerName + ' starts the event.'
      : 'Take part in this event.',
    disabled: me.blocked || !active,
    onclick: () => (event.orderBook ? askOrder(event) : askBuy(event)),
  }, event.orderBook ? 'Place order...' : 'Buy shares...'));
}

function renderInvolvement() {
  const snap = state.snapshot;
  const event = selectedEvent();
  const inv = snap.involvement;
  const matching = inv && event && inv.eventId === event.id;

  $('fullDetailsPanel').hidden = !event;
  if (event && changed('fullDetails', detailPrint(event))) {
    fill($('involvementDetail'), buildEventDetail(event));
  }

  if (!changed('involvement', [event && event.id, matching ? inv : null])) return;
  const holder = $('involvement');

  if (!event) {
    fill(holder, el('p', { class: 'hint' }, 'Select one of the events above to take part in it.'));
    return;
  }
  if (!matching) {
    fill(holder, el('p', { class: 'hint' }, "Loading '" + event.name + "'..."));
    return;
  }

  const parts = [el('h3', { class: 'section-title' },
    "Your involvement in '" + inv.eventName + "'" +
    (inv.marketMaker ? '  (you are the market maker)' : ''))];

  const totals = (quantityHeading) => table(
    [{ label: 'Option' }, { label: quantityHeading, num: true },
     { label: 'Paid for them', num: true }],
    [el('tr', {}, [el('td', {}, inv.option1Name),
                   el('td', { class: 'num' }, fmt.count(inv.option1Quantity)),
                   el('td', { class: 'num' }, fmt.money(inv.option1Paid))]),
     el('tr', {}, [el('td', {}, inv.option2Name),
                   el('td', { class: 'num' }, fmt.count(inv.option2Quantity)),
                   el('td', { class: 'num' }, fmt.money(inv.option2Paid))])], '');

  const closed = inv.winningOptionName !== null && inv.winningOptionName !== undefined;

  if (inv.orderBook) {
    parts.push(totals('Shares held'));
  } else {
    parts.push(el('p', { class: 'hint' }, 'Your trades in this event:'));
    parts.push(tradesTable(inv.tradesNewestFirst));
    // Once it is over, the running total per option says more than the list.
    if (closed) parts.push(totals('Shares bought'));
  }

  parts.push(el('p', {}, 'Commission you paid: ' + fmt.money(inv.commissionPaid)));

  if (inv.openOrderCount > 0) {
    parts.push(el('p', { class: 'banner' },
      fmt.plural(inv.openOrderCount, 'order') + ' of yours (' + inv.openOrderQuantity +
      ' shares) are still waiting in the book and have not traded yet.'));
  }

  if (closed) {
    parts.push(el('p', {}, 'Winning option: ' + inv.winningOptionName));
    if (inv.profitOrLoss === null || inv.profitOrLoss === undefined) {
      parts.push(el('p', { class: 'hint' },
        'You did not take part in this event, so closing it did not change your balance.'));
    } else {
      // Running an event and trading in it are two different pots of money, so
      // they are shown apart and then added up.
      const ran = inv.marketMakerPaid > 0 || inv.marketMakerReceived > 0;
      if (ran && inv.tradingResult !== null && inv.tradingResult !== undefined) {
        parts.push(pairs([
          ['From trading:', fmt.signed(inv.tradingResult)],
          inv.marketMakerPaid > 0
            ? ['Put in to run the event:', fmt.signed(-inv.marketMakerPaid)] : null,
          inv.marketMakerReceived > 0
            ? ['Taken back as market maker:', fmt.signed(inv.marketMakerReceived)] : null,
        ]));
      }
      parts.push(el('h3', { class: 'section-title' },
        'Your result from this event: ' + fmt.signed(inv.profitOrLoss)));
      const heldNothing = inv.option1Quantity === 0 && inv.option2Quantity === 0;
      parts.push(el('p', { class: 'hint' }, heldNothing && !ran
        ? 'You held no shares when the event closed, so nothing was paid out to you. '
          + 'Any order of yours that had not traded was cancelled.'
        : 'This is the total change to your balance from this event.'));
    }
  }

  fill(holder, parts);
}

// ------------------------------------------------------------------- chat

function renderChat() {
  const snap = state.snapshot;
  // A server restart leaves this page holding more lines than exist. Without
  // noticing that, the count sent on every poll would stay too high for ever
  // and no new line would ever arrive.
  if (snap.chatTotal < state.chatLines.length) state.chatLines = [];
  if (snap.newChatMessages.length) {
    state.chatLines = state.chatLines.concat(snap.newChatMessages);
  }

  const panel = $('chatPanel');
  $('chatSummary').textContent = !panel.open && snap.chatTotal > 0
    ? 'Chat (' + snap.chatTotal + ')'
    : 'Chat';

  if (!changed('chat', state.chatLines.length)) return;
  const lines = $('chatLines');
  clear(lines);
  if (!state.chatLines.length) {
    lines.append(el('p', { class: 'hint' }, 'Nothing said yet.'));
    return;
  }
  state.chatLines.forEach((m) => lines.append(
    el('p', { class: 'chat-line' }, '[' + m.time + ']  ' + m.userName + ':  ' + m.text)));
  lines.scrollTop = lines.scrollHeight;
}

async function sendChat() {
  const field = $('chatText');
  const said = field.value.trim();
  if (!said) return;
  field.value = '';
  try {
    await api.chat(said);
  } catch (error) {
    // Put it back, so a refusal does not cost somebody what they typed.
    field.value = said;
    await say('That was not sent', error.message);
  }
  await refresh();
}

// ---------------------------------------------------------------- actions

async function confirmOpen(event, opening) {
  const go = await dialog("Open '" + event.name + "'?",
    'You will pay ' + fmt.money(opening) +
    ' into the event account to start it. This cannot be undone.',
    [], cancelAnd('Open'));
  if (!go) return;
  await run(async () => {
    await api.openEvent(event.id);
    await say('Event opened', "'" + event.name + "' is now active and open for trading.");
  });
}

async function askClose(event) {
  const choice = el('select', {}, [
    el('option', { value: '0' }, event.option1Name),
    el('option', { value: '1' }, event.option2Name),
  ]);
  const go = await dialog('Close event', event.name,
    [fieldRow('Winning option:', choice)], cancelAnd('Close event'));
  if (!go) return;

  const winner = choice.selectedIndex === 0 ? event.option1Name : event.option2Name;
  const sure = await dialog("Close '" + event.name + "'?",
    "'" + winner + "' will be declared the winner. This cannot be undone.",
    [], cancelAnd('Close event'));
  if (!sure) return;

  await run(async () => {
    const result = await api.closeEvent(event.id, Number(choice.value));
    let message = "'" + result.winningOptionName + "' won.\n\n" +
      'Winners paid: ' + result.winnersPaid + '\n' +
      'Total paid out: ' + fmt.money(result.totalPaidOut) + '\n';
    if (result.commissionCollected > 0) {
      message += 'Commission collected: ' + fmt.money(result.commissionCollected) + '\n';
    }
    if (result.returnedToMarketMaker > 0) {
      message += 'Returned to the market maker: ' + fmt.money(result.returnedToMarketMaker) + '\n';
    }
    if (result.cancelledOrders > 0) {
      message += result.cancelledOrders === 1
        ? '1 resting order was cancelled.'
        : result.cancelledOrders + ' resting orders were cancelled.';
    }
    await say('Event closed', message.trim());
  });
}

/**
 * Keeps a preview in step with the numbers being typed.
 *
 * The quote comes from the server, because the price comes out of a formula and
 * must never be a surprise after confirming. It is asked for a short moment
 * after typing stops rather than on every keystroke, and an answer to a question
 * that has since been replaced is thrown away - otherwise a slow reply can land
 * last and leave an older figure on the screen.
 */
function liveQuote(show) {
  let pending = null;
  let latest = 0;
  return () => {
    clearTimeout(pending);
    const mine = ++latest;
    pending = setTimeout(async () => {
      try {
        const answer = await show.quote();
        if (mine === latest) show.good(answer);
      } catch (error) {
        if (mine === latest) show.bad(error.message);
      }
    }, 200);
  };
}

/** A row of a preview whose value is replaced as the numbers change. */
function previewRow(label, strong) {
  return {
    label: el('dt', {}, label),
    value: el('dd', { class: strong ? 'section-title' : null }, '-'),
  };
}

function previewGrid(rows) {
  const dl = el('dl', { class: 'kv' });
  rows.forEach((r) => dl.append(r.label, r.value));
  return dl;
}

function warningChip() {
  const chip = el('p', { class: 'banner', hidden: true }, '');
  return {
    node: chip,
    set(message) {
      chip.textContent = message || '';
      // An empty chip would still paint its red background and read as a mark.
      chip.hidden = !message;
    },
  };
}

function wholeNumber(field) {
  const typed = field.value.trim();
  if (!/^-?\d+$/.test(typed)) {
    return { ok: false, message: "'" + typed + "' is not a whole number." };
  }
  return { ok: true, value: Number(typed) };
}

async function askBuy(event) {
  const option = el('select', {}, [
    el('option', { value: '0' }, event.option1Name),
    el('option', { value: '1' }, event.option2Name),
  ]);
  const quantity = el('input', { type: 'number', min: '1', step: '1', value: '10' });

  const cost = previewRow('Shares cost:');
  const commission = previewRow('Commission:');
  const total = previewRow('Total to pay:', true);
  const after = previewRow('Price afterwards:');
  const rows = [cost, commission, total, after];
  const warning = warningChip();

  const blank = (message) => {
    rows.forEach((r) => { r.value.textContent = '-'; });
    warning.set(message);
  };

  const update = liveQuote({
    quote: async () => {
      const typed = quantity.value.trim();
      // Asked for less than one share this would divide by zero on the server
      // and come back as a server error rather than an answer, so it is a
      // question not worth asking.
      if (!/^\d+$/.test(typed) || Number(typed) < 1) {
        return { refuse: typed === '' ? '' : 'Enter a whole number of 1 or more.' };
      }
      return { quote: await api.quoteLmsr(event.id, Number(option.value), Number(typed)) };
    },
    good: (answer) => {
      if (!answer.quote) {
        blank(answer.refuse);
        return;
      }
      const q = answer.quote;
      cost.value.textContent = fmt.money(q.sharesCost) +
        '   (' + fmt.money(q.averagePricePerShare) + ' per share)';
      commission.value.textContent = fmt.money(q.commission);
      after.value.textContent = fmt.money(q.priceAfterwards);
      if (!q.worthCharging) {
        // Below a cent the price has stopped being a real one, so the exact
        // figure is shown rather than a rounded $0.00.
        total.value.textContent = 'less than $0.01 (' + q.totalCost.toFixed(8) + ')';
        warning.set('This option has been pushed so low that the purchase would cost less '
          + 'than $0.01. Buy more of it to reach a real price.');
        return;
      }
      total.value.textContent = fmt.money(q.totalCost);
      warning.set(q.affordable
        ? ''
        : 'Not enough money: the balance is ' + fmt.money(q.buyerBalance) + '.');
    },
    bad: (message) => blank(message),
  });

  option.addEventListener('change', update);
  quantity.addEventListener('input', update);
  update();

  const go = await dialog('Buy shares', event.name,
    [fieldRow('Option:', option), fieldRow('Quantity:', quantity),
     previewGrid(rows), warning.node],
    cancelAnd('Buy'));
  if (!go) return;

  const asked = wholeNumber(quantity);
  if (!asked.ok) return say('Invalid quantity', asked.message);
  if (asked.value < 1) return say('Invalid quantity', 'The quantity must be at least 1.');

  return run(async () => {
    const result = await api.buyLmsr(event.id, Number(option.value), asked.value);
    await say('Purchase complete',
      'Shares: ' + fmt.money(result.sharesCost) + '\n' +
      'Commission: ' + fmt.money(result.commissionPaid) + '\n' +
      'Total paid: ' + fmt.money(result.totalPaid) + '\n' +
      'New balance: ' + fmt.money(result.newBalance) +
      (result.blockedNow
        ? '\n\nThis purchase took the account below zero. ' + state.snapshot.user.name
          + ' is now blocked from further actions.'
        : ''));
  });
}

/**
 * What is actually on offer on the other side of the book.
 *
 * An order book needs somebody else to deal with, so the form says who is there
 * rather than only whether the order would trade.
 */
function marketNote(q) {
  const nobody = q.bestOpposingPrice === null || q.bestOpposingPrice === undefined
    || q.availableNow <= 0;
  if (nobody) {
    if (!q.buying) {
      return 'Nobody is bidding for this option right now, so your order will wait in the '
        + 'book until somebody does. No money moves until it trades.';
    }
    return q.mintingAllowed
      ? 'Nobody is selling this option right now, and nobody is bidding enough on the other '
        + 'one to create new shares. Your order will wait in the book until somebody does. '
        + 'No money moves until it trades.'
      : 'Nobody is selling this option right now. This event does not create new shares, so '
        + 'the only way in is from somebody who already holds them and offers them for sale. '
        + 'Your order will wait in the book until one does. No money moves until it trades.';
  }

  const best = fmt.money(q.bestOpposingPrice);
  const offer = 'You can ' + (q.buying ? 'buy ' : 'sell ')
    + fmt.plural(q.availableNow, 'share') + ' now at ' + best + ' each.';

  if (q.wouldTradeNow) {
    return offer + ' Your order trades immediately at that price' + (q.buying
      ? ', so the amounts above are the most you could pay.'
      : ', so the amounts above are the least you could receive.');
  }
  return offer + (q.buying
    ? ' Your price is below that, so your order waits in the book until somebody accepts it. '
      + 'Raise it to ' + best + ' to trade at once.'
    : ' Your price is above that, so your order waits in the book until somebody accepts it. '
      + 'Lower it to ' + best + ' to trade at once.');
}

async function askOrder(event) {
  const option = el('select', {}, [
    el('option', { value: '0' }, event.option1Name),
    el('option', { value: '1' }, event.option2Name),
  ]);
  const side = el('select', {}, [
    el('option', { value: 'BUY' }, 'Buy'),
    el('option', { value: 'SELL' }, 'Sell'),
  ]);
  const quantity = el('input', { type: 'number', min: '1', step: '1', value: '10' });
  const price = el('input', { type: 'number', min: '0.01', step: '0.01', value: '0.50' });
  const allowed = el('p', { class: 'hint' }, '');

  const value = previewRow('Order value:');
  const commission = previewRow('Commission:');
  const total = previewRow('Total to pay:', true);
  const holding = previewRow('Your balance:');
  const rows = [value, commission, total, holding];
  const note = el('p', { class: 'hint' }, '');
  const warning = warningChip();

  const blank = (message) => {
    rows.forEach((r) => { r.value.textContent = '-'; });
    note.textContent = '';
    warning.set(message);
  };

  const update = liveQuote({
    quote: async () => {
      const askedQuantity = quantity.value.trim();
      const askedPrice = price.value.trim();
      if (!/^\d+$/.test(askedQuantity) || Number(askedQuantity) < 1) {
        return { refuse: askedQuantity === '' ? '' : 'Enter a whole number of 1 or more.' };
      }
      if (!/^\d*\.?\d+$/.test(askedPrice)) {
        return { refuse: askedPrice === '' ? '' : "'" + askedPrice + "' is not a valid price." };
      }
      return { quote: await api.quoteOrder(event.id, Number(option.value), side.value,
        Number(askedPrice), Number(askedQuantity)) };
    },
    good: (answer) => {
      if (!answer.quote) {
        blank(answer.refuse);
        return;
      }
      const q = answer.quote;
      allowed.textContent = 'Allowed: ' + fmt.money(0.01) + ' to ' + fmt.money(q.maxPrice)
        + ', in whole cents.';
      value.value.textContent = fmt.money(q.orderValue);
      commission.value.textContent = q.buying ? fmt.money(q.commission) : 'none when selling';
      total.label.textContent = q.buying ? 'Total to pay:' : 'You would receive:';
      total.value.textContent = fmt.money(q.totalCost);
      holding.label.textContent = q.buying ? 'Your balance:' : 'Shares you hold:';
      holding.value.textContent = q.buying ? fmt.money(q.balance) : fmt.count(q.sharesHeld);
      note.textContent = marketNote(q);

      let said = '';
      if (!q.priceValid) {
        said += 'Price must be in whole cents between 0.01 and ' + fmt.money(q.maxPrice) + '. ';
      }
      if (q.buying && !q.affordable) {
        said += 'Not enough money: this needs ' + fmt.money(q.totalCost) + ' but the balance is '
          + fmt.money(q.balance) + '. ';
      }
      if (!q.buying && !q.enoughShares) {
        said += 'Not enough shares: only ' + q.sharesHeld + ' held.';
      }
      warning.set(said.trim());
    },
    bad: (message) => blank(message),
  });

  [option, side].forEach((c) => c.addEventListener('change', update));
  [quantity, price].forEach((c) => c.addEventListener('input', update));
  update();

  const go = await dialog('Place an order', event.name,
    [fieldRow('Option:', option), fieldRow('Side:', side), fieldRow('Quantity:', quantity),
     fieldRow('Price per share:', price), allowed, previewGrid(rows), note, warning.node],
    cancelAnd('Place order'));
  if (!go) return;

  const asked = wholeNumber(quantity);
  if (!asked.ok) return say('Invalid quantity', asked.message);
  if (asked.value < 1) return say('Invalid quantity', 'The quantity must be at least 1.');
  const askedPrice = price.value.trim();
  if (!/^\d*\.?\d+$/.test(askedPrice)) {
    return say('Invalid price',
      "'" + askedPrice + "' is not a valid price. Use a value such as 0.42.");
  }

  return run(async () => {
    const r = await api.placeOrder(event.id, Number(option.value), side.value,
      Number(askedPrice), asked.value);
    let message = '';
    if (!r.fills.length) {
      message += 'Nothing matched right now.\n';
    } else {
      message += 'Matched:\n';
      r.fills.forEach((f) => {
        // A mint has no counterparty: nobody sold anything, the shares are new.
        const who = f.counterpartyName ? ' with ' + f.counterpartyName : '';
        message += '  ' + f.quantity + ' @ ' + fmt.money(f.price)
          + '  (' + f.kindLabel + who + ')\n';
      });
    }
    if (r.mintedQuantity > 0) message += 'Minted ' + r.mintedQuantity + ' new share pairs.\n';
    if (r.restingQuantity > 0) message += r.restingQuantity + ' left resting in the book.\n';
    message += '\nPaid: ' + fmt.money(r.cashPaid) +
      '\nReceived: ' + fmt.money(r.cashReceived) +
      '\nCommission: ' + fmt.money(r.commissionPaid) +
      '\nNew balance: ' + fmt.money(r.newBalance);
    if (r.blockedNow) {
      message += '\n\nThis order took the account below zero. ' + state.snapshot.user.name
        + ' is now blocked from further actions.';
    }
    await say('Order processed', message);
  });
}

async function askLoadFunds() {
  const amount = el('input', { type: 'number', min: '0.01', step: '1', value: '100' });
  const go = await dialog('Load funds', 'How much would you like to load?',
    [fieldRow('Amount to put into your account:', amount)], cancelAnd('Load'));
  if (!go) return;

  const typed = amount.value.trim();
  if (typed === '' || Number.isNaN(Number(typed))) {
    return say('That is not an amount', "'" + typed + "' is not a number.");
  }
  return run(async () => {
    const after = await api.loadFunds(Number(typed));
    await say('Funds loaded', fmt.money(Number(typed)) +
      ' went into your account, which now holds ' + fmt.money(after.balance) + '.');
  });
}

async function askCreateEvent() {
  const name = el('input', { type: 'text', maxlength: '80' });
  const description = el('textarea', { rows: '2' });
  const commission = el('input', { type: 'number', min: '0', step: '1', value: '5' });
  const collected = el('select', {}, [
    el('option', { value: 'on-purchase' }, 'on-purchase'),
    el('option', { value: 'on-close' }, 'on-close'),
  ]);
  const option1 = el('input', { type: 'text', maxlength: '40', value: 'Yes' });
  const option2 = el('input', { type: 'text', maxlength: '40', value: 'No' });
  const method = el('select', {}, [
    el('option', { value: 'lmsr' }, 'LMSR'),
    el('option', { value: 'book' }, 'Order Book'),
  ]);
  const liquidity = el('input', { type: 'number', min: '1', step: '1', value: '100' });
  const baseValue = el('input', { type: 'number', min: '1', step: '1', value: '1' });
  const investment = el('input', { type: 'number', min: '0', step: '1', value: '100' });
  const allowMint = el('input', { type: 'checkbox', checked: true });

  const liquidityRow = fieldRow('Liquidity b:', liquidity);
  const baseValueRow = fieldRow('Base value d:', baseValue);
  const investmentRow = fieldRow('Initial investment:', investment);
  const mintRow = fieldRow('Allow minting:', allowMint);

  // The rows the other method does not use are taken out of the layout, not
  // merely emptied, or they would leave gaps where something used to be.
  const showMethod = () => {
    const book = method.value === 'book';
    liquidityRow.hidden = book;
    [baseValueRow, investmentRow, mintRow].forEach((r) => { r.hidden = !book; });
  };
  method.addEventListener('change', showMethod);
  showMethod();

  const go = await dialog('Create a new event',
    state.snapshot.user.name + ' will be the market maker of this event.',
    [fieldRow('Name:', name), fieldRow('Description:', description),
     fieldRow('Commission %:', commission), fieldRow('Collected:', collected),
     fieldRow('Option 1:', option1), fieldRow('Option 2:', option2),
     fieldRow('Method:', method), liquidityRow, baseValueRow, investmentRow, mintRow],
    cancelAnd('Create'));
  if (!go) return;

  const book = method.value === 'book';
  const numbers = [[commission, 'commission']];
  if (book) numbers.push([baseValue, 'base value'], [investment, 'initial investment']);
  else numbers.push([liquidity, 'liquidity b']);
  for (const [field, what] of numbers) {
    const read = wholeNumber(field);
    if (!read.ok) return say('Invalid ' + what, read.message);
  }

  return run(async () => {
    const created = await api.createEvent({
      name: name.value,
      description: description.value,
      commissionPercent: Number(commission.value),
      commissionTypeLabel: collected.value,
      option1Name: option1.value,
      option2Name: option2.value,
      orderBook: book,
      // The method that is not in use sends zeroes, the way the desktop dialog
      // does, so the server reads one consistent shape either way.
      b: book ? 0 : Number(liquidity.value),
      baseValue: book ? Number(baseValue.value) : 0,
      initialInvestment: book ? Number(investment.value) : 0,
      allowMint: book && allowMint.checked,
    });
    state.selectedEventId = created.id;
    await say('Event created', "'" + created.name + "' was created with you as its market maker. "
      + 'It still needs to be opened before trading.');
  });
}

// ------------------------------------------------------------------ setup

function buildFilters() {
  const groups = [
    ['methodFilter', 'orderBook', [['All', null], ['LMSR', false], ['Order Book', true]]],
    ['statusFilter', 'status', [['All', null], ['Not started', 'Not started'],
                                ['Active', 'Active'], ['Closed', 'Closed']]],
    ['commissionFilter', 'commission', [['All', null], ['on-purchase', 'on-purchase'],
                                        ['on-close', 'on-close']]],
  ];
  groups.forEach(([holderId, key, options]) => {
    const holder = $(holderId);
    options.forEach(([label, value], index) => {
      const button = el('button', {
        class: 'toggle', type: 'button', 'aria-pressed': index === 0 ? 'true' : 'false',
      }, label);
      button.addEventListener('click', () => {
        // One of each group is always chosen, so clicking the chosen one again
        // leaves it chosen rather than clearing the whole dimension.
        [...holder.children].forEach((b) => b.setAttribute('aria-pressed', 'false'));
        button.setAttribute('aria-pressed', 'true');
        state.filters[key] = value;
        renderEventsTable();
      });
      holder.append(button);
    });
  });
}

function showTab(which) {
  state.tab = which;
  $('tabEvents').setAttribute('aria-selected', String(which === 'events'));
  $('tabAccount').setAttribute('aria-selected', String(which === 'account'));
  $('eventsScreen').hidden = which !== 'events';
  $('accountScreen').hidden = which !== 'account';
}

document.addEventListener('DOMContentLoaded', () => {
  $('loginServer').textContent = 'Server: ' + location.origin + API;
  $('loginButton').addEventListener('click', doLogin);
  $('loginName').addEventListener('keydown', (e) => { if (e.key === 'Enter') doLogin(); });
  $('loadFundsButton').addEventListener('click', askLoadFunds);
  $('createEventButton').addEventListener('click', askCreateEvent);
  $('tabEvents').addEventListener('click', () => showTab('events'));
  $('tabAccount').addEventListener('click', () => showTab('account'));
  $('chatSend').addEventListener('click', sendChat);
  $('chatText').addEventListener('keydown', (e) => { if (e.key === 'Enter') sendChat(); });
  $('chatPanel').addEventListener('toggle', () => { if (state.snapshot) renderChat(); });
  $('chartPanel').addEventListener('toggle', () => {
    // Built only while open, so it has to be built afresh when it is opened.
    state.prints.balanceChart = null;
    if (state.snapshot) renderBalanceChart();
  });
  buildFilters();
  buildEventsHead();
  openingMove();
});
