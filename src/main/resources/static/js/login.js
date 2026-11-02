/*
 * /js/login.js  -  the login page.
 * Needs (loaded before it): api.js and auth-ui.js.
 *
 * Contract section 6.1:
 *   POST /api/auth/login   request  { email, password }
 *                          response { token, tokenType: "Bearer", expiresIn: 3600, user: { id, name, email } }
 *
 * This file contains NO fetch(). The only way it reaches the backend is Api.post(...).
 * There is no fake login: if the backend is not running, the page shows an honest error.
 */
(function () {
  "use strict";

  const EMAIL_PATTERN = /^\S+@\S+\.\S+$/;     // a simple "looks like an email" check; the backend does the real validation

  const scene = AuthUI.initScene();
  AuthUI.setupPasswordToggles();

  // A greeting that matches the time of day
  const hello = {
    morning: "Good morning. Welcome back.",
    evening: scene.hour < 17 ? "Good afternoon. Welcome back." : "Good evening. Welcome back.",
    night: "Up late? Welcome back."
  };
  document.getElementById("greeting").textContent = hello[scene.name];

  const form = document.getElementById("login-form");
  const emailInput = document.getElementById("email");
  const passwordInput = document.getElementById("password");
  const submitButton = document.getElementById("submit");
  const messageBox = document.getElementById("message");

  form.addEventListener("submit", async function (event) {
    event.preventDefault();
    AuthUI.clearErrors(form, messageBox);

    const email = emailInput.value.trim();
    const password = passwordInput.value;       // passwords are never trimmed

    // ---- 1. Check the form before calling the backend ----
    let firstInvalid = null;
    if (!email) {
      AuthUI.setFieldError(emailInput, "Enter your email address.");
      firstInvalid = firstInvalid || emailInput;
    } else if (!EMAIL_PATTERN.test(email)) {
      AuthUI.setFieldError(emailInput, "That doesn't look like an email address. Example: you@example.com");
      firstInvalid = firstInvalid || emailInput;
    }
    if (!password) {
      AuthUI.setFieldError(passwordInput, "Enter your password.");
      firstInvalid = firstInvalid || passwordInput;
    }
    if (firstInvalid) { firstInvalid.focus(); return; }

    // ---- 2. Call the backend through the shared helper ----
    AuthUI.setBusy(submitButton, true, "Logging in...", "Log in");
    try {
      const data = await Api.post("/api/auth/login", { email: email, password: password }, { auth: false });

      // Only a real, complete login reply is saved (keys et_token and et_user are owned by api.js).
      Api.saveSession(data);
      window.location.href = "/dashboard";
    } catch (err) {
      AuthUI.showApiError(form, messageBox, err);
      AuthUI.setBusy(submitButton, false, "Logging in...", "Log in");
    }
  });
})();
