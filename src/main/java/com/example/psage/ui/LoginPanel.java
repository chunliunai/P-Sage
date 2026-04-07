package com.example.psage.ui;

import com.example.psage.http.BackendClient;

import javax.swing.*;
import java.awt.*;

public class LoginPanel extends JPanel {

    private final JTextField usernameField = new JTextField(15);
    private final JPasswordField passwordField = new JPasswordField(15);
    private final JButton loginButton = new JButton("登录");
    private final JLabel statusLabel = new JLabel("未登录");

    public LoginPanel() {
        setLayout(new GridBagLayout());
        setBorder(BorderFactory.createTitledBorder("账户登录"));

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 6, 4, 6);
        c.fill = GridBagConstraints.HORIZONTAL;

        // 账号
        c.gridx = 0; c.gridy = 0; c.weightx = 0;
        add(new JLabel("账号:"), c);
        c.gridx = 1; c.weightx = 1;
        add(usernameField, c);

        // 密码
        c.gridx = 0; c.gridy = 1; c.weightx = 0;
        add(new JLabel("密码:"), c);
        c.gridx = 1; c.weightx = 1;
        add(passwordField, c);

        // 登录按钮 + 状态
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        btnRow.add(loginButton);
        btnRow.add(Box.createHorizontalStrut(10));
        btnRow.add(statusLabel);
        c.gridx = 0; c.gridy = 2; c.gridwidth = 2;
        add(btnRow, c);

        loginButton.addActionListener(e -> doLogin());

        // 回车触发登录
        passwordField.addActionListener(e -> doLogin());
    }

    private void doLogin() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());
        if (username.isEmpty() || password.isEmpty()) {
            setStatus("账号或密码不能为空", Color.RED);
            return;
        }
        loginButton.setEnabled(false);
        setStatus("登录中...", Color.GRAY);

        new Thread(() -> {
            String result = BackendClient.login(username, password);
            SwingUtilities.invokeLater(() -> {
                loginButton.setEnabled(true);
                if (result.startsWith("success:")) {
                    String user = result.substring(8);
                    setStatus("已登录: " + user, new Color(0, 150, 0));
                } else {
                    String msg = result.startsWith("error:") ? result.substring(6) : result;
                    setStatus("失败: " + msg, Color.RED);
                }
            });
        }).start();
    }

    private void setStatus(String text, Color color) {
        statusLabel.setText(text);
        statusLabel.setForeground(color);
    }
}
