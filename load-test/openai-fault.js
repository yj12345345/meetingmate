// OpenAI fault injection (LOCAL STACK ONLY).
//
// Open workload: AI recommendation requests keep ARRIVING at a fixed rate whether or
// not earlier ones finished (real users don't wait for each other). A second probe
// scenario measures collateral damage on non-AI endpoints (login, my page).
//
// Timeline (fixed, so runs are comparable):
//   0s   - 60s   normal       (mock answers in ~3s)
//   60s  - 180s  FAULT        (injected into the WireMock mock via its admin API)
//   180s - 300s  recovery     (fault removed)
//
//   FAULT=hang  ./load-test/run.sh openai-fault baseline-hang
//   FAULT=error | reset | slow | unavailable | none (host injects the fault, see db-fault.sh)
//
// The AI request timeout on the client is 60s = Nginx's default proxy_read_timeout,
// i.e. what a real browser behind our Nginx would experience.

import http from 'k6/http';
import { check, sleep, fail } from 'k6';
import { Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://backend:8080';
const MOCK_ADMIN = __ENV.MOCK_ADMIN || 'http://openai-mock:8080/__admin';
const FAULT = __ENV.FAULT || 'hang';
const AI_RATE = parseInt(__ENV.AI_RATE || '4', 10);
const RUN_ID = __ENV.RUN_ID || `${Date.now()}`;
const USERS = 40;
const PASSWORD = 'loadtest-pass-1';
const FAULT_ID = '7a1f0c9e-0000-4000-8000-00000000fa17';

const recommendSource = new Counter('recommend_source');

const FAULTS = {
  // Upstream accepts the request and never answers in a useful time.
  hang: { status: 200, fixedDelayMilliseconds: 300000, jsonBody: {} },
  // Upstream answers slowly but eventually (degraded, not dead).
  slow: { status: 200, fixedDelayMilliseconds: 20000, jsonBody: {} },
  // Upstream fails fast.
  error: { status: 500, jsonBody: { error: { message: 'injected 500' } } },
  unavailable: { status: 503, jsonBody: { error: { message: 'injected 503' } } },
  // TCP connection reset mid-request.
  reset: { fault: 'CONNECTION_RESET_BY_PEER' },
};

function phase(start) {
  const t = (Date.now() - start) / 1000;
  return t < 60 ? 'normal' : t < 180 ? 'fault' : 'recovery';
}

const NOOP = { http_req_duration: 'max>=0', http_req_failed: 'rate>=0', recommend_source: 'count>=0' };
const sub = (metric, name) => ['normal', 'fault', 'recovery']
  .reduce((acc, p) => Object.assign(acc, { [`${metric}{name:${name},phase:${p}}`]: [NOOP[metric]] }), {});

export const options = {
  setupTimeout: '2m',
  scenarios: {
    ai: {
      executor: 'constant-arrival-rate', exec: 'ai', rate: AI_RATE, timeUnit: '1s',
      duration: '300s', preAllocatedVUs: 50, maxVUs: 600,
    },
    probe: {
      executor: 'constant-arrival-rate', exec: 'probe', rate: 2, timeUnit: '1s',
      duration: '300s', preAllocatedVUs: 10, maxVUs: 200,
    },
    chaos: { executor: 'shared-iterations', exec: 'chaos', vus: 1, iterations: 1, maxDuration: '310s' },
  },
  // No-op thresholds force k6 to print per-phase sub-metrics in the summary.
  thresholds: Object.assign({},
    sub('http_req_duration', 'recommend'), sub('http_req_failed', 'recommend'),
    sub('http_req_duration', 'login'), sub('http_req_failed', 'login'),
    sub('http_req_duration', 'user_me'), sub('http_req_failed', 'user_me'),
    sub('recommend_source', 'recommend')),
  summaryTrendStats: ['med', 'p(95)', 'p(99)', 'max'],
};

function params(token, name, start, timeout) {
  return {
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      'X-Request-Id': `k6-${RUN_ID.slice(-6)}-${__VU}-${typeof __ITER === "undefined" ? "setup" : __ITER}-${name}`,
    },
    tags: { name, phase: start ? phase(start) : 'setup' },
    timeout,
  };
}

const email = (i) => `fault-${RUN_ID}-${i}@loadtest.local`;

export function setup() {
  // Make sure no fault is left over from an aborted run.
  http.del(`${MOCK_ADMIN}/mappings/${FAULT_ID}`);
  const tokens = [];
  for (let i = 0; i < USERS; i++) {
    http.post(`${BASE_URL}/api/auth/signup`, JSON.stringify({ email: email(i), password: PASSWORD, nickname: `f${i}` }),
      params(null, 'setup', 0, '30s'));
    const r = http.post(`${BASE_URL}/api/auth/login`, JSON.stringify({ email: email(i), password: PASSWORD }),
      params(null, 'setup', 0, '30s'));
    if (r.status !== 200) fail(`setup login ${r.status}`);
    tokens.push(r.json('data.accessToken'));
  }
  const start = Date.now();
  console.log(`[t0] ${new Date(start).toISOString()}`);
  return { tokens, start };
}

const AREAS = ['홍대', '강남역', '성수동', '잠실', '신촌', '연남동'];

export function ai(data) {
  const token = data.tokens[Math.floor(Math.random() * data.tokens.length)];
  const area = AREAS[Math.floor(Math.random() * AREAS.length)];
  const res = http.get(
    `${BASE_URL}/api/recommend?keyword=${encodeURIComponent(area)}&location=${encodeURIComponent(area)}&planHint=${encodeURIComponent('식사\n카페')}`,
    params(token, 'recommend', data.start, '60s'));
  const ph = phase(data.start);
  check(res, { 'recommend 2xx': (r) => r.status >= 200 && r.status < 300 });
  let source = 'error';
  if (res.status === 200) { try { source = res.json('data.source'); } catch (e) { source = 'unparseable'; } }
  else if (res.status === 0) source = 'client_timeout';
  recommendSource.add(1, { name: 'recommend', phase: ph, source });
}

export function probe(data) {
  if (__ITER % 2 === 0) {
    const r = http.post(`${BASE_URL}/api/auth/login`, JSON.stringify({ email: email(0), password: PASSWORD }),
      params(null, 'login', data.start, '30s'));
    check(r, { 'login 2xx': (x) => x.status === 200 });
  } else {
    const r = http.get(`${BASE_URL}/api/user/me`, params(data.tokens[0], 'user_me', data.start, '30s'));
    check(r, { 'user_me 2xx': (x) => x.status === 200 });
  }
}

export function chaos(data) {
  // FAULT=none: no OpenAI fault; used when another fault (e.g. MySQL stop via
  // load-test/db-fault.sh) is injected from the host on the same timeline.
  if (FAULT === 'none') return;
  const spec = FAULTS[FAULT];
  if (!spec) fail(`unknown FAULT=${FAULT}`);
  sleep(Math.max(0, 60 - (Date.now() - data.start) / 1000));
  const inject = http.post(`${MOCK_ADMIN}/mappings`, JSON.stringify({
    id: FAULT_ID, priority: 1,
    request: { method: 'POST', urlPath: '/v1/chat/completions' },
    response: Object.assign({ headers: { 'Content-Type': 'application/json' } }, spec),
  }), { headers: { 'Content-Type': 'application/json' } });
  console.log(`[chaos] t=${((Date.now() - data.start) / 1000).toFixed(0)}s inject ${FAULT} -> ${inject.status}`);
  sleep(Math.max(0, 180 - (Date.now() - data.start) / 1000));
  const restore = http.del(`${MOCK_ADMIN}/mappings/${FAULT_ID}`);
  console.log(`[chaos] t=${((Date.now() - data.start) / 1000).toFixed(0)}s restore -> ${restore.status}`);
}

export function teardown() {
  http.del(`${MOCK_ADMIN}/mappings/${FAULT_ID}`);
}
