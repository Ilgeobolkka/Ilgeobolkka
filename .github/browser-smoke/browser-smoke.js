import {clearCommonError, showCommonError} from "/js/common/error-display.js";
import {ApiRequestError, requestJson} from "/js/common/request-json.js";
import {
    createViewer,
    requestPageContent,
    VIEWER_SESSION_STORAGE_KEY
} from "/js/viewer/viewer-page.js";

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
        await verifyPageContentRequest();
    } finally {
        window.fetch = originalFetch;
    }

    verifySafeErrorDisplay();
    await verifyViewerFlow();
    await verifyViewerInitialPageAndRecovery();
    await verifyViewerRenderFailureStopsQueue();
    await verifyLogoutNavigation("success");
    await verifyLogoutNavigation("server-error");
    await verifyLogoutRetryableError("network-error");
    await verifyLogoutRetryableError("missing-csrf");
}

async function verifyPageContentRequest() {
    let capturedRequest;
    window.fetch = async (url, options) => {
        capturedRequest = {url, options};
        return new Response("페이지 본문", {
            status: 200,
            headers: {"Content-Type": "text/plain;charset=UTF-8"}
        });
    };

    const content = await requestPageContent(
        "/api/reading-sessions/current/pages/1/content",
        "viewer-session-id",
        "TEXT"
    );

    assert(content.contentType === "TEXT", "텍스트 콘텐츠 형식을 보존해야 합니다.");
    assert(content.body === "페이지 본문", "텍스트 콘텐츠 바디를 그대로 반환해야 합니다.");
    assert(
        capturedRequest.options.method === "GET",
        "페이지 콘텐츠는 GET으로 요청해야 합니다.");
    assert(
        capturedRequest.options.credentials === "same-origin",
        "페이지 콘텐츠는 same-origin 자격 증명만 전송해야 합니다.");
    assert(
        capturedRequest.options.headers.get("X-Viewer-Session-Id")
            === "viewer-session-id",
        "페이지 콘텐츠 요청에 뷰어 세션 헤더를 전달해야 합니다.");
    assert(
        !capturedRequest.options.headers.has("X-CSRF-TOKEN"),
        "페이지 콘텐츠 GET에는 CSRF 헤더를 추가하면 안 됩니다.");

    for (const mediaType of ["image/jpeg", "image/png"]) {
        window.fetch = async () => new Response(
            new Uint8Array([1, 2, 3]),
            {
                status: 200,
                headers: {"Content-Type": mediaType}
            }
        );
        const imageContent = await requestPageContent(
            "/api/reading-sessions/current/pages/2/content",
            "viewer-session-id",
            "IMAGE"
        );

        assert(
            imageContent.contentType === "IMAGE",
            `${mediaType} 콘텐츠 형식을 보존해야 합니다.`);
        assert(
            imageContent.body instanceof Blob && imageContent.body.type === mediaType,
            `${mediaType} 응답을 같은 형식의 Blob으로 반환해야 합니다.`);
    }

    window.fetch = async () => new Response("잘못된 이미지 응답", {
        status: 200,
        headers: {
            "Content-Type": "application/octet-stream",
            "X-Request-Id": "invalid-image-request"
        }
    });
    await assertApiError(
        () => requestPageContent(
            "/api/reading-sessions/current/pages/2/content",
            "viewer-session-id",
            "IMAGE"
        ),
        "INVALID_RESPONSE",
        200,
        "invalid-image-request",
        DEFAULT_ERROR_MESSAGE);

    window.fetch = async () => new Response(JSON.stringify({
        code: "VIEWER_SESSION_REPLACED",
        message: "새 뷰어로 교체된 열람 세션입니다."
    }), {
        status: 409,
        headers: {
            "Content-Type": "application/json",
            "X-Request-Id": "viewer-replaced-request"
        }
    });
    await assertApiError(
        () => requestPageContent(
            "/api/reading-sessions/current/pages/1/content",
            "viewer-session-id",
            "TEXT"
        ),
        "VIEWER_SESSION_REPLACED",
        409,
        "viewer-replaced-request",
        "새 뷰어로 교체된 열람 세션입니다.");
}

async function verifyViewerFlow() {
    const root = createViewerFixture();
    fixtureContainer.append(root);

    const storedValues = new Map();
    const storage = {
        getItem: (key) => storedValues.get(key) ?? null,
        setItem: (key, value) => storedValues.set(key, value),
        removeItem: (key) => storedValues.delete(key)
    };
    const metadataRequests = [];
    const contentRequests = [];
    let delayedPageTwo = true;
    let resolveDelayedPageTwo;

    const request = async (url, options = {}) => {
        metadataRequests.push({url, options});
        if (url === "/api/books/1") {
            return {
                bookId: 1,
                title: "브라우저 검증 도서",
                totalPageCount: 5
            };
        }

        const pageNumber = JSON.parse(options.body).pageNumber;
        if (pageNumber === 4) {
            throw new ApiRequestError(
                "INSUFFICIENT_INK",
                "잉크가 부족합니다. 잉크를 충전해 주세요.",
                422,
                "insufficient-ink-request"
            );
        }
        if (pageNumber === 5) {
            throw new ApiRequestError(
                "VIEWER_SESSION_REPLACED",
                "새 뷰어로 교체된 열람 세션입니다.",
                409,
                "viewer-replaced-request"
            );
        }

        return viewerMetadata(
            pageNumber,
            pageNumber === 3 ? "IMAGE" : "TEXT",
            pageNumber === 3
        );
    };

    const loadContent = async (url, viewerSessionId, contentType, options) => {
        const pageNumber = Number(url.match(/pages\/(\d+)\/content/)?.[1]);
        contentRequests.push({pageNumber, viewerSessionId, contentType, options});

        if (pageNumber === 2 && delayedPageTwo) {
            delayedPageTwo = false;
            return new Promise((resolve) => {
                resolveDelayedPageTwo = () => resolve({
                    contentType: "TEXT",
                    body: "늦게 도착한 2페이지"
                });
            });
        }
        if (contentType === "IMAGE") {
            return {
                contentType: "IMAGE",
                body: new Blob(["image"], {type: "image/png"})
            };
        }
        return {
            contentType: "TEXT",
            body: `${pageNumber}페이지 본문`
        };
    };

    const viewer = createViewer(root, {
        request,
        loadContent,
        storage,
        createObjectUrl: () => "data:image/png;base64,iVBORw0KGgo=",
        revokeObjectUrl: () => {}
    });

    await viewer.start();
    await viewer.whenIdle();

    const content = root.querySelector("[data-viewer-content]");
    const access = root.querySelector("[data-viewer-access]");
    const inkBalance = root.querySelector("[data-viewer-ink-balance]");
    assert(
        storedValues.get(VIEWER_SESSION_STORAGE_KEY) === "viewer-session-id",
        "viewerSessionId를 탭별 sessionStorage에 저장해야 합니다.");
    assert(
        content.textContent === "1페이지 본문",
        "첫 페이지 텍스트 콘텐츠를 표시해야 합니다.");
    assert(
        document.activeElement === content,
        "페이지를 표시한 뒤 콘텐츠 영역으로 포커스를 이동해야 합니다.");
    assert(
        access.textContent.includes("1잉크 사용")
            && access.textContent.includes("2026")
            && access.textContent.includes("까지 대여"),
        "신규 대여의 잉크 차감과 만료 시각을 표시해야 합니다.");
    assert(
        inkBalance.textContent === "남은 잉크 99",
        "페이지 열기 뒤 현재 잉크 잔액을 표시해야 합니다.");

    const requestCountBeforeTextSize =
        metadataRequests.length + contentRequests.length;
    root.querySelector("[data-viewer-text-larger]").click();
    assert(
        content.dataset.textSize === "large",
        "글자 크기 조작은 텍스트 표시 크기를 변경해야 합니다.");
    assert(
        metadataRequests.length + contentRequests.length === requestCountBeforeTextSize,
        "글자 크기 조작은 페이지 API를 다시 호출하면 안 됩니다.");

    viewer.requestPage(2);
    await waitFor(
        () => typeof resolveDelayedPageTwo === "function",
        "2페이지 지연 응답을 준비해야 합니다.");
    viewer.requestPage(3);
    resolveDelayedPageTwo();
    await viewer.whenIdle();

    assert(
        root.querySelector(".viewer-image")?.alt === "3페이지 이미지 콘텐츠",
        "새 요청 뒤 도착한 이전 콘텐츠가 현재 페이지를 덮어쓰면 안 됩니다.");
    assert(
        !content.textContent.includes("늦게 도착한 2페이지"),
        "지연된 이전 페이지 본문을 표시하면 안 됩니다.");
    assert(
        access.textContent === "온라인 소장 도서 · 잉크 차감과 대여 만료 없음",
        "온라인 소장 도서는 잉크 차감과 대여 만료가 없음을 표시해야 합니다.");
    assert(
        metadataRequests
            .filter((entry) => entry.options.method === "PATCH")
            .every((entry) =>
                entry.options.headers["X-Viewer-Session-Id"] === "viewer-session-id"),
        "모든 PATCH 요청에 뷰어 세션 헤더를 전달해야 합니다.");
    assert(
        contentRequests.every((entry) =>
            entry.viewerSessionId === "viewer-session-id"),
        "모든 콘텐츠 요청에 뷰어 세션 ID를 전달해야 합니다.");

    const requestCountBeforeZoom =
        metadataRequests.length + contentRequests.length;
    root.querySelector("[data-viewer-zoom-in]").click();
    assert(
        content.dataset.imageZoom === "125",
        "이미지 확대 조작은 표시 배율을 변경해야 합니다.");
    assert(
        metadataRequests.length + contentRequests.length === requestCountBeforeZoom,
        "이미지 확대는 페이지 API를 다시 호출하면 안 됩니다.");

    const requestCountBeforeControlArrow =
        metadataRequests.length + contentRequests.length;
    root.querySelector("[data-viewer-zoom-in]").dispatchEvent(new KeyboardEvent("keydown", {
        key: "ArrowLeft",
        bubbles: true
    }));
    await viewer.whenIdle();
    assert(
        metadataRequests.length + contentRequests.length === requestCountBeforeControlArrow,
        "뷰어 조작 버튼에서 화살표 키를 눌러도 페이지를 이동하면 안 됩니다.");

    content.dispatchEvent(new KeyboardEvent("keydown", {
        key: "ArrowLeft",
        bubbles: true
    }));
    await viewer.whenIdle();
    assert(
        content.textContent === "2페이지 본문",
        "왼쪽 화살표 키로 이전 페이지를 열어야 합니다.");
    assert(
        access.textContent.includes("대여 중")
            && access.textContent.includes("2026")
            && access.textContent.includes("까지"),
        "활성 대여의 재사용 상태와 만료 시각을 표시해야 합니다.");

    await viewer.requestPage(4);
    await viewer.whenIdle();
    assert(
        root.querySelector("[data-viewer-notice-message]")
            .textContent.includes("잉크가 부족"),
        "INSUFFICIENT_INK를 구분해 안내해야 합니다.");
    assert(
        root.querySelector("[data-viewer-ink-link]").hidden === false,
        "잉크 부족 안내에 충전 화면 링크를 제공해야 합니다.");
    assert(
        content.textContent === "2페이지 본문",
        "잉크 부족이면 현재 페이지 콘텐츠를 유지해야 합니다.");

    await viewer.requestPage(5);
    await viewer.whenIdle();
    assert(
        root.querySelector("[data-viewer-notice-message]")
            .textContent.includes("다른 탭"),
        "VIEWER_SESSION_REPLACED를 구분해 안내해야 합니다.");
    assert(
        content.childElementCount === 0,
        "교체된 뷰어는 기존 콘텐츠를 더 이상 표시하면 안 됩니다.");
    assert(
        root.querySelector("[data-viewer-next]").disabled,
        "교체된 뷰어의 페이지 이동을 중지해야 합니다.");
    assert(
        !storedValues.has(VIEWER_SESSION_STORAGE_KEY),
        "교체된 뷰어의 sessionStorage 값을 제거해야 합니다.");

    root.remove();
}

async function verifyViewerInitialPageAndRecovery() {
    const root = createViewerFixture(3);
    fixtureContainer.append(root);

    const requestedPages = [];
    const request = async (url, options = {}) => {
        if (url === "/api/books/1") {
            return {
                bookId: 1,
                title: "초기 페이지 검증 도서",
                totalPageCount: 5
            };
        }

        const pageNumber = JSON.parse(options.body).pageNumber;
        requestedPages.push({method: options.method, pageNumber});
        if (pageNumber === 3) {
            throw new ApiRequestError(
                "INSUFFICIENT_INK",
                "잉크가 부족합니다. 잉크를 충전해 주세요.",
                422,
                "initial-insufficient-ink-request"
            );
        }
        return viewerMetadata(pageNumber, "TEXT");
    };
    const viewer = createViewer(root, {
        request,
        loadContent: async () => ({
            contentType: "TEXT",
            body: "4페이지 활성 대여 본문"
        }),
        storage: {
            getItem: () => null,
            setItem: () => {},
            removeItem: () => {}
        }
    });

    await viewer.start();
    await viewer.whenIdle();

    const pageInput = root.querySelector("[data-viewer-page-input]");
    assert(
        requestedPages[0]?.method === "POST" && requestedPages[0]?.pageNumber === 3,
        "URL에서 선택한 초기 페이지만 첫 세션 생성 요청으로 보내야 합니다.");
    assert(
        pageInput.disabled === false,
        "초기 페이지 열기에 실패해도 다른 페이지 번호를 입력할 수 있어야 합니다.");

    pageInput.value = "";
    root.querySelector("[data-viewer-page-form]").requestSubmit();
    assert(
        pageInput.validationMessage.includes("1부터 5 사이"),
        "빈 페이지 번호를 제출하면 페이지 범위 오류를 표시해야 합니다.");

    pageInput.value = "4";
    pageInput.dispatchEvent(new Event("input", {bubbles: true}));
    assert(
        pageInput.validationMessage === "",
        "페이지 번호를 다시 입력하면 이전 사용자 지정 오류를 해제해야 합니다.");
    root.querySelector("[data-viewer-page-form]").requestSubmit();
    await viewer.whenIdle();

    assert(
        requestedPages[1]?.method === "POST" && requestedPages[1]?.pageNumber === 4,
        "세션 생성 전에는 새로 선택한 페이지로 세션 생성 요청을 다시 보내야 합니다.");
    assert(
        root.querySelector("[data-viewer-content]").textContent
            === "4페이지 활성 대여 본문",
        "잉크가 없어도 활성 대여 중인 다른 페이지를 표시할 수 있어야 합니다.");

    root.remove();
}

async function verifyViewerRenderFailureStopsQueue() {
    const root = createViewerFixture();
    fixtureContainer.append(root);

    let pageRequestCount = 0;
    let renderAttemptCount = 0;
    const request = async (url) => {
        if (url === "/api/books/1") {
            return {
                bookId: 1,
                title: "렌더링 오류 검증 도서",
                totalPageCount: 1
            };
        }

        pageRequestCount += 1;
        return viewerMetadata(1, "IMAGE", true);
    };
    const viewer = createViewer(root, {
        request,
        loadContent: async () => ({
            contentType: "IMAGE",
            body: new Blob(["image"], {type: "image/png"})
        }),
        storage: {
            getItem: () => null,
            setItem: () => {},
            removeItem: () => {}
        },
        createObjectUrl: () => {
            renderAttemptCount += 1;
            if (renderAttemptCount === 1) {
                throw new Error("강제 렌더링 오류");
            }
            return "data:image/png;base64,iVBORw0KGgo=";
        },
        revokeObjectUrl: () => {}
    });

    await viewer.start();
    await viewer.whenIdle();

    assert(pageRequestCount === 1, "렌더링 오류 뒤 페이지 요청을 자동 반복하면 안 됩니다.");
    assert(renderAttemptCount === 1, "렌더링 오류 뒤 렌더링을 자동 재시도하면 안 됩니다.");
    assert(
        document.querySelector("[data-common-error]").textContent
            === DEFAULT_ERROR_MESSAGE,
        "렌더링 오류는 안전한 공통 오류 메시지로 표시해야 합니다.");

    clearCommonError();
    root.remove();
}

function createViewerFixture(initialPage = 1) {
    const root = document.createElement("section");
    root.dataset.bookId = "1";
    root.dataset.initialPage = String(initialPage);
    root.dataset.viewerRoot = "";
    root.innerHTML = `
        <h1 data-viewer-title>도서 뷰어</h1>
        <p data-viewer-status></p>
        <div tabindex="-1" data-viewer-notice hidden>
            <span data-viewer-notice-message></span>
            <a href="/ink" data-viewer-ink-link hidden>잉크 충전하기</a>
        </div>
        <button type="button" data-viewer-previous disabled>이전</button>
        <form data-viewer-page-form>
            <input type="number" min="1" data-viewer-page-input disabled>
            <button type="submit" data-viewer-page-submit disabled>이동</button>
        </form>
        <span data-viewer-total-pages></span>
        <button type="button" data-viewer-next disabled>다음</button>
        <div data-viewer-text-controls hidden>
            <button type="button" data-viewer-text-smaller>작게</button>
            <button type="button" data-viewer-text-larger>크게</button>
        </div>
        <div data-viewer-image-controls hidden>
            <button type="button" data-viewer-zoom-out>축소</button>
            <button type="button" data-viewer-zoom-fit>너비 맞춤</button>
            <button type="button" data-viewer-zoom-in>확대</button>
        </div>
        <span data-viewer-access></span>
        <span data-viewer-ink-balance></span>
        <div tabindex="-1"
             data-text-size="medium"
             data-image-zoom="100"
             data-viewer-content></div>
    `;
    return root;
}

function viewerMetadata(pageNumber, contentType, owned = false) {
    return {
        viewerSessionId: "viewer-session-id",
        bookId: 1,
        pageNumber,
        owned,
        deductedInk: !owned && pageNumber === 1 ? 1 : 0,
        inkBalance: 99,
        rentedAt: owned ? null : "2026-07-31T06:00:00Z",
        expiresAt: owned ? null : "2026-08-30T06:00:00Z",
        contentType
    };
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
