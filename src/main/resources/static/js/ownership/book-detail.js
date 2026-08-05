import {initializeBookDetailPage} from "./book-detail-page.js";

const root = document.querySelector("[data-book-detail-root]");
if (root) {
    initializeBookDetailPage(root);
}
