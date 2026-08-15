import {clearCommonError, showCommonError} from "../common/error-display.js";
import {requestJson} from "../common/request-json.js";

export function createLibraryPage(root, dependencies = {}) {
    const request = dependencies.request || requestJson;
    const elements = findElements(root);

    async function start() {
        clearCommonError();
        elements.status.textContent = "내 서재를 불러오고 있습니다.";
        elements.empty.hidden = true;

        try {
            const response = await request("/api/library");
            validateResponse(response, elements.aiRouteEnabled);
            renderEntries(response.entries);
            return response;
        } catch (error) {
            elements.status.textContent = "내 서재를 불러오지 못했습니다.";
            showCommonError(error);
            return null;
        }
    }

    function renderEntries(entries) {
        elements.list.replaceChildren();
        if (entries.length === 0) {
            elements.status.textContent = "서재에 등록된 도서가 없습니다.";
            elements.empty.hidden = false;
            return;
        }

        elements.empty.hidden = true;
        elements.status.textContent = `서재에서 ${entries.length}권을 찾았습니다.`;
        for (const entry of entries) {
            elements.list.append(createCard(entry, elements.template));
        }
    }

    return {start};
}

function createCard(entry, template) {
    const card = template.content.cloneNode(true);
    const title = card.querySelector("[data-library-title]");
    const category = card.querySelector("[data-library-category]");
    const lastPage = card.querySelector("[data-library-last-page]");
    const cover = card.querySelector("[data-library-cover]");
    const coverPlaceholder = card.querySelector("[data-library-cover-placeholder]");
    const access = card.querySelector("[data-library-access]");
    const rental = card.querySelector("[data-library-rental]");
    const rentedAt = card.querySelector("[data-library-rented-at]");
    const expiresAt = card.querySelector("[data-library-expires-at]");
    const owned = card.querySelector("[data-library-owned]");
    const resume = card.querySelector("[data-library-resume]");
    const aiRoutes = card.querySelector("[data-library-ai-routes]");
    const routeOnly = isRouteOnlyEntry(entry);

    title.textContent = entry.title;
    category.textContent = entry.category;
    lastPage.textContent = routeOnly
        ? "아직 읽은 페이지가 없습니다."
        : `마지막으로 읽은 페이지: ${entry.lastPageNumber}쪽`;
    resume.href = `/books/${entry.bookId}/viewer?page=${entry.lastPageNumber}`;
    resume.textContent = routeOnly ? "첫 페이지 읽기" : "이어서 읽기";
    resume.setAttribute(
        "aria-label",
        routeOnly
            ? `${entry.title} 첫 페이지 읽기`
            : `${entry.title} ${entry.lastPageNumber}페이지부터 이어서 읽기`);

    if (entry.coverImagePath) {
        cover.src = entry.coverImagePath;
        cover.alt = `${entry.title} 표지`;
    } else {
        cover.hidden = true;
        coverPlaceholder.hidden = false;
    }

    if (entry.owned) {
        access.textContent = "온라인 소장";
        access.classList.add("text-bg-primary");
        rental.hidden = true;
        owned.hidden = false;
    } else if (routeOnly) {
        access.textContent = "AI 경로 저장";
        access.classList.add("text-bg-info");
        rental.hidden = true;
    } else {
        access.textContent = entry.activeRental ? "대여 중" : "대여 만료";
        access.classList.add(entry.activeRental ? "text-bg-success" : "text-bg-secondary");
        rentedAt.textContent = `대여 시작: ${formatInstant(entry.rentedAt)}`;
        expiresAt.textContent = `대여 만료: ${formatInstant(entry.expiresAt)}`;
    }

    renderAiRoutes(entry, aiRoutes);

    return card;
}

function renderAiRoutes(entry, section) {
    if (!section || entry.routes.length === 0) {
        return;
    }

    const list = requiredElement(section, "[data-library-route-list]");
    for (const route of entry.routes) {
        const item = document.createElement("li");
        const link = document.createElement("a");
        link.href = `/ai-routes/${route.routeId}`;
        link.textContent = route.purpose;
        item.append(link);

        if (route.routeId === entry.currentRouteId) {
            const badge = document.createElement("span");
            badge.className = "badge text-bg-primary ms-2";
            badge.textContent = "현재 경로";
            item.append(badge);
        }
        list.append(item);
    }
    section.hidden = false;
}

function findElements(root) {
    const template = requiredElement(root, "[data-library-card-template]");
    return {
        status: requiredElement(root, "[data-library-status]"),
        list: requiredElement(root, "[data-library-list]"),
        empty: requiredElement(root, "[data-library-empty]"),
        template,
        aiRouteEnabled: template.content.querySelector("[data-library-ai-routes]") !== null
    };
}

function requiredElement(root, selector) {
    const element = root.querySelector(selector);
    if (!element) {
        throw new Error(`필수 서재 화면 요소가 없습니다: ${selector}`);
    }
    return element;
}

function validateResponse(response, aiRouteEnabled) {
    if (!response || !Array.isArray(response.entries)) {
        throw new Error("서재 API 응답 형식이 올바르지 않습니다.");
    }
    response.entries.forEach((entry) => validateEntry(entry, aiRouteEnabled));
}

function validateEntry(entry, aiRouteEnabled) {
    const hasIdentity = Number.isInteger(entry?.bookId) && entry.bookId > 0
        && Number.isInteger(entry.lastPageNumber) && entry.lastPageNumber > 0;
    const hasBookText = typeof entry?.title === "string"
        && typeof entry.category === "string";
    const hasAiRoutes = aiRouteEnabled
        ? validAiRoutes(entry)
        : entry.routes === undefined && entry.currentRouteId === undefined;
    const routeOnly = aiRouteEnabled && isRouteOnlyEntry(entry);
    const hasAccess = typeof entry?.owned === "boolean" && (entry.owned
        ? entry.rentedAt === null && entry.expiresAt === null && entry.activeRental === null
        : routeOnly || (isInstant(entry.rentedAt)
            && isInstant(entry.expiresAt)
            && typeof entry.activeRental === "boolean"));
    if (!hasIdentity || !hasBookText || !hasAccess || !hasAiRoutes) {
        throw new Error("서재 API 응답 형식이 올바르지 않습니다.");
    }
}

function isRouteOnlyEntry(entry) {
    return entry?.owned === false
        && entry.rentedAt === null
        && entry.expiresAt === null
        && entry.activeRental === null
        && Array.isArray(entry.routes)
        && entry.routes.length > 0;
}

function validAiRoutes(entry) {
    if (!Array.isArray(entry.routes)) {
        return false;
    }
    const validRoutes = entry.routes.every((route) =>
        Number.isInteger(route?.routeId) && route.routeId > 0
        && typeof route.purpose === "string");
    const validCurrentRoute = entry.currentRouteId === undefined
        || (Number.isInteger(entry.currentRouteId)
            && entry.routes.some((route) => route.routeId === entry.currentRouteId));
    return validRoutes && validCurrentRoute;
}

function isInstant(value) {
    return typeof value === "string" && !Number.isNaN(Date.parse(value));
}

function formatInstant(value) {
    return new Intl.DateTimeFormat("ko-KR", {
        dateStyle: "medium",
        timeStyle: "short"
    }).format(new Date(value));
}
