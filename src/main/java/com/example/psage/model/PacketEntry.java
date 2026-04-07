package com.example.psage.model;

import burp.api.montoya.http.message.requests.HttpRequest;

public class PacketEntry {

    private final String method;
    private final String url;
    private String requestRaw;
    private String responseRaw;
    private int statusCode;

    // 保存 Montoya 原始对象，用于获取 host/port/TLS 信息以便重新发包
    private final HttpRequest originalRequest;

    public PacketEntry(String method, String url, String requestRaw, HttpRequest originalRequest) {
        this.method = method;
        this.url = url;
        this.requestRaw = requestRaw;
        this.responseRaw = "";
        this.statusCode = 0;
        this.originalRequest = originalRequest;
    }

    public String getMethod() { return method; }
    public String getUrl() { return url; }
    public String getRequestRaw() { return requestRaw; }
    public String getResponseRaw() { return responseRaw; }
    public int getStatusCode() { return statusCode; }
    public HttpRequest getOriginalRequest() { return originalRequest; }

    public void setRequestRaw(String requestRaw) { this.requestRaw = requestRaw; }
    public void setResponseRaw(String responseRaw) { this.responseRaw = responseRaw; }
    public void setStatusCode(int statusCode) { this.statusCode = statusCode; }

    @Override
    public String toString() {
        String status = statusCode > 0 ? String.valueOf(statusCode) : "-";
        return method + "  " + status + "  " + url;
    }
}
