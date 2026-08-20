import {check} from "k6";
import http from "k6/http";
import {
    activeRentalRead,
    authenticatedRead,
    loginOnly,
    newRental,
    ownedRead,
    publicExplore
} from "./lib/flows.js";
import {writeSummary} from "./lib/summary.js";

const BASE_URL = __ENV.BASE_URL || "http://127.0.0.1:8080";

export const options = {
    vus: 1,
    iterations: 1,
    thresholds: {
        checks: ["rate==1"],
        http_req_failed: ["rate==0"]
    }
};

export default function () {
    const smoke = http.get(`${BASE_URL}/api/smoke`, {
        tags: {flow: "smoke", name: "GET /api/smoke"}
    });
    check(smoke, {"애플리케이션 smoke 성공": (response) => response.status === 204});
    publicExplore();
    authenticatedRead();
    ownedRead();
    activeRentalRead();
    newRental();
    loginOnly();
}

export function handleSummary(data) {
    return writeSummary(data);
}
