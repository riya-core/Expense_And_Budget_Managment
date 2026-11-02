/*
 * /js/auth-ui.js  -  small look-and-feel helpers used by BOTH login.html and register.html.
 * Nothing in here talks to the backend. (Backend calls live only in api.js.)
 *
 *   AuthUI.initScene()              paints the dotted landscape and returns { name, hour }
 *   AuthUI.setupPasswordToggles()   makes every "Show / Hide" button work
 *   AuthUI.showMessage(...)         shows the message box at the top of the form
 *   AuthUI.setFieldError(...)       shows a red hint under one input
 *   AuthUI.clearErrors(...)         removes all hints and the message
 *   AuthUI.showApiError(...)        shows an ApiError (its message and any fieldErrors)
 *   AuthUI.setBusy(...)             disables the button while a request is running
 */
(function () {
  "use strict";

  // ================= A. THE PAINTING (changes with the time of day) =================
  // To preview one scene, add ?scene=morning, ?scene=evening or ?scene=night to the page address.
  const SCENES = {
    morning: { sky: [[120, 160, 230], [190, 180, 240], [255, 205, 170]], sun: { x: 0.3, y: 0.4, r: 0.12, inner: [255, 244, 214], outer: [255, 196, 110] },
               hills: [[[70, 90, 170], [110, 130, 210]], [[30, 120, 90], [90, 170, 110]]], grass: [255, 200, 120], stars: false },
    evening: { sky: [[24, 24, 90], [110, 96, 230], [240, 170, 90]], sun: { x: 0.68, y: 0.4, r: 0.13, inner: [255, 236, 190], outer: [242, 169, 59] },
               hills: [[[44, 44, 140], [78, 74, 190]], [[16, 92, 92], [40, 140, 120]]], grass: [242, 169, 59], stars: false },
    night:   { sky: [[8, 10, 40], [30, 32, 90], [70, 60, 140]], sun: { x: 0.7, y: 0.24, r: 0.075, inner: [250, 247, 225], outer: [205, 205, 190] },
               hills: [[[18, 20, 70], [36, 40, 110]], [[10, 50, 60], [24, 90, 90]]], grass: [200, 215, 160], stars: true }
  };

  function random(seed) {                      // same seed = same painting every time
    return function () {
      seed |= 0; seed = (seed + 0x6D2B79F5) | 0;
      let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
      t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  }
  const mix = (a, b, t) => a.map((v, i) => v + (b[i] - v) * t);

  function paint(canvas, W, H, dots, seed, scene) {
    canvas.width = W; canvas.height = H;
    const g = canvas.getContext("2d");
    const rand = random(seed), size = W / 520;
    g.fillStyle = "rgb(" + scene.sky[0].join(",") + ")";
    g.fillRect(0, 0, W, H);

    const ridge = [
      (x) => H * (0.56 + 0.05 * Math.sin((x / W) * 3 + 0.8)),   // far hill
      (x) => H * (0.72 + 0.04 * Math.sin((x / W) * 4 + 2))      // near hill
    ];
    const sun = { x: W * scene.sun.x, y: H * scene.sun.y, r: W * scene.sun.r };

    for (let i = 0; i < dots; i++) {
      const x = rand() * W, y = rand() * H;
      let color, radius = (0.8 + rand() * 1.5) * size;

      if (y > ridge[1](x)) {
        color = mix(scene.hills[1][0], scene.hills[1][1], rand());
        if (rand() < 0.03) { color = scene.grass; radius = (2.2 + rand()) * size; }   // little amber specks in the grass
      } else if (y > ridge[0](x)) {
        color = mix(scene.hills[0][0], scene.hills[0][1], rand());
      } else {
        const f = y / (H * 0.6);
        color = f < 0.6 ? mix(scene.sky[0], scene.sky[1], f / 0.6) : mix(scene.sky[1], scene.sky[2], Math.min((f - 0.6) / 0.4, 1));
        const d = Math.hypot(x - sun.x, y - sun.y);
        if (d < sun.r) color = mix(scene.sun.inner, scene.sun.outer, d / sun.r);
        else if (scene.stars && rand() < 0.004) { color = [255, 255, 255]; radius *= 1.5; }
      }

      const shade = 0.85 + rand() * 0.3;
      g.fillStyle = "rgb(" + color.map((v) => Math.min(255, v * shade)).join(",") + ")";
      g.beginPath(); g.arc(x, y, radius, 0, Math.PI * 2); g.fill();
    }
  }

  function initScene() {
    const hour = new Date().getHours();
    let name = hour >= 5 && hour < 11 ? "morning" : hour >= 11 && hour < 19 ? "evening" : "night";
    const forced = new URLSearchParams(window.location.search).get("scene");
    if (forced && Object.prototype.hasOwnProperty.call(SCENES, forced)) name = forced;

    const painting = document.getElementById("painting");
    const backdrop = document.getElementById("backdrop");
    if (painting) paint(painting, 520, 700, 80000, 7, SCENES[name]);
    if (backdrop) paint(backdrop, 360, 240, 45000, 7, SCENES[name]);   // small version, stretched and blurred by CSS
    return { name: name, hour: hour };
  }

  // ================= B. PASSWORD "SHOW / HIDE" BUTTONS =================
  // In the HTML: <button data-toggle-password="password">Show</button> controls the input with id="password".
  function setupPasswordToggles() {
    document.querySelectorAll("[data-toggle-password]").forEach(function (button) {
      const input = document.getElementById(button.dataset.togglePassword);
      if (!input) return;
      button.addEventListener("click", function () {
        const reveal = input.type === "password";
        input.type = reveal ? "text" : "password";
        button.textContent = reveal ? "Hide" : "Show";
        button.setAttribute("aria-pressed", String(reveal));
      });
    });
  }

  // ================= C. MESSAGES AND FIELD ERRORS =================
  function showMessage(box, text, kind) {            // kind: "error" | "success" | "info"
    box.textContent = text;
    box.className = "message " + kind;
    box.hidden = false;
  }

  function setFieldError(input, text) {
    const hint = document.getElementById(input.id + "-error");
    if (hint) { hint.textContent = text; hint.hidden = false; }
    input.setAttribute("aria-invalid", "true");
  }

  function clearErrors(form, box) {
    form.querySelectorAll(".field-error").forEach(function (hint) { hint.hidden = true; hint.textContent = ""; });
    form.querySelectorAll("[aria-invalid]").forEach(function (input) { input.removeAttribute("aria-invalid"); });
    box.hidden = true;
  }

  // Shows an error thrown by Api.* . The backend's `message` goes in the box (contract section 7);
  // each entry of `fieldErrors` goes under the matching input (inputs use name="email", name="password", ...).
  function showApiError(form, box, err) {
    const leftOver = [];
    (err.fieldErrors || []).forEach(function (item) {
      const input = form.elements[item.field];
      if (input && input.id && document.getElementById(input.id + "-error")) setFieldError(input, item.message);
      else leftOver.push(item.field + ": " + item.message);
    });
    let text = err.message || "Something went wrong. Please try again.";
    if (leftOver.length) text += " (" + leftOver.join("; ") + ")";
    showMessage(box, text, "error");
  }

  function setBusy(button, busy, busyText, idleText) {
    button.disabled = busy;
    button.textContent = busy ? busyText : idleText;
  }

  window.AuthUI = {
    initScene: initScene,
    setupPasswordToggles: setupPasswordToggles,
    showMessage: showMessage,
    setFieldError: setFieldError,
    clearErrors: clearErrors,
    showApiError: showApiError,
    setBusy: setBusy
  };
})();
