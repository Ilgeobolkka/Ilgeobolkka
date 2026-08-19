import {clearCommonError, showCommonError} from "../common/error-display.js";
import {requestJson} from "../common/request-json.js";

const root = document.querySelector("[data-book-list-root]");

if (root) {
    const elements = findElements(root);
    const initialQuery = new URLSearchParams(window.location.search);
    let currentPage = parsePage(initialQuery.get("page"));
    let totalPages = 0;
    let keyword = (initialQuery.get("keyword") || "").trim();
    let category = (initialQuery.get("category") || "").trim();
    let requestSequence = 0;

    elements.search.value = keyword;
    elements.searchForm.addEventListener("submit", (event) => {
        event.preventDefault();
        keyword = elements.search.value.trim();
        loadPage(1, "push");
    });
    elements.category.addEventListener("change", () => {
        category = elements.category.value;
        loadPage(1, "push");
    });
    elements.previous.addEventListener("click", () => {
        const previousPage = currentPage > totalPages && totalPages > 0
            ? totalPages
            : currentPage - 1;
        loadPage(previousPage, "push");
    });
    elements.next.addEventListener("click", () => loadPage(currentPage + 1, "push"));
    window.addEventListener("popstate", () => {
        const query = new URLSearchParams(window.location.search);
        keyword = (query.get("keyword") || "").trim();
        category = (query.get("category") || "").trim();
        elements.search.value = keyword;
        elements.category.value = category;
        loadPage(parsePage(query.get("page")));
    });

    loadPage(currentPage, "replace");

    async function loadPage(page, historyMode) {
        const sequence = ++requestSequence;
        currentPage = page;
        clearCommonError();
        elements.status.textContent = "도서 목록을 불러오고 있습니다.";
        elements.empty.hidden = true;
        elements.previous.disabled = true;
        elements.next.disabled = true;

        const query = new URLSearchParams({page: String(currentPage)});
        if (keyword) {
            query.set("keyword", keyword);
        }
        if (category) {
            query.set("category", category);
        }

        try {
            const response = await requestJson(`/api/books?${query}`);
            if (sequence !== requestSequence) {
                return;
            }
            validateResponse(response);
            currentPage = response.page;
            totalPages = response.totalPages;
            category = response.selectedCategory || "";
            renderCategories(elements.category, response.categories, category);
            renderBooks(response.books);
            renderPage(response);
            updateHistory(query, historyMode);
        } catch (error) {
            if (sequence !== requestSequence) {
                return;
            }
            elements.list.replaceChildren();
            elements.status.textContent = "도서 목록을 불러오지 못했습니다.";
            elements.page.textContent = "페이지 정보를 불러오지 못했습니다.";
            showCommonError(error);
        }
    }

    function renderBooks(books) {
        elements.list.replaceChildren();
        if (books.length === 0) {
            renderEmpty();
            return;
        }

        elements.empty.hidden = true;
        for (const book of books) {
            elements.list.append(createBookCard(book, elements.template));
        }
    }

    function renderEmpty() {
        elements.empty.hidden = false;
        if (totalPages > 0 && currentPage > totalPages) {
            elements.emptyTitle.textContent = "요청한 페이지에 도서가 없습니다.";
            elements.emptyDescription.textContent = "이전 버튼을 누르면 마지막 페이지로 이동합니다.";
            return;
        }
        if (keyword && category) {
            elements.emptyTitle.textContent = "선택한 카테고리에서 검색 결과가 없습니다.";
            elements.emptyDescription.textContent = "다른 카테고리나 검색어로 다시 찾아보세요.";
            return;
        }
        if (keyword) {
            elements.emptyTitle.textContent = "검색 결과가 없습니다.";
            elements.emptyDescription.textContent = "다른 제목이나 저자로 다시 검색해 보세요.";
            return;
        }
        if (category) {
            elements.emptyTitle.textContent = "선택한 카테고리에 도서가 없습니다.";
            elements.emptyDescription.textContent = "전체 또는 다른 카테고리를 선택해 보세요.";
            return;
        }
        elements.emptyTitle.textContent = "표시할 도서가 없습니다.";
        elements.emptyDescription.textContent = "등록된 도서가 생기면 여기에 표시됩니다.";
    }

    function renderPage(response) {
        const resultStatus = response.books.length === 0
            ? `전체 ${formatNumber(response.totalCount)}권 중 표시할 도서가 없습니다.`
            : `전체 ${formatNumber(response.totalCount)}권 중 ${response.books.length}권을 표시했습니다.`;
        const pageStatus = response.totalPages === 0
            ? "전체 페이지가 없습니다."
            : `${response.page}페이지, 전체 ${response.totalPages}페이지입니다.`;
        elements.status.textContent = `${resultStatus} ${pageStatus}`;
        elements.page.textContent = `${response.page}페이지 / 전체 ${response.totalPages}페이지`;
        elements.previous.disabled = response.totalPages === 0 || response.page <= 1;
        elements.next.disabled = response.totalPages === 0 || response.page >= response.totalPages;
    }

    function updateHistory(query, historyMode) {
        const url = `/books?${query}`;
        if (historyMode === "replace") {
            window.history.replaceState(null, "", url);
        } else if (historyMode === "push"
                && url !== `${window.location.pathname}${window.location.search}`) {
            window.history.pushState(null, "", url);
        }
    }
}

function renderCategories(select, categories, selectedCategory) {
    const all = document.createElement("option");
    all.value = "";
    all.textContent = "전체";
    select.replaceChildren(all);

    categories.forEach(category => {
        const option = document.createElement("option");
        option.value = category;
        option.textContent = category;
        select.append(option);
    });
    select.value = selectedCategory || "";
}

function createBookCard(book, template) {
    const card = template.content.cloneNode(true);
    const cover = card.querySelector("[data-book-cover]");
    const coverPlaceholder = card.querySelector("[data-book-cover-placeholder]");
    const title = card.querySelector("[data-book-title]");

    if (book.coverImagePath) {
        cover.src = book.coverImagePath;
        cover.alt = `${book.title} 표지`;
    } else {
        cover.hidden = true;
        coverPlaceholder.hidden = false;
    }
    card.querySelector("[data-book-category]").textContent = book.category;
    title.textContent = book.title;
    title.href = `/books/${book.bookId}`;
    card.querySelector("[data-book-author]").textContent = book.author;
    return card;
}

function findElements(root) {
    return {
        searchForm: requiredElement(root, "[data-book-search-form]"),
        search: requiredElement(root, "[data-book-search]"),
        category: requiredElement(root, "[data-book-category-filter]"),
        status: requiredElement(root, "[data-book-status]"),
        list: requiredElement(root, "[data-book-list]"),
        empty: requiredElement(root, "[data-book-empty]"),
        emptyTitle: requiredElement(root, "[data-book-empty-title]"),
        emptyDescription: requiredElement(root, "[data-book-empty-description]"),
        template: requiredElement(root, "[data-book-card-template]"),
        previous: requiredElement(root, "[data-book-previous]"),
        next: requiredElement(root, "[data-book-next]"),
        page: requiredElement(root, "[data-book-page]")
    };
}

function requiredElement(root, selector) {
    const element = root.querySelector(selector);
    if (!element) {
        throw new Error(`필수 도서 목록 화면 요소가 없습니다: ${selector}`);
    }
    return element;
}

function parsePage(value) {
    const page = Number(value);
    return Number.isInteger(page) && page > 0 ? page : 1;
}

function validateResponse(response) {
    const hasPage = Number.isInteger(response?.page) && response.page > 0
        && Number.isInteger(response.totalPages) && response.totalPages >= 0
        && Number.isInteger(response.totalCount) && response.totalCount >= 0;
    const hasCategories = Array.isArray(response?.categories)
        && response.categories.every(category => typeof category === "string")
        && (response.selectedCategory === null
            || (typeof response.selectedCategory === "string"
                && response.categories.includes(response.selectedCategory)));
    if (!hasPage || !Array.isArray(response.books) || !hasCategories) {
        throw new Error("도서 목록 API 응답 형식이 올바르지 않습니다.");
    }
    response.books.forEach(validateBook);
}

function validateBook(book) {
    const hasIdentity = Number.isInteger(book?.bookId) && book.bookId > 0;
    const hasText = typeof book?.category === "string"
        && typeof book.title === "string"
        && typeof book.author === "string";
    const hasCover = book?.coverImagePath === null || typeof book.coverImagePath === "string";
    if (!hasIdentity || !hasText || !hasCover
            || !Number.isInteger(book.bookPrice) || book.bookPrice <= 0) {
        throw new Error("도서 목록 API 응답 형식이 올바르지 않습니다.");
    }
}

function formatNumber(value) {
    return new Intl.NumberFormat("ko-KR").format(value);
}
