/**
 * 玩机工具箱 · 公告后台（Worker 写 GitHub 版）
 *
 * 架构：
 *   你（后台网页）→ Worker（持 token）→ 写 GitHub 仓库的 announcements.json
 *   用户 App     → 直接读 GitHub（国内可达，不需要梯子）
 *
 * 环境变量（Cloudflare → Settings → Variables）：
 *   ADMIN_KEY       发布口令（自定义）
 *   GITHUB_TOKEN    细粒度 PAT，仅需对目标仓库 Contents: Read and write
 *   GITHUB_OWNER    默认 xgocgo
 *   GITHUB_REPO     默认 FreePlayToolBox
 *   GITHUB_BRANCH   默认 master
 *   GITHUB_PATH     默认 announcements.json
 *
 * 路由：
 *   GET  /                     → 发布后台
 *   GET  /api/announcements    → 读取当前公告（从 GitHub）
 *   POST /api/announcements    → 保存公告（写 GitHub，需 X-Admin-Key）
 */

const CORS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type, X-Admin-Key',
};

function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store', ...CORS },
  });
}

function b64encode(str) {
  const bytes = new TextEncoder().encode(str);
  let bin = '';
  for (const b of bytes) bin += String.fromCharCode(b);
  return btoa(bin);
}

function b64decode(b64) {
  const bin = atob(String(b64).replace(/\s/g, ''));
  const bytes = Uint8Array.from(bin, (c) => c.charCodeAt(0));
  return new TextDecoder().decode(bytes);
}

function cfg(env) {
  return {
    owner: env.GITHUB_OWNER || 'xgocgo',
    repo: env.GITHUB_REPO || 'FreePlayToolBox',
    branch: env.GITHUB_BRANCH || 'master',
    path: env.GITHUB_PATH || 'announcements.json',
  };
}

async function ghGet(env) {
  const c = cfg(env);
  const url = `https://api.github.com/repos/${c.owner}/${c.repo}/contents/${c.path}?ref=${c.branch}`;
  const headers = {
    Accept: 'application/vnd.github+json',
    'User-Agent': 'FreePlayToolBox-Worker',
  };
  if (env.GITHUB_TOKEN) headers.Authorization = `Bearer ${env.GITHUB_TOKEN}`;
  const r = await fetch(url, { headers, cache: 'no-store' });
  if (!r.ok) {
    return { ok: false, status: r.status, error: `GitHub 读取失败 (${r.status})`, sha: null, items: [] };
  }
  const d = await r.json();
  let items = [];
  try {
    const parsed = JSON.parse(b64decode(d.content || ''));
    if (Array.isArray(parsed.items)) items = parsed.items;
  } catch {
    items = [];
  }
  return { ok: true, status: 200, sha: d.sha, items };
}

async function ghPut(env, items, sha) {
  const c = cfg(env);
  if (!env.GITHUB_TOKEN) {
    return { ok: false, status: 500, error: '未配置 GITHUB_TOKEN，无法写入' };
  }
  const body = JSON.stringify({ items }, null, 2);
  const url = `https://api.github.com/repos/${c.owner}/${c.repo}/contents/${c.path}`;
  const payload = {
    message: `公告更新 ${new Date().toISOString()}`,
    content: b64encode(body + '\n'),
    branch: c.branch,
  };
  if (sha) payload.sha = sha;

  const r = await fetch(url, {
    method: 'PUT',
    headers: {
      Accept: 'application/vnd.github+json',
      Authorization: `Bearer ${env.GITHUB_TOKEN}`,
      'User-Agent': 'FreePlayToolBox-Worker',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
  });
  if (!r.ok) {
    const t = await r.text();
    return { ok: false, status: r.status, error: `GitHub 写入失败 (${r.status}): ${t.slice(0, 200)}` };
  }
  return { ok: true, status: 200 };
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (request.method === 'OPTIONS') return new Response(null, { headers: CORS });

    if (url.pathname === '/api/announcements') {
      if (request.method === 'GET') {
        const r = await ghGet(env);
        return json(
          { items: r.items, source: 'github', updatedAt: Date.now(), error: r.error },
          r.ok ? 200 : 500,
        );
      }
      if (request.method === 'POST') {
        const key = request.headers.get('X-Admin-Key') || '';
        if (!env.ADMIN_KEY || key !== env.ADMIN_KEY) {
          return json({ ok: false, error: '发布口令错误' }, 401);
        }
        let body;
        try {
          body = await request.json();
        } catch {
          return json({ ok: false, error: '请求体不是合法 JSON' }, 400);
        }
        const items = Array.isArray(body.items) ? body.items : [];
        const cur = await ghGet(env);
        const w = await ghPut(env, items, cur.sha);
        if (!w.ok) return json({ ok: false, error: w.error }, w.status || 500);
        return json({ ok: true, count: items.length });
      }
      return json({ ok: false, error: 'method not allowed' }, 405);
    }

    if (url.pathname === '/' || url.pathname === '/admin') {
      return new Response(ADMIN_HTML, {
        headers: { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store' },
      });
    }

    return new Response('Not Found', { status: 404 });
  },
};

const ADMIN_HTML = `<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>玩机工具箱 · 公告后台</title>
<style>
  :root{--bg:#0e0e10;--surface:#1a1a1f;--surface2:#242430;--line:#2c2c33;
    --text:#eaeadf;--muted:#9a9aa2;--accent:#69d49a;--red:#ff6b6b;--orange:#ffb86b}
  *{box-sizing:border-box;-webkit-tap-highlight-color:transparent}
  body{margin:0 auto;max-width:820px;padding:16px;background:var(--bg);color:var(--text);
    font:15px/1.6 -apple-system,BlinkMacSystemFont,"PingFang SC","Microsoft YaHei",sans-serif}
  h1{font-size:22px;margin:6px 0 2px}
  .sub{color:var(--muted);font-size:12px;margin-bottom:18px}
  .card{background:var(--surface);border-radius:22px;padding:18px;margin-bottom:12px;
    border:1px solid transparent;transition:border-color .2s}
  .card:focus-within{border-color:rgba(105,212,154,.4)}
  label{display:block;font-size:12px;color:var(--muted);margin:12px 0 6px}
  label:first-child{margin-top:0}
  input,textarea,select{width:100%;background:var(--surface2);color:var(--text);
    border:1px solid var(--line);border-radius:14px;padding:12px 14px;font:inherit;outline:none}
  input:focus,textarea:focus,select:focus{border-color:var(--accent)}
  textarea{min-height:96px;resize:vertical}
  .row{display:flex;gap:10px;flex-wrap:wrap}.row>*{flex:1;min-width:120px}
  button{background:var(--accent);color:#00391c;border:0;border-radius:14px;padding:13px 20px;
    font-size:15px;font-weight:600;cursor:pointer}
  button:active{opacity:.75}
  button.ghost{background:var(--surface2);color:var(--text);border:1px solid var(--line)}
  button.danger{background:transparent;color:var(--red);border:1px solid rgba(255,107,107,.35)}
  .actions{display:flex;gap:10px;margin-top:14px;flex-wrap:wrap}
  .item{background:var(--surface);border-radius:18px;padding:14px 16px;margin-bottom:10px;
    display:flex;gap:12px;align-items:flex-start}
  .dot{width:34px;height:34px;border-radius:11px;flex:0 0 auto;display:flex;align-items:center;
    justify-content:center;font-size:13px;font-weight:700}
  .dot.info{background:rgba(105,212,154,.16);color:var(--accent)}
  .dot.warn{background:rgba(255,184,107,.16);color:var(--orange)}
  .dot.important{background:rgba(255,107,107,.16);color:var(--red)}
  .item .body{flex:1;min-width:0}
  .item h3{margin:0;font-size:15px}
  .item p{margin:3px 0 0;font-size:12px;color:var(--muted);white-space:pre-wrap;
    word-break:break-word;max-height:80px;overflow:hidden}
  .item .id{font-size:10px;color:#666;margin-top:4px;font-family:ui-monospace,monospace}
  .empty{color:var(--muted);text-align:center;padding:26px;font-size:13px}
  .toast{position:fixed;left:50%;bottom:24px;transform:translateX(-50%) translateY(20px);
    background:var(--surface2);padding:12px 22px;border-radius:14px;font-size:13px;opacity:0;
    pointer-events:none;transition:.25s;border:1px solid var(--line);z-index:99}
  .toast.show{opacity:1;transform:translateX(-50%) translateY(0)}
  .toast.err{border-color:rgba(255,107,107,.5);color:#ffb3b3}
  .hint{font-size:11px;color:var(--muted);margin-top:6px}
  .bar{height:3px;background:var(--line);border-radius:2px;margin:14px 0;overflow:hidden}
  .bar i{display:block;height:100%;width:0;background:var(--accent);transition:width .3s}
  .imgprev{width:100%;max-height:120px;object-fit:cover;border-radius:12px;margin-top:8px;display:none}
</style>
</head>
<body>
<h1>公告后台</h1>
<div class="sub">发布后写入 GitHub，用户打开 App 即可看到</div>

<div class="card">
  <label>发布口令</label>
  <input id="key" type="password" placeholder="ADMIN_KEY">
  <div class="hint">仅保存在本机浏览器</div>
</div>

<div class="card">
  <label>标题</label>
  <input id="title" placeholder="例如：v0.12.0 已发布">
  <label>正文</label>
  <textarea id="body" placeholder="写点什么…"></textarea>
  <div class="row">
    <div><label>等级</label>
      <select id="level"><option value="info">普通</option><option value="warn">提醒</option>
      <option value="important">重要</option></select></div>
    <div><label>ID（留空自动）</label><input id="id" placeholder="自动生成"></div>
  </div>
  <label>图片链接（可选）</label>
  <input id="image" placeholder="https://…" oninput="preview()">
  <img id="imgprev" class="imgprev">
  <div class="actions"><button onclick="addItem()">添加到列表</button></div>
</div>

<div class="card">
  <label style="margin-top:0">当前公告（<span id="count">0</span> 条）</label>
  <div id="list"></div>
  <div class="actions">
    <button onclick="publish()">发布到线上</button>
    <button class="ghost" onclick="load()">从 GitHub 刷新</button>
  </div>
  <div class="bar"><i id="prog"></i></div>
  <div class="hint">发布 = 写入仓库 announcements.json，约 1~2 秒生效</div>
</div>

<div class="toast" id="toast"></div>
<script>
let items=[];
const $=id=>document.getElementById(id);
function toast(m,e){const t=$('toast');t.textContent=m;t.className='toast show'+(e?' err':'');
  clearTimeout(t._h);t._h=setTimeout(()=>t.className='toast',2800);}
function preview(){const u=$('image').value.trim(),i=$('imgprev');
  if(!u){i.style.display='none';return;} i.src=u;i.style.display='block';i.onerror=()=>i.style.display='none';}
function newId(){const d=new Date(),p=n=>String(n).padStart(2,'0');
  return 'ann-'+d.getFullYear()+p(d.getMonth()+1)+p(d.getDate())+'-'+p(d.getHours())+p(d.getMinutes())+p(d.getSeconds());}
function addItem(){
  const t=$('title').value.trim(),b=$('body').value.trim();
  if(!t&&!b){toast('标题和正文至少填一个',true);return;}
  items.unshift({id:($('id').value.trim()||newId()),title:t||'公告',body:b,
    level:$('level').value,image:$('image').value.trim()});
  $('title').value='';$('body').value='';$('id').value='';$('image').value='';
  $('imgprev').style.display='none';render();toast('已加入列表，记得点「发布到线上」');}
function removeItem(i){items.splice(i,1);render();}
function esc(s){return String(s||'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));}
function render(){
  $('count').textContent=items.length;
  const box=$('list');
  if(!items.length){box.innerHTML='<div class="empty">还没有公告</div>';return;}
  box.innerHTML=items.map((a,i)=>'<div class="item"><div class="dot '+(a.level||'info')+'">'+(i+1)+
    '</div><div class="body"><h3>'+esc(a.title)+'</h3>'+
    (a.image?'<div style="font-size:10px;color:#69d49a">含图片</div>':'')+
    '<p>'+esc(a.body||'')+'</p><div class="id">'+esc(a.id)+'</div></div>'+
    '<button class="danger" onclick="removeItem('+i+')">删除</button></div>').join('');}
async function load(){
  try{const r=await fetch('/api/announcements',{cache:'no-store'});const d=await r.json();
    items=d.items||[];render();
    if(d.error) toast(d.error,true); else toast('已从 GitHub 刷新');}
  catch(e){toast('刷新失败：'+e.message,true);}}
async function publish(){
  const k=$('key').value.trim();
  if(!k){toast('先填发布口令',true);return;}
  $('prog').style.width='40%';
  try{const r=await fetch('/api/announcements',{method:'POST',
      headers:{'Content-Type':'application/json','X-Admin-Key':k},
      body:JSON.stringify({items})});
    $('prog').style.width='100%';const d=await r.json();
    if(d.ok){localStorage.setItem('ann_key',k);toast('已发布 '+d.count+' 条，秒级生效');}
    else toast('发布失败：'+(d.error||'未知'),true);
  }catch(e){toast('发布失败：'+e.message,true);}
  setTimeout(()=>$('prog').style.width='0',900);}
$('key').value=localStorage.getItem('ann_key')||'';
load();
</script>
</body>
</html>`;
