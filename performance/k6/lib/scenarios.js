const MIX = [
    ["publicExplore", 35],
    ["authenticatedRead", 20],
    ["ownedRead", 15],
    ["activeRentalRead", 10],
    ["newRental", 15],
    ["loginOnly", 5]
];

export function constantMixedScenarios(totalRate, duration) {
    const rates = apportionedRates(totalRate);
    return Object.fromEntries(MIX.map(([exec], index) => [exec, {
        executor: "constant-arrival-rate",
        exec,
        rate: rates[index],
        timeUnit: "1s",
        duration,
        preAllocatedVUs: Math.max(2, rates[index] * 2),
        maxVUs: Math.max(10, rates[index] * 8),
        gracefulStop: "30s",
        tags: {workload: exec}
    }]));
}

export function rampingMixedScenarios(stages) {
    return Object.fromEntries(MIX.map(([exec, percent]) => [exec, {
        executor: "ramping-arrival-rate",
        exec,
        startRate: Math.max(1, Math.round(stages[0].target * percent / 100)),
        timeUnit: "1s",
        stages: stages.map((stage) => ({
            duration: stage.duration,
            target: Math.max(1, Math.round(stage.target * percent / 100))
        })),
        preAllocatedVUs: Math.max(4, Math.round(stages[0].target * percent / 50)),
        maxVUs: Math.max(20, Math.round(maxTarget(stages) * percent / 10)),
        gracefulStop: "30s",
        tags: {workload: exec}
    }]));
}

export const qualityThresholds = {
    checks: ["rate==1"],
    http_req_failed: ["rate<0.001"],
    http_req_duration: ["p(99)<1500"],
    "http_req_duration{flow:public}": ["p(95)<300"],
    "http_req_duration{flow:authenticated-read}": ["p(95)<500"],
    "http_req_duration{flow:new-rental}": ["p(95)<800"]
};

function apportionedRates(totalRate) {
    const exact = MIX.map(([, percent]) => totalRate * percent / 100);
    const rates = exact.map((rate) => Math.floor(rate));
    let remainder = totalRate - rates.reduce((sum, rate) => sum + rate, 0);
    const order = exact
        .map((rate, index) => ({index, fraction: rate - rates[index]}))
        .sort((left, right) => right.fraction - left.fraction);
    for (let index = 0; index < remainder; index += 1) {
        rates[order[index].index] += 1;
    }
    return rates.map((rate) => Math.max(1, rate));
}

function maxTarget(stages) {
    return stages.reduce((maximum, stage) => Math.max(maximum, stage.target), 0);
}
