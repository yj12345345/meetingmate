// MeetingMate real user flow (personal place-recommendation service).
//
// Mirrors the frontend routes  / -> /select -> /result -> /selected-route  (+ MyPage):
//   1. (30% of sessions) log in        POST /api/auth/login
//   2. enter conditions, get places    GET  /api/recommend            (OpenAI call)
//   3. read results / open Kakao Map   think time
//   4. pick 2-3 places, get a route    POST /api/recommend/selected-routes (OpenAI call)
//   5. (30%) open MyPage               GET  /api/user/me
//
// OAuth (Kakao/Google) cannot be scripted, so test accounts use the app's existing
// local-account endpoints. OpenAI is replaced by the local WireMock mock.
//
//   ./load-test/run.sh recommend-flow <label> [local|smoke|prod-baseline]

import http from 'k6/http';
import { check, sleep, fail } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://backend:8080';
const USERS = parseInt(__ENV.USERS || '60', 10);
const PROFILE = __ENV.PROFILE || 'local';
const RUN_ID = __ENV.RUN_ID || `${Date.now()}`;
const PASSWORD = 'loadtest-pass-1';

const recommendDuration = new Trend('recommend_duration', true);
const routeDuration = new Trend('selected_route_duration', true);
const flowErrors = new Rate('flow_errors');
const aiSource = new Counter('recommend_source');

const profiles = {
  smoke: { executor: 'constant-vus', vus: 2, duration: '40s' },
  local: {
    executor: 'ramping-vus',
    startVUs: 0,
    stages: [
      { duration: '30s', target: 20 },
      { duration: '1m', target: 60 },
      { duration: '2m', target: 60 },
      { duration: '30s', target: 0 },
    ],
    gracefulRampDown: '15s',
  },
  // Cost principle #4: max 30 VUs, total < 3 minutes.
  'prod-baseline': {
    executor: 'ramping-vus',
    startVUs: 0,
    stages: [
      { duration: '30s', target: 10 },
      { duration: '1m30s', target: 30 },
      { duration: '30s', target: 0 },
    ],
    gracefulRampDown: '10s',
  },
};

export const options = {
  setupTimeout: '3m',
  scenarios: { recommend_flow: profiles[PROFILE] },
  thresholds: {
    'http_req_failed{type:api}': ['rate<0.01'],
    'http_req_duration{type:api}': ['p(95)<500'],
    recommend_duration: ['p(95)<8000'],
    selected_route_duration: ['p(95)<8000'],
    flow_errors: ['rate<0.01'],
  },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

const AI_CALLS = new Set(['recommend', 'selected_routes']);

function params(token, name) {
  const iter = typeof __ITER === 'undefined' ? 'setup' : __ITER;
  return {
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      'X-Request-Id': `k6-${RUN_ID.slice(-6)}-${__VU}-${iter}-${name}`,
    },
    tags: { name, type: AI_CALLS.has(name) ? 'ai' : 'api' },
    timeout: '60s',
  };
}

function ok(res, name) {
  const passed = check(res, { [`${name} 2xx`]: (r) => r.status >= 200 && r.status < 300 });
  flowErrors.add(!passed);
  return passed;
}

function email(i) {
  return `lt-${RUN_ID}-${i}@loadtest.local`;
}

export function setup() {
  const tokens = [];
  for (let i = 0; i < USERS; i++) {
    http.post(`${BASE_URL}/api/auth/signup`,
      JSON.stringify({ email: email(i), password: PASSWORD, nickname: `lt${i}` }), params(null, 'setup_signup'));
    const res = http.post(`${BASE_URL}/api/auth/login`,
      JSON.stringify({ email: email(i), password: PASSWORD }), params(null, 'setup_login'));
    if (res.status !== 200) fail(`setup login failed: ${res.status}`);
    tokens.push(res.json('data.accessToken'));
  }
  return { tokens };
}

const AREAS = ['홍대', '강남역', '성수동', '잠실', '신촌', '연남동', '건대입구', '합정'];
const TYPES = ['친구 모임', '데이트', '회식', '스터디'];
const MOODS = ['조용한', '활기찬', '가성비', '감성적인'];
const PLANS = ['식사\n카페', '식사\n술집', '카페\n산책', '식사\n카페\n노래방'];

export default function (data) {
  const idx = (__VU - 1) % data.tokens.length;
  let token = data.tokens[idx];

  // 1. Session start: some users log in again, the rest still have a valid token.
  if (Math.random() < 0.3) {
    const res = http.post(`${BASE_URL}/api/auth/login`,
      JSON.stringify({ email: email(idx), password: PASSWORD }), params(null, 'login'));
    if (ok(res, 'login')) token = res.json('data.accessToken');
  }

  // 2. /select: user types conditions, then requests recommendations.
  sleep(2 + Math.random() * 3);
  const area = AREAS[Math.floor(Math.random() * AREAS.length)];
  const q = {
    keyword: area,
    location: area,
    meetingType: TYPES[__ITER % TYPES.length],
    mood: MOODS[Math.floor(Math.random() * MOODS.length)],
    planHint: PLANS[Math.floor(Math.random() * PLANS.length)],
  };
  const qs = Object.keys(q).map((k) => `${k}=${encodeURIComponent(q[k])}`).join('&');
  const rec = http.get(`${BASE_URL}/api/recommend?${qs}`, params(token, 'recommend'));
  recommendDuration.add(rec.timings.duration);
  if (!ok(rec, 'recommend')) return;
  aiSource.add(1, { source: rec.json('data.source') || 'unknown' });

  // 3. /result: read cards, open Kakao Map links.
  sleep(3 + Math.random() * 4);

  // 4. /selected-route: like ResultPage, the user picks ONE place per category
  //    (the server rejects fewer than 2 distinct categories with 400).
  const byCategory = {};
  for (const p of rec.json('data.recommendations') || []) {
    if (!byCategory[p.category]) byCategory[p.category] = p;
  }
  const places = Object.values(byCategory).slice(0, 2 + (__ITER % 2));
  if (places.length >= 2) {
    const body = Object.assign({}, q, {
      selectedPlaces: places.map((p) => ({
        category: p.category, name: p.name, address: p.address,
        reason: p.reason, tags: p.tags, mapQuery: p.mapQuery,
      })),
    });
    const route = http.post(`${BASE_URL}/api/recommend/selected-routes`,
      JSON.stringify(body), params(token, 'selected_routes'));
    routeDuration.add(route.timings.duration);
    ok(route, 'selected_routes');
    sleep(3 + Math.random() * 3);
  }

  // 5. MyPage
  if (Math.random() < 0.3) {
    ok(http.get(`${BASE_URL}/api/user/me`, params(token, 'user_me')), 'user_me');
    sleep(1 + Math.random() * 2);
  }
}
