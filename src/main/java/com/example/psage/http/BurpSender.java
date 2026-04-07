package com.example.psage.http;

import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.example.psage.PSageExtension;
import com.example.psage.model.PacketEntry;

import java.nio.charset.StandardCharsets;

public class BurpSender {

    /**
     * 用 Montoya API 发送请求。
     * 使用 entry 中原始请求的 HttpService（含 host/port/TLS），
     * 但请求体内容取当前编辑区的内容（用户可能已修改）。
     */
    public static HttpRequestResponse send(PacketEntry entry) {
        HttpRequest originalReq = entry.getOriginalRequest();

        // 用原始 service 信息 + 当前编辑内容构造新请求
        HttpRequest newReq = HttpRequest.httpRequest(
            originalReq.httpService(),
            ByteArray.byteArray(entry.getRequestRaw().getBytes(StandardCharsets.UTF_8))
        );

        return PSageExtension.montoyaApi.http().sendRequest(newReq);
    }
}
