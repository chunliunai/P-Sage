package com.example.psage;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import com.example.psage.menu.SendToPSageMenu;
import com.example.psage.ui.PSagePanel;

public class PSageExtension implements BurpExtension {

    public static MontoyaApi montoyaApi;

    @Override
    public void initialize(MontoyaApi api) {
        montoyaApi = api;
        api.extension().setName("P-Sage");

        PSagePanel panel = new PSagePanel(api);

        api.userInterface().registerContextMenuItemsProvider(
            new SendToPSageMenu(api, entry -> {
                panel.addPacket(entry);
                api.logging().logToOutput("已接收: " + entry.getMethod() + " " + entry.getUrl());
            })
        );

        api.userInterface().registerSuiteTab("P-Sage", panel);

        api.logging().logToOutput("P-Sage 加载成功 v1.0.0");
    }
}
