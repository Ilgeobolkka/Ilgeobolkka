import {
    activeRentalRead,
    authenticatedRead,
    loginOnly,
    newRental,
    ownedRead,
    publicExplore
} from "./lib/flows.js";
import {constantMixedScenarios, qualityThresholds} from "./lib/scenarios.js";
import {writeSummary} from "./lib/summary.js";

export const options = {
    noCookiesReset: true,
    scenarios: constantMixedScenarios(20, __ENV.PERF_DURATION || "10m"),
    thresholds: qualityThresholds
};

export {activeRentalRead, authenticatedRead, loginOnly, newRental, ownedRead, publicExplore};

export function handleSummary(data) {
    return writeSummary(data);
}
