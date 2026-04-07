package com.example.psage.ui;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.core.ByteArray;
import burp.api.montoya.ui.editor.EditorOptions;
import burp.api.montoya.ui.editor.HttpRequestEditor;
import burp.api.montoya.ui.editor.HttpResponseEditor;
import com.example.psage.model.PacketEntry;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class LeftPanel extends JPanel {

    private final MontoyaApi api;
    private final List<PacketEntry> entries = new ArrayList<>();
    private final DefaultTableModel tableModel;
    private final JTable table;

    // Burp 原生编辑器：自带 Pretty/Raw/Hex Tab + 语法高亮
    private final HttpRequestEditor  requestEditor;
    private final HttpResponseEditor responseEditor;

    private final JButton sendBtn = new JButton("Send");
    private Consumer<PacketEntry> onPacketSelectedCallback = null;
    private int hoverRow = -1;

    public void setOnPacketSelected(Consumer<PacketEntry> callback) {
        this.onPacketSelectedCallback = callback;
    }

    public LeftPanel(MontoyaApi api) {
        this.api = api;
        setLayout(new BorderLayout());

        // 创建 Burp 原生编辑器
        requestEditor  = api.userInterface().createHttpRequestEditor();
        responseEditor = api.userInterface().createHttpResponseEditor(EditorOptions.READ_ONLY);

        // ── 数据包列表 ──────────────────────────────────────────
        tableModel = new DefaultTableModel(new String[]{"方法", "状态", "URL"}, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
        table = new JTable(tableModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFont(new Font("Monospaced", Font.PLAIN, 12));
        table.getColumnModel().getColumn(0).setMaxWidth(70);
        table.getColumnModel().getColumn(1).setMaxWidth(60);

        // URL 列 Renderer：hover 时右端显示灰色 ✕
        table.getColumnModel().getColumn(2).setCellRenderer((t, value, isSelected, hasFocus, row, col) -> {
            JPanel cell = new JPanel(new BorderLayout(0, 0));
            cell.setOpaque(true);
            cell.setBackground(isSelected ? t.getSelectionBackground() : t.getBackground());
            JLabel urlLbl = new JLabel(value != null ? value.toString() : "");
            urlLbl.setFont(t.getFont());
            urlLbl.setForeground(isSelected ? t.getSelectionForeground() : t.getForeground());
            urlLbl.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 0));
            cell.add(urlLbl, BorderLayout.CENTER);
            if (row == hoverRow) {
                JPanel eastWrap = new JPanel(new BorderLayout());
                eastWrap.setOpaque(false);
                eastWrap.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 10));
                JLabel xLbl = new JLabel("✕", SwingConstants.CENTER);
                xLbl.setForeground(Color.GRAY);
                eastWrap.add(xLbl, BorderLayout.CENTER);
                cell.add(eastWrap, BorderLayout.EAST);
            }
            return cell;
        });

        // 鼠标进入 URL 列右侧 35px 触发 hover，移出或离开表格则清除
        table.addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                boolean inDeleteZone = false;
                if (row >= 0) {
                    Rectangle urlRect = table.getCellRect(row, 2, false);
                    inDeleteZone = e.getX() >= urlRect.x + urlRect.width - 35;
                }
                int newHoverRow = inDeleteZone ? row : -1;
                if (newHoverRow != hoverRow) {
                    hoverRow = newHoverRow;
                    table.repaint();
                }
            }
        });
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseExited(MouseEvent e) {
                hoverRow = -1;
                table.repaint();
            }
            // 左键点击 URL 列右侧 25px 区域 → 删除该行
            @Override public void mouseClicked(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) return;
                int row = table.rowAtPoint(e.getPoint());
                if (row >= 0 && row == hoverRow) {
                    Rectangle urlRect = table.getCellRect(row, 2, false);
                    if (e.getX() >= urlRect.x + urlRect.width - 25) {
                        deleteRow(row);
                        return;
                    }
                }
            }
            @Override public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) showContextMenu(e);
            }
            @Override public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) showContextMenu(e);
            }
        });

        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = table.getSelectedRow();
                if (row >= 0 && row < entries.size()) {
                    PacketEntry selected = entries.get(row);
                    fillEditor(selected);
                    if (onPacketSelectedCallback != null) {
                        onPacketSelectedCallback.accept(selected);
                    }
                }
            }
        });
        JScrollPane tableScroll = new JScrollPane(table);

        // ── Send 工具栏（列表和编辑区之间，始终可见）──────────────
        sendBtn.addActionListener(e -> doSend());
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        toolbar.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, Color.LIGHT_GRAY));
        toolbar.add(sendBtn);

        JPanel topArea = new JPanel(new BorderLayout());
        topArea.add(tableScroll, BorderLayout.CENTER);
        topArea.add(toolbar, BorderLayout.SOUTH);

        // ── 请求/返回包编辑区（Burp 原生，左右分割）──────────────
        JPanel requestPanel = new JPanel(new BorderLayout());
        requestPanel.setBorder(BorderFactory.createTitledBorder("请求包"));
        requestPanel.add(requestEditor.uiComponent(), BorderLayout.CENTER);

        JPanel responsePanel = new JPanel(new BorderLayout());
        responsePanel.setBorder(BorderFactory.createTitledBorder("返回包"));
        responsePanel.add(responseEditor.uiComponent(), BorderLayout.CENTER);

        JSplitPane editorSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, requestPanel, responsePanel);
        editorSplit.setResizeWeight(0.5);
        editorSplit.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                editorSplit.setDividerLocation(0.5);
                editorSplit.removeComponentListener(this);
            }
        });

        // ── 整体上下分割 ────────────────────────────────────────
        JSplitPane outerSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topArea, editorSplit);
        outerSplit.setDividerLocation(185);
        outerSplit.setResizeWeight(0.25);

        add(outerSplit, BorderLayout.CENTER);
    }

    /** 外部调用：获取当前选中的数据包 */
    public PacketEntry getSelectedEntry() {
        int row = table.getSelectedRow();
        if (row >= 0 && row < entries.size()) return entries.get(row);
        return null;
    }

    /** 外部调用：获取当前选中数据包的返回包原始文本 */
    public String getCurrentResponseRaw() {
        PacketEntry entry = getSelectedEntry();
        if (entry == null) return "";
        String raw = entry.getResponseRaw();
        return raw != null ? raw : "";
    }

    /** 外部调用：获取编辑器当前显示的请求包原始文本（供分析使用，反映用户最新修改） */
    public String getCurrentRequestRaw() {
        try {
            HttpRequest req = requestEditor.getRequest();
            if (req == null) return "";
            return new String(req.toByteArray().getBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    /** 外部调用：新增数据包到列表 */
    public void addPacket(PacketEntry entry) {
        SwingUtilities.invokeLater(() -> {
            entries.add(entry);
            tableModel.addRow(new Object[]{entry.getMethod(), "-", entry.getUrl()});
            int row = entries.size() - 1;
            table.setRowSelectionInterval(row, row);
            fillEditor(entry);
        });
    }

    /** 填充请求/返回包编辑器 */
    private void fillEditor(PacketEntry entry) {
        // 使用原始 HttpRequest（含 service 信息），编辑器自动渲染语法高亮
        requestEditor.setRequest(entry.getOriginalRequest());

        if (entry.getResponseRaw() != null && !entry.getResponseRaw().isEmpty()) {
            HttpResponse resp = HttpResponse.httpResponse(
                ByteArray.byteArray(entry.getResponseRaw().getBytes(StandardCharsets.UTF_8))
            );
            responseEditor.setResponse(resp);
        } else {
            responseEditor.setResponse(HttpResponse.httpResponse(ByteArray.byteArray(new byte[0])));
        }

    }

    /** 删除单行 */
    private void deleteRow(int row) {
        if (row < 0 || row >= entries.size()) return;
        entries.remove(row);
        tableModel.removeRow(row);
        if (entries.isEmpty()) {
            if (onPacketSelectedCallback != null) onPacketSelectedCallback.accept(null);
        } else {
            int nextRow = Math.min(row, entries.size() - 1);
            table.setRowSelectionInterval(nextRow, nextRow);
            PacketEntry next = entries.get(nextRow);
            fillEditor(next);
            if (onPacketSelectedCallback != null) onPacketSelectedCallback.accept(next);
        }
    }

    /** 清除全部 */
    private void clearAll() {
        entries.clear();
        tableModel.setRowCount(0);
        if (onPacketSelectedCallback != null) onPacketSelectedCallback.accept(null);
    }

    /** 右键菜单：仅在有数据的行上触发 */
    private void showContextMenu(MouseEvent e) {
        int row = table.rowAtPoint(e.getPoint());
        if (row < 0 || row >= entries.size()) return;
        JPopupMenu menu = new JPopupMenu();
        JMenuItem clearAllItem = new JMenuItem("Clear All");
        clearAllItem.addActionListener(evt -> clearAll());
        menu.add(clearAllItem);
        menu.show(table, e.getX(), e.getY());
    }

    /** 发包 */
    private void doSend() {
        int row = table.getSelectedRow();
        if (row < 0) { api.logging().logToOutput("请先选择一个数据包"); return; }

        PacketEntry entry = entries.get(row);

        // 从编辑器拿取当前内容（用户可能已修改），含 service 信息，可直接发送
        HttpRequest editedRequest = requestEditor.getRequest();
        entry.setRequestRaw(editedRequest.toString());

        sendBtn.setEnabled(false);

        new Thread(() -> {
            try {
                var result = api.http().sendRequest(editedRequest);
                int status = result.response() != null ? result.response().statusCode() : 0;
                String responseStr = result.response() != null ? result.response().toString() : "";

                entry.setResponseRaw(responseStr);
                entry.setStatusCode(status);

                SwingUtilities.invokeLater(() -> {
                    tableModel.setValueAt(String.valueOf(status), row, 1);
                    if (result.response() != null) {
                        // 直接传 Burp 原生 response 对象，语法高亮正常渲染
                        responseEditor.setResponse(result.response());
                    }
                    sendBtn.setEnabled(true);
                });
            } catch (Exception ex) {
                api.logging().logToError("发包失败: " + ex.getMessage());
                SwingUtilities.invokeLater(() -> sendBtn.setEnabled(true));
            }
        }).start();
    }
}
