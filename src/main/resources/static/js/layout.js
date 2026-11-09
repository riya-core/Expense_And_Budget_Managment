/*
 * /js/layout.js - behaviour shared by every logged-in page.
 * Needs api.js loaded first. Does:
 *   1. Sends you to /login if there is no saved token.
 *   2. Shows the user's name and initial.
 *   3. Log out button.
 *   4. Mobile sidebar open/close.
 *   5. Alerts bell (contract 6.6): unread badge, dropdown list, mark all read.
 *   6. Layout.toast(message) for short messages from any page.
 */
(function () {
  "use strict";

  if (!Api.getToken()) {
    window.location.assign("/login");
    return;
  }

  function $(id) { return document.getElementById(id); }

  // ---- 1. User name + avatar ----
  const user = Api.getUser();
  if (user && user.name) {
    $("uname").textContent = user.name;
    $("avatar").textContent = user.name.trim().charAt(0).toUpperCase();
  }

  // ---- 2. Log out ----
  $("logout-btn").addEventListener("click", function () {
    Api.clearSession();
    window.location.assign("/login");
  });

  // ---- 3. Mobile sidebar ----
  const side = $("sidebar");
  const menuBtn = $("menu-btn");
  const scrim = document.createElement("div");
  scrim.className = "scrim";
  scrim.hidden = true;
  document.body.appendChild(scrim);

  function setMenu(open) {
    side.classList.toggle("open", open);
    scrim.hidden = !open;
    menuBtn.setAttribute("aria-expanded", String(open));
  }
  menuBtn.addEventListener("click", function () { setMenu(!side.classList.contains("open")); });
  scrim.addEventListener("click", function () { setMenu(false); });

  // ---- 4. Toast ----
  let toastTimer = null;
  function toast(message) {
    let el = document.querySelector(".toast");
    if (!el) {
      el = document.createElement("div");
      el.className = "toast";
      el.setAttribute("role", "status");
      document.body.appendChild(el);
    }
    el.textContent = message;
    el.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { el.hidden = true; }, 4000);
  }

  // ---- 5. Alerts bell ----
  const bell = $("bell");
  const badge = $("bell-badge");
  const panel = $("bell-panel");
  const list = $("bell-list");

  async function loadCount() {
    try {
      const data = await Api.get("/api/alerts/unread-count");
      const count = (data && data.count) || 0;
      badge.textContent = count > 99 ? "99+" : String(count);
      badge.hidden = count === 0;
    } catch (e) { /* the bell is optional; stay quiet */ }
  }

  async function loadList() {
    list.innerHTML = "";
    try {
      const alerts = await Api.get("/api/alerts");
      if (!alerts || alerts.length === 0) {
        const empty = document.createElement("li");
        empty.className = "bell-empty";
        empty.textContent = "No alerts yet.";
        list.appendChild(empty);
        return;
      }
      alerts.slice(0, 10).forEach(function (a) {
        const li = document.createElement("li");
        li.className = "bell-item" + (a.read ? "" : " unread") + " t-" + a.type;
        li.textContent = a.message;          // textContent: never inject server text as HTML
        list.appendChild(li);
      });
    } catch (e) {
      const err = document.createElement("li");
      err.className = "bell-empty";
      err.textContent = e.message;
      list.appendChild(err);
    }
  }

  bell.addEventListener("click", function () {
    const open = panel.hidden;
    panel.hidden = !open;
    bell.setAttribute("aria-expanded", String(open));
    if (open) loadList();
  });

  $("bell-readall").addEventListener("click", async function () {
    try {
      await Api.put("/api/alerts/read-all");
      await loadList();
      await loadCount();
    } catch (e) { toast(e.message); }
  });

  document.addEventListener("click", function (event) {
    if (!panel.hidden && !event.target.closest(".bell-wrap")) {
      panel.hidden = true;
      bell.setAttribute("aria-expanded", "false");
    }
  });

  loadCount();

  window.Layout = { toast: toast, refreshAlerts: loadCount };
})();