import {browser} from "k6/browser";
import {check} from "k6";
import {Trend} from "k6/metrics";
import {readerEmail} from "./lib/auth.js";
import {writeSummary} from "./lib/summary.js";

const BASE_URL = __ENV.BASE_URL || "http://127.0.0.1:8080";
const PASSWORD = __ENV.PERF_USER_PASSWORD;
const PAIR_COUNT = 3;
const publicCold = trends("browser_public_cold_ms");
const publicWarm = trends("browser_public_warm_ms");
const protectedCold = trends("browser_protected_cold_ms");
const protectedWarm = trends("browser_protected_warm_ms");

export const options = {
    scenarios: {
        browserCache: {
            executor: "shared-iterations",
            vus: 1,
            iterations: 1,
            options: {browser: {type: "chromium"}}
        }
    },
    thresholds: {checks: ["rate==1"]}
};

export default async function () {
    for (let pair = 1; pair <= PAIR_COUNT; pair += 1) {
        await measurePair(pair);
    }
}

async function measurePair(pair) {
    const context = await browser.newContext();
    const page = await context.newPage();
    const responses = [];
    let phase = "idle";
    let browserError = null;

    page.on("response", (response) => {
        const url = response.url();
        if (isStaticAsset(url) || url.includes("/reading-sessions/current/pages/")) {
            const headers = response.headers();
            responses.push({
                phase,
                url,
                status: response.status(),
                cacheControl: header(headers, "cache-control")
            });
        }
    });

    try {
        phase = "public-cold";
        const publicColdMs = await navigate(page, `${BASE_URL}/books`);
        publicCold[pair - 1].add(publicColdMs);
        const publicColdResources = await resourceSnapshot(page);

        await page.evaluate(() => performance.clearResourceTimings());
        phase = "public-warm";
        const publicWarmMs = await reload(page);
        publicWarm[pair - 1].add(publicWarmMs);
        const publicWarmResources = await resourceSnapshot(page);

        phase = "login";
        await login(page);

        phase = "protected-cold";
        const protectedColdMs = await navigate(
            page,
            `${BASE_URL}/books/3/viewer?page=1`,
            "[data-viewer-content][aria-busy='false']"
        );
        protectedCold[pair - 1].add(protectedColdMs);

        phase = "protected-warm";
        const protectedWarmMs = await reload(
            page,
            "[data-viewer-content][aria-busy='false']"
        );
        protectedWarm[pair - 1].add(protectedWarmMs);

        const evidence = {
            pair,
            public: {
                coldMs: publicColdMs,
                warmMs: publicWarmMs,
                coldTransferBytes: transferBytes(publicColdResources),
                warmTransferBytes: transferBytes(publicWarmResources),
                coldAssets: responsesFor(responses, "public-cold"),
                warmAssets: responsesFor(responses, "public-warm")
            },
            protected: {
                coldMs: protectedColdMs,
                warmMs: protectedWarmMs,
                coldResponses: responsesFor(responses, "protected-cold"),
                warmResponses: responsesFor(responses, "protected-warm")
            }
        };
        check(evidence, {
            "cold 공개 정적 자산 관측": (result) => result.public.coldAssets.length > 0,
            "warm 공개 정적 자산 관측": (result) => result.public.warmAssets.length > 0,
            "cold 보호 콘텐츠 no-store": (result) =>
                hasPrivateNoStore(result.protected.coldResponses),
            "warm 보호 콘텐츠 no-store": (result) =>
                hasPrivateNoStore(result.protected.warmResponses)
        });
        console.log(`BROWSER_CACHE_EVIDENCE ${JSON.stringify(evidence)}`);
    } catch (error) {
        browserError = error;
    } finally {
        await page.close();
        await context.close();
    }

    check(browserError, {
        "브라우저 cache 흐름 예외 없음": (error) => error === null
    });
    if (browserError) {
        throw browserError;
    }
}

async function login(page) {
    await page.goto(`${BASE_URL}/login`, {waitUntil: "load"});
    await page.locator("#login-email").fill(readerEmail(3));
    await page.locator("#login-password").fill(PASSWORD);
    await Promise.all([
        page.waitForURL(/\/books$/),
        page.locator("button[type='submit']").click()
    ]);
}

async function navigate(page, url, readySelector = null) {
    const started = Date.now();
    await page.goto(url, {waitUntil: "load"});
    if (readySelector) {
        await page.locator(readySelector).waitFor();
    }
    return Date.now() - started;
}

async function reload(page, readySelector = null) {
    const started = Date.now();
    await page.reload({waitUntil: "load"});
    if (readySelector) {
        await page.locator(readySelector).waitFor();
    }
    return Date.now() - started;
}

async function resourceSnapshot(page) {
    return page.evaluate(() => performance.getEntriesByType("resource").map((entry) => ({
        name: entry.name,
        transferSize: entry.transferSize
    })));
}

function trends(prefix) {
    return Array.from({length: PAIR_COUNT}, (_, index) =>
        new Trend(`${prefix}_pair_${index + 1}`, true));
}

function isStaticAsset(url) {
    return url.includes("/webjars/") || url.includes("/css/") || url.includes("/js/");
}

function responsesFor(responses, targetPhase) {
    return responses.filter((response) => response.phase === targetPhase);
}

function transferBytes(resources) {
    return resources
        .filter((resource) => isStaticAsset(resource.name))
        .reduce((total, resource) => total + resource.transferSize, 0);
}

function hasPrivateNoStore(responses) {
    return responses.some((response) =>
        response.cacheControl.includes("private") && response.cacheControl.includes("no-store"));
}

function header(headers, expectedName) {
    const found = Object.entries(headers)
        .find(([name]) => name.toLowerCase() === expectedName);
    return found ? found[1] : "";
}

export function handleSummary(data) {
    return writeSummary(data);
}
