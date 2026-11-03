/*
 * /js/transactions.js - the Transactions page.
 *
 * USE_MOCK = true  -> fake data kept in memory (reloading the page resets it).
 * USE_MOCK = false -> real APIs from the team contract:
 *   GET /api/transactions?page&size&sort&from&to&categoryId&type&q   (6.3)
 *   POST / PUT /api/transactions[/{id}]   DELETE /api/transactions/{id}
 *   GET /api/categories (6.2)   GET /api/summary/monthly   GET /api/budgets
 *
 * Search (q): sent to the server. Until the backend supports it, the rows that
 * come back are also filtered here, but only for the current page.
 */
(function () {
  "use strict";

  const USE_MOCK = true;
  const PAGE_SIZE = 10;

  // ---------- helpers ----------
  const $ = (id) => document.getElementById(id);
  const pad = (n) => String(n).padStart(2, "0");
  const iso = (y, m, d) => y + "-" + pad(m) + "-" + pad(d);

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

  function fmtDate(isoDate) {
    const p = String(isoDate).slice(0, 10).split("-");
    return new Date(Number(p[0]), Number(p[1]) - 1, Number(p[2]))
      .toLocaleDateString("en-IN", { day: "2-digit", month: "short", year: "numeric" });
  }

  function timeOf(isoString) {
    if (!isoString) return "";
    const d = new Date(isoString);
    if (isNaN(d)) return "";
    return d.toLocaleTimeString("en-IN", { hour: "numeric", minute: "2-digit" }).toUpperCase();
  }

  function toast(message) { if (window.Layout) Layout.toast(message); }

  const now = new Date();
  const NY = now.getFullYear();
  const NM = now.getMonth() + 1;
  const TODAY = iso(NY, NM, now.getDate());

  // ---------- fake data (only used when USE_MOCK = true) ----------
  const MOCK_CATS = [
    { id: 1, name: "Salary", type: "INCOME", isDefault: true },
    { id: 2, name: "Freelance", type: "INCOME", isDefault: true },
    { id: 3, name: "Gifts", type: "INCOME", isDefault: true },
    { id: 11, name: "Food & Dining", type: "EXPENSE", isDefault: true },
    { id: 12, name: "Transport", type: "EXPENSE", isDefault: true },
    { id: 13, name: "Shopping", type: "EXPENSE", isDefault: true },
    { id: 14, name: "Bills & Utilities", type: "EXPENSE", isDefault: true },
    { id: 15, name: "Groceries", type: "EXPENSE", isDefault: true },
    { id: 16, name: "Others", type: "EXPENSE", isDefault: true }
  ];
  let mockTx = null;
  let mockNextId = 1000;

  function mockCat(id) { return MOCK_CATS.find(function (c) { return c.id === Number(id); }); }

  function seedMock() {
    const expense = [["Zomato", 11, 450], ["Metro Card", 12, 220], ["Big Basket", 15, 610], ["Electricity Bill", 14, 1250],
                     ["Amazon Order", 13, 1899], ["Uber Ride", 12, 340], ["Movie Tickets", 16, 700], ["Grocery Store", 15, 980]];
    const income = [["Salary Credit", 1, 28000], ["Freelance Payment", 2, 5000]];
    mockTx = [];
    for (let i = 0; i < 42; i++) {
      const d = new Date(NY, NM - 1, now.getDate() - i);
      const isIncome = i % 6 === 2;
      const s = isIncome ? income[(i / 6 | 0) % income.length] : expense[i % expense.length];
      const date = iso(d.getFullYear(), d.getMonth() + 1, d.getDate());
      mockTx.push({
        id: i + 1, categoryId: s[1], categoryName: mockCat(s[1]).name, type: isIncome ? "INCOME" : "EXPENSE",
        amount: s[2], description: s[0], date: date,
        createdAt: date + "T" + pad(9 + (i * 3) % 11) + ":" + pad((i * 7) % 60) + ":00"
      });
    }
  }

  function mockList(q) {
    let rows = mockTx.slice();
    if (q.get("from")) rows = rows.filter(function (t) { return t.date >= q.get("from"); });
    if (q.get("to")) rows = rows.filter(function (t) { return t.date <= q.get("to"); });
    if (q.get("categoryId")) rows = rows.filter(function (t) { return t.categoryId === Number(q.get("categoryId")); });
    if (q.get("type")) rows = rows.filter(function (t) { return t.type === q.get("type"); });
    if (q.get("q")) {
      const needle = q.get("q").toLowerCase();
      rows = rows.filter(function (t) { return (t.description || "").toLowerCase().indexOf(needle) !== -1; });
    }
    rows.sort(function (a, b) { return a.date < b.date ? 1 : a.date > b.date ? -1 : b.id - a.id; });
    const size = Number(q.get("size")) || PAGE_SIZE;
    const page = Number(q.get("page")) || 0;
    const totalPages = Math.ceil(rows.length / size);
    return {
      content: rows.slice(page * size, page * size + size), number: page, size: size,
      totalElements: rows.length, totalPages: totalPages, first: page === 0, last: page >= totalPages - 1
    };
  }

  function mockBuild(body, id, createdAt) {
    const cat = mockCat(body.categoryId);
    return {
      id: id, categoryId: Number(body.categoryId), categoryName: cat ? cat.name : null, type: body.type,
      amount: Number(body.amount), description: body.description || "", date: body.date,
      createdAt: createdAt || new Date().toISOString().slice(0, 19)
    };
  }

  function mock(method, path, body) {
    if (!mockTx) seedMock();
    const parts = path.split("?");
    const route = parts[0];
    const q = new URLSearchParams(parts[1] || "");

    if (route === "/api/categories") return MOCK_CATS;
    if (route === "/api/budgets") {
      return [{ limitAmount: 8000, spent: 6640 }, { limitAmount: 5000, spent: 4290 },
              { limitAmount: 5000, spent: 3120 }, { limitAmount: 4000, spent: 2340 }];
    }
    if (route === "/api/summary/monthly") {
      const prefix = iso(NY, NM, 1).slice(0, 7);
      let inc = 0, exp = 0;
      mockTx.forEach(function (t) {
        if (t.date.indexOf(prefix) !== 0) return;
        if (t.type === "INCOME") inc += t.amount; else exp += t.amount;
      });
      return { month: NM, year: NY, totalIncome: inc, totalExpense: exp, balance: inc - exp };
    }
    const m = route.match(/^\/api\/transactions\/(\d+)$/);
    if (route === "/api/transactions" && method === "GET") return mockList(q);
    if (route === "/api/transactions" && method === "POST") {
      const t = mockBuild(body, mockNextId++);
      mockTx.push(t);
      return t;
    }
    if (m && method === "PUT") {
      const i = mockTx.findIndex(function (t) { return t.id === Number(m[1]); });
      if (i >= 0) mockTx[i] = mockBuild(body, mockTx[i].id, mockTx[i].createdAt);
      return mockTx[i];
    }
    if (m && method === "DELETE") {
      mockTx = mockTx.filter(function (t) { return t.id !== Number(m[1]); });
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
  const state = { page: 0, from: "", to: "", categoryId: "", type: "", q: "" };
  let categories = [];
  let editing = null;      // the transaction being edited, or null when adding
  let deleting = null;     // the transaction waiting for delete confirmation
  let dialogType = "EXPENSE";

  // ---------- summary cards (this month) ----------
  async function loadSummary() {
    const results = await Promise.all([
      request("GET", "/api/summary/monthly?month=" + NM + "&year=" + NY),
      request("GET", "/api/budgets?month=" + NM + "&year=" + NY)
    ]);
    const m = results[0] || {};
    const buds = results[1] || [];
    $("sum-income").textContent = money(m.totalIncome);
    $("sum-expense").textContent = money(m.totalExpense);
    $("sum-balance").textContent = money(m.balance);

    const limit = buds.reduce(function (s, b) { return s + Number(b.limitAmount); }, 0);
    const spent = buds.reduce(function (s, b) { return s + Number(b.spent); }, 0);
    const note = $("sum-budget-note");
    if (limit === 0) {
      $("sum-budget").textContent = "–";
      note.textContent = "No budgets set";
    } else {
      $("sum-budget").textContent = money(limit - spent);
      note.textContent = limit - spent >= 0 ? Math.round(((limit - spent) / limit) * 100) + "% of total budget" : "Over budget";
    }
  }

  // ---------- table ----------
  function renderRows(rows) {
    const body = $("tx-body");
    body.innerHTML = "";
    if (rows.length === 0) {
      const tr = el("tr");
      const td = el("td", "tx-empty", "No transactions found. Try changing the filters, or add a new one.");
      td.colSpan = 6;
      tr.appendChild(td);
      body.appendChild(tr);
      return;
    }
    rows.forEach(function (t) {
      const isExpense = t.type === "EXPENSE";
      const tr = el("tr");

      const dateTd = el("td");
      dateTd.appendChild(el("strong", "", fmtDate(t.date)));
      const time = timeOf(t.createdAt);
      if (time) dateTd.appendChild(el("small", "sub", time));
      tr.appendChild(dateTd);

      const descTd = el("td");
      descTd.appendChild(el("strong", "", t.description || "–"));
      tr.appendChild(descTd);

      const catTd = el("td");
      catTd.appendChild(el("span", "pill pill-cat", t.categoryName || "Uncategorised"));
      tr.appendChild(catTd);

      const typeTd = el("td");
      typeTd.appendChild(el("span", "pill pill-" + t.type, isExpense ? "Expense" : "Income"));
      tr.appendChild(typeTd);

      const amtTd = el("td", "r");
      amtTd.appendChild(el("span", "amt " + (isExpense ? "exp" : "inc"), (isExpense ? "- " : "+ ") + money(t.amount)));
      tr.appendChild(amtTd);

      const actTd = el("td", "r");
      const edit = el("button", "row-btn", "Edit");
      edit.type = "button";
      edit.setAttribute("aria-label", "Edit " + (t.description || "transaction"));
      edit.addEventListener("click", function () { openEdit(t); });
      const del = el("button", "row-btn danger", "Delete");
      del.type = "button";
      del.setAttribute("aria-label", "Delete " + (t.description || "transaction"));
      del.addEventListener("click", function () { openDelete(t); });
      actTd.appendChild(edit);
      actTd.appendChild(del);
      tr.appendChild(actTd);

      body.appendChild(tr);
    });
  }

  function renderPager(data, shown) {
    const info = $("pager-info");
    const pager = $("pager");
    pager.innerHTML = "";

    const total = data.totalElements || 0;
    if (total === 0) {
      info.textContent = "No transactions";
      return;
    }
    const start = data.number * data.size + 1;
    info.textContent = "Showing " + start + " - " + (start + shown - 1 < start ? start : start + shown - 1) + " of " + total + " transactions";

    function addBtn(label, page, opts) {
      const b = el("button", "pg", label);
      b.type = "button";
      if (opts && opts.disabled) b.disabled = true;
      if (opts && opts.current) b.setAttribute("aria-current", "page");
      if (opts && opts.aria) b.setAttribute("aria-label", opts.aria);
      b.addEventListener("click", function () { state.page = page; safe(loadList); });
      pager.appendChild(b);
    }

    const pages = data.totalPages || 1;
    addBtn("‹", data.number - 1, { disabled: data.first, aria: "Previous page" });
    const first = Math.max(0, Math.min(data.number - 2, pages - 5));
    const last = Math.min(pages, first + 5);
    for (let p = first; p < last; p++) {
      addBtn(String(p + 1), p, { current: p === data.number, aria: "Page " + (p + 1) });
    }
    addBtn("›", data.number + 1, { disabled: data.last, aria: "Next page" });
  }

  async function loadList() {
    const p = new URLSearchParams({ page: state.page, size: PAGE_SIZE, sort: "date,desc" });
    if (state.from) p.set("from", state.from);
    if (state.to) p.set("to", state.to);
    if (state.categoryId) p.set("categoryId", state.categoryId);
    if (state.type) p.set("type", state.type);
    if (state.q) p.set("q", state.q);

    const data = (await request("GET", "/api/transactions?" + p.toString())) || { content: [] };
    let rows = data.content || [];
    if (state.q) {                                    // safety net, see the note at the top
      const needle = state.q.toLowerCase();
      rows = rows.filter(function (t) { return (t.description || "").toLowerCase().indexOf(needle) !== -1; });
    }
    renderRows(rows);
    renderPager(data, rows.length);
  }

  // ---------- filters ----------
  function fillFilterCategories() {
    const select = $("f-cat");
    const keep = select.value;
    select.innerHTML = "";
    const all = el("option", "", "All Categories");
    all.value = "";
    select.appendChild(all);
    categories.forEach(function (c) {
      if (state.type && c.type !== state.type) return;
      const o = el("option", "", c.name);
      o.value = String(c.id);
      select.appendChild(o);
    });
    select.value = Array.prototype.some.call(select.options, function (o) { return o.value === keep; }) ? keep : "";
    state.categoryId = select.value;
  }

  function applyFilters() {
    if (state.from && state.to && state.from > state.to) {
      toast("The From date is after the To date.");
      return;
    }
    state.page = 0;
    safe(loadList);
  }

  let searchTimer = null;
  $("f-q").addEventListener("input", function (e) {
    clearTimeout(searchTimer);
    searchTimer = setTimeout(function () { state.q = e.target.value.trim(); applyFilters(); }, 350);
  });
  $("f-type").addEventListener("change", function (e) { state.type = e.target.value; fillFilterCategories(); applyFilters(); });
  $("f-cat").addEventListener("change", function (e) { state.categoryId = e.target.value; applyFilters(); });
  $("f-from").addEventListener("change", function (e) { state.from = e.target.value; applyFilters(); });
  $("f-to").addEventListener("change", function (e) { state.to = e.target.value; applyFilters(); });
  $("f-reset").addEventListener("click", function () {
    $("f-q").value = ""; $("f-type").value = ""; $("f-from").value = ""; $("f-to").value = "";
    state.q = ""; state.type = ""; state.from = ""; state.to = ""; state.categoryId = "";
    fillFilterCategories();
    applyFilters();
  });

  // ---------- add / edit popup ----------
  const dlg = $("tx-dialog");
  const form = $("tx-form");

  function setDialogType(type, selectedId) {
    dialogType = type;
    document.querySelectorAll(".seg-btn").forEach(function (b) {
      b.setAttribute("aria-pressed", String(b.getAttribute("data-type") === type));
    });
    const select = $("d-cat");
    select.innerHTML = "";
    const list = categories.filter(function (c) { return c.type === type; });
    if (list.length === 0) {
      const o = el("option", "", "No categories for this type");
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

  function showError(message) {
    const box = $("d-error");
    box.textContent = message;
    box.hidden = !message;
  }

  function openAdd() {
    editing = null;
    $("dlg-title").textContent = "Add transaction";
    setDialogType("EXPENSE");
    $("d-amount").value = "";
    $("d-date").value = TODAY;
    $("d-desc").value = "";
    showError("");
    dlg.showModal();
  }

  function openEdit(t) {
    editing = t;
    $("dlg-title").textContent = "Edit transaction";
    setDialogType(t.type, t.categoryId);
    $("d-amount").value = t.amount;
    $("d-date").value = String(t.date).slice(0, 10);
    $("d-desc").value = t.description || "";
    showError("");
    dlg.showModal();
  }

  document.querySelectorAll(".seg-btn").forEach(function (b) {
    b.addEventListener("click", function () { setDialogType(b.getAttribute("data-type")); });
  });
  $("add-btn").addEventListener("click", openAdd);
  $("d-cancel").addEventListener("click", function () { dlg.close(); });

  form.addEventListener("submit", async function (event) {
    event.preventDefault();
    const categoryId = $("d-cat").value;
    const amount = Number($("d-amount").value);
    const date = $("d-date").value;
    if (!categoryId) { showError("Please choose a category."); return; }
    if (!(amount > 0)) { showError("Amount must be more than 0."); return; }
    if (!date) { showError("Please choose a date."); return; }

    const body = { categoryId: Number(categoryId), type: dialogType, amount: amount, description: $("d-desc").value.trim(), date: date };
    const save = $("d-save");
    save.disabled = true;
    showError("");
    try {
      if (editing) await request("PUT", "/api/transactions/" + editing.id, body);
      else await request("POST", "/api/transactions", body);
      dlg.close();
      toast(editing ? "Transaction updated." : "Transaction added.");
      await Promise.all([loadList(), loadSummary()]);
    } catch (e) {
      const details = (e.fieldErrors || []).map(function (f) { return f.message; }).join(" ");
      showError(details || e.message || "Could not save. Please try again.");
    } finally {
      save.disabled = false;
    }
  });

  // ---------- delete confirmation ----------
  const delDlg = $("del-dialog");

  function openDelete(t) {
    deleting = t;
    $("del-text").textContent = "Delete \"" + (t.description || "this transaction") + "\" (" + money(t.amount) + ", " + fmtDate(t.date) + ")? This cannot be undone.";
    delDlg.showModal();
  }

  $("del-cancel").addEventListener("click", function () { delDlg.close(); });
  $("del-confirm").addEventListener("click", async function () {
    if (!deleting) return;
    const btn = $("del-confirm");
    btn.disabled = true;
    try {
      await request("DELETE", "/api/transactions/" + deleting.id);
      delDlg.close();
      toast("Transaction deleted.");
      if ($("tx-body").children.length <= 1 && state.page > 0) state.page -= 1;   // last row on this page was removed
      await Promise.all([loadList(), loadSummary()]);
    } catch (e) {
      delDlg.close();
      toast(e.message || "Could not delete.");
    } finally {
      btn.disabled = false;
      deleting = null;
    }
  });

  // ---------- start ----------
  function safe(fn) {
    return fn().catch(function (e) { toast(e.message || "Could not load transactions."); });
  }

  (async function init() {
    try {
      categories = (await request("GET", "/api/categories")) || [];
    } catch (e) {
      toast(e.message || "Could not load categories.");
    }
    fillFilterCategories();
    safe(loadList);
    safe(loadSummary);
  })();
})();