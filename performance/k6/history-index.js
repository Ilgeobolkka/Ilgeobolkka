import http from "k6/http";
import {check} from "k6";
import {activeReaderNumber} from "./lib/flows.js";
import {login} from "./lib/auth.js";
import {writeSummary} from "./lib/summary.js";

const BASE_URL = __ENV.BASE_URL || "http://127.0.0.1:8080";
const authenticatedVus = new Set();

export const options = {
    noCookiesReset: true,
    scenarios: {
        library: endpointScenario("libraryEndpoint"),
        ledger: endpointScenario("ledgerEndpoint")
    },
    thresholds: {
        checks: ["rate==1"],
        http_req_failed: ["rate==0"],
        "http_req_duration{measurement:history-library}": ["p(95)<1000"],
        "http_req_duration{measurement:history-ledger}": ["p(95)<1000"]
    }
};

export function libraryEndpoint() {
    ensureLogin();
    const response = http.get(`${BASE_URL}/api/library`, {
        tags: {measurement: "history-library", name: "GET /api/library"}
    });
    check(response, {"서재 이력 조회 성공": (result) => result.status === 200});
}

export function ledgerEndpoint() {
    ensureLogin();
    const response = http.get(`${BASE_URL}/api/ink/ledger?page=1`, {
        tags: {measurement: "history-ledger", name: "GET /api/ink/ledger"}
    });
    check(response, {"원장 이력 조회 성공": (result) => result.status === 200});
}

export function handleSummary(data) {
    return writeSummary(data);
}

function ensureLogin() {
    if (authenticatedVus.has(__VU)) {
        return;
    }
    login(activeReaderNumber(__VU), {setup: "true"});
    authenticatedVus.add(__VU);
}

function endpointScenario(exec) {
    return {
        executor: "constant-arrival-rate",
        exec,
        rate: 10,
        timeUnit: "1s",
        duration: __ENV.PERF_DURATION || "2m",
        preAllocatedVUs: 10,
        maxVUs: 40,
        gracefulStop: "10s"
    };
}
