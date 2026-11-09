// SUPERSEDED by recommend-flow.js (2026-11-09). This flow browses the group/place
// features, which exist in the backend but are not used by any frontend page.
// Kept only so the first ops-log measurements stay reproducible.
//
// MeetingMate realistic user-flow load test (k6).
//
// Flow per iteration (one "visit"):
//   my profile -> my groups -> group detail -> place candidates -> AI recommendation
//   with think time between steps, like a real user reading the screen.
//
// Login: OAuth (Kakao/Google) cannot be automated, so setup() uses the app's own
// local-account endpoints (/api/auth/signup, /api/auth/login) to get real JWTs.
// No test-only auth code is added to the application.
//
// Run (local stack only):
//   ./load-test/run.sh user-flow baseline
//
// Env:
//   BASE_URL   default http://backend:8080 (inside the compose network)
//   USERS      number of test accounts (default 60)
//   PROFILE    "local" (ramp to 60 VUs) | "smoke" (2 VUs, 30s) | "prod-baseline" (<=30 VUs, <3 min)

import http from 'k6/http';
import { check, sleep, group, fail } from 'k6';
import { Trend, Rate } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://backend:8080';
const USERS = parseInt(__ENV.USERS || '60', 10);
const PROFILE = __ENV.PROFILE || 'local';
const RUN_ID = __ENV.RUN_ID || `${Date.now()}`;

const recommendDuration = new Trend('recommend_duration', true);
const flowErrors = new Rate('flow_errors');

const profiles = {
  smoke: { executor: 'constant-vus', vus: 2, duration: '30s' },
  local: {
    executor: 'ramping-vus',
    startVUs: 0,
    stages: [
      { duration: '30s', target: 20 },
      { duration: '1m', target: 60 },
      { duration: '2m', target: 60 },
      { duration: '30s', target: 0 },
    ],
    gracefulRampDown: '10s',
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
  scenarios: { user_flow: profiles[PROFILE] },
  thresholds: {
    'http_req_failed{type:api}': ['rate<0.01'],
    'http_req_duration{type:api}': ['p(95)<500'],
    recommend_duration: ['p(95)<8000'],
    flow_errors: ['rate<0.01'],
  },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

function headers(token, name) {
  return {
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      // Lets us find any slow request in the backend logs via MDC.
      'X-Request-Id': `k6-${RUN_ID.slice(-6)}-${__VU}-${typeof __ITER === 'undefined' ? 'setup' : __ITER}-${name}`,
    },
    tags: { name, type: name === 'recommend' ? 'recommend' : 'api' },
  };
}

function ok(res, name) {
  const passed = check(res, { [`${name} 2xx`]: (r) => r.status >= 200 && r.status < 300 });
  flowErrors.add(!passed);
  return passed;
}

// --- setup: create accounts, one group per account, a few place candidates ---
export function setup() {
  const accounts = [];
  for (let i = 0; i < USERS; i++) {
    const email = `lt-${RUN_ID}-${i}@loadtest.local`;
    const password = 'loadtest-pass-1';
    http.post(`${BASE_URL}/api/auth/signup`,
      JSON.stringify({ email, password, nickname: `lt${i}` }), headers(null, 'setup_signup'));
    const login = http.post(`${BASE_URL}/api/auth/login`,
      JSON.stringify({ email, password }), headers(null, 'setup_login'));
    if (login.status !== 200) fail(`login failed for ${email}: ${login.status} ${login.body}`);
    const token = login.json('data.accessToken');

    const g = http.post(`${BASE_URL}/api/groups`,
      JSON.stringify({ name: `loadtest group ${i}`, description: 'k6' }), headers(token, 'setup_group'));
    const groupId = g.json('data.id');

    for (let p = 0; p < 5; p++) {
      http.post(`${BASE_URL}/api/groups/${groupId}/places`, JSON.stringify({
        name: `후보 장소 ${p}`, category: p % 2 ? 'CAFE' : 'FOOD',
        lat: 37.55 + p * 0.001, lng: 126.92 + p * 0.001,
      }), headers(token, 'setup_place'));
    }
    accounts.push({ token, groupId });
  }
  return { accounts };
}

const KEYWORDS = ['홍대', '강남역', '성수동', '잠실', '신촌', '연남동', '건대입구', '합정'];

export default function (data) {
  const me = data.accounts[(__VU - 1) % data.accounts.length];
  const t = me.token;

  group('browse', () => {
    ok(http.get(`${BASE_URL}/api/user/me`, headers(t, 'user_me')), 'user_me');
    sleep(0.5 + Math.random());
    ok(http.get(`${BASE_URL}/api/groups/me`, headers(t, 'my_groups')), 'my_groups');
    sleep(0.5 + Math.random());
    ok(http.get(`${BASE_URL}/api/groups/${me.groupId}`, headers(t, 'group_detail')), 'group_detail');
    ok(http.get(`${BASE_URL}/api/groups/${me.groupId}/places`, headers(t, 'places')), 'places');
    sleep(1 + Math.random() * 2);
  });

  group('recommend', () => {
    const kw = KEYWORDS[Math.floor(Math.random() * KEYWORDS.length)];
    // Vary mood so the request is not identical every time.
    const mood = ['조용한', '활기찬', '가성비'][__ITER % 3];
    const res = http.get(
      `${BASE_URL}/api/recommend?keyword=${encodeURIComponent(kw)}&location=${encodeURIComponent(kw)}&mood=${encodeURIComponent(mood)}`,
      Object.assign(headers(t, 'recommend'), { timeout: '60s' }),
    );
    recommendDuration.add(res.timings.duration);
    ok(res, 'recommend');
    sleep(2 + Math.random() * 3);
  });
}
