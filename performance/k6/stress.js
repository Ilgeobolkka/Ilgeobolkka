import {
    activeRentalRead,
    authenticatedRead,
    loginOnly,
    newRental,
    ownedRead,
    publicExplore
} from "./lib/flows.js";
import {rampingMixedScenarios} from "./lib/scenarios.js";
import {writeSummary} from "./lib/summary.js";

const stages = [
    {duration: "3m", target: 10},
    {duration: "3m", target: 25},
    {duration: "3m", target: 50},
    {duration: "3m", target: 100},
    {duration: "3m", target: 200}
];

export const options = {
    noCookiesReset: true,
    scenarios: rampingMixedScenarios(stages),
    thresholds: {
        checks: ["rate>0.99"],
        http_req_failed: [{threshold: "rate<=0.01", abortOnFail: true, delayAbortEval: "2m"}],
        http_req_duration: [{threshold: "p(95)<=2000", abortOnFail: true, delayAbortEval: "2m"}]
    }
};

export {activeRentalRead, authenticatedRead, loginOnly, newRental, ownedRead, publicExplore};

export function handleSummary(data) {
    return writeSummary(data);
}
