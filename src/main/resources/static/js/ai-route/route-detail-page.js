import {clearCommonError, showCommonError} from "../common/error-display.js";
import {requestPageContent} from "../common/request-page-content.js";
import {ApiRequestError, requestJson} from "../common/request-json.js";
import {VIEWER_SESSION_STORAGE_KEY} from "../common/viewer-session.js";

const DEFAULT_ERROR_MESSAGE = "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.";
const CONTENT_TYPES = new Set(["TEXT", "IMAGE"]);

export function createRouteDetailPage(root, dependencies = {}) {
    const request = dependencies.request || requestJson;
    const loadContent = dependencies.loadContent || requestPageContent;
    const storage = dependencies.storage || window.sessionStorage;
    const askToContinue = dependencies.confirm || window.confirm.bind(window);
    const navigate = dependencies.navigate || ((url) => window.location.assign(url));
    const createObjectUrl = dependencies.createObjectUrl
        || ((blob) => URL.createObjectURL(blob));
    const revokeObjectUrl = dependencies.revokeObjectUrl
        || ((url) => URL.revokeObjectURL(url));
    const elements = findElements(root);
    const routeId = Number(root.dataset.routeId);
    const bookId = Number(root.dataset.bookId);
    const items = [...elements.items]
        .sort((left, right) => position(left) - position(right));

    const state = {
        routeId,
        bookId,
        viewerSessionId: null,
        activeIndex: -1,
        busy: false,
        replaced: false,
        current: root.dataset.routeCurrent === "true",
        completed: root.dataset.routeCompleted === "true",
        rating: root.dataset.routeRating || null,
        objectUrl: null
    };

    bindEvents();

    function start() {
        storage.removeItem(VIEWER_SESSION_STORAGE_KEY);
        items.forEach(item => updateOpenedTime(item, item.dataset.openedAt || null));
        updateCompletedTime(root.dataset.routeCompletedAt || null);
        updateProgress();
        updateRouteState();
        updateControls();
    }

    async function openItem(index) {
        if (state.busy || state.replaced || index < 0 || index >= items.length) {
            return;
        }
        if (hasUnreadPrerequisiteBefore(index)
                && !askToContinue(
                    "아직 읽지 않은 선수 개념 페이지가 있습니다. 건너뛰고 이 페이지를 여시겠습니까?")) {
            return;
        }

        const item = items[index];
        const pageNumber = page(item);
        setBusy(true);
        clearCommonError();
        clearInkNotice();
        elements.status.textContent = `원본 ${pageNumber}페이지를 여는 중입니다.`;
        elements.content.setAttribute("aria-busy", "true");

        try {
            const metadata = state.viewerSessionId
                ? await request("/api/reading-sessions/current/page", {
                    method: "PATCH",
                    headers: {"X-Viewer-Session-Id": state.viewerSessionId},
                    body: JSON.stringify({pageNumber})
                })
                : await request(`/api/books/${state.bookId}/reading-sessions`, {
                    method: "POST",
                    body: JSON.stringify({pageNumber})
                });
            validateOpenMetadata(
                metadata,
                state.bookId,
                pageNumber,
                state.viewerSessionId
            );

            if (!state.viewerSessionId) {
                state.viewerSessionId = metadata.viewerSessionId;
                storage.setItem(VIEWER_SESSION_STORAGE_KEY, metadata.viewerSessionId);
            }
            applyOpenCostState(item, metadata);

            const content = await loadContent(
                `/api/ai-routes/${state.routeId}/pages/${pageNumber}/content`,
                state.viewerSessionId,
                metadata.contentType,
                {method: "POST"}
            );
            renderContent(content, pageNumber);
            setActiveItem(index);
            applyContentSuccess(item);
            elements.content.focus();
            await refreshRouteSnapshotAfterContentSuccess(index, pageNumber);
        } catch (error) {
            if (error?.code === "VIEWER_SESSION_REPLACED") {
                handleViewerSessionReplaced();
            } else if (error?.code === "INSUFFICIENT_INK") {
                elements.status.textContent = "잉크가 부족하여 페이지를 열지 못했습니다.";
                showInkNotice();
            } else {
                elements.status.textContent = "페이지를 열지 못했습니다.";
                showCommonError(error);
            }
        } finally {
            elements.content.setAttribute("aria-busy", "false");
            setBusy(false);
        }
    }

    function applyOpenCostState(item, metadata) {
        item.dataset.costStatus = metadata.owned ? "OWNED" : "ACTIVE_RENTAL";
        item.querySelector("[data-open-route-item]").textContent =
            openButtonLabel(item.dataset.costStatus);
        updateOriginalViewerLink();
        updateControls();
    }

    function applyContentSuccess(item) {
        item.dataset.opened = "true";
        item.querySelector("[data-item-opened-badge]").hidden = false;
        updateProgress();

        if (items.every(routeItem => routeItem.dataset.opened === "true")) {
            state.completed = true;
            root.dataset.routeCompleted = "true";
            updateRouteState();
        }
        updateOriginalViewerLink();
    }

    async function refreshRouteSnapshotAfterContentSuccess(index, pageNumber) {
        try {
            const route = await request(`/api/ai-routes/${state.routeId}`);
            applyRouteSnapshot(route);
            elements.status.textContent =
                `추천 순서 ${index + 1}번, 원본 ${pageNumber}페이지입니다.`;
        } catch (error) {
            elements.status.textContent =
                "콘텐츠는 열었지만 진행 상태를 새로고침하지 못했습니다.";
            showCommonError(error);
        }
    }

    function handleViewerSessionReplaced() {
        state.replaced = true;
        state.viewerSessionId = null;
        state.activeIndex = -1;
        storage.removeItem(VIEWER_SESSION_STORAGE_KEY);
        clearRenderedContent();

        items.forEach(item => {
            item.classList.remove("active");
            item.removeAttribute("aria-current");
        });

        const notice = document.createElement("p");
        notice.className = "text-secondary text-center p-5";
        notice.textContent =
            "다른 탭에서 새 뷰어가 열려 이 탭의 콘텐츠를 더 이상 표시하지 않습니다.";
        elements.content.append(notice);
        elements.content.setAttribute("aria-label", "종료된 열람 세션");
        elements.status.textContent = "이 탭의 열람 세션이 종료되었습니다.";
        updateOriginalViewerLink();
        updateControls();
    }

    async function makeCurrent() {
        if (state.busy || state.current) {
            return;
        }
        setBusy(true);
        clearCommonError();
        try {
            const route = await request(`/api/books/${state.bookId}/ai-routes/current`, {
                method: "PUT",
                body: JSON.stringify({routeId: state.routeId})
            });
            applyRouteSnapshot(route);
            elements.status.textContent = "현재 경로로 지정했습니다.";
        } catch (error) {
            showCommonError(error);
        } finally {
            setBusy(false);
        }
    }

    async function deleteRoute() {
        if (state.busy
                || !askToContinue("이 저장 경로와 진행 상태, 피드백을 삭제하시겠습니까?")) {
            return;
        }
        setBusy(true);
        clearCommonError();
        try {
            await request(`/api/ai-routes/${state.routeId}`, {method: "DELETE"});
            navigate("/library");
        } catch (error) {
            showCommonError(error);
            setBusy(false);
        }
    }

    async function changeFeedback(rating) {
        if (state.busy || !state.completed) {
            return;
        }
        setBusy(true);
        clearCommonError();
        try {
            const response = await request(`/api/ai-routes/${state.routeId}/feedback`, {
                method: "PUT",
                body: JSON.stringify({rating})
            });
            if (!response || !["HELPFUL", "NEUTRAL", "NOT_HELPFUL"].includes(response.rating)) {
                throw invalidResponseError();
            }
            state.rating = response.rating;
            updateFeedback();
            elements.feedbackGuide.textContent = "평가를 저장했습니다. 언제든 다른 평가로 바꿀 수 있습니다.";
        } catch (error) {
            showCommonError(error);
        } finally {
            setBusy(false);
        }
    }

    function bindEvents() {
        items.forEach((item, index) => {
            item.querySelector("[data-open-route-item]")
                ?.addEventListener("click", () => openItem(index));
        });
        elements.previous.addEventListener("click", () => openItem(state.activeIndex - 1));
        elements.next.addEventListener("click", () => openItem(state.activeIndex + 1));
        elements.makeCurrent.addEventListener("click", makeCurrent);
        elements.deleteRoute.addEventListener("click", deleteRoute);
        elements.feedbackButtons.forEach(button => {
            button.addEventListener("click", () => changeFeedback(button.dataset.feedbackRating));
        });
    }

    function applyRouteSnapshot(route) {
        validateRouteSnapshot(route, state.routeId, state.bookId);
        if (route.items.length !== items.length) {
            throw invalidResponseError();
        }
        const itemByPosition = new Map(
            route.items.map(item => [`${item.position}:${item.pageNumber}`, item])
        );

        items.forEach(itemElement => {
            const snapshot = itemByPosition.get(`${position(itemElement)}:${page(itemElement)}`);
            if (!snapshot) {
                throw invalidResponseError();
            }
            itemElement.dataset.opened = String(snapshot.openedAt !== null);
            itemElement.dataset.openedAt = snapshot.openedAt || "";
            itemElement.dataset.costStatus = snapshot.additionalCostStatus;
            itemElement.querySelector("[data-item-opened-badge]").hidden = snapshot.openedAt === null;
            updateOpenedTime(itemElement, snapshot.openedAt);
            itemElement.querySelector("[data-open-route-item]").textContent =
                openButtonLabel(snapshot.additionalCostStatus);
        });

        state.current = route.current;
        state.completed = route.completedAt !== null;
        state.rating = route.rating;
        root.dataset.routeCurrent = String(state.current);
        root.dataset.routeCompleted = String(state.completed);
        root.dataset.routeCompletedAt = route.completedAt || "";
        root.dataset.routeRating = state.rating || "";
        updateCompletedTime(route.completedAt);
        updateProgress();
        updateRouteState();
        updateOriginalViewerLink();
        updateControls();
    }

    function renderContent(content, pageNumber) {
        clearRenderedContent();
        if (content.contentType === "TEXT") {
            const text = document.createElement("div");
            text.className = "viewer-text";
            text.textContent = content.body;
            elements.content.append(text);
        } else if (content.contentType === "IMAGE") {
            state.objectUrl = createObjectUrl(content.body);
            const image = document.createElement("img");
            image.className = "viewer-image";
            image.alt = `${pageNumber}페이지 이미지 콘텐츠`;
            image.src = state.objectUrl;
            elements.content.append(image);
        } else {
            throw invalidResponseError();
        }
        elements.content.setAttribute("aria-label", `원본 ${pageNumber}페이지 콘텐츠`);
    }

    function clearRenderedContent() {
        if (state.objectUrl) {
            revokeObjectUrl(state.objectUrl);
            state.objectUrl = null;
        }
        elements.content.replaceChildren();
    }

    function setActiveItem(index) {
        state.activeIndex = index;
        items.forEach((item, itemIndex) => {
            const active = itemIndex === index;
            item.classList.toggle("active", active);
            if (active) {
                item.setAttribute("aria-current", "step");
            } else {
                item.removeAttribute("aria-current");
            }
        });
        updateOriginalViewerLink();
        updateControls();
    }

    function updateProgress() {
        const openedCount = items.filter(item => item.dataset.opened === "true").length;
        elements.progress.textContent = `${openedCount} / ${items.length} 완료`;
    }

    function updateOpenedTime(item, openedAt) {
        const openedTime = item.querySelector("[data-item-opened-time]");
        openedTime.hidden = openedAt === null || openedAt === "";
        openedTime.dateTime = openedAt || "";
        openedTime.textContent = openedAt ? `열람 ${formatDateTime(openedAt)}` : "";
    }

    function updateCompletedTime(completedAt) {
        elements.completedTime.hidden = completedAt === null || completedAt === "";
        elements.completedTime.dateTime = completedAt || "";
        elements.completedTime.textContent = completedAt
            ? `완료 ${formatDateTime(completedAt)}`
            : "";
    }

    function updateRouteState() {
        elements.currentBadge.hidden = !state.current;
        elements.completedBadge.hidden = !state.completed;
        elements.makeCurrent.disabled = state.busy || state.current;
        elements.feedbackGuide.textContent = state.completed
            ? "평가는 선택 사항이며 잉크나 열람 권한을 바꾸지 않습니다."
            : "모든 경로 페이지를 연 뒤 선택적으로 평가할 수 있습니다.";
        updateFeedback();
    }

    function updateFeedback() {
        elements.feedbackButtons.forEach(button => {
            button.disabled = state.busy || !state.completed;
            button.setAttribute(
                "aria-pressed",
                String(button.dataset.feedbackRating === state.rating)
            );
        });
    }

    function updateOriginalViewerLink() {
        if (state.activeIndex < 0) {
            elements.originalViewerLink.hidden = true;
            return;
        }
        const item = items[state.activeIndex];
        const pageNumber = page(item);
        elements.originalViewerLink.href = `/books/${state.bookId}/viewer?page=${pageNumber}`;
        elements.originalViewerLink.textContent =
            `원본 ${pageNumber}페이지부터 연속 읽기 · 경로 밖 새 페이지는 권한이 없으면 1잉크`;
        elements.originalViewerLink.hidden = false;
    }

    function setBusy(busy) {
        state.busy = busy;
        items.forEach(item => {
            item.querySelector("[data-open-route-item]").disabled = busy || state.replaced;
        });
        elements.makeCurrent.disabled = busy || state.current;
        elements.deleteRoute.disabled = busy;
        updateControls();
        updateFeedback();
    }

    function updateControls() {
        updateNavigationButton(elements.previous, "이전", items[state.activeIndex - 1]);
        updateNavigationButton(elements.next, "다음", items[state.activeIndex + 1]);
        elements.previous.disabled = state.busy || state.replaced || state.activeIndex <= 0;
        elements.next.disabled = state.busy
            || state.replaced
            || state.activeIndex < 0
            || state.activeIndex >= items.length - 1;
    }

    function updateNavigationButton(button, direction, targetItem) {
        button.textContent = targetItem
            ? `${direction} 경로 페이지 · ${openButtonLabel(targetItem.dataset.costStatus)}`
            : `${direction} 경로 페이지`;
    }

    function showInkNotice() {
        elements.inkNoticeMessage.textContent =
            "잉크가 부족하여 새 페이지를 열 수 없습니다.";
        elements.inkLink.hidden = false;
        elements.inkNotice.hidden = false;
        elements.inkNotice.focus();
    }

    function clearInkNotice() {
        elements.inkNoticeMessage.textContent = "";
        elements.inkLink.hidden = true;
        elements.inkNotice.hidden = true;
    }

    function hasUnreadPrerequisiteBefore(index) {
        return items.slice(0, index).some(item =>
            item.dataset.prerequisite === "true" && item.dataset.opened !== "true");
    }

    return {start, openItem};
}

function findElements(root) {
    return {
        items: root.querySelectorAll("[data-route-position]"),
        progress: root.querySelector("[data-route-progress]"),
        status: root.querySelector("[data-reader-status]"),
        content: root.querySelector("[data-route-content]"),
        inkNotice: root.querySelector("[data-route-ink-notice]"),
        inkNoticeMessage: root.querySelector("[data-route-ink-notice-message]"),
        inkLink: root.querySelector("[data-route-ink-link]"),
        previous: root.querySelector("[data-route-previous]"),
        next: root.querySelector("[data-route-next]"),
        originalViewerLink: root.querySelector("[data-original-viewer-link]"),
        makeCurrent: root.querySelector("[data-make-current]"),
        deleteRoute: root.querySelector("[data-delete-route]"),
        currentBadge: root.querySelector("[data-current-badge]"),
        completedBadge: root.querySelector("[data-completed-badge]"),
        completedTime: root.querySelector("[data-completed-time]"),
        feedbackGuide: root.querySelector("[data-feedback-guide]"),
        feedbackButtons: root.querySelectorAll("[data-feedback-rating]")
    };
}

function validateOpenMetadata(metadata, bookId, pageNumber, currentViewerSessionId) {
    if (!metadata
            || typeof metadata.viewerSessionId !== "string"
            || metadata.viewerSessionId.length === 0
            || (currentViewerSessionId !== null
                && metadata.viewerSessionId !== currentViewerSessionId)
            || metadata.bookId !== bookId
            || metadata.pageNumber !== pageNumber
            || typeof metadata.owned !== "boolean"
            || !CONTENT_TYPES.has(metadata.contentType)) {
        throw invalidResponseError();
    }
}

function validateRouteSnapshot(route, routeId, bookId) {
    if (!route
            || route.routeId !== routeId
            || route.bookId !== bookId
            || typeof route.current !== "boolean"
            || !(route.completedAt === null || typeof route.completedAt === "string")
            || !(route.rating === null || typeof route.rating === "string")
            || !Array.isArray(route.items)) {
        throw invalidResponseError();
    }
    route.items.forEach(item => {
        if (!item
                || !Number.isInteger(item.position)
                || !Number.isInteger(item.pageNumber)
                || !(item.openedAt === null || typeof item.openedAt === "string")
                || !["ONE_INK", "ACTIVE_RENTAL", "OWNED"]
                    .includes(item.additionalCostStatus)) {
            throw invalidResponseError();
        }
    });
}

function formatDateTime(value) {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
        throw invalidResponseError();
    }
    return new Intl.DateTimeFormat("ko-KR", {
        dateStyle: "medium",
        timeStyle: "short"
    }).format(date);
}

function position(item) {
    return Number(item.dataset.routePosition);
}

function page(item) {
    return Number(item.dataset.pageNumber);
}

function openButtonLabel(status) {
    if (status === "ONE_INK") {
        return "1잉크로 열기";
    }
    if (status === "ACTIVE_RENTAL") {
        return "대여 중 · 열기";
    }
    if (status === "OWNED") {
        return "소장 도서 · 열기";
    }
    throw invalidResponseError();
}

function invalidResponseError() {
    return new ApiRequestError("INVALID_RESPONSE", DEFAULT_ERROR_MESSAGE, 0, null);
}
