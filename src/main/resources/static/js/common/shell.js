import {clearCommonError, showCommonError} from "./error-display.js";
import {requestJson} from "./request-json.js";

const logoutForm = document.querySelector("[data-logout-form]");

logoutForm?.addEventListener("submit", async (event) => {
    event.preventDefault();
    clearCommonError();

    const submitButton = logoutForm.querySelector("button[type='submit']");
    submitButton.disabled = true;
    try {
        await requestJson(logoutForm.action, {
            method: "POST",
            body: null
        });
    } catch (error) {
        if (error?.status === 0) {
            showCommonError(error);
            submitButton.disabled = false;
            return;
        }
    }
    window.location.assign("/books");
});
