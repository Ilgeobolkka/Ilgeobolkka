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
            validateResponse(response);
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

    title.textContent = entry.title;
    category.textContent = entry.category;
    lastPage.textContent = `마지막으로 읽은 페이지: ${entry.lastPageNumber}쪽`;
    resume.href = `/books/${entry.bookId}/viewer?page=${entry.lastPageNumber}`;
    resume.setAttribute("aria-label", `${entry.title} ${entry.lastPageNumber}페이지부터 이어서 읽기`);

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
    } else {
        access.textContent = entry.activeRental ? "대여 중" : "대여 만료";
        access.classList.add(entry.activeRental ? "text-bg-success" : "text-bg-secondary");
        rentedAt.textContent = `대여 시작: ${formatInstant(entry.rentedAt)}`;
        expiresAt.textContent = `대여 만료: ${formatInstant(entry.expiresAt)}`;
    }

    return card;
}

function findElements(root) {
    return {
        status: requiredElement(root, "[data-library-status]"),
        list: requiredElement(root, "[data-library-list]"),
        empty: requiredElement(root, "[data-library-empty]"),
        template: requiredElement(root, "[data-library-card-template]")
    };
}

function requiredElement(root, selector) {
    const element = root.querySelector(selector);
    if (!element) {
        throw new Error(`필수 서재 화면 요소가 없습니다: ${selector}`);
    }
    return element;
}

function validateResponse(response) {
    if (!response || !Array.isArray(response.entries)) {
        throw new Error("서재 API 응답 형식이 올바르지 않습니다.");
    }
    response.entries.forEach(validateEntry);
}

function validateEntry(entry) {
    const hasIdentity = Number.isInteger(entry?.bookId) && entry.bookId > 0
        && Number.isInteger(entry.lastPageNumber) && entry.lastPageNumber > 0;
    const hasBookText = typeof entry?.title === "string"
        && typeof entry.category === "string";
    const hasAccess = typeof entry?.owned === "boolean"
        && (entry.activeRental === null || typeof entry.activeRental === "boolean");
    const hasRental = entry.owned
        ? entry.rentedAt === null && entry.expiresAt === null && entry.activeRental === null
        : isInstant(entry.rentedAt) && isInstant(entry.expiresAt);
    if (!hasIdentity || !hasBookText || !hasAccess || !hasRental) {
        throw new Error("서재 API 응답 형식이 올바르지 않습니다.");
    }
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
