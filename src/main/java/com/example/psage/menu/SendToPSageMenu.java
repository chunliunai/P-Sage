package com.example.psage.menu;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;

import java.nio.charset.StandardCharsets;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;
import burp.api.montoya.ui.contextmenu.MessageEditorHttpRequestResponse;
import com.example.psage.model.PacketEntry;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class SendToPSageMenu implements ContextMenuItemsProvider {

    private final MontoyaApi api;
    private final Consumer<PacketEntry> onPacketReceived;

    public SendToPSageMenu(MontoyaApi api, Consumer<PacketEntry> onPacketReceived) {
        this.api = api;
        this.onPacketReceived = onPacketReceived;
    }

    @Override
    public List<Component> provideMenuItems(ContextMenuEvent event) {
        List<Component> items = new ArrayList<>();

        // 从列表中发送（Proxy历史、Intruder等）
        if (!event.selectedRequestResponses().isEmpty()) {
            JMenuItem item = new JMenuItem("Send to P-Sage");
            item.addActionListener(e -> {
                event.selectedRequestResponses().forEach(reqRes -> {
                    HttpRequest req = reqRes.request();
                    PacketEntry entry = new PacketEntry(
                        req.method(), req.url(), new String(req.toByteArray().getBytes(), StandardCharsets.UTF_8), req
                    );
                    onPacketReceived.accept(entry);
                });
            });
            items.add(item);
        }

        // 从编辑器中发送（Repeater 等）
        if (event.messageEditorRequestResponse().isPresent()) {
            MessageEditorHttpRequestResponse editor = event.messageEditorRequestResponse().get();
            JMenuItem item = new JMenuItem("Send to P-Sage");
            item.addActionListener(e -> {
                HttpRequest req = editor.requestResponse().request();
                PacketEntry entry = new PacketEntry(
                    req.method(), req.url(), new String(req.toByteArray().getBytes(), StandardCharsets.UTF_8), req
                );
                onPacketReceived.accept(entry);
            });
            items.add(item);
        }

        return items;
    }
}
