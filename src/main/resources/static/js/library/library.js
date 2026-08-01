import {createLibraryPage} from "./library-page.js";

const root = document.querySelector("[data-library-root]");
if (root) {
    createLibraryPage(root).start();
}
