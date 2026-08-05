import {initializeInkPage} from "./ink-page.js";

const inkPage = document.querySelector("[data-ink-page]");

if (inkPage) {
    initializeInkPage(inkPage);
}
