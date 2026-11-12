// FyoCord support relay: a free Cloudflare Worker.
//
// What it does
//   * keeps the Discord webhook URL secret (the app never contains it),
//   * mints support codes that stop working after a chosen time,
//   * receives a bug report (text + log file) from the app and forwards it to your Discord webhook.
//
// Secrets (Worker > Settings > Variables and Secrets, type "Secret"):
//   SECRET       any long random string, signs the codes
//   ADMIN_KEY    the password you type on the mint page
//   WEBHOOK_URL  your Discord webhook URL
//
// Code format:  FC1.<base64url({"u": worker url, "e": expiry unix seconds, "n": nonce})>.<base64url(HMAC-SHA256)>

const MAX_BODY = 1_000_000;          // bytes accepted for one report
const MAX_HOURS = 72;                // longest validity a code can be minted with
const DEFAULT_HOURS = 24;
const RATE_PER_MINUTE = 5;           // per code and per client address (best effort, per isolate)

const enc = new TextEncoder();
const hits = new Map();

const b64url = bytes => {
  let s = "";
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
};
const fromB64url = str => {
  const p = str.replace(/-/g, "+").replace(/_/g, "/") + "===".slice((str.length + 3) % 4);
  const bin = atob(p);
  return Uint8Array.from(bin, c => c.charCodeAt(0));
};

const hmacKey = (secret, usage) =>
  crypto.subtle.importKey("raw", enc.encode(secret), { name: "HMAC", hash: "SHA-256" }, false, [usage]);

async function sign(secret, text) {
  const sig = await crypto.subtle.sign("HMAC", await hmacKey(secret, "sign"), enc.encode(text));
  return b64url(new Uint8Array(sig));
}

async function verify(secret, text, sigB64) {
  try {
    return await crypto.subtle.verify("HMAC", await hmacKey(secret, "verify"), fromB64url(sigB64), enc.encode(text));
  } catch {
    return false;
  }
}

async function sameString(a, b) {
  // compare hashes so the comparison time doesn't depend on where the strings differ
  const [x, y] = await Promise.all([a, b].map(async s => new Uint8Array(await crypto.subtle.digest("SHA-256", enc.encode(s)))));
  let d = 0;
  for (let i = 0; i < x.length; i++) d |= x[i] ^ y[i];
  return d === 0;
}

function rateLimited(key, now) {
  const recent = (hits.get(key) || []).filter(t => now - t < 60_000);
  recent.push(now);
  hits.set(key, recent);
  if (hits.size > 5000) hits.clear();
  return recent.length > RATE_PER_MINUTE;
}

const text = (body, status = 200, type = "text/plain; charset=utf-8") =>
  new Response(body, { status, headers: { "content-type": type, "cache-control": "no-store" } });

const clip = (s, n) => {
  s = String(s ?? "");
  return s.length > n ? s.slice(0, n - 1) + "…" : s;
};

async function mintCode(env, origin, hours) {
  const payload = b64url(enc.encode(JSON.stringify({
    u: origin,
    e: Math.floor(Date.now() / 1000) + Math.round(hours * 3600),
    n: b64url(crypto.getRandomValues(new Uint8Array(6)))
  })));
  const head = "FC1." + payload;
  return head + "." + await sign(env.SECRET, head);
}

const PAGE = (body) => `<!doctype html><html lang="ar" dir="rtl"><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>FyoCord support codes</title>
<style>body{font-family:system-ui,sans-serif;background:#0d0b1a;color:#f2efff;max-width:520px;margin:0 auto;padding:24px}
input,button{font-size:16px;padding:12px;border-radius:10px;border:1px solid #2e2760;background:#181430;color:#f2efff;width:100%;box-sizing:border-box;margin:6px 0}
button{background:linear-gradient(90deg,#7c5cff,#ff4d9d);border:0;font-weight:700}
code{display:block;word-break:break-all;background:#181430;border:1px solid #2e2760;border-radius:10px;padding:12px;direction:ltr;user-select:all}
h1{background:linear-gradient(90deg,#9d7bff,#ff4d9d);-webkit-background-clip:text;color:transparent}</style>${body}</html>`;

async function handleMint(request, env, origin) {
  if (request.method === "GET") {
    return text(PAGE(`<h1>FyoCord</h1><form method="post" action="/mint">
      <label>كلمة السر (ADMIN_KEY)</label><input type="password" name="key" required autocomplete="current-password">
      <label>مدة الصلاحية بالساعات (حتى ${MAX_HOURS})</label><input type="number" name="hours" value="${DEFAULT_HOURS}" min="0.1" max="${MAX_HOURS}" step="any">
      <button>إنشاء كود</button></form>`), 200, "text/html; charset=utf-8");
  }
  const form = await request.formData();
  if (!env.ADMIN_KEY || !(await sameString(String(form.get("key") || ""), env.ADMIN_KEY))) {
    return text(PAGE(`<h1>FyoCord</h1><p>كلمة السر غير صحيحة.</p><a href="/" style="color:#9d7bff">رجوع</a>`), 401, "text/html; charset=utf-8");
  }
  let hours = Number(form.get("hours"));
  if (!Number.isFinite(hours) || hours <= 0) hours = DEFAULT_HOURS;
  hours = Math.min(hours, MAX_HOURS);
  const code = await mintCode(env, origin, hours);
  return text(PAGE(`<h1>FyoCord</h1><p>الكود صالح لمدة ${hours} ساعة. أرسله للمستخدم:</p><code>${code}</code><p><a href="/" style="color:#9d7bff">كود جديد</a></p>`),
    200, "text/html; charset=utf-8");
}

async function handleReport(request, env) {
  if (request.method !== "POST") return text("method not allowed", 405);
  const length = Number(request.headers.get("content-length") || 0);
  if (length > MAX_BODY) return text("too large", 413);

  const now = Date.now();
  const client = request.headers.get("cf-connecting-ip") || "unknown";
  if (rateLimited("ip:" + client, now)) return text("slow down", 429);

  let form;
  try {
    form = await request.formData();
  } catch {
    return text("bad request", 400);
  }

  const code = String(form.get("code") || "").replace(/\s+/g, "");
  const parts = code.split(".");
  if (parts.length !== 3 || parts[0] !== "FC1") return text("invalid code", 401);
  if (!(await verify(env.SECRET, parts[0] + "." + parts[1], parts[2]))) return text("invalid code", 401);

  let payload;
  try {
    payload = JSON.parse(new TextDecoder().decode(fromB64url(parts[1])));
  } catch {
    return text("invalid code", 401);
  }
  if (!(payload.e > now / 1000)) return text("expired", 410);
  if (rateLimited("code:" + parts[2], now)) return text("slow down", 429);

  const file = form.get("log");
  if (file && typeof file !== "string" && file.size > MAX_BODY) return text("too large", 413);

  const description = clip(form.get("description"), 1800);
  const contact = clip(form.get("contact"), 200) || "—";
  const meta = clip(form.get("meta"), 900);

  const payloadJson = {
    username: "FyoCord reports",
    allowed_mentions: { parse: [] },
    embeds: [{
      title: "تقرير مشكلة · Bug report",
      description,
      color: 0x7c5cff,
      fields: [
        { name: "التواصل · Contact", value: "```" + clip(contact.replace(/`/g, "'"), 190) + "```" },
        { name: "الجهاز · Device", value: "```" + meta.replace(/`/g, "'") + "```" },
        { name: "الكود صالح حتى", value: `<t:${Math.floor(payload.e)}:f>` }
      ],
      timestamp: new Date(now).toISOString()
    }]
  };

  const out = new FormData();
  out.append("payload_json", JSON.stringify(payloadJson));
  if (file && typeof file !== "string") out.append("files[0]", file, "fyocord-log.txt");

  const res = await fetch(env.WEBHOOK_URL, { method: "POST", body: out });
  if (!res.ok) return text("relay failed " + res.status, 502);
  return text("ok");
}

export default {
  async fetch(request, env) {
    if (!env.SECRET || !env.WEBHOOK_URL) return text("not configured: set the SECRET, ADMIN_KEY and WEBHOOK_URL secrets", 500);
    const url = new URL(request.url);
    try {
      if (url.pathname === "/report") return await handleReport(request, env);
      if (url.pathname === "/" || url.pathname === "/mint") return await handleMint(request, env, url.origin);
    } catch (e) {
      return text("error", 500);
    }
    return text("not found", 404);
  }
};
