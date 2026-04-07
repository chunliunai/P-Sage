package com.example.psage.ui;

import com.example.psage.http.BackendClient;
import com.example.psage.model.PacketEntry;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ParamAnalysisPanel extends JPanel {

    private final Supplier<PacketEntry> selectedPacket;
    private final Supplier<String> currentRequestRaw;
    private final Supplier<String> currentResponseRaw;

    private final JTextArea resultArea = new JTextArea() {
        @Override public boolean getScrollableTracksViewportWidth() { return false; }
    };
    private JScrollPane resultScroll;

    // 缓存：每个数据包 × 每种分析类型 → 格式化结果
    private final Map<PacketEntry, Map<String, String>> resultCache = new HashMap<>();
    // 每个数据包上次查看的分析类型（切包后用于恢复显示）
    private final Map<PacketEntry, String> lastTypeCache = new HashMap<>();

    // 搜索相关
    private final JTextField searchField = new JTextField();
    private final JLabel matchLabel = new JLabel("");
    private final List<Integer> matchPositions = new ArrayList<>();
    private int currentMatchIndex = -1;
    private static final Color HIGHLIGHT_ALL     = new Color(255, 220, 0, 160);
    private static final Color HIGHLIGHT_CURRENT = new Color(255, 140, 0, 200);

    public ParamAnalysisPanel(Supplier<PacketEntry> selectedPacket,
                               Supplier<String> currentRequestRaw,
                               Supplier<String> currentResponseRaw) {
        this.selectedPacket = selectedPacket;
        this.currentRequestRaw = currentRequestRaw;
        this.currentResponseRaw = currentResponseRaw;

        setLayout(new BorderLayout(0, 4));
        setBorder(BorderFactory.createTitledBorder("参数分析"));

        // ── 分析按钮行 ──────────────────────────────────────────────
        JButton reqBtn  = new JButton("请求包分析");
        JButton respBtn = new JButton("返回包分析");
        JButton errBtn  = new JButton("报错分析");
        JButton allBtn  = new JButton("综合分析");

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        btnRow.add(reqBtn);
        btnRow.add(respBtn);
        btnRow.add(errBtn);
        btnRow.add(allBtn);

        // ── 搜索行 ──────────────────────────────────────────────────
        JButton findBtn = new JButton("查找");
        matchLabel.setFont(matchLabel.getFont().deriveFont(Font.PLAIN, 11f));
        matchLabel.setForeground(Color.GRAY);

        JPanel findControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        findControls.add(findBtn);
        findControls.add(matchLabel);

        JPanel searchRow = new JPanel(new BorderLayout(4, 0));
        searchRow.add(new JLabel(" 搜索: "), BorderLayout.WEST);
        searchRow.add(searchField, BorderLayout.CENTER);
        searchRow.add(findControls, BorderLayout.EAST);

        add(btnRow, BorderLayout.NORTH);

        // ── 结果展示区 ───────────────────────────────────────────────
        resultArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        resultArea.setEditable(false);
        resultArea.setLineWrap(false);
        resultScroll = new JScrollPane(resultArea) {
            @Override public Dimension getPreferredSize() { return new Dimension(100, 100); }
            @Override public Dimension getMinimumSize()   { return new Dimension(0, 0); }
        };
        resultScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        resultScroll.setBorder(BorderFactory.createTitledBorder("分析结果"));
        add(resultScroll, BorderLayout.CENTER);

        add(searchRow, BorderLayout.SOUTH);

        // ── 事件绑定 ─────────────────────────────────────────────────
        reqBtn .addActionListener(e -> doAnalyze("request",  true,  false));
        respBtn.addActionListener(e -> doAnalyze("response", false, true));
        errBtn .addActionListener(e -> doAnalyze("error",    false, true));
        allBtn .addActionListener(e -> doAnalyze("combined", true,  true));

        findBtn.addActionListener(e -> doSearchOrNext());
        searchField.addActionListener(e -> doSearchOrNext());

        // 输入框内容变化 → 清除旧高亮，等待下次查找
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { clearHighlights(); }
            public void removeUpdate(DocumentEvent e) { clearHighlights(); }
            public void changedUpdate(DocumentEvent e) { clearHighlights(); }
        });

        // ESC 清空搜索框
        searchField.registerKeyboardAction(
            e -> { searchField.setText(""); clearHighlights(); },
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_FOCUSED
        );
    }

    /** 切换数据包时由外部调用，恢复该包上次查看的缓存结果 */
    public void onPacketSelected(PacketEntry entry) {
        SwingUtilities.invokeLater(() -> {
            clearHighlights();
            if (entry == null) { setResultText(""); return; }
            String lastType = lastTypeCache.get(entry);
            if (lastType != null) {
                Map<String, String> packetCache = resultCache.get(entry);
                if (packetCache != null && packetCache.containsKey(lastType)) {
                    setResultText(packetCache.get(lastType));
                    resultArea.setCaretPosition(0);
                    return;
                }
            }
            setResultText("");
        });
    }

    private void doAnalyze(String type, boolean useRequest, boolean useResponse) {
        PacketEntry entry = selectedPacket.get();
        if (entry == null) {
            setResultText("请先在左侧选中一个数据包");
            return;
        }

        String requestRaw  = useRequest  ? currentRequestRaw.get()  : null;
        String responseRaw = useResponse ? currentResponseRaw.get() : null;

        if (useRequest && (requestRaw == null || requestRaw.isEmpty())) {
            setResultText("请求包内容为空，请重新选中数据包");
            return;
        }
        if (useResponse && (responseRaw == null || responseRaw.isEmpty())) {
            setResultText("返回包内容为空，请先点击 Send 发送请求");
            return;
        }

        // 命中缓存直接显示
        Map<String, String> packetCache = resultCache.get(entry);
        if (packetCache != null && packetCache.containsKey(type)) {
            String cached = packetCache.get(type);
            clearHighlights();
            setResultText(cached);
            resultArea.setCaretPosition(0);
            lastTypeCache.put(entry, type);
            return;
        }

        clearHighlights();
        setResultText("正在分析，请稍候（约 10 ~ 20 秒）...");

        final String reqRaw          = requestRaw;
        final String respRaw         = responseRaw;
        final PacketEntry analyzedEntry = entry;

        new Thread(() -> {
            try {
                String resp      = BackendClient.analyzeParam(reqRaw, respRaw);
                String json      = extractData(resp);
                String formatted = formatResult(json, type);

                SwingUtilities.invokeLater(() -> {
                    resultCache.computeIfAbsent(analyzedEntry, k -> new HashMap<>()).put(type, formatted);
                    lastTypeCache.put(analyzedEntry, type);
                    if (analyzedEntry == selectedPacket.get()) {
                        setResultText(formatted);
                        resultArea.setCaretPosition(0);
                    }
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    if (analyzedEntry == selectedPacket.get())
                        setResultText("请求失败: " + ex.getMessage());
                });
            }
        }).start();
    }

    /** setText + setPreferredSize 锁定宽度，确保横向滚动条不因布局刷新而消失 */
    private void setResultText(String text) {
        resultArea.setText(text);
        SwingUtilities.invokeLater(() -> {
            if (resultScroll == null) return;
            Font font = resultArea.getFont() != null
                    ? resultArea.getFont() : new Font("Monospaced", Font.PLAIN, 12);
            BufferedImage bi = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            Graphics g = bi.createGraphics();
            FontMetrics fm = g.getFontMetrics(font);
            g.dispose();
            int aW = fm.charWidth('M');
            int maxW = 0;
            String[] lines = (text != null && !text.isEmpty()) ? text.split("\n", -1) : new String[0];
            for (String line : lines) {
                if (line.isEmpty()) continue;
                int w = 0;
                for (char c : line.toCharArray())
                    w += (c > 0x2000) ? aW * 2 : fm.charWidth(c);
                maxW = Math.max(maxW, w + 8);
            }
            int prefH = Math.max(lines.length * fm.getHeight() + 8, 20);
            resultArea.setPreferredSize(new Dimension(Math.max(maxW, 50), prefH));
            resultScroll.validate();
            resultScroll.repaint();
        });
    }

    // ── 搜索功能 ──────────────────────────────────────────────────────

    /** 首次查找时高亮所有匹配并跳到第一个；已有结果时跳下一个 */
    private void doSearchOrNext() {
        String keyword = searchField.getText();
        if (keyword.isEmpty()) return;

        // 如果有上次结果且关键词未变，直接跳下一个
        if (!matchPositions.isEmpty()) {
            currentMatchIndex = (currentMatchIndex + 1) % matchPositions.size();
            scrollToCurrentMatch();
            return;
        }

        // 重新搜索
        Highlighter hl = resultArea.getHighlighter();
        hl.removeAllHighlights();
        matchPositions.clear();
        currentMatchIndex = -1;
        matchLabel.setText("");

        String text = resultArea.getText();
        String kwLower = keyword.toLowerCase();
        String textLower = text.toLowerCase();

        Highlighter.HighlightPainter painter =
            new DefaultHighlighter.DefaultHighlightPainter(HIGHLIGHT_ALL);

        int idx = 0;
        while ((idx = textLower.indexOf(kwLower, idx)) >= 0) {
            try {
                hl.addHighlight(idx, idx + keyword.length(), painter);
                matchPositions.add(idx);
            } catch (BadLocationException ignored) {}
            idx += keyword.length();
        }

        if (matchPositions.isEmpty()) {
            matchLabel.setText("无结果");
        } else {
            currentMatchIndex = 0;
            scrollToCurrentMatch();
        }
    }

    private void scrollToCurrentMatch() {
        if (matchPositions.isEmpty()) return;
        int pos = matchPositions.get(currentMatchIndex);
        int len = searchField.getText().length();

        // 重绘当前匹配为深色
        Highlighter hl = resultArea.getHighlighter();
        hl.removeAllHighlights();
        Highlighter.HighlightPainter allPainter =
            new DefaultHighlighter.DefaultHighlightPainter(HIGHLIGHT_ALL);
        Highlighter.HighlightPainter curPainter =
            new DefaultHighlighter.DefaultHighlightPainter(HIGHLIGHT_CURRENT);
        for (int i = 0; i < matchPositions.size(); i++) {
            int p = matchPositions.get(i);
            try {
                hl.addHighlight(p, p + len, i == currentMatchIndex ? curPainter : allPainter);
            } catch (BadLocationException ignored) {}
        }

        resultArea.setCaretPosition(pos);
        matchLabel.setText((currentMatchIndex + 1) + "/" + matchPositions.size());
    }

    private void clearHighlights() {
        resultArea.getHighlighter().removeAllHighlights();
        matchPositions.clear();
        currentMatchIndex = -1;
        matchLabel.setText("");
    }

    // ── 后端通信 & 格式化 ─────────────────────────────────────────────

    private String extractData(String response) {
        Pattern mp = Pattern.compile("\"message\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        Matcher mm = mp.matcher(response);
        if (mm.find()) {
            String msg = unescape(mm.group(1));
            boolean isSuccess = response.contains("\"code\":200") || response.contains("\"code\": 200");
            return isSuccess ? msg : "错误：" + msg;
        }
        return response;
    }

    private String formatResult(String json, String type) {
        if (json.startsWith("错误：")) return json;

        StringBuilder sb = new StringBuilder();

        if ("request".equals(type) || "combined".equals(type)) {
            String reqArr = extractArray(json, "requestParams");
            if (reqArr != null) {
                sb.append("【请求参数】\n");
                sb.append("─".repeat(50)).append("\n");
                for (String obj : splitObjects(reqArr)) appendParam(sb, obj);
            }
        }

        if ("response".equals(type) || "combined".equals(type)) {
            String respArr = extractArray(json, "responseParams");
            if (respArr != null) {
                sb.append("【返回字段】\n");
                sb.append("─".repeat(50)).append("\n");
                for (String obj : splitObjects(respArr)) appendParam(sb, obj);
            }
        }

        if ("error".equals(type) || "combined".equals(type)) {
            String errObj = extractObject(json, "errorAnalysis");
            String hasError = errObj != null ? extractBool(errObj, "hasError") : null;
            if ("true".equals(hasError)) {
                sb.append("【报错分析】\n");
                sb.append("─".repeat(50)).append("\n");
                appendField(sb, "错误类型", extractStr(errObj, "errorType"));
                appendField(sb, "摘  要",   extractStr(errObj, "summary"));
                appendField(sb, "说  明",   extractStr(errObj, "explanation"));
                appendField(sb, "触发原因", extractStr(errObj, "possibleCause"));
                appendField(sb, "安全暴露", extractStr(errObj, "securityExposure"));
            } else {
                sb.append("【报错分析】未检测到明显报错信息\n");
            }
        }

        return sb.length() > 0 ? sb.toString().trim() : json;
    }

    private void appendParam(StringBuilder sb, String obj) {
        String name         = extractStr(obj, "name");
        String value        = extractStr(obj, "value");
        String translation  = extractStr(obj, "translation");
        String businessRole = extractStr(obj, "businessRole");

        if (name != null) sb.append("参数名: ").append(name).append("\n");
        if (translation  != null && !translation.isEmpty())  sb.append("中  文: ").append(translation).append("\n");
        if (value        != null && !value.isEmpty())        sb.append("参数值: ").append(value).append("\n");
        if (businessRole != null && !businessRole.isEmpty()) sb.append("业务含义: ").append(businessRole).append("\n");
        sb.append("\n");
    }

    private void appendField(StringBuilder sb, String label, String value) {
        if (value != null && !value.isEmpty())
            sb.append(label).append(": ").append(value).append("\n");
    }

    // ── JSON 工具 ──────────────────────────────────────────────────────

    private String extractArray(String json, String key) {
        int keyIdx = json.indexOf("\"" + key + "\"");
        if (keyIdx < 0) return null;
        int arrStart = json.indexOf('[', keyIdx);
        if (arrStart < 0) return null;
        int depth = 0; boolean inStr = false;
        for (int i = arrStart; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"' && (i == 0 || json.charAt(i - 1) != '\\')) inStr = !inStr;
            if (inStr) continue;
            if (c == '[') depth++;
            else if (c == ']') { if (--depth == 0) return json.substring(arrStart + 1, i); }
        }
        return null;
    }

    private String extractObject(String json, String key) {
        int keyIdx = json.indexOf("\"" + key + "\"");
        if (keyIdx < 0) return null;
        int objStart = json.indexOf('{', keyIdx);
        if (objStart < 0) return null;
        int depth = 0; boolean inStr = false;
        for (int i = objStart; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"' && (i == 0 || json.charAt(i - 1) != '\\')) inStr = !inStr;
            if (inStr) continue;
            if (c == '{') depth++;
            else if (c == '}') { if (--depth == 0) return json.substring(objStart + 1, i); }
        }
        return null;
    }

    private List<String> splitObjects(String content) {
        List<String> list = new ArrayList<>();
        int depth = 0, start = -1; boolean inStr = false;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '"' && (i == 0 || content.charAt(i - 1) != '\\')) inStr = !inStr;
            if (inStr) continue;
            if (c == '{') { if (depth++ == 0) start = i; }
            else if (c == '}') { if (--depth == 0 && start >= 0) { list.add(content.substring(start + 1, i)); start = -1; } }
        }
        return list;
    }

    private String extractStr(String json, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
        return m.find() ? unescape(m.group(1)) : null;
    }

    private String extractBool(String json, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*(true|false)").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private String unescape(String s) {
        return s.replace("\\n", "\n").replace("\\t", "\t")
                .replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
