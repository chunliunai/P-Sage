package com.example.psage.http;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BackendClient {

    private static final String BASE_URL = "http://localhost:9090";
    private static final HttpClient client = HttpClient.newHttpClient();

    public static String jwtToken = "";
    public static String loggedInUser = "";

    /**
     * 登录，成功后存储 token 和用户名，返回提示信息
     */
    public static String login(String username, String password) {
        String body = "{\"username\":\"" + escape(username) + "\",\"password\":\"" + escape(password) + "\"}";
        try {
            HttpResponse<String> resp = post("/api/auth/login", body, false);
            String json = resp.body();
            if (resp.statusCode() == 200) {
                String token = extract(json, "token");
                String user = extract(json, "username");
                if (token != null && !token.isEmpty()) {
                    jwtToken = token;
                    loggedInUser = user != null ? user : username;
                    return "success:" + loggedInUser;
                }
            }
            // 尝试从 message 字段获取错误信息
            String msg = extract(json, "message");
            return "error:" + (msg != null ? msg : "登录失败");
        } catch (Exception e) {
            return "error:" + e.getMessage();
        }
    }

    /**
     * POST 漏洞分析
     */
    public static String analyzeVuln(String requestRaw) throws Exception {
        String body = "{\"userInput\":" + toJsonString(requestRaw) + "}";
        HttpResponse<String> resp = post("/api/ai/chat", body, true);
        return resp.body();
    }

    /**
     * POST 参数分析
     */
    public static String analyzeParam(String requestRaw, String responseRaw) throws Exception {
        StringBuilder sb = new StringBuilder("{");
        if (requestRaw != null && !requestRaw.isEmpty()) {
            sb.append("\"requestRaw\":").append(toJsonString(requestRaw));
        }
        if (responseRaw != null && !responseRaw.isEmpty()) {
            if (sb.length() > 1) sb.append(",");
            sb.append("\"responseRaw\":").append(toJsonString(responseRaw));
        }
        sb.append("}");
        HttpResponse<String> resp = post("/api/ai/param-analyze", sb.toString(), true);
        return resp.body();
    }

    private static HttpResponse<String> post(String path, String jsonBody, boolean withAuth) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));
        if (withAuth && !jwtToken.isEmpty()) {
            builder.header("Authorization", "Bearer " + jwtToken);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** 从 JSON 字符串中提取指定字段值（简单实现，适用于扁平结构） */
    private static String extract(String json, String key) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : null;
    }

    /** 转义 JSON 字符串 */
    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** 将多行字符串转为 JSON 字符串值（带双引号） */
    private static String toJsonString(String s) {
        return "\"" + s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t") + "\"";
    }
}
