// k6 load test for the redirect hot path (the endpoint that must scale).
//
// Usage:
//   1. Start infra + app:   docker compose up -d && make run
//   2. Seed a code:         CODE=$(curl -s -XPOST localhost:8080/api/v1/links \
//                              -H 'Content-Type: application/json' \
//                              -d '{"url":"https://example.com/target"}' | jq -r .link.shortCode)
//   3. Run:                 BASE_URL=http://localhost:8080 CODE=$CODE k6 run perf/redirect-load.js
//
// The test asserts p95 latency and a near-zero error rate under a ramping load, exercising the
// Redis cache-aside path. It is intentionally NOT run in CI (needs a running stack + k6 binary).

import http from "k6/http";
import { check } from "k6";
import { Rate } from "k6/metrics";

const errors = new Rate("errors");

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const CODE = __ENV.CODE || "abc1234";

export const options = {
  stages: [
    { duration: "30s", target: 50 }, // ramp up
    { duration: "1m", target: 200 }, // sustained load
    { duration: "30s", target: 0 }, // ramp down
  ],
  thresholds: {
    // The redirect path is cache-served; hold a tight latency budget.
    http_req_duration: ["p(95)<50", "p(99)<150"],
    errors: ["rate<0.001"],
  },
};

export default function () {
  // Do not follow the 302; we measure the shortener's response, not example.com.
  const res = http.get(`${BASE_URL}/${CODE}`, { redirects: 0 });
  const ok = check(res, {
    "status is 302": (r) => r.status === 302,
    "has location": (r) => !!r.headers["Location"],
  });
  errors.add(!ok);
}
