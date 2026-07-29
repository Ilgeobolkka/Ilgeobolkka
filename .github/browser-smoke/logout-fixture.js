const mode = new URLSearchParams(window.location.search).get("mode");

if (mode === "missing-csrf") {
    document.querySelector("meta[name='_csrf']").removeAttribute("content");
}

window.fetch = async () => {
    if (mode === "network-error") {
        throw new TypeError("강제 네트워크 오류");
    }
    if (mode === "server-error") {
        return new Response(JSON.stringify({
            code: "INTERNAL_SERVER_ERROR",
            message: "강제 서버 오류"
        }), {
            status: 500,
            headers: {"Content-Type": "application/json"}
        });
    }
    return new Response(JSON.stringify({readerId: 1}), {
        status: 200,
        headers: {"Content-Type": "application/json"}
    });
};

await import("/js/common/shell.js");
document.body.dataset.logoutFixtureReady = "true";
