import {
    activeRentalRead,
    authenticatedRead,
    loginOnly,
    newRental,
    ownedRead,
    publicExplore
} from "./lib/flows.js";
import {rampingMixedScenarios, qualityThresholds} from "./lib/scenarios.js";
import {writeSummary} from "./lib/summary.js";

export const options = {
    noCookiesReset: true,
    scenarios: rampingMixedScenarios([
        {duration: "2m", target: 20},
        {duration: "2m", target: 100},
        {duration: "5m", target: 20}
    ]),
    thresholds: qualityThresholds
};

export {activeRentalRead, authenticatedRead, loginOnly, newRental, ownedRead, publicExplore};

export function handleSummary(data) {
    return writeSummary(data);
}
