const DEFAULT_ERROR_MESSAGE = "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.";

export function showCommonError(error) {
    const errorRegion = document.querySelector("[data-common-error]");
    if (!errorRegion) {
        return;
    }

    errorRegion.textContent = error?.message || DEFAULT_ERROR_MESSAGE;
    errorRegion.hidden = false;
    errorRegion.focus();
}

export function clearCommonError() {
    const errorRegion = document.querySelector("[data-common-error]");
    if (!errorRegion) {
        return;
    }

    errorRegion.textContent = "";
    errorRegion.hidden = true;
}
