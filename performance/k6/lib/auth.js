import http from "k6/http";
import {check, fail} from "k6";

const BASE_URL = __ENV.BASE_URL || "http://127.0.0.1:8080";
const PASSWORD = __ENV.PERF_USER_PASSWORD;

export function readerEmail(readerNumber) {
    return `perf-reader-${String(readerNumber).padStart(4, "0")}@perf.ilgeobolkka.test`;
}

export function login(readerNumber, tags = {}, refreshAfterLogin = true) {
    if (!PASSWORD) {
        fail("PERF_USER_PASSWORD가 필요합니다.");
    }

    const loginPage = http.get(`${BASE_URL}/login`, {
        tags: {...tags, name: "GET /login"}
    });
    const csrf = csrfFrom(loginPage);
    const response = http.post(
        `${BASE_URL}/api/auth/login`,
        JSON.stringify({
            email: readerEmail(readerNumber),
            password: PASSWORD
        }),
        {
            headers: {
                "Content-Type": "application/json",
                [csrf.headerName]: csrf.token
            },
            tags: {...tags, name: "POST /api/auth/login"}
        }
    );

    if (!check(response, {
        "로그인 성공": (result) => result.status === 200
    })) {
        fail(`로그인에 실패했습니다: status=${response.status}`);
    }
    return refreshAfterLogin ? refreshedCsrf(tags) : null;
}

export function refreshedCsrf(tags = {}) {
    const loginPage = http.get(`${BASE_URL}/login`, {
        tags: {...tags, name: "GET /login"}
    });
    return csrfFrom(loginPage);
}

export function csrfHeaders(csrf) {
    return {
        "Content-Type": "application/json",
        [csrf.headerName]: csrf.token
    };
}

function csrfFrom(response) {
    if (!check(response, {
        "CSRF 페이지 조회 성공": (result) => result.status === 200
    })) {
        fail(`CSRF 페이지 조회에 실패했습니다: status=${response.status}`);
    }

    const token = metaContent(response.body, "_csrf");
    const headerName = metaContent(response.body, "_csrf_header");
    if (!token || !headerName) {
        fail("로그인 페이지에서 CSRF token 또는 header 이름을 찾지 못했습니다.");
    }
    return {token, headerName};
}

function metaContent(html, name) {
    const nameFirst = new RegExp(
        `<meta[^>]*name=["']${name}["'][^>]*content=["']([^"']+)["'][^>]*>`,
        "i"
    ).exec(html);
    if (nameFirst) {
        return nameFirst[1];
    }
    const contentFirst = new RegExp(
        `<meta[^>]*content=["']([^"']+)["'][^>]*name=["']${name}["'][^>]*>`,
        "i"
    ).exec(html);
    return contentFirst ? contentFirst[1] : null;
}
