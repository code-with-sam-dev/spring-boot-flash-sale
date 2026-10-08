// A flash sale: buyers all want product 1, everyone else browses the catalogue.
// Open model (constant-arrival-rate): new requests keep arriving whether or not the app keeps up,
// the way real users do. Rates come from the environment so every run is a command line.
import http from 'k6/http';
import { check } from 'k6';

const BUY = Number(__ENV.BUY_RATE || 20);       // orders started per second
const BROWSE = Number(__ENV.BROWSE_RATE || 200); // product page views per second
const DURATION = __ENV.DURATION || '60s';
const BASE = __ENV.BASE || 'http://localhost:8080';

export const options = {
  discardResponseBodies: true,
  // Thresholds that always pass, only so k6 reports orders and page views separately.
  thresholds: {
    'http_req_duration{name:order}': ['p(99)>=0'],
    'http_req_duration{name:browse}': ['p(99)>=0'],
    'http_req_failed{name:order}': ['rate>=0'],
    'http_req_failed{name:browse}': ['rate>=0'],
  },
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    buyers: {
      executor: 'constant-arrival-rate', rate: BUY, timeUnit: '1s', duration: DURATION,
      preAllocatedVUs: 200, maxVUs: Number(__ENV.MAX_VUS || 5000), exec: 'buy',
    },
    browsers: {
      executor: 'constant-arrival-rate', rate: BROWSE, timeUnit: '1s', duration: DURATION,
      preAllocatedVUs: 200, maxVUs: Number(__ENV.MAX_VUS || 5000), exec: 'browse',
    },
  },
};

const params = { headers: { 'Content-Type': 'application/json' }, timeout: '60s' };

export function buy() {
  const res = http.post(`${BASE}/orders`, JSON.stringify({ productId: 1, customer: `buyer-${__VU}-${__ITER}` }),
    Object.assign({ tags: { name: 'order' } }, params));
  check(res, { 'order placed': (r) => r.status === 200 });
}

export function browse() {
  const id = 2 + Math.floor(Math.random() * 999);
  const res = http.get(`${BASE}/products/${id}`, { tags: { name: 'browse' }, timeout: '60s' });
  check(res, { 'page shown': (r) => r.status === 200 });
}
