import {clearCommonError, showCommonError} from "../common/error-display.js";
import {requestJson} from "../common/request-json.js";

const PORTONE_SDK_URL = "https://cdn.portone.io/v2/browser-sdk.esm.js";
const WON_FORMATTER = new Intl.NumberFormat("ko-KR", {
    style: "currency",
    currency: "KRW",
    maximumFractionDigits: 0
});

let portOneModulePromise;

export function initializeBookDetailPage(root, dependencies = {}) {
    const request = dependencies.request || requestJson;
    const loadPortOne = dependencies.loadPortOne || loadPortOneSdk;
    const bookId = Number(root.dataset.bookId);
    const authenticated = root.dataset.authenticated === "true";
    const paymentEnabled = root.dataset.paymentEnabled === "true";
    const elements = findElements(root);

    let book = null;
    let paymentBusy = false;
    let preparedPayment = null;
    let portOne = null;
    let retryPayment = null;

    elements.purchase.addEventListener("click", () => runPayment(startPayment));
    elements.retry.addEventListener("click", () => {
        if (retryPayment) {
            runPayment(retryPayment);
        }
    });

    const ready = loadBook();
    return {ready};

    async function loadBook() {
        clearCommonError();
        try {
            const response = await request(`/api/books/${encodeURIComponent(bookId)}`);
            validateBook(response, bookId, elements.aiRouteEntry !== null);
            book = response;
            renderBook();
            elements.loading.hidden = true;
            elements.content.hidden = false;
            return response;
        } catch (error) {
            elements.loading.textContent = "도서 정보를 불러오지 못했습니다.";
            showCommonError(error);
            return null;
        }
    }

    function renderBook() {
        elements.category.textContent = book.category;
        elements.title.textContent = book.title;
        elements.author.textContent = book.author;
        elements.description.textContent = book.description ?? "";
        elements.pageCount.textContent = `${book.totalPageCount}페이지`;
        elements.price.textContent = formatWon(book.bookPrice);
        elements.viewer.href = `/books/${book.bookId}/viewer?page=1`;
        elements.viewer.setAttribute("aria-label", `${book.title} 첫 페이지 읽기`);

        if (book.coverImagePath) {
            elements.cover.src = book.coverImagePath;
            elements.cover.alt = `${book.title} 표지`;
            elements.cover.hidden = false;
            elements.coverPlaceholder.hidden = true;
        } else {
            elements.cover.hidden = true;
            elements.coverPlaceholder.hidden = false;
        }

        renderOwnershipState();
        renderAiRouteState();
    }

    function renderAiRouteState() {
        if (!elements.aiRouteEntry) {
            return;
        }
        if (!book.aiRouteSupported) {
            elements.aiRouteEntry.remove();
            return;
        }

        const generationPath = `/books/${book.bookId}/ai-route`;
        if (authenticated) {
            elements.aiRouteDescription.textContent =
                "읽고 싶은 목적에 맞춰 이 책의 추천 페이지 경로를 만들 수 있습니다.";
            elements.aiRouteLink.href = generationPath;
            elements.aiRouteLink.textContent = "AI 독서 경로 만들기";
        } else {
            elements.aiRouteDescription.textContent =
                "로그인하면 읽고 싶은 목적에 맞춘 추천 페이지 경로를 만들 수 있습니다.";
            elements.aiRouteLink.href = `/login?returnTo=${encodeURIComponent(generationPath)}`;
            elements.aiRouteLink.textContent = "로그인 후 경로 만들기";
        }
        elements.aiRouteEntry.hidden = false;
    }

    function renderOwnershipState() {
        if (!authenticated || book.owned === null) {
            elements.summary.hidden = false;
            elements.summary.textContent =
                `도서 원가 ${formatWon(book.bookPrice)}을 원화로 직접 결제해 온라인 소장할 수 있습니다.`;
            elements.purchase.hidden = true;
            elements.login.hidden = false;
            return;
        }

        elements.login.hidden = true;
        if (book.owned) {
            elements.summary.hidden = false;
            elements.summary.textContent =
                "온라인 소장 중입니다. 잉크 차감과 대여 기간 없이 모든 페이지를 읽을 수 있습니다.";
            elements.purchase.hidden = true;
            elements.retry.hidden = true;
            retryPayment = null;
            return;
        }

        elements.summary.hidden = paymentEnabled;
        elements.summary.textContent = paymentEnabled
            ? ""
            : "현재 환경에서는 온라인 소장 결제를 사용할 수 없습니다.";
        elements.purchase.textContent = paymentEnabled
            ? `${formatWon(book.bookPrice)} 소장 결제`
            : "소장 결제 비활성화";
        elements.purchase.hidden = false;
        updatePaymentButtons();
    }

    async function runPayment(action) {
        if (paymentBusy || !book || book.owned || !authenticated || !paymentEnabled) {
            return;
        }

        paymentBusy = true;
        updatePaymentButtons();
        clearCommonError();
        try {
            await action();
        } catch {
            showPaymentStatus(
                "danger",
                "결제 요청을 처리하지 못했습니다. 결제 버튼으로 다시 시도해 주세요.");
        } finally {
            paymentBusy = false;
            updatePaymentButtons();
        }
    }

    async function startPayment() {
        showPaymentStatus("info", "결제 모듈을 불러오고 있습니다.");
        try {
            portOne = await loadPortOne();
        } catch {
            preparedPayment = null;
            showPaymentStatus(
                "danger",
                "결제 모듈을 불러오지 못했습니다. 잠시 후 결제 버튼으로 다시 시도해 주세요.");
            return;
        }

        showPaymentStatus("info", "도서 원가 전액 결제를 준비하고 있습니다.");
        try {
            preparedPayment = await request(
                `/api/books/${encodeURIComponent(book.bookId)}/ownership-payments`,
                {
                    method: "POST",
                    body: null
                });
        } catch (error) {
            preparedPayment = null;
            if (error.code === "BOOK_ALREADY_OWNED") {
                book = {...book, owned: true};
                renderOwnershipState();
                showPaymentStatus(
                    "success",
                    "이미 온라인 소장 중입니다. 모든 페이지를 읽을 수 있습니다.");
                return;
            }
            showPaymentStatus(
                "danger",
                `${error.message} 결제 버튼으로 다시 시도해 주세요.`);
            return;
        }

        await openPreparedPayment();
    }

    async function openPreparedPayment() {
        showPaymentStatus("info", "결제창에서 도서 원가 전액을 카드로 결제해 주세요.");
        let response;
        try {
            response = await portOne.requestPayment({
                storeId: preparedPayment.storeId,
                channelKey: preparedPayment.channelKey,
                paymentId: preparedPayment.paymentId,
                orderName: preparedPayment.orderName,
                totalAmount: preparedPayment.totalAmount,
                currency: preparedPayment.currency,
                payMethod: "CARD"
            });
        } catch {
            await completePayment(true);
            return;
        }

        if (!response || response.code !== undefined) {
            await completePayment(true);
            return;
        }

        await completePayment();
    }

    async function completePayment(reopenWhenPending = false) {
        showPaymentStatus("info", "결제 결과를 서버에서 확인하고 있습니다.");
        try {
            const response = await request(
                `/api/ownership-payments/${encodeURIComponent(preparedPayment.paymentId)}/complete`,
                {
                    method: "POST",
                    body: null
                });

            if (response.status === "PAID" && response.owned === true) {
                preparedPayment = null;
                book = {...book, owned: true};
                renderOwnershipState();
                showPaymentStatus(
                    "success",
                    "온라인 소장이 완료되었습니다. 이제 모든 페이지를 잉크 차감 없이 읽을 수 있습니다.");
                return;
            }

            showPaymentStatus(
                "warning",
                reopenWhenPending
                    ? "[PENDING] 결제가 완료되지 않았습니다. 별도 취소 기록 없이 기존 결제창을 다시 열 수 있습니다."
                    : "[PENDING] 아직 결제가 확인되지 않았습니다. 잠시 후 같은 결제 결과를 다시 확인해 주세요.",
                reopenWhenPending ? "결제창 다시 열기" : "결제 결과 다시 확인",
                reopenWhenPending ? openPreparedPayment : completePayment);
        } catch (error) {
            if (error.status === 409 || error.status === 422) {
                preparedPayment = null;
                showPaymentStatus(
                    "danger",
                    `[FAILED] ${error.message} 온라인 소장은 부여되지 않았으며 잉크도 변경되지 않았습니다.`);
                return;
            }
            if (error.status === 503) {
                showPaymentStatus(
                    "warning",
                    "[PENDING] 결제 확인 서비스에 일시적인 문제가 있습니다. 기존 결제 결과를 다시 확인해 주세요.",
                    "결제 결과 다시 확인",
                    completePayment);
                return;
            }

            showPaymentStatus(
                "danger",
                `${error.message} 기존 결제 결과를 다시 확인해 주세요.`,
                "결제 결과 다시 확인",
                completePayment);
        }
    }

    function showPaymentStatus(kind, message, retryLabel, retryAction) {
        const color = {
            success: "success",
            warning: "warning",
            danger: "danger",
            info: "info"
        }[kind];

        elements.paymentStatus.className = `alert alert-${color} mt-3 mb-0`;
        elements.paymentStatus.textContent = message;
        elements.paymentStatus.hidden = false;
        elements.paymentStatus.focus();

        retryPayment = retryAction || null;
        elements.retry.textContent = retryLabel || "";
        elements.retry.hidden = !retryPayment;
        updatePaymentButtons();
    }

    function updatePaymentButtons() {
        elements.purchase.disabled =
            paymentBusy || !paymentEnabled || preparedPayment !== null;
        elements.retry.disabled = paymentBusy;
    }
}

async function loadPortOneSdk() {
    if (!portOneModulePromise) {
        portOneModulePromise = import(PORTONE_SDK_URL)
            .then((portOne) => {
                if (typeof portOne.requestPayment !== "function") {
                    throw new Error("PortOne requestPayment를 찾을 수 없습니다.");
                }
                return portOne;
            })
            .catch((error) => {
                portOneModulePromise = null;
                throw error;
            });
    }
    return portOneModulePromise;
}

function findElements(root) {
    return {
        loading: requiredElement(root, "[data-book-detail-loading]"),
        content: requiredElement(root, "[data-book-detail-content]"),
        cover: requiredElement(root, "[data-book-cover]"),
        coverPlaceholder: requiredElement(root, "[data-book-cover-placeholder]"),
        category: requiredElement(root, "[data-book-category]"),
        title: requiredElement(root, "[data-book-title]"),
        author: requiredElement(root, "[data-book-author]"),
        description: requiredElement(root, "[data-book-description]"),
        pageCount: requiredElement(root, "[data-book-page-count]"),
        price: requiredElement(root, "[data-book-price]"),
        viewer: requiredElement(root, "[data-book-viewer-link]"),
        summary: requiredElement(root, "[data-ownership-summary]"),
        purchase: requiredElement(root, "[data-ownership-purchase]"),
        login: requiredElement(root, "[data-ownership-login]"),
        paymentStatus: requiredElement(root, "[data-ownership-payment-status]"),
        retry: requiredElement(root, "[data-ownership-payment-retry]"),
        aiRouteEntry: root.querySelector("[data-ai-route-entry]"),
        aiRouteDescription: optionalChild(root, "[data-ai-route-entry]", "[data-ai-route-description]"),
        aiRouteLink: optionalChild(root, "[data-ai-route-entry]", "[data-ai-route-link]")
    };
}

function optionalChild(root, parentSelector, childSelector) {
    const parent = root.querySelector(parentSelector);
    return parent ? requiredElement(parent, childSelector) : null;
}

function requiredElement(root, selector) {
    const element = root.querySelector(selector);
    if (!element) {
        throw new Error(`필수 도서 상세 화면 요소가 없습니다: ${selector}`);
    }
    return element;
}

function validateBook(book, expectedBookId, aiRouteEnabled) {
    const validIdentity = Number.isInteger(book?.bookId) && book.bookId === expectedBookId;
    const validBookText = typeof book?.category === "string"
        && typeof book.title === "string"
        && typeof book.author === "string"
        && (book.description === null || typeof book.description === "string");
    const validBookNumbers = Number.isInteger(book?.totalPageCount) && book.totalPageCount > 0
        && Number.isInteger(book.bookPrice) && book.bookPrice > 0;
    const validCover = book?.coverImagePath === null || typeof book.coverImagePath === "string";
    const validOwnership = book?.owned === null || typeof book.owned === "boolean";
    const validAiRouteSupport = aiRouteEnabled
        ? typeof book?.aiRouteSupported === "boolean"
        : book?.aiRouteSupported === undefined;

    if (!validIdentity || !validBookText || !validBookNumbers || !validCover
            || !validOwnership || !validAiRouteSupport) {
        throw new Error("도서 상세 API 응답 형식이 올바르지 않습니다.");
    }
}

function formatWon(value) {
    return WON_FORMATTER.format(value);
}
