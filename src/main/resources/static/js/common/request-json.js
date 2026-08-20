const SAFE_METHODS = new Set(["GET", "HEAD", "OPTIONS", "TRACE"]);
const DEFAULT_ERROR_MESSAGE = "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.";

export class ApiRequestError extends Error {

    constructor(code, message, status, requestId) {
        super(message);
        this.name = "ApiRequestError";
        this.code = code;
        this.status = status;
        this.requestId = requestId;
    }
}

export async function requestJson(url, options = {}) {
    const requestUrl = resolveSameOriginUrl(url);
    const method = (options.method || "GET").toUpperCase();
    const headers = new Headers(options.headers);

    addCsrfHeader(headers, method);
    if (options.body !== undefined && !headers.has("Content-Type")) {
        headers.set("Content-Type", "application/json");
    }

    let response;
    try {
        response = await fetch(requestUrl, {
            ...options,
            method,
            headers,
            credentials: "same-origin"
        });
    } catch {
        throw new ApiRequestError("NETWORK_ERROR", DEFAULT_ERROR_MESSAGE, 0, null);
    }

    const requestId = response.headers.get("X-Request-Id");
    if (response.ok) {
        if (response.status === 204) {
            return null;
        }
        try {
            return await response.json();
        } catch {
            throw new ApiRequestError(
                "INVALID_RESPONSE",
                DEFAULT_ERROR_MESSAGE,
                response.status,
                requestId
            );
        }
    }

    let errorBody;
    try {
        errorBody = await response.json();
    } catch {
        errorBody = null;
    }

    const code = typeof errorBody?.code === "string" ? errorBody.code : "INVALID_RESPONSE";
    const message = typeof errorBody?.message === "string"
        ? errorBody.message
        : DEFAULT_ERROR_MESSAGE;
    throw new ApiRequestError(code, message, response.status, requestId);
}

export function resolveSameOriginUrl(url) {
    const requestUrl = new URL(url, window.location.href);
    if (requestUrl.origin !== window.location.origin) {
        throw new ApiRequestError(
            "CROSS_ORIGIN_REQUEST",
            "같은 출처의 API만 호출할 수 있습니다.",
            0,
            null
        );
    }
    return requestUrl;
}

export function addCsrfHeader(headers, method) {
    if (SAFE_METHODS.has(method.toUpperCase())) {
        return;
    }
    const token = document.querySelector("meta[name='_csrf']")?.content;
    const headerName = document.querySelector("meta[name='_csrf_header']")?.content;
    if (!token || !headerName) {
        throw new ApiRequestError(
            "MISSING_CSRF_TOKEN",
            "보안 토큰을 찾을 수 없습니다. 페이지를 새로고침해 주세요.",
            0,
            null
        );
    }
    headers.set(headerName, token);
}
