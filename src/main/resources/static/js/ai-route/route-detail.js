import {createRouteDetailPage} from "./route-detail-page.js";

const root = document.querySelector("[data-ai-route-detail-root]");
if (root) {
    createRouteDetailPage(root).start();
}
