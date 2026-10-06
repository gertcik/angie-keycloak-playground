// njs: Basic -> JWT (Keycloak password grant) with in-memory cache.
// Used via auth_request (location /_jwt) + js_set $auth_token (proxy header).

function enc(s) {
  return s.replace(/[^A-Za-z0-9\-._~]/g, function (c) {
    return '%' + c.charCodeAt(0).toString(16).toUpperCase();
  });
}

function userFrom(auth) {
  try {
    var cred = Buffer.from(auth.substring(6), 'base64').toString('utf8');
    var idx = cred.indexOf(':');
    return idx < 0 ? null : cred.substring(0, idx);
  } catch (e) { return null; }
}

// Возвращает epoch-seconds срока действия JWT (без проверки подписи) или 0.
function expires(tok) {
  try {
    var p = tok.split('.');
    if (p.length < 2) { return 0; }
    var seg = p[1].replace(/-/g, '+').replace(/_/g, '/');
    while (seg.length % 4) { seg += '='; }
    return JSON.parse(Buffer.from(seg, 'base64').toString('utf8')).exp || 0;
  } catch (e) { return 0; }
}

// Deterministic password hash for cached credential verification.
// Uses njs crypto (SHA-256) when available, otherwise FNV-1a 32-bit fallback.
function hash(s) {
  try {
    return require('crypto').createHash('sha256').update(s, 'utf8').digest('hex');
  } catch (e) {
    var h = 0x811c9dc5;
    for (var i = 0; i < s.length; i++) {
      h = ((h ^ s.charCodeAt(i)) * 0x01000193) >>> 0;
    }
    return h.toString(36);
  }
}

// auth_request subrequest handler: 200 = allowed, 401 = denied.
function handle(r) {
  var auth = r.headersIn['Authorization'] || '';
  if (auth.indexOf('Bearer ') === 0) { r.return(200, 'ok'); return; }
  if (auth.indexOf('Basic ') !== 0) { r.return(401, 'unauthorized'); return; }

  var cred = null;
  try { cred = Buffer.from(auth.substring(6), 'base64').toString('utf8'); }
  catch (e) { r.return(401, 'bad base64'); return; }
  var idx = cred.indexOf(':');
  if (idx < 0) { r.return(401, 'bad format'); return; }

  var user = cred.substring(0, idx);
  var pass = cred.substring(idx + 1);
  var passHash = hash(pass);

  var cached = ngx.shared.jwt_cache.get(user);
  if (cached) {
    var bar = cached.indexOf('|');
    var storedHash = bar < 0 ? null : cached.substring(bar + 1);
    if (storedHash !== null && storedHash !== passHash) {
      r.error('jwt: wrong password for cached user ' + user);
      r.return(401, 'auth failed');
      return;
    }
    r.return(200, 'ok');
    return;
  }

  var raw = 'grant_type=password&client_id=bank-web&username=' + enc(user) +
            '&password=' + enc(pass);
  r.subrequest('/_kc', {
    method: 'POST',
    body: raw,
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' }
  }).then(function (res) {
    if (res.status !== 200) {
      r.error('jwt: keycloak rejected ' + user + ' (HTTP ' + res.status + ')');
      r.return(401, 'auth failed');
      return;
    }
    try {
      var tok = JSON.parse(res.responseText).access_token;
      // NB: НЕ передаём третий аргумент timeout в set() - в этой сборке Angie
      // он трактуется как миллисекунды (проверено: set(...,270) живёт ~270 мс).
      // TTL задаётся таймаутом самой зоны jwt_cache в angie.conf (270 с).
      try { ngx.shared.jwt_cache.set(user, tok + '|' + passHash); } catch (e) {}
      r.return(200, 'ok');
    } catch (e) {
      r.error('jwt: token parse failed: ' + e.message);
      r.return(500, 'no token');
    }
  }, function (e) {
    r.error('jwt: keycloak subrequest failed: ' + e.message);
    r.return(500, 'keycloak unreachable');
  });
}

// js_set variable: full Authorization value for the upstream proxy header.
function lookup(r) {
  var auth = r.headersIn['Authorization'] || '';
  if (auth.indexOf('Bearer ') === 0) { return auth; }
  if (auth.indexOf('Basic ') === 0) {
    var user = userFrom(auth);
    if (user) {
      var t = ngx.shared.jwt_cache.get(user);
      if (t) {
        var bar = t.indexOf('|');
        var tok = bar < 0 ? t : t.substring(0, bar);
        // NB: записи, созданные из auth_request-subrequest (handle()), в этой
        // сборке Angie живут лишь ~0,3 c. lookup() исполняется в контексте
        // ОСНОВНОГО запроса -> именно здесь запись переписывается, чтобы жить
        // по таймауту зоны (jwt_cache, 270 с). Пока токену осталось >60 c —
        // продлеваем; на исходе срока гасим запись, чтобы следующий Basic-
        // запрос перевыпустил свежий токен (иначе refresh-on-use служил бы
        // протухший). Передавать 3-й аргумент в set() (timeout) нельзя:
        // в этой сборке он трактуется как миллисекунды.
        var remains = expires(tok) - Math.floor(Date.now() / 1000);
        if (remains > 60) {
          try { ngx.shared.jwt_cache.set(user, t); } catch (e) {}
        } else if (remains > 0) {
          try { ngx.shared.jwt_cache.set(user, ''); } catch (e) {}
        }
        return 'Bearer ' + tok;
      }
    }
  }
  return '';
}

// Debug endpoint: сколько токенов в jwt_cache и для каких пользователей.
// Наружу отдаются только логины и наличие токена (сами токены не разглашаются).
function stats(r) {
  var keys = ngx.shared.jwt_cache.keys(1000);
  var users = [];
  for (var i = 0; i < keys.length; i++) {
    var v = ngx.shared.jwt_cache.get(keys[i]);
    var bar = v ? v.indexOf('|') : -1;
    users.push({ user: keys[i], token: bar > 0 });
  }
  r.headersOut['Content-Type'] = 'application/json; charset=utf-8';
  r.return(200, JSON.stringify({ total: keys.length, users: users }));
}

export default { handle: handle, lookup: lookup, stats: stats };