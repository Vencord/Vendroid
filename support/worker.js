// FyoCord support relay: a free Cloudflare Worker.
//
// What it does
//   * keeps the Discord webhook URL secret (the app never contains it),
//   * mints ACCESS codes for the people you choose: each one has a name and stops working after the time you pick
//     (hours, days, months or years). The app asks for it before anything else and locks itself when it expires,
//   * activates / re-checks codes (/activate), so a revoked code stops working as soon as the phone is online,
//   * receives a bug report (text + log file) from the app and forwards it to your Discord webhook.
//
// With a KV namespace bound as CODES the Worker also serves an admin dashboard at "/": make codes with an exact end
// date/time, set how many devices each may use, pause / resume / delete them, see the devices, change the time later.
// Without KV it falls back to the old stateless mode (code expiry baked into the code, REVOKED list).
//
// Secrets (Worker > Settings > Variables and Secrets, type "Secret"):
//   SECRET       any long random string, signs the codes
//   ADMIN_KEY    the password you type on the mint page
//   WEBHOOK_URL  your Discord webhook URL
//   REVOKED      (optional) comma separated code IDs to switch off at once, e.g. "a1b2c3,d4e5f6"
//
// Code format:  FC1.<base64url({"u": worker url, "e": expiry unix seconds, "n": id, "l": person's name})>.<base64url(HMAC-SHA256)>

const MAX_BODY = 1_000_000;          // bytes accepted for one report
const MAX_HOURS = 24 * 365 * 20;     // longest validity a code can be minted with (20 years)
const RATE_PER_MINUTE = 10;          // per code and per client address (best effort, per isolate)
const UNIT_HOURS = { minutes: 1 / 60, hours: 1, days: 24, months: 24 * 30, years: 24 * 365 };
const SIX_HOURS = 6 * 3600_000;

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

const esc = t => String(t).replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));

const html = (body, status = 200) => text(body, status, "text/html; charset=utf-8");
const json = (obj, status = 200) => text(JSON.stringify(obj), status, "application/json; charset=utf-8");

const FAR = 20 * 365 * 24 * 3600; // KV mode: the signed token never expires, the record decides

async function mintCode(env, origin, expSeconds, label, id) {
  const payload = b64url(enc.encode(JSON.stringify({ u: origin, e: expSeconds, n: id, l: clip(label, 40) })));
  const head = "FC1." + payload;
  return head + "." + await sign(env.SECRET, head);
}
const newId = () => b64url(crypto.getRandomValues(new Uint8Array(6)));

async function isAdmin(request, env) {
  const key = request.headers.get("x-admin-key") || "";
  return !!env.ADMIN_KEY && key.length > 0 && await sameString(key, env.ADMIN_KEY);
}

async function listRecords(env) {
  const out = [];
  let cursor;
  do {
    const page = await env.CODES.list({ prefix: "c:", cursor, limit: 1000 });
    for (const k of page.keys) {
      const r = await env.CODES.get(k.name, "json");
      if (r) out.push(r);
    }
    cursor = page.list_complete ? undefined : page.cursor;
  } while (cursor && out.length < 1000);
  return out.sort((x, y) => y.created - x.created);
}

// Dashboard API (needs the x-admin-key header)
async function handleApi(request, env, url) {
  if (!(await isAdmin(request, env))) return json({ error: "bad key" }, 401);
  if (!env.CODES) return json({ error: "no KV: bind a KV namespace called CODES" }, 501);
  const m = url.pathname.match(/^\/api\/codes(?:\/([\w-]+))?$/);
  if (!m) return json({ error: "not found" }, 404);
  const id = m[1];
  const now = Math.floor(Date.now() / 1000);

  if (!id && request.method === "GET") return json({ now, codes: await listRecords(env) });

  if (!id && request.method === "POST") {
    const b = await request.json().catch(() => ({}));
    let exp = Number(b.exp);
    if (!Number.isFinite(exp) || exp <= now) return json({ error: "bad end time" }, 400);
    exp = Math.min(exp, now + FAR);
    const max = Math.max(1, Math.min(1000, Math.floor(Number(b.maxDevices) || 1)));
    const nid = newId();
    const label = clip(String(b.label || "").trim() || "user", 40);
    const token = await mintCode(env, url.origin, now + FAR, label, nid);
    const rec = { id: nid, label, exp, max, disabled: false, created: now, token, devices: {} };
    await env.CODES.put("c:" + nid, JSON.stringify(rec));
    return json(rec);
  }

  const rec = id && await env.CODES.get("c:" + id, "json");
  if (!rec) return json({ error: "not found" }, 404);

  if (request.method === "DELETE") {
    await env.CODES.delete("c:" + id);
    return json({ ok: true });
  }
  if (request.method === "PATCH") {
    const b = await request.json().catch(() => ({}));
    if (typeof b.disabled === "boolean") rec.disabled = b.disabled;
    if (b.label != null) rec.label = clip(String(b.label).trim() || rec.label, 40);
    if (b.exp != null) {
      const e = Number(b.exp);
      if (!Number.isFinite(e) || e <= now) return json({ error: "bad end time" }, 400);
      rec.exp = Math.min(e, now + FAR);
    }
    if (b.maxDevices != null) rec.max = Math.max(1, Math.min(1000, Math.floor(Number(b.maxDevices) || 1)));
    if (b.clearDevices) rec.devices = {};
    if (b.removeDevice) delete rec.devices[String(b.removeDevice)];
    await env.CODES.put("c:" + id, JSON.stringify(rec));
    return json(rec);
  }
  return json({ error: "method" }, 405);
}

const DASH = `<!doctype html><html lang="ar" dir="rtl"><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>FyoCord codes</title>
<style>
*{box-sizing:border-box;border-radius:0}
body{font-family:ui-monospace,Menlo,Consolas,monospace;background:#000;color:#fff;max-width:760px;margin:0 auto;padding:16px}
h1{letter-spacing:4px;margin:8px 0 16px;border-bottom:2px solid #fff;padding-bottom:8px}
input,select,button{font:inherit;font-size:15px;padding:10px;border:2px solid #fff;background:#000;color:#fff;margin:4px 0;width:100%}
button{cursor:pointer;font-weight:700;width:auto;padding:8px 12px}
button:hover,button.on{background:#fff;color:#000}
.card{border:2px solid #fff;padding:12px;margin:12px 0}
.row{display:flex;gap:8px;flex-wrap:wrap;align-items:center}
.row>*{flex:1 1 140px}
.small{opacity:.7;font-size:13px}
.tok{direction:ltr;word-break:break-all;border:1px dashed #fff;padding:8px;margin:8px 0;font-size:12px;user-select:all}
.tag{display:inline-block;border:1px solid #fff;padding:1px 6px;font-size:12px;margin-inline-start:6px}
.dim{opacity:.45}
label{font-size:13px;opacity:.8;display:block;margin-top:6px}
.dev{direction:ltr;font-size:12px;border-top:1px solid #444;padding:4px 0}
</style>
<h1>■ FYODOR · CODES</h1>
<div id="login" class="card"><label>ADMIN_KEY</label><input id="key" type="password" autocomplete="current-password"><button id="go">دخول</button><div id="err" class="small"></div></div>
<div id="app" style="display:none">
 <div class="card"><b>كود جديد</b>
  <label>اسم الشخص</label><input id="label" maxlength="40" placeholder="Ahmed">
  <label>عدد الأجهزة المسموحة</label><input id="max" type="number" min="1" value="1">
  <label>ينتهي في (تاريخ ووقت بالضبط)</label><input id="end" type="datetime-local">
  <div class="row"><button data-add="3600">+1h</button><button data-add="86400">+1d</button><button data-add="604800">+7d</button><button data-add="2592000">+30d</button><button data-add="31536000">+1y</button></div>
  <div class="small" id="endinfo"></div>
  <button id="create" style="margin-top:8px">إنشاء كود</button>
 </div>
 <div id="list"></div>
</div>
<script>
var KEY="",NOW=0;
function $(i){return document.getElementById(i)}
function pad(n){return String(n).padStart(2,"0")}
function toLocalInput(sec){var d=new Date(sec*1000);return d.getFullYear()+"-"+pad(d.getMonth()+1)+"-"+pad(d.getDate())+"T"+pad(d.getHours())+":"+pad(d.getMinutes())}
function fmt(sec){return new Date(sec*1000).toLocaleString("en-GB",{hour12:false})}
function left(sec){var s=sec-NOW;if(s<=0)return "منتهي";var d=Math.floor(s/86400),h=Math.floor(s%86400/3600),m=Math.floor(s%3600/60);return (d?d+"d ":"")+h+"h "+m+"m"}
function api(method,path,body){return fetch(path,{method:method,headers:{"x-admin-key":KEY,"content-type":"application/json"},body:body?JSON.stringify(body):undefined}).then(function(r){return r.json().then(function(j){if(!r.ok)throw new Error(j.error||r.status);return j})})}
function esc(t){return String(t).replace(/[&<>"']/g,function(c){return {"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]})}
function endSec(){var v=$("end").value;return v?Math.floor(new Date(v).getTime()/1000):0}
function showEnd(){var e=endSec();$("endinfo").textContent=e?("ينتهي: "+fmt(e)+" (توقيت جهازك)"):""}
function setEnd(sec){$("end").value=toLocalInput(sec);showEnd()}
function load(){return api("GET","/api/codes").then(function(r){NOW=r.now;render(r.codes)})}
function render(codes){
 var h="";
 if(!codes.length)h='<div class="small">لا توجد أكواد بعد</div>';
 codes.forEach(function(c){
  var n=Object.keys(c.devices||{}).length,exp=c.exp<=NOW,st=c.disabled?"موقوف":exp?"منتهي":"فعّال";
  h+='<div class="card'+((c.disabled||exp)?' dim':'')+'" data-id="'+c.id+'"><b>'+esc(c.label)+'</b><span class="tag">'+st+'</span><span class="tag">'+n+'/'+c.max+' أجهزة</span>'
  +'<div class="small">ينتهي: '+fmt(c.exp)+' · المتبقي: '+left(c.exp)+'</div>'
  +'<div class="tok">'+esc(c.token)+'</div>'
  +'<div class="row"><button data-a="copy">نسخ</button><button data-a="toggle">'+(c.disabled?"تشغيل":"إيقاف")+'</button><button data-a="time">تغيير الوقت</button><button data-a="max">عدد الأجهزة</button><button data-a="clear">مسح الأجهزة</button><button data-a="del">حذف</button></div>';
  var ds=c.devices||{};
  Object.keys(ds).forEach(function(k){var d=ds[k];h+='<div class="dev">'+esc(d.m||"?")+' · '+k.slice(0,8)+' · first '+fmt(d.f)+' · last '+fmt(d.l)+' <button data-a="rm" data-d="'+k+'" style="padding:2px 8px">x</button></div>'});
  h+='</div>';
 });
 $("list").innerHTML=h;
}
$("go").onclick=function(){KEY=$("key").value;$("err").textContent="";load().then(function(){$("login").style.display="none";$("app").style.display="block";setEnd(NOW+30*86400)}).catch(function(e){$("err").textContent=e.message})};
$("end").oninput=showEnd;
document.querySelectorAll("[data-add]").forEach(function(b){b.onclick=function(){var base=Math.max(endSec(),NOW);setEnd(base+Number(b.dataset.add))}});
$("create").onclick=function(){var e=endSec();if(!e){alert("اختر وقت الانتهاء");return}
 api("POST","/api/codes",{label:$("label").value,maxDevices:Number($("max").value)||1,exp:e}).then(function(){$("label").value="";return load()}).catch(function(x){alert(x.message)})};
$("list").onclick=function(ev){var b=ev.target.closest("button");if(!b)return;var card=b.closest(".card"),id=card.dataset.id,a=b.dataset.a;
 var done=function(){return load()},fail=function(x){alert(x.message)};
 if(a==="copy"){navigator.clipboard.writeText(card.querySelector(".tok").textContent);b.textContent="✓";return}
 if(a==="toggle"){api("GET","/api/codes").then(function(r){var c=r.codes.find(function(z){return z.id===id});return api("PATCH","/api/codes/"+id,{disabled:!c.disabled})}).then(done).catch(fail)}
 if(a==="del"){if(confirm("حذف الكود نهائياً؟ سيُقفل التطبيق عند صاحبه."))api("DELETE","/api/codes/"+id).then(done).catch(fail)}
 if(a==="clear"){if(confirm("مسح كل الأجهزة المسجلة؟"))api("PATCH","/api/codes/"+id,{clearDevices:true}).then(done).catch(fail)}
 if(a==="rm"){api("PATCH","/api/codes/"+id,{removeDevice:b.dataset.d}).then(done).catch(fail)}
 if(a==="max"){var v=prompt("عدد الأجهزة المسموحة","1");if(v)api("PATCH","/api/codes/"+id,{maxDevices:Number(v)}).then(done).catch(fail)}
 if(a==="time"){var v=prompt("وقت الانتهاء الجديد (YYYY-MM-DD HH:MM) بتوقيت جهازك");if(v){var t=new Date(v.replace(" ","T")).getTime();if(!t){alert("صيغة غير صحيحة");return}api("PATCH","/api/codes/"+id,{exp:Math.floor(t/1000)}).then(done).catch(fail)}}
};
</script></html>`;

// Stateless fallback (no KV): minimal form, expiry baked into the code
const LEGACY = (body) => `<!doctype html><html lang="ar" dir="rtl"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>FyoCord</title>
<style>body{font-family:monospace;background:#000;color:#fff;max-width:520px;margin:0 auto;padding:24px}input,select,button{font:inherit;padding:12px;border:2px solid #fff;background:#000;color:#fff;width:100%;box-sizing:border-box;margin:6px 0}code{display:block;word-break:break-all;border:2px solid #fff;padding:12px;direction:ltr;user-select:all}</style>${body}</html>`;

async function handleMint(request, env, origin) {
  if (env.CODES) return html(DASH);
  if (request.method === "GET") {
    return html(LEGACY(`<h1>FYODOR</h1><p>بدون KV: ربط KV باسم CODES يفتح لوحة التحكم الكاملة.</p><form method="post" action="/mint">
      <input type="password" name="key" placeholder="ADMIN_KEY" required>
      <input type="text" name="label" maxlength="40" placeholder="Name" required>
      <input type="number" name="amount" value="30" min="1" step="1" required>
      <select name="unit"><option value="minutes">دقائق</option><option value="hours">ساعات</option><option value="days" selected>أيام</option><option value="months">أشهر</option><option value="years">سنوات</option></select>
      <button>إنشاء كود</button></form>`));
  }
  const form = await request.formData();
  if (!env.ADMIN_KEY || !(await sameString(String(form.get("key") || ""), env.ADMIN_KEY))) return html(LEGACY("<p>كلمة السر غير صحيحة.</p>"), 401);
  const unit = UNIT_HOURS[String(form.get("unit"))] || 24;
  let hours = Number(form.get("amount")) * unit;
  if (!Number.isFinite(hours) || hours <= 0) hours = 24 * 30;
  hours = Math.min(hours, MAX_HOURS);
  const id = newId();
  const exp = Math.floor(Date.now() / 1000) + Math.round(hours * 3600);
  const label = String(form.get("label") || "").trim() || "user";
  const code = await mintCode(env, origin, exp, label, id);
  return html(LEGACY(`<h1>FYODOR</h1><p>${esc(label)} · ${new Date(exp * 1000).toISOString().replace("T", " ").slice(0, 16)} UTC</p><code>${code}</code><p style="opacity:.7;direction:ltr">ID: ${id}</p>`));
}

// shared by /activate, /check and /report: returns { payload (with the real expiry), sig } or { fail: Response }.
// device (optional): registers it against the code's device limit when KV is bound.
async function checkCode(env, codeText, now, device, model) {
  const parts = String(codeText || "").replace(/\s+/g, "").split(".");
  if (parts.length !== 3 || parts[0] !== "FC1") return { fail: text("invalid code", 401) };
  if (!(await verify(env.SECRET, parts[0] + "." + parts[1], parts[2]))) return { fail: text("invalid code", 401) };
  let payload;
  try {
    payload = JSON.parse(new TextDecoder().decode(fromB64url(parts[1])));
  } catch {
    return { fail: text("invalid code", 401) };
  }
  if (env.CODES) {
    const rec = await env.CODES.get("c:" + payload.n, "json");
    if (!rec) return { fail: text("revoked", 403 ) };          // deleted from the dashboard
    if (rec.disabled) return { fail: text("revoked", 403) };   // paused
    if (!(rec.exp > now / 1000)) return { fail: text("expired", 410) };
    payload.e = rec.exp;
    payload.l = rec.label;
    if (device) {
      const id = clip(String(device).replace(/[^\w-]/g, ""), 64);
      const known = rec.devices[id];
      const t = Math.floor(now / 1000);
      if (!known) {
        if (Object.keys(rec.devices).length >= rec.max) return { fail: text("device limit", 409) };
        rec.devices[id] = { m: clip(model || "?", 60), f: t, l: t };
        await env.CODES.put("c:" + rec.id, JSON.stringify(rec));
      } else if (now - known.l * 1000 > SIX_HOURS) {
        known.l = t;
        await env.CODES.put("c:" + rec.id, JSON.stringify(rec));
      }
    }
    return { payload, sig: parts[2] };
  }
  if (!(payload.e > now / 1000)) return { fail: text("expired", 410) };
  const revoked = String(env.REVOKED || "").split(",").map(x => x.trim()).filter(Boolean);
  if (payload.n && revoked.includes(payload.n)) return { fail: text("revoked", 403) };
  return { payload, sig: parts[2] };
}

async function handleActivate(request, env) {
  if (request.method !== "POST") return text("method not allowed", 405);
  const now = Date.now();
  if (rateLimited("ip:" + (request.headers.get("cf-connecting-ip") || "unknown"), now)) return text("slow down", 429);
  let form;
  try {
    form = await request.formData();
  } catch {
    return text("bad request", 400);
  }
  const r = await checkCode(env, form.get("code"), now, form.get("device"), form.get("model"));
  if (r.fail) return r.fail;
  return text(JSON.stringify({ ok: true, exp: r.payload.e, label: r.payload.l || "" }), 200, "application/json");
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

  const r = await checkCode(env, form.get("code"), now);
  if (r.fail) return r.fail;
  const payload = r.payload;
  if (rateLimited("code:" + r.sig, now)) return text("slow down", 429);

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
        { name: "المستخدم · User", value: "```" + clip(String(payload.l || "—").replace(/`/g, "'"), 60) + "```" },
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
      if (url.pathname === "/activate" || url.pathname === "/check") return await handleActivate(request, env);
      if (url.pathname.startsWith("/api/")) return await handleApi(request, env, url);
      if (url.pathname === "/" || url.pathname === "/mint") return await handleMint(request, env, url.origin);
    } catch (e) {
      return text("error", 500);
    }
    return text("not found", 404);
  }
};
