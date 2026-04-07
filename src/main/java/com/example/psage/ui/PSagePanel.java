package com.example.psage.ui;

import burp.api.montoya.MontoyaApi;
import com.example.psage.model.PacketEntry;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

public class PSagePanel extends JPanel {

    private final LeftPanel leftPanel;

    public PSagePanel(MontoyaApi api) {
        setLayout(new BorderLayout());

        leftPanel = new LeftPanel(api);

        // 把 getSelectedEntry 和 getCurrentRequestRaw 都传给 RightPanel
        RightPanel rightPanel = new RightPanel(leftPanel::getSelectedEntry, leftPanel::getCurrentRequestRaw, leftPanel::getCurrentResponseRaw);
        JScrollPane rightScroll = new JScrollPane(rightPanel);
        rightScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        rightScroll.getVerticalScrollBar().setUnitIncrement(16);

        // 切包时通知右侧更新缓存结果
        leftPanel.setOnPacketSelected(rightPanel::onPacketSelected);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightScroll);
        splitPane.setResizeWeight(0.65);
        // 首次渲染后设为右侧约占 30%
        splitPane.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                splitPane.setDividerLocation(0.65);
                splitPane.removeComponentListener(this);
            }
        });

        add(splitPane, BorderLayout.CENTER);
    }

    public void addPacket(PacketEntry entry) {
        leftPanel.addPacket(entry);
    }
}
