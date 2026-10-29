package client.view;

import client.model.SiteProfile;
import client.service.SiteManagerService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class SiteManagerDialog extends JDialog {
    private final DefaultListModel<SiteProfile> listModel = new DefaultListModel<>();
    private final JList<SiteProfile> siteList = new JList<>(listModel);

    private final JTextField nameField = new JTextField(20);
    private final JTextField hostField = new JTextField(20);
    private final JTextField portField = new JTextField(6);
    private final JCheckBox chkAnonymous = new JCheckBox("Đăng nhập ẩn danh (Anonymous)");
    private final JTextField userField = new JTextField(20);
    private final JPasswordField passField = new JPasswordField(20);

    private final JButton btnAdd = new JButton("➕ Thêm Site");
    private final JButton btnDelete = new JButton("🗑️ Xóa");
    private final JButton btnDuplicate = new JButton("📋 Nhân bản");

    private final JButton btnConnect = new JButton("🚀 Kết nối");
    private final JButton btnSave = new JButton("💾 Lưu cấu hình");
    private final JButton btnClose = new JButton("Đóng");

    private SiteProfile currentSelectedSite = null;
    private boolean isUpdatingFields = false;

    private Consumer<SiteProfile> onConnectCallback;

    public SiteManagerDialog(Frame parent) {
        super(parent, "Quản lý Site (Site Manager)", true);
        setSize(650, 420);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout(8, 8));

        // Panel bên trái: Danh sách các Site
        JPanel leftPanel = new JPanel(new BorderLayout(5, 5));
        leftPanel.setBorder(BorderFactory.createTitledBorder(" Danh sách Site đã lưu "));
        leftPanel.setPreferredSize(new Dimension(240, 0));

        siteList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        leftPanel.add(new JScrollPane(siteList), BorderLayout.CENTER);

        JPanel leftBtnBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 4));
        leftBtnBar.add(btnAdd);
        leftBtnBar.add(btnDuplicate);
        leftBtnBar.add(btnDelete);
        leftPanel.add(leftBtnBar, BorderLayout.SOUTH);

        // Panel bên phải: Chi tiết Site
        JPanel rightPanel = new JPanel(new BorderLayout(5, 5));
        rightPanel.setBorder(BorderFactory.createTitledBorder(" Thông tin kết nối chi tiết "));

        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(6, 6, 6, 6);

        int row = 0;
        // Tên site
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0;
        formPanel.add(new JLabel("Tên gợi nhớ:"), gbc);
        gbc.gridx = 1; gbc.gridy = row++; gbc.weightx = 1.0;
        formPanel.add(nameField, gbc);

        // Host
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0;
        formPanel.add(new JLabel("Máy chủ (Host):"), gbc);
        gbc.gridx = 1; gbc.gridy = row++; gbc.weightx = 1.0;
        formPanel.add(hostField, gbc);

        // Port
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0;
        formPanel.add(new JLabel("Cổng (Port):"), gbc);
        gbc.gridx = 1; gbc.gridy = row++; gbc.weightx = 1.0;
        formPanel.add(portField, gbc);

        // Anonymous checkbox
        gbc.gridx = 1; gbc.gridy = row++; gbc.weightx = 1.0;
        formPanel.add(chkAnonymous, gbc);

        // Username
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0;
        formPanel.add(new JLabel("Tài khoản:"), gbc);
        gbc.gridx = 1; gbc.gridy = row++; gbc.weightx = 1.0;
        formPanel.add(userField, gbc);

        // Password
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0;
        formPanel.add(new JLabel("Mật khẩu:"), gbc);
        gbc.gridx = 1; gbc.gridy = row++; gbc.weightx = 1.0;
        formPanel.add(passField, gbc);

        rightPanel.add(formPanel, BorderLayout.NORTH);

        // Split pane kết hợp
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        splitPane.setDividerLocation(240);
        add(splitPane, BorderLayout.CENTER);

        // Thanh nút phía dưới
        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        bottomBar.add(btnConnect);
        bottomBar.add(btnSave);
        bottomBar.add(btnClose);
        add(bottomBar, BorderLayout.SOUTH);

        // Sự kiện Anonymous checkbox
        chkAnonymous.addActionListener(e -> {
            boolean anon = chkAnonymous.isSelected();
            userField.setEnabled(!anon);
            passField.setEnabled(!anon);
            if (anon) {
                userField.setText("anonymous");
                passField.setText("anonymous@example.com");
            }
        });

        // Sự kiện chọn trên list
        siteList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                if (currentSelectedSite != null) {
                    saveFormToSite(currentSelectedSite);
                }
                SiteProfile selected = siteList.getSelectedValue();
                if (selected != null) {
                    currentSelectedSite = selected;
                    loadSiteToForm(selected);
                }
            }
        });

        // Nút thêm mới
        btnAdd.addActionListener(e -> {
            if (currentSelectedSite != null) {
                saveFormToSite(currentSelectedSite);
            }
            SiteProfile newSite = new SiteProfile();
            newSite.setName("Site mới " + (listModel.size() + 1));
            listModel.addElement(newSite);
            siteList.setSelectedValue(newSite, true);
        });

        // Nút nhân bản
        btnDuplicate.addActionListener(e -> {
            SiteProfile selected = siteList.getSelectedValue();
            if (selected == null) return;
            saveFormToSite(selected);

            SiteProfile copy = new SiteProfile(
                    String.valueOf(System.currentTimeMillis()),
                    selected.getName() + " (Bản sao)",
                    selected.getHost(),
                    selected.getPort(),
                    selected.getUsername(),
                    selected.getPassword(),
                    selected.isAnonymous()
            );
            listModel.addElement(copy);
            siteList.setSelectedValue(copy, true);
        });

        // Nút xóa
        btnDelete.addActionListener(e -> {
            int idx = siteList.getSelectedIndex();
            if (idx == -1) return;
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Bạn có chắc muốn xóa site này?",
                    "Xác nhận xóa", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                currentSelectedSite = null;
                listModel.remove(idx);
                if (listModel.getSize() > 0) {
                    int nextIdx = Math.min(idx, listModel.getSize() - 1);
                    siteList.setSelectedIndex(nextIdx);
                } else {
                    clearForm();
                }
                saveAllSites();
            }
        });

        // Nút Lưu
        btnSave.addActionListener(e -> {
            if (currentSelectedSite != null) {
                saveFormToSite(currentSelectedSite);
            }
            saveAllSites();
            JOptionPane.showMessageDialog(this, "Đã lưu danh sách Site thành công!", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
        });

        // Nút Kết nối
        btnConnect.addActionListener(e -> {
            if (currentSelectedSite != null) {
                saveFormToSite(currentSelectedSite);
            }
            saveAllSites();
            SiteProfile selected = siteList.getSelectedValue();
            if (selected != null && onConnectCallback != null) {
                dispose();
                onConnectCallback.accept(selected);
            } else {
                JOptionPane.showMessageDialog(this, "Vui lòng chọn một Site để kết nối!", "Thông báo", JOptionPane.WARNING_MESSAGE);
            }
        });

        btnClose.addActionListener(e -> {
            if (currentSelectedSite != null) {
                saveFormToSite(currentSelectedSite);
            }
            saveAllSites();
            dispose();
        });

        loadAllSites();
    }

    public void setOnConnectCallback(Consumer<SiteProfile> callback) {
        this.onConnectCallback = callback;
    }

    private void loadAllSites() {
        List<SiteProfile> sites = SiteManagerService.loadSites();
        listModel.clear();
        for (SiteProfile site : sites) {
            listModel.addElement(site);
        }
        if (!listModel.isEmpty()) {
            siteList.setSelectedIndex(0);
        }
    }

    private void saveAllSites() {
        List<SiteProfile> sites = new ArrayList<>();
        for (int i = 0; i < listModel.size(); i++) {
            sites.add(listModel.get(i));
        }
        SiteManagerService.saveSites(sites);
    }

    private void loadSiteToForm(SiteProfile site) {
        isUpdatingFields = true;
        nameField.setText(site.getName());
        hostField.setText(site.getHost());
        portField.setText(String.valueOf(site.getPort()));
        chkAnonymous.setSelected(site.isAnonymous());
        userField.setText(site.getUsername());
        passField.setText(site.getPassword());

        boolean anon = site.isAnonymous();
        userField.setEnabled(!anon);
        passField.setEnabled(!anon);
        isUpdatingFields = false;
    }

    private void saveFormToSite(SiteProfile site) {
        if (isUpdatingFields || site == null) return;
        site.setName(nameField.getText().trim());
        site.setHost(hostField.getText().trim());
        try {
            site.setPort(Integer.parseInt(portField.getText().trim()));
        } catch (NumberFormatException ignored) {}
        site.setAnonymous(chkAnonymous.isSelected());
        site.setUsername(userField.getText().trim());
        site.setPassword(new String(passField.getPassword()));
        siteList.repaint();
    }

    private void clearForm() {
        nameField.setText("");
        hostField.setText("");
        portField.setText("");
        chkAnonymous.setSelected(false);
        userField.setText("");
        passField.setText("");
        userField.setEnabled(true);
        passField.setEnabled(true);
    }
}

