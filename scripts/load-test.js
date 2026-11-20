import http from 'k6/http'
import { check, sleep } from 'k6'

const BASE = __ENV.BASE_URL || 'http://localhost:18080/api/v1'

export const options = {
  vus: Number(__ENV.VUS || 10),
  duration: __ENV.DURATION || '20s',
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500'],
  },
}

export default function () {
  const response = http.get(`${BASE}/health`)
  check(response, {
    'health status is 200': (res) => res.status === 200,
    'health body has code 0': (res) => {
      try {
        return JSON.parse(res.body).code === 0
      } catch {
        return false
      }
    },
  })
  sleep(1)
}
