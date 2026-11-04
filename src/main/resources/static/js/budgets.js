/*
 * /js/budgets.js - the Budgets page.
 *
 * USE_MOCK = true  -> fake data kept in memory (reloading the page resets it).
 * USE_MOCK = false -> real APIs from the team contract (section 6.4):
 *   GET /api/budgets?month=&year=   POST /api/budgets   PUT|DELETE /api/budgets/{id}
 *   GET /api/categories
 *
 * The status (OK / WARNING / EXCEEDED) and percentUsed come from the backend.
 * This page only displays them; it never decides a status itself.
 * (The mock mimics the backend: WARNING from 80%, EXCEEDED from 100%.)
 */
(function () {
  "use strict";

  const USE_MOCK = true;

  const MONTHS = ["January", "February", "March", "April", "May", "June",
                  "July", "August", "September", "October", "November", "December"];
  const STATUS_LABEL = { OK: "OK", WARNING: "Warning", EXCEEDED: "Exceeded" };

  // ---------- helpers ----------
  const $ = (id) => document.getElementById(id);

  function money(n) {
    const v = Number(n || 0);
    const frac = Number.isInteger(v) ? 0 : 2;
    return (v < 0 ? "-" : "") + "₹" + Math.abs(v).toLocaleString("en-IN", { minimumFractionDigits: frac, maximumFractionDigits: frac });
  }

  function el(tag, cls, text) {
    const e = document.createElement(tag);
    if (cls) e.className = cls;
    if (text !== undefined) e.textContent = text;       // textContent: server text is never run as HTML
    return e;
  }

  function toast(message) { if (window.Layout) Layout.toast(message); }

  function safeStatus(s) { return STATUS_LABEL[s] ? s : "OK"; }

  const now = new Date();
  const NY = now.getFullYear();
  const NM = now.getMonth() + 1;

  // ---------- fake data (only used when USE_MOCK = true) ----------
  const MOCK_CATS = [
    { id: 1, name: "Salary", type: "INCOME" },
    { id: 2, name: "Freelance", type: "INCOME" },
    { id: 11, name: "Food & Dining", type: "EXPENSE" },
    { id: 12, name: "Transport", type: "EXPENSE" },
    { id: 13, name: "Shopping", type: "EXPENSE" },
    { id: 14, name: "Bills & Utilities", type: "EXPENSE" },
    { id: 15, name: "Groceries", type: "EXPENSE" },
    { id: 16, name: "Others", type: "EXPENSE" },
    { id: 17, name: "Entertainment", type: "EXPENSE" },
    { id: 18, name: "Health", type: "EXPENSE" }
  ];
  let mockBudgets = null;
  let mockNextId = 100;

  function mockCalc(b) {
    const pct = b.limitAmount > 0 ? (b.spent / b.limitAmount) * 100 : 0;
    b.percentUsed = Math.round(pct * 10) / 10;
    b.status = pct >= 100 ? "EXCEEDED" : pct >= 80 ? "WARNING" : "OK";
    return b;
  }

  function mockMake(id, catId, month, year, limit, spent) {
    const cat = MOCK_CATS.find(function (c) { return c.id === Number(catId); });
    return mockCalc({ id: id, categoryId: Number(catId), categoryName: cat ? cat.name : "?", month: Number(month),
                      year: Number(year), limitAmount: Number(limit), spent: spent });
  }

  function seedMock() {
    mockBudgets = [
      mockMake(1, 11, NM, NY, 5000, 4200),
      mockMake(2, 12, NM, NY, 3000, 1200),
      mockMake(3, 17, NM, NY, 2000, 2150),
      mockMake(4, 14, NM, NY, 3000, 1500),
      mockMake(5, 13, NM, NY, 4000, 2000),
      mockMake(6, 18, NM, NY, 3000, 1200)
    ];
  }

  function mock(method, path, body) {
    if (!mockBudgets) seedMock();
    const parts = path.split("?");
    const route = parts[0];
    const q = new URLSearchParams(parts[1] || "");

    if (route === "/api/categories") return MOCK_CATS;
    if (route === "/api/budgets" && method === "GET") {
      return mockBudgets.filter(function (b) {
        return b.month === Number(q.get("month")) && b.year === Number(q.get("year"));
      });
    }
    if (route === "/api/budgets" && method === "POST") {
      const b = mockMake(mockNextId++, body.categoryId, body.month, body.year, body.limitAmount, 0);
      mockBudgets.push(b);
      return b;
    }
    const m = route.match(/^\/api\/budgets\/(\d+)$/);
    if (m && method === "PUT") {
      const b = mockBudgets.find(function (x) { return x.id === Number(m[1]); });
      if (b) { b.limitAmount = Number(body.limitAmount); mockCalc(b); }
      return b;
    }
    if (m && method === "DELETE") {
      mockBudgets = mockBudgets.filter(function (x) { return x.id !== Number(m[1]); });
      return null;
    }
    return null;
  }

  function request(method, path, body) {
    if (USE_MOCK) return Promise.resolve(mock(method, path, body));
    if (method === "GET") return Api.get(path);
    if (method === "POST") return Api.post(path, body);
    if (method === "PUT") return Api.put(path, body);
    return Api.del(path);
  }

  // ---------- state ----------
  const sel = { month: NM, year: NY };
  let budgets = [];
  let categories = [];
  let editing = null;
  let deleting = null;
  let donut = null;

  function monthLabel() { return MONTHS[sel.month - 1] + " " + sel.year; }

  function totals() {
    const limit = budgets.reduce(function (s, b) { return s + Number(b.limitAmount); }, 0);
    const spent = budgets.reduce(function (s, b) { return s + Number(b.spent); }, 0);
    return { limit: limit, spent: spent, left: limit - spent, pct: limit > 0 ? Math.round((spent / limit) * 100) : 0 };
  }

  // ---------- summary cards ----------
  function renderCards() {
    const t = totals();
    const n = budgets.length;
    $("b-total").textContent = money(t.limit);
    $("b-total-note").textContent = n === 0 ? "No budgets yet" : n + (n === 1 ? " budget" : " budgets");
    $("b-spent").textContent = money(t.spent);
    $("b-spent-note").textContent = t.limit > 0 ? t.pct + "% of budget" : "";
    $("b-left").textContent = money(t.left);
    const leftNote = $("b-left-note");
    if (t.limit === 0) leftNote.textContent = "";
    else if (t.left >= 0) leftNote.textContent = Math.round((t.left / t.limit) * 100) + "% left";
    else leftNote.textContent = "Over by " + money(-t.left);
    leftNote.className = "note" + (t.left < 0 ? " bad" : "");
    $("b-month").textContent = MONTHS[sel.month - 1].slice(0, 3) + " " + sel.year;
    $("b-month-note").textContent = sel.month === NM && sel.year === NY ? "Current month" : "";
  }

  // ---------- table ----------
  function renderTable() {
    const body = $("bud-body");
    body.innerHTML = "";
    if (budgets.length === 0) {
      const tr = el("tr");
      const td = el("td", "bud-empty", "No budgets for " + monthLabel() + " yet. Click \"Set Budget\" to add one.");
      td.colSpan = 6;
      tr.appendChild(td);
      body.appendChild(tr);
      return;
    }
    budgets.forEach(function (b) {
      const status = safeStatus(b.status);
      const pct = Math.round(Number(b.percentUsed) || 0);
      const tr = el("tr");

      const catTd = el("td");
      const cell = el("div", "cat-cell");
      cell.appendChild(el("span", "tx-chip", (b.categoryName || "?").charAt(0).toUpperCase()));
      cell.appendChild(el("strong", "", b.categoryName));
      catTd.appendChild(cell);
      tr.appendChild(catTd);

      tr.appendChild(el("td", "r", money(b.limitAmount)));
      tr.appendChild(el("td", "r", money(b.spent)));

      const progTd = el("td");
      const prog = el("div", "prog");
      const bar = el("div", "bar lv-" + status);
      bar.setAttribute("role", "progressbar");
      bar.setAttribute("aria-valuenow", String(pct));
      bar.setAttribute("aria-valuemin", "0");
      bar.setAttribute("aria-valuemax", "100");
      const fill = el("div", "fill");
      fill.style.width = Math.min(100, pct) + "%";
      bar.appendChild(fill);
      prog.appendChild(bar);
      prog.appendChild(el("span", "prog-pct", pct + "%"));
      progTd.appendChild(prog);
      tr.appendChild(progTd);

      const stTd = el("td");
      stTd.appendChild(el("span", "pill pill-" + status, STATUS_LABEL[status]));
      tr.appendChild(stTd);

      const actTd = el("td", "r");
      const edit = el("button", "row-btn", "Edit");
      edit.type = "button";
      edit.setAttribute("aria-label", "Edit budget for " + b.categoryName);
      edit.addEventListener("click", function () { openEdit(b); });
      const del = el("button", "row-btn danger", "Delete");
      del.type = "button";
      del.setAttribute("aria-label", "Delete budget for " + b.categoryName);
      del.addEventListener("click", function () { openDelete(b); });
      actTd.appendChild(edit);
      actTd.appendChild(del);
      tr.appendChild(actTd);

      body.appendChild(tr);
    });
  }

  // ---------- donut ----------
  function renderDonut() {
    const t = totals();
    $("b-pct").textContent = t.pct + "%";
    $("ov-spent").textContent = money(t.spent);
    $("ov-left").textContent = money(Math.max(0, t.left));

    if (donut) { donut.destroy(); donut = null; }
    if (typeof Chart === "undefined") return;
    const used = t.limit > 0 ? Math.min(t.spent, t.limit) : 0;
    const rest = t.limit > 0 ? Math.max(0, t.limit - t.spent) : 1;      // grey ring when there is no budget
    donut = new Chart($("b-donut"), {
      type: "doughnut",
      data: { labels: ["Spent", "Remaining"], datasets: [{ data: [used, rest], backgroundColor: ["#5a4bd1", "#e4e6f4"], borderWidth: 0 }] },
      options: { cutout: "72%", maintainAspectRatio: false, plugins: { legend: { display: false } } }
    });
  }

  // ---------- tips ----------
  function renderTips() {
    const title = $("tip-title");
    const text = $("tip-text");
    const exceeded = budgets.filter(function (b) { return b.status === "EXCEEDED"; });
    const warning = budgets.filter(function (b) { return b.status === "WARNING"; });
    function names(list) {
      return list.slice(0, 3).map(function (b) { return b.categoryName; }).join(", ") + (list.length > 3 ? " and more" : "");
    }
    if (budgets.length === 0) {
      title.textContent = "No budgets yet.";
      text.textContent = "Set a monthly limit for a category to start tracking your spending.";
    } else if (exceeded.length > 0) {
      title.textContent = exceeded.length + (exceeded.length === 1 ? " budget is over its limit." : " budgets are over their limit.");
      text.textContent = names(exceeded) + ". Try to cut back, or raise the limit if it is too tight.";
    } else if (warning.length > 0) {
      title.textContent = warning.length + (warning.length === 1 ? " budget is close to its limit." : " budgets are close to their limit.");
      text.textContent = names(warning) + " passed 80%. Keep an eye on them.";
    } else {
      title.textContent = "Great! You are within your budget this month.";
      text.textContent = "Keep going and avoid unnecessary expenses!";
    }
  }

  // ---------- load ----------
  async function loadBudgets() {
    budgets = (await request("GET", "/api/budgets?month=" + sel.month + "&year=" + sel.year)) || [];
    renderCards();
    renderTable();
    renderDonut();
    renderTips();
  }

  function safe(fn) {
    return fn().catch(function (e) { toast(e.message || "Could not load budgets."); });
  }

  // ---------- month / year pickers ----------
  function fillPeriod() {
    const ms = $("b-sel-month");
    MONTHS.forEach(function (name, i) {
      const o = el("option", "", name);
      o.value = String(i + 1);
      ms.appendChild(o);
    });
    ms.value = String(sel.month);
    const ys = $("b-sel-year");
    for (let y = NY - 2; y <= NY + 1; y++) {
      const o = el("option", "", String(y));
      o.value = String(y);
      ys.appendChild(o);
    }
    ys.value = String(sel.year);
    ms.addEventListener("change", function () { sel.month = Number(ms.value); safe(loadBudgets); });
    ys.addEventListener("change", function () { sel.year = Number(ys.value); safe(loadBudgets); });
  }

  // ---------- set / edit popup ----------
  const dlg = $("bud-dialog");
  const form = $("bud-form");

  function showError(message) {
    const box = $("bd-error");
    box.textContent = message;
    box.hidden = !message;
  }

  function fillCategorySelect(list, selectedId) {
    const select = $("bd-cat");
    select.innerHTML = "";
    if (list.length === 0) {
      const o = el("option", "", "Every expense category already has a budget");
      o.value = "";
      select.appendChild(o);
      return;
    }
    list.forEach(function (c) {
      const o = el("option", "", c.name);
      o.value = String(c.id);
      select.appendChild(o);
    });
    if (selectedId) select.value = String(selectedId);
  }

  function openAdd() {
    editing = null;
    $("bd-title").textContent = "Set budget";
    $("bd-period").textContent = "For " + monthLabel();
    const used = budgets.map(function (b) { return b.categoryId; });
    fillCategorySelect(categories.filter(function (c) { return c.type === "EXPENSE" && used.indexOf(c.id) === -1; }));
    $("bd-cat").disabled = false;
    $("bd-limit").value = "";
    showError("");
    dlg.showModal();
  }

  function openEdit(b) {
    editing = b;
    $("bd-title").textContent = "Edit budget";
    $("bd-period").textContent = b.categoryName + " · " + monthLabel();
    fillCategorySelect([{ id: b.categoryId, name: b.categoryName }], b.categoryId);
    $("bd-cat").disabled = true;                     // the category of a budget does not change
    $("bd-limit").value = b.limitAmount;
    showError("");
    dlg.showModal();
  }

  $("bd-cancel").addEventListener("click", function () { dlg.close(); });
  $("qa-add").addEventListener("click", openAdd);
  $("qa-alerts").addEventListener("click", function () {
    window.scrollTo({ top: 0, behavior: "smooth" });
    const bell = $("bell");
    if (bell) bell.click();
  });

  form.addEventListener("submit", async function (event) {
    event.preventDefault();
    const categoryId = $("bd-cat").value;
    const limit = Number($("bd-limit").value);
    if (!categoryId) { showError("Please choose a category."); return; }
    if (!(limit > 0)) { showError("The limit must be more than 0."); return; }

    const body = { categoryId: Number(categoryId), month: sel.month, year: sel.year, limitAmount: limit };
    const save = $("bd-save");
    save.disabled = true;
    showError("");
    try {
      if (editing) await request("PUT", "/api/budgets/" + editing.id, body);
      else await request("POST", "/api/budgets", body);
      dlg.close();
      toast(editing ? "Budget updated." : "Budget added.");
      await loadBudgets();
    } catch (e) {
      if (e.status === 409) {
        showError("This category already has a budget for " + monthLabel() + ". Edit that one instead.");
      } else {
        const details = (e.fieldErrors || []).map(function (f) { return f.message; }).join(" ");
        showError(details || e.message || "Could not save. Please try again.");
      }
    } finally {
      save.disabled = false;
    }
  });

  // ---------- delete confirmation ----------
  const delDlg = $("bdel-dialog");

  function openDelete(b) {
    deleting = b;
    $("bdel-text").textContent = "Delete the " + b.categoryName + " budget (" + money(b.limitAmount) + ") for " + monthLabel() + "? This cannot be undone.";
    delDlg.showModal();
  }

  $("bdel-cancel").addEventListener("click", function () { delDlg.close(); });
  $("bdel-confirm").addEventListener("click", async function () {
    if (!deleting) return;
    const btn = $("bdel-confirm");
    btn.disabled = true;
    try {
      await request("DELETE", "/api/budgets/" + deleting.id);
      delDlg.close();
      toast("Budget deleted.");
      await loadBudgets();
    } catch (e) {
      delDlg.close();
      toast(e.message || "Could not delete.");
    } finally {
      btn.disabled = false;
      deleting = null;
    }
  });

  // ---------- start ----------
  (async function init() {
    fillPeriod();
    try {
      categories = (await request("GET", "/api/categories")) || [];
    } catch (e) {
      toast(e.message || "Could not load categories.");
    }
    safe(loadBudgets);
  })();
})();