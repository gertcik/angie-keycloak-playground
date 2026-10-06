import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:82';
const KC_URL = __ENV.KC_URL || 'http://localhost:8081';
const KC_REALM = __ENV.KC_REALM || 'bank';
const KC_CLIENT = __ENV.KC_CLIENT || 'bank-web';
const KC_USER = __ENV.KC_USER || 'testuser';
const KC_PASS = __ENV.KC_PASS || 'testpass123';

const clientSuccess = new Counter('client_success');
const accountSuccess = new Counter('account_success');
const clientAccountsSuccess = new Counter('client_accounts_success');
const meSuccess = new Counter('me_success');

const clientErrorRate = new Rate('client_error_rate');
const accountErrorRate = new Rate('account_error_rate');
const clientAccountsErrorRate = new Rate('client_accounts_error_rate');
const meErrorRate = new Rate('me_error_rate');

const clientDuration = new Trend('client_duration');
const accountDuration = new Trend('account_duration');
const clientAccountsDuration = new Trend('client_accounts_duration');
const meDuration = new Trend('me_duration');

export const options = {
  scenarios: {
    constant_load: {
      executor: 'constant-vus',
      vus: 10,
      duration: '30s',
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
  },
};

// Получаем access token из Keycloak (password grant) один раз на всю нагрузку.
// Дожидаемся готовности Keycloak ретраями — у контейнера нет healthcheck-гейта.
export function setup() {
  let token = null;
  for (let attempt = 0; attempt < 40 && !token; attempt++) {
    const res = http.post(
      `${KC_URL}/realms/${KC_REALM}/protocol/openid-connect/token`,
      {
        grant_type: 'password',
        client_id: KC_CLIENT,
        username: KC_USER,
        password: KC_PASS,
      },
      { headers: { 'Content-Type': 'application/x-www-form-urlencoded' } }
    );
    if (res.status === 200) {
      token = res.json('access_token');
    } else {
      sleep(2);
    }
  }
  if (!token) {
    throw new Error(`Cannot obtain access token from Keycloak (${KC_URL}, user ${KC_USER})`);
  }
  return { token };
}

export default function (data) {
  const headers = { Authorization: `Bearer ${data.token}` };
  const clientId = (Math.floor(Math.random() * 2) + 1).toString();
  const accountId = (Math.floor(Math.random() * 3) + 1).toString();

  // TEST 1: GET /api/me
  const meRes = http.get(`${BASE_URL}/api/me`, { headers });
  meDuration.add(meRes.timings.duration);
  if (meRes.status === 200) {
    meSuccess.add(1);
    meErrorRate.add(0);
    check(meRes, { 'me status is 200': (r) => r.status === 200 });
  } else {
    meErrorRate.add(1);
    check(meRes, { 'me status not 200': (r) => r.status !== 200 });
  }
  sleep(0.1);

  // TEST 2: GET /api/client/{id}
  const clientRes = http.get(`${BASE_URL}/api/client/${clientId}`, { headers });
  clientDuration.add(clientRes.timings.duration);
  if (clientRes.status === 200) {
    clientSuccess.add(1);
    clientErrorRate.add(0);
    check(clientRes, { 'client status is 200': (r) => r.status === 200 });
  } else {
    clientErrorRate.add(1);
    check(clientRes, { 'client status not 200': (r) => r.status !== 200 });
  }
  sleep(0.1);

  // TEST 3: GET /api/account/{id}
  const accountRes = http.get(`${BASE_URL}/api/account/${accountId}`, { headers });
  accountDuration.add(accountRes.timings.duration);
  if (accountRes.status === 200) {
    accountSuccess.add(1);
    accountErrorRate.add(0);
    check(accountRes, { 'account status is 200': (r) => r.status === 200 });
  } else {
    accountErrorRate.add(1);
    check(accountRes, { 'account status not 200': (r) => r.status !== 200 });
  }
  sleep(0.1);

  // TEST 4: GET /api/client/{id}/accounts
  const clientAccountsRes = http.get(`${BASE_URL}/api/client/${clientId}/accounts`, { headers });
  clientAccountsDuration.add(clientAccountsRes.timings.duration);
  if (clientAccountsRes.status === 200) {
    clientAccountsSuccess.add(1);
    clientAccountsErrorRate.add(0);
    check(clientAccountsRes, { 'client accounts status is 200': (r) => r.status === 200 });
  } else {
    clientAccountsErrorRate.add(1);
    check(clientAccountsRes, { 'client accounts status not 200': (r) => r.status !== 200 });
  }
  sleep(0.1);
}

export function handleSummary(data) {
  console.log('=== Load Test Results ===');
  console.log(`ME Success: ${data.metrics.me_success?.values?.count || 0}`);
  console.log(`Client Success: ${data.metrics.client_success?.values?.count || 0}`);
  console.log(`Account Success: ${data.metrics.account_success?.values?.count || 0}`);
  console.log(`Client Accounts Success: ${data.metrics.client_accounts_success?.values?.count || 0}`);
  console.log(`HTTP Req Duration p95: ${data.metrics.http_req_duration?.values['p(95)'] || 0}ms`);
  return { stdout: 'text' };
}