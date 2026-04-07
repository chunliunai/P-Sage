package com.example.psage.ui;

import javax.swing.*;
import java.awt.*;
import java.util.function.Supplier;
import com.example.psage.model.PacketEntry;

public class RightPanel extends JPanel {

    private final VulnAnalysisPanel  vulnPanel;
    private final ParamAnalysisPanel paramPanel;

    public void onPacketSelected(PacketEntry entry) {
        vulnPanel.onPacketSelected(entry);
        paramPanel.onPacketSelected(entry);
    }

    public RightPanel(Supplier<PacketEntry> selectedPacket,
                      Supplier<String> currentRequestRaw,
                      Supplier<String> currentResponseRaw) {
        setLayout(new BorderLayout(0, 0));

        // 登录区（固定顶部）
        LoginPanel loginPanel = new LoginPanel();
        add(loginPanel, BorderLayout.NORTH);

        // 漏洞分析区 + 参数分析区（填满剩余空间）
        vulnPanel  = new VulnAnalysisPanel(selectedPacket, currentRequestRaw);
        paramPanel = new ParamAnalysisPanel(selectedPacket, currentRequestRaw, currentResponseRaw);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, vulnPanel, paramPanel);
        splitPane.setResizeWeight(0.5);
        splitPane.setBorder(null);
        splitPane.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                splitPane.setDividerLocation(0.5);
                splitPane.removeComponentListener(this);
            }
        });
        add(splitPane, BorderLayout.CENTER);
    }
}
