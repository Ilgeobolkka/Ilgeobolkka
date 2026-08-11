import {browser} from "k6/browser";
import {check} from "k6";
import {readerEmail} from "./lib/auth.js";
import {writeSummary} from "./lib/summary.js";

const BASE_URL = __ENV.BASE_URL || "http://127.0.0.1:8080";
const PASSWORD = __ENV.PERF_USER_PASSWORD;

export const options = {
    scenarios: {
        browser: {
            executor: "shared-iterations",
            vus: 1,
            iterations: 1,
            options: {
                browser: {type: "chromium"}
            }
        }
    },
    thresholds: {checks: ["rate==1"]}
};

export default async function () {
    const page = await browser.newPage();
    let browserError = null;
    try {
        await page.goto(`${BASE_URL}/books`, {waitUntil: "networkidle"});
        check(page, {"도서 목록 화면 표시": () => page.url().includes("/books")});

        await page.goto(`${BASE_URL}/books/1`, {waitUntil: "networkidle"});
        check(page, {"도서 상세 화면 표시": () => page.url().endsWith("/books/1")});

        await page.goto(`${BASE_URL}/login`, {waitUntil: "networkidle"});
        await page.locator("#login-email").fill(readerEmail(3));
        await page.locator("#login-password").fill(PASSWORD);
        await Promise.all([
            page.waitForURL(/\/books$/),
            page.locator("button[type='submit']").click()
        ]);

        await page.goto(`${BASE_URL}/books/3/viewer?page=1`, {waitUntil: "networkidle"});
        await page.locator("[data-viewer-content][aria-busy='false']").waitFor();
        const viewerText = await page.locator("[data-viewer-content]").textContent();
        check(viewerText, {
            "소장 뷰어 콘텐츠 표시": (text) => text.includes("성능 도서")
        });
    } catch (error) {
        browserError = error;
    } finally {
        await page.close();
    }
    check(browserError, {
        "브라우저 흐름 예외 없음": (error) => error === null
    });
    if (browserError) {
        throw browserError;
    }
}

export function handleSummary(data) {
    return writeSummary(data);
}
