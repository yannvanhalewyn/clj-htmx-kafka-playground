// Small, framework-free helpers that make HTMX's behaviour visible:
// - a "wire log" of every request and SSE message
// - a short flash on whatever element just got swapped
// - syntax highlighting for the "Show code" foldouts

(function () {
  const MAX_ENTRIES = 30;

  function describe(elt) {
    if (!elt || !elt.tagName) return "?";
    if (elt.id) return "#" + elt.id;
    const sseSwap = elt.getAttribute("sse-swap");
    if (sseSwap) return `[sse-swap="${sseSwap}"]`;
    return elt.tagName.toLowerCase();
  }

  function span(className, text) {
    const el = document.createElement("span");
    el.className = className;
    el.textContent = text;
    return el;
  }

  function logEntry({ method, path, details }) {
    const log = document.getElementById("wire-log");
    if (!log) return;
    document.getElementById("wire-log-empty")?.classList.add("hidden");

    const li = document.createElement("li");
    li.className = "wire-log__entry";

    const top = document.createElement("div");
    top.className = "flex items-center gap-2";
    top.append(
      span("method method--" + method.toLowerCase(), method),
      span("truncate text-white", path),
    );

    li.append(top, span("mt-1 block text-lavender/60", details));
    log.prepend(li);
    while (log.children.length > MAX_ENTRIES) log.lastChild.remove();
  }

  function swapStyle(elt) {
    return elt.closest("[hx-swap]")?.getAttribute("hx-swap") || "innerHTML";
  }

  // Requests

  document.addEventListener("htmx:beforeRequest", (e) => {
    e.detail.xhr.__startedAt = performance.now();
  });

  document.addEventListener("htmx:afterRequest", (e) => {
    const { xhr, requestConfig, target, elt } = e.detail;
    const ms = Math.round(performance.now() - (xhr.__startedAt ?? performance.now()));
    const path = new URL(requestConfig.path, location.href).pathname;
    logEntry({
      method: requestConfig.verb.toUpperCase(),
      path,
      details: `${xhr.status} · ${ms}ms → ${describe(target)} (${swapStyle(elt)})`,
    });
  });

  // Server-sent events

  document.addEventListener("htmx:sseOpen", (e) => {
    const url = new URL(e.detail.source.url);
    logEntry({ method: "SSE", path: url.pathname + url.search, details: "connection opened" });
  });

  document.addEventListener("htmx:sseMessage", (e) => {
    const bytes = new Blob([e.detail.data ?? ""]).size;
    logEntry({
      method: "SSE",
      path: `event: ${e.detail.type}`,
      details: `${bytes} bytes → ${describe(e.target)} (${swapStyle(e.target)})`,
    });
  });

  document.addEventListener("htmx:sseClose", () => {
    logEntry({ method: "SSE", path: "/sse", details: "connection closed" });
  });

  // Flash swapped content, skipping full page navigations

  document.addEventListener("htmx:afterSettle", (e) => {
    const target = e.detail.target;
    if (!target || target.id === "app-root") return;
    target.classList.remove("hx-flash");
    void target.offsetWidth; // restart the animation
    target.classList.add("hx-flash");
  });

  document.addEventListener("animationend", (e) => {
    if (e.animationName === "flash") e.target.classList.remove("hx-flash");
  });

  // Syntax highlighting

  function highlight(root) {
    if (!window.hljs) return;
    root.querySelectorAll("pre code:not([data-highlighted])").forEach((block) => {
      hljs.highlightElement(block);
      block.querySelectorAll("span").forEach((token) => {
        if (/^:(hx|sse)-/.test(token.textContent)) token.classList.add("hx-attr");
      });
    });
  }

  document.addEventListener("htmx:load", (e) => highlight(e.detail.elt));

  document.addEventListener("click", (e) => {
    if (e.target.id !== "wire-log-clear") return;
    document.getElementById("wire-log").replaceChildren();
    document.getElementById("wire-log-empty")?.classList.remove("hidden");
  });
})();
