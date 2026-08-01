import {clearCommonError, showCommonError} from "../common/error-display.js";
import {requestJson} from "../common/request-json.js";

const page = document.querySelector("[data-auth-page]");
const form = page?.querySelector("[data-auth-form]");

form?.addEventListener("submit", async (event) => {
    event.preventDefault();
    clearCommonError();

    const submitButton = form.querySelector("button[type='submit']");
    submitButton.disabled = true;

    try {
        await requestJson(form.action, {
            method: "POST",
            body: JSON.stringify({
                email: form.elements.namedItem("email").value,
                password: form.elements.namedItem("password").value
            })
        });
        window.location.assign(page.dataset.successPath);
    } catch (error) {
        showCommonError(error);
        submitButton.disabled = false;
    }
});
