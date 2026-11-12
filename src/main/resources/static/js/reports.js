/*
 * /js/reports.js - the Reports page.
 *
 * USE_MOCK = true  -> fake data (downloads are skipped in preview).
 * USE_MOCK = false -> real APIs from the team contract:
 *   GET /api/summary/trend?months=N              (6.5, oldest first)
 *   GET /api/summary/by-category?month&year&type (6.5)
 *   GET /api/reports/monthly?month&year&format   (6.7, file download via Api.download)
 *
 * Period tabs (1, 3, 6, 12 months): cards, trend chart, category donut and the table
 * follow the tab. "vs last period" compares with the same number of months just before.
 * Savings rate = (income - expenses) / income, worked out here for display only.
 * Downloads cover ONE month (the contract exports one month at a time).
 */
(function () {
  "use strict";

  const USE_MOCK = true;

  const MONTHS = ["January", "February", "March", "April", "May", "June",
                  "July", "August", "September", "October", "November", "December"];
  const PALETTE = ["#3f3a9e", "#a78bfa", "#fdba74", "#86e0c4", "#93c5fd", "#f9a8d4", "#fcd34d"];
  const EXT = { pdf: "pdf", xlsx: "xlsx", csv: "csv" };

  // ---------- helpers ----------
  const $ = (id) => document.getElementById(id);
  const pad = (n) => String(n).padStart(2, "0");

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

  function parseLabel(label) {                          // "2026-05" -> { year: 2026, month: 5 }
    const p = String(label).split("-");
    return { year: Number(p[0]), month: Number(p[1]) };
  }
  const shortMonth = (label) => MONTHS[parseLabel(label).month - 1].slice(0, 3);
  const longLabel = (label) => shortMonth(label) + " " + parseLabel(label).year;

  const now = new Date();
  const NY = now.getFullYear();
  const NM = now.getMonth() + 1;

  // ---------- fake data (only used when USE_MOCK = true) ----------
  function mockTrend() {
    const rows = [];
    for (let k = 23; k >= 0; k--) {
      const d = new Date(NY, NM - 1 - k, 1);
      const i = 23 - k;
      rows.push({
        label: d.getFullYear() + "-" + pad(d.getMonth() + 1),
        income: k === 0 ? 32000 : 27000 + (i * 379) % 6000,
        expense: k === 0 ? 19550 : 17000 + (i * 811) % 5000
      });
    }
    return rows;
  }

  function mock(path) {
    const parts = path.split("?");
    const q = new URLSearchParams(parts[1] || "");
    if (parts[0] === "/api/summary/trend") {
      const n = Number(q.get("months")) || 6;
      return mockTrend().slice(-n);
    }
    if (parts[0] === "/api/summary/by-category") {
      const label = q.get("year") + "-" + pad(Number(q.get("month")));
      const row = mockTrend().find(function (r) { return r.label === label; });
      const total = row ? row.expense : 0;
      const shares = [["Food", 0.30], ["Transport", 0.22], ["Utilities", 0.16], ["Shopping", 0.12], ["Health", 0.11], ["Other", 0.09]];
      return shares.map(function (s) { return { category: s[0], total: Math.round(total * s[1]) }; });
    }
    return null;
  }

  function get(path) {
    return USE_MOCK ? Promise.resolve(mock(path)) : Api.get(path);
  }

  // ---------- state ----------
  let months = 1;
  let trendChart = null;
  let donut = null;

  function sum(rows, key) { return rows.reduce(function (s, r) { return s + Number(r[key]); }, 0); }
  function rateOf(income, expense) { return income > 0 ? Math.round(((income - expense) / income) * 100) : null; }

  // ---------- cards ----------
  function setNote(id, current, previous, upIsGood) {
    const note = $(id);
    if (previous === null || previous === 0) {
      note.textContent = "No earlier data";
      note.className = "note";
      return;
    }
    const pct = Math.round(((current - previous) / Math.abs(previous)) * 100);
    const up = pct >= 0;
    note.textContent = (up ? "↑ " : "↓ ") + Math.abs(pct) + "% vs last period";
    note.className = "note " + (up === upIsGood ? "good" : "bad");
  }

  function renderCards(cur, prev) {
    const income = sum(cur, "income");
    const expense = sum(cur, "expense");
    const balance = income - expense;
    const rate = rateOf(income, expense);

    $("r-income").textContent = money(income);
    $("r-expense").textContent = money(expense);
    $("r-balance").textContent = money(balance);
    $("r-rate").textContent = rate === null ? "–" : rate + "%";

    const hasPrev = prev.length > 0;
    const pIncome = hasPrev ? sum(prev, "income") : null;
    const pExpense = hasPrev ? sum(prev, "expense") : null;
    setNote("r-income-note", income, pIncome, true);
    setNote("r-expense-note", expense, pExpense, false);        // spending going up is bad
    setNote("r-balance-note", balance, hasPrev ? pIncome - pExpense : null, true);

    const note = $("r-rate-note");
    const pRate = hasPrev ? rateOf(pIncome, pExpense) : null;
    if (rate === null || pRate === null) {
      note.textContent = "No earlier data";
      note.className = "note";
    } else {
      const diff = rate - pRate;
      note.textContent = (diff >= 0 ? "↑ " : "↓ ") + Math.abs(diff) + " pts vs last period";
      note.className = "note " + (diff >= 0 ? "good" : "bad");
    }
  }

  // ---------- trend chart ----------
  function renderTrend(rows) {
    if (trendChart) { trendChart.destroy(); trendChart = null; }
    if (typeof Chart === "undefined") return;
    trendChart = new Chart($("r-trend"), {
      type: "line",
      data: {
        labels: rows.map(function (r) { return shortMonth(r.label); }),
        datasets: [
          { label: "Income", data: rows.map(function (r) { return Number(r.income); }), borderColor: "#2f9e6b",
            backgroundColor: "rgba(47, 158, 107, 0.10)", fill: true, tension: 0.35, pointRadius: 3 },
          { label: "Expenses", data: rows.map(function (r) { return Number(r.expense); }), borderColor: "#5a8dee",
            backgroundColor: "rgba(90, 141, 238, 0.08)", fill: true, tension: 0.35, pointRadius: 3 }
        ]
      },
      options: {
        maintainAspectRatio: false,
        interaction: { mode: "index", intersect: false },
        plugins: {
          legend: { position: "top", align: "end", labels: { usePointStyle: true, boxWidth: 8 } },
          tooltip: { callbacks: { label: function (c) { return c.dataset.label + ": " + money(c.parsed.y); } } }
        },
        scales: {
          y: { beginAtZero: true, ticks: { callback: function (v) { return v >= 1000 ? v / 1000 + "k" : v; } } },
          x: { grid: { display: false } }
        }
      }
    });
  }

  // ---------- category donut (adds up the months of the period) ----------
  async function renderCategories(cur) {
    const lists = await Promise.all(cur.map(function (r) {
      const p = parseLabel(r.label);
      return get("/api/summary/by-category?month=" + p.month + "&year=" + p.year + "&type=EXPENSE");
    }));
    const totals = {};
    lists.forEach(function (list) {
      (list || []).forEach(function (c) { totals[c.category] = (totals[c.category] || 0) + Number(c.total); });
    });
    let rows = Object.keys(totals).map(function (name) { return { category: name, total: totals[name] }; })
      .sort(function (a, b) { return b.total - a.total; });
    if (rows.length > 6) {                                      // keep the donut readable
      const rest = rows.slice(5).reduce(function (s, r) { return s + r.total; }, 0);
      rows = rows.slice(0, 5).concat([{ category: "Others", total: rest }]);
    }
    const total = rows.reduce(function (s, r) { return s + r.total; }, 0);

    $("r-total").textContent = money(total);
    const legend = $("r-legend");
    legend.innerHTML = "";
    if (rows.length === 0) legend.appendChild(el("li", "legend-empty", "No spending in this period."));
    rows.forEach(function (r, i) {
      const li = el("li", "legend-item");
      const dot = el("span", "dot");
      dot.style.background = PALETTE[i % PALETTE.length];
      const text = el("div", "legend-text");
      text.appendChild(el("strong", "", r.category));
      const share = total > 0 ? Math.round((r.total / total) * 1000) / 10 : 0;
      text.appendChild(el("small", "", share + "%  " + money(r.total)));
      li.appendChild(dot);
      li.appendChild(text);
      legend.appendChild(li);
    });

    if (donut) { donut.destroy(); donut = null; }
    if (typeof Chart === "undefined" || rows.length === 0) return;
    donut = new Chart($("r-donut"), {
      type: "doughnut",
      data: {
        labels: rows.map(function (r) { return r.category; }),
        datasets: [{ data: rows.map(function (r) { return r.total; }),
                     backgroundColor: rows.map(function (_, i) { return PALETTE[i % PALETTE.length]; }), borderWidth: 0 }]
      },
      options: { cutout: "66%", maintainAspectRatio: false, plugins: { legend: { display: false } } }
    });
  }

  // ---------- monthly summary table ----------
  function renderTable(rows) {
    const body = $("r-body");
    body.innerHTML = "";
    rows.slice().reverse().forEach(function (r) {            // newest first
      const income = Number(r.income);
      const expense = Number(r.expense);
      const rate = rateOf(income, expense);
      const tr = el("tr");
      tr.appendChild(el("td", "", longLabel(r.label)));
      tr.appendChild(el("td", "r", money(income)));
      tr.appendChild(el("td", "r", money(expense)));
      tr.appendChild(el("td", "r", money(income - expense)));
      tr.appendChild(el("td", "r", rate === null ? "–" : rate + "%"));
      body.appendChild(tr);
    });
  }

  // ---------- load everything for the chosen period ----------
  async function load() {
    const fetchMonths = Math.min(24, Math.max(months * 2, 6));
    const rows = (await get("/api/summary/trend?months=" + fetchMonths)) || [];
    const cur = rows.slice(-months);
    const prev = rows.length > months ? rows.slice(-months * 2, -months) : [];

    renderCards(cur, prev);
    renderTrend(rows.slice(-Math.max(6, months)));
    renderTable(rows.slice(-Math.max(3, months)));
    await renderCategories(cur);
  }

  function safe(fn) {
    return fn().catch(function (e) { toast(e.message || "Could not load the report."); });
  }

  // ---------- period tabs ----------
  document.querySelectorAll(".tab").forEach(function (tab) {
    tab.addEventListener("click", function () {
      months = Number(tab.getAttribute("data-months"));
      document.querySelectorAll(".tab").forEach(function (t) {
        t.setAttribute("aria-pressed", String(t === tab));
      });
      safe(load);
    });
  });

  // ---------- report month + downloads ----------
  function fillPeriod() {
    const ms = $("r-sel-month");
    MONTHS.forEach(function (name, i) {
      const o = el("option", "", name);
      o.value = String(i + 1);
      ms.appendChild(o);
    });
    ms.value = String(NM);
    const ys = $("r-sel-year");
    for (let y = NY - 2; y <= NY; y++) {
      const o = el("option", "", String(y));
      o.value = String(y);
      ys.appendChild(o);
    }
    ys.value = String(NY);
    ms.addEventListener("change", updateNote);
    ys.addEventListener("change", updateNote);
    updateNote();
  }

  function updateNote() {
    $("export-note").textContent = "Report for " + MONTHS[Number($("r-sel-month").value) - 1] + " " + $("r-sel-year").value;
  }

  async function downloadReport(format) {
    const month = Number($("r-sel-month").value);
    const year = Number($("r-sel-year").value);
    if (USE_MOCK) {
      toast("Preview mode: downloads need the real backend.");
      return;
    }
    try {
      await Api.download("/api/reports/monthly?month=" + month + "&year=" + year + "&format=" + format,
                         "report-" + year + "-" + pad(month) + "." + EXT[format]);
      toast("Report downloaded.");
    } catch (e) {
      toast(e.message || "Could not download the report.");
    }
  }

  document.querySelectorAll("#export-menu [data-fmt]").forEach(function (b) {
    b.addEventListener("click", function () {
      $("export-menu").open = false;
      downloadReport(b.getAttribute("data-fmt"));
    });
  });
  $("qa-pdf").addEventListener("click", function () { downloadReport("pdf"); });
  $("qa-xlsx").addEventListener("click", function () { downloadReport("xlsx"); });
  $("qa-alerts").addEventListener("click", function (event) {
    event.stopPropagation();                                  // keeps the bell open (see Budgets fix)
    window.scrollTo({ top: 0, behavior: "smooth" });
    const bell = $("bell");
    if (bell) bell.click();
  });
  document.addEventListener("click", function (event) {      // close the export menu when clicking elsewhere
    const menu = $("export-menu");
    if (menu.open && !event.target.closest("#export-menu")) menu.open = false;
  });

  // ---------- start ----------
  fillPeriod();
  safe(load);
})();