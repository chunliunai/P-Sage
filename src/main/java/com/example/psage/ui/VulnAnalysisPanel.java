package com.example.psage.ui;

import com.example.psage.PSageExtension;
import com.example.psage.http.BackendClient;
import com.example.psage.model.PacketEntry;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VulnAnalysisPanel extends JPanel {

    private final Supplier<PacketEntry> selectedPacket;
    private final Supplier<String> currentRequestRaw;
    private final JButton analyzeBtn = new JButton("漏洞分析");

    private final List<VulnEntry> vulnEntries = new ArrayList<>();
    private final Map<PacketEntry, List<VulnEntry>> resultCache = new HashMap<>();

    // ── 漏洞列表 ──────────────────────────────────────────────────
    private final DefaultListModel<String> vulnListModel = new DefaultListModel<>();
    private final JList<String> vulnList = new JList<String>(vulnListModel) {

        @Override public boolean getScrollableTracksViewportWidth() { return false; }

        @Override
        protected void processMouseEvent(MouseEvent e) {
            int index = locationToIndex(e.getPoint());
            if (index >= 0) {
                java.awt.Rectangle bounds = getCellBounds(index, index);
                if (bounds == null || !bounds.contains(e.getPoint())) return;
            }
            super.processMouseEvent(e);
        }
    };

    // ── payload 展示区 ────────────────────────────────────────────
    // getScrollableTracksViewportWidth=false 告诉 JViewport 用 preferredSize.width 而非 viewport 宽
    // 实际宽度通过 setPreferredSize() 在内容变化时显式设置，不依赖 getPreferredSize() 实时计算
    private final JTextArea payloadArea = new JTextArea() {
        @Override public boolean getScrollableTracksViewportWidth() { return false; }
    };

    private JScrollPane listScroll;
    private JScrollPane payloadScroll;

    public VulnAnalysisPanel(Supplier<PacketEntry> selectedPacket, Supplier<String> currentRequestRaw) {
        this.selectedPacket = selectedPacket;
        this.currentRequestRaw = currentRequestRaw;
        setLayout(new BorderLayout(0, 4));
        setBorder(BorderFactory.createTitledBorder("漏洞分析"));

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        btnRow.add(analyzeBtn);
        add(btnRow, BorderLayout.NORTH);

        // 漏洞列表
        vulnList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        vulnList.setSelectionBackground(vulnList.getBackground());
        vulnList.setSelectionForeground(vulnList.getForeground());
        vulnList.setCellRenderer((list, value, index, isSelected, cellHasFocus) -> {
            VulnEntry entry = (index >= 0 && index < vulnEntries.size()) ? vulnEntries.get(index) : null;
            JPanel cell = new JPanel();
            cell.setLayout(new BoxLayout(cell, BoxLayout.Y_AXIS));
            cell.setOpaque(true);
            cell.setBackground(list.getBackground());
            cell.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            if (entry != null) {
                JLabel titleLbl = new JLabel("[" + entry.severity + "] " + entry.type);
                titleLbl.setFont(new Font("Monospaced", Font.PLAIN, 12));
                titleLbl.setForeground(list.getForeground());
                JLabel evidLbl = new JLabel(entry.evidence != null && !entry.evidence.isEmpty()
                        ? entry.evidence : " ");
                evidLbl.setFont(new Font("Monospaced", Font.PLAIN, 11));
                evidLbl.setForeground(list.getForeground());
                cell.add(titleLbl);
                cell.add(evidLbl);
            } else {
                JLabel lbl = new JLabel(value);
                lbl.setFont(new Font("Monospaced", Font.PLAIN, 12));
                lbl.setForeground(list.getForeground());
                cell.add(lbl);
            }
            return cell;
        });

        listScroll = new JScrollPane(vulnList) {
            @Override public Dimension getPreferredSize() { return new Dimension(100, 100); }
            @Override public Dimension getMinimumSize()   { return new Dimension(0, 0); }
        };
        listScroll.setBorder(BorderFactory.createTitledBorder("漏洞风险"));
        listScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);

        // payload 区
        payloadArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        payloadArea.setEditable(false);
        payloadArea.setLineWrap(false);
        payloadScroll = new JScrollPane(payloadArea) {
            @Override public Dimension getPreferredSize() { return new Dimension(100, 100); }
            @Override public Dimension getMinimumSize()   { return new Dimension(0, 0); }
        };
        payloadScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        payloadScroll.setBorder(BorderFactory.createTitledBorder("Payload"));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, listScroll, payloadScroll);
        split.setResizeWeight(0.5);
        add(split, BorderLayout.CENTER);

        vulnList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int idx = vulnList.getSelectedIndex();
                if (idx >= 0 && idx < vulnEntries.size())
                    showPayloads(vulnEntries.get(idx));
            }
        });

        analyzeBtn.addActionListener(e -> doAnalyze());
    }

    public void onPacketSelected(PacketEntry entry) {
        SwingUtilities.invokeLater(() -> {
            vulnEntries.clear();
            vulnListModel.clear();
            payloadArea.setText("");
            if (entry == null) return;

            List<VulnEntry> cached = resultCache.get(entry);
            if (cached != null && !cached.isEmpty()) {
                vulnEntries.addAll(cached);              // 先填数据，getPreferredSize 调用时数据已就位
                for (VulnEntry v : cached)
                    vulnListModel.addElement(v.type);
                // 显式 validate 确保 scrollbar model 用最新 preferred size 更新
                SwingUtilities.invokeLater(() -> {
                    listScroll.validate();
                    listScroll.repaint();
                });
                vulnList.setSelectedIndex(0);
            }
        });
    }

    private void doAnalyze() {
        PacketEntry entry = selectedPacket.get();
        if (entry == null) { setPlaceholder("请先在左侧选中一个数据包"); return; }
        String requestRaw = currentRequestRaw.get();
        if (requestRaw == null || requestRaw.isEmpty()) {
            setPlaceholder("请求包内容为空，请重新选中数据包"); return;
        }
        analyzeBtn.setEnabled(false);
        setPlaceholder("正在分析，请稍候（约 15 ~ 30 秒）...");

        final PacketEntry analyzedEntry = entry;
        new Thread(() -> {
            try {
                String resp = BackendClient.analyzeVuln(requestRaw);
                String json = extractResultJson(resp);
                List<VulnEntry> entries = parseVulnerabilities(json);

                SwingUtilities.invokeLater(() -> {
                    resultCache.put(analyzedEntry, entries);
                    if (analyzedEntry != selectedPacket.get()) return;

                    vulnEntries.clear();
                    vulnListModel.clear();
                    payloadArea.setText("");

                    if (entries.isEmpty()) {
                        setPlaceholder("未发现明显漏洞");
                    } else {
                        vulnEntries.addAll(entries);          // 先填数据
                        for (VulnEntry v : entries)
                            vulnListModel.addElement(v.type);
                        SwingUtilities.invokeLater(() -> {
                            listScroll.validate();
                            listScroll.repaint();
                        });
                        vulnList.setSelectedIndex(0);
                    }
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    if (analyzedEntry == selectedPacket.get())
                        setPlaceholder("请求失败: " + ex.getMessage());
                });
            } finally {
                SwingUtilities.invokeLater(() -> analyzeBtn.setEnabled(true));
            }
        }).start();
    }

    private void showPayloads(VulnEntry entry) {
        String text = entry.payloads.isEmpty()
                ? "（无 Payload）"
                : String.join("\n", entry.payloads);
        payloadArea.setText(text);
        payloadArea.setCaretPosition(0);
        SwingUtilities.invokeLater(() -> {
            payloadScroll.revalidate();
            payloadScroll.repaint();
        });
    }

    private void setPlaceholder(String text) {
        vulnEntries.clear();
        vulnListModel.clear();
        vulnListModel.addElement(text);
        payloadArea.setText("");
    }

    // ── JSON 解析 ────────────────────────────────────────────────

    private String extractResultJson(String response) {
        Pattern dataPattern = Pattern.compile("\"data\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        Matcher dataMatcher = dataPattern.matcher(response);
        if (dataMatcher.find()) return unescape(dataMatcher.group(1));
        Pattern msgPattern = Pattern.compile("\"message\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        Matcher msgMatcher = msgPattern.matcher(response);
        if (msgMatcher.find()) return unescape(msgMatcher.group(1));
        return response;
    }

    private List<VulnEntry> parseVulnerabilities(String json) {
        List<VulnEntry> list = new ArrayList<>();
        String arrContent = extractArrayContent(json, "vulnerabilities");
        if (arrContent == null) return list;
        for (String obj : splitJsonObjects(arrContent)) {
            VulnEntry entry = new VulnEntry();
            Matcher t = Pattern.compile("\"type\"\\s*:\\s*\"([^\"]+)\"").matcher(obj);
            if (t.find()) entry.type = t.group(1);
            Matcher s = Pattern.compile("\"severity\"\\s*:\\s*\"([^\"]+)\"").matcher(obj);
            if (s.find()) entry.severity = s.group(1);
            Matcher ev = Pattern.compile("\"evidence\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(obj);
            if (ev.find()) entry.evidence = unescape(ev.group(1));
            Matcher p = Pattern.compile("\"payloads\"\\s*:\\s*\\[([^\\]]*?)\\]").matcher(obj);
            if (p.find()) {
                Matcher item = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)+)\"").matcher(p.group(1));
                while (item.find()) entry.payloads.add(unescape(item.group(1)));
            }
            if (entry.type != null) list.add(entry);
        }
        return list;
    }

    private String extractArrayContent(String json, String key) {
        int keyIdx = json.indexOf("\"" + key + "\"");
        if (keyIdx < 0) return null;
        int arrStart = json.indexOf('[', keyIdx);
        if (arrStart < 0) return null;
        int depth = 0; boolean inString = false;
        for (int i = arrStart; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"' && (i == 0 || json.charAt(i - 1) != '\\')) inString = !inString;
            if (inString) continue;
            if (c == '[') depth++;
            else if (c == ']') { if (--depth == 0) return json.substring(arrStart + 1, i); }
        }
        return null;
    }

    private List<String> splitJsonObjects(String content) {
        List<String> objects = new ArrayList<>();
        int depth = 0, start = -1; boolean inString = false;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '"' && (i == 0 || content.charAt(i - 1) != '\\')) inString = !inString;
            if (inString) continue;
            if (c == '{') { if (depth++ == 0) start = i; }
            else if (c == '}') { if (--depth == 0 && start >= 0) { objects.add(content.substring(start, i + 1)); start = -1; } }
        }
        return objects;
    }

    private String unescape(String s) {
        return s.replace("\\n", "\n").replace("\\t", "\t")
                .replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static class VulnEntry {
        String type = "";
        String severity = "";
        String evidence = "";
        List<String> payloads = new ArrayList<>();
    }
}
