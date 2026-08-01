import {clearCommonError, showCommonError} from "../common/error-display.js";
import {requestJson} from "../common/request-json.js";

const WON_FORMATTER = new Intl.NumberFormat("ko-KR", {
    style: "currency",
    currency: "KRW",
    maximumFractionDigits: 0
});
const DATE_TIME_FORMATTER = new Intl.DateTimeFormat("ko-KR", {
    dateStyle: "medium",
    timeStyle: "short"
});

export function createOwnershipHistoryPage(root, dependencies = {}) {
    const request = dependencies.request || requestJson;
    const elements = findElements(root);
    let currentPage = 1;
    let totalPages = 0;

    elements.previous.addEventListener("click", () => load(currentPage - 1));
    elements.next.addEventListener("click", () => load(currentPage + 1));

    async function start() {
        return load(1);
    }

    async function load(pageNumber) {
        if (pageNumber < 1) {
            return null;
        }

        setButtonsDisabled(true);
        clearCommonError();
        elements.status.textContent = "소장 결제 내역을 불러오고 있습니다.";
        try {
            const response = await request(
                `/api/ownership-payments?page=${encodeURIComponent(pageNumber)}`);
            validateResponse(response);
            render(response);
            return response;
        } catch (error) {
            elements.status.textContent = "소장 결제 내역을 불러오지 못했습니다.";
            showCommonError(error);
            return null;
        } finally {
            updateButtons();
        }
    }

    function render(response) {
        currentPage = response.page;
        totalPages = response.totalPages;
        elements.list.replaceChildren();

        if (response.payments.length === 0) {
            elements.list.append(createEmptyRow());
            elements.status.textContent = "완료된 소장 결제 내역이 없습니다.";
        } else {
            const fragment = document.createDocumentFragment();
            response.payments.forEach((payment) => fragment.append(createPaymentRow(payment)));
            elements.list.append(fragment);
            elements.status.textContent = `완료된 소장 결제 ${response.totalCount}건을 찾았습니다.`;
        }

        elements.page.textContent = totalPages === 0
            ? "내역 없음"
            : `${currentPage} / ${totalPages}페이지`;
    }

    function updateButtons() {
        elements.previous.disabled = currentPage <= 1;
        elements.next.disabled = totalPages === 0 || currentPage >= totalPages;
    }

    function setButtonsDisabled(disabled) {
        elements.previous.disabled = disabled;
        elements.next.disabled = disabled;
    }

    return {start};
}

function createPaymentRow(payment) {
    const row = document.createElement("tr");
    const bookCell = document.createElement("td");
    const bookLink = document.createElement("a");
    const amountCell = document.createElement("td");
    const paidAtCell = document.createElement("td");
    const paidAt = document.createElement("time");
    const ownershipCell = document.createElement("td");
    const ownership = document.createElement("span");

    bookLink.href = `/books/${payment.bookId}`;
    bookLink.textContent = payment.bookTitle;
    bookCell.append(bookLink);
    amountCell.textContent = WON_FORMATTER.format(payment.amountWon);
    paidAt.dateTime = payment.paidAt;
    paidAt.textContent = DATE_TIME_FORMATTER.format(new Date(payment.paidAt));
    paidAtCell.append(paidAt);
    ownership.className = payment.owned
        ? "badge text-bg-primary"
        : "badge text-bg-secondary";
    ownership.textContent = payment.owned ? "온라인 소장 중" : "소장 상태 확인 필요";
    ownershipCell.append(ownership);

    row.append(bookCell, amountCell, paidAtCell, ownershipCell);
    return row;
}

function createEmptyRow() {
    const row = document.createElement("tr");
    const cell = document.createElement("td");
    cell.className = "text-secondary text-center py-4";
    cell.colSpan = 4;
    cell.textContent = "완료된 소장 결제 내역이 없습니다.";
    row.append(cell);
    return row;
}

function findElements(root) {
    return {
        status: requiredElement(root, "[data-ownership-history-status]"),
        list: requiredElement(root, "[data-ownership-history-list]"),
        page: requiredElement(root, "[data-ownership-history-page]"),
        previous: requiredElement(root, "[data-ownership-history-previous]"),
        next: requiredElement(root, "[data-ownership-history-next]")
    };
}

function requiredElement(root, selector) {
    const element = root.querySelector(selector);
    if (!element) {
        throw new Error(`필수 소장 결제 내역 화면 요소가 없습니다: ${selector}`);
    }
    return element;
}

function validateResponse(response) {
    const validPage = Number.isInteger(response?.page) && response.page > 0;
    const validTotals = Number.isInteger(response?.totalPages) && response.totalPages >= 0
        && Number.isInteger(response.totalCount) && response.totalCount >= 0;
    if (!Array.isArray(response?.payments) || !validPage || !validTotals) {
        throw new Error("소장 결제 내역 API 응답 형식이 올바르지 않습니다.");
    }
    response.payments.forEach(validatePayment);
}

function validatePayment(payment) {
    const validIdentity = typeof payment?.paymentId === "string"
        && Number.isInteger(payment.bookId) && payment.bookId > 0;
    const validBook = typeof payment?.bookTitle === "string"
        && Number.isInteger(payment.amountWon) && payment.amountWon > 0;
    const validPayment = typeof payment?.paidAt === "string"
        && !Number.isNaN(Date.parse(payment.paidAt))
        && typeof payment.owned === "boolean";
    if (!validIdentity || !validBook || !validPayment) {
        throw new Error("소장 결제 내역 API 응답 형식이 올바르지 않습니다.");
    }
}
