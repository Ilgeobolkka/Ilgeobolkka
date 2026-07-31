import {createViewer} from "./viewer-page.js";

const root = document.querySelector("[data-viewer-root]");
if (root) {
    createViewer(root).start();
}
