(() => {
  window.addEventListener("message", (event) => {
    if (event.source !== window || !event.data || event.data.source !== "smartqa-zoom") {
      return;
    }
    chrome.runtime.sendMessage(event.data, (response) => {
      window.postMessage(
        Object.assign({ source: "smartqa-zoom-result", requestId: event.data.requestId }, response || { ok: false }),
        "*"
      );
    });
  });
})();
