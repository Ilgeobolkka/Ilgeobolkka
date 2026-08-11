export function writeSummary(data) {
    const summaryPath = __ENV.K6_SUMMARY_PATH || "/results/summary.json";
    return {
        [summaryPath]: JSON.stringify(data, null, 2)
    };
}
