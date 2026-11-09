/*
 * /js/api.js  -  the ONE shared helper for talking to the backend (TEAM_CONTRACT section 9).
 *
 * Every page calls the backend through this file, never with its own fetch().
 * It does these jobs:
 *   1. Adds "Authorization: Bearer <token>" to protected requests.
 *   2. Reads the standard error JSON (section 7) and turns it into an ApiError with a `message`.
 *   3. On a 401 from a protected request: clears the saved login and goes to /login.
 *   4. Handles empty replies (204 No Content) safely.
 *
 * How to use it from a page script:
 *   const user = await Api.post("/api/auth/login", { email, password }, { auth: false });
 *   const page = await Api.get("/api/transactions?page=0&size=10");
 *
 * Paths are written in full, exactly as in the contract (starting with /api).
 * If a teammate needs something new (for example a file download for reports), add it HERE.
 */
(function () {
  "use strict";

  // Storage keys are fixed by contract section 9.
  const TOKEN_KEY = "et_token";
  const USER_KEY = "et_user";
  const LOGIN_PATH = "/login";

  // ---- An error that carries what the backend told us (contract section 7) ----
  class ApiError extends Error {
    constructor(message, status, details) {
      super(message);
      this.name = "ApiError";
      this.status = status;                                           // 0 means "could not reach the server"
      this.fieldErrors = (details && details.fieldErrors) || [];      // [{ field, message }], only for validation errors
      this.details = details || null;                                 // the full error JSON, if there was one
    }
  }

  // ---- localStorage, wrapped so a blocked/full storage never crashes the page ----
  function getToken() {
    try { return localStorage.getItem(TOKEN_KEY); } catch (e) { return null; }
  }
  function getUser() {
    try { return JSON.parse(localStorage.getItem(USER_KEY)); } catch (e) { return null; }
  }
  function clearSession() {
    try { localStorage.removeItem(TOKEN_KEY); localStorage.removeItem(USER_KEY); } catch (e) { /* ignore */ }
  }
  // Call this ONLY with a real login response: { token, tokenType, expiresIn, user: { id, name, email } }
  function saveSession(loginResponse) {
    if (!loginResponse || !loginResponse.token || !loginResponse.user) {
      throw new Error("The server's login reply was missing the token or user.");
    }
    localStorage.setItem(TOKEN_KEY, loginResponse.token);
    localStorage.setItem(USER_KEY, JSON.stringify(loginResponse.user));
  }

  // A friendly sentence for when the backend did not send a `message`
  function fallbackMessage(status) {
    if (status === 400) return "Please check what you entered and try again.";
    if (status === 401) return "Please log in again.";
    if (status === 404) return "We couldn't find that.";
    if (status === 409) return "That already exists.";
    if (status >= 500) return "Something went wrong on the server. Please try again in a moment.";
    return "Something went wrong. Please try again.";
  }

  // Reads the reply body safely. Returns parsed JSON, or null if the body is empty or not JSON.
  async function readBody(response) {
    if (response.status === 204) return null;
    const text = await response.text();
    if (!text) return null;
    try { return JSON.parse(text); } catch (e) { return null; }
  }

  /*
   * request(method, path, options)
   *   options.body  : a JavaScript object, sent as JSON
   *   options.auth  : true (default) = protected endpoint, send the token and redirect on 401
   *                   false          = public endpoint (login, register): no token, no redirect
   * Returns the parsed JSON (or null for an empty reply). Throws ApiError on any failure.
   */
  async function request(method, path, options) {
    const opts = options || {};
    const auth = opts.auth !== false;

    const headers = { Accept: "application/json" };
    if (opts.body !== undefined) headers["Content-Type"] = "application/json";
    if (auth) {
      const token = getToken();
      if (token) headers.Authorization = "Bearer " + token;
    }

    let response;
    try {
      response = await fetch(path, {
        method: method,
        headers: headers,
        body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined
      });
    } catch (networkError) {
      throw new ApiError("We couldn't reach the server. Check your connection and try again.", 0, null);
    }

    const data = await readBody(response);
    if (response.ok) return data;

    // Contract section 7: 401 = missing/invalid token, so go back to the login page.
    // (Public calls such as login are skipped, otherwise a wrong password would reload the login page.)
    if (response.status === 401 && auth) {
      clearSession();
      if (window.location.pathname !== LOGIN_PATH) window.location.assign(LOGIN_PATH);
    }
    throw new ApiError((data && data.message) || fallbackMessage(response.status), response.status, data);
  }

  window.Api = {
    request: request,
    get: function (path, options) { return request("GET", path, options); },
    post: function (path, body, options) { return request("POST", path, Object.assign({}, options, { body: body })); },
    put: function (path, body, options) { return request("PUT", path, Object.assign({}, options, { body: body })); },
    del: function (path, options) { return request("DELETE", path, options); },
    getToken: getToken,
    getUser: getUser,
    saveSession: saveSession,
    clearSession: clearSession,
    ApiError: ApiError,
    TOKEN_KEY: TOKEN_KEY,
    USER_KEY: USER_KEY
  };
})();
