import {
    addCsrfHeader,
    ApiRequestError,
    resolveSameOriginUrl
} from "./request-json.js";

const DEFAULT_ERROR_MESSAGE = "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.";

export async function requestPageContent(
        url,
        viewerSessionId,
        expectedContentType,
        options = {}) {
    const requestUrl = resolveSameOriginUrl(url);
    const method = (options.method || "GET").toUpperCase();
    const headers = new Headers(options.headers);

    addCsrfHeader(headers, method);
    headers.set("X-Viewer-Session-Id", viewerSessionId);

    let response;
    try {
        response = await fetch(requestUrl, {
            ...options,
            method,
            headers,
            credentials: "same-origin"
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
        return {contentType: "TEXT", body: await response.text()};
    }
    if (expectedContentType === "IMAGE"
            && (mediaType === "image/jpeg" || mediaType === "image/png")) {
        return {contentType: "IMAGE", body: await response.blob()};
    }

    throw new ApiRequestError(
        "INVALID_RESPONSE",
        DEFAULT_ERROR_MESSAGE,
        response.status,
        requestId
    );
}

async function createApiError(response, requestId) {
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
    return new ApiRequestError(code, message, response.status, requestId);
}
