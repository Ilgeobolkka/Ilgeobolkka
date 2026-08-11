import http from "k6/http";
import {check, fail} from "k6";
import {csrfHeaders, login} from "./auth.js";

const BASE_URL = __ENV.BASE_URL || "http://127.0.0.1:8080";
const NEW_READER_COUNT = 334;
const ACTIVE_READER_COUNT = 333;
const OWNED_READER_COUNT = 333;
const BOOK_COUNT = 100;
const PAGES_PER_BOOK = 4;

const authenticatedReaders = new Map();

export function publicExplore() {
    const choice = __ITER % 3;
    if (choice === 0) {
        const response = http.get(`${BASE_URL}/api/books?page=1`, {
            tags: {flow: "public", name: "GET /api/books"}
        });
        check(response, {"도서 목록 성공": (result) => result.status === 200});
        return;
    }
    if (choice === 1) {
        const keyword = encodeURIComponent("성능");
        const response = http.get(`${BASE_URL}/api/books?page=1&keyword=${keyword}`, {
            tags: {flow: "public", name: "GET /api/books?keyword"}
        });
        check(response, {"도서 검색 성공": (result) => result.status === 200});
        return;
    }

    const bookId = (__VU + __ITER) % BOOK_COUNT + 1;
    const response = http.get(`${BASE_URL}/api/books/${bookId}`, {
        tags: {flow: "public", name: "GET /api/books/{bookId}"}
    });
    check(response, {"도서 상세 성공": (result) => result.status === 200});
}

export function authenticatedRead() {
    const readerNumber = activeReaderNumber(__VU);
    ensureLogin(readerNumber, "authenticated-read");

    const choice = __ITER % 3;
    const paths = ["/api/library", "/api/ink/balance", "/api/ink/ledger?page=1"];
    const names = ["GET /api/library", "GET /api/ink/balance", "GET /api/ink/ledger"];
    const response = http.get(`${BASE_URL}${paths[choice]}`, {
        tags: {flow: "authenticated-read", name: names[choice]}
    });
    check(response, {"인증 조회 성공": (result) => result.status === 200});
}

export function ownedRead() {
    const readerNumber = ownedReaderNumber(__VU);
    const bookId = seededBookId(readerNumber);
    ensureLogin(readerNumber, "owned-read");
    openAndRead(readerNumber, bookId, 1, true, 0, "owned-read");
}

export function activeRentalRead() {
    const readerNumber = activeReaderNumber(__VU);
    const bookId = seededBookId(readerNumber);
    ensureLogin(readerNumber, "active-rental");
    openAndRead(readerNumber, bookId, 1, false, 0, "active-rental");
}

export function newRental() {
    const accountCycle = Math.floor(__ITER / 80);
    const readerNumber = newReaderNumber(__VU + accountCycle * 37);
    const slot = __ITER % 80;
    const bookId = Math.floor(slot / PAGES_PER_BOOK) + 1;
    const pageNumber = slot % PAGES_PER_BOOK + 1;
    ensureLogin(readerNumber, "new-rental");
    openAndRead(readerNumber, bookId, pageNumber, false, 1, "new-rental");
}

export function loginOnly() {
    login(newReaderNumber(__VU + __ITER), {flow: "login"}, false);
}

export function openPageOnly(readerNumber, bookId, pageNumber, flow) {
    const auth = ensureLogin(readerNumber, flow);
    const response = http.post(
        `${BASE_URL}/api/books/${bookId}/reading-sessions`,
        JSON.stringify({pageNumber}),
        {
            headers: csrfHeaders(auth.csrf),
            tags: {flow, name: "POST /api/books/{bookId}/reading-sessions"}
        }
    );
    check(response, {"동시 페이지 열기 성공": (result) => result.status === 201});
    return response;
}

export function logout(readerNumber, flow) {
    const auth = ensureLogin(readerNumber, flow);
    const response = http.post(`${BASE_URL}/api/auth/logout`, null, {
        headers: csrfHeaders(auth.csrf),
        tags: {flow, name: "POST /api/auth/logout"}
    });
    check(response, {"로그아웃 성공": (result) => result.status === 200});
    authenticatedReaders.delete(__VU);
}

export function newReaderNumber(seed) {
    return 1 + 3 * ((seed - 1) % NEW_READER_COUNT);
}

export function activeReaderNumber(seed) {
    return 2 + 3 * ((seed - 1) % ACTIVE_READER_COUNT);
}

export function ownedReaderNumber(seed) {
    return 3 + 3 * ((seed - 1) % OWNED_READER_COUNT);
}

function ensureLogin(readerNumber, flow) {
    const current = authenticatedReaders.get(__VU);
    if (current?.readerNumber === readerNumber) {
        return current;
    }
    const auth = {
        readerNumber,
        csrf: login(readerNumber, {flow, setup: "true"})
    };
    authenticatedReaders.set(__VU, auth);
    return auth;
}

function openAndRead(readerNumber, bookId, pageNumber, owned, deductedInk, flow) {
    const auth = ensureLogin(readerNumber, flow);
    const openResponse = http.post(
        `${BASE_URL}/api/books/${bookId}/reading-sessions`,
        JSON.stringify({pageNumber}),
        {
            headers: csrfHeaders(auth.csrf),
            tags: {flow, name: "POST /api/books/{bookId}/reading-sessions"}
        }
    );
    if (!check(openResponse, {
        "페이지 열기 성공": (result) => result.status === 201,
        "소장 상태 일치": (result) => jsonField(result, "owned") === owned,
        "잉크 차감 일치": (result) => jsonField(result, "deductedInk") === deductedInk
    })) {
        return;
    }

    const viewerSessionId = jsonField(openResponse, "viewerSessionId");
    if (!viewerSessionId) {
        fail(`viewerSessionId가 없습니다: reader=${readerNumber}`);
    }
    const contentResponse = http.get(
        `${BASE_URL}/api/reading-sessions/current/pages/${pageNumber}/content`,
        {
            headers: {"X-Viewer-Session-Id": viewerSessionId},
            tags: {flow, name: "GET /api/reading-sessions/current/pages/{pageNumber}/content"}
        }
    );
    check(contentResponse, {
        "페이지 콘텐츠 성공": (result) => result.status === 200,
        "페이지 콘텐츠 no-store": (result) =>
            (result.headers["Cache-Control"] || "").includes("private, no-store")
    });
}

function seededBookId(readerNumber) {
    return (readerNumber - 1) % BOOK_COUNT + 1;
}

function jsonField(response, field) {
    try {
        return response.json(field);
    } catch (_) {
        return null;
    }
}
