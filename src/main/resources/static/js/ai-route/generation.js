import {createAiRouteGenerationPage} from "./generation-page.js";

const root = document.querySelector("[data-ai-route-generation-root]");
if (root) {
    createAiRouteGenerationPage(root).start();
}
