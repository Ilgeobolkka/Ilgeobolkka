import {createOwnershipHistoryPage} from "./ownership-history-page.js";

const root = document.querySelector("[data-ownership-history-root]");
if (root) {
    createOwnershipHistoryPage(root).start();
}
