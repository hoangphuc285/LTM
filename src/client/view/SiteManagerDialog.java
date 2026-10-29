package client.view;

import client.model.SiteProfile;
import client.service.SiteManagerService;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class SiteManagerDialog extends JDialog {
    private final DefaultListModel<SiteProfile> listModel = new DefaultListModel<>();
    private final JList<SiteProfile> siteList = new JList<>(listModel);

    private final JTextField nameField = new JTextField(20);
    private final JTextField hostField = new JTextField(20);
    private final JTextField portField = new JTextField("2121", 6);
    private final JComboBox<String> cmbProtocol = new JComboBox<>(new String[]{"FTP - File Transfer Protocol", "FTPS - FTP over TLS/SSL"});
    private final JComboBox<String> cmbLogonType = new JComboBox<>(new String[]{"Bình thường", "Ẩn danh (Anonymous)"});
    private final JTextField userField = new JTextField(20);
    private final JPasswordField passField = new JPasswordField(20);

    private final JButton btnAdd = new JButton("Them Site");
    private final JButton btnDelete = new JButton("Xoa");
    private final JButton btnDuplicate = new JButton("Nhan ban");
    private final JButton btnSave = new JButton("Luu cau hinh");
    private final JButton btnConnect = new JButton("Ket noi");
    private final JButton btnClose = new JButton("Dong");

    private Consumer<SiteProfile> onConnectCallback;

    public SiteManagerDialog(Frame parent) {
        super(parent, "Quản lý Site (Site Manager)", true);
        setSize(700, 450);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout(8, 8));

        // 1. Panel bên trái: Danh sách site
        JPanel leftPanel = new JPanel(new BorderLayout(6, 6));
        leftPanel.setBorder(BorderFactory.createTitledBorder(" Danh sách Site đã lưu "));
        leftPanel.setPreferredSize(new Dimension(220, 0));
        siteList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        leftPanel.add(new JScrollPane(siteList), BorderLayout.CENTER);

        JPanel leftBtns = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 4));
        leftBtns.add(btnAdd);
        leftBtns.add(btnDuplicate);
        leftBtns.add(btnDelete);
        leftPanel.add(leftBtns, BorderLayout.SOUTH);

        // 2. Panel bên phải: Form chi tiết
        JPanel rightPanel = new JPanel(new GridBagLayout());
        rightPanel.setBorder(BorderFactory.createTitledBorder(" Thông số kết nối chi tiết "));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(6, 8, 6, 8);

        int row = 0;
        addFormRow(rightPanel, gbc, row++, "Tên gợi nhớ:", nameField);
        addFormRow(rightPanel, gbc, row++, "Giao thức:", cmbProtocol);
        addFormRow(rightPanel, gbc, row++, "Máy chủ (Host):", hostField);
        addFormRow(rightPanel, gbc, row++, "Cổng (Port):", portField);
        addFormRow(rightPanel, gbc, row++, "Kiểu đăng nhập:", cmbLogonType);
        addFormRow(rightPanel, gbc, row++, "Tài khoản:", userField);
        addFormRow(rightPanel, gbc, row++, "Mật khẩu:", passField);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        split.setDividerLocation(230);
        add(split, BorderLayout.CENTER);

        // 3. Thanh nút dưới cùng
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        bottomPanel.add(btnSave);
        bottomPanel.add(btnConnect);
        bottomPanel.add(btnClose);
        add(bottomPanel, BorderLayout.SOUTH);

        initEvents();
        loadSites();
    }

    private void addFormRow(JPanel p, GridBagConstraints gbc, int row, String lbl, JComponent comp) {
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0;
        p.add(new JLabel(lbl), gbc);
        gbc.gridx = 1; gbc.gridy = row; gbc.weightx = 1.0;
        p.add(comp, gbc);
    }

    private void initEvents() {
        cmbLogonType.addActionListener(e -> {
            boolean isAnon = cmbLogonType.getSelectedIndex() == 1;
            userField.setEnabled(!isAnon);
            passField.setEnabled(!isAnon);
            if (isAnon) {
                userField.setText("anonymous");
                passField.setText("anonymous@example.com");
            }
        });

        siteList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                SiteProfile sel = siteList.getSelectedValue();
                if (sel != null) {
                    nameField.setText(sel.getName());
                    hostField.setText(sel.getHost());
                    portField.setText(String.valueOf(sel.getPort()));
                    cmbLogonType.setSelectedIndex(sel.isAnonymous() ? 1 : 0);
                    userField.setText(sel.getUsername());
                    passField.setText(sel.getPassword());
                    userField.setEnabled(!sel.isAnonymous());
                    passField.setEnabled(!sel.isAnonymous());
                }
            }
        });

        btnAdd.addActionListener(e -> {
            SiteProfile p = new SiteProfile();
            p.setName("Site mới " + (listModel.size() + 1));
            listModel.addElement(p);
            siteList.setSelectedValue(p, true);
        });

        btnDuplicate.addActionListener(e -> {
            SiteProfile sel = siteList.getSelectedValue();
            if (sel != null) {
                SiteProfile dup = new SiteProfile(String.valueOf(System.currentTimeMillis()),
                        sel.getName() + " (Bản sao)", sel.getHost(), sel.getPort(),
                        sel.getUsername(), sel.getPassword(), sel.isAnonymous());
                listModel.addElement(dup);
                siteList.setSelectedValue(dup, true);
            }
        });

        btnDelete.addActionListener(e -> {
            int idx = siteList.getSelectedIndex();
            if (idx != -1) {
                listModel.remove(idx);
                saveAllSites();
            }
        });

        btnSave.addActionListener(e -> {
            SiteProfile sel = siteList.getSelectedValue();
            if (sel != null) {
                sel.setName(nameField.getText().trim());
                sel.setHost(hostField.getText().trim());
                try {
                    sel.setPort(Integer.parseInt(portField.getText().trim()));
                } catch (Exception ex) {
                    sel.setPort(2121);
                }
                sel.setAnonymous(cmbLogonType.getSelectedIndex() == 1);
                sel.setUsername(userField.getText().trim());
                sel.setPassword(new String(passField.getPassword()));

                saveAllSites();
                siteList.repaint();
                JOptionPane.showMessageDialog(this, "Đã lưu thông tin cấu hình Site!", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
            }
        });

        btnConnect.addActionListener(e -> {
            SiteProfile sel = siteList.getSelectedValue();
            if (sel != null && onConnectCallback != null) {
                onConnectCallback.accept(sel);
                dispose();
            }
        });

        btnClose.addActionListener(e -> dispose());
    }

    private void saveAllSites() {
        List<SiteProfile> list = new ArrayList<>();
        for (int i = 0; i < listModel.size(); i++) {
            list.add(listModel.get(i));
        }
        SiteManagerService.saveSites(list);
    }

    private void loadSites() {
        listModel.clear();
        for (SiteProfile s : SiteManagerService.loadSites()) {
            listModel.addElement(s);
        }
        if (!listModel.isEmpty()) siteList.setSelectedIndex(0);
    }

    public void setOnConnectCallback(Consumer<SiteProfile> cb) { this.onConnectCallback = cb; }
}
