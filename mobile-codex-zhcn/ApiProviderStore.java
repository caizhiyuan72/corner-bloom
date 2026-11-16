package dev.mobilecodex.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONArray;
import org.json.JSONObject;
import static dev.mobilecodex.app.core.Json.obj;

/** Isolated API credentials. No raw keys in WebView state, logs, TOML, or process argv. */
final class ApiProviderStore {
    private static final String ALIAS = "mobile_codex_zhcn_api_providers_1";
    private static final String ENV = "MOBILE_CODEX_CUSTOM_API_KEY";
    private static final Pattern MODEL = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/+\\-]{0,127}");
    private static final Pattern ID = Pattern.compile("api_[a-f0-9]{12}");
    private final SharedPreferences prefs;

    ApiProviderStore(Context context) {
        prefs = context.getSharedPreferences("api_provider_profiles_v1", Context.MODE_PRIVATE);
    }

    synchronized String activeId() {
        return prefs.getString("active", "");
    }
    synchronized boolean isActive() { return !activeId().isBlank(); }

    private SecretKey secretKey(boolean create) throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (!ks.containsAlias(ALIAS)) {
            if (!create) throw new IOException("API 密钥已丢失，请先恢复原应用数据");
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build());
            generator.generateKey();
        }
        return ((KeyStore.SecretKeyEntry) ks.getEntry(ALIAS, null)).getSecretKey();
    }

    private JSONArray load() throws IOException {
        String encrypted = prefs.getString("encrypted", "");
        if (encrypted.isBlank()) return new JSONArray();
        try {
            byte[] packed = Base64.decode(encrypted, Base64.NO_WRAP);
            if (packed.length < 29) throw new IOException("Invalid credential payload");
            byte[] iv = java.util.Arrays.copyOfRange(packed, 0, 12);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey(false), new GCMParameterSpec(128, iv));
            String text = new String(cipher.doFinal(packed, 12, packed.length - 12), StandardCharsets.UTF_8);
            return new JSONArray(text);
        } catch (Exception error) {
            throw new IOException("无法解密已保存的 API 服务商；原数据未被覆盖");
        }
    }
    private void commit(JSONArray profiles) throws IOException {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            byte[] iv = new byte[12]; new SecureRandom().nextBytes(iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey(true), new GCMParameterSpec(128, iv));
            byte[] ciphertext = cipher.doFinal(profiles.toString().getBytes(StandardCharsets.UTF_8));
            byte[] packed = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, packed, 0, iv.length);
            System.arraycopy(ciphertext, 0, packed, iv.length, ciphertext.length);
            if (!prefs.edit().putString("encrypted", Base64.encodeToString(packed, Base64.NO_WRAP)).commit())
                throw new IOException("保存 API 服务商失败");
        } catch (IOException error) { throw error; }
        catch (Exception error) { throw new IOException("无法安全存储 API 密钥", error); }
    }

    private JSONObject find(JSONArray profiles, String id) {
        for (int i = 0; i < profiles.length(); i++) {
            JSONObject profile = profiles.optJSONObject(i);
            if (profile != null && id.equals(profile.optString("id"))) return profile;
        }
        return null;
    }
    private JSONObject current() throws IOException {
        String id = activeId();
        if (id.isBlank()) return null;
        JSONObject found = find(load(), id);
        if (found == null) throw new IOException("已选择的 API 服务商不存在，请在设置中切换");
        return found;
    }
    synchronized JSONArray publicProfiles() throws IOException {
        JSONArray result = new JSONArray();
        JSONArray stored = load();
        for (int i = 0; i < stored.length(); i++) {
            JSONObject p = stored.optJSONObject(i);
            if (p != null) result.put(obj("id", p.optString("id"), "name", p.optString("name"),
                "baseUrl", p.optString("baseUrl"), "models", p.optJSONArray("models"),
                "hasKey", !p.optString("key").isEmpty()));
        }
        return result;
    }
    synchronized JSONArray models() throws IOException {
        JSONArray result = new JSONArray();
        JSONObject p = current();
        if (p == null) return result;
        JSONArray names = p.optJSONArray("models");
        for (int i = 0; names != null && i < names.length(); i++) {
            String model = names.optString(i);
            result.put(obj("model", model, "id", model,
                "displayName", p.optString("name") + " · " + model,
                "description", "第三方 Responses API · 按服务商计费", "isDefault", i == 0,
                "supportedReasoningEfforts", new JSONArray()));
        }
        return result;
    }

    static String validateBaseUrl(String input) throws IOException {
        String value = input == null ? "" : input.trim();
        if (value.isBlank() || value.length() > 512 || value.contains("\\") || value.contains("\n") || value.contains("\r"))
            throw new IOException("请填写有效的 HTTPS Base URL");
        try {
            URI uri = new URI(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getHost().isBlank()
                || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                || uri.getPort() > 65535 || uri.getPort() == 0)
                throw new IOException("Base URL 必须是 HTTPS 且不能含用户名、密码、查询参数或片段");
            if (value.endsWith("/responses") || value.endsWith("/chat/completions"))
                throw new IOException("请填写 Base URL，勿包含 /responses 或 /chat/completions");
            return value.replaceAll("/+$", "");
        } catch (java.net.URISyntaxException ex) { throw new IOException("Base URL 不是有效网址"); }
    }
    static JSONArray parseModels(String raw) throws IOException {
        if (raw == null || raw.length() > 4096) throw new IOException("模型 ID 太长");
        Set<String> models = new LinkedHashSet<>();
        for (String name : raw.split("[,\\n\\r]")) {
            String model = name.trim();
            if (model.isEmpty()) continue;
            if (!MODEL.matcher(model).matches()) throw new IOException("模型 ID 只能包含字母数字及 . _ - / : +");
            models.add(model);
        }
        if (models.isEmpty() || models.size() > 20) throw new IOException("请填写 1 至 20 个模型 ID");
        return new JSONArray(models);
    }
    synchronized void save(JSONObject args) throws Exception {
        JSONArray profiles = load();
        String id = args.optString("id", "");
        JSONObject existing = id.isBlank() ? null : find(profiles, id);
        if (!id.isBlank() && (existing == null || !ID.matcher(id).matches())) throw new IOException("服务商不存在");
        if (profiles.length() >= 25 && existing == null) throw new IOException("最多保存 25 个服务商");
        String name = args.optString("name", "").trim();
        if (name.isEmpty() || name.length() > 60 || name.matches("(?s).*?[\\x00-\\x1f].*")) throw new IOException("服务商名称无效");
        String url = validateBaseUrl(args.optString("baseUrl"));
        JSONArray models = parseModels(args.optString("models"));
        String secret = args.optString("apiKey", "").trim();
        if (secret.isBlank() && existing != null) secret = existing.optString("key");
        if (secret.isBlank() || secret.length() > 4096 || secret.indexOf('\n') >= 0 || secret.indexOf('\r') >= 0)
            throw new IOException("请填写有效的 API Key");
        if (id.isBlank()) id = "api_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        JSONObject entry = obj("id", id, "name", name, "baseUrl", url, "models", models, "key", secret);
        JSONArray updated = new JSONArray();
        for (int i = 0; i < profiles.length(); i++) {
            JSONObject p = profiles.optJSONObject(i);
            if (p != null && !id.equals(p.optString("id"))) updated.put(p);
        }
        updated.put(entry);
        commit(updated);
    }
    synchronized void remove(String id) throws Exception {
        JSONArray profiles = load(), updated = new JSONArray();
        if (!ID.matcher(id).matches() || find(profiles, id) == null) throw new IOException("服务商不存在");
        for (int i = 0; i < profiles.length(); i++) {
            JSONObject p = profiles.optJSONObject(i);
            if (p != null && !id.equals(p.optString("id"))) updated.put(p);
        }
        commit(updated);
        if (id.equals(activeId()) && !prefs.edit().putString("active", "").commit())
            throw new IOException("无法切回 ChatGPT");
    }
    synchronized void select(String id) throws Exception {
        if (!id.isBlank() && (!ID.matcher(id).matches() || find(load(), id) == null))
            throw new IOException("所选 API 服务商不存在");
        if (!prefs.edit().putString("active", id).commit()) throw new IOException("无法切换 API 服务商");
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }
    synchronized void configure(ProcessBuilder builder) throws IOException {
        JSONObject p = current();
        if (p == null) return;
        String id = p.optString("id"), prefix = "model_providers." + id + ".";
        JSONArray models = p.optJSONArray("models");
        if (models == null || models.length() == 0) throw new IOException("所选服务商没有模型");
        String secret = p.optString("key");
        if (secret.isBlank()) throw new IOException("所选服务商没有 API Key");
        builder.environment().put(ENV, secret);
        List<String> args = new ArrayList<>();
        addConfig(args, "model_provider", id);
        addConfig(args, "model", models.optString(0));
        addConfig(args, prefix + "name", p.optString("name"));
        addConfig(args, prefix + "base_url", p.optString("baseUrl"));
        addConfig(args, prefix + "wire_api", "responses");
        addConfig(args, prefix + "env_key", ENV);
        builder.command().addAll(1, args);
    }
    private static void addConfig(List<String> args, String name, String value) {
        args.add("-c"); args.add(name + "=" + quote(value));
    }
}