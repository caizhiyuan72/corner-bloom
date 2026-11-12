#!/usr/bin/env python3
"""Add a complete Simplified Chinese UI catalog to Mobile Codex v0.3.9.

Usage: python3 apply_zhcn.py /path/to/mobile-codex
No third-party Python dependencies; does not access the network.
"""
from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

SOURCE_EN_BLOB = "920d5affe06b15909f56843efa7390ab02206721"
SOURCE_FILES = {
    "app/src/main/java/dev/mobilecodex/app/AppLanguage.java": "0296291758545ac336d760a35f48b57a99998f82",
    "app/src/main/java/dev/mobilecodex/app/core/Texts.java": "1f16070a0d19871aafa5613637c78623fd3f31ed",
    "app/src/main/assets/web/locale.js": "9a79e4a57107291c36ace38eaeb0417d582d1fe9",
    "app/src/main/assets/web/index.html": "ac3887b33acf1d02b28de4a2d452cd057b186795",
    "app/src/main/assets/web/app.js": "080cad941ab2c8f2df8b12cf967835ca68a0750e",
    "app/src/test/java/dev/mobilecodex/app/AppLanguageTest.java": "cfe7b18e16b3caa7a59c4eb89ffafe03ffde77f7",
    "docs/localization.md": "76281ce9fdc208d27fc395bd110c0366616a1c32",
}
PLACEHOLDER = re.compile(r"\{([A-Za-z][A-Za-z0-9_]*)\}")

APP_LANGUAGE = '''package dev.mobilecodex.app;

import android.content.Context;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.util.*;
import dev.mobilecodex.app.core.Texts;
import static dev.mobilecodex.app.core.Json.*;

final class AppLanguage {
    private static Map<String, String> english = Map.of();
    private static Map<String, String> chinese = Map.of();

    private static Map<String, String> load(Context context, String asset) {
        try (var stream = context.getAssets().open(asset)) {
            var bytes = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192]; int count;
            while ((count = stream.read(buffer)) != -1) bytes.write(buffer, 0, count);
            JSONObject json = new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));
            var values = new HashMap<String, String>();
            for (var keys = json.keys(); keys.hasNext();) {
                String key = keys.next(); values.put(key, json.getString(key));
            }
            return Map.copyOf(values);
        } catch (Exception failure) {
            throw new IllegalStateException("Unable to load bundled translations: " + asset, failure);
        }
    }

    static void initialize(Context context) {
        english = load(context, "translations-en.json");
        chinese = load(context, "translations-zh-CN.json");
        configure(context);
    }

    static String choice(Context context) {
        return context.getSharedPreferences("appearance", 0).getString("language", "system");
    }
    static Locale locale(Context context) {
        String choice = choice(context);
        return choice.equals("system")
            ? context.getResources().getConfiguration().getLocales().get(0)
            : Locale.forLanguageTag(choice);
    }
    static void configure(Context context) {
        Texts.configure(locale(context).toLanguageTag(), english, chinese);
    }
    static JSONObject snapshot(Context context) {
        return obj("choice", choice(context), "systemLanguage",
            context.getResources().getConfiguration().getLocales().get(0).toLanguageTag());
    }
    @android.annotation.SuppressLint("ApplySharedPref")
    static void set(Context context, String value) throws java.io.IOException {
        if (!Set.of("system", "en", "ko", "zh-CN").contains(value))
            throw new IllegalArgumentException("Unsupported language");
        if (!context.getSharedPreferences("appearance", 0).edit().putString("language", value).commit())
            throw new java.io.IOException("Unable to save language setting");
        configure(context);
        UsageWidgetProvider.updateAll(context);
    }
}
'''

TEXTS = '''package dev.mobilecodex.app.core;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Translate explicit UI/error labels only, never file, model, terminal, or user data. */
public final class Texts {
    private record Catalog(String language, Map<String, String> english,
            Map<String, String> chinese, Map<String, String> source) {}
    private static volatile Catalog catalog = new Catalog("ko", Map.of(), Map.of(), Map.of());
    private Texts() {}

    private static String language(String tag) {
        String value = (tag == null ? "" : tag).toLowerCase(Locale.ROOT).replace('_', '-');
        if (value.equals("ko") || value.startsWith("ko-")) return "ko";
        if (value.equals("zh") || value.equals("zh-cn") || value.equals("zh-sg")
                || value.startsWith("zh-hans")) return "zh-CN";
        return "en";
    }
    public static void configure(String tag, Map<String, String> english,
            Map<String, String> chinese) {
        var reverse = new HashMap<String, String>();
        english.forEach((key, value) -> reverse.putIfAbsent(value, key));
        catalog = new Catalog(language(tag), Map.copyOf(english),
            Map.copyOf(chinese), Map.copyOf(reverse));
    }
    /** Compatibility with pre-localization tests and callers. */
    public static void configure(boolean korean, Map<String, String> english) {
        configure(korean ? "ko" : "en", english, Map.of());
    }
    public static String t(String text) {
        if (text == null) return null;
        Catalog c = catalog;
        if (c.language.equals("ko")) return c.source.getOrDefault(text, text);
        if (c.language.equals("en")) return c.english.getOrDefault(text, text);
        String key = c.source.getOrDefault(text, text);
        return c.chinese.getOrDefault(key, c.english.getOrDefault(key, text));
    }
}
'''

WEB_LOCALE = '''/* Localize only packaged UI labels and explicit t() calls. Never user/model/file content. */
(() => {
  'use strict';
  const english = window.MobileCodexEnglish || {};
  const chinese = window.MobileCodexChinese || {};
  let native = {};
  try { native = JSON.parse(window.Native?.locale?.() || '{}'); } catch {}
  let choice = native.choice || localStorage.getItem('language') || 'system';
  const deviceLanguage = native.systemLanguage || navigator.language || 'en';
  const language = () => {
    const tag = (choice === 'system' ? deviceLanguage : choice).toLowerCase().replace('_','-');
    if (tag === 'ko' || tag.startsWith('ko-')) return 'ko';
    if (tag === 'zh' || tag === 'zh-cn' || tag === 'zh-sg' || tag.startsWith('zh-hans')) return 'zh-CN';
    return 'en';
  };
  const t = (key, args = {}) => {
    const lang = language();
    const value = lang === 'ko' ? key : lang === 'zh-CN'
      ? (chinese[key] ?? english[key] ?? key)
      : (english[key] ?? key);
    return value.replace(/\\{([a-zA-Z]+)\\}/g, (token, name) =>
      Object.hasOwn(args, name) ? String(args[name]) : token);
  };
  const bindings = [];
  const walk = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
  while (walk.nextNode()) {
    const node = walk.currentNode, raw = node.textContent, key = raw.trim();
    if (!Object.hasOwn(english, key)) continue;
    bindings.push({node, key, raw, last:raw});
  }
  for (const node of document.querySelectorAll('*')) {
    for (const attribute of ['aria-label', 'title', 'placeholder', 'data-prompt']) {
      const key = node.getAttribute(attribute);
      if (key !== null && Object.hasOwn(english, key))
        bindings.push({node, key, attribute, last:key});
    }
  }
  function apply() {
    document.documentElement.lang = language();
    for (const b of bindings) {
      if (!b.node.isConnected) continue;
      const current = b.attribute ? b.node.getAttribute(b.attribute) : b.node.textContent;
      // Dynamic values take ownership; never overwrite user content.
      if (current !== b.last) continue;
      const value = b.attribute ? t(b.key) : b.raw.replace(b.key, t(b.key));
      if (b.attribute) b.node.setAttribute(b.attribute, value); else b.node.textContent = value;
      b.last = value;
    }
  }
  window.MobileCodexLocale = {t, language, choice: () => choice, apply,
    set(value) {
      choice = ['system','en','ko','zh-CN'].includes(value) ? value : 'system';
      localStorage.setItem('language', choice);
      apply();
    }};
  apply();
})();
'''

PHONE_RES = '''<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="phone_use_label">Mobile Codex 手机控制</string>
    <string name="phone_use_description">当你在应用中开启手机控制后，Codex 可以将其他应用的屏幕内容和截图发送到 AI 服务，并通过点击、输入和滚动执行你的请求。屏幕信息可能保留在 Codex 对话历史中。你可以随时通过屏幕上的停止按钮关闭控制。仅授权无障碍权限并不会自动开启控制。</string>
</resources>
'''

WIDGET_RES = '''<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="codex_usage_widget">Codex 用量</string>
    <string name="claude_usage_widget">Claude 用量</string>
    <string name="gemini_usage_widget">Gemini 用量</string>
    <string name="usage_widget_description">剩余用量和重置时间</string>
    <string name="usage_widget_refresh">刷新用量</string>
</resources>
'''

DOC = '''# Mobile Codex 简体中文本地化（非官方补丁）

适用版本：原项目 `v0.3.9`。本补丁只更改 UI 字符串、语言选择器、中文 Android 资源、日期格式和测试；不会更改 Codex 的请求方式、登录、MCP、手机控制权限或模型服务。

中文翻译由补丁作者独立翻译，与项目作者和 OpenAI 无官方关联。版权所有与第三方声明沿用原项目 GPL-3.0-only、LICENSE、THIRD_PARTY_NOTICES.md。

- 系统为简体中文（zh-CN、zh-SG、zh-Hans）时自动使用中文。
- “设置 → 常规 → 语言”可手动选择“简体中文”；仍保留系统、英文、韩文。
- 1,257 条原始 UI 目录键均提供中文译文。动态内容、用户聊天记录、终端输出、文件内容不翻译。
- Android 资源仅为简体中文中国地区添加了无障碍与小组件文案。
- 自行签名的 APK 无法覆盖安装使用原开发者签名的 APK。卸载会删除应用私有数据，务必先备份。

请自行在 Android 设备上测试布局、输入法组合、长句换行、语言热切换及无障碍提示。未经设备测试，不应宣称已实现发布级质量。
'''

def git_blob_sha(data: bytes) -> str:
    return hashlib.sha1(b"blob " + str(len(data)).encode() + b"\0" + data).hexdigest()


def required_replace(text: str, old: str, new: str, path: str) -> str:
    if text.count(old) != 1:
        raise ValueError(f"{path}: expected 1 match of {old[:60]!r}, found {text.count(old)}")
    return text.replace(old, new, 1)


def load_chinese(package_dir: Path, expected: int) -> list[str]:
    rows = (package_dir / "zh_translations.tsv").read_text(encoding="utf-8").splitlines()
    if len(rows) != expected:
        raise ValueError(f"Expected {expected} translations, found {len(rows)}")
    translated = []
    for i, row in enumerate(rows, 1):
        index, separator, value = row.partition("|")
        if not separator or index != str(i) or not value:
            raise ValueError(f"Translation line {i}: index mismatch or blank value")
        translated.append(value.replace(r"\n", "\n").replace(r"\t", "\t"))
    return translated


def prepare(repo: Path, package_dir: Path, parallel_install: bool = False):
    read_bytes = lambda path: (repo / path).read_bytes()
    source_path = "app/src/main/assets/translations-en.json"
    raw = read_bytes(source_path)
    if git_blob_sha(raw) != SOURCE_EN_BLOB:
        raise ValueError("English catalog differs from v0.3.9; checkout tag v0.3.9 before applying")
    originals = {}
    for path, sha in SOURCE_FILES.items():
        b = read_bytes(path)
        if git_blob_sha(b) != sha:
            raise ValueError(f"Source differs from v0.3.9 or was already patched: {path}")
        originals[path] = b.decode("utf-8")
    english = json.loads(raw.decode("utf-8"))
    if not isinstance(english, dict):
        raise ValueError("Invalid English catalog")
    chinese = load_chinese(package_dir, len(english))
    catalog = dict(zip(english, chinese))
    for (key, en), zh in zip(english.items(), chinese):
        source_vars = Counter(PLACEHOLDER.findall(en))
        result_vars = Counter(PLACEHOLDER.findall(zh))
        if source_vars != result_vars:
            raise ValueError(f"Placeholder mismatch: {en[:60]!r} English={source_vars}, Chinese={result_vars}")
    if len(catalog) != 1257:
        raise ValueError(f"Unexpected translation count: {len(catalog)}")

    output = {}
    dest = "app/src/main/assets/translations-zh-CN.json"
    output[dest] = json.dumps(catalog, ensure_ascii=False, indent=2) + "\n"
    output["app/src/main/assets/web/translations-zh.js"] = (
        "/* Simplified Chinese UI messages. Do not translate user/model/file content. */\n"
        "window.MobileCodexChinese = "
        + json.dumps(catalog, ensure_ascii=False, indent=2) + ";\n"
    )
    output["app/src/main/assets/web/locale.js"] = WEB_LOCALE
    output["app/src/main/java/dev/mobilecodex/app/AppLanguage.java"] = APP_LANGUAGE
    output["app/src/main/java/dev/mobilecodex/app/core/Texts.java"] = TEXTS
    output["app/src/main/res/values-zh-rCN/phone_use_strings.xml"] = PHONE_RES
    output["app/src/main/res/values-zh-rCN/usage_widgets.xml"] = WIDGET_RES
    output["docs/localization-zh-CN.md"] = DOC

    html_path = "app/src/main/assets/web/index.html"
    html = originals[html_path]
    html = required_replace(html,
        '  <script src="translations.js" defer></script>\n',
        '  <script src="translations.js" defer></script>\n  <script src="translations-zh.js" defer></script>\n', html_path)
    html = required_replace(html,
        '<option value="ko">한국어</option></select></div><div class="settings-row">',
        '<option value="ko">한국어</option><option value="zh-CN">简体中文</option></select></div><div class="settings-row">', html_path)
    output[html_path] = html

    appjs_path = "app/src/main/assets/web/app.js"
    appjs = required_replace(originals[appjs_path],
        "L.language() === 'ko' ? 'ko-KR' : 'en-US', options",
        "L.language() === 'ko' ? 'ko-KR' : (L.language() === 'zh-CN' ? 'zh-CN' : 'en-US'), options",
        appjs_path)
    output[appjs_path] = appjs

    test_path = "app/src/test/java/dev/mobilecodex/app/AppLanguageTest.java"
    tests = required_replace(originals[test_path],
        'AppLanguage.set(context,"en");assertEquals("Settings",Texts.t("설정"));assertEquals("user content",Texts.t("user content"));',
        'AppLanguage.set(context,"en");assertEquals("Settings",Texts.t("설정"));assertEquals("user content",Texts.t("user content"));\n'
        '        AppLanguage.set(context,"zh-CN");assertEquals("设置",Texts.t("설정"));assertEquals("设置",Texts.t("Settings"));\n'
        '        assertEquals("user content",Texts.t("user content"));',
        test_path)
    output[test_path] = tests

    localization_doc = "docs/localization.md"
    old_doc = originals[localization_doc]
    old_doc = required_replace(old_doc,
        "Settings → General → Language supports **System**, **English**, and **한국어**. Korean device locales use Korean; other device locales fall back to English.",
        "Settings → General → Language supports **System**, **English**, **한국어**, and **简体中文**. Korean device locales use Korean; Simplified Chinese locales use zh-CN; other device locales fall back to English.", localization_doc)
    old_doc = required_replace(old_doc,
        "Chinese (Simplified) and Japanese are potential future translations, not selectable languages yet. To add one:",
        "Japanese is a potential future translation, not selectable yet. To add a locale:", localization_doc)
    output[localization_doc] = old_doc

    # Validate XML syntactically, JS catalogs are emitted from JSON, and no files changed so far.
    for path in ("app/src/main/res/values-zh-rCN/phone_use_strings.xml",
                 "app/src/main/res/values-zh-rCN/usage_widgets.xml"):
        ET.fromstring(output[path])
    if parallel_install:
        build_path = "app/build.gradle"
        manifest_path = "app/src/main/AndroidManifest.xml"
        build_bytes = read_bytes(build_path)
        manifest_bytes = read_bytes(manifest_path)
        if git_blob_sha(build_bytes) != "ff084ce8e227e99806493bfed478f77d245ec0b3":
            raise ValueError("Unexpected Gradle configuration for parallel variant")
        if git_blob_sha(manifest_bytes) != "6f677292f031b51c22e08022ed7672b28730786f":
            raise ValueError("Unexpected Android manifest for parallel variant")
        output[build_path] = required_replace(build_bytes.decode("utf-8"),
            "debug { if (suppliedSigningStore) signingConfig signingConfigs.provided }",
            "debug { applicationIdSuffix '.zhcn'; versionNameSuffix '-zhcn'; if (suppliedSigningStore) signingConfig signingConfigs.provided }", build_path)
        output[manifest_path] = required_replace(manifest_bytes.decode("utf-8"),
            'android:label="Mobile Codex"', 'android:label="Mobile Codex 中文版"', manifest_path)
    return output


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("repo", type=Path, help="Unmodified Mobile Codex v0.3.9 checkout")
    parser.add_argument("--check-only", action="store_true", help="Validate source and translations without changing files")
    parser.add_argument("--parallel", action="store_true", help="Give debug APK a separate Android ID (.zhcn) to avoid overwriting the original app")
    args = parser.parse_args()
    repo = args.repo.expanduser().resolve()
    if not (repo / "app/build.gradle").is_file():
        parser.error("Repository does not look like Mobile Codex")
    changes = prepare(repo, Path(__file__).resolve().parent, args.parallel)
    if not args.check_only:
        for path, data in changes.items():
            dest = repo / path
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_text(data, encoding="utf-8")
    print(f"{'Validated' if args.check_only else 'Applied'}: 1,257 Chinese UI strings, "
          f"Android resources, language switcher and tests; {len(changes)} files"
          f"{' (parallel debug package ID)' if args.parallel else '' }.")

if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, json.JSONDecodeError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        sys.exit(1)
