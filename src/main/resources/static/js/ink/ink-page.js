import {clearCommonError, showCommonError} from "../common/error-display.js";
import {requestJson} from "../common/request-json.js";

const PORTONE_SDK_URL = "https://cdn.portone.io/v2/browser-sdk.esm.js";
const DATE_TIME_FORMATTER = new Intl.DateTimeFormat("ko-KR", {
    dateStyle: "medium",
    timeStyle: "short"
});

let portOneModulePromise;

export function initializeInkPage(
    page,
    {
        request = requestJson,
        loadPortOne = loadPortOneSdk
    } = {}
) {
    const balance = page.querySelector("[data-ink-balance]");
    const purchaseButton = page.querySelector("[data-ink-purchase]");
    const paymentStatus = page.querySelector("[data-ink-payment-status]");
    const paymentRetryButton = page.querySelector("[data-ink-payment-retry]");
    const ledgerBody = page.querySelector("[data-ink-ledger]");
    const ledgerPage = page.querySelector("[data-ink-ledger-page]");
    const previousButton = page.querySelector("[data-ink-ledger-previous]");
    const nextButton = page.querySelector("[data-ink-ledger-next]");

    let currentPage = 1;
    let totalPages = 0;
    let paymentBusy = false;
    let preparedPayment = null;
    let portOne = null;
    let retryPayment = null;

    purchaseButton.addEventListener("click", () => runPayment(startPurchase));
    paymentRetryButton.addEventListener("click", () => {
        if (retryPayment) {
            runPayment(retryPayment);
        }
    });
    previousButton.addEventListener("click", () => loadLedger(currentPage - 1));
    nextButton.addEventListener("click", () => loadLedger(currentPage + 1));

    const ready = Promise.all([loadBalance(), loadLedger(1)]);
    return {ready};

    async function loadBalance() {
        try {
            const response = await request("/api/ink/balance");
            balance.textContent = `${response.balance}잉크`;
        } catch (error) {
            balance.textContent = "확인할 수 없음";
            showCommonError(error);
        }
    }

    async function loadLedger(pageNumber) {
        if (pageNumber < 1) {
            return;
        }

        setLedgerButtonsDisabled(true);
        clearCommonError();
        try {
            const response = await request(`/api/ink/ledger?page=${pageNumber}`);
            renderLedger(response);
        } catch (error) {
            showCommonError(error);
        } finally {
            updateLedgerButtons();
        }
    }

    function renderLedger(response) {
        currentPage = response.page;
        totalPages = response.totalPages;
        ledgerBody.replaceChildren();

        if (response.entries.length === 0) {
            const row = document.createElement("tr");
            const cell = document.createElement("td");
            cell.className = "text-secondary text-center py-4";
            cell.colSpan = 4;
            cell.textContent = "표시할 잉크 내역이 없습니다.";
            row.append(cell);
            ledgerBody.append(row);
        } else {
            const fragment = document.createDocumentFragment();
            response.entries.forEach((entry) => fragment.append(createLedgerRow(entry)));
            ledgerBody.append(fragment);
        }

        ledgerPage.textContent = totalPages === 0
            ? "내역 없음"
            : `${currentPage} / ${totalPages}페이지`;
    }

    function createLedgerRow(entry) {
        const row = document.createElement("tr");
        row.append(
            createChangeCell(entry),
            createDetailCell(entry),
            createTextCell(`${entry.balanceAfter}잉크`),
            createTimeCell(entry.occurredAt)
        );
        return row;
    }

    function createChangeCell(entry) {
        const cell = document.createElement("td");
        const type = document.createElement("span");
        const amount = document.createElement("span");

        type.className = entry.type === "GRANT"
            ? "badge text-bg-success"
            : "badge text-bg-secondary";
        type.textContent = entry.type === "GRANT" ? "지급" : "차감";
        amount.className = "d-block mt-1 fw-semibold";
        amount.textContent = `${entry.type === "GRANT" ? "+" : "-"}${entry.amount}잉크`;

        cell.append(type, amount);
        return cell;
    }

    function createDetailCell(entry) {
        const cell = document.createElement("td");
        if (entry.type === "GRANT") {
            cell.textContent = "잉크 구매 지급";
            return cell;
        }

        const book = document.createElement("strong");
        const pageNumber = document.createElement("span");
        const rentalPeriod = document.createElement("span");

        book.className = "d-block";
        book.textContent = entry.bookTitle;
        pageNumber.className = "d-block";
        pageNumber.textContent = `원본 ${entry.pageNumber}페이지`;
        rentalPeriod.className = "d-block small text-secondary";
        rentalPeriod.textContent =
            `대여 ${formatDateTime(entry.rentedAt)} · 만료 ${formatDateTime(entry.expiresAt)}`;

        cell.append(book, pageNumber, rentalPeriod);
        return cell;
    }

    function createTextCell(text) {
        const cell = document.createElement("td");
        cell.textContent = text;
        return cell;
    }

    function createTimeCell(value) {
        const cell = document.createElement("td");
        const time = document.createElement("time");
        time.dateTime = value;
        time.textContent = formatDateTime(value);
        cell.append(time);
        return cell;
    }

    function updateLedgerButtons() {
        previousButton.disabled = currentPage <= 1;
        nextButton.disabled = totalPages === 0 || currentPage >= totalPages;
    }

    function setLedgerButtonsDisabled(disabled) {
        previousButton.disabled = disabled;
        nextButton.disabled = disabled;
    }

    async function runPayment(action) {
        if (paymentBusy) {
            return;
        }

        paymentBusy = true;
        updatePaymentButtons();
        clearCommonError();
        try {
            await action();
        } finally {
            paymentBusy = false;
            updatePaymentButtons();
        }
    }

    async function startPurchase() {
        showPaymentStatus("info", "결제 모듈을 불러오고 있습니다.");
        try {
            portOne = await loadPortOne();
        } catch {
            preparedPayment = null;
            showPaymentStatus(
                "danger",
                "결제 모듈을 불러오지 못했습니다. 잠시 후 구매 버튼으로 다시 시도해 주세요.");
            return;
        }

        showPaymentStatus("info", "결제를 준비하고 있습니다.");
        try {
            preparedPayment = await request("/api/ink/purchases", {
                method: "POST",
                body: null
            });
        } catch (error) {
            preparedPayment = null;
            showPaymentStatus(
                "danger",
                `${error.message} 구매 버튼으로 다시 시도해 주세요.`);
            return;
        }

        await openPreparedPayment();
    }

    async function openPreparedPayment() {
        showPaymentStatus("info", "결제창에서 카드 결제를 진행해 주세요.");
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
            showInterruptedPayment();
            return;
        }

        if (!response || response.code !== undefined) {
            showInterruptedPayment();
            return;
        }

        await completePayment();
    }

    function showInterruptedPayment() {
        showPaymentStatus(
            "warning",
            "[PENDING] 결제가 완료되지 않았습니다. 기존 결제를 유지한 채 결제창을 다시 열 수 있습니다.",
            "결제창 다시 열기",
            openPreparedPayment);
    }

    async function completePayment() {
        showPaymentStatus("info", "결제 결과를 확인하고 있습니다.");
        try {
            const response = await request(
                `/api/ink/purchases/${encodeURIComponent(preparedPayment.paymentId)}/complete`,
                {
                    method: "POST",
                    body: null
                });
            if (response.status === "PAID") {
                preparedPayment = null;
                showPaymentStatus(
                    "success",
                    `[PAID] ${response.grantedInk}잉크가 지급되었습니다. 현재 잔액은 ${response.inkBalance}잉크입니다.`);
                await Promise.all([loadBalance(), loadLedger(1)]);
                return;
            }

            showPaymentStatus(
                "warning",
                "[PENDING] 아직 결제가 확인되지 않았습니다. 잠시 후 같은 결제 결과를 다시 확인해 주세요.",
                "결제 결과 다시 확인",
                completePayment);
        } catch (error) {
            if (error.status === 409 || error.status === 422) {
                preparedPayment = null;
                showPaymentStatus(
                    "danger",
                    `[FAILED] ${error.message} 잉크는 지급되지 않았습니다. 새 구매로 다시 시도해 주세요.`);
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

        paymentStatus.className = `alert alert-${color} mt-3 mb-0`;
        paymentStatus.textContent = message;
        paymentStatus.hidden = false;
        paymentStatus.focus();

        retryPayment = retryAction ?? null;
        paymentRetryButton.textContent = retryLabel ?? "";
        paymentRetryButton.hidden = !retryPayment;
        updatePaymentButtons();
    }

    function updatePaymentButtons() {
        purchaseButton.disabled = paymentBusy || preparedPayment !== null;
        paymentRetryButton.disabled = paymentBusy;
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

function formatDateTime(value) {
    return DATE_TIME_FORMATTER.format(new Date(value));
}
