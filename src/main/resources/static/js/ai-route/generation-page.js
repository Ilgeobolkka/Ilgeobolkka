import {clearCommonError, showCommonError} from "../common/error-display.js";
import {requestJson} from "../common/request-json.js";

const MAX_PURPOSE_CODE_POINTS = 200;
const DEFAULT_POLL_DELAY_MS = 1000;
const DEPTHS = new Set(["QUICK", "BALANCED", "DEEP"]);
const GENERATION_STATUSES = new Set(["GENERATING", "ROUTE", "NO_ROUTE", "SAVED"]);
const NO_ROUTE_REASONS = new Set([
    "NO_RELEVANT_PAGES",
    "INSUFFICIENT_BUDGET",
    "INSUFFICIENT_DEPTH"
]);
const RELEVANCES = new Set(["HIGH", "MEDIUM"]);
const ROLES = new Set([
    "PREREQUISITE",
    "CORE",
    "EXAMPLE",
    "COUNTERPOINT",
    "CONCLUSION"
]);
const COST_STATUSES = new Set(["ONE_INK", "ACTIVE_RENTAL", "OWNED"]);

const RELEVANCE_LABELS = {
    HIGH: "관련도 높음",
    MEDIUM: "관련도 보통"
};
const ROLE_LABELS = {
    PREREQUISITE: "선수 개념",
    CORE: "핵심",
    EXAMPLE: "사례",
    COUNTERPOINT: "반론",
    CONCLUSION: "결론"
};
const COST_LABELS = {
    ONE_INK: "추가 1잉크",
    ACTIVE_RENTAL: "대여 중 · 추가 잉크 없음",
    OWNED: "소장 도서 · 추가 잉크 없음"
};

export function createAiRouteGenerationPage(root, dependencies = {}) {
    const request = dependencies.request || requestJson;
    const randomUUID = dependencies.randomUUID || (() => crypto.randomUUID());
    const schedule = dependencies.schedule || ((callback, delay) => window.setTimeout(callback, delay));
    const pollDelay = dependencies.pollDelay ?? DEFAULT_POLL_DELAY_MS;
    const bookId = Number(root.dataset.bookId);
    const elements = findElements(root);

    let book = null;
    let inkBalance = null;
    let activeRun = null;
    let retryAction = null;
    let saving = false;

    if (!Number.isInteger(bookId) || bookId <= 0) {
        throw new Error("AI 경로 화면의 도서 식별자가 올바르지 않습니다.");
    }

    elements.form.addEventListener("submit", (event) => {
        event.preventDefault();
        startNewGeneration();
    });
    elements.purpose.addEventListener("input", updatePurposeCount);
    elements.retry.addEventListener("click", () => {
        if (retryAction) {
            retryAction();
        }
    });
    elements.save.addEventListener("click", saveRoute);
    elements.budgetChoices.forEach((button) => {
        button.addEventListener("click", () => {
            elements.budget.value = button.dataset.aiRouteBudgetChoice;
        });
    });
    elements.budgetAll.addEventListener("click", () => {
        if (inkBalance !== null) {
            elements.budget.value = String(inkBalance);
        }
    });

    async function start() {
        clearCommonError();
        setStatus("도서와 현재 읽기 권한을 확인하고 있습니다.", "info");
        elements.inputs.disabled = true;

        try {
            const response = await request(`/api/books/${encodeURIComponent(bookId)}`);
            validateBook(response, bookId);
            book = response;
            elements.bookTitle.textContent = `${book.title}의 AI 독서 경로를 만듭니다.`;

            if (book.owned) {
                configureOwnedInput();
            } else {
                const balanceResponse = await request("/api/ink/balance");
                validateBalance(balanceResponse);
                inkBalance = balanceResponse.balance;
                configureBudgetInput();
            }

            elements.inputs.disabled = false;
            setStatus("독서 목적과 경로 범위를 입력해 주세요.", "info");
            elements.purpose.focus();
            return {book, inkBalance};
        } catch (error) {
            setStatus("AI 경로 화면을 준비하지 못했습니다.", "danger");
            showCommonError(error);
            return null;
        }
    }

    async function startNewGeneration() {
        if (!book || saving) {
            return;
        }

        clearCommonError();
        clearResult();
        setRetry(null);

        let body;
        try {
            body = generationRequestOf({
                purpose: elements.purpose.value,
                owned: book.owned,
                inkBalance,
                budget: elements.budget.value,
                depth: selectedDepth(elements.depths)
            });
        } catch (error) {
            setStatus(error.message, "danger", true);
            return;
        }

        const run = {
            key: randomUUID(),
            body,
            generationId: null,
            result: null,
            entitlementInvalid: false
        };
        activeRun = run;
        await requestGeneration(run);
    }

    async function requestGeneration(run) {
        if (activeRun !== run) {
            return;
        }
        clearCommonError();
        setRetry(null);
        setBusy(true);
        setStatus("AI 경로 생성을 요청하고 있습니다.", "info");

        try {
            const response = await request(
                `/api/books/${encodeURIComponent(bookId)}/ai-route-generations`,
                {
                    method: "POST",
                    headers: {"Idempotency-Key": run.key},
                    body: JSON.stringify(run.body)
                });
            handleGenerationResponse(run, response);
        } catch (error) {
            handleGenerationError(run, error, () => requestGeneration(run));
        }
    }

    async function pollGeneration(run) {
        if (activeRun !== run || !run.generationId) {
            return;
        }
        clearCommonError();
        setRetry(null);
        setBusy(true);
        setStatus("경로를 생성하고 있습니다. 잠시만 기다려 주세요.", "info");

        try {
            const response = await request(
                `/api/ai-route-generations/${encodeURIComponent(run.generationId)}`);
            handleGenerationResponse(run, response);
        } catch (error) {
            handleGenerationError(run, error, () => pollGeneration(run));
        }
    }

    function handleGenerationResponse(run, response) {
        if (activeRun !== run) {
            return;
        }

        const display = generationDisplayOf(response, bookId);
        run.generationId = display.generationId;
        run.result = response;
        setRetry(null);

        if (display.kind === "GENERATING") {
            setStatus(display.message, "info");
            schedule(() => pollGeneration(run), pollDelay);
            return;
        }

        setBusy(false);
        if (display.kind === "ROUTE") {
            renderRoute(display);
            setStatus(display.message, "success", true);
            return;
        }
        if (display.kind === "NO_ROUTE") {
            renderNoRoute(display);
            setStatus(display.message, "secondary", true);
            return;
        }

        setStatus(display.message, "success", true);
    }

    function handleGenerationError(run, error, retry) {
        if (activeRun !== run) {
            return;
        }
        setBusy(false);
        setStatus("AI 경로 생성 요청을 완료하지 못했습니다.", "danger");
        showCommonError(error);
        if (isRetryableTransportError(error)) {
            setRetry(retry);
        }
    }

    async function saveRoute() {
        if (saving
                || !activeRun?.generationId
                || activeRun?.result?.status !== "ROUTE"
                || activeRun.entitlementInvalid) {
            return;
        }

        saving = true;
        elements.save.disabled = true;
        clearCommonError();
        setStatus("경로를 저장하고 있습니다.", "info");

        try {
            const response = await request(
                `/api/ai-route-generations/${encodeURIComponent(activeRun.generationId)}/routes`,
                {method: "POST"});
            validateSavedRoute(response, bookId);
            setStatus("경로를 저장했습니다. 이 도서의 현재 AI 독서 경로로 지정되었습니다.", "success", true);
        } catch (error) {
            if (error?.code === "AI_ROUTE_ENTITLEMENT_CHANGED") {
                invalidateEntitlementPreview();
                return;
            }
            elements.save.disabled = false;
            setStatus("경로를 저장하지 못했습니다.", "danger");
            showCommonError(error);
        } finally {
            saving = false;
        }
    }

    function invalidateEntitlementPreview() {
        activeRun.entitlementInvalid = true;
        elements.save.disabled = true;
        elements.costs().forEach((cost) => {
            cost.textContent = "비용 상태 무효";
            cost.className = "badge text-bg-secondary align-self-start";
            cost.setAttribute(
                "aria-label",
                "생성 뒤 권한이 변경되어 이 항목의 비용 상태가 무효입니다.");
        });
        setStatus(
            "대여·소장 상태가 바뀌어 필요한 잉크가 생성 시점보다 늘었습니다. "
                + "이 미리보기는 저장할 수 없으니 새 경로를 생성해 주세요.",
            "warning",
            true);
    }

    function configureOwnedInput() {
        elements.budgetFields.hidden = true;
        elements.depthFields.hidden = false;
    }

    function configureBudgetInput() {
        elements.depthFields.hidden = true;
        elements.budgetFields.hidden = false;
        elements.budget.max = String(inkBalance);
        elements.budget.value = String(Math.min(10, inkBalance));
        elements.balance.textContent = `현재 보유 잉크는 ${inkBalance}개입니다.`;
        elements.budgetChoices.forEach((button) => {
            button.hidden = Number(button.dataset.aiRouteBudgetChoice) > inkBalance;
        });
    }

    function renderRoute(display) {
        elements.noRoute.hidden = true;
        elements.items.replaceChildren();
        elements.previewPurpose.textContent = display.purpose;
        elements.remaining.textContent = `오늘 새로 생성할 수 있는 경로가 ${display.remaining}회 남았습니다.`;

        display.items.forEach((item) => {
            const fragment = elements.itemTemplate.content.cloneNode(true);
            fragment.querySelector("[data-ai-route-position]").textContent = String(item.position);
            fragment.querySelector("[data-ai-route-page]").textContent = String(item.pageNumber);
            fragment.querySelector("[data-ai-route-metadata]").textContent = item.metadata;
            fragment.querySelector("[data-ai-route-guide]").textContent = item.guide;
            const cost = fragment.querySelector("[data-ai-route-cost]");
            cost.textContent = item.cost;
            cost.className = `badge ${item.costClass} align-self-start`;
            elements.items.append(fragment);
        });

        elements.save.disabled = false;
        elements.preview.hidden = false;
    }

    function renderNoRoute(display) {
        elements.preview.hidden = true;
        elements.noRouteMessage.textContent = display.detail;
        elements.noRoute.hidden = false;
    }

    function clearResult() {
        elements.preview.hidden = true;
        elements.noRoute.hidden = true;
        elements.noRouteMessage.textContent = "";
        elements.previewPurpose.textContent = "";
        elements.remaining.textContent = "";
        elements.items.replaceChildren();
        elements.save.disabled = true;
    }

    function setBusy(busy) {
        elements.inputs.disabled = busy;
        elements.generate.textContent = busy ? "경로 생성 중" : "새 경로 생성";
    }

    function setRetry(action) {
        retryAction = action;
        elements.retry.hidden = !retryAction;
    }

    function setStatus(message, kind, focus = false) {
        elements.status.className = `alert alert-${kind}`;
        elements.status.textContent = message;
        if (focus) {
            elements.status.focus();
        }
    }

    function updatePurposeCount() {
        elements.purposeCount.textContent = String(codePointCount(elements.purpose.value));
    }

    return {start, startNewGeneration};
}

export function generationRequestOf({purpose, owned, inkBalance, budget, depth}) {
    const purposeCount = codePointCount(purpose);
    if (purpose.trim().length === 0) {
        throw new Error("독서 목적을 입력해 주세요.");
    }
    if (purposeCount > MAX_PURPOSE_CODE_POINTS) {
        throw new Error("독서 목적은 Unicode 문자 기준 200자 이하여야 합니다.");
    }

    if (owned) {
        if (!DEPTHS.has(depth)) {
            throw new Error("경로 깊이를 선택해 주세요.");
        }
        return {purpose, maxAdditionalInk: null, depth};
    }

    const parsedBudget = Number(budget);
    if (!Number.isInteger(parsedBudget)
            || parsedBudget < 0
            || !Number.isInteger(inkBalance)
            || parsedBudget > inkBalance) {
        throw new Error(`추가 잉크 예산은 0부터 현재 잔액 ${inkBalance} 사이의 정수여야 합니다.`);
    }
    return {purpose, maxAdditionalInk: parsedBudget, depth: null};
}

export function generationDisplayOf(response, expectedBookId) {
    validateGenerationResponse(response, expectedBookId);

    if (response.status === "GENERATING") {
        return {
            kind: "GENERATING",
            generationId: response.generationId,
            message: "경로를 생성하고 있습니다. 잠시만 기다려 주세요."
        };
    }
    if (response.status === "NO_ROUTE") {
        return {
            kind: "NO_ROUTE",
            generationId: response.generationId,
            message: "요청한 조건으로 완성된 경로가 없습니다.",
            detail: noRouteMessage(response.noRouteReason, response.minimumRequiredInk)
        };
    }
    if (response.status === "SAVED") {
        return {
            kind: "SAVED",
            generationId: response.generationId,
            message: "이미 저장된 AI 독서 경로입니다."
        };
    }

    return {
        kind: "ROUTE",
        generationId: response.generationId,
        purpose: response.purpose,
        remaining: response.remainingDailyGenerations,
        message: `${response.items.length}개 페이지로 경로를 만들었습니다.`,
        items: response.items.map(itemDisplayOf)
    };
}

export function codePointCount(value) {
    return Array.from(value).length;
}

function itemDisplayOf(item) {
    return {
        position: item.position,
        pageNumber: item.pageNumber,
        metadata: [
            RELEVANCE_LABELS[item.relevance],
            item.prerequisite ? "선수 페이지" : "추천 페이지",
            ROLE_LABELS[item.role],
            `예상 ${item.estimatedMinutes}분`
        ].join(" · "),
        guide: item.guide,
        cost: COST_LABELS[item.additionalCostStatus],
        costClass: item.additionalCostStatus === "ONE_INK"
            ? "text-bg-warning"
            : "text-bg-success"
    };
}

function noRouteMessage(reason, minimumRequiredInk) {
    if (reason === "NO_RELEVANT_PAGES") {
        return "독서 목적과 충분히 관련된 페이지를 찾지 못했습니다.";
    }
    if (reason === "INSUFFICIENT_DEPTH") {
        return "선택한 깊이 안에서 선수 페이지를 포함한 유효한 경로를 만들 수 없습니다.";
    }
    return `선택한 예산으로는 유효한 경로를 만들 수 없습니다. 최소 ${minimumRequiredInk}잉크가 필요합니다.`;
}

function validateBook(book, expectedBookId) {
    if (!book
            || !Number.isInteger(book.bookId)
            || book.bookId !== expectedBookId
            || typeof book.title !== "string"
            || typeof book.owned !== "boolean") {
        throw new Error("도서 상세 API 응답 형식이 올바르지 않습니다.");
    }
}

function validateBalance(response) {
    if (!response || !Number.isInteger(response.balance) || response.balance < 0) {
        throw new Error("잉크 잔액 API 응답 형식이 올바르지 않습니다.");
    }
}

function validateGenerationResponse(response, expectedBookId) {
    const validBase = response
        && isUuid(response.generationId)
        && GENERATION_STATUSES.has(response.status)
        && response.bookId === expectedBookId
        && typeof response.contentVersion === "string"
        && typeof response.purpose === "string"
        && Number.isInteger(response.remainingDailyGenerations)
        && response.remainingDailyGenerations >= 0
        && Array.isArray(response.items);
    if (!validBase) {
        throw new Error("AI 경로 생성 API 응답 형식이 올바르지 않습니다.");
    }

    const routeValid = response.status !== "ROUTE"
        || (response.items.length > 0
            && response.items.every(validateGenerationItem)
            && response.noRouteReason === null
            && response.minimumRequiredInk === null);
    const noRouteValid = response.status !== "NO_ROUTE"
        || (response.items.length === 0
            && NO_ROUTE_REASONS.has(response.noRouteReason)
            && (response.noRouteReason === "INSUFFICIENT_BUDGET"
                ? Number.isInteger(response.minimumRequiredInk)
                    && response.minimumRequiredInk >= 0
                : response.minimumRequiredInk === null));
    const emptyResultValid = !["GENERATING", "SAVED"].includes(response.status)
        || response.items.length === 0;
    if (!routeValid || !noRouteValid || !emptyResultValid) {
        throw new Error("AI 경로 생성 API 응답 형식이 올바르지 않습니다.");
    }
}

function validateGenerationItem(item) {
    return Number.isInteger(item?.position)
        && item.position > 0
        && Number.isInteger(item.pageNumber)
        && item.pageNumber > 0
        && RELEVANCES.has(item.relevance)
        && typeof item.prerequisite === "boolean"
        && ROLES.has(item.role)
        && Number.isInteger(item.estimatedMinutes)
        && item.estimatedMinutes > 0
        && typeof item.guide === "string"
        && COST_STATUSES.has(item.additionalCostStatus);
}

function validateSavedRoute(response, expectedBookId) {
    if (!response
            || !Number.isInteger(response.routeId)
            || response.routeId <= 0
            || response.bookId !== expectedBookId
            || typeof response.purpose !== "string"
            || !Array.isArray(response.items)) {
        throw new Error("AI 경로 저장 API 응답 형식이 올바르지 않습니다.");
    }
}

function isUuid(value) {
    return typeof value === "string"
        && /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/iu.test(value);
}

function isRetryableTransportError(error) {
    return error?.status === 0 || error?.code === "INVALID_RESPONSE";
}

function selectedDepth(depths) {
    return depths.find((depth) => depth.checked)?.value ?? null;
}

function findElements(root) {
    return {
        bookTitle: requiredElement(root, "[data-ai-route-book-title]"),
        form: requiredElement(root, "[data-ai-route-form]"),
        inputs: requiredElement(root, "[data-ai-route-inputs]"),
        purpose: requiredElement(root, "[data-ai-route-purpose]"),
        purposeCount: requiredElement(root, "[data-ai-route-purpose-count]"),
        budgetFields: requiredElement(root, "[data-ai-route-budget-fields]"),
        budget: requiredElement(root, "[data-ai-route-budget]"),
        balance: requiredElement(root, "[data-ai-route-balance]"),
        budgetChoices: Array.from(root.querySelectorAll("[data-ai-route-budget-choice]")),
        budgetAll: requiredElement(root, "[data-ai-route-budget-all]"),
        depthFields: requiredElement(root, "[data-ai-route-depth-fields]"),
        depths: Array.from(root.querySelectorAll("[data-ai-route-depth]")),
        generate: requiredElement(root, "[data-ai-route-generate]"),
        retry: requiredElement(root, "[data-ai-route-retry]"),
        status: requiredElement(root, "[data-ai-route-status]"),
        noRoute: requiredElement(root, "[data-ai-route-no-route]"),
        noRouteMessage: requiredElement(root, "[data-ai-route-no-route-message]"),
        preview: requiredElement(root, "[data-ai-route-preview]"),
        previewPurpose: requiredElement(root, "[data-ai-route-preview-purpose]"),
        remaining: requiredElement(root, "[data-ai-route-remaining]"),
        save: requiredElement(root, "[data-ai-route-save]"),
        items: requiredElement(root, "[data-ai-route-items]"),
        itemTemplate: requiredElement(root, "[data-ai-route-item-template]"),
        costs: () => Array.from(root.querySelectorAll("[data-ai-route-cost]"))
    };
}

function requiredElement(root, selector) {
    const element = root.querySelector(selector);
    if (!element) {
        throw new Error(`필수 AI 경로 화면 요소가 없습니다: ${selector}`);
    }
    return element;
}
