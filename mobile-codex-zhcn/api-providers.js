/* Multi-provider API settings: local Android bridge only; no external scripts. */
(() => {
  'use strict';
  let call, refresh, toast, profiles=[], active='', editing='', installed=false, discovered=[], selected=new Set(), scanBusy=false;
  const $=id=>document.getElementById(id);
  const el=(tag,text,cls)=>{const n=document.createElement(tag);if(text!==undefined)n.textContent=text;if(cls)n.className=cls;return n;};
  const status=(msg,err=false)=>{const n=$('api-status');n.textContent=msg||'';n.style.color=err?'#bd3030':'';};
  function clear(){
    editing='';$('api-form').reset();$('api-key').required=true;
    $('api-editor-heading').textContent='添加服务商';
    $('api-key-note').textContent='密钥使用 Android Keystore 加密保存，不会再次显示。';
    $('api-cancel').hidden=true;
    resetDiscovery();
  }
  function edit(p) {
    editing=p.id;$('api-name').value=p.name;$('api-url').value=p.baseUrl;
    $('api-models').value=p.models.join('\n');$('api-key').value='';
    $('api-key').required=false;
    $('api-key-note').textContent='留空保留旧密钥，填写则替换。';
    $('api-cancel').hidden=false;$('api-editor-heading').textContent='编辑 · '+p.name;
    resetDiscovery();
    $('api-form').scrollIntoView({block:'nearest'});
  }
  async function action(type,params) {
    status('正在保存…');
    try { await call(type,params);await refresh();status('操作完成。切换服务商后请新建对话。');}
    catch(e){status(e.message,true);toast(e.message);}
  }
  const btn=(text,fn,disabled=false)=>{
    const b=el('button',text,'secondary-button');b.type='button';b.disabled=disabled;
    b.addEventListener('click',async()=>{b.disabled=true;try{await fn();}finally{b.disabled=false;}});
    return b;
  };

  function resetDiscovery() {
    discovered=[];selected.clear();
    const panel=$('api-discovery');if(panel){panel.hidden=true;$('api-discovery-results').replaceChildren();}
  }
  function renderDiscovery(){
    const panel=$('api-discovery'),box=$('api-discovery-results');
    if(!panel||!box)return;
    panel.hidden=false;box.replaceChildren();
    const q=$('api-filter').value.toLowerCase().trim();
    const matching=discovered.filter(id=>id.toLowerCase().includes(q));
    $('api-discovery-count').textContent='找到 '+discovered.length+' 个模型 · 已选 '+selected.size+' 个（最多 150）';
    for(const id of matching){
      const row=el('label',undefined,'api-model-row');
      const cb=el('input');cb.type='checkbox';cb.checked=selected.has(id);
      cb.addEventListener('change',()=>{
        if(cb.checked&&selected.size>=150){cb.checked=false;status('最多可添加 150 个模型',true);return;}
        if(cb.checked)selected.add(id);else selected.delete(id);
        $('api-discovery-count').textContent='找到 '+discovered.length+' 个模型 · 已选 '+selected.size+' 个（最多 150）';
      });
      const name=el('span',id);name.style.overflowWrap='anywhere';row.append(cb,name);box.append(row);
    }
    if(!matching.length)box.append(el('p','没有匹配的模型','muted'));
  }
  async function scan(){
    if(scanBusy)return;
    const baseUrl=$('api-url').value.trim(),apiKey=$('api-key').value.trim();
    if(!baseUrl){status('请先填写 API Base URL',true);return;}
    if(!apiKey&&!editing){status('请先填写 API Key',true);return;}
    scanBusy=true;$('api-scan').disabled=true;status('正在连接服务商并读取模型列表…');
    try{
      const data=await call('providers.discover',{baseUrl,apiKey,id:editing});
      discovered=Array.isArray(data.models)?data.models:[];
      const existing=new Set($('api-models').value.split(/[\r\n,]/).map(x=>x.trim()));
      selected=new Set(discovered.filter(id=>existing.has(id)));
      $('api-filter').value='';renderDiscovery();
      status('已找到 '+discovered.length+' 个模型，请勾选后点击“添加选中模型”。');
    }catch(e){resetDiscovery();status(e.message||'扫描失败，请手动填写模型 ID',true);}
    finally{scanBusy=false;$('api-scan').disabled=false;}
  }
  function addSelected(){
    if(!selected.size){status('请先勾选模型',true);return;}
    const old=$('api-models').value.split(/[\r\n,]/).map(x=>x.trim()).filter(Boolean);
    const names=[...new Set([...old,...selected])];
    if(names.length>150){status('最多可保存 150 个模型',true);return;}
    $('api-models').value=names.join('\n');
    status('已添加 '+selected.size+' 个模型，点击下方“保存服务商”完成保存。');
  }
  function draw() {
    if(!installed)return;
    const list=$('api-profiles');list.replaceChildren();
    const c=el('div',undefined,'api-card');c.append(el('strong','ChatGPT · Codex 订阅'));
    c.append(btn(active?'切回 ChatGPT':'当前默认',()=>action('providers.select',{id:''}),!active));list.append(c);
    for(const p of profiles) {
      const card=el('div',undefined,'api-card');
      card.append(el('strong',p.name+(active===p.id?' · 使用中':'')));
      card.append(el('p',p.baseUrl+' · '+p.models.join('、'),'api-meta'));
      card.append(el('p',p.hasKey?'API Key：已加密保存':'API Key：未设置','api-meta'));
      const actions=el('div',undefined,'api-buttons');
      actions.append(btn(active===p.id?'当前使用':'启用',()=>action('providers.select',{id:p.id}),active===p.id));
      actions.append(btn('编辑',()=>edit(p)));
      actions.append(btn('删除',async()=>{
        if(confirm('删除“'+p.name+'”并清除其 API Key？')){await action('providers.remove',{id:p.id});clear();}
      }));
      card.append(actions);list.append(card);
    }
    if(!profiles.length)list.append(el('p','还没有添加第三方 API 服务商。','muted'));
    $('api-current').textContent=active?'当前使用第三方 API，按服务商收费。':'当前使用 ChatGPT 登录的 Codex 额度。';
  }
  function init(opt) {
    if(installed)return;
    call=opt.call;refresh=opt.refresh;toast=opt.toast;
    const nav=document.querySelector('.settings-nav');
    const tab=el('button','模型 API');tab.type='button';tab.dataset.settingsTab='providers';
    nav.insertBefore(tab,nav.querySelector('[data-settings-tab="advanced"]'));
    const sec=el('section',undefined,'settings-section');sec.dataset.settingsPanel='providers';sec.hidden=true;
    sec.innerHTML=`<h3>模型 API 管理</h3>
      <p class="muted">添加 DeepSeek、七牛云等服务商；仅支持 HTTPS 的 OpenAI Responses API。Chat Completions-only 接口暂不兼容。切换时会重启运行时并开始新对话。</p>
      <p class="muted" id="api-current"></p>
      <div id="api-profiles"></div>
      <form id="api-form" class="api-form" autocomplete="off">
      <h4 id="api-editor-heading">添加服务商</h4>
      <label for="api-name">服务商名称</label><input class="text-input" id="api-name" maxlength="60" required placeholder="例如 DeepSeek / 七牛云">
      <label for="api-url">API Base URL（HTTPS）</label><input class="text-input" id="api-url" type="url" maxlength="512" required autocapitalize="off" spellcheck="false" placeholder="https://api.deepseek.com">
      <label for="api-models">模型 ID（每行一个，支持自动扫描）</label><textarea class="text-input" id="api-models" rows="3" required spellcheck="false" autocapitalize="off" placeholder="deepseek-flash"></textarea>
      <label for="api-key">API Key</label><input class="text-input" id="api-key" type="password" required maxlength="4096" autocomplete="new-password">
      <small class="muted" id="api-key-note">密钥使用 Android Keystore 加密保存，不会再次显示。</small>
      <button class="secondary-button" type="button" id="api-scan">扫描模型（自动获取）</button>
      <div class="api-discovery" id="api-discovery" hidden>
        <label for="api-filter">搜索模型</label><input id="api-filter" class="text-input" placeholder="搜索模型 ID">
        <small class="muted" id="api-discovery-count"></small>
        <div class="api-buttons"><button class="secondary-button" type="button" id="api-select-all">全选筛选结果</button><button class="secondary-button" type="button" id="api-select-none">取消选择</button></div>
        <div class="api-discovery-results" id="api-discovery-results" role="group" aria-label="扫描到的模型"></div>
        <button class="primary-button" type="button" id="api-add-selected">添加选中模型</button>
      </div>
      <p class="muted">保存不代表连接测试通过。服务商需兼容 /responses、流式输出和工具调用。</p>
      <div class="api-buttons"><button class="primary-button" type="submit" id="api-save">保存服务商</button><button class="secondary-button" type="button" id="api-cancel" hidden>取消编辑</button></div>
      <p id="api-status" class="muted" role="status" aria-live="polite"></p>
      </form>`;
    document.querySelector('.settings-content').append(sec);
    const css=el('style');css.textContent=`
      .api-card{border:1px solid var(--border,#ddd);padding:13px;border-radius:12px;margin:12px 0;overflow-wrap:anywhere}
      .api-meta{font-size:12px;color:var(--text-secondary,#777);margin:8px 0}
      .api-buttons{display:flex;flex-wrap:wrap;gap:7px;margin:8px 0}
      .api-buttons button{min-height:39px}
      .api-form{display:flex;flex-direction:column;gap:9px;margin:18px 0}
      .api-form label{font-weight:600;font-size:13px}
      .api-form input,.api-form textarea{width:100%;box-sizing:border-box;min-height:40px}
      .api-form textarea{min-height:80px;resize:vertical}
      .api-discovery{border:1px solid var(--border,#ddd);padding:10px;border-radius:12px;display:flex;flex-direction:column;gap:8px}
      .api-discovery[hidden]{display:none}
      .api-discovery-results{max-height:300px;overflow-y:auto;border:1px solid var(--border,#ddd);border-radius:8px}
      .api-model-row{display:flex;align-items:center;gap:9px;padding:8px 5px;cursor:pointer;font-size:13px}
      .api-model-row input{min-width:18px;min-height:18px}`;
    document.head.append(css);
    $('api-form').addEventListener('submit',async(e)=>{
      e.preventDefault();const b=$('api-save');b.disabled=true;
      const params={id:editing,name:$('api-name').value.trim(),baseUrl:$('api-url').value.trim(),
        models:$('api-models').value,apiKey:$('api-key').value};
      try{await action('providers.save',params);clear();}
      finally{$('api-key').value='';b.disabled=false;}
    });
    $('api-cancel').addEventListener('click',clear);
    $('api-scan').addEventListener('click',scan);
    $('api-filter').addEventListener('input',renderDiscovery);
    $('api-select-none').addEventListener('click',()=>{selected.clear();renderDiscovery();});
    $('api-select-all').addEventListener('click',()=>{
      const q=$('api-filter').value.toLowerCase().trim();
      for(const id of discovered.filter(x=>x.toLowerCase().includes(q))){
        if(selected.size>=150)break;selected.add(id);
      }
      renderDiscovery();
    });
    $('api-add-selected').addEventListener('click',addSelected);
    installed=true;clear();draw();
  }
  function render(state){
    if(!installed)return;
    profiles=Array.isArray(state.apiProviders)?state.apiProviders:[];
    active=typeof state.apiActive==='string'?state.apiActive:'';
    draw();
  }
  window.MobileCodexApiProviders={init,render};
})();