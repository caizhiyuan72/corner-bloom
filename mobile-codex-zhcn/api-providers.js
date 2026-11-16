/* Multi-provider API settings: local Android bridge only; no external scripts. */
(() => {
  'use strict';
  let call, refresh, toast, profiles=[], active='', editing='', installed=false;
  const $=id=>document.getElementById(id);
  const el=(tag,text,cls)=>{const n=document.createElement(tag);if(text!==undefined)n.textContent=text;if(cls)n.className=cls;return n;};
  const status=(msg,err=false)=>{const n=$('api-status');n.textContent=msg||'';n.style.color=err?'#bd3030':'';};
  function clear(){
    editing='';$('api-form').reset();$('api-key').required=true;
    $('api-editor-heading').textContent='添加服务商';
    $('api-key-note').textContent='密钥使用 Android Keystore 加密保存，不会再次显示。';
    $('api-cancel').hidden=true;
  }
  function edit(p) {
    editing=p.id;$('api-name').value=p.name;$('api-url').value=p.baseUrl;
    $('api-models').value=p.models.join('\n');$('api-key').value='';
    $('api-key').required=false;
    $('api-key-note').textContent='留空保留旧密钥，填写则替换。';
    $('api-cancel').hidden=false;$('api-editor-heading').textContent='编辑 · '+p.name;
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
      <label for="api-models">模型 ID（每行一个）</label><textarea class="text-input" id="api-models" rows="3" required spellcheck="false" autocapitalize="off" placeholder="deepseek-flash"></textarea>
      <label for="api-key">API Key</label><input class="text-input" id="api-key" type="password" required maxlength="4096" autocomplete="new-password">
      <small class="muted" id="api-key-note">密钥使用 Android Keystore 加密保存，不会再次显示。</small>
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
      .api-form textarea{min-height:80px;resize:vertical}`;
    document.head.append(css);
    $('api-form').addEventListener('submit',async(e)=>{
      e.preventDefault();const b=$('api-save');b.disabled=true;
      const params={id:editing,name:$('api-name').value.trim(),baseUrl:$('api-url').value.trim(),
        models:$('api-models').value,apiKey:$('api-key').value};
      try{await action('providers.save',params);clear();}
      finally{$('api-key').value='';b.disabled=false;}
    });
    $('api-cancel').addEventListener('click',clear);
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