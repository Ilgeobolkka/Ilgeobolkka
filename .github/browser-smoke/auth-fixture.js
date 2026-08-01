const AUTH_REQUEST_STORAGE_KEY = "browser-smoke-auth-request";
const query = new URLSearchParams(window.location.search);
const flow = query.get("flow");
const mode = query.get("mode");
const page = document.querySelector("[data-auth-page]");
const form = document.querySelector("[data-auth-form]");

if (flow === "signup") {
    form.action = "/api/auth/signup";
    page.dataset.successPath = "/login";
}

window.sessionStorage.removeItem(AUTH_REQUEST_STORAGE_KEY);
window.fetch = async (url, options) => {
    const requestUrl = new URL(url, window.location.href);
    const headers = new Headers(options.headers);
    window.sessionStorage.setItem(AUTH_REQUEST_STORAGE_KEY, JSON.stringify({
        path: requestUrl.pathname,
        method: options.method,
        credentials: options.credentials,
        csrfToken: headers.get("X-CSRF-TOKEN"),
        contentType: headers.get("Content-Type"),
        body: JSON.parse(options.body)
    }));

    if (mode === "server-error") {
        return new Response(JSON.stringify({
            code: "INVALID_CREDENTIALS",
            message: "강제 인증 오류"
        }), {
            status: 401,
            headers: {"Content-Type": "application/json"}
        });
    }
    return new Response(JSON.stringify({
        readerId: 1,
        email: "reader@example.com"
    }), {
        status: flow === "signup" ? 201 : 200,
        headers: {"Content-Type": "application/json"}
    });
};

await import("/js/auth/auth.js");
document.body.dataset.authFixtureReady = "true";
