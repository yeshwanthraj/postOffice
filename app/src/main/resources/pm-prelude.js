// Builds the Postman-style `pm` object for one script run.
// Kotlin sets globalThis.__input before this file runs and defines the __host* functions.
// Nothing here talks to Kotlin except logging and crypto: variable changes and request
// edits are collected and handed back in one go by __collect().
(function () {
  'use strict';

  var input = globalThis.__input;
  var phase = input.phase;
  var ops = [];
  var tests = [];

  // ---------- helpers ----------
  function fmt(v) {
    if (typeof v === 'string') return v;
    if (v instanceof Error) return v.name + ': ' + v.message;
    try {
      var s = JSON.stringify(v);
      return s === undefined ? String(v) : s;
    } catch (e) {
      return String(v);
    }
  }
  function show(v) {
    if (typeof v === 'string') return "'" + v + "'";
    if (v !== null && typeof v === 'object') return fmt(v);
    return String(v);
  }
  function str(v) {
    if (v === undefined || v === null) return '';
    return typeof v === 'string' ? v : fmt(v);
  }
  function deepEqual(a, b) {
    if (a === b) return true;
    if (typeof a === 'number' && typeof b === 'number') return a !== a && b !== b; // NaN
    if (a === null || b === null || typeof a !== 'object' || typeof b !== 'object') return false;
    if (Array.isArray(a) !== Array.isArray(b)) return false;
    var ka = Object.keys(a), kb = Object.keys(b);
    if (ka.length !== kb.length) return false;
    for (var i = 0; i < ka.length; i++) {
      if (!Object.prototype.hasOwnProperty.call(b, ka[i]) || !deepEqual(a[ka[i]], b[ka[i]])) return false;
    }
    return true;
  }

  // ---------- console ----------
  function logger(level) {
    return function () {
      __hostLog(level, Array.prototype.map.call(arguments, fmt).join(' '));
    };
  }
  globalThis.console = {
    log: logger('log'), info: logger('info'), debug: logger('log'),
    warn: logger('warn'), error: logger('error')
  };

  // ---------- key/value lists (headers, params, urlencoded body) ----------
  function PropertyList(items, caseInsensitive) {
    var list = (items || []).map(function (i) { return { key: String(i.key), value: str(i.value) }; });
    function same(a, b) {
      return caseInsensitive ? String(a).toLowerCase() === String(b).toLowerCase() : String(a) === String(b);
    }
    function norm(k, v) {
      return (k !== null && typeof k === 'object') ? { key: String(k.key), value: str(k.value) } : { key: String(k), value: str(v) };
    }
    return {
      get: function (k) {
        for (var i = 0; i < list.length; i++) if (same(list[i].key, k)) return list[i].value;
        return undefined;
      },
      has: function (k) { return this.get(k) !== undefined; },
      add: function (k, v) { list.push(norm(k, v)); },
      upsert: function (k, v) {
        var item = norm(k, v);
        for (var i = 0; i < list.length; i++) if (same(list[i].key, item.key)) { list[i].value = item.value; return; }
        list.push(item);
      },
      remove: function (k) {
        for (var i = list.length - 1; i >= 0; i--) if (same(list[i].key, k)) list.splice(i, 1);
      },
      clear: function () { list.length = 0; },
      count: function () { return list.length; },
      each: function (fn) { list.forEach(function (i) { fn({ key: i.key, value: i.value }); }); },
      all: function () { return list.map(function (i) { return { key: i.key, value: i.value }; }); },
      toObject: function () {
        var o = {};
        list.forEach(function (i) { o[i.key] = i.value; });
        return o;
      }
    };
  }

  // ---------- variables ----------
  var env = Object.assign({}, input.environment);
  var globals = Object.assign({}, input.globals);
  var locals = Object.assign({}, input.locals);

  function resolve(text) {
    var s = str(text);
    for (var depth = 0; depth < 5; depth++) {
      var next = s.replace(/\{\{\s*([^{}]+?)\s*\}\}/g, function (m, name) {
        var v = lookup(name);
        return v === undefined ? m : v;
      });
      if (next === s) break;
      s = next;
    }
    return s;
  }
  function lookup(name) {
    if (Object.prototype.hasOwnProperty.call(locals, name)) return locals[name];
    if (Object.prototype.hasOwnProperty.call(env, name)) return env[name];
    if (Object.prototype.hasOwnProperty.call(globals, name)) return globals[name];
    return undefined;
  }

  function Scope(scopeName, data, writable, label) {
    function denied(action, key) {
      console.warn('No environment is selected, so pm.environment.' + action + "('" + key + "') was ignored.");
    }
    var self = {
      get: function (k) { return data[k]; },
      has: function (k) { return Object.prototype.hasOwnProperty.call(data, k); },
      set: function (k, v) {
        k = String(k);
        if (!writable) return denied('set', k);
        data[k] = str(v);
        ops.push({ scope: scopeName, op: 'set', key: k, value: data[k] });
      },
      unset: function (k) {
        k = String(k);
        if (!writable) return denied('unset', k);
        delete data[k];
        ops.push({ scope: scopeName, op: 'unset', key: k });
      },
      clear: function () { Object.keys(data).forEach(function (k) { self.unset(k); }); },
      toObject: function () { return Object.assign({}, data); },
      replaceIn: resolve
    };
    if (label !== undefined) self.name = label;
    return self;
  }

  // ---------- request ----------
  var r = input.request;
  var request = {
    method: r.method,
    url: r.url,
    params: PropertyList(r.params, false),
    headers: PropertyList(r.headers, true),
    body: { mode: r.body.mode, raw: r.body.raw, urlencoded: PropertyList(r.body.urlencoded, false) }
  };

  // ---------- assertions (a subset of chai's expect) ----------
  function AssertionError(message) {
    this.name = 'AssertionError';
    this.message = message;
  }
  AssertionError.prototype = Object.create(Error.prototype);
  AssertionError.prototype.constructor = AssertionError;

  function fail(message) { throw new AssertionError(message); }

  function Assertion(actual, message) {
    this._actual = actual;
    this._negate = false;
    this._deep = false;
    this._message = message;
  }
  var A = Assertion.prototype;
  ['to', 'be', 'been', 'is', 'that', 'which', 'and', 'has', 'have', 'with', 'at', 'of', 'same', 'does', 'but', 'still']
    .forEach(function (word) { Object.defineProperty(A, word, { get: function () { return this; } }); });
  Object.defineProperty(A, 'not', { get: function () { this._negate = !this._negate; return this; } });
  Object.defineProperty(A, 'deep', { get: function () { this._deep = true; return this; } });

  A._check = function (ok, what) {
    if (this._negate ? ok : !ok) {
      var text = 'expected ' + show(this._actual) + (this._negate ? ' to not ' : ' to ') + what;
      fail(this._message ? this._message + ': ' + text : text);
    }
    return this;
  };
  function getter(names, fn) {
    names.forEach(function (n) { Object.defineProperty(A, n, { get: fn }); });
  }
  function method(names, fn) {
    names.forEach(function (n) { A[n] = fn; });
  }

  getter(['ok'], function () { return this._check(!!this._actual, 'be truthy'); });
  getter(['true'], function () { return this._check(this._actual === true, 'be true'); });
  getter(['false'], function () { return this._check(this._actual === false, 'be false'); });
  getter(['null'], function () { return this._check(this._actual === null, 'be null'); });
  getter(['undefined'], function () { return this._check(this._actual === undefined, 'be undefined'); });
  getter(['NaN'], function () { return this._check(this._actual !== this._actual, 'be NaN'); });
  getter(['exist'], function () { return this._check(this._actual !== null && this._actual !== undefined, 'exist'); });
  getter(['empty'], function () {
    var a = this._actual;
    var empty = (typeof a === 'string' || Array.isArray(a)) ? a.length === 0
      : (a !== null && typeof a === 'object') ? Object.keys(a).length === 0 : false;
    return this._check(empty, 'be empty');
  });

  method(['equal', 'equals', 'eq'], function (v) {
    return this._check(this._deep ? deepEqual(this._actual, v) : this._actual === v, (this._deep ? 'deeply ' : '') + 'equal ' + show(v));
  });
  method(['eql', 'eqls'], function (v) { return this._check(deepEqual(this._actual, v), 'deeply equal ' + show(v)); });
  method(['above', 'gt', 'greaterThan'], function (n) { return this._check(this._actual > n, 'be above ' + n); });
  method(['least', 'gte'], function (n) { return this._check(this._actual >= n, 'be at least ' + n); });
  method(['below', 'lt', 'lessThan'], function (n) { return this._check(this._actual < n, 'be below ' + n); });
  method(['most', 'lte'], function (n) { return this._check(this._actual <= n, 'be at most ' + n); });
  method(['within'], function (lo, hi) {
    return this._check(this._actual >= lo && this._actual <= hi, 'be within ' + lo + '..' + hi);
  });
  method(['a', 'an'], function (type) {
    var a = this._actual, t = String(type).toLowerCase();
    var actualType = a === null ? 'null' : Array.isArray(a) ? 'array' : typeof a;
    return this._check(actualType === t, 'be a' + (/^[aeiou]/.test(t) ? 'n ' : ' ') + t);
  });
  method(['include', 'includes', 'contain', 'contains'], function (v) {
    var a = this._actual, ok = false;
    if (typeof a === 'string') ok = a.indexOf(v) >= 0;
    else if (Array.isArray(a)) ok = a.some(function (x) { return deepEqual(x, v); });
    else if (a !== null && typeof a === 'object' && v !== null && typeof v === 'object') {
      ok = Object.keys(v).every(function (k) { return deepEqual(a[k], v[k]); });
    }
    return this._check(ok, 'include ' + show(v));
  });
  method(['property'], function (name, value) {
    var a = this._actual;
    var has = a !== null && a !== undefined && (typeof a === 'object' || typeof a === 'function') && name in a;
    if (arguments.length > 1) {
      this._check(has && (this._deep ? deepEqual(a[name], value) : a[name] === value),
        'have property ' + show(name) + ' of ' + show(value));
    } else {
      this._check(has, 'have property ' + show(name));
    }
    if (!this._negate && has) return new Assertion(a[name], this._message);
    return this;
  });
  method(['lengthOf'], function (n) {
    var len = this._actual === null || this._actual === undefined ? undefined : this._actual.length;
    return this._check(len === n, 'have length ' + n + ' (got ' + len + ')');
  });
  method(['match', 'matches'], function (re) { return this._check(re.test(String(this._actual)), 'match ' + re); });
  method(['oneOf'], function (list) {
    var a = this._actual;
    return this._check(list.some(function (x) { return deepEqual(x, a); }), 'be one of ' + show(list));
  });
  method(['keys'], function () {
    var wanted = Array.isArray(arguments[0]) ? arguments[0] : Array.prototype.slice.call(arguments);
    var a = this._actual || {};
    return this._check(wanted.every(function (k) { return Object.prototype.hasOwnProperty.call(a, k); }), 'have keys ' + show(wanted));
  });

  function expect(actual, message) { return new Assertion(actual, message); }

  // ---------- response ----------
  var response;
  if (input.response) {
    var res = input.response, parsed, parsedOk = false;
    var assertion = function (ok, message) { if (!ok) fail(message); };
    response = {
      code: res.code,
      status: res.status,
      responseTime: res.responseTime,
      responseSize: res.size,
      headers: PropertyList(res.headers, true),
      text: function () { return res.body; },
      json: function () {
        if (!parsedOk) { parsed = JSON.parse(res.body); parsedOk = true; }
        return parsed;
      }
    };
    response.to = {
      have: {
        status: function (expected) {
          if (typeof expected === 'number') {
            assertion(res.code === expected, 'expected response to have status code ' + expected + ' but got ' + res.code);
          } else {
            assertion(res.status === expected, "expected response to have status reason '" + expected + "' but got '" + res.status + "'");
          }
        },
        header: function (name, value) {
          var actual = response.headers.get(name);
          assertion(actual !== undefined, "expected response to have header '" + name + "'");
          if (arguments.length > 1) {
            assertion(actual === value, "expected header '" + name + "' to be '" + value + "' but got '" + actual + "'");
          }
        },
        body: function (value) {
          assertion(res.body === value, 'expected response body to equal ' + show(value));
        },
        jsonBody: function (path) {
          var body;
          try { body = response.json(); } catch (e) { fail('expected response to have a JSON body'); }
          if (path !== undefined) assertion(body !== null && typeof body === 'object' && path in body, "expected JSON body to have property '" + path + "'");
        }
      },
      be: {}
    };
    var statusChecks = {
      ok: [function (c) { return c >= 200 && c < 300; }, 'be ok (2xx)'],
      success: [function (c) { return c >= 200 && c < 300; }, 'be successful (2xx)'],
      redirection: [function (c) { return c >= 300 && c < 400; }, 'be a redirection (3xx)'],
      clientError: [function (c) { return c >= 400 && c < 500; }, 'be a client error (4xx)'],
      serverError: [function (c) { return c >= 500; }, 'be a server error (5xx)'],
      error: [function (c) { return c >= 400; }, 'be an error (4xx/5xx)'],
      notFound: [function (c) { return c === 404; }, 'be not found (404)'],
      unauthorized: [function (c) { return c === 401; }, 'be unauthorized (401)'],
      forbidden: [function (c) { return c === 403; }, 'be forbidden (403)']
    };
    Object.keys(statusChecks).forEach(function (k) {
      Object.defineProperty(response.to.be, k, {
        get: function () {
          assertion(statusChecks[k][0](res.code), 'expected response to ' + statusChecks[k][1] + ' but got ' + res.code);
          return true;
        }
      });
    });
  }

  // ---------- pm ----------
  function test(name, fn) {
    try {
      fn();
      tests.push({ name: String(name), status: 'PASS' });
    } catch (e) {
      var msg = e && e.name === 'AssertionError' ? e.message : fmt(e);
      tests.push({ name: String(name), status: 'FAIL', error: msg });
    }
  }
  test.skip = function (name) { tests.push({ name: String(name), status: 'SKIP' }); };

  var hasEnv = input.environmentName !== null && input.environmentName !== undefined;

  globalThis.pm = {
    info: { eventName: phase === 'pre' ? 'prerequest' : 'test' },
    environment: Scope('ENVIRONMENT', env, hasEnv, hasEnv ? input.environmentName : undefined),
    globals: Scope('GLOBALS', globals, true),
    variables: {
      get: function (k) { return lookup(k); },
      has: function (k) { return lookup(k) !== undefined; },
      set: function (k, v) { locals[String(k)] = str(v); },
      unset: function (k) { delete locals[String(k)]; },
      toObject: function () { return Object.assign({}, globals, env, locals); },
      replaceIn: resolve
    },
    request: request,
    response: response,
    test: test,
    expect: expect,
    uuid: function () { return __hostUuid(); },
    crypto: {
      sha256: function (s) { return __hostCrypto('sha256', str(s), ''); },
      sha1: function (s) { return __hostCrypto('sha1', str(s), ''); },
      md5: function (s) { return __hostCrypto('md5', str(s), ''); },
      hmacSha256: function (key, s) { return __hostCrypto('hmacSha256', str(s), str(key)); },
      hmacSha1: function (key, s) { return __hostCrypto('hmacSha1', str(s), str(key)); },
      base64Encode: function (s) { return __hostCrypto('base64Encode', str(s), ''); },
      base64Decode: function (s) { return __hostCrypto('base64Decode', str(s), ''); }
    }
  };
  globalThis.btoa = pm.crypto.base64Encode;
  globalThis.atob = pm.crypto.base64Decode;

  globalThis.__collect = function () {
    var out = { ops: ops, locals: locals, tests: tests, request: null };
    if (phase === 'pre') {
      out.request = {
        method: String(request.method),
        url: str(request.url),
        params: request.params.all(),
        headers: request.headers.all(),
        body: {
          mode: String(request.body.mode),
          raw: str(request.body.raw),
          urlencoded: request.body.urlencoded.all()
        }
      };
    }
    return JSON.stringify(out);
  };
})();
