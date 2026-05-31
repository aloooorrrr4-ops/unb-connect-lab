const http = require("http");
const fs = require("fs");
const path = require("path");
const crypto = require("crypto");

const PORT = Number(process.env.PORT || 3108);
const ROOT = __dirname;
const DATA = path.join(ROOT, "data");
const QUEUE_FILE = path.join(DATA, "termux_queue.json");
const TOKEN_FILE = path.join(DATA, "web_token.txt");
const STATE_FILE = path.join(DATA, "device_state.json");

fs.mkdirSync(DATA, { recursive: true });

if (!fs.existsSync(TOKEN_FILE)) {
  fs.writeFileSync(TOKEN_FILE, "UNB_" + crypto.randomBytes(24).toString("hex"));
}
if (!fs.existsSync(QUEUE_FILE)) {
  fs.writeFileSync(QUEUE_FILE, "[]");
}
if (!fs.existsSync(STATE_FILE)) {
  fs.writeFileSync(STATE_FILE, JSON.stringify({
    mode: "termux_temp_queue",
    whatsapp_web_status: "disconnected",
    whatsapp_web_session: "wa_temp_1",
    last_heartbeat: null,
    full_link_enabled: false
  }, null, 2));
}

const TOKEN = fs.readFileSync(TOKEN_FILE, "utf8").trim();

const DEVICE_METHODS = ["call_apk", "sms_apk", "whatsapp_apk", "whatsapp_web"];
const METHOD_ORDER = {
  call_apk: 1,
  sms_apk: 2,
  whatsapp_apk: 3,
  whatsapp_web: 4
};

function nowIso() {
  return new Date().toISOString();
}

function makeId(prefix) {
  return prefix + "_" + Date.now() + "_" + crypto.randomBytes(4).toString("hex");
}

function jsonRead(file, fallback) {
  try {
    const raw = fs.readFileSync(file, "utf8");
    const data = JSON.parse(raw || "null");
    return data ?? fallback;
  } catch {
    return fallback;
  }
}

function jsonWrite(file, value) {
  fs.writeFileSync(file, JSON.stringify(value, null, 2), "utf8");
}

function readQueue() {
  const rows = jsonRead(QUEUE_FILE, []);
  return Array.isArray(rows) ? rows : [];
}

function writeQueue(rows) {
  jsonWrite(QUEUE_FILE, rows);
}

function readState() {
  return jsonRead(STATE_FILE, {});
}

function writeState(state) {
  jsonWrite(STATE_FILE, state);
}

function send(res, code, obj) {
  const body = JSON.stringify(obj, null, 2);
  res.writeHead(code, {
    "Content-Type": "application/json; charset=utf-8",
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Headers": "Content-Type, X-Web-Token",
    "Access-Control-Allow-Methods": "GET,POST,OPTIONS"
  });
  res.end(body);
}

function cleanPhone(v) {
  return String(v || "").replace(/[^\d+]/g, "");
}

function parseBody(req) {
  return new Promise(resolve => {
    let buf = "";
    req.on("data", c => buf += c);
    req.on("end", () => {
      try { resolve(buf ? JSON.parse(buf) : {}); }
      catch { resolve({}); }
    });
  });
}

function tokenFrom(req, u, body) {
  return String(
    (body && body.token) ||
    u.searchParams.get("token") ||
    req.headers["x-web-token"] ||
    ""
  ).trim();
}

function requireToken(req, res, u, body) {
  if (tokenFrom(req, u, body) !== TOKEN) {
    send(res, 401, { ok: false, error: "INVALID_WEB_TOKEN" });
    return false;
  }
  return true;
}

function normalizeMethods(value) {
  let arr = [];
  if (Array.isArray(value)) arr = value;
  else if (typeof value === "string") arr = value.split(",");
  arr = arr.map(x => String(x || "").trim()).filter(Boolean);
  arr = arr.filter(x => DEVICE_METHODS.includes(x));
  arr = Array.from(new Set(arr));
  return arr.length ? arr : DEVICE_METHODS.slice();
}

function sortQueue(rows) {
  return rows.sort((a, b) => {
    const ma = METHOD_ORDER[a.delivery_method] || 99;
    const mb = METHOD_ORDER[b.delivery_method] || 99;
    if (ma !== mb) return ma - mb;

    const da = Date.parse(a.due_at || a.created_at || 0) || 0;
    const db = Date.parse(b.due_at || b.created_at || 0) || 0;
    if (da !== db) return da - db;

    const pa = Number(a.priority || 0);
    const pb = Number(b.priority || 0);
    if (pa !== pb) return pb - pa;

    return String(a.created_at || "").localeCompare(String(b.created_at || ""));
  });
}

function stats(rows) {
  const out = { total: rows.length, by_status: {}, by_method: {}, pending_device_rows: 0 };
  for (const r of rows) {
    out.by_status[r.status] = (out.by_status[r.status] || 0) + 1;
    out.by_method[r.delivery_method] = (out.by_method[r.delivery_method] || 0) + 1;
    if (r.status === "pending" && DEVICE_METHODS.includes(r.delivery_method)) out.pending_device_rows++;
  }
  return out;
}

function page(res) {
  res.writeHead(200, { "Content-Type": "text/html; charset=utf-8" });
  res.end(`<!doctype html>
<html lang="ar" dir="rtl">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>UNB Connect APK Hydra</title>
<style>
:root{--bg:#f4f6fb;--card:#fff;--dark:#111827;--muted:#6b7280;--line:#e5e7eb;--ok:#0f766e;--bad:#b91c1c;--warn:#b45309}
*{box-sizing:border-box}
body{font-family:system-ui,-apple-system,"Segoe UI",sans-serif;background:var(--bg);margin:0;color:#111}
.wrap{max-width:1060px;margin:auto;padding:14px}
.hero{background:linear-gradient(135deg,#111827,#374151);color:white;border-radius:24px;padding:18px;margin-bottom:14px;box-shadow:0 10px 25px #0002}
.hero h1{margin:0 0 6px;font-size:22px}
.hero p{margin:0;color:#d1d5db}
.card{background:var(--card);border:1px solid var(--line);border-radius:20px;padding:14px;margin-bottom:12px;box-shadow:0 6px 18px #0000000d}
.grid{display:grid;grid-template-columns:1fr 1fr;gap:10px}
.grid3{display:grid;grid-template-columns:repeat(3,1fr);gap:8px}
input,textarea,button,select{width:100%;padding:11px;border-radius:13px;border:1px solid #d1d5db;font:inherit;margin:5px 0;background:white}
textarea{min-height:86px}
button{background:#111827;color:white;font-weight:800;border:0}
button.secondary{background:#374151}
button.ok{background:var(--ok)}
button.warn{background:var(--warn)}
button.bad{background:var(--bad)}
button.light{background:#eef2ff;color:#111827;border:1px solid #c7d2fe}
pre{background:#07101f;color:#d7ecff;padding:12px;border-radius:14px;white-space:pre-wrap;direction:ltr;text-align:left;max-height:360px;overflow:auto}
.badge{display:inline-block;border-radius:999px;padding:6px 10px;margin:3px;font-size:12px;background:#eef2ff;border:1px solid #c7d2fe;color:#111827}
.status{font-weight:900}
.status.ok{color:var(--ok)}.status.bad{color:var(--bad)}.status.warn{color:var(--warn)}
.method-row{display:grid;grid-template-columns:110px 1fr auto;gap:7px;align-items:center;border:1px solid var(--line);border-radius:14px;padding:8px;margin:6px 0;background:#fafafa}
.small{font-size:12px;color:var(--muted)}
.ltr{direction:ltr;text-align:left}
@media(max-width:760px){.grid,.grid3{grid-template-columns:1fr}.method-row{grid-template-columns:1fr}}
</style>
</head>
<body>
<div class="wrap">
  <div class="hero">
    <h1>UNB Connect APK Hydra</h1>
    <p>ربط مؤقت مع Termux الآن، ولاحقًا نفس البروتوكول يربط مع نظام UNB ERP الحقيقي.</p>
  </div>

  <div class="card">
    <h3>1) ربط النظام المؤقت</h3>
    <div class="grid">
      <div>
        <label>System URL</label>
        <input id="systemUrl" value="http://127.0.0.1:${PORT}">
      </div>
      <div>
        <label>Web Token</label>
        <input id="webToken" placeholder="اضغط تحميل التوكن">
      </div>
    </div>
    <label>
      <input id="fullLink" type="checkbox" style="width:auto"> تفعيل الربط الكامل مع الطابور
    </label>
    <div class="grid3">
      <button onclick="loadConfig()" class="light">تحميل التوكن</button>
      <button onclick="saveLink()" class="ok">حفظ الرابط والتوكن</button>
      <button onclick="heartbeat()" class="secondary">فحص الاتصال</button>
    </div>
    <div>
      <span class="badge">الوضع: <b>Termux Temporary Queue</b></span>
      <span class="badge">المنفذ: <b>${PORT}</b></span>
      <span class="badge">طرق الجهاز: <b>call/sms/wa-apk/wa-web</b></span>
    </div>
  </div>

  <div class="card">
    <h3>2) ربط WhatsApp Web داخل التطبيق</h3>
    <p class="small">مؤقتًا: الزر يستدعي Android Bridge إن وُجد داخل APK. إذا لم يوجد، يفتح رابط WhatsApp Web فقط. لاحقًا نربطه Native WebView كامل.</p>
    <div class="grid3">
      <button onclick="waStart()" class="ok">ربط WhatsApp Web</button>
      <button onclick="waStatus()" class="secondary">حالة الربط</button>
      <button onclick="waLogout()" class="bad">فصل الربط</button>
    </div>
    <p>الحالة: <span id="waState" class="status warn">غير معروف</span></p>
  </div>

  <div class="card">
    <h3>3) إضافة طابور مؤقت من Termux</h3>
    <div class="grid">
      <div>
        <label>رقم الجوال</label>
        <input id="phone" value="967700000000">
      </div>
      <div>
        <label>نوع المستلم</label>
        <select id="recipientType">
          <option value="customer">customer</option>
          <option value="manager">manager</option>
        </select>
      </div>
    </div>
    <label>الرسالة</label>
    <textarea id="message">اختبار من طابور Termux المؤقت</textarea>
    <label>طرق الإرسال</label>
    <select id="methods" multiple size="4">
      <option value="call_apk">call_apk</option>
      <option value="sms_apk" selected>sms_apk</option>
      <option value="whatsapp_apk" selected>whatsapp_apk</option>
      <option value="whatsapp_web" selected>whatsapp_web</option>
    </select>
    <div class="grid3">
      <button onclick="enqueue()">إضافة للطابور</button>
      <button onclick="pullQueue()" class="secondary">سحب الطابور</button>
      <button onclick="executePulled()" class="warn">تنفيذ المسحوب مؤقتًا</button>
    </div>
  </div>

  <div class="card">
    <h3>4) الطابور مرتب حسب طريقة الإرسال</h3>
    <div class="grid3">
      <button onclick="listQueue()" class="light">تحديث القائمة</button>
      <button onclick="pullQueue()" class="secondary">Pull مثل التطبيق</button>
      <button onclick="clearOutput()" class="bad">مسح العرض</button>
    </div>
    <div id="queueRows"></div>
  </div>

  <div class="card">
    <h3>النتيجة</h3>
    <pre id="out">جاهز...</pre>
  </div>
</div>

<script>
let pulledJobs = [];

function apiBase(){
  return (localStorage.getItem("systemUrl") || systemUrl.value || location.origin).replace(/\\/$/,"");
}
function token(){
  return localStorage.getItem("webToken") || webToken.value || "";
}
function selectedMethods(){
  return Array.from(methods.selectedOptions).map(x=>x.value);
}
function print(x){
  out.textContent = typeof x === "string" ? x : JSON.stringify(x,null,2);
}
function clearOutput(){ out.textContent=""; }
async function j(path,opt={}){
  const r = await fetch(apiBase()+path,opt);
  const t = await r.text();
  try{return JSON.parse(t)}catch{return {ok:false,raw:t,status:r.status}}
}
async function loadConfig(){
  const d = await j("/api/config");
  if(d.ok){
    systemUrl.value = d.system_url;
    webToken.value = d.web_token;
    localStorage.setItem("systemUrl", d.system_url);
    localStorage.setItem("webToken", d.web_token);
  }
  print(d);
}
function saveLink(){
  localStorage.setItem("systemUrl", systemUrl.value.trim());
  localStorage.setItem("webToken", webToken.value.trim());
  localStorage.setItem("fullLink", fullLink.checked ? "1":"0");
  print({ok:true, saved:true, system_url:systemUrl.value, token_saved:!!webToken.value, full_link:fullLink.checked});
}
async function heartbeat(){
  const d = await j("/api/device/heartbeat",{
    method:"POST",
    headers:{"Content-Type":"application/json"},
    body:JSON.stringify({token:token(), device_id:"hydra_android_temp", methods:["call_apk","sms_apk","whatsapp_apk","whatsapp_web"], full_link_enabled:fullLink.checked})
  });
  print(d);
}
async function waStart(){
  if(window.Android && Android.openWhatsAppWeb){
    Android.openWhatsAppWeb("wa_temp_1");
  } else {
    window.open("https://web.whatsapp.com/","_blank");
  }
  const d = await j("/api/whatsapp_web/start",{
    method:"POST",
    headers:{"Content-Type":"application/json"},
    body:JSON.stringify({token:token(), session_id:"wa_temp_1"})
  });
  waState.textContent = d.status || "qr_pending";
  waState.className = "status warn";
  print(d);
}
async function waStatus(){
  const d = await j("/api/whatsapp_web/status?token="+encodeURIComponent(token()));
  waState.textContent = d.status || "unknown";
  waState.className = "status " + (d.status==="connected"?"ok":(d.status==="disconnected"?"bad":"warn"));
  print(d);
}
async function waLogout(){
  const d = await j("/api/whatsapp_web/logout",{
    method:"POST",
    headers:{"Content-Type":"application/json"},
    body:JSON.stringify({token:token()})
  });
  waState.textContent = d.status || "disconnected";
  waState.className = "status bad";
  print(d);
}
async function enqueue(){
  const d = await j("/api/queue/enqueue",{
    method:"POST",
    headers:{"Content-Type":"application/json"},
    body:JSON.stringify({
      token:token(),
      phone:phone.value,
      message:message.value,
      recipient_type:recipientType.value,
      methods:selectedMethods()
    })
  });
  print(d);
  listQueue();
}
async function listQueue(){
  const d = await j("/api/queue/list?token="+encodeURIComponent(token()));
  renderRows(d.rows || []);
  print(d);
}
async function pullQueue(){
  const d = await j("/api/queue/pull",{
    method:"POST",
    headers:{"Content-Type":"application/json"},
    body:JSON.stringify({token:token(), limit:20, methods:["call_apk","sms_apk","whatsapp_apk","whatsapp_web"]})
  });
  pulledJobs = d.jobs || [];
  renderRows(pulledJobs);
  print(d);
}
function renderRows(rows){
  queueRows.innerHTML = "";
  if(!rows.length){
    queueRows.innerHTML = '<p class="small">لا توجد صفوف.</p>';
    return;
  }
  for(const r of rows){
    const div = document.createElement("div");
    div.className = "method-row";
    div.innerHTML = '<b class="ltr">'+r.delivery_method+'</b><div><div>'+escapeHtml(r.phone||"")+'</div><div class="small">'+escapeHtml((r.body||"").slice(0,120))+'</div><div class="small ltr">'+escapeHtml(r.id||"")+' | '+escapeHtml(r.status||"")+'</div></div><button onclick="ackSent(\\''+r.id+'\\')">Sent</button>';
    queueRows.appendChild(div);
  }
}
function escapeHtml(s){
  return String(s||"").replace(/[&<>"']/g,m=>({"&":"&amp;","<":"&lt;",">":"&gt;","\\"":"&quot;","'":"&#039;"}[m]));
}
async function ackSent(id){
  const d = await j("/api/queue/ack",{
    method:"POST",
    headers:{"Content-Type":"application/json"},
    body:JSON.stringify({token:token(), queue_id:id, status:"sent"})
  });
  print(d);
  listQueue();
}
async function executePulled(){
  const results = [];
  for(const job of pulledJobs){
    let status = "sent";
    let note = "mock_sent";

    try{
      if(window.Android){
        if(job.delivery_method==="sms_apk" && Android.sendSms){
          Android.sendSms(job.phone, job.body, job.id);
          note = "Android.sendSms";
        } else if(job.delivery_method==="call_apk" && Android.makeCall){
          Android.makeCall(job.phone, job.id);
          note = "Android.makeCall";
        } else if(job.delivery_method==="whatsapp_apk" && Android.sendWhatsAppApk){
          Android.sendWhatsAppApk(job.phone, job.body, job.id);
          note = "Android.sendWhatsAppApk";
        } else if(job.delivery_method==="whatsapp_web" && Android.sendWhatsAppWeb){
          Android.sendWhatsAppWeb(job.phone, job.body, job.id);
          note = "Android.sendWhatsAppWeb";
        } else {
          note = "Android bridge method missing, temporary mock";
        }
      }
    }catch(e){
      status = "failed";
      note = String(e);
    }

    const ack = await j("/api/queue/ack",{
      method:"POST",
      headers:{"Content-Type":"application/json"},
      body:JSON.stringify({token:token(), queue_id:job.id, status, error:note})
    });
    results.push({job:job.id, method:job.delivery_method, status, note, ack});
  }
  print({ok:true, executed:results});
  listQueue();
}
window.addEventListener("load",()=>{
  systemUrl.value = localStorage.getItem("systemUrl") || "http://127.0.0.1:${PORT}";
  webToken.value = localStorage.getItem("webToken") || "";
  fullLink.checked = localStorage.getItem("fullLink") === "1";
  if(!webToken.value) loadConfig(); else listQueue();
});
</script>
</body>
</html>`);
}

http.createServer(async (req, res) => {
  const u = new URL(req.url, "http://127.0.0.1");
  if (req.method === "OPTIONS") return send(res, 200, { ok: true });

  if (u.pathname === "/") return page(res);

  if (u.pathname === "/health") {
    return send(res, 200, {
      ok: true,
      app: "UNB Connect APK Hydra",
      mode: "termux_temp_queue",
      port: PORT,
      device_methods: DEVICE_METHODS,
      token_required: true
    });
  }

  if (u.pathname === "/api/config") {
    return send(res, 200, {
      ok: true,
      system_url: "http://127.0.0.1:" + PORT,
      web_token: TOKEN,
      mode: "termux_temp_queue",
      device_methods: DEVICE_METHODS
    });
  }

  if (u.pathname === "/api/device/heartbeat" && req.method === "POST") {
    const body = await parseBody(req);
    if (!requireToken(req, res, u, body)) return;

    const state = readState();
    state.last_heartbeat = nowIso();
    state.device_id = String(body.device_id || "hydra_android_temp");
    state.full_link_enabled = !!body.full_link_enabled;
    state.methods = normalizeMethods(body.methods);
    writeState(state);

    return send(res, 200, {
      ok: true,
      server_time: nowIso(),
      state,
      device_methods: DEVICE_METHODS
    });
  }

  if (u.pathname === "/api/whatsapp_web/start" && req.method === "POST") {
    const body = await parseBody(req);
    if (!requireToken(req, res, u, body)) return;

    const state = readState();
    state.whatsapp_web_status = "qr_pending";
    state.whatsapp_web_session = String(body.session_id || "wa_temp_1");
    state.whatsapp_web_updated_at = nowIso();
    writeState(state);

    return send(res, 200, {
      ok: true,
      status: state.whatsapp_web_status,
      session_id: state.whatsapp_web_session,
      note: "APK should open WhatsApp Web WebView and complete QR linking itself."
    });
  }

  if (u.pathname === "/api/whatsapp_web/status") {
    const body = {};
    if (!requireToken(req, res, u, body)) return;
    const state = readState();
    return send(res, 200, {
      ok: true,
      status: state.whatsapp_web_status || "disconnected",
      session_id: state.whatsapp_web_session || "wa_temp_1",
      state
    });
  }

  if (u.pathname === "/api/whatsapp_web/connected" && req.method === "POST") {
    const body = await parseBody(req);
    if (!requireToken(req, res, u, body)) return;
    const state = readState();
    state.whatsapp_web_status = "connected";
    state.whatsapp_web_phone = String(body.phone || "");
    state.whatsapp_web_updated_at = nowIso();
    writeState(state);
    return send(res, 200, { ok: true, status: "connected", state });
  }

  if (u.pathname === "/api/whatsapp_web/logout" && req.method === "POST") {
    const body = await parseBody(req);
    if (!requireToken(req, res, u, body)) return;
    const state = readState();
    state.whatsapp_web_status = "disconnected";
    state.whatsapp_web_updated_at = nowIso();
    writeState(state);
    return send(res, 200, { ok: true, status: "disconnected", state });
  }

  if (u.pathname === "/api/queue/list") {
    const body = {};
    if (!requireToken(req, res, u, body)) return;
    const rows = sortQueue(readQueue());
    return send(res, 200, { ok: true, stats: stats(rows), rows });
  }

  if (u.pathname === "/api/queue/enqueue" && req.method === "POST") {
    const body = await parseBody(req);
    if (!requireToken(req, res, u, body)) return;

    const phone = cleanPhone(body.phone);
    const message = String(body.message || body.body || "").trim();
    const methods = normalizeMethods(body.methods || body.method);
    const recipientType = String(body.recipient_type || "customer");
    const title = String(body.title || "UNB Temporary Queue");

    if (!phone) return send(res, 400, { ok: false, error: "PHONE_REQUIRED" });
    if (!message && !methods.includes("call_apk")) return send(res, 400, { ok: false, error: "MESSAGE_REQUIRED" });

    const rows = readQueue();
    const created = [];

    for (const method of methods) {
      const row = {
        id: makeId("TQ"),
        institution_id: Number(body.institution_id || 1),
        event_id: String(body.event_id || ""),
        dispatch_kind: method === "call_apk" ? "call" : "message",
        delivery_method: method,
        recipient_type: recipientType,
        recipient_id: String(body.recipient_id || ""),
        recipient_name: String(body.recipient_name || ""),
        phone,
        title,
        body: message,
        due_at: String(body.due_at || nowIso()),
        priority: Number(body.priority || 5),
        status: "pending",
        attempts: 0,
        max_attempts: Number(body.max_attempts || 3),
        last_error: "",
        created_at: nowIso(),
        updated_at: nowIso(),
        processing_at: null,
        sent_at: null,
        failed_at: null,
        source: "termux_temp_queue"
      };
      rows.push(row);
      created.push(row);
    }

    writeQueue(rows);
    return send(res, 200, { ok: true, created_count: created.length, created });
  }

  if (u.pathname === "/api/test/send" && req.method === "POST") {
    const body = await parseBody(req);
    body.token = body.token || TOKEN;
    body.methods = [String(body.method || "sms_apk")];
    body.message = body.message || "اختبار من UNB Connect Hydra";
    body.recipient_type = body.recipient_type || "customer";

    if (body.token !== TOKEN) return send(res, 401, { ok: false, error: "INVALID_WEB_TOKEN" });

    const phone = cleanPhone(body.phone);
    if (!phone) return send(res, 400, { ok: false, error: "PHONE_REQUIRED" });

    const method = normalizeMethods(body.methods)[0];
    const rows = readQueue();
    const row = {
      id: makeId("TQ"),
      institution_id: 1,
      event_id: "",
      dispatch_kind: method === "call_apk" ? "call" : "message",
      delivery_method: method,
      recipient_type: body.recipient_type,
      recipient_id: "",
      recipient_name: "",
      phone,
      title: "UNB Test",
      body: String(body.message || ""),
      due_at: nowIso(),
      priority: 5,
      status: "pending",
      attempts: 0,
      max_attempts: 3,
      last_error: "",
      created_at: nowIso(),
      updated_at: nowIso(),
      processing_at: null,
      sent_at: null,
      failed_at: null,
      source: "api_test_send"
    };

    rows.push(row);
    writeQueue(rows);
    return send(res, 200, { ok: true, job: row });
  }

  if (u.pathname === "/api/queue/pull" && (req.method === "GET" || req.method === "POST")) {
    const body = req.method === "POST" ? await parseBody(req) : {};
    if (!requireToken(req, res, u, body)) return;

    const requestedMethods = normalizeMethods(body.methods || u.searchParams.get("methods"));
    const limit = Math.max(1, Math.min(100, Number(body.limit || u.searchParams.get("limit") || 20)));

    const rows = readQueue();
    const now = Date.now();

    for (const r of rows) {
      if (r.status === "processing") {
        const t = Date.parse(r.processing_at || r.updated_at || 0) || 0;
        if (now - t > 120000) {
          r.status = "pending";
          r.last_error = "PROCESSING_TIMEOUT_RETURNED_TO_PENDING";
          r.updated_at = nowIso();
          r.processing_at = null;
        }
      }
    }

    const pending = sortQueue(rows.filter(r =>
      r.status === "pending" &&
      requestedMethods.includes(r.delivery_method)
    )).slice(0, limit);

    for (const r of pending) {
      r.status = "processing";
      r.processing_at = nowIso();
      r.updated_at = nowIso();
      r.attempts = Number(r.attempts || 0) + 1;
    }

    writeQueue(rows);

    const by_method = {};
    for (const r of pending) {
      if (!by_method[r.delivery_method]) by_method[r.delivery_method] = [];
      by_method[r.delivery_method].push(r);
    }

    return send(res, 200, {
      ok: true,
      count: pending.length,
      methods: requestedMethods,
      by_method,
      jobs: pending
    });
  }

  if (u.pathname === "/api/queue/ack" && req.method === "POST") {
    const body = await parseBody(req);
    if (!requireToken(req, res, u, body)) return;

    const queueId = String(body.queue_id || body.id || "").trim();
    const status = String(body.status || "").trim();
    const error = String(body.error || "").trim();

    if (!queueId) return send(res, 400, { ok: false, error: "QUEUE_ID_REQUIRED" });
    if (!["sent", "failed", "pending", "processing"].includes(status)) {
      return send(res, 400, { ok: false, error: "INVALID_STATUS" });
    }

    const rows = readQueue();
    const row = rows.find(r => r.id === queueId);
    if (!row) return send(res, 404, { ok: false, error: "QUEUE_ROW_NOT_FOUND" });

    row.status = status;
    row.updated_at = nowIso();
    row.last_error = error;
    if (status === "sent") row.sent_at = nowIso();
    if (status === "failed") row.failed_at = nowIso();
    if (status !== "processing") row.processing_at = null;

    writeQueue(rows);
    return send(res, 200, { ok: true, row });
  }

  if (u.pathname === "/api/jobs") {
    const rows = sortQueue(readQueue());
    return send(res, 200, { ok: true, count: rows.length, jobs: rows });
  }

  return send(res, 404, { ok: false, error: "NOT_FOUND", path: u.pathname });
}).listen(PORT, "0.0.0.0", () => {
  console.log("UNB Connect APK Hydra temporary queue running on port " + PORT);
});
