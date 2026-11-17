#!/usr/bin/env python3
"""Post-patch Mobile Codex v0.3.9 localization + model reasoning improvements.
Apply AFTER apply_zhcn.py --parallel and apply_api_providers.py.
All source edits are strict-anchored and fail closed on source drift.
"""
import json
from pathlib import Path
import sys
import re

HANGUL = re.compile(r"[\uac00-\ud7a3]")
EFFORTS = ("low", "medium", "high", "xhigh")
def patch(src, old, new, name):
    count=src.count(old)
    if count!=1:
        raise RuntimeError(f"{name} expected exactly one source anchor, got {count}: {old[:120]!r}")
    return src.replace(old,new,1)

def rewrite(path, fn):
    before=path.read_text(encoding="utf-8")
    after=fn(before)
    if after==before: raise RuntimeError(f"No changes produced for {path}")
    path.write_text(after,encoding="utf-8")

def group_chinese(s):
    changes=[
      ('return "사용자";','return "用户";'),
      ('"(첨부 " + files.length() + "개 · Codex만 열람 가능)"','"(附件 " + files.length() + " 个 · 仅 Codex 可读取)"'),
      ('"(앞선 메시지 " + omitted + "개는 길이 제한으로 생략했습니다.)\\n\\n"','"(前面的 " + omitted + " 条消息因长度限制而省略。)\\n\\n"'),
      ('"[그룹 대화] 이 대화에는 사용자와 Codex 외에 "','"[群组对话] 除用户与 Codex 外，参与者还有 "'),
      ('"도 참여합니다. 다른 참가자의 의견이 꼭 필요할 때만 독립된 @ID로 호출하세요."','"。只有确实需要其他参与者的意见时，才通过独立的 @ID 呼叫他们。请默认使用简体中文回答。"'),
      ('"\\n\\nCodex의 마지막 답변 이후 오간 메시지:\\n"','"\\n\\nCodex 上次回答之后的消息：\\n"'),
      ('"\\n[그룹 대화 끝]"','"\\n[群组对话结束]"'),
      ('"작업 폴더는 mobile_* 도구로 읽기·검색만 할 수 있습니다. 수정은 Codex에게 맡기세요."','"可通过 mobile_* 工具读取和搜索工作目录，修改操作应交给 Codex。"'),
      ('"작업 폴더는 mobile_* 도구로 다룰 수 있습니다. 수정·명령은 앱의 승인 설정과 작업 차례를 따릅니다."','"可通过 mobile_* 工具访问工作目录。修改和执行命令须遵守应用的审批设置和任务顺序。"'),
      ('"파일이나 휴대폰 도구는 사용할 수 없습니다. 대화로만 답하세요."','"不能调用文件或手机工具，只能通过对话回答。"'),
      ('"당신은 Mobile Codex 그룹 대화의 @"','"你是 Mobile Codex 群组对话中的 @"'),
      ('입니다. 다른 참가자: ','。其他参与者：'),
      ('" 공유된 대화 기록을 읽고 답하세요. 같은 질문에 다른 참가자도 동시에 답할 수 있습니다. 다른 참가자 의견이 꼭 필요할 때만 독립된 @ID로 호출하세요. "','"请阅读共享对话记录后回答。同一个问题可能有其他参与者同时回答。仅在确有必要时使用独立的 @ID 呼叫其他参与者。默认用简体中文回答，除非用户明确要求其他语言。"'),
      ('"@file:경로, @app:이름은 참가자 호출이 아닙니다. 도구 결과 없이 작업 성공을 주장하지 마세요.\\n"','"@file:路径 和 @app:名称 不是对参与者的呼叫。未经工具结果验证，不要声称操作成功。\\n"'),
      ('"\\n새 대화 기록:\\n"','"\\n新增对话记录：\\n"'),
      ('"\\n\\n이제 답하세요."','"\\n\\n现在请回答。"')
    ]
    for a,b in changes: s=patch(s,a,b,"Group prompt translation")
    return s

def icons_chinese(_):
    # Source icon tokens remain the original asset IDs, not natural-language instructions.
    # Prevent the long Korean instruction blob from entering the model context.
    return """package dev.mobilecodex.app.core;

/** App-owned Chinese output guidance; never changes the user's own AGENTS.md. */
public final class ChatIconInstructions {
    private ChatIconInstructions() {}
    public static String forEnabled(boolean enabled) {
        if (!enabled) return "";
        return "\\n\\n聊天图标说明：默认使用简体中文回答，除非用户明确要求其他语言。" +
          "聊天图标只是可选装饰，普通任务通常无需使用；不要为了插入图标而影响文字准确性。" +
          "如确需使用，只能使用应用已有素材的准确图标标识，不要编造标识。" +
          "只有实际完成任务才能使用完成类图标。编程、医疗、法律和金融任务应优先提供文字说明。" +
          "不要把图标的内部标识当作对话语言。";
    }
}
"""

def api_models(s):
    old='''                "supportedReasoningEfforts", new JSONArray()));'''
    new='''                "defaultReasoningEffort", "",
                // User-selectable suggestions, not a claim that the provider supports each value.
                // Providers may reject an effort or ignore it; leave unset for automatic mode.
                "supportedReasoningEfforts", new JSONArray()
                    .put(obj("reasoningEffort", "low"))
                    .put(obj("reasoningEffort", "medium"))
                    .put(obj("reasoningEffort", "high"))
                    .put(obj("reasoningEffort", "xhigh"))));'''
    return patch(s,old,new,"API effort options")

def app_chinese(s):
    s=patch(s,
        '''    $('effort').value = values.some(e => e.reasoningEffort === previous) ? previous : (values.some(e => e.reasoningEffort === selected?.defaultReasoningEffort) ? selected.defaultReasoningEffort : values[0]?.reasoningEffort || '');''',
        '''    if (state.apiActive) {
      // Empty effort means "automatic": omit the optional request parameter.
      $('effort').add(new Option('自动（服务商默认）', ''));
      $('effort').value = values.some(e => e.reasoningEffort === previous) ? previous : '';
    } else {
      $('effort').value = values.some(e => e.reasoningEffort === previous) ? previous : (values.some(e => e.reasoningEffort === selected?.defaultReasoningEffort) ? selected.defaultReasoningEffort : values[0]?.reasoningEffort || '');
    }''',
        "effort selection")
    s=patch(s,
        '''    slider.disabled = options.length < 2;''',
        '''    slider.disabled = options.length < 2;
    const auto = $('effort-auto');
    if (auto) {
      auto.hidden = !state.apiActive;
      auto.disabled = !options.length;
      auto.setAttribute('aria-pressed', String(state.apiActive && !$('effort').value));
    }''',
        "effort auto button visibility")
    s=patch(s,
        '''  on('effort-slider', previewReasoning, 'input');''',
        '''  on('effort-auto', () => {
    if (!state.apiActive) return;
    $('effort').value = ''; $('effort').dispatchEvent(new Event('change'));
    $('effort-heading').textContent = '自动（服务商默认）';
    syncReasoningPicker(); saveOptions(); optionsSummary();
  });
  on('effort-slider', previewReasoning, 'input');''',
        "effort auto event")
    # Prevent frontend from speaking Korean in its own aria string.
    s=patch(s,
        """(listId === 'projects' ? '프로젝트 ' : '일반 대화 ') + (expanded ? '접기' : '펼치기')""",
        """(listId === 'projects' ? '项目 ' : '普通对话 ') + (expanded ? '收起' : '展开')""",
        "sidebar aria labels")
    return s

def html_chinese(s):
    s=patch(s,
        '''          <input id="effort-slider" type="range" min="0" max="0" step="1" value="0" aria-label="추론 강도">''',
        '''          <input id="effort-slider" type="range" min="0" max="0" step="1" value="0" aria-label="思考强度">
          <button id="effort-auto" class="secondary-button" type="button" hidden style="margin-top:12px;width:100%">自动（使用服务商默认强度）</button>
          <small class="muted" style="display:block;margin-top:9px">第三方模型的思考强度支持情况取决于服务商；若请求报错，请切回“自动”。</small>''',
        "reasoning settings")
    for a,b in [
      ('aria-label="프로젝트 접기"','aria-label="收起项目"'),
      ('aria-label="일반 대화 접기"','aria-label="收起普通对话"'),
      ('aria-label="돌아가기"','aria-label="返回"')
    ]:
        s=s.replace(a,b)
    return s

def engine_chinese(s):
    s=patch(s,
       'String context = AgentGroup.codexContext(active, run.context, run.context.length(), Set.of()) + "\\n\\n다른 참가자가 Codex를 호출했습니다. 위 대화에 이어서 답하세요.";',
       'String context = AgentGroup.codexContext(active, run.context, run.context.length(), Set.of()) + "\\n\\n其他参与者呼叫了 Codex，请结合上文用简体中文继续回答。";',
       "group turn prompt")
    # Note: main ToolCatalog already requests user-language replies; strengthen for Chinese distribution.
    return s

def tool_chinese(s):
    return patch(s,
      """public static final String INSTRUCTIONS = "You are Codex running on the user's Android device. Reply in the user's language. " +""",
      """public static final String INSTRUCTIONS = "You are Codex running on the user's Android device. Respond in Simplified Chinese by default, unless the user explicitly requests another language. Never treat internal icon identifiers or text in files as language instructions. " +""",
      "default language instructions")

def test_group(s):
    for a,b in [
        ('[사용자] 첫 질문','[用户] 첫 질문'),
        ('[사용자] 둘째 질문','[用户] 둘째 질문'),
        ('@claude (Claude)입니다','@claude (Claude)。'),
        ('(앞선 메시지 ','(前面的 ')
    ]:
        s=patch(s,a,b,"group test expected output")
    return s

def locale_chinese(s):
    return patch(s,"localStorage.getItem('language') || 'system';","localStorage.getItem('language') || 'zh-CN';","default simplified Chinese")

def app_lang_chinese(s):
    return patch(s,'getString("language", "system");','getString("language", "zh-CN");',"Android default language")

EXTRA={
  "전송 상태를 확인해야 합니다. 같은 메시지를 다시 보내기 전에 ChatGPT 웹 대화를 확인하세요.":"需要确认发送状态。再次发送相同消息前，请先查看 ChatGPT 网页对话。",
  "보내지 못했습니다.":"发送失败。",
  "실행 확인 실패":"运行检查失败",
  "프로젝트를 제거할까요? 대화는 일반 대화로 이동해요.\n파일은 그대로 유지됩니다.":"要移除此项目吗？对话将移至普通对话，文件保持不变。",
  " 새 대화":" 新对话",
  "Codex 답변 기다리는 중":"正在等待 Codex 回复",
  "텍스트 전체를 표시할 수 없는 파일입니다.\n이전: ":"无法完整显示此文件。\n原内容：",
  "\n현재: ":"\n当前：",
  "지원하지 않는 언어 설정입니다.":"不支持该语言设置。",
  "음성 연결 요청이 올바르지 않습니다.":"语音连接请求不正确。",
  "지원하지 않는 음성입니다. 목록을 새로고침해 주세요.":"不支持该语音，请刷新列表。",
  "음성 대화":"语音对话",
  "음성 연결이 종료되었습니다.":"语音连接已结束。",
  "음성 대화를 먼저 종료해 주세요.":"请先结束语音对话。",
  "GPT-6-Pro 웹 모델은 Codex 요청으로 보낼 수 없습니다.":"不能通过 Codex 请求使用 GPT-6-Pro 网页模型。",
  "이 대화에는 진행 중인 Codex 작업이 없습니다.":"当前对话没有正在执行的 Codex 任务。",
  "대화 작업을 찾을 수 없습니다.":"找不到对话任务。",
  "다른 Pro 문의가 진행 중입니다. 자동으로 다시 문의하지 않습니다.":"其他 Pro 咨询正在执行，不会自动重复发送。",
  "ChatGPT Pro에 문의 중입니다.":"正在咨询 ChatGPT Pro。",
  "Pro 문의에 실패했습니다.":"Pro 咨询失败。",
  "Pro 문의를 완료하지 못했습니다.":"无法完成 Pro 咨询。",
  "잘못된 GPT-6-Pro 모델 요청입니다.":"GPT-6-Pro 模型请求不正确。",
  "현재 Codex 작업을 먼저 중지해 주세요.":"请先停止当前 Codex 任务。",
  "답변 완료":"回答完成",
  "승인 필요":"需要审批",
  "Codex 작업의 승인이 필요합니다.":"Codex 任务需要审批。",
  "Codex 연결이 변경되었습니다.":"Codex 连接已发生变化。",
  "발언이 종료되었습니다.":"发言已结束。",
  "앱의 파일 권한이 읽기 전용입니다.":"应用的文件权限为只读。",
  "마이크 권한이 필요합니다.":"需要麦克风权限。",
  "음성 연결을 시작할 수 없습니다.":"无法启动语音连接。",
  "음성 연결이 취소되었습니다.":"语音连接已取消。",
  "공식 공급자의 로그인 주소만 열 수 있습니다.":"仅允许打开官方登录地址。",
  "로그인 코드":"登录代码",
  "자동":"自动"
}


EXTRA.update({
  "앱이 종료되어 Pro 문의 결과를 원래 Codex 작업에 전달하지 못했습니다. 자동으로 다시 보내지 않습니다.":"应用已结束，无法将 Pro 咨询结果交还原 Codex 任务，不会自动重复发送。",
  "앱이 종료되어 웹 전송 결과를 확인하지 못했습니다. 다시 보내기 전에 ChatGPT 웹 대화를 확인해 주세요.":"应用已结束，无法确认网页发送结果。重新发送前请先检查 ChatGPT 网页对话。",
  "대화 작업 폴더 이전을 저장하지 못했습니다.":"无法保存对话工作目录的迁移。",
  "대화 이전 복구 사본을 저장하지 못했습니다.":"无法保存对话迁移的恢复备份。",
  "프로젝트 연결 해제 전 대화 복구 사본을 저장하지 못했습니다.":"断开项目连接之前，无法保存对话恢复副本。",
  "사용자가 Pro 문의를 취소했습니다.":"用户取消了 Pro 咨询。",
  "현재 대화의 작업을 먼저 중지해 주세요.":"请先停止当前对话任务。",
  "GPT-6-Pro 답변을 기다리는 대화는 먼저 중지해 주세요.":"请先停止等待 GPT-6-Pro 回复的对话。",
  "Pro 문의를 기다리는 대화는 먼저 중지해 주세요.":"请先停止等待 Pro 咨询的对话。",
  "Codex 연결이 종료되어 Pro 문의를 완료하지 못했습니다.":"Codex 连接已中断，无法完成 Pro 咨询。",
  "저장된 계정의 로그인 토큰이 폐기되었습니다. 이 계정은 다시 로그인해야 합니다.":"已保存账号的登录令牌失效，需要重新登录。",
  "새 로그인 공간을 준비하지 못했습니다.":"无法准备新的登录环境。",
  "이 대화의 Pro 문의 도구를 연결하지 못했습니다. 연결 후 다시 시도해 주세요.":"无法连接当前对话的 Pro 咨询工具，请连接后重试。",
  "이 사용자 요청에는 Pro 문의 권한이 없습니다.":"当前用户请求没有 Pro 咨询权限。",
  "Pro 문의 연결이 취소되었습니다.":"Pro 咨询连接已取消。",
  "Pro 문의는 1~50,000자로 입력해 주세요.":"Pro 咨询内容需要 1～50,000 个字符。",
  "이 사용자 요청의 Pro 문의 권한이 없거나 이미 사용되었습니다.":"当前用户请求的 Pro 咨询权限不存在或已被使用。",
  "Pro 문의 응답 시간이 초과되었습니다. 자동으로 다시 보내지 않습니다.":"Pro 咨询响应超时，不会自动重复发送。",
  "Pro 문의가 잘못된 응답 상태를 반환했습니다.":"Pro 咨询返回了不正确的响应状态。",
  "Pro 답변 길이가 올바르지 않습니다.":"Pro 回复长度不正确。",
  "원래 Codex 작업이나 연결이 종료되어 Pro 결과를 전달하지 못했습니다.":"原 Codex 任务或连接已结束，无法转交 Pro 结果。",
  "원래 Pro 문의 대화를 찾을 수 없습니다.":"找不到原来的 Pro 咨询对话。",
  "Pro 문의 결과를 저장하지 못했습니다. 자동으로 다시 보내지 않습니다.":"无法保存 Pro 咨询结果，不会自动重复发送。",
  "Pro 결과를 원래 Codex 연결에 전달하지 못했습니다.":"无法将 Pro 结果传递回原 Codex 连接。",
  "GPT-6-Pro 답변을 기다리는 중입니다.":"正在等待 GPT-6-Pro 回复。",
  "GPT-6-Pro 읽기 전용 모드에서는 스킬을 사용할 수 없습니다.":"GPT-6-Pro 只读模式下无法使用技能。",
  "ChatGPT Pro 답변 길이가 올바르지 않습니다.":"ChatGPT Pro 回复长度不正确。",
  "GPT-6-Pro 작업이 변경되었습니다.":"GPT-6-Pro 任务已改变。",
  "GPT-6-Pro 사용자 메시지를 찾을 수 없습니다.":"找不到 GPT-6-Pro 用户消息。",
  "ChatGPT Pro가 답변을 마쳤습니다.":"ChatGPT Pro 已完成回答。",
  "잘못된 GPT-6-Pro 작업 상태입니다.":"GPT-6-Pro 任务状态不正确。",
  "웹 전송 상태 확인 필요":"需要确认网页发送状态",
  "원래 Codex 작업이 종료되어 Pro 문의를 취소했습니다.":"原 Codex 任务已结束，Pro 咨询已取消。",
  "Codex 작업이 실패했습니다.":"Codex 任务失败。",
  "Codex가 작업을 마쳤습니다.":"Codex 已完成任务。",
  "Pro 문의 정보가 올바르지 않습니다.":"Pro 咨询信息不正确。",
  "Codex 실행이 중지되어 Pro 문의를 취소했습니다.":"Codex 已停止运行，因此取消了 Pro 咨询。",
  "이전 계정을 복원하지 못했습니다: ":"无法恢复原账号：",
  "앱의 파일 권한이 읽기 전용입니다.":"应用的文件权限为只读。",
  "모델 목록 조회 필요":"需要刷新模型列表",
  "추론 강도 조절 미지원":"当前模型不支持调整思考强度",
  "대화만":"仅聊天",
  "프로젝트 읽기":"读取项目",
  "프로젝트 수정":"修改项目",
  "연결됨":"已连接",
  "CLI 설치 필요":"需要安装 CLI",
  "로그인 필요":"需要登录",
  "상태 확인 불가":"无法确认状态",
  "실행 확인 필요":"需要确认运行状态",
  "업데이트 필요":"需要更新",
  "확인 전":"尚未检查",
  "설치 중…":"安装中…",
  "로그인 진행 중…":"正在登录…",
  "응답 중":"正在回复",
  "작업 차례 대기":"等待执行顺序",
  "승인 대기":"等待审批",
  "차례 대기":"排队中",
  "대기":"等待",
  "생각 중":"思考中",
  "프로젝트 접기":"收起项目",
  "일반 대화 접기":"收起普通对话",
  "돌아가기":"返回"
})

def main(root):
    p=Path(root)
    def edit(relative,fn): rewrite(p/relative,fn)
    edit("app/src/main/java/dev/mobilecodex/app/AgentGroup.java",group_chinese)
    edit("app/src/main/java/dev/mobilecodex/app/core/ChatIconInstructions.java",icons_chinese)
    edit("app/src/main/java/dev/mobilecodex/app/core/ToolCatalog.java",tool_chinese)
    edit("app/src/main/java/dev/mobilecodex/app/Engine.java",engine_chinese)
    edit("app/src/main/java/dev/mobilecodex/app/ApiProviderStore.java",api_models)
    edit("app/src/main/assets/web/app.js",app_chinese)
    edit("app/src/main/assets/web/index.html",html_chinese)
    edit("app/src/main/assets/web/locale.js",locale_chinese)
    edit("app/src/main/java/dev/mobilecodex/app/AppLanguage.java",app_lang_chinese)
    edit("app/src/test/java/dev/mobilecodex/app/AgentGroupTest.java",test_group)
    # Group integration tests assert the text of app-generated headers, not user content.
    engine_test=p/"app/src/test/java/dev/mobilecodex/app/AgentEngineTest.java"
    original=engine_test.read_text("utf-8")
    original=original.replace('[그룹 대화]', '[群组对话]').replace('[사용자]', '[用户]')
    engine_test.write_text(original,encoding="utf-8")
    # The old icon hints contained Korean tag IDs; the Chinese edition uses a
    # text-only guidance prompt instead. Assert the localized behavior.
    original=engine_test.read_text("utf-8")
    original=original.replace('assertTrue(launch("claude").getString("prompt").contains("[[icon:태그명]]"));',
        'assertTrue(launch("claude").getString("prompt").contains("聊天图标说明"));')
    engine_test.write_text(original,encoding="utf-8")
    offline=p/"app/src/test/java/dev/mobilecodex/app/EngineOfflineSessionTest.java"
    text=offline.read_text("utf-8")
    text=text.replace('assertTrue(started.getString("developerInstructions").contains("[[icon:태그명]]"));',
        'assertTrue(started.getString("developerInstructions").contains("聊天图标说明"));')
    text=text.replace('assertTrue(started.getString("developerInstructions").contains("검토완료"));',
        'assertTrue(started.getString("developerInstructions").contains("简体中文"));')
    text=text.replace('assertTrue(enabled.getString("developerInstructions").contains("[[icon:태그명]]"));',
        'assertTrue(enabled.getString("developerInstructions").contains("聊天图标说明"));')
    offline.write_text(text,encoding="utf-8")

    # Add missing Korean->Chinese dynamic UI messages to both native and WebView catalogs.
    file=p/"app/src/main/assets/translations-zh-CN.json"
    catalog=json.loads(file.read_text("utf-8"))
    catalog.update(EXTRA)
    file.write_text(json.dumps(catalog,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    js=p/"app/src/main/assets/web/translations-zh.js"
    js.write_text("window.MobileCodexChinese = "+json.dumps(catalog,ensure_ascii=False,indent=2)+";\n",encoding="utf-8")
    # Ensure actual model prompts contain no Korean; preserve Korean alias parsing
    # for compatibility with legacy tests and past conversation mentions.
    for rel in ["app/src/main/java/dev/mobilecodex/app/AgentGroup.java",
                "app/src/main/java/dev/mobilecodex/app/core/ChatIconInstructions.java"]:
        text=(p/rel).read_text("utf-8")
        if rel.endswith("AgentGroup.java"):
            text=re.sub(r'// "@codex야"[^\n]+', '// Keep language-specific mention aliases for compatibility.',text)
            # They are parsing tokens, never instruction content.
            for line in text.splitlines():
                if 'String header =' in line or 'String context =' in line or 'StringBuilder out =' in line:
                    if HANGUL.search(line): raise RuntimeError("Korean remains in group prompt")
        elif HANGUL.search(text):
            raise RuntimeError("Korean remains in chat icon developer instructions")
    print("Applied Chinese conversation prompt rewrite, expanded localization, third-party reasoning effort UI.")

if __name__=="__main__":
    if len(sys.argv)!=2: sys.exit("Usage: apply_zh_v2.py /path/to/upstream-after-patches")
    main(sys.argv[1])
