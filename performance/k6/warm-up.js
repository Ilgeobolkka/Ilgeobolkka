import {
    activeRentalRead,
    authenticatedRead,
    loginOnly,
    newRental,
    ownedRead,
    publicExplore
} from "./lib/flows.js";
import {writeSummary} from "./lib/summary.js";

export const options = {
    noCookiesReset: true,
    scenarios: {
        warmUp: {
            executor: "constant-arrival-rate",
            exec: "warmUp",
            rate: 5,
            timeUnit: "1s",
            duration: __ENV.PERF_DURATION || "3m",
            preAllocatedVUs: 10,
            maxVUs: 40,
            gracefulStop: "30s"
        }
    },
    thresholds: {checks: ["rate==1"], http_req_failed: ["rate<0.001"]}
};

const FLOWS = [
    publicExplore,
    publicExplore,
    authenticatedRead,
    ownedRead,
    activeRentalRead,
    newRental,
    loginOnly
];

export function warmUp() {
    FLOWS[(__VU - 1) % FLOWS.length]();
}

export function handleSummary(data) {
    return writeSummary(data);
}
