#!/usr/bin/env python3
"""Apply Mobile Codex multi-provider API support AFTER apply_zhcn.py --parallel.
Requires a clean v0.3.9 upstream checkout and prior Chinese patch.
"""
from pathlib import Path
import sys

def replace_once(text, old, new, name):
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{name}: expected exactly one anchor ({count} found): {old[:100]}")
    return text.replace(old,new,1)

def main(root):
    src=Path(root)
    patch=Path(__file__).resolve().parent
    store=patch/"ApiProviderStore.java"
    ui=patch/"api-providers.js"
    assert store.is_file() and ui.is_file()
    engine=src/"app/src/main/java/dev/mobilecodex/app/Engine.java"
    html=src/"app/src/main/assets/web/index.html"
    appjs=src/"app/src/main/assets/web/app.js"
    activity=src/"app/src/main/java/dev/mobilecodex/app/MainActivity.java"
    data={}
    s=engine.read_text()
    s=replace_once(s,
      "private JSONArray models = new JSONArray();",
      "private JSONArray models = new JSONArray();\n    private final ApiProviderStore apiProviders;",
      "Engine field")
    s=replace_once(s,
      "this.context = context;\n",
      "this.context = context;\n        apiProviders = new ApiProviderStore(context);\n",
      "Engine constructor")
    s=replace_once(s,
      '"accounts", accountProfiles.list(), "rateLimits", rateLimits, "models", models,',
      '"accounts", accountProfiles.list(), "rateLimits", rateLimits, "models", models, "apiProviders", apiProfilesForUi(), "apiActive", apiProviders.activeId(),',
      "Engine snapshot")
    s=replace_once(s,
      '    /** Return the most recent messages in their original chronological order. */',
      '    private JSONArray apiProfilesForUi() {\n'
      '        try { return apiProviders.publicProfiles(); }\n'
      '        catch (Exception ignored) { return new JSONArray(); }\n'
      '    }\n'
      '    /** Return the most recent messages in their original chronological order. */',
      "Engine profile projection")
    s=replace_once(s,
      'case "models.refresh" -> {\n                         if (!ready) start();',
      'case "providers.save" -> { ensureEngineIdle(); apiProviders.save(args); publish(); reply.complete(snapshot(), null); }\n'
      '                    case "providers.remove" -> {\n'
      '                        ensureEngineIdle(); boolean wasActive = args.optString("id").equals(apiProviders.activeId());\n'
      '                        if (wasActive) stopCodex();\n'
      '                        apiProviders.remove(args.getString("id"));\n'
      '                        if (wasActive) { clearActive(); start(false); }\n'
      '                        publish(); reply.complete(snapshot(), null);\n'
      '                    }\n'
      '                    case "providers.select" -> {\n'
      '                        ensureEngineIdle(); String chosen = args.optString("id", "");\n'
      '                        if (!chosen.equals(apiProviders.activeId())) {\n'
      '                            String previous = apiProviders.activeId();\n'
      '                            stopCodex(); apiProviders.select(chosen); clearActive();\n'
      '                            try { start(false); }\n'
      '                            catch (Exception failure) {\n'
      '                                apiProviders.select(previous); clearActive();\n'
      '                                try { start(false); } catch (Exception ignored) {}\n'
      '                                throw failure;\n'
      '                            }\n'
      '                        }\n'
      '                        publish(); reply.complete(snapshot(), null);\n'
      '                    }\n'
      '                    case "models.refresh" -> {\n                         if (!ready) start();\n'
      '                         if (apiProviders.isActive()) { models = apiProviders.models(); publish(); reply.complete(obj("models", models), null); break; }',
      "Engine actions")
    # prevent authenticating against ChatGPT when custom endpoint is selected
    s=replace_once(s,
      'private void readAccount() throws Exception {\n',
      'private void readAccount() throws Exception {\n'
      '        if (apiProviders.isActive()) {\n'
      '            account = obj("type", "api_key"); authState = "signed_in"; authError = "";\n'
      '            lastAccountCheckElapsed = SystemClock.elapsedRealtime(); publish(); return;\n'
      '        }\n',
      "Engine account")
    s=replace_once(s,
      'private void readRateLimits() throws Exception {\n',
      'private void readRateLimits() throws Exception {\n'
      '        if (apiProviders.isActive()) { rateLimits = new JSONObject(); return; }\n',
      "Engine usage")
    s=replace_once(s,
      'if (!accountProfiles.hasSavedLogin()) authState = "signed_out";',
      'if (!accountProfiles.hasSavedLogin() && !apiProviders.isActive()) authState = "signed_out";',
      "Engine default auth state")
    s=replace_once(s,
      'if (!ready && !accountProfiles.hasSavedLogin()) {',
      'if (!ready && !accountProfiles.hasSavedLogin() && !apiProviders.isActive()) {',
      "Engine login restore")
    s=replace_once(s,
      'builder.environment().put("CODEX_SELF_EXE", binary.getAbsolutePath());',
      'builder.environment().put("CODEX_SELF_EXE", binary.getAbsolutePath());\n'
      '            apiProviders.configure(builder);',
      "Engine launch")
    s=replace_once(s,
      'if (foregroundRuntime) try { models = call("model/list", obj("limit", 100, "includeHidden", false)).optJSONArray("data"); }',
      'if (apiProviders.isActive()) models = apiProviders.models();\n'
      '            else if (foregroundRuntime) try { models = call("model/list", obj("limit", 100, "includeHidden", false)).optJSONArray("data"); }',
      "Engine initial models")
    s=replace_once(s,
      '        try { models = call("model/list", obj("limit", 100, "includeHidden", false)).optJSONArray("data"); }',
      '        try { models = apiProviders.isActive() ? apiProviders.models() : call("model/list", obj("limit", 100, "includeHidden", false)).optJSONArray("data"); }',
      "Engine promoted models")
    s=replace_once(s,
      'try { JSONArray data = call("model/list", obj("limit", 100)).optJSONArray("data"); if (data != null) models = data; }',
      'try { JSONArray data = apiProviders.isActive() ? apiProviders.models() : call("model/list", obj("limit", 100)).optJSONArray("data"); if (data != null) models = data; }',
      "Engine refreshed models")
    data[engine]=s
    s=html.read_text()
    s=replace_once(s,
      '  <script src="app.js" defer></script>',
      '  <script src="api-providers.js" defer></script>\n  <script src="app.js" defer></script>',
      "HTML API module")
    data[html]=s
    s=appjs.read_text()
    s=replace_once(s,
      "let agentUi = null;",
      "let agentUi = null;\n  let apiProvidersUi = window.MobileCodexApiProviders;",
      "App UI extension field")
    s=replace_once(s,
      "setDraftScope(next); state = historySnapshot(next, changedThread);",
      "setDraftScope(next); state = historySnapshot(next, changedThread);\n    apiProvidersUi?.render(state);",
      "App snapshot provider render")
    s=replace_once(s,
      "selectSettingsTab('general');",
      "apiProvidersUi?.init({call, refresh:()=>call('state').then(render), toast});\n  selectSettingsTab('general');",
      "App provider UI init")
    data[appjs]=s
    s=activity.read_text()
    s=replace_once(s,
      'path.equals("/translations-zh.js") || path.equals("/locale.js")',
      'path.equals("/translations-zh.js") || path.equals("/api-providers.js") || path.equals("/locale.js")',
      "WebView secured allowlist")
    data[activity]=s
    # validate every patch anchor before touching any file
    for p,s in data.items():
        p.write_text(s,encoding="utf-8")
    (src/"app/src/main/java/dev/mobilecodex/app/ApiProviderStore.java").write_bytes(store.read_bytes())
    (src/"app/src/main/assets/web/api-providers.js").write_bytes(ui.read_bytes())
    print("Applied multi-API provider integration, WebView allowlist, encrypted key storage and UI")

if __name__=="__main__":
    if len(sys.argv)!=2:
        sys.exit("Usage: apply_api_providers.py /path/to/patched/mobile-codex")
    main(sys.argv[1])
