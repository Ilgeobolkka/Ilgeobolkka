import {clearCommonError, showCommonError} from "/js/common/error-display.js";
import {initializeInkPage} from "/js/ink/ink-page.js";
import {createLibraryPage} from "/js/library/library-page.js";
import {
    codePointCount,
    createAiRouteGenerationPage,
    generationDisplayOf,
    generationRequestOf
} from "/js/ai-route/generation-page.js";
import {initializeBookDetailPage} from "/js/ownership/book-detail-page.js";
import {createOwnershipHistoryPage} from "/js/ownership/ownership-history-page.js";
import {createRouteDetailPage} from "/js/ai-route/route-detail-page.js";
import {requestPageContent} from "/js/common/request-page-content.js";
import {ApiRequestError, requestJson} from "/js/common/request-json.js";
import {VIEWER_SESSION_STORAGE_KEY} from "/js/common/viewer-session.js";
import {createViewer} from "/js/viewer/viewer-page.js";

const DEFAULT_ERROR_MESSAGE = "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.";
const AUTH_REQUEST_STORAGE_KEY = "browser-smoke-auth-request";
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
        await verifyRoutePageContentRequest();
        await verifyCatalogLatestRequestWins();
    } finally {
        window.fetch = originalFetch;
    }

    verifySafeErrorDisplay();
    await verifyAuthSuccess("signup", "/api/auth/signup", "/login");
    await verifyAuthSuccess("login", "/api/auth/login", "/books");
    await verifyAuthFailure();
    await verifyViewerFlow();
    await verifyViewerInitialPageAndRecovery();
    await verifyViewerInvalidInitialPage();
    await verifyViewerRenderFailureStopsQueue();
    await verifyRouteDetailFlow();
    await verifyRouteDetailPrerequisiteAndNavigation();
    await verifyRouteDetailImageAndReplacement();
    await verifyRouteDetailFailures();
    await verifyLogoutNavigation("success");
    await verifyLogoutNavigation("server-error");
    await verifyLogoutRetryableError("network-error");
    await verifyLogoutRetryableError("missing-csrf");
    await verifyLibraryPage();
    await verifyBookDetailPage();
    await verifyOwnershipHistoryPage();
    await verifyInkPage();
    await verifyAiRouteGenerationPage();
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

async function verifyRoutePageContentRequest() {
    await assertApiError(
        () => requestPageContent(
            "https://example.com/api/ai-routes/476/pages/42/content",
            "route-viewer-session-id",
            "TEXT",
            {method: "POST"}),
        "CROSS_ORIGIN_REQUEST",
        0,
        null,
        "같은 출처의 API만 호출할 수 있습니다.");

    let capturedRequest;
    window.fetch = async (url, options) => {
        capturedRequest = {url, options};
        return new Response("경로 페이지 본문", {
            status: 200,
            headers: {"Content-Type": "text/plain;charset=UTF-8"}
        });
    };

    const content = await requestPageContent(
        "/api/ai-routes/476/pages/42/content",
        "route-viewer-session-id",
        "TEXT",
        {method: "POST"}
    );

    assert(content.contentType === "TEXT", "경로 텍스트 콘텐츠 형식을 보존해야 합니다.");
    assert(content.body === "경로 페이지 본문", "경로 텍스트 콘텐츠 바디를 보존해야 합니다.");
    assert(capturedRequest.url.pathname === "/api/ai-routes/476/pages/42/content",
        "경로 콘텐츠 URL을 보존해야 합니다.");
    assert(capturedRequest.options.method === "POST", "경로 콘텐츠는 POST로 요청해야 합니다.");
    assert(capturedRequest.options.credentials === "same-origin",
        "경로 콘텐츠는 same-origin 자격 증명만 전송해야 합니다.");
    assert(capturedRequest.options.headers.get("X-CSRF-TOKEN") === "browser-smoke-token",
        "경로 콘텐츠 요청에 페이지의 CSRF 토큰을 추가해야 합니다.");
    assert(capturedRequest.options.headers.get("X-Viewer-Session-Id")
        === "route-viewer-session-id",
        "경로 콘텐츠 요청에 뷰어 세션 헤더를 추가해야 합니다.");

    for (const mediaType of ["image/jpeg", "image/png"]) {
        window.fetch = async () => new Response(
            new Uint8Array([1, 2, 3]),
            {
                status: 200,
                headers: {"Content-Type": mediaType}
            }
        );
        const imageContent = await requestPageContent(
            "/api/ai-routes/476/pages/42/content",
            "route-viewer-session-id",
            "IMAGE",
            {method: "POST"}
        );

        assert(imageContent.contentType === "IMAGE",
            `${mediaType} 경로 콘텐츠 형식을 보존해야 합니다.`);
        assert(imageContent.body instanceof Blob && imageContent.body.type === mediaType,
            `${mediaType} 경로 응답을 같은 형식의 Blob으로 반환해야 합니다.`);
    }

    window.fetch = async () => new Response("잘못된 경로 이미지 응답", {
        status: 200,
        headers: {
            "Content-Type": "application/octet-stream",
            "X-Request-Id": "invalid-route-image-request"
        }
    });
    await assertApiError(
        () => requestPageContent(
            "/api/ai-routes/476/pages/42/content",
            "route-viewer-session-id",
            "IMAGE",
            {method: "POST"}),
        "INVALID_RESPONSE",
        200,
        "invalid-route-image-request",
        DEFAULT_ERROR_MESSAGE);

    window.fetch = async () => new Response(JSON.stringify({
        code: "VIEWER_SESSION_REPLACED",
        message: "새 뷰어로 교체된 열람 세션입니다."
    }), {
        status: 409,
        headers: {
            "Content-Type": "application/json",
            "X-Request-Id": "route-viewer-replaced-request"
        }
    });
    await assertApiError(
        () => requestPageContent(
            "/api/ai-routes/476/pages/42/content",
            "route-viewer-session-id",
            "TEXT",
            {method: "POST"}),
        "VIEWER_SESSION_REPLACED",
        409,
        "route-viewer-replaced-request",
        "새 뷰어로 교체된 열람 세션입니다.");

    const tokenMeta = document.querySelector("meta[name='_csrf']");
    const token = tokenMeta.content;
    let fetchCalled = false;
    tokenMeta.removeAttribute("content");
    window.fetch = async () => {
        fetchCalled = true;
        return new Response("전송되면 안 되는 콘텐츠", {
            status: 200,
            headers: {"Content-Type": "text/plain"}
        });
    };
    try {
        await assertApiError(
            () => requestPageContent(
                "/api/ai-routes/476/pages/42/content",
                "route-viewer-session-id",
                "TEXT",
                {method: "POST"}),
            "MISSING_CSRF_TOKEN",
            0,
            null,
            "보안 토큰을 찾을 수 없습니다. 페이지를 새로고침해 주세요.");
        assert(!fetchCalled, "CSRF 토큰이 없으면 경로 콘텐츠 요청을 전송하지 않아야 합니다.");
    } finally {
        tokenMeta.content = token;
    }
}

async function verifyCatalogLatestRequestWins() {
    const root = createCatalogFixture();
    fixtureContainer.append(root);
    const originalPath = `${window.location.pathname}${window.location.search}${window.location.hash}`;
    let resolvePreviousSearch;
    let previousResponseConsumed = false;
    let rejectPreviousSearch;
    let previousErrorCompleted = false;

    window.fetch = async (url) => {
        const keyword = url.searchParams.get("keyword");
        if (keyword === "이전 검색") {
            return new Promise((resolve) => {
                resolvePreviousSearch = () => resolve({
                    ok: true,
                    status: 200,
                    headers: new Headers(),
                    json: async () => {
                        previousResponseConsumed = true;
                        return catalogResponse("이전 검색 도서", 4);
                    }
                });
            });
        }
        if (keyword === "이전 오류") {
            return new Promise((resolve, reject) => {
                rejectPreviousSearch = () => {
                    previousErrorCompleted = true;
                    reject(new TypeError("강제 지연 오류"));
                };
            });
        }
        if (keyword === "최신 검색" || keyword === "오류 뒤 최신") {
            const title = keyword === "최신 검색" ? "최신 검색 도서" : "오류 뒤 최신 도서";
            const totalPages = keyword === "최신 검색" ? 1 : 2;
            const page = Number(url.searchParams.get("page"));
            return new Response(JSON.stringify(catalogResponse(title, totalPages, page)), {
                status: 200,
                headers: {"Content-Type": "application/json"}
            });
        }
        return new Response(JSON.stringify(catalogResponse("초기 도서", 1)), {
            status: 200,
            headers: {"Content-Type": "application/json"}
        });
    };

    try {
        await import(`/js/book/catalog.js?browser-smoke=${Date.now()}`);
        await waitFor(
            () => root.querySelector("[data-book-title]")?.textContent === "초기 도서",
            "초기 도서 목록을 표시해야 합니다.");

        const form = root.querySelector("[data-book-search-form]");
        const search = root.querySelector("[data-book-search]");
        search.value = "이전 검색";
        form.requestSubmit();
        await waitFor(
            () => typeof resolvePreviousSearch === "function",
            "이전 검색 응답을 지연할 수 있어야 합니다.");

        search.value = "최신 검색";
        form.requestSubmit();
        await waitFor(
            () => root.querySelector("[data-book-title]")?.textContent === "최신 검색 도서",
            "최신 검색 결과를 먼저 표시해야 합니다.");

        resolvePreviousSearch();
        await waitFor(
            () => previousResponseConsumed,
            "지연된 이전 검색 응답이 완료되어야 합니다.");
        await new Promise((resolve) => setTimeout(resolve, 0));

        const currentUrl = new URL(window.location.href);
        assert(
            root.querySelector("[data-book-title]")?.textContent === "최신 검색 도서",
            "지연된 이전 응답이 최신 검색 결과를 덮어쓰면 안 됩니다.");
        assert(
            root.querySelector("[data-book-page]").textContent === "1페이지 / 전체 1페이지"
                && root.querySelector("[data-book-next]").disabled,
            "지연된 이전 응답이 최신 페이지 상태를 덮어쓰면 안 됩니다.");
        assert(
            currentUrl.pathname === "/books"
                && currentUrl.searchParams.get("page") === "1"
                && currentUrl.searchParams.get("keyword") === "최신 검색",
            "지연된 이전 응답이 최신 검색 URL을 덮어쓰면 안 됩니다.");

        search.value = "이전 오류";
        form.requestSubmit();
        await waitFor(
            () => typeof rejectPreviousSearch === "function",
            "이전 검색 오류를 지연할 수 있어야 합니다.");

        search.value = "오류 뒤 최신";
        form.requestSubmit();
        await waitFor(
            () => root.querySelector("[data-book-title]")?.textContent === "오류 뒤 최신 도서",
            "이전 요청 오류보다 최신 검색 결과를 먼저 표시해야 합니다.");

        rejectPreviousSearch();
        await waitFor(
            () => previousErrorCompleted,
            "지연된 이전 검색 오류가 완료되어야 합니다.");
        await new Promise((resolve) => setTimeout(resolve, 0));

        const urlAfterPreviousError = new URL(window.location.href);
        assert(
            root.querySelector("[data-book-title]")?.textContent === "오류 뒤 최신 도서"
                && root.querySelector("[data-book-page]").textContent === "1페이지 / 전체 2페이지"
                && !root.querySelector("[data-book-next]").disabled,
            "지연된 이전 오류가 최신 검색 결과와 페이지 상태를 지우면 안 됩니다.");
        assert(
            document.querySelector("[data-common-error]").hidden,
            "지연된 이전 오류를 현재 검색의 오류로 표시하면 안 됩니다.");
        assert(
            urlAfterPreviousError.searchParams.get("keyword") === "오류 뒤 최신",
            "지연된 이전 오류가 최신 검색 URL을 바꾸면 안 됩니다.");

        root.querySelector("[data-book-next]").click();
        await waitFor(
            () => root.querySelector("[data-book-page]").textContent === "2페이지 / 전체 2페이지",
            "다음 버튼은 두 번째 페이지를 표시해야 합니다.");
        assert(
            new URL(window.location.href).searchParams.get("page") === "2",
            "다음 페이지 이동은 브라우저 기록에 두 번째 페이지를 추가해야 합니다.");

        window.history.back();
        await waitFor(
            () => new URL(window.location.href).searchParams.get("page") === "1"
                && root.querySelector("[data-book-page]").textContent === "1페이지 / 전체 2페이지",
            "뒤로 가기는 이전 도서 목록 페이지를 복원해야 합니다.");
        assert(
            search.value === "오류 뒤 최신",
            "뒤로 가기는 URL의 검색어를 검색 입력에 복원해야 합니다.");

        window.history.forward();
        await waitFor(
            () => new URL(window.location.href).searchParams.get("page") === "2"
                && root.querySelector("[data-book-page]").textContent === "2페이지 / 전체 2페이지",
            "앞으로 가기는 다음 도서 목록 페이지를 복원해야 합니다.");
    } finally {
        root.remove();
        window.history.replaceState(null, "", originalPath);
    }
}

function createCatalogFixture() {
    const root = document.createElement("section");
    root.dataset.bookListRoot = "";
    root.innerHTML = `
        <form data-book-search-form>
            <input type="search" data-book-search>
            <button type="submit">검색</button>
        </form>
        <p data-book-status></p>
        <div data-book-list></div>
        <section data-book-empty hidden>
            <h2 data-book-empty-title></h2>
            <p data-book-empty-description></p>
        </section>
        <button type="button" data-book-previous disabled>이전</button>
        <span data-book-page></span>
        <button type="button" data-book-next disabled>다음</button>
        <template data-book-card-template>
            <article>
                <img data-book-cover alt="">
                <div data-book-cover-placeholder hidden>표지 없음</div>
                <span data-book-category></span>
                <a data-book-title></a>
                <span data-book-author></span>
                <span data-book-price></span>
            </article>
        </template>
    `;
    return root;
}

function catalogResponse(title, totalPages, page = 1) {
    return {
        books: [{
            bookId: totalPages,
            category: "소설",
            title,
            author: "테스트 저자",
            bookPrice: 10000,
            coverImagePath: null
        }],
        page,
        totalPages,
        totalCount: totalPages
    };
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

    const accessBeforeInsufficientInk = access.textContent;
    const balanceBeforeInsufficientInk = inkBalance.textContent;
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
    assert(
        access.textContent === accessBeforeInsufficientInk,
        "잉크 부족이면 현재 페이지의 열람 권한 안내를 유지해야 합니다.");
    assert(
        inkBalance.textContent === balanceBeforeInsufficientInk,
        "잉크 부족이면 현재 페이지의 잉크 잔액 안내를 유지해야 합니다.");

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
        return {
            ...viewerMetadata(pageNumber, "TEXT"),
            inkBalance: 0
        };
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
    const notice = root.querySelector("[data-viewer-notice]");
    assert(
        requestedPages[0]?.method === "POST" && requestedPages[0]?.pageNumber === 3,
        "URL에서 선택한 초기 페이지만 첫 세션 생성 요청으로 보내야 합니다.");
    assert(
        root.querySelector("[data-viewer-access]").textContent === "",
        "초기 잉크 부족 응답 뒤 열람 권한 확인 문구를 제거해야 합니다.");
    assert(
        root.querySelector("[data-viewer-notice-message]")
            .textContent.includes("잉크가 부족"),
        "초기 잉크 부족을 경고 안내로 표시해야 합니다.");
    assert(
        root.querySelector("[data-viewer-ink-link]").hidden === false,
        "초기 잉크 부족 안내에 충전 화면 링크를 제공해야 합니다.");
    assert(
        document.activeElement === notice,
        "초기 잉크 부족 안내 영역으로 포커스를 이동해야 합니다.");
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
    assert(
        root.querySelector("[data-viewer-access]").textContent.includes("대여 중"),
        "잉크 부족 후 복구한 페이지의 활성 대여 상태를 표시해야 합니다.");
    assert(
        root.querySelector("[data-viewer-ink-balance]").textContent === "남은 잉크 0",
        "잉크 부족 후 복구한 페이지에 Reader B의 0잉크 잔액을 표시해야 합니다.");
    assert(
        root.querySelector("[data-viewer-notice]").hidden,
        "페이지 복구 후 이전 잉크 부족 안내를 숨겨야 합니다.");
    assert(
        root.querySelector("[data-viewer-ink-link]").hidden,
        "페이지 복구 후 이전 충전 화면 링크를 숨겨야 합니다.");
    assert(
        document.activeElement === root.querySelector("[data-viewer-content]"),
        "페이지 복구 후 콘텐츠 영역으로 포커스를 이동해야 합니다.");

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
    assert(
        root.querySelector("[data-viewer-access]").textContent === "",
        "초기 페이지 오류 후 열람 권한 확인 문구를 제거해야 합니다.");

    clearCommonError();
    root.remove();
}

async function verifyViewerInvalidInitialPage() {
    const root = createViewerFixture(6);
    fixtureContainer.append(root);

    let pageRequestCount = 0;
    const viewer = createViewer(root, {
        request: async (url) => {
            if (url === "/api/books/1") {
                return {
                    bookId: 1,
                    title: "페이지 범위 검증 도서",
                    totalPageCount: 5
                };
            }
            pageRequestCount += 1;
            throw new Error("범위 밖 페이지는 요청하면 안 됩니다.");
        }
    });

    await viewer.start();
    await viewer.whenIdle();

    assert(pageRequestCount === 0, "범위 밖 초기 페이지는 API에 요청하면 안 됩니다.");
    assert(
        root.querySelector("[data-viewer-status]").textContent
            === "페이지 번호를 확인해 주세요.",
        "범위 밖 초기 페이지 번호를 안내해야 합니다.");
    assert(
        root.querySelector("[data-viewer-access]").textContent === "",
        "범위 밖 초기 페이지에서 열람 권한 확인 문구를 제거해야 합니다.");
    assert(
        document.activeElement === root.querySelector("[data-viewer-page-input]"),
        "범위 밖 초기 페이지에서 페이지 입력으로 포커스를 이동해야 합니다.");

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
        <div role="alert" tabindex="-1" data-viewer-notice hidden>
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
        <span data-viewer-access>열람 권한을 확인하고 있습니다.</span>
        <span data-viewer-ink-balance></span>
        <div tabindex="-1"
             data-text-size="medium"
             data-image-zoom="100"
             data-viewer-content></div>
    `;
    return root;
}

async function verifyRouteDetailFlow() {
    const root = createRouteDetailFixture();
    fixtureContainer.append(root);

    const storedValues = new Map();
    const openedPages = new Set();
    const events = [];
    let current = false;
    let navigatedPath = null;
    const request = async (url, options = {}) => {
        const method = options.method || "GET";
        events.push({type: "json", url, method, options});

        if (url === "/api/books/15/reading-sessions") {
            return routeOpenMetadata(42, false);
        }
        if (url === "/api/reading-sessions/current/page") {
            return routeOpenMetadata(JSON.parse(options.body).pageNumber, true);
        }
        if (url === "/api/ai-routes/476" && method === "GET") {
            return routeSnapshot(openedPages, current);
        }
        if (url === "/api/books/15/ai-routes/current") {
            current = true;
            return routeSnapshot(openedPages, current);
        }
        if (url === "/api/ai-routes/476/feedback") {
            return {routeId: 476, rating: JSON.parse(options.body).rating};
        }
        if (url === "/api/ai-routes/476" && method === "DELETE") {
            return null;
        }
        throw new Error(`예상하지 않은 경로 상세 요청: ${method} ${url}`);
    };
    const loadContent = async (url, viewerSessionId, contentType, options = {}) => {
        const pageNumber = Number(url.match(/pages\/(\d+)\/content$/)?.[1]);
        events.push({type: "content", url, viewerSessionId, contentType, options});
        openedPages.add(pageNumber);
        return {contentType: "TEXT", body: `${pageNumber}페이지 경로 본문`};
    };
    const page = createRouteDetailPage(root, {
        request,
        loadContent,
        storage: {
            getItem: (key) => storedValues.get(key) || null,
            setItem: (key, value) => storedValues.set(key, value),
            removeItem: (key) => storedValues.delete(key)
        },
        confirm: () => true,
        navigate: (url) => {
            navigatedPath = url;
        }
    });

    page.start();
    assert(root.querySelector("[data-route-next]").textContent === "다음 경로 페이지",
        "경로를 열기 전에는 비활성 다음 버튼에 첫 페이지 비용을 표시하면 안 됩니다.");
    await page.openItem(0);

    const items = root.querySelectorAll("[data-route-position]");
    assert(events[0]?.url === "/api/books/15/reading-sessions"
        && events[0]?.method === "POST",
        "첫 경로 페이지는 새 열람 세션 POST로 열어야 합니다.");
    assert(events[1]?.type === "content"
        && events[1]?.viewerSessionId === "route-viewer-session-id"
        && events[1]?.options.method === "POST",
        "페이지 열기 성공 뒤 같은 뷰어 세션과 POST로 경로 콘텐츠를 요청해야 합니다.");
    assert(events[2]?.url === "/api/ai-routes/476" && events[2]?.method === "GET",
        "콘텐츠 성공 뒤 경로 상세 상태를 새로고침해야 합니다.");
    assert(storedValues.get(VIEWER_SESSION_STORAGE_KEY) === "route-viewer-session-id",
        "열기 성공의 뷰어 세션을 탭 sessionStorage에 저장해야 합니다.");
    assert(items[0].dataset.opened === "true"
        && items[0].querySelector("[data-item-opened-badge]").hidden === false,
        "콘텐츠 제공에 성공한 경로 항목만 완료로 표시해야 합니다.");
    assert(items[1].dataset.opened === "false",
        "아직 콘텐츠를 제공하지 않은 경로 항목은 완료로 표시하면 안 됩니다.");
    assert(root.querySelector("[data-route-content]").textContent === "42페이지 경로 본문",
        "경로 콘텐츠 성공 응답을 실제 DOM에 표시해야 합니다.");
    assert(root.querySelector("[data-route-progress]").textContent === "1 / 2 완료",
        "첫 콘텐츠 성공 뒤 서버 진행 상태를 표시해야 합니다.");

    await page.openItem(1);

    const patchEvent = events.find(event =>
        event.url === "/api/reading-sessions/current/page");
    assert(patchEvent?.method === "PATCH"
        && patchEvent.options.headers["X-Viewer-Session-Id"] === "route-viewer-session-id"
        && JSON.parse(patchEvent.options.body).pageNumber === 3,
        "다음 경로 페이지는 현재 세션 PATCH와 뷰어 세션 헤더로 열어야 합니다.");
    assert(root.querySelector("[data-route-progress]").textContent === "2 / 2 완료",
        "모든 콘텐츠 성공 뒤 전체 진행을 완료로 표시해야 합니다.");
    assert(root.querySelector("[data-completed-badge]").hidden === false,
        "서버가 완료한 경로의 완료 배지를 표시해야 합니다.");
    assert(!root.querySelector("[data-completed-time]").textContent.includes("T"),
        "동적으로 갱신한 완료 시각도 ISO 원문이 아닌 읽기 쉬운 형식이어야 합니다.");
    assert([...root.querySelectorAll("[data-feedback-rating]")]
        .every(button => button.disabled === false),
        "경로 완료 뒤 세 피드백 버튼을 활성화해야 합니다.");
    assert(root.querySelector("[data-route-content]").textContent === "3페이지 경로 본문",
        "다음 추천 위치의 원본 페이지 콘텐츠를 표시해야 합니다.");

    root.querySelector("[data-make-current]").click();
    await waitFor(
        () => root.querySelector("[data-current-badge]").hidden === false,
        "현재 경로 지정 성공 상태를 표시해야 합니다.");
    assert(events.some(event => event.url === "/api/books/15/ai-routes/current"
        && event.method === "PUT"),
        "현재 경로 지정은 S03 PUT을 호출해야 합니다.");

    root.querySelector("[data-feedback-rating='HELPFUL']").click();
    await waitFor(
        () => root.querySelector("[data-feedback-rating='HELPFUL']")
            .getAttribute("aria-pressed") === "true",
        "완료 경로 피드백 성공 상태를 표시해야 합니다.");
    assert(events.some(event => event.url === "/api/ai-routes/476/feedback"
        && event.method === "PUT"),
        "완료 경로 피드백은 S05 PUT을 호출해야 합니다.");

    root.querySelector("[data-delete-route]").click();
    await waitFor(() => navigatedPath === "/library",
        "경로 삭제 성공 뒤 내 서재로 이동해야 합니다.");
    assert(events.some(event => event.url === "/api/ai-routes/476"
        && event.method === "DELETE"),
        "경로 삭제는 S03 DELETE를 호출해야 합니다.");

    root.remove();
}

async function verifyRouteDetailPrerequisiteAndNavigation() {
    const root = createRouteDetailFixture();
    fixtureContainer.append(root);

    const openedPages = new Set();
    const requestedPages = [];
    const confirmationResults = [false, true];
    const confirmationMessages = [];
    const request = async (url, options = {}) => {
        const method = options.method || "GET";
        if (url === "/api/books/15/reading-sessions"
                || url === "/api/reading-sessions/current/page") {
            const pageNumber = JSON.parse(options.body).pageNumber;
            requestedPages.push({method, pageNumber});
            return routeOpenMetadata(pageNumber, pageNumber === 3);
        }
        if (url === "/api/ai-routes/476" && method === "GET") {
            return routeSnapshot(openedPages, false);
        }
        throw new Error(`예상하지 않은 선수 개념 경로 요청: ${method} ${url}`);
    };
    const page = createRouteDetailPage(root, {
        request,
        loadContent: async (url) => {
            const pageNumber = Number(url.match(/pages\/(\d+)\/content$/)?.[1]);
            openedPages.add(pageNumber);
            return {contentType: "TEXT", body: `${pageNumber}페이지 경로 본문`};
        },
        storage: routeStorage(),
        confirm: (message) => {
            confirmationMessages.push(message);
            return confirmationResults.shift();
        }
    });

    page.start();
    await page.openItem(1);

    assert(confirmationMessages.length === 1
        && confirmationMessages[0].includes("읽지 않은 선수 개념 페이지"),
        "선수 개념을 건너뛰면 계속할지 확인해야 합니다.");
    assert(requestedPages.length === 0 && openedPages.size === 0,
        "선수 개념 건너뛰기를 취소하면 페이지를 열면 안 됩니다.");

    await page.openItem(1);

    assert(confirmationMessages.length === 2,
        "선수 개념 건너뛰기를 다시 시도하면 확인창을 다시 표시해야 합니다.");
    assert(requestedPages[0]?.method === "POST" && requestedPages[0]?.pageNumber === 3,
        "선수 개념 건너뛰기를 확인하면 선택한 두 번째 항목을 열어야 합니다.");
    assert(root.querySelector("[data-route-content]").textContent === "3페이지 경로 본문",
        "건너뛰기 확인 뒤 두 번째 경로 항목의 콘텐츠를 표시해야 합니다.");

    const previous = root.querySelector("[data-route-previous]");
    const next = root.querySelector("[data-route-next]");
    assert(previous.disabled === false && next.disabled === true,
        "두 번째 경로 항목에서는 이전 이동만 활성화해야 합니다.");

    previous.click();
    await waitFor(
        () => root.querySelector("[data-route-content]").textContent === "42페이지 경로 본문"
            && next.disabled === false,
        "이전 경로 버튼으로 첫 번째 항목을 열어야 합니다.");
    assert(requestedPages[1]?.method === "PATCH" && requestedPages[1]?.pageNumber === 42,
        "이전 경로 버튼은 현재 세션을 첫 번째 항목의 원본 페이지로 이동해야 합니다.");

    next.click();
    await waitFor(
        () => root.querySelector("[data-route-content]").textContent === "3페이지 경로 본문"
            && requestedPages.length === 3,
        "다음 경로 버튼으로 두 번째 항목을 다시 열어야 합니다.");
    assert(requestedPages[2]?.method === "PATCH" && requestedPages[2]?.pageNumber === 3,
        "다음 경로 버튼은 현재 세션을 두 번째 항목의 원본 페이지로 이동해야 합니다.");
    assert(confirmationMessages.length === 2,
        "선수 개념을 연 뒤 다음 이동에서는 건너뛰기 확인창을 다시 표시하면 안 됩니다.");

    root.remove();
}

async function verifyRouteDetailImageAndReplacement() {
    const root = createRouteDetailFixture();
    fixtureContainer.append(root);

    const storedValues = new Map();
    const openedPages = new Set();
    let createdBlob = null;
    let revokedUrl = null;
    const page = createRouteDetailPage(root, {
        request: async (url, options = {}) => {
            if (url === "/api/books/15/reading-sessions") {
                return routeOpenMetadata(42, false, "IMAGE");
            }
            if (url === "/api/ai-routes/476" && (options.method || "GET") === "GET") {
                return routeSnapshot(openedPages, false);
            }
            if (url === "/api/reading-sessions/current/page") {
                return routeOpenMetadata(3, false);
            }
            throw new Error(`예상하지 않은 이미지 경로 요청: ${url}`);
        },
        loadContent: async (url, viewerSessionId, contentType) => {
            if (url === "/api/ai-routes/476/pages/3/content") {
                assert(viewerSessionId === "route-viewer-session-id"
                    && contentType === "TEXT",
                "교체 직전 콘텐츠 요청에도 현재 세션과 응답 형식을 전달해야 합니다.");
                throw new ApiRequestError(
                    "VIEWER_SESSION_REPLACED",
                    "새 뷰어로 교체된 열람 세션입니다.",
                    409,
                    "route-viewer-replaced");
            }
            assert(url === "/api/ai-routes/476/pages/42/content",
                "이미지 경로 항목의 원본 페이지 콘텐츠를 요청해야 합니다.");
            assert(viewerSessionId === "route-viewer-session-id" && contentType === "IMAGE",
                "이미지 경로 콘텐츠 요청에 세션과 콘텐츠 형식을 전달해야 합니다.");
            openedPages.add(42);
            return {
                contentType: "IMAGE",
                body: new Blob([new Uint8Array([1, 2, 3])], {type: "image/png"})
            };
        },
        storage: routeStorage(storedValues),
        confirm: () => true,
        createObjectUrl: (blob) => {
            createdBlob = blob;
            return "blob:route-image";
        },
        revokeObjectUrl: (url) => {
            revokedUrl = url;
        }
    });

    page.start();
    await page.openItem(0);

    const image = root.querySelector(".viewer-image");
    assert(createdBlob instanceof Blob && createdBlob.type === "image/png",
        "경로 이미지 Blob으로 Object URL을 만들어야 합니다.");
    assert(image?.alt === "42페이지 이미지 콘텐츠",
        "경로 이미지에 원본 페이지 번호를 설명하는 대체 텍스트를 표시해야 합니다.");
    assert(storedValues.get(VIEWER_SESSION_STORAGE_KEY) === "route-viewer-session-id",
        "이미지 경로를 연 뷰어 세션을 저장해야 합니다.");

    await page.openItem(1);

    assert(revokedUrl === "blob:route-image",
        "뷰어 세션이 교체되면 표시하던 이미지 Object URL을 해제해야 합니다.");
    assert(root.querySelector(".viewer-image") === null
        && root.querySelector("[data-route-content]").textContent
            .includes("다른 탭에서 새 뷰어가 열려"),
        "교체된 세션의 이미지를 제거하고 종료 안내를 표시해야 합니다.");
    assert(!storedValues.has(VIEWER_SESSION_STORAGE_KEY),
        "교체된 뷰어 세션 ID를 sessionStorage에서 제거해야 합니다.");
    assert([...root.querySelectorAll("[data-open-route-item]")]
        .every(button => button.disabled),
        "교체된 세션에서는 경로 페이지를 더 열 수 없도록 막아야 합니다.");

    root.remove();
}

async function verifyRouteDetailFailures() {
    const openFailureRoot = createRouteDetailFixture();
    fixtureContainer.append(openFailureRoot);
    let contentRequestCount = 0;
    const openFailurePage = createRouteDetailPage(openFailureRoot, {
        request: async () => {
            throw new ApiRequestError(
                "INSUFFICIENT_INK",
                "잉크가 부족합니다.",
                422,
                "route-open-failure");
        },
        loadContent: async () => {
            contentRequestCount += 1;
            throw new Error("열기 실패 뒤 콘텐츠를 요청하면 안 됩니다.");
        },
        storage: routeStorage()
    });

    openFailurePage.start();
    await openFailurePage.openItem(0);

    const openFailureItem = openFailureRoot.querySelector("[data-route-position='1']");
    assert(contentRequestCount === 0,
        "페이지 열기 API가 실패하면 경로 콘텐츠를 요청하면 안 됩니다.");
    assert(openFailureItem.dataset.opened === "false"
        && openFailureItem.querySelector("[data-item-opened-badge]").hidden,
        "페이지 열기 실패를 경로 항목 완료로 표시하면 안 됩니다.");
    assert(openFailureRoot.querySelector("[data-route-progress]").textContent === "0 / 2 완료",
        "페이지 열기 실패 뒤 진행 상태를 유지해야 합니다.");
    assert(openFailureRoot.querySelector("[data-route-ink-notice]").hidden === false
        && openFailureRoot.querySelector("[data-route-ink-link]").hidden === false,
        "잉크 부족으로 열지 못하면 충전 안내를 표시해야 합니다.");
    openFailureRoot.remove();

    const contentFailureRoot = createRouteDetailFixture();
    fixtureContainer.append(contentFailureRoot);
    let jsonRequestCount = 0;
    const storedValues = new Map();
    const contentFailurePage = createRouteDetailPage(contentFailureRoot, {
        request: async () => {
            jsonRequestCount += 1;
            return routeOpenMetadata(42, false);
        },
        loadContent: async () => {
            throw new ApiRequestError(
                "NETWORK_ERROR",
                "강제 콘텐츠 오류",
                0,
                null);
        },
        storage: routeStorage(storedValues)
    });

    contentFailurePage.start();
    await contentFailurePage.openItem(0);

    const contentFailureItem = contentFailureRoot.querySelector("[data-route-position='1']");
    assert(jsonRequestCount === 1,
        "콘텐츠 실패 뒤 경로 상세 새로고침을 성공으로 처리하면 안 됩니다.");
    assert(contentFailureItem.dataset.costStatus === "ACTIVE_RENTAL"
        && contentFailureItem.querySelector("[data-open-route-item]").textContent
            === "대여 중 · 열기",
        "열기 성공은 뒤이은 콘텐츠 실패와 무관하게 현재 비용 상태를 반영해야 합니다.");
    assert(contentFailureItem.dataset.opened === "false"
        && contentFailureItem.querySelector("[data-item-opened-badge]").hidden,
        "콘텐츠 제공 실패를 경로 항목 완료로 표시하면 안 됩니다.");
    assert(contentFailureRoot.querySelector("[data-route-content]").textContent
        .includes("경로 페이지를 열면"),
        "콘텐츠 제공 실패 시 성공 콘텐츠로 교체하면 안 됩니다.");
    assert(storedValues.get(VIEWER_SESSION_STORAGE_KEY) === "route-viewer-session-id",
        "열기 성공의 세션은 콘텐츠 실패와 무관하게 저장해야 합니다.");
    assert(document.querySelector("[data-common-error]").textContent === "강제 콘텐츠 오류",
        "콘텐츠 실패를 안전한 공통 오류 영역에 표시해야 합니다.");

    clearCommonError();
    contentFailureRoot.remove();
}

function createRouteDetailFixture() {
    const root = document.createElement("section");
    root.dataset.aiRouteDetailRoot = "";
    root.dataset.routeId = "476";
    root.dataset.bookId = "15";
    root.dataset.routeCurrent = "false";
    root.dataset.routeCompleted = "false";
    root.dataset.routeCompletedAt = "";
    root.dataset.routeRating = "";
    root.innerHTML = `
        <span data-current-badge hidden>현재 경로</span>
        <span data-completed-badge hidden>경로 열람 완료</span>
        <time data-completed-time hidden></time>
        <button type="button" data-make-current>현재 경로로 지정</button>
        <button type="button" data-delete-route>경로 삭제</button>
        <span data-route-progress></span>
        <ol>
            ${routeItemFixture(1, 42, "ONE_INK", true)}
            ${routeItemFixture(2, 3, "OWNED", false)}
        </ol>
        <p data-reader-status></p>
        <button type="button" data-route-previous disabled>이전 경로 페이지</button>
        <button type="button" data-route-next disabled>다음 경로 페이지</button>
        <a data-original-viewer-link hidden>원본 순서로 읽기</a>
        <div role="alert" tabindex="-1" data-route-ink-notice hidden>
            <span data-route-ink-notice-message></span>
            <a href="/ink" data-route-ink-link hidden>잉크 충전하기</a>
        </div>
        <div tabindex="-1" aria-busy="false" data-route-content>
            <p>경로 페이지를 열면 콘텐츠가 여기에 표시됩니다.</p>
        </div>
        <p data-feedback-guide></p>
        <button type="button" data-feedback-rating="HELPFUL" disabled>도움</button>
        <button type="button" data-feedback-rating="NEUTRAL" disabled>보통</button>
        <button type="button" data-feedback-rating="NOT_HELPFUL" disabled>도움 안 됨</button>
    `;
    return root;
}

function routeItemFixture(position, pageNumber, costStatus, prerequisite) {
    return `
        <li data-route-position="${position}"
            data-page-number="${pageNumber}"
            data-cost-status="${costStatus}"
            data-opened="false"
            data-opened-at=""
            data-prerequisite="${prerequisite}">
            <span data-item-opened-badge hidden>열람 완료</span>
            <time data-item-opened-time hidden></time>
            <button type="button" data-open-route-item>열기</button>
        </li>
    `;
}

function routeOpenMetadata(pageNumber, owned, contentType = "TEXT") {
    return {
        viewerSessionId: "route-viewer-session-id",
        bookId: 15,
        pageNumber,
        owned,
        contentType
    };
}

function routeSnapshot(openedPages, current) {
    const completed = openedPages.size === 2;
    return {
        routeId: 476,
        bookId: 15,
        current,
        completedAt: completed ? "2026-08-14T01:10:00Z" : null,
        rating: null,
        items: [
            {
                position: 1,
                pageNumber: 42,
                openedAt: openedPages.has(42) ? "2026-08-14T01:05:00Z" : null,
                additionalCostStatus: openedPages.has(42) ? "ACTIVE_RENTAL" : "ONE_INK"
            },
            {
                position: 2,
                pageNumber: 3,
                openedAt: openedPages.has(3) ? "2026-08-14T01:10:00Z" : null,
                additionalCostStatus: "OWNED"
            }
        ]
    };
}

function routeStorage(values = new Map()) {
    return {
        getItem: (key) => values.get(key) || null,
        setItem: (key, value) => values.set(key, value),
        removeItem: (key) => values.delete(key)
    };
}

async function verifyLibraryPage() {
    const root = createLibraryFixture();
    fixtureContainer.append(root);
    let requestedUrl;

    const response = await createLibraryPage(root, {
        request: async (url) => {
            requestedUrl = url;
            return {
                entries: [
                    libraryEntry(11, "소장 도서", 7, true, null),
                    libraryEntry(12, "대여 중 도서", 3, false, true),
                    libraryEntry(13, "만료 도서", 2, false, false)
                ]
            };
        }
    }).start();

    const cards = root.querySelectorAll("[data-library-card]");
    assert(requestedUrl === "/api/library", "내 서재 API를 요청해야 합니다.");
    assert(response.entries.length === 3, "검증한 서재 API 응답을 반환해야 합니다.");
    assert(cards.length === 3, "소장·대여 중·대여 만료 도서를 모두 렌더링해야 합니다.");
    assert(
        root.querySelector("[data-library-status]").textContent === "서재에서 3권을 찾았습니다.",
        "서재 도서 수를 상태 영역에 표시해야 합니다.");

    const ownedCard = cards[0];
    assert(
        ownedCard.querySelector("[data-library-access]").textContent === "온라인 소장",
        "소장 도서 상태를 표시해야 합니다.");
    assert(ownedCard.querySelector("[data-library-rental]").hidden, "소장 도서에는 대여 기간을 숨겨야 합니다.");
    assert(!ownedCard.querySelector("[data-library-owned]").hidden, "소장 도서 안내를 표시해야 합니다.");
    assert(
        ownedCard.querySelector("[data-library-resume]").getAttribute("href")
            === "/books/11/viewer?page=7",
        "마지막 페이지를 이어서 읽기 URL에 포함해야 합니다.");

    const activeRentalCard = cards[1];
    assert(
        activeRentalCard.querySelector("[data-library-access]").textContent === "대여 중",
        "활성 대여 상태를 표시해야 합니다.");
    assert(
        activeRentalCard.querySelector("[data-library-access]").classList.contains("text-bg-success"),
        "활성 대여 배지 스타일을 적용해야 합니다.");
    assert(
        activeRentalCard.querySelector("[data-library-rented-at]").textContent.startsWith("대여 시작:"),
        "대여 시작 시각을 표시해야 합니다.");
    assert(
        activeRentalCard.querySelector("[data-library-expires-at]").textContent.startsWith("대여 만료:"),
        "대여 만료 시각을 표시해야 합니다.");

    const expiredRentalCard = cards[2];
    assert(
        expiredRentalCard.querySelector("[data-library-access]").textContent === "대여 만료",
        "만료된 대여 상태를 표시해야 합니다.");
    assert(
        expiredRentalCard.querySelector("[data-library-access]").classList.contains("text-bg-secondary"),
        "만료된 대여 배지 스타일을 적용해야 합니다.");

    await createLibraryPage(root, {
        request: async () => ({entries: []})
    }).start();
    assert(root.querySelector("[data-library-list]").children.length === 0, "빈 서재는 카드 목록을 비워야 합니다.");
    assert(!root.querySelector("[data-library-empty]").hidden, "빈 서재 안내를 표시해야 합니다.");

    const invalidResponse = await createLibraryPage(root, {
        request: async () => ({entries: [{bookId: 0}]})
    }).start();
    assert(invalidResponse === null, "잘못된 서재 API 응답은 성공으로 반환하면 안 됩니다.");
    assert(
        root.querySelector("[data-library-status]").textContent === "내 서재를 불러오지 못했습니다.",
        "서재 오류 상태를 표시해야 합니다.");
    assert(
        document.querySelector("[data-common-error]").textContent
            === "서재 API 응답 형식이 올바르지 않습니다.",
        "응답 검증 오류를 공통 오류 영역에 표시해야 합니다.");

    clearCommonError();
    root.remove();

    const aiRoot = createLibraryFixture({aiRouteEnabled: true});
    fixtureContainer.append(aiRoot);
    const htmlPurpose = "<img src=x onerror=alert('library')>";
    await createLibraryPage(aiRoot, {
        request: async () => ({
            entries: [
                {
                    bookId: 21,
                    coverImagePath: null,
                    title: "경로만 저장한 도서",
                    category: "인문",
                    lastPageNumber: 1,
                    rentedAt: null,
                    expiresAt: null,
                    activeRental: null,
                    owned: false,
                    routes: [{routeId: 31, purpose: htmlPurpose}],
                    currentRouteId: 31
                },
                {
                    ...libraryEntry(22, "여러 경로 도서", 4, false, true),
                    routes: [
                        {routeId: 42, purpose: "현재 경로"},
                        {routeId: 41, purpose: "이전 경로"}
                    ],
                    currentRouteId: 42
                }
            ]
        })
    }).start();

    const aiCards = aiRoot.querySelectorAll("[data-library-card]");
    const routeOnlyCard = aiCards[0];
    const routeOnlyLink = routeOnlyCard.querySelector("[data-library-route-list] a");
    assert(aiCards.length === 2, "AI 경로가 있는 책도 책당 카드 하나로 렌더링해야 합니다.");
    assert(
        routeOnlyCard.querySelector("[data-library-access]").textContent === "AI 경로 저장",
        "대여·소장 없이 경로만 저장한 책을 구분해야 합니다.");
    assert(
        routeOnlyCard.querySelector("[data-library-last-page]").textContent
            === "아직 읽은 페이지가 없습니다.",
        "경로만 저장한 책을 1페이지를 읽은 것처럼 표시하면 안 됩니다.");
    assert(
        routeOnlyCard.querySelector("[data-library-resume]").textContent === "첫 페이지 읽기"
            && routeOnlyCard.querySelector("[data-library-resume]").getAttribute("href")
                === "/books/21/viewer?page=1",
        "경로만 저장한 책은 1페이지 첫 진입을 제공해야 합니다.");
    assert(
        routeOnlyLink.getAttribute("href") === "/ai-routes/31",
        "저장 경로는 W02 상세 화면으로 연결해야 합니다.");
    assert(
        routeOnlyLink.textContent === htmlPurpose && routeOnlyLink.querySelector("img") === null,
        "HTML 모양의 목적을 텍스트로만 표시해야 합니다.");
    assert(
        routeOnlyCard.querySelector("[data-library-route-list] .badge").textContent === "현재 경로",
        "현재 경로를 배지로 표시해야 합니다.");
    assert(
        aiCards[1].querySelectorAll("[data-library-route-list] a").length === 2,
        "한 책의 저장 경로 여러 개를 같은 카드에 표시해야 합니다.");
    aiRoot.remove();
}

function createLibraryFixture({aiRouteEnabled = false} = {}) {
    const root = document.createElement("section");
    root.innerHTML = `
        <p data-library-status></p>
        <div data-library-list></div>
        <section data-library-empty hidden>빈 서재</section>
        <template data-library-card-template>
            <article data-library-card>
                <img data-library-cover alt="">
                <div data-library-cover-placeholder hidden>표지 없음</div>
                <span data-library-category></span>
                <h2 data-library-title></h2>
                <p data-library-last-page></p>
                <span data-library-access></span>
                <div data-library-rental>
                    <p data-library-rented-at></p>
                    <p data-library-expires-at></p>
                </div>
                <p data-library-owned hidden>소장 안내</p>
                ${aiRouteEnabled ? `
                    <section data-library-ai-routes hidden>
                        <ul data-library-route-list></ul>
                    </section>
                ` : ""}
                <a data-library-resume>이어서 읽기</a>
            </article>
        </template>
    `;
    return root;
}

function libraryEntry(bookId, title, lastPageNumber, owned, activeRental) {
    return {
        bookId,
        coverImagePath: null,
        title,
        category: "소설",
        lastPageNumber,
        rentedAt: owned ? null : "2026-07-31T06:00:00Z",
        expiresAt: owned ? null : "2026-08-30T06:00:00Z",
        owned,
        activeRental
    };
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

async function verifyAuthSuccess(flow, expectedApiPath, expectedSuccessPath) {
    const iframe = await loadAuthFixture(flow, "success");
    const iframeDocument = iframe.contentDocument;
    iframeDocument.querySelector("[name='email']").value = "reader@example.com";
    iframeDocument.querySelector("[name='password']").value = "Password1!";
    iframeDocument.querySelector("[data-auth-form]").requestSubmit();

    await waitFor(
        () => iframe.contentWindow.location.pathname === expectedSuccessPath,
        `${flow} 성공 뒤 ${expectedSuccessPath}(으)로 이동해야 합니다.`);

    assertAuthRequest(iframe, expectedApiPath);
    iframe.contentWindow.sessionStorage.removeItem(AUTH_REQUEST_STORAGE_KEY);
    iframe.remove();
}

async function verifyAuthFailure() {
    const iframe = await loadAuthFixture("login", "server-error");
    const iframeDocument = iframe.contentDocument;
    iframeDocument.querySelector("[name='email']").value = "reader@example.com";
    iframeDocument.querySelector("[name='password']").value = "Password1!";
    iframeDocument.querySelector("[data-auth-form]").requestSubmit();

    await waitFor(
        () => !iframeDocument.querySelector("[data-common-error]").hidden,
        "로그인 실패 오류를 현재 화면에 표시해야 합니다.");
    assert(
        iframe.contentWindow.location.pathname === "/auth-fixture.html",
        "로그인 실패에서는 현재 화면을 유지해야 합니다.");
    assert(
        iframeDocument.querySelector("[data-common-error]").textContent === "강제 인증 오류",
        "로그인 실패의 공개 오류 메시지를 표시해야 합니다.");
    assert(
        iframeDocument.querySelector("button[type='submit']").disabled === false,
        "로그인 실패 뒤 다시 시도할 수 있어야 합니다.");
    assert(
        iframeDocument.activeElement === iframeDocument.querySelector("[data-common-error]"),
        "로그인 실패 오류 영역으로 포커스를 이동해야 합니다.");
    assertAuthRequest(iframe, "/api/auth/login");
    iframe.contentWindow.sessionStorage.removeItem(AUTH_REQUEST_STORAGE_KEY);
    iframe.remove();
}

function assertAuthRequest(iframe, expectedApiPath) {
    const request = JSON.parse(
        iframe.contentWindow.sessionStorage.getItem(AUTH_REQUEST_STORAGE_KEY));
    assert(request.path === expectedApiPath, `${expectedApiPath} 인증 API를 호출해야 합니다.`);
    assert(request.method === "POST", "인증 API는 POST로 호출해야 합니다.");
    assert(request.credentials === "same-origin", "인증 요청은 same-origin 자격 증명만 전송해야 합니다.");
    assert(request.csrfToken === "browser-smoke-token", "인증 요청에 페이지의 CSRF 토큰을 추가해야 합니다.");
    assert(request.contentType === "application/json", "인증 요청은 JSON Content-Type을 사용해야 합니다.");
    assert(
        request.body.email === "reader@example.com"
            && request.body.password === "Password1!",
        "인증 폼의 이메일과 비밀번호를 JSON 본문에 담아야 합니다.");
}

async function loadAuthFixture(flow, mode) {
    const iframe = document.createElement("iframe");
    const loaded = new Promise((resolve) => iframe.addEventListener("load", resolve, {once: true}));
    iframe.src = `/auth-fixture.html?flow=${flow}&mode=${mode}`;
    fixtureContainer.append(iframe);
    await loaded;
    await waitFor(
        () => iframe.contentDocument.body.dataset.authFixtureReady === "true",
        `${flow} ${mode} 인증 fixture가 준비되어야 합니다.`);
    assert(
        iframe.contentDocument.querySelector("[data-auth-fields]")?.disabled === false,
        `${flow} 인증 필드는 스크립트 준비 뒤 활성화되어야 합니다.`);
    return iframe;
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

async function verifyBookDetailPage() {
    const root = createBookDetailFixture();
    fixtureContainer.append(root);
    const purchaseButton = root.querySelector("[data-ownership-purchase]");
    const retryButton = root.querySelector("[data-ownership-payment-retry]");
    const paymentStatus = root.querySelector("[data-ownership-payment-status]");
    let sdkMode = "failed";
    let paymentMode = "interrupted";
    let completeMode = "pending";
    let prepareCount = 0;
    let completeCount = 0;
    let lastPaymentRequest;

    const request = async (url) => {
        if (url === "/api/books/17") {
            return bookDetailResponse(false);
        }
        if (url === "/api/books/17/ownership-payments") {
            prepareCount += 1;
            return {
                paymentId: `ownership-payment-${prepareCount}`,
                storeId: "store-test",
                channelKey: "channel-test",
                orderName: "읽어볼까 도서 소장",
                totalAmount: 12000,
                currency: "CURRENCY_KRW"
            };
        }
        if (url.includes("/complete")) {
            completeCount += 1;
            if (completeMode === "pending") {
                return {
                    paymentId: `ownership-payment-${prepareCount}`,
                    status: "PENDING",
                    bookId: 17,
                    owned: false
                };
            }
            if (completeMode === "unavailable") {
                throw new ApiRequestError(
                    "PAYMENT_PROVIDER_UNAVAILABLE",
                    "결제 확인 서비스 오류",
                    503,
                    "ownership-payment-503"
                );
            }
            if (completeMode === "failed") {
                throw new ApiRequestError(
                    "PAYMENT_VERIFICATION_FAILED",
                    "결제 검증에 실패했습니다.",
                    422,
                    "ownership-payment-422"
                );
            }
            return {
                paymentId: `ownership-payment-${prepareCount}`,
                status: "PAID",
                bookId: 17,
                owned: true
            };
        }
        throw new Error(`예상하지 않은 도서 상세 API 요청: ${url}`);
    };

    const loadPortOne = async () => {
        if (sdkMode === "failed") {
            throw new Error("강제 CDN 오류");
        }
        return {
            requestPayment: async (paymentRequest) => {
                lastPaymentRequest = paymentRequest;
                if (paymentMode === "interrupted") {
                    return {
                        paymentId: paymentRequest.paymentId,
                        code: "PAYMENT_PROCESS_ABORTED",
                        message: "사용자가 결제창을 닫았습니다."
                    };
                }
                if (paymentMode === "already-paid") {
                    return {
                        paymentId: paymentRequest.paymentId,
                        code: "PAYMENT_ALREADY_PAID",
                        message: "이미 결제된 paymentId입니다."
                    };
                }
                return {paymentId: paymentRequest.paymentId};
            }
        };
    };

    const {ready} = initializeBookDetailPage(root, {request, loadPortOne});
    const loadedBook = await ready;

    assert(loadedBook?.description === null, "description이 null인 도서 상세 응답도 허용해야 합니다.");
    assert(root.querySelector("[data-book-description]").textContent === "", "description이 null이면 빈 소개를 표시해야 합니다.");
    assert(root.querySelector("[data-book-title]").textContent === "브라우저 소장 도서", "도서 제목을 표시해야 합니다.");
    assert(root.querySelector("[data-book-price]").textContent === "₩12,000", "도서 원가를 원화로 표시해야 합니다.");
    assert(
        root.querySelector("[data-book-viewer-link]").getAttribute("href")
            === "/books/17/viewer?page=1",
        "첫 페이지 뷰어 URL을 설정해야 합니다.");
    assert(!root.querySelector("[data-book-cover-placeholder]").hidden, "표지가 없으면 대체 영역을 표시해야 합니다.");
    assert(!purchaseButton.disabled, "로그인한 미소장 독자는 소장 결제를 시작할 수 있어야 합니다.");

    purchaseButton.click();
    await waitFor(
        () => paymentStatus.textContent.includes("결제 모듈을 불러오지 못했습니다"),
        "PortOne CDN 실패를 소장 결제 영역에 표시해야 합니다.");
    assert(prepareCount === 0, "SDK를 불러오지 못하면 PENDING 소장 결제를 만들면 안 됩니다.");
    assert(!purchaseButton.disabled, "SDK 실패 뒤 소장 결제를 다시 시도할 수 있어야 합니다.");

    sdkMode = "success";
    purchaseButton.click();
    await waitFor(
        () => retryButton.textContent === "결제창 다시 열기",
        "결제창 이탈 뒤 같은 소장 결제창 재시도를 제공해야 합니다.");
    assert(prepareCount === 1, "소장 결제 준비는 한 번만 생성해야 합니다.");
    assert(completeCount === 1, "결제창 이탈 뒤에도 서버에서 소장 결제 상태를 확인해야 합니다.");
    assert(purchaseButton.disabled, "PENDING 소장 결제가 있으면 새 결제를 막아야 합니다.");
    assert(lastPaymentRequest.payMethod === "CARD", "소장 결제 수단은 카드로 고정해야 합니다.");
    assert(lastPaymentRequest.totalAmount === 12000, "서버가 준비한 도서 원가를 결제창에 전달해야 합니다.");

    paymentMode = "success";
    retryButton.click();
    await waitFor(
        () => retryButton.textContent === "결제 결과 다시 확인",
        "PENDING 소장 결제는 결과 재확인을 제공해야 합니다.");
    assert(prepareCount === 1, "결제창 재시도에서 새 소장 결제를 준비하면 안 됩니다.");
    assert(completeCount === 2, "결제창 성공 뒤 소장 완료 API를 호출해야 합니다.");
    assert(paymentStatus.textContent.includes("[PENDING]"), "PENDING 소장 상태를 구분해 표시해야 합니다.");

    completeMode = "unavailable";
    retryButton.click();
    await waitFor(
        () => paymentStatus.textContent.includes("일시적인 문제가 있습니다"),
        "503 응답은 소장 결제 일시 장애로 안내해야 합니다.");
    assert(purchaseButton.disabled, "503 뒤에도 새 소장 결제를 만들면 안 됩니다.");

    completeMode = "failed";
    retryButton.click();
    await waitFor(
        () => paymentStatus.textContent.includes("[FAILED]"),
        "422 응답은 소장 결제 FAILED 상태로 표시해야 합니다.");
    assert(!purchaseButton.disabled, "FAILED 뒤에는 새 소장 결제를 허용해야 합니다.");

    completeMode = "paid";
    purchaseButton.click();
    await waitFor(
        () => paymentStatus.textContent.includes("[PAID]"),
        "검증된 소장 결제 성공을 PAID 상태로 표시해야 합니다.");
    assert(prepareCount === 2, "FAILED 뒤 새 paymentId를 준비해야 합니다.");
    assert(purchaseButton.hidden, "PAID 뒤 소장 결제 버튼을 숨겨야 합니다.");
    assert(retryButton.hidden, "PAID 뒤 결제 재시도 버튼을 숨겨야 합니다.");
    assert(
        root.querySelector("[data-ownership-summary]").textContent.includes("온라인 소장 중입니다"),
        "PAID 뒤 기간 제한 없는 소장 상태를 표시해야 합니다.");

    assert(document.querySelector("[data-common-error]").hidden, "결제 상태 오류는 공통 오류 영역을 열면 안 됩니다.");
    root.remove();

    paymentMode = "already-paid";
    completeMode = "paid";
    const prepareCountBeforeReconnect = prepareCount;
    const completeCountBeforeReconnect = completeCount;
    const reconnectRoot = createBookDetailFixture();
    fixtureContainer.append(reconnectRoot);
    const reconnectStatus = reconnectRoot.querySelector("[data-ownership-payment-status]");
    await initializeBookDetailPage(reconnectRoot, {request, loadPortOne}).ready;

    reconnectRoot.querySelector("[data-ownership-purchase]").click();
    await waitFor(
        () => reconnectStatus.textContent.includes("[PAID]"),
        "이미 결제된 paymentId의 SDK 오류 뒤 서버 PAID 조회로 소장을 복구해야 합니다.");
    assert(
        prepareCount === prepareCountBeforeReconnect + 1,
        "재접속은 기존 PENDING 소장 결제 준비 정보를 한 번 조회해야 합니다.");
    assert(
        completeCount === completeCountBeforeReconnect + 1,
        "이미 결제된 SDK 오류 뒤 소장 완료 API를 호출해야 합니다.");
    assert(
        reconnectRoot.querySelector("[data-ownership-purchase]").hidden,
        "재접속 PAID 복구 뒤 소장 결제 버튼을 숨겨야 합니다.");
    reconnectRoot.remove();

    const anonymousRoot = createBookDetailFixture({authenticated: false});
    fixtureContainer.append(anonymousRoot);
    await initializeBookDetailPage(anonymousRoot, {
        request: async () => bookDetailResponse(null),
        loadPortOne
    }).ready;
    assert(anonymousRoot.querySelector("[data-ownership-purchase]").hidden, "비로그인 사용자에게 결제 버튼을 숨겨야 합니다.");
    assert(!anonymousRoot.querySelector("[data-ownership-login]").hidden, "비로그인 사용자에게 로그인 링크를 표시해야 합니다.");
    anonymousRoot.remove();

    const disabledRoot = createBookDetailFixture({paymentEnabled: false});
    fixtureContainer.append(disabledRoot);
    await initializeBookDetailPage(disabledRoot, {
        request: async () => bookDetailResponse(false),
        loadPortOne
    }).ready;
    assert(disabledRoot.querySelector("[data-ownership-purchase]").disabled, "결제 비활성 환경에서는 소장 결제를 막아야 합니다.");
    assert(
        disabledRoot.querySelector("[data-ownership-summary]").textContent.includes("사용할 수 없습니다"),
        "결제 비활성 환경 안내를 표시해야 합니다.");
    disabledRoot.remove();

    const supportedRoot = createBookDetailFixture({aiRouteEnabled: true});
    fixtureContainer.append(supportedRoot);
    const supportedRequests = [];
    await initializeBookDetailPage(supportedRoot, {
        request: async (url) => {
            supportedRequests.push(url);
            return bookDetailResponse(false, true);
        },
        loadPortOne
    }).ready;
    assert(
        supportedRoot.querySelector("[data-ai-route-link]").getAttribute("href")
            === "/books/17/ai-route",
        "로그인한 사용자의 지원 도서는 W01 생성 화면으로 연결해야 합니다.");
    assert(!supportedRoot.querySelector("[data-ai-route-entry]").hidden, "지원 도서의 AI 진입점을 표시해야 합니다.");
    assert(!supportedRoot.querySelector("[data-ownership-purchase]").hidden, "AI 진입점이 기존 소장 결제 버튼을 숨기면 안 됩니다.");
    assert(
        supportedRequests.length === 1 && supportedRequests[0] === "/api/books/17",
        "도서 상세 진입점은 AI 생성 API를 호출하면 안 됩니다.");
    supportedRoot.remove();

    const anonymousAiRoot = createBookDetailFixture({
        authenticated: false,
        aiRouteEnabled: true
    });
    fixtureContainer.append(anonymousAiRoot);
    await initializeBookDetailPage(anonymousAiRoot, {
        request: async () => bookDetailResponse(null, true),
        loadPortOne
    }).ready;
    assert(
        anonymousAiRoot.querySelector("[data-ai-route-link]").getAttribute("href")
            === "/login?returnTo=%2Fbooks%2F17%2Fai-route",
        "비로그인 지원 도서는 로그인 후 W01로 복귀해야 합니다.");
    assert(
        anonymousAiRoot.querySelector("[data-ai-route-description]").textContent.includes("로그인하면"),
        "비로그인 사용자에게 AI 경로 기능을 설명해야 합니다.");
    anonymousAiRoot.remove();

    const unsupportedRoot = createBookDetailFixture({aiRouteEnabled: true});
    fixtureContainer.append(unsupportedRoot);
    await initializeBookDetailPage(unsupportedRoot, {
        request: async () => bookDetailResponse(false, false),
        loadPortOne
    }).ready;
    assert(
        unsupportedRoot.querySelector("[data-ai-route-entry]") === null,
        "미지원 도서에는 AI 생성 control을 남기면 안 됩니다.");
    unsupportedRoot.remove();
}

function createBookDetailFixture({
    authenticated = true,
    paymentEnabled = true,
    aiRouteEnabled = false
} = {}) {
    const root = document.createElement("section");
    root.dataset.bookId = "17";
    root.dataset.authenticated = String(authenticated);
    root.dataset.paymentEnabled = String(paymentEnabled);
    root.innerHTML = `
        <p data-book-detail-loading></p>
        <div data-book-detail-content hidden>
            <img data-book-cover alt="">
            <div data-book-cover-placeholder hidden></div>
            <span data-book-category></span>
            <h1 data-book-title></h1>
            <span data-book-author></span>
            <p data-book-description></p>
            <span data-book-page-count></span>
            <span data-book-price></span>
            <a data-book-viewer-link>첫 페이지 읽기</a>
            ${aiRouteEnabled ? `
                <section data-ai-route-entry hidden>
                    <p data-ai-route-description></p>
                    <a data-ai-route-link></a>
                </section>
            ` : ""}
            <p data-ownership-summary></p>
            <button type="button" data-ownership-purchase disabled>소장 결제</button>
            <a href="/login" data-ownership-login hidden>로그인</a>
            <div tabindex="-1" data-ownership-payment-status hidden></div>
            <button type="button" data-ownership-payment-retry hidden></button>
        </div>
    `;
    return root;
}

function bookDetailResponse(owned, aiRouteSupported) {
    const response = {
        bookId: 17,
        category: "소설",
        coverImagePath: null,
        title: "브라우저 소장 도서",
        author: "브라우저 작가",
        description: null,
        totalPageCount: 120,
        bookPrice: 12000,
        owned
    };
    if (aiRouteSupported !== undefined) {
        response.aiRouteSupported = aiRouteSupported;
    }
    return response;
}

async function verifyOwnershipHistoryPage() {
    const root = createOwnershipHistoryFixture();
    fixtureContainer.append(root);
    const requestedPages = [];
    const page = root.querySelector("[data-ownership-history-page]");
    const previousButton = root.querySelector("[data-ownership-history-previous]");
    const nextButton = root.querySelector("[data-ownership-history-next]");

    const history = createOwnershipHistoryPage(root, {
        request: async (url) => {
            const requestedPage = Number(new URL(url, window.location.href).searchParams.get("page"));
            requestedPages.push(requestedPage);
            return ownershipHistoryResponse(requestedPage);
        }
    });
    await history.start();

    let rows = root.querySelectorAll("[data-ownership-history-list] tr");
    assert(requestedPages[0] === 1, "소장 결제 내역 첫 페이지를 요청해야 합니다.");
    assert(rows.length === 2, "첫 페이지의 완료된 소장 결제 두 건을 렌더링해야 합니다.");
    assert(rows[0].textContent.includes("최신 소장 도서"), "최신 소장 결제를 먼저 표시해야 합니다.");
    assert(rows[0].textContent.includes("₩12,000"), "소장 결제 금액을 원화로 표시해야 합니다.");
    assert(
        rows[0].querySelector("a").getAttribute("href") === "/books/17",
        "소장 결제 도서 상세 링크를 제공해야 합니다.");
    assert(rows[0].textContent.includes("온라인 소장 중"), "현재 소장 상태를 표시해야 합니다.");
    assert(page.textContent === "1 / 2페이지", "현재 소장 결제 내역 페이지를 표시해야 합니다.");
    assert(previousButton.disabled, "첫 페이지에서 이전 버튼을 비활성화해야 합니다.");
    assert(!nextButton.disabled, "다음 소장 결제 내역이 있으면 다음 버튼을 활성화해야 합니다.");

    nextButton.click();
    await waitFor(
        () => page.textContent === "2 / 2페이지",
        "다음 소장 결제 내역 페이지를 표시해야 합니다.");
    rows = root.querySelectorAll("[data-ownership-history-list] tr");
    assert(requestedPages.includes(2), "소장 결제 내역 2페이지를 API에 요청해야 합니다.");
    assert(rows.length === 1 && rows[0].textContent.includes("이전 소장 도서"), "둘째 페이지 내역을 교체해 표시해야 합니다.");
    assert(!previousButton.disabled, "둘째 페이지에서 이전 버튼을 활성화해야 합니다.");
    assert(nextButton.disabled, "마지막 페이지에서 다음 버튼을 비활성화해야 합니다.");
    root.remove();

    const emptyRoot = createOwnershipHistoryFixture();
    fixtureContainer.append(emptyRoot);
    await createOwnershipHistoryPage(emptyRoot, {
        request: async () => ({payments: [], page: 1, totalPages: 0, totalCount: 0})
    }).start();
    assert(
        emptyRoot.querySelector("[data-ownership-history-status]").textContent
            === "완료된 소장 결제 내역이 없습니다.",
        "빈 소장 결제 내역을 안내해야 합니다.");
    assert(
        emptyRoot.querySelector("[data-ownership-history-page]").textContent === "내역 없음",
        "빈 소장 결제 내역의 페이지 상태를 표시해야 합니다.");
    assert(emptyRoot.querySelector("[data-ownership-history-next]").disabled, "빈 내역에서는 다음 버튼을 비활성화해야 합니다.");
    emptyRoot.remove();

    const invalidRoot = createOwnershipHistoryFixture();
    fixtureContainer.append(invalidRoot);
    const invalidResponse = await createOwnershipHistoryPage(invalidRoot, {
        request: async () => ({payments: [{bookId: 0}], page: 1, totalPages: 1, totalCount: 1})
    }).start();
    assert(invalidResponse === null, "잘못된 소장 결제 내역 응답은 성공으로 반환하면 안 됩니다.");
    assert(
        invalidRoot.querySelector("[data-ownership-history-status]").textContent
            === "소장 결제 내역을 불러오지 못했습니다.",
        "소장 결제 내역 오류 상태를 표시해야 합니다.");
    assert(
        document.querySelector("[data-common-error]").textContent
            === "소장 결제 내역 API 응답 형식이 올바르지 않습니다.",
        "소장 결제 응답 검증 오류를 공통 오류 영역에 표시해야 합니다.");

    clearCommonError();
    invalidRoot.remove();
}

function createOwnershipHistoryFixture() {
    const root = document.createElement("section");
    root.innerHTML = `
        <p data-ownership-history-status></p>
        <table><tbody data-ownership-history-list></tbody></table>
        <span data-ownership-history-page></span>
        <button type="button" data-ownership-history-previous disabled>이전</button>
        <button type="button" data-ownership-history-next disabled>다음</button>
    `;
    return root;
}

function ownershipHistoryResponse(page) {
    const payments = page === 1
        ? [
            ownershipHistoryEntry("payment-latest", 17, "최신 소장 도서", 12000, "2026-08-01T08:00:00Z"),
            ownershipHistoryEntry("payment-middle", 18, "중간 소장 도서", 10000, "2026-07-31T08:00:00Z")
        ]
        : [ownershipHistoryEntry("payment-old", 19, "이전 소장 도서", 9000, "2026-07-30T08:00:00Z")];
    return {payments, page, totalPages: 2, totalCount: 3};
}

function ownershipHistoryEntry(paymentId, bookId, bookTitle, amountWon, paidAt) {
    return {paymentId, bookId, bookTitle, amountWon, paidAt, owned: true};
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

async function verifyAiRouteGenerationPage() {
    const fixture = createAiRouteGenerationFixture();
    fixtureContainer.append(fixture);
    const generationId = "11111111-1111-4111-8111-111111111111";
    const secondGenerationId = "22222222-2222-4222-8222-222222222222";
    const successfulGenerationId = "55555555-5555-4555-8555-555555555555";
    const nonCurrentGenerationId = "66666666-6666-4666-8666-666666666666";
    const generatedIds = [
        generationId,
        secondGenerationId,
        "33333333-3333-4333-8333-333333333333",
        "44444444-4444-4444-8444-444444444444",
        successfulGenerationId,
        nonCurrentGenerationId
    ];
    const generationRequests = [];
    const saveRequests = [];
    let generationAttempt = 0;
    let mode = "route";

    const routeResponse = {
        generationId,
        status: "ROUTE",
        bookId: 17,
        contentVersion: "ai-route-v2",
        purpose: "<img src=x onerror=alert(1)> 핵심 읽기",
        expiresAt: "2026-08-14T12:00:00Z",
        routeId: null,
        remainingDailyGenerations: 9,
        noRouteReason: null,
        minimumRequiredInk: null,
        items: [
            {
                position: 1,
                pageNumber: 3,
                relevance: "HIGH",
                prerequisite: true,
                role: "PREREQUISITE",
                estimatedMinutes: 4,
                guide: "<script>위험</script> 선수 개념",
                additionalCostStatus: "ONE_INK"
            },
            {
                position: 2,
                pageNumber: 7,
                relevance: "MEDIUM",
                prerequisite: false,
                role: "CORE",
                estimatedMinutes: 6,
                guide: "핵심 관점을 확인하세요.",
                additionalCostStatus: "ACTIVE_RENTAL"
            }
        ]
    };

    const request = async (url, options = {}) => {
        if (url === "/api/books/17") {
            return {bookId: 17, title: "검증 도서", owned: false};
        }
        if (url === "/api/ink/balance") {
            return {balance: 12};
        }
        if (url === "/api/books/17/ai-route-generations") {
            generationAttempt += 1;
            generationRequests.push({url, options});
            if (generationAttempt === 1) {
                throw new ApiRequestError(
                    "NETWORK_ERROR",
                    "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.",
                    0,
                    null);
            }
            if (mode === "daily-limit") {
                throw new ApiRequestError(
                    "AI_ROUTE_DAILY_LIMIT_EXCEEDED",
                    "오늘 생성할 수 있는 AI 경로 횟수를 모두 사용했습니다.",
                    429,
                    "daily-limit");
            }
            if (mode === "provider-unavailable") {
                throw new ApiRequestError(
                    "AI_ROUTE_PROVIDER_UNAVAILABLE",
                    "AI 경로 생성 서비스를 일시적으로 사용할 수 없습니다.",
                    503,
                    "provider-unavailable");
            }
            if (mode === "no-route") {
                return {
                    ...routeResponse,
                    generationId: secondGenerationId,
                    status: "NO_ROUTE",
                    purpose: "더 깊이 읽기",
                    noRouteReason: "INSUFFICIENT_BUDGET",
                    minimumRequiredInk: 13,
                    items: []
                };
            }
            if (mode === "save-success") {
                return {
                    ...routeResponse,
                    generationId: successfulGenerationId
                };
            }
            if (mode === "save-not-current") {
                return {
                    ...routeResponse,
                    generationId: nonCurrentGenerationId
                };
            }
            return {
                ...routeResponse,
                status: "GENERATING",
                expiresAt: null,
                items: []
            };
        }
        if (url === `/api/ai-route-generations/${generationId}`) {
            generationRequests.push({url, options});
            return routeResponse;
        }
        if (url === `/api/ai-route-generations/${generationId}/routes`) {
            saveRequests.push({url, options});
            throw new ApiRequestError(
                "AI_ROUTE_ENTITLEMENT_CHANGED",
                "대여·소장 상태가 변경되었습니다.",
                409,
                "entitlement-changed");
        }
        if (url === `/api/ai-route-generations/${successfulGenerationId}/routes`) {
            saveRequests.push({url, options});
            return {
                routeId: 71,
                bookId: 17,
                bookTitle: "검증 도서",
                purpose: routeResponse.purpose,
                current: true,
                createdAt: "2026-08-14T12:01:00Z",
                completedAt: null,
                rating: null,
                items: routeResponse.items
            };
        }
        if (url === `/api/ai-route-generations/${nonCurrentGenerationId}/routes`) {
            saveRequests.push({url, options});
            return {
                routeId: 72,
                bookId: 17,
                bookTitle: "검증 도서",
                purpose: routeResponse.purpose,
                current: false,
                createdAt: "2026-08-14T12:02:00Z",
                completedAt: null,
                rating: null,
                items: routeResponse.items
            };
        }
        throw new Error(`예상하지 않은 AI 경로 API 요청: ${url}`);
    };

    const page = createAiRouteGenerationPage(fixture, {
        request,
        randomUUID: () => generatedIds.shift(),
        schedule: (callback) => Promise.resolve().then(callback),
        pollDelay: 0
    });
    await page.start();

    const budget = fixture.querySelector("[data-ai-route-budget]");
    assert(budget.max === "12", "추가 잉크 예산 상한은 현재 잔액이어야 합니다.");
    assert(budget.value === "10", "비소장 예산 기본값은 min(10, 현재 잔액)이어야 합니다.");
    assert(
        fixture.querySelector('[data-ai-route-budget-choice="15"]').hidden,
        "현재 잔액보다 큰 빠른 예산 선택은 표시하면 안 됩니다.");
    assert(
        !fixture.querySelector("[data-ai-route-budget-all]").hidden,
        "보유 잉크 전부 선택은 항상 표시해야 합니다.");

    assertThrows(
        () => generationRequestOf({
            purpose: "핵심 읽기",
            owned: false,
            inkBalance: 12,
            budget: "",
            depth: null
        }),
        "비소장 도서의 빈 예산을 0잉크로 바꾸지 말고 거부해야 합니다.");

    const purpose = fixture.querySelector("[data-ai-route-purpose]");
    const canonicallyValidPurpose = "e\u0301".repeat(101);
    purpose.value = canonicallyValidPurpose;
    purpose.dispatchEvent(new Event("input"));
    assert(
        fixture.querySelector("[data-ai-route-purpose-count]").textContent === "101",
        "독서 목적 글자 수는 서버와 같이 NFC 정규화한 뒤 세어야 합니다.");
    const canonicallyValidRequest = generationRequestOf({
        purpose: canonicallyValidPurpose,
        owned: false,
        inkBalance: 12,
        budget: "10",
        depth: null
    });
    assert(
        canonicallyValidRequest.purpose === canonicallyValidPurpose,
        "NFC 정규화 뒤 200자 이하인 원문은 서버 검증까지 전달해야 합니다.");

    purpose.value = routeResponse.purpose;
    purpose.dispatchEvent(new Event("input"));
    await page.startNewGeneration();

    const retry = fixture.querySelector("[data-ai-route-retry]");
    assert(!retry.hidden, "전송 실패 뒤 같은 생성 실행의 재시도를 제공해야 합니다.");
    retry.click();
    await waitFor(
        () => !fixture.querySelector("[data-ai-route-preview]").hidden,
        "202 polling 뒤 ROUTE 미리보기를 표시해야 합니다.");

    assert(generationRequests.length === 3, "생성 retry 두 번과 polling 한 번만 요청해야 합니다.");
    assert(
        generationRequests[0].options.headers["Idempotency-Key"] === generationId
            && generationRequests[1].options.headers["Idempotency-Key"] === generationId,
        "같은 생성 실행의 전송 재시도는 UUID 멱등 키를 재사용해야 합니다.");
    assert(
        generationRequests[2].url === `/api/ai-route-generations/${generationId}`
            && generationRequests[2].options.method === undefined,
        "polling은 generation GET만 호출해야 합니다.");
    assert(
        document.querySelector("[data-common-error]").hidden,
        "성공한 재시도 뒤 이전 전송 오류를 지워야 합니다.");
    assert(
        fixture.querySelectorAll("[data-ai-route-item]").length === 2,
        "ROUTE의 전체 페이지 항목을 표시해야 합니다.");
    assert(
        fixture.querySelector("[data-ai-route-preview-purpose]").textContent
            === routeResponse.purpose,
        "서버가 정규화한 독서 목적을 텍스트로 표시해야 합니다.");
    assert(
        fixture.querySelector("[data-ai-route-guide]").textContent
            === "<script>위험</script> 선수 개념"
            && fixture.querySelector("[data-ai-route-guide] script") === null,
        "HTML 모양 guide를 요소로 해석하면 안 됩니다.");

    fixture.querySelector("[data-ai-route-save]").click();
    await waitFor(
        () => fixture.querySelector("[data-ai-route-status]").textContent.includes("새 경로를 생성"),
        "권한 변동 저장 거부는 새 경로 생성만 안내해야 합니다.");
    assert(saveRequests.length === 1, "generationId 저장 요청은 한 번만 보내야 합니다.");
    assert(
        saveRequests[0].options.body === undefined,
        "저장 요청에는 페이지·순서·guide를 포함한 바디가 없어야 합니다.");
    assert(
        fixture.querySelector("[data-ai-route-save]").disabled,
        "권한 변동 저장 거부 뒤 같은 preview의 저장을 비활성화해야 합니다.");
    assert(
        Array.from(fixture.querySelectorAll("[data-ai-route-cost]"))
            .every((cost) => cost.textContent === "비용 상태 무효"),
        "권한 변동 저장 거부 뒤 모든 항목의 비용 상태를 무효화해야 합니다.");
    assert(
        !fixture.textContent.includes("추가 1잉크")
            && !fixture.textContent.includes("대여 중 · 추가 잉크 없음"),
        "권한 변동 뒤 생성 시점 비용·권한 상세가 DOM에 남으면 안 됩니다.");

    mode = "no-route";
    purpose.value = "더 깊이 읽기";
    budget.value = "10";
    await page.startNewGeneration();
    await waitFor(
        () => !fixture.querySelector("[data-ai-route-no-route]").hidden,
        "NO_ROUTE 결과를 별도 안내로 표시해야 합니다.");
    assert(
        fixture.querySelector("[data-ai-route-no-route-message]").textContent
            .includes("최소 13잉크"),
        "예산 부족 NO_ROUTE는 서버의 최소 필요 잉크를 표시해야 합니다.");
    assert(
        generationRequests.at(-1).options.headers["Idempotency-Key"] === secondGenerationId,
        "새 생성 버튼은 이전 실행과 다른 UUID 멱등 키를 사용해야 합니다.");

    const noRelevantDisplay = generationDisplayOf({
        ...routeResponse,
        status: "NO_ROUTE",
        noRouteReason: "NO_RELEVANT_PAGES",
        minimumRequiredInk: null,
        items: []
    }, 17);
    const insufficientDepthDisplay = generationDisplayOf({
        ...routeResponse,
        status: "NO_ROUTE",
        noRouteReason: "INSUFFICIENT_DEPTH",
        minimumRequiredInk: null,
        items: []
    }, 17);
    assert(
        noRelevantDisplay.detail.includes("관련된 페이지를 찾지 못했습니다"),
        "관련 페이지 없음 NO_ROUTE를 오류가 아닌 전용 안내로 표시해야 합니다.");
    assert(
        insufficientDepthDisplay.detail.includes("선택한 깊이"),
        "깊이 부족 NO_ROUTE를 예산 부족과 구분해 표시해야 합니다.");

    mode = "daily-limit";
    await page.startNewGeneration();
    assert(
        document.querySelector("[data-common-error]").textContent
            === "오늘 생성할 수 있는 AI 경로 횟수를 모두 사용했습니다.",
        "429 오류의 서버 사용자 메시지를 그대로 표시해야 합니다.");

    mode = "provider-unavailable";
    await page.startNewGeneration();
    assert(
        document.querySelector("[data-common-error]").textContent
            === "AI 경로 생성 서비스를 일시적으로 사용할 수 없습니다.",
        "503 오류의 서버 사용자 메시지를 그대로 표시해야 합니다.");

    mode = "save-success";
    await page.startNewGeneration();
    fixture.querySelector("[data-ai-route-save]").click();
    await waitFor(
        () => fixture.querySelector("[data-ai-route-status]").textContent.includes("경로를 저장했습니다"),
        "정상 저장 응답은 저장 완료로 안내해야 합니다.");
    assert(saveRequests.length === 2, "저장 성공 요청도 generationId로 한 번 보내야 합니다.");
    assert(
        saveRequests[1].url === `/api/ai-route-generations/${successfulGenerationId}/routes`
            && saveRequests[1].options.method === "POST"
            && saveRequests[1].options.body === undefined,
        "저장 성공 요청도 generationId만 경로에 넣고 바디 없이 POST해야 합니다.");
    assert(
        fixture.querySelector("[data-ai-route-save]").disabled,
        "저장 성공 뒤 같은 preview를 다시 저장할 수 없어야 합니다.");

    mode = "save-not-current";
    await page.startNewGeneration();
    fixture.querySelector("[data-ai-route-save]").click();
    await waitFor(
        () => fixture.querySelector("[data-ai-route-status]").textContent === "경로를 저장했습니다.",
        "현재 경로가 아닌 멱등 저장 응답에 현재 경로 지정 완료를 잘못 안내하면 안 됩니다.");
    assert(saveRequests.length === 3, "현재 경로가 아닌 저장 응답도 generationId로 한 번 요청해야 합니다.");

    const ownedRequest = generationRequestOf({
        purpose: "핵심 읽기",
        owned: true,
        inkBalance: null,
        budget: "",
        depth: "DEEP"
    });
    assert(
        ownedRequest.maxAdditionalInk === null && ownedRequest.depth === "DEEP",
        "소장 도서는 예산 없이 깊이만 전송해야 합니다.");
    assert(
        codePointCount("😀".repeat(200)) === 200,
        "독서 목적 길이는 UTF-16 unit이 아니라 Unicode code point로 세어야 합니다.");
    assertThrows(
        () => generationRequestOf({
            purpose: "😀".repeat(201),
            owned: true,
            inkBalance: null,
            budget: "",
            depth: "QUICK"
        }),
        "Unicode code point 201개 목적은 client 보조 검증에서 거부해야 합니다.");

    fixture.remove();

    const ownedFixture = createAiRouteGenerationFixture();
    fixtureContainer.append(ownedFixture);
    const ownedRequests = [];
    const ownedPage = createAiRouteGenerationPage(ownedFixture, {
        request: async (url) => {
            ownedRequests.push(url);
            if (url === "/api/books/17") {
                return {bookId: 17, title: "소장 검증 도서", owned: true};
            }
            throw new Error(`소장 화면이 호출하면 안 되는 API: ${url}`);
        }
    });
    await ownedPage.start();
    assert(
        !ownedFixture.querySelector("[data-ai-route-depth-fields]").hidden,
        "소장 도서는 QUICK/BALANCED/DEEP 깊이 입력을 표시해야 합니다.");
    assert(
        ownedFixture.querySelector("[data-ai-route-budget-fields]").hidden,
        "소장 도서는 추가 잉크 예산 입력을 표시하면 안 됩니다.");
    assert(
        ownedRequests.length === 1 && ownedRequests[0] === "/api/books/17",
        "소장 도서는 잉크 잔액 API를 호출하면 안 됩니다.");
    ownedFixture.remove();

    const invalidBookFixture = createAiRouteGenerationFixture();
    invalidBookFixture.dataset.bookId = "0";
    fixtureContainer.append(invalidBookFixture);
    let invalidBookRequestCount = 0;
    const invalidBookPage = createAiRouteGenerationPage(invalidBookFixture, {
        request: async () => {
            invalidBookRequestCount += 1;
            throw new Error("잘못된 bookId로 API를 호출하면 안 됩니다.");
        }
    });
    await invalidBookPage.start();
    assert(invalidBookRequestCount === 0, "잘못된 bookId는 API 호출 전에 거부해야 합니다.");
    assert(
        document.querySelector("[data-common-error]").textContent
            === "AI 경로 화면의 도서 식별자가 올바르지 않습니다.",
        "잘못된 bookId 초기화 실패를 공통 오류 영역에 표시해야 합니다.");
    assert(
        invalidBookFixture.querySelector("[data-ai-route-status]").textContent
            === "AI 경로 화면을 준비하지 못했습니다.",
        "잘못된 bookId에서도 화면이 준비 중 상태로 남으면 안 됩니다.");
    invalidBookFixture.remove();
}

function createAiRouteGenerationFixture() {
    const root = document.createElement("section");
    root.dataset.aiRouteGenerationRoot = "";
    root.dataset.bookId = "17";
    root.innerHTML = `
        <p data-ai-route-book-title></p>
        <form data-ai-route-form>
            <fieldset data-ai-route-inputs disabled>
                <textarea data-ai-route-purpose></textarea>
                <span data-ai-route-purpose-count></span>
                <fieldset data-ai-route-budget-fields hidden>
                    <input data-ai-route-budget>
                    <p data-ai-route-balance></p>
                    <button type="button" data-ai-route-budget-choice="5">5</button>
                    <button type="button" data-ai-route-budget-choice="10">10</button>
                    <button type="button" data-ai-route-budget-choice="15">15</button>
                    <button type="button" data-ai-route-budget-all>전부</button>
                </fieldset>
                <fieldset data-ai-route-depth-fields hidden>
                    <input type="radio" value="QUICK" data-ai-route-depth>
                    <input type="radio" value="BALANCED" data-ai-route-depth checked>
                    <input type="radio" value="DEEP" data-ai-route-depth>
                </fieldset>
                <button type="submit" data-ai-route-generate>생성</button>
                <button type="button" data-ai-route-retry hidden>재시도</button>
            </fieldset>
        </form>
        <p data-ai-route-status tabindex="-1"></p>
        <section data-ai-route-no-route hidden>
            <p data-ai-route-no-route-message></p>
        </section>
        <section data-ai-route-preview hidden>
            <span data-ai-route-preview-purpose></span>
            <span data-ai-route-remaining></span>
            <button type="button" data-ai-route-save>저장</button>
            <ol data-ai-route-items></ol>
            <template data-ai-route-item-template>
                <li data-ai-route-item>
                    <span data-ai-route-position></span>
                    <span data-ai-route-page></span>
                    <span data-ai-route-metadata></span>
                    <span data-ai-route-guide></span>
                    <span data-ai-route-cost></span>
                </li>
            </template>
        </section>`;
    return root;
}

function assertThrows(action, message) {
    try {
        action();
    } catch {
        assert(true, message);
        return;
    }
    assert(false, message);
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
