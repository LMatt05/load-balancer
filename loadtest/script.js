import http from 'k6/http';
import { check } from 'k6';

const TARGET = __ENV.TARGET || 'http://balancer:8080';
const RATE = Number(__ENV.RATE || 500);
const DURATION = __ENV.DURATION || '30s';
const MAX_VUS = Number(__ENV.MAX_VUS || 1000);

export const options = {
    scenarios: {
        load: {
            executor: 'constant-arrival-rate',
            rate: RATE,
            timeUnit: '1s',
            duration: DURATION,
            preAllocatedVUs: 100,
            maxVUs: MAX_VUS,
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.01'],
        http_req_duration: ['p(95)<500'],
    },
    summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export default function () {
    const res = http.get(`${TARGET}/`);
    check(res, { 'status is 200': (r) => r.status === 200 });
}