/**
 * Relays page → extension zoom requests. chrome.tabs.setZoom updates Chrome ⋮ → Zoom.
 */
chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  if (!message || message.source !== "smartqa-zoom") {
    return;
  }
  const tabId = sender.tab && sender.tab.id;
  if (message.type === "SET_ZOOM") {
    const factor = Number(message.factor);
    if (!tabId || !(factor > 0)) {
      sendResponse({ ok: false, error: "missing-tab-or-factor" });
      return;
    }
    chrome.tabs.setZoom(tabId, factor)
      .then(() => chrome.tabs.getZoom(tabId))
      .then((zoom) => sendResponse({ ok: true, zoom: zoom }))
      .catch((err) => sendResponse({ ok: false, error: String(err) }));
    return true;
  }
  if (message.type === "GET_ZOOM") {
    if (!tabId) {
      sendResponse({ ok: false, error: "missing-tab" });
      return;
    }
    chrome.tabs.getZoom(tabId)
      .then((zoom) => sendResponse({ ok: true, zoom: zoom }))
      .catch((err) => sendResponse({ ok: false, error: String(err) }));
    return true;
  }
});
