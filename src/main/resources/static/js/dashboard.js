/*
 * /js/dashboard.js - fills the dashboard page.
 *
 * USE_MOCK = true  -> uses the fake data below (no backend needed).
 * USE_MOCK = false -> calls the real APIs from the team contract (section 6).
 *
 * Data sources:
 *   summary cards  /api/summary/monthly + /api/summary/trend
 *   donut + legend /api/summary/by-category
 *   calendar       /api/transactions (one month)
 *   today list     /api/transactions (today only)
 *   budget cards   /api/budgets (colour comes from the backend "status")
 */
(function () {
  "use strict";

  const USE_MOCK = true;

  const PALETTE = ["#3f3a9e", "#a78bfa", "#fdba74", "#86e0c4", "#93c5fd", "#f9a8d4", "#fcd34d"];

  // ---------- small helpers ----------
  const $ = (id) => document.getElementById(id);
  const pad = (n) => String(n).padStart(2, "0");
  const iso = (y, m, d) => y + "-" + pad(m) + "-" + pad(d);

  function money(n) {
    const v = Number(n || 0);
    return (v < 0 ? "-" : "") + "₹" + Math.abs(v).toLocaleString("en-IN", { maximumFractionDigits: 0 });
  }

  // Build an element; text goes in with textContent so server text is never run as HTML.
  function el(tag, cls, text) {
    const e = document.createElement(tag);
    if (cls) e.className = cls;
    if (text !== undefined) e.textContent = text;
    return e;
  }

  function monthShift(year, month, delta) {
    const d = new Date(year, month - 1 + delta, 1);
    return { year: d.getFullYear(), month: d.getMonth() + 1 };
  }

  function timeOf(isoString) {
    if (!isoString) return "";
    const d = new Date(isoString);
    if (isNaN(d)) return "";
    return d.toLocaleTimeString("en-IN", { hour: "numeric", minute: "2-digit" }).toUpperCase();
  }

  const now = new Date();
  const NY = now.getFullYear();
  const NM = now.getMonth() + 1;
  const TODAY = iso(NY, NM, now.getDate());

  // ---------- fake data (only used when USE_MOCK = true) ----------
  function mockGet(path) {
    const query = new URLSearchParams(path.split("?")[1] || "");

    if (path.startsWith("/api/summary/monthly")) {
      return { month: NM, year: NY, totalIncome: 32000, totalExpense: 19550, balance: 12450 };
    }
    if (path.startsWith("/api/summary/trend")) {
      return [
        { label: "last", income: 28571, expense: 18443 },
        { label: "this", income: 32000, expense: 19550 }
      ];
    }
    if (path.startsWith("/api/summary/by-category")) {
      return [
        { category: "Food & Dining", total: 6640 },
        { category: "Transport", total: 4290 },
        { category: "Shopping", total: 3120 },
        { category: "Bills & Utilities", total: 2340 },
        { category: "Others", total: 3160 }
      ];
    }
    if (path.startsWith("/api/budgets")) {
      return [
        { id: 1, categoryName: "Food & Dining", limitAmount: 8000, spent: 6640, percentUsed: 83, status: "WARNING" },
        { id: 2, categoryName: "Transport", limitAmount: 5000, spent: 4290, percentUsed: 86, status: "WARNING" },
        { id: 3, categoryName: "Shopping", limitAmount: 5000, spent: 3120, percentUsed: 62, status: "OK" },
        { id: 4, categoryName: "Bills & Utilities", limitAmount: 4000, spent: 2340, percentUsed: 58, status: "OK" }
      ];
    }
    if (path.startsWith("/api/transactions")) {
      if (query.get("from") === query.get("to")) {          // the "today" request
        return {
          content: [
            { id: 1, categoryName: "Food & Dining", type: "EXPENSE", amount: 450, description: "Lunch", date: TODAY, createdAt: TODAY + "T13:32:00" },
            { id: 2, categoryName: "Transport", type: "EXPENSE", amount: 220, description: "Bus Pass", date: TODAY, createdAt: TODAY + "T10:15:00" },
            { id: 3, categoryName: "Food & Dining", type: "EXPENSE", amount: 610, description: "Groceries", date: TODAY, createdAt: TODAY + "T09:10:00" }
          ]
        };
      }
      const y = Number(query.get("from").slice(0, 4));        // the "whole month" request
      const m = Number(query.get("from").slice(5, 7));
      const days = [1, 2, 4, 5, 6, 7, 9, 10, 12, 13, 14, 16, 17, 19, 21, 22, 23, 24, 25, 26, 28, 29];
      const content = [];
      days.forEach(function (d) {
        content.push({ type: "EXPENSE", date: iso(y, m, d) });
        if (d % 7 === 0) content.push({ type: "INCOME", date: iso(y, m, d) });
      });
      return { content: content };
    }
    return null;
  }

  function get(path) {
    return USE_MOCK ? Promise.resolve(mockGet(path)) : Api.get(path);
  }

  // ---------- 1. summary cards ----------
  function setNote(id, current, previous, upIsGood) {
    const note = $(id);
    if (!previous) {
      note.textContent = "No data for last month";
      note.className = "note";
      return;
    }
    const pct = Math.round(((current - previous) / Math.abs(previous)) * 100);
    const up = pct >= 0;
    note.textContent = (up ? "↑ " : "↓ ") + Math.abs(pct) + "% than last month";
    note.className = "note " + (up === upIsGood ? "good" : "bad");
  }

  async function loadSummary() {
    const results = await Promise.all([
      get("/api/summary/monthly?month=" + NM + "&year=" + NY),
      get("/api/summary/trend?months=2")
    ]);
    const m = results[0] || {};
    const trend = results[1] || [];
    const prev = trend.length > 1 ? trend[trend.length - 2] : null;

    $("val-balance").textContent = money(m.balance);
    $("val-income").textContent = money(m.totalIncome);
    $("val-expense").textContent = money(m.totalExpense);

    setNote("note-balance", m.balance, prev ? prev.income - prev.expense : 0, true);
    setNote("note-income", m.totalIncome, prev ? prev.income : 0, true);
    setNote("note-expense", m.totalExpense, prev ? prev.expense : 0, false);   // spending going up is bad
  }

  // ---------- 2. donut + legend ----------
  let donut = null;

  async function loadDonut() {
    const shift = Number($("period").value) || 0;
    const p = monthShift(NY, NM, -shift);
    const rows = (await get("/api/summary/by-category?month=" + p.month + "&year=" + p.year + "&type=EXPENSE")) || [];
    const total = rows.reduce(function (sum, r) { return sum + Number(r.total); }, 0);

    $("donut-total").textContent = money(total);

    const legend = $("legend");
    legend.innerHTML = "";
    if (rows.length === 0) {
      legend.appendChild(el("li", "legend-empty", "No spending yet."));
    }
    rows.forEach(function (r, i) {
      const li = el("li", "legend-item");
      const dot = el("span", "dot");
      dot.style.background = PALETTE[i % PALETTE.length];
      const text = el("div", "legend-text");
      text.appendChild(el("strong", "", r.category));
      const share = total > 0 ? Math.round((Number(r.total) / total) * 100) : 0;
      text.appendChild(el("small", "", share + "%  " + money(r.total)));
      li.appendChild(dot);
      li.appendChild(text);
      legend.appendChild(li);
    });

    if (donut) { donut.destroy(); donut = null; }
    if (typeof Chart === "undefined" || rows.length === 0) return;

    donut = new Chart($("donut"), {
      type: "doughnut",
      data: {
        labels: rows.map(function (r) { return r.category; }),
        datasets: [{
          data: rows.map(function (r) { return Number(r.total); }),
          backgroundColor: rows.map(function (_, i) { return PALETTE[i % PALETTE.length]; }),
          borderWidth: 0
        }]
      },
      options: { cutout: "68%", maintainAspectRatio: false, plugins: { legend: { display: false } } }
    });
  }

  // ---------- 3. today list ----------
  async function loadToday() {
    $("today-title").textContent = "Today · " +
      now.toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" });

    const page = await get("/api/transactions?from=" + TODAY + "&to=" + TODAY + "&size=20&sort=date,desc");
    const items = (page && page.content) || [];
    const spent = items
      .filter(function (t) { return t.type === "EXPENSE"; })
      .reduce(function (sum, t) { return sum + Number(t.amount); }, 0);

    $("today-total").textContent = money(spent);
    $("today-count").textContent = items.length + (items.length === 1 ? " transaction" : " transactions");

    const list = $("today-list");
    list.innerHTML = "";
    if (items.length === 0) {
      list.appendChild(el("li", "today-empty", "Nothing spent today."));
      return;
    }
    items.slice(0, 4).forEach(function (t) {
      const li = el("li", "today-item");
      li.appendChild(el("span", "tx-chip", (t.categoryName || "?").charAt(0).toUpperCase()));

      const info = el("div", "tx-info");
      info.appendChild(el("strong", "", t.description || t.categoryName));
      const time = timeOf(t.createdAt);
      info.appendChild(el("small", "", (t.categoryName || "") + (time ? " · " + time : "")));
      li.appendChild(info);

      const isExpense = t.type === "EXPENSE";
      li.appendChild(el("span", "tx-amt " + (isExpense ? "exp" : "inc"), (isExpense ? "- " : "+ ") + money(t.amount)));
      list.appendChild(li);
    });
  }

  // ---------- 4. calendar ----------
  let calY = NY;
  let calM = NM;

  async function loadCalendar() {
    const last = new Date(calY, calM, 0).getDate();
    const page = await get("/api/transactions?from=" + iso(calY, calM, 1) + "&to=" + iso(calY, calM, last) +
      "&size=500&sort=date,asc");
    const items = (page && page.content) || [];

    const marks = {};                                        // "2026-10-14" -> {exp:true, inc:false}
    items.forEach(function (t) {
      const key = String(t.date).slice(0, 10);
      marks[key] = marks[key] || { exp: false, inc: false };
      if (t.type === "EXPENSE") marks[key].exp = true;
      if (t.type === "INCOME") marks[key].inc = true;
    });

    $("cal-title").textContent = new Date(calY, calM - 1, 1)
      .toLocaleDateString("en-IN", { month: "long", year: "numeric" });

    const grid = $("cal-grid");
    grid.innerHTML = "";
    ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"].forEach(function (d) {
      grid.appendChild(el("span", "cal-dow", d));
    });
    const firstWeekday = new Date(calY, calM - 1, 1).getDay();
    for (let i = 0; i < firstWeekday; i++) grid.appendChild(el("span", "cal-blank"));

    for (let day = 1; day <= last; day++) {
      const key = iso(calY, calM, day);
      const cell = el("span", "cal-day" + (key === TODAY ? " today" : ""));
      cell.appendChild(el("span", "cal-num", String(day)));
      const mark = marks[key];
      if (mark) {
        cell.appendChild(el("span", "dot " + (mark.exp && mark.inc ? "dot-both" : mark.exp ? "dot-exp" : "dot-inc")));
      }
      grid.appendChild(cell);
    }
  }

  // ---------- 5. budget cards + "Budget Left" card ----------
  async function loadBudgets() {
    const buds = (await get("/api/budgets?month=" + NM + "&year=" + NY)) || [];

    const limit = buds.reduce(function (s, b) { return s + Number(b.limitAmount); }, 0);
    const spent = buds.reduce(function (s, b) { return s + Number(b.spent); }, 0);
    const left = limit - spent;
    const note = $("note-budget");
    if (limit === 0) {
      $("val-budget").textContent = "–";
      note.textContent = "No budgets set";
      note.className = "note";
    } else {
      $("val-budget").textContent = money(left);
      note.textContent = left >= 0 ? Math.round((left / limit) * 100) + "% of total budget" : "Over budget";
      note.className = "note " + (left >= 0 ? "" : "bad");
    }

    const list = $("bud-list");
    list.innerHTML = "";
    if (buds.length === 0) {
      list.appendChild(el("li", "bud-empty", "No budgets for this month yet."));
      return;
    }
    buds.slice(0, 4).forEach(function (b) {
      const pct = Math.round(Number(b.percentUsed) || 0);
      const li = el("li", "bud-card st-" + b.status);

      const head = el("div", "bud-head");
      head.appendChild(el("span", "tx-chip", (b.categoryName || "?").charAt(0).toUpperCase()));
      head.appendChild(el("span", "bud-name", b.categoryName));
      li.appendChild(head);

      const amounts = el("p", "bud-amounts");
      amounts.appendChild(el("strong", "", money(b.spent)));
      amounts.appendChild(el("small", "", " / " + money(b.limitAmount)));
      li.appendChild(amounts);

      const row = el("div", "bud-bar-row");
      const bar = el("div", "bar");
      bar.setAttribute("role", "progressbar");
      bar.setAttribute("aria-valuenow", String(pct));
      bar.setAttribute("aria-valuemin", "0");
      bar.setAttribute("aria-valuemax", "100");
      const fill = el("div", "fill");
      fill.style.width = Math.min(100, pct) + "%";
      bar.appendChild(fill);
      row.appendChild(bar);
      row.appendChild(el("span", "bud-pct", pct + "%"));
      li.appendChild(row);

      list.appendChild(li);
    });
  }

  // ---------- start ----------
  function safe(fn) {
    return fn().catch(function (e) {
      if (window.Layout) Layout.toast(e.message || "Could not load some data");
    });
  }

  const hour = now.getHours();
  $("greet").textContent = hour < 12 ? "Good morning," : hour < 17 ? "Good afternoon," : "Good evening,";
  const user = Api.getUser();
  const firstName = user && user.name ? user.name.trim().split(" ")[0] : "";
  const heading = document.querySelector(".hello h1");
  if (heading && firstName) heading.textContent = "Welcome back, " + firstName + "!";

  $("period").addEventListener("change", function () { safe(loadDonut); });
  $("cal-prev").addEventListener("click", function () {
    calM -= 1;
    if (calM < 1) { calM = 12; calY -= 1; }
    safe(loadCalendar);
  });
  $("cal-next").addEventListener("click", function () {
    calM += 1;
    if (calM > 12) { calM = 1; calY += 1; }
    safe(loadCalendar);
  });

  safe(loadSummary);
  safe(loadDonut);
  safe(loadToday);
  safe(loadCalendar);
  safe(loadBudgets);
})();