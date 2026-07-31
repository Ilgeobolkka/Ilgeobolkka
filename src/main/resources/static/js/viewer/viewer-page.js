import {clearCommonError, showCommonError} from "../common/error-display.js";
import {ApiRequestError, requestJson} from "../common/request-json.js";

const DEFAULT_ERROR_MESSAGE = "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.";
const TEXT_SIZES = ["small", "medium", "large", "x-large"];
const IMAGE_ZOOMS = [75, 100, 125, 150, 200];

export const VIEWER_SESSION_STORAGE_KEY = "ilgeobolkka.viewerSessionId";

export async function requestPageContent(
        url,
        viewerSessionId,
        expectedContentType,
        options = {}) {
    const requestUrl = new URL(url, window.location.href);
    if (requestUrl.origin !== window.location.origin) {
        throw new ApiRequestError(
            "CROSS_ORIGIN_REQUEST",
            "같은 출처의 API만 호출할 수 있습니다.",
            0,
            null
        );
    }

    const headers = new Headers(options.headers);
    headers.set("X-Viewer-Session-Id", viewerSessionId);

    let response;
    try {
        response = await fetch(requestUrl, {
            method: "GET",
            headers,
            credentials: "same-origin",
            signal: options.signal
        });
    } catch (error) {
        if (error?.name === "AbortError") {
            throw error;
        }
        throw new ApiRequestError("NETWORK_ERROR", DEFAULT_ERROR_MESSAGE, 0, null);
    }

    const requestId = response.headers.get("X-Request-Id");
    if (!response.ok) {
        throw await createApiError(response, requestId);
    }

    const mediaType = (response.headers.get("Content-Type") || "")
        .split(";")[0]
        .trim()
        .toLowerCase();

    if (expectedContentType === "TEXT" && mediaType === "text/plain") {
        return {
            contentType: "TEXT",
            body: await response.text()
        };
    }

    if (expectedContentType === "IMAGE"
            && (mediaType === "image/jpeg" || mediaType === "image/png")) {
        return {
            contentType: "IMAGE",
            body: await response.blob()
        };
    }

    throw new ApiRequestError(
        "INVALID_RESPONSE",
        DEFAULT_ERROR_MESSAGE,
        response.status,
        requestId
    );
}

export function createViewer(root, dependencies = {}) {
    const request = dependencies.request || requestJson;
    const loadContent = dependencies.loadContent || requestPageContent;
    const storage = dependencies.storage || window.sessionStorage;
    const createObjectUrl = dependencies.createObjectUrl
        || ((blob) => URL.createObjectURL(blob));
    const revokeObjectUrl = dependencies.revokeObjectUrl
        || ((url) => URL.revokeObjectURL(url));
    const elements = findElements(root);
    const bookId = Number(root.dataset.bookId);
    const initialPage = Number(root.dataset.initialPage);

    const state = {
        bookId,
        totalPageCount: 0,
        viewerSessionId: null,
        displayedPage: 0,
        targetPage: initialPage,
        requestSequence: 0,
        handledSequence: 0,
        queuePromise: null,
        contentAbortController: null,
        objectUrl: null,
        replaced: false,
        textSizeIndex: 1,
        imageZoomIndex: 1
    };

    bindEvents();
    updateControls();

    async function start() {
        setBusy(true, "도서 정보를 불러오고 있습니다.");
        clearNotice();
        clearCommonError();
        storage.removeItem(VIEWER_SESSION_STORAGE_KEY);

        try {
            const book = await request(`/api/books/${state.bookId}`);
            validateBook(book);
            state.totalPageCount = book.totalPageCount;
            elements.title.textContent = `${book.title} 뷰어`;
            elements.totalPages.textContent = String(book.totalPageCount);
            elements.pageInput.max = String(book.totalPageCount);
            elements.pageInput.value = String(initialPage);
            updateControls();
            return requestPage(initialPage);
        } catch (error) {
            handlePageError(error);
            return null;
        }
    }

    function requestPage(pageNumber) {
        if (state.replaced) {
            return Promise.resolve();
        }

        const normalizedPage = Number(pageNumber);
        if (!Number.isInteger(normalizedPage)
                || normalizedPage < 1
                || normalizedPage > state.totalPageCount) {
            elements.content.setAttribute("aria-busy", "false");
            if (state.displayedPage === 0) {
                elements.status.textContent = "페이지 번호를 확인해 주세요.";
            }
            elements.pageInput.setCustomValidity(
                `1부터 ${state.totalPageCount} 사이의 페이지 번호를 입력해 주세요.`
            );
            updateControls();
            elements.pageInput.reportValidity();
            elements.pageInput.focus();
            return Promise.resolve();
        }

        elements.pageInput.setCustomValidity("");
        state.targetPage = normalizedPage;
        state.requestSequence += 1;
        state.contentAbortController?.abort();
        clearNotice();
        clearCommonError();
        setBusy(true, `${normalizedPage}페이지를 불러오고 있습니다.`);
        elements.pageInput.value = String(normalizedPage);
        updateControls();

        return startQueue();
    }

    async function whenIdle() {
        while (state.queuePromise) {
            await state.queuePromise;
        }
    }

    function startQueue() {
        if (!state.queuePromise) {
            state.queuePromise = processQueue().finally(() => {
                state.queuePromise = null;
                if (!state.replaced && state.handledSequence < state.requestSequence) {
                    startQueue();
                }
            });
        }
        return state.queuePromise;
    }

    async function processQueue() {
        while (!state.replaced && state.handledSequence < state.requestSequence) {
            const sequence = state.requestSequence;
            const pageNumber = state.targetPage;
            try {
                await openPage(pageNumber, sequence);
            } catch {
                if (sequence === state.requestSequence) {
                    handlePageError(invalidResponseError());
                }
            } finally {
                state.handledSequence = sequence;
            }
        }
    }

    async function openPage(pageNumber, sequence) {
        let metadata;
        try {
            metadata = state.viewerSessionId
                ? await request("/api/reading-sessions/current/page", {
                    method: "PATCH",
                    headers: {
                        "X-Viewer-Session-Id": state.viewerSessionId
                    },
                    body: JSON.stringify({pageNumber})
                })
                : await request(`/api/books/${state.bookId}/reading-sessions`, {
                    method: "POST",
                    body: JSON.stringify({pageNumber})
                });
            validateMetadata(metadata, pageNumber);
        } catch (error) {
            if (sequence === state.requestSequence) {
                handlePageError(error);
            }
            return;
        }

        if (!state.viewerSessionId) {
            state.viewerSessionId = metadata.viewerSessionId;
            storage.setItem(VIEWER_SESSION_STORAGE_KEY, metadata.viewerSessionId);
            updateControls();
        }

        if (sequence !== state.requestSequence) {
            return;
        }

        const abortController = new AbortController();
        state.contentAbortController = abortController;

        let content;
        try {
            content = await loadContent(
                `/api/reading-sessions/current/pages/${pageNumber}/content`,
                state.viewerSessionId,
                metadata.contentType,
                {signal: abortController.signal}
            );
        } catch (error) {
            if (error?.name === "AbortError" || sequence !== state.requestSequence) {
                return;
            }
            handlePageError(error);
            return;
        } finally {
            if (state.contentAbortController === abortController) {
                state.contentAbortController = null;
            }
        }

        if (sequence !== state.requestSequence) {
            return;
        }

        renderPage(metadata, content);
    }

    function renderPage(metadata, content) {
        clearPageContent();

        if (content.contentType === "TEXT") {
            const text = document.createElement("div");
            text.className = "viewer-text";
            text.textContent = content.body;
            elements.content.append(text);
            elements.textControls.hidden = false;
            elements.imageControls.hidden = true;
        } else {
            state.objectUrl = createObjectUrl(content.body);
            const image = document.createElement("img");
            image.className = "viewer-image";
            image.alt = `${metadata.pageNumber}페이지 이미지 콘텐츠`;
            image.src = state.objectUrl;
            elements.content.append(image);
            elements.textControls.hidden = true;
            elements.imageControls.hidden = false;
        }

        state.displayedPage = metadata.pageNumber;
        state.targetPage = metadata.pageNumber;
        elements.pageInput.value = String(metadata.pageNumber);
        elements.content.setAttribute("aria-label", `${metadata.pageNumber}페이지 콘텐츠`);
        elements.content.setAttribute("aria-busy", "false");
        elements.status.textContent =
            `${metadata.pageNumber} / ${state.totalPageCount} 페이지`;
        elements.access.textContent = accessMessage(metadata);
        elements.inkBalance.textContent = `남은 잉크 ${metadata.inkBalance}`;
        elements.content.scrollTop = 0;
        elements.content.scrollLeft = 0;
        elements.content.focus({preventScroll: true});
        updateControls();
    }

    function handlePageError(error) {
        setBusy(false);
        state.targetPage = state.displayedPage || 1;
        elements.pageInput.value = String(state.targetPage);
        elements.status.textContent = state.displayedPage > 0
            ? `${state.displayedPage} / ${state.totalPageCount} 페이지`
            : "페이지를 불러오지 못했습니다.";

        if (error instanceof ApiRequestError
                && error.code === "VIEWER_SESSION_REPLACED") {
            state.replaced = true;
            state.viewerSessionId = null;
            storage.removeItem(VIEWER_SESSION_STORAGE_KEY);
            clearPageContent();
            elements.content.setAttribute("aria-busy", "false");
            elements.status.textContent = "이 탭의 열람 세션이 종료되었습니다.";
            showNotice(
                "다른 탭에서 새 뷰어가 열려 이 탭의 콘텐츠를 더 이상 표시하지 않습니다.",
                false
            );
            updateControls();
            return;
        }

        if (error instanceof ApiRequestError && error.code === "INSUFFICIENT_INK") {
            elements.content.setAttribute("aria-busy", "false");
            showNotice("잉크가 부족하여 새 페이지를 열 수 없습니다.", true);
            updateControls();
            return;
        }

        elements.content.setAttribute("aria-busy", "false");
        showCommonError(error);
        updateControls();
    }

    function bindEvents() {
        elements.previous.addEventListener("click", () => {
            requestPage(state.targetPage - 1);
        });
        elements.next.addEventListener("click", () => {
            requestPage(state.targetPage + 1);
        });
        elements.pageForm.addEventListener("submit", (event) => {
            event.preventDefault();
            requestPage(elements.pageInput.valueAsNumber);
        });
        elements.pageInput.addEventListener("input", () => {
            elements.pageInput.setCustomValidity("");
        });
        elements.content.addEventListener("keydown", (event) => {
            if (event.altKey || event.ctrlKey || event.metaKey) {
                return;
            }
            if (event.key === "ArrowLeft" && !elements.previous.disabled) {
                event.preventDefault();
                requestPage(state.targetPage - 1);
            }
            if (event.key === "ArrowRight" && !elements.next.disabled) {
                event.preventDefault();
                requestPage(state.targetPage + 1);
            }
        });
        elements.textSmaller.addEventListener("click", () => {
            state.textSizeIndex = Math.max(0, state.textSizeIndex - 1);
            elements.content.dataset.textSize = TEXT_SIZES[state.textSizeIndex];
            updateControls();
        });
        elements.textLarger.addEventListener("click", () => {
            state.textSizeIndex = Math.min(
                TEXT_SIZES.length - 1,
                state.textSizeIndex + 1
            );
            elements.content.dataset.textSize = TEXT_SIZES[state.textSizeIndex];
            updateControls();
        });
        elements.zoomOut.addEventListener("click", () => {
            state.imageZoomIndex = Math.max(0, state.imageZoomIndex - 1);
            updateImageZoom();
        });
        elements.zoomFit.addEventListener("click", () => {
            state.imageZoomIndex = IMAGE_ZOOMS.indexOf(100);
            updateImageZoom();
        });
        elements.zoomIn.addEventListener("click", () => {
            state.imageZoomIndex = Math.min(
                IMAGE_ZOOMS.length - 1,
                state.imageZoomIndex + 1
            );
            updateImageZoom();
        });
    }

    function updateImageZoom() {
        elements.content.dataset.imageZoom =
            String(IMAGE_ZOOMS[state.imageZoomIndex]);
        updateControls();
    }

    function updateControls() {
        const navigationEnabled = state.totalPageCount > 0 && !state.replaced;
        elements.previous.disabled =
            !navigationEnabled || state.targetPage <= 1;
        elements.next.disabled =
            !navigationEnabled || state.targetPage >= state.totalPageCount;
        elements.pageInput.disabled = !navigationEnabled;
        elements.pageSubmit.disabled = !navigationEnabled;
        elements.textSmaller.disabled = state.textSizeIndex === 0;
        elements.textLarger.disabled =
            state.textSizeIndex === TEXT_SIZES.length - 1;
        elements.zoomOut.disabled = state.imageZoomIndex === 0;
        elements.zoomIn.disabled =
            state.imageZoomIndex === IMAGE_ZOOMS.length - 1;
    }

    function setBusy(busy, message) {
        elements.content.setAttribute("aria-busy", String(busy));
        if (message) {
            elements.status.textContent = message;
        }
    }

    function showNotice(message, showInkLink) {
        elements.noticeMessage.textContent = message;
        elements.inkLink.hidden = !showInkLink;
        elements.notice.hidden = false;
        elements.notice.focus();
    }

    function clearNotice() {
        elements.noticeMessage.textContent = "";
        elements.inkLink.hidden = true;
        elements.notice.hidden = true;
    }

    function clearPageContent() {
        if (state.objectUrl) {
            revokeObjectUrl(state.objectUrl);
            state.objectUrl = null;
        }
        elements.content.replaceChildren();
        elements.textControls.hidden = true;
        elements.imageControls.hidden = true;
    }

    function validateBook(book) {
        if (book?.bookId !== state.bookId
                || !Number.isInteger(book?.totalPageCount)
                || book.totalPageCount < 1
                || typeof book?.title !== "string") {
            throw invalidResponseError();
        }
    }

    function validateMetadata(metadata, pageNumber) {
        if (metadata?.bookId !== state.bookId
                || metadata?.pageNumber !== pageNumber
                || typeof metadata?.viewerSessionId !== "string"
                || !["TEXT", "IMAGE"].includes(metadata?.contentType)
                || !Number.isInteger(metadata?.inkBalance)) {
            throw invalidResponseError();
        }
    }

    function invalidResponseError() {
        return new ApiRequestError(
            "INVALID_RESPONSE",
            DEFAULT_ERROR_MESSAGE,
            0,
            null
        );
    }

    return {
        start,
        requestPage,
        whenIdle
    };
}

async function createApiError(response, requestId) {
    let errorBody;
    try {
        errorBody = await response.json();
    } catch {
        errorBody = null;
    }

    return new ApiRequestError(
        typeof errorBody?.code === "string" ? errorBody.code : "INVALID_RESPONSE",
        typeof errorBody?.message === "string"
            ? errorBody.message
            : DEFAULT_ERROR_MESSAGE,
        response.status,
        requestId
    );
}

function accessMessage(metadata) {
    if (metadata.owned) {
        return "온라인 소장 도서 · 잉크 차감과 대여 만료 없음";
    }

    const expiresAt = new Intl.DateTimeFormat("ko-KR", {
        dateStyle: "medium",
        timeStyle: "short"
    }).format(new Date(metadata.expiresAt));

    if (metadata.deductedInk === 1) {
        return `1잉크 사용 · ${expiresAt}까지 대여`;
    }
    return `대여 중 · ${expiresAt}까지`;
}

function findElements(root) {
    const selectors = {
        title: "[data-viewer-title]",
        status: "[data-viewer-status]",
        notice: "[data-viewer-notice]",
        noticeMessage: "[data-viewer-notice-message]",
        inkLink: "[data-viewer-ink-link]",
        previous: "[data-viewer-previous]",
        next: "[data-viewer-next]",
        pageForm: "[data-viewer-page-form]",
        pageInput: "[data-viewer-page-input]",
        pageSubmit: "[data-viewer-page-submit]",
        totalPages: "[data-viewer-total-pages]",
        textControls: "[data-viewer-text-controls]",
        textSmaller: "[data-viewer-text-smaller]",
        textLarger: "[data-viewer-text-larger]",
        imageControls: "[data-viewer-image-controls]",
        zoomOut: "[data-viewer-zoom-out]",
        zoomFit: "[data-viewer-zoom-fit]",
        zoomIn: "[data-viewer-zoom-in]",
        access: "[data-viewer-access]",
        inkBalance: "[data-viewer-ink-balance]",
        content: "[data-viewer-content]"
    };

    return Object.fromEntries(
        Object.entries(selectors).map(([name, selector]) => {
            const element = root.querySelector(selector);
            if (!element) {
                throw new Error(`뷰어 요소를 찾을 수 없습니다: ${selector}`);
            }
            return [name, element];
        })
    );
}
