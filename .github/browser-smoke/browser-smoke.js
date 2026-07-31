import {clearCommonError, showCommonError} from "/js/common/error-display.js";
import {initializeInkPage} from "/js/ink/ink-page.js";
import {ApiRequestError, requestJson} from "/js/common/request-json.js";

const DEFAULT_ERROR_MESSAGE = "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.";
const result = document.querySelector("[data-browser-smoke-result]");
const fixtureContainer = document.querySelector("[data-logout-fixtures]");
let assertionCount = 0;

run()
    .then(() => {
        document.documentElement.dataset.browserSmoke = "passed";
        result.textContent = `PASS (${assertionCount})`;
    })
    .catch((error) => {
        document.documentElement.dataset.browserSmoke = "failed";
        result.textContent = `FAIL: ${error.message}`;
    });

async function run() {
    const books = await requestJson("/api/books?page=1");
    assert(Array.isArray(books.books), "도서 목록 JSON을 반환해야 합니다.");
    assert(books.page === 1, "요청한 도서 목록 페이지 번호를 보존해야 합니다.");
    assert(await requestJson("/api/smoke") === null, "204 응답은 null이어야 합니다.");

    await assertApiError(
        () => requestJson("https://example.com/api"),
        "CROSS_ORIGIN_REQUEST",
        0,
        null,
        "같은 출처의 API만 호출할 수 있습니다.");

    const originalFetch = window.fetch;
    try {
        await verifyResponseHandling();
        await verifyCsrfHandling();
        await verifyNoAutomaticAuthenticationRedirect();
    } finally {
        window.fetch = originalFetch;
    }

    verifySafeErrorDisplay();
    await verifyLogoutNavigation("success");
    await verifyLogoutNavigation("server-error");
    await verifyLogoutRetryableError("network-error");
    await verifyLogoutRetryableError("missing-csrf");
    await verifyInkPage();
}

async function verifyResponseHandling() {
    window.fetch = async () => new Response("<html>not json</html>", {
        status: 200,
        headers: {"X-Request-Id": "invalid-success-id"}
    });
    await assertApiError(
        () => requestJson("/api/test"),
        "INVALID_RESPONSE",
        200,
        "invalid-success-id",
        DEFAULT_ERROR_MESSAGE);

    window.fetch = async () => new Response("not json", {
        status: 503,
        headers: {"X-Request-Id": "invalid-failure-id"}
    });
    await assertApiError(
        () => requestJson("/api/test"),
        "INVALID_RESPONSE",
        503,
        "invalid-failure-id",
        DEFAULT_ERROR_MESSAGE);

    window.fetch = async () => {
        throw new TypeError("강제 네트워크 오류");
    };
    await assertApiError(
        () => requestJson("/api/test"),
        "NETWORK_ERROR",
        0,
        null,
        DEFAULT_ERROR_MESSAGE);
}

async function verifyCsrfHandling() {
    let capturedRequest;
    window.fetch = async (url, options) => {
        capturedRequest = {url, options};
        return new Response(null, {status: 204});
    };

    await requestJson("/api/test");
    assert(
        !capturedRequest.options.headers.has("X-CSRF-TOKEN"),
        "안전한 HTTP 메서드에는 CSRF 헤더를 추가하면 안 됩니다.");

    const response = await requestJson("/api/test", {
        method: "post",
        body: null
    });
    assert(response === null, "상태 변경 204 응답은 null이어야 합니다.");
    assert(capturedRequest.url.pathname === "/api/test", "same-origin URL을 유지해야 합니다.");
    assert(capturedRequest.options.method === "POST", "HTTP 메서드를 대문자로 정규화해야 합니다.");
    assert(
        capturedRequest.options.credentials === "same-origin",
        "same-origin 자격 증명만 전송해야 합니다.");
    assert(
        capturedRequest.options.headers.get("X-CSRF-TOKEN") === "browser-smoke-token",
        "페이지의 CSRF 토큰을 요청 헤더에 추가해야 합니다.");
    assert(
        capturedRequest.options.headers.get("Content-Type") === "application/json",
        "JSON 요청의 Content-Type을 추가해야 합니다.");

    const tokenMeta = document.querySelector("meta[name='_csrf']");
    const token = tokenMeta.content;
    let fetchCalled = false;
    tokenMeta.removeAttribute("content");
    window.fetch = async () => {
        fetchCalled = true;
        return new Response(null, {status: 204});
    };
    try {
        await assertApiError(
            () => requestJson("/api/test", {method: "POST"}),
            "MISSING_CSRF_TOKEN",
            0,
            null,
            "보안 토큰을 찾을 수 없습니다. 페이지를 새로고침해 주세요.");
        assert(!fetchCalled, "CSRF 토큰이 없으면 요청을 전송하지 않아야 합니다.");
    } finally {
        tokenMeta.content = token;
    }
}

async function verifyNoAutomaticAuthenticationRedirect() {
    const currentUrl = window.location.href;
    for (const status of [401, 403]) {
        window.fetch = async () => new Response(JSON.stringify({
            code: "AUTHENTICATION_ERROR",
            message: "인증 오류"
        }), {
            status,
            headers: {
                "Content-Type": "application/json",
                "X-Request-Id": `authentication-${status}`
            }
        });
        await assertApiError(
            () => requestJson("/api/test"),
            "AUTHENTICATION_ERROR",
            status,
            `authentication-${status}`,
            "인증 오류");
        assert(window.location.href === currentUrl, `${status} 응답에서 자동 이동하면 안 됩니다.`);
    }
}

function verifySafeErrorDisplay() {
    const errorRegion = document.querySelector("[data-common-error]");
    const message = "<strong>브라우저 오류</strong>";
    showCommonError(new Error(message));
    assert(errorRegion.textContent === message, "오류 메시지를 textContent로 표시해야 합니다.");
    assert(errorRegion.querySelector("strong") === null, "오류 메시지를 HTML로 해석하면 안 됩니다.");
    assert(!errorRegion.hidden, "오류 영역을 표시해야 합니다.");
    assert(document.activeElement === errorRegion, "오류 영역으로 포커스를 이동해야 합니다.");

    clearCommonError();
    assert(errorRegion.hidden, "오류를 지우면 오류 영역을 숨겨야 합니다.");
    assert(errorRegion.textContent === "", "오류를 지우면 기존 메시지를 제거해야 합니다.");
}

async function verifyLogoutNavigation(mode) {
    const iframe = await loadLogoutFixture(mode);
    iframe.contentDocument.querySelector("[data-logout-form]").requestSubmit();
    await waitFor(
        () => iframe.contentWindow.location.pathname === "/books",
        `${mode} 로그아웃 뒤 /books로 이동해야 합니다.`);
    iframe.remove();
}

async function verifyLogoutRetryableError(mode) {
    const iframe = await loadLogoutFixture(mode);
    const iframeDocument = iframe.contentDocument;
    iframeDocument.querySelector("[data-logout-form]").requestSubmit();

    await waitFor(
        () => !iframeDocument.querySelector("[data-common-error]").hidden,
        "네트워크 오류를 현재 화면에 표시해야 합니다.");
    assert(
        iframe.contentWindow.location.pathname === "/logout-fixture.html",
        "요청을 보내지 못한 오류에서는 현재 화면을 유지해야 합니다.");
    assert(
        iframeDocument.querySelector("button[type='submit']").disabled === false,
        "네트워크 오류 뒤 로그아웃을 다시 시도할 수 있어야 합니다.");
    assert(
        iframeDocument.activeElement === iframeDocument.querySelector("[data-common-error]"),
        "네트워크 오류 영역으로 포커스를 이동해야 합니다.");
    iframe.remove();
}

async function loadLogoutFixture(mode) {
    const iframe = document.createElement("iframe");
    const loaded = new Promise((resolve) => iframe.addEventListener("load", resolve, {once: true}));
    iframe.src = `/logout-fixture.html?mode=${mode}`;
    fixtureContainer.append(iframe);
    await loaded;
    await waitFor(
        () => iframe.contentDocument.body.dataset.logoutFixtureReady === "true",
        `${mode} 로그아웃 fixture가 준비되어야 합니다.`);
    return iframe;
}

async function verifyInkPage() {
    const fixture = document.querySelector("[data-ink-smoke-fixture]");
    const purchaseButton = fixture.querySelector("[data-ink-purchase]");
    const retryButton = fixture.querySelector("[data-ink-payment-retry]");
    const paymentStatus = fixture.querySelector("[data-ink-payment-status]");
    const previousButton = fixture.querySelector("[data-ink-ledger-previous]");
    const nextButton = fixture.querySelector("[data-ink-ledger-next]");
    const requestedLedgerPages = [];
    let balance = 100;
    let prepareCount = 0;
    let completeCount = 0;
    let sdkMode = "success";
    let paymentMode = "success";
    let completeMode = "paid";
    let lastPaymentRequest;

    const fakeRequest = async (url) => {
        if (url === "/api/ink/balance") {
            return {balance};
        }
        if (url.startsWith("/api/ink/ledger")) {
            const page = Number(new URL(url, window.location.href).searchParams.get("page"));
            requestedLedgerPages.push(page);
            return inkLedgerResponse(page);
        }
        if (url === "/api/ink/purchases") {
            prepareCount += 1;
            return {
                paymentId: `payment-${prepareCount}`,
                storeId: "store-test",
                channelKey: "channel-test",
                orderName: "읽어볼까 100잉크",
                totalAmount: 1000,
                currency: "CURRENCY_KRW"
            };
        }
        if (url.includes("/complete")) {
            completeCount += 1;
            if (completeMode === "pending") {
                return {
                    paymentId: `payment-${prepareCount}`,
                    status: "PENDING",
                    grantedInk: 0,
                    inkBalance: balance
                };
            }
            if (completeMode === "unavailable") {
                throw new ApiRequestError(
                    "PAYMENT_PROVIDER_UNAVAILABLE",
                    "결제 확인 서비스 오류",
                    503,
                    "payment-503");
            }
            if (completeMode === "failed") {
                throw new ApiRequestError(
                    "PAYMENT_VERIFICATION_FAILED",
                    "결제 검증에 실패했습니다.",
                    422,
                    "payment-422");
            }
            if (completeMode === "conflict") {
                throw new ApiRequestError(
                    "PAYMENT_STATE_CONFLICT",
                    "결제 상태가 변경되어 요청을 처리할 수 없습니다.",
                    409,
                    "payment-409");
            }
            balance = 200;
            return {
                paymentId: `payment-${prepareCount}`,
                status: "PAID",
                grantedInk: 100,
                inkBalance: balance
            };
        }
        throw new Error(`예상하지 않은 잉크 API 요청: ${url}`);
    };

    const fakePortOneLoader = async () => {
        if (sdkMode === "failed") {
            throw new Error("강제 CDN 오류");
        }
        return {
            requestPayment: async (request) => {
                lastPaymentRequest = request;
                if (paymentMode === "interrupted") {
                    return {
                        paymentId: request.paymentId,
                        code: "PAYMENT_PROCESS_ABORTED",
                        message: "사용자가 결제창을 닫았습니다."
                    };
                }
                return {paymentId: request.paymentId};
            }
        };
    };

    const {ready} = initializeInkPage(fixture, {
        request: fakeRequest,
        loadPortOne: fakePortOneLoader
    });
    await ready;

    assert(
        fixture.querySelector("[data-ink-balance]").textContent === "100잉크",
        "현재 잉크 잔액을 표시해야 합니다.");
    assert(
        fixture.querySelector("[data-ink-ledger]").textContent.includes("+100잉크"),
        "GRANT 내역은 +100잉크로 표시해야 합니다.");
    assert(
        fixture.querySelector("[data-ink-ledger]").textContent.includes("-1잉크"),
        "DEDUCTION 내역은 -1잉크로 표시해야 합니다.");
    assert(
        fixture.querySelector("[data-ink-ledger]").textContent.includes("원본 12페이지"),
        "차감 내역에 원본 페이지 번호를 표시해야 합니다.");
    assert(
        fixture.querySelector("[data-ink-ledger]").textContent.includes("샘플 도서"),
        "차감 내역에 도서 제목을 표시해야 합니다.");
    assert(previousButton.disabled, "첫 페이지에서 이전 버튼을 비활성화해야 합니다.");
    assert(!nextButton.disabled, "다음 페이지가 있으면 다음 버튼을 활성화해야 합니다.");

    nextButton.click();
    await waitFor(
        () => fixture.querySelector("[data-ink-ledger-page]").textContent === "2 / 2페이지",
        "다음 잉크 내역 페이지를 표시해야 합니다.");
    assert(requestedLedgerPages.includes(2), "잉크 내역 2페이지를 API에 요청해야 합니다.");
    assert(!previousButton.disabled, "둘째 페이지에서 이전 버튼을 활성화해야 합니다.");
    assert(nextButton.disabled, "마지막 페이지에서 다음 버튼을 비활성화해야 합니다.");

    sdkMode = "failed";
    purchaseButton.click();
    await waitFor(
        () => paymentStatus.textContent.includes("결제 모듈을 불러오지 못했습니다"),
        "PortOne CDN 실패를 결제 영역에 표시해야 합니다.");
    assert(prepareCount === 0, "SDK를 불러오지 못하면 PENDING 구매를 생성하지 않아야 합니다.");
    assert(
        document.querySelector("[data-common-error]").hidden,
        "PortOne CDN 실패는 공통 오류 영역을 열면 안 됩니다.");
    assert(purchaseButton.disabled === false, "SDK 실패 뒤 구매를 다시 시도할 수 있어야 합니다.");

    sdkMode = "success";
    paymentMode = "interrupted";
    purchaseButton.click();
    await waitFor(
        () => retryButton.textContent === "결제창 다시 열기",
        "결제창 이탈 뒤 같은 결제창 재시도를 제공해야 합니다.");
    assert(prepareCount === 1, "결제 준비는 한 번만 생성해야 합니다.");
    assert(completeCount === 0, "결제창 이탈은 완료 API를 호출하면 안 됩니다.");
    assert(purchaseButton.disabled, "PENDING 결제가 있으면 새 구매를 막아야 합니다.");
    assert(lastPaymentRequest.payMethod === "CARD", "결제 수단은 카드로 고정해야 합니다.");
    assert(
        lastPaymentRequest.currency === "CURRENCY_KRW",
        "서버가 준비한 결제 통화를 그대로 사용해야 합니다.");

    paymentMode = "success";
    completeMode = "pending";
    retryButton.click();
    await waitFor(
        () => retryButton.textContent === "결제 결과 다시 확인",
        "PENDING 응답은 결과 재확인을 제공해야 합니다.");
    assert(prepareCount === 1, "결제창 재시도에서 새 구매를 준비하면 안 됩니다.");
    assert(completeCount === 1, "결제창 성공 뒤 완료 API를 호출해야 합니다.");
    assert(paymentStatus.textContent.includes("[PENDING]"), "PENDING 상태를 구분해 표시해야 합니다.");

    completeMode = "unavailable";
    retryButton.click();
    await waitFor(
        () => paymentStatus.textContent.includes("일시적인 문제가 있습니다"),
        "503 응답은 일시 장애로 안내해야 합니다.");
    assert(paymentStatus.textContent.includes("[PENDING]"), "503에서도 PENDING 유지를 표시해야 합니다.");
    assert(purchaseButton.disabled, "503 뒤에도 새 구매를 만들면 안 됩니다.");

    completeMode = "failed";
    retryButton.click();
    await waitFor(
        () => paymentStatus.textContent.includes("[FAILED]"),
        "422 응답은 FAILED 상태로 표시해야 합니다.");
    assert(purchaseButton.disabled === false, "FAILED 뒤에는 새 구매를 허용해야 합니다.");

    completeMode = "conflict";
    purchaseButton.click();
    await waitFor(
        () => paymentStatus.textContent.includes("결제 상태가 변경되어"),
        "이미 FAILED인 결제의 409 응답도 종료 상태로 안내해야 합니다.");
    assert(purchaseButton.disabled === false, "409 상태 충돌 뒤에는 새 구매를 허용해야 합니다.");

    completeMode = "paid";
    purchaseButton.click();
    await waitFor(
        () => paymentStatus.textContent.includes("[PAID]"),
        "검증된 결제 성공을 PAID 상태로 표시해야 합니다.");
    assert(
        fixture.querySelector("[data-ink-balance]").textContent === "200잉크",
        "PAID 뒤 현재 잉크 잔액을 다시 조회해야 합니다.");
    assert(
        requestedLedgerPages.at(-1) === 1,
        "PAID 뒤 원장 첫 페이지를 다시 조회해야 합니다.");
    assert(purchaseButton.disabled === false, "PAID 뒤 새 구매를 허용해야 합니다.");
}

function inkLedgerResponse(page) {
    if (page === 1) {
        return {
            entries: [
                {
                    type: "DEDUCTION",
                    amount: 1,
                    balanceAfter: 99,
                    bookTitle: "샘플 도서",
                    pageNumber: 12,
                    rentedAt: "2026-07-27T09:00:00Z",
                    expiresAt: "2026-08-26T09:00:00Z",
                    occurredAt: "2026-07-27T09:00:00Z"
                },
                {
                    type: "GRANT",
                    amount: 100,
                    balanceAfter: 100,
                    bookTitle: null,
                    pageNumber: null,
                    rentedAt: null,
                    expiresAt: null,
                    occurredAt: "2026-07-27T08:00:00Z"
                }
            ],
            page: 1,
            totalPages: 2,
            totalCount: 3
        };
    }
    return {
        entries: [{
            type: "GRANT",
            amount: 100,
            balanceAfter: 100,
            bookTitle: null,
            pageNumber: null,
            rentedAt: null,
            expiresAt: null,
            occurredAt: "2026-07-26T08:00:00Z"
        }],
        page,
        totalPages: 2,
        totalCount: 3
    };
}

async function assertApiError(action, code, status, requestId, message) {
    try {
        await action();
    } catch (error) {
        assert(error instanceof ApiRequestError, "ApiRequestError를 반환해야 합니다.");
        assert(error.code === code, `${code} 오류 코드를 보존해야 합니다.`);
        assert(error.status === status, `${code} HTTP 상태를 보존해야 합니다.`);
        assert(error.requestId === requestId, `${code} 요청 ID를 보존해야 합니다.`);
        assert(error.message === message, `${code} 사용자 메시지를 보존해야 합니다.`);
        return;
    }
    throw new Error(`${code} 오류가 발생해야 합니다.`);
}

async function waitFor(condition, failureMessage) {
    const deadline = Date.now() + 3000;
    while (Date.now() < deadline) {
        if (condition()) {
            return;
        }
        await new Promise((resolve) => setTimeout(resolve, 20));
    }
    throw new Error(failureMessage);
}

function assert(condition, message) {
    assertionCount += 1;
    if (!condition) {
        throw new Error(message);
    }
}
