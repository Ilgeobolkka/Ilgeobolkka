import {
    logout,
    newReaderNumber,
    openPageOnly
} from "./lib/flows.js";
import {writeSummary} from "./lib/summary.js";

const contentionCase = __ENV.CONTENTION_CASE || "same-page";
const configurations = {
    "same-page": {exec: "samePage", vus: 20},
    "different-pages": {exec: "differentPages", vus: 20},
    "different-readers": {exec: "differentReaders", vus: 100},
    session: {exec: "multipleSessions", vus: 20}
};
const configuration = configurations[contentionCase];
if (!configuration) {
    throw new Error(`지원하지 않는 CONTENTION_CASE입니다: ${contentionCase}`);
}

export const options = {
    scenarios: {
        contention: {
            executor: "per-vu-iterations",
            exec: configuration.exec,
            vus: configuration.vus,
            iterations: 1,
            maxDuration: "2m"
        }
    },
    thresholds: {
        checks: ["rate==1"],
        http_req_failed: ["rate==0"]
    }
};

export function samePage() {
    openPageOnly(1, 100, 4, "contention-same-page");
}

export function differentPages() {
    const slot = __VU - 1;
    const bookId = Math.floor(slot / 4) + 1;
    const pageNumber = slot % 4 + 1;
    openPageOnly(4, bookId, pageNumber, "contention-different-pages");
}

export function differentReaders() {
    const readerNumber = newReaderNumber(__VU);
    const slot = __VU - 1;
    const bookId = Math.floor(slot / 4) + 1;
    const pageNumber = slot % 4 + 1;
    openPageOnly(readerNumber, bookId, pageNumber, "contention-different-readers");
}

export function multipleSessions() {
    logout(7, "contention-session");
}

export function handleSummary(data) {
    return writeSummary(data);
}
