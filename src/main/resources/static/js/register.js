/*
 * /js/register.js  -  the registration page.
 * Needs (loaded before it): api.js and auth-ui.js.
 *
 * Contract section 6.1:
 *   POST /api/auth/register   request  { name, email, password }
 *                             response 201 { id, name, email }
 *
 * Only name, email and password are sent. "Confirm password" is checked here in the browser
 * and is never sent to the backend. There is no automatic login: after success the user is
 * asked to log in (the contract does not define auto-login).
 */
(function () {
  "use strict";

  const EMAIL_PATTERN = /^\S+@\S+\.\S+$/;     // a simple "looks like an email" check; the backend does the real validation

  // The password rules are NOT in the contract. Leave this at 0 until P1 tells us the rule
  // (for example 8), then change the number here. Until then the backend's own message is shown.
  const MIN_PASSWORD_LENGTH = 0;

  AuthUI.initScene();
  AuthUI.setupPasswordToggles();

  const form = document.getElementById("register-form");
  const nameInput = document.getElementById("name");
  const emailInput = document.getElementById("email");
  const passwordInput = document.getElementById("password");
  const confirmInput = document.getElementById("confirmPassword");
  const submitButton = document.getElementById("submit");
  const messageBox = document.getElementById("message");
  const successBox = document.getElementById("success");
  const successText = document.getElementById("success-text");
  const goToLogin = document.getElementById("go-login");

  form.addEventListener("submit", async function (event) {
    event.preventDefault();
    AuthUI.clearErrors(form, messageBox);

    const name = nameInput.value.trim();
    const email = emailInput.value.trim();
    const password = passwordInput.value;       // passwords are never trimmed
    const confirm = confirmInput.value;

    // ---- 1. Check the form before calling the backend ----
    let firstInvalid = null;
    function fail(input, text) {
      AuthUI.setFieldError(input, text);
      firstInvalid = firstInvalid || input;
    }

    if (!name) fail(nameInput, "Enter your name.");
    else if (name.length > 100) fail(nameInput, "Your name can be at most 100 characters.");     // users.name is VARCHAR(100)

    if (!email) fail(emailInput, "Enter your email address.");
    else if (!EMAIL_PATTERN.test(email)) fail(emailInput, "That doesn't look like an email address. Example: you@example.com");
    else if (email.length > 150) fail(emailInput, "Your email can be at most 150 characters.");  // users.email is VARCHAR(150)

    if (!password) fail(passwordInput, "Choose a password.");
    else if (MIN_PASSWORD_LENGTH && password.length < MIN_PASSWORD_LENGTH) fail(passwordInput, "Use at least " + MIN_PASSWORD_LENGTH + " characters.");

    if (!confirm) fail(confirmInput, "Type your password again to confirm it.");
    else if (password && confirm !== password) fail(confirmInput, "The two passwords don't match.");

    if (firstInvalid) { firstInvalid.focus(); return; }

    // ---- 2. Call the backend through the shared helper (only the three contract fields) ----
    AuthUI.setBusy(submitButton, true, "Creating account...", "Create account");
    try {
      const created = await Api.post("/api/auth/register", { name: name, email: email, password: password }, { auth: false });

      // Success: clear the passwords, hide the form and point to the login page.
      const shownName = (created && created.name) || name;
      form.reset();
      form.hidden = true;
      messageBox.hidden = true;
      successText.textContent = "Welcome, " + shownName + "! Your account is ready. Log in to start tracking.";
      successBox.hidden = false;
      goToLogin.focus();
    } catch (err) {
      AuthUI.showApiError(form, messageBox, err);       // e.g. a 409 "email already registered" message from the backend
      AuthUI.setBusy(submitButton, false, "Creating account...", "Create account");
    }
  });
})();
