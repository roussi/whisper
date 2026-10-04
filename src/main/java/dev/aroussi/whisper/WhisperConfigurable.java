package dev.aroussi.whisper;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.xmlb.XmlSerializerUtil;
import dev.aroussi.whisper.recorder.Recorder;
import dev.aroussi.whisper.setup.LocalSetup;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public final class WhisperConfigurable implements Configurable {

    private static final String CARD_LOCAL = "LOCAL";
    private static final String CARD_OPENAI = "OPENAI";
    private static final String CARD_GROQ = "GROQ";
    private static final AtomicBoolean SETUP_RUNNING = new AtomicBoolean();

    private static final Map<String, String> MODEL_CHOICES = new LinkedHashMap<>() {{
        put("tiny", "tiny — ~75 MB, fastest");
        put("base", "base — ~150 MB, balanced (recommended)");
        put("small", "small — ~500 MB, better accuracy");
        put("medium", "medium — ~1.5 GB, high accuracy");
    }};

    private static final Map<String, String> LANGUAGE_CHOICES = new LinkedHashMap<>() {{
        put("en", "English");
        put("fr", "French");
        put("de", "German");
        put("es", "Spanish");
        put("it", "Italian");
        put("pt", "Portuguese");
        put("nl", "Dutch");
        put("ja", "Japanese");
        put("zh", "Chinese");
        put("ko", "Korean");
        put("ar", "Arabic");
        put("ru", "Russian");
        put("hi", "Hindi");
    }};

    private ScrollableVPanel root;

    private BackendCard localCard;
    private BackendCard openaiCard;
    private BackendCard groqCard;
    private JPanel detailsPanel;
    private CardLayout detailsLayout;
    private WhisperSettings.Backend selectedBackend;

    private ComboBox<String> localModelBox;
    private JBLabel localStatusLabel;
    private JButton localSetupButton;
    private JBLabel localReinstallLink;
    private JTextArea localPathLabel;
    private JBTextField localPathOverrideField;

    private JBPasswordField openaiKeyField;
    private JBLabel openaiTestLabel;
    private JBPasswordField groqKeyField;
    private JBLabel groqTestLabel;

    private JRadioButton modeDictate;
    private JRadioButton modeCode;
    private JRadioButton modeCommand;

    private JRadioButton toolAuto;
    private JRadioButton toolSox;
    private JRadioButton toolFfmpeg;
    private JTextArea audioDetectedLabel;

    private ComboBox<String> languageBox;
    private JCheckBox notificationsBox;

    private Runnable onChange;
    private boolean suppressChange;
    private WhisperSettings.State initialState;

    public void setOnChange(Runnable r) { this.onChange = r; }
    private void fireChanged() { if (!suppressChange && onChange != null) onChange.run(); }

    @Override public @Nls String getDisplayName() { return "Whisper"; }

    @Override
    public @Nullable JComponent createComponent() {
        root = new ScrollableVPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBorder(new EmptyBorder(JBUI.insets(16, 16, 16, 16)));

        root.add(buildBackendSection(true));
        root.add(sectionSeparator());
        root.add(buildModesSection(false));
        root.add(sectionSeparator());
        root.add(buildAudioSection(false));
        root.add(sectionSeparator());
        root.add(buildAdvancedSection(false));
        root.add(Box.createVerticalGlue());

        JScrollPane scroll = new JScrollPane(root,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        watchChoice(localModelBox);
        watchChoice(languageBox);
        reset();
        return scroll;
    }

    // ---------- sections ----------

    private JComponent buildBackendSection(boolean expanded) {
        SectionPanel section = section("Transcription Backend",
                "Choose where audio is sent. Local stays on your machine; cloud backends require an API key.",
                expanded);

        JPanel cards = new JPanel();
        cards.setLayout(new BoxLayout(cards, BoxLayout.Y_AXIS));
        cards.setOpaque(false);
        cards.setAlignmentX(Component.LEFT_ALIGNMENT);
        localCard = new BackendCard(WhisperSettings.Backend.LOCAL, "Local",
                "whisper.cpp — runs offline, free");
        openaiCard = new BackendCard(WhisperSettings.Backend.OPENAI, "OpenAI",
                "Whisper API — fast, paid");
        groqCard = new BackendCard(WhisperSettings.Backend.GROQ, "Groq",
                "Whisper Large V3 — very fast, paid");
        cards.add(localCard);
        cards.add(verticalGap(8));
        cards.add(openaiCard);
        cards.add(verticalGap(8));
        cards.add(groqCard);
        section.add(cards);
        section.add(verticalGap(14));

        detailsLayout = new CardLayout();
        detailsPanel = new JPanel(detailsLayout);
        detailsPanel.setOpaque(false);
        detailsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        detailsPanel.add(buildLocalCard(), CARD_LOCAL);
        detailsPanel.add(buildOpenAiCard(), CARD_OPENAI);
        detailsPanel.add(buildGroqCard(), CARD_GROQ);
        section.add(detailsPanel);

        return section;
    }

    private JComponent buildLocalCard() {
        JPanel p = subPanel();

        p.add(fieldLabel("Model"));
        localModelBox = new ComboBox<>(MODEL_CHOICES.keySet().toArray(new String[0]));
        localModelBox.setEditable(true);
        localModelBox.setRenderer(new MapLabelRenderer(MODEL_CHOICES));
        localModelBox.addActionListener(e -> { refreshLocalStatus(); fireChanged(); });
        constrainHeight(localModelBox);
        p.add(localModelBox);

        p.add(verticalGap(10));
        localStatusLabel = new JBLabel(" ");
        localStatusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(localStatusLabel);

        localPathLabel = wrapHelpLabel(" ");
        localPathLabel.setBorder(new EmptyBorder(JBUI.insets(2, 0, 0, 0)));
        p.add(localPathLabel);

        p.add(verticalGap(8));
        localSetupButton = new JButton("Install / Update");
        localSetupButton.addActionListener(e -> runLocalSetup());
        constrainHeight(localSetupButton);
        localSetupButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(localSetupButton);

        localReinstallLink = new JBLabel("<html><a href=''>Re-install</a></html>");
        localReinstallLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        localReinstallLink.setAlignmentX(Component.LEFT_ALIGNMENT);
        localReinstallLink.setBorder(new EmptyBorder(JBUI.insets(2, 0, 0, 0)));
        localReinstallLink.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { runLocalSetup(); }
        });
        p.add(localReinstallLink);

        p.add(verticalGap(14));

        JPanel advanced = new JPanel();
        advanced.setLayout(new BoxLayout(advanced, BoxLayout.Y_AXIS));
        advanced.setOpaque(false);
        advanced.setAlignmentX(Component.LEFT_ALIGNMENT);
        advanced.setVisible(false);
        advanced.add(fieldLabel("Custom binary (optional)"));
        localPathOverrideField = new JBTextField();
        localPathOverrideField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { refreshBackendBadges(); fireChanged(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { refreshBackendBadges(); fireChanged(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { refreshBackendBadges(); fireChanged(); }
        });
        constrainHeight(localPathOverrideField);
        advanced.add(localPathOverrideField);
        JTextArea hint = wrapHelpLabel("Path to your own whisper.cpp binary (e.g. brew install whisper-cpp). Leave empty to use the bundled binary.");
        hint.setBorder(new EmptyBorder(JBUI.insets(4, 0, 0, 0)));
        advanced.add(hint);

        JBLabel toggle = new JBLabel("<html><a href=''>Show advanced options</a></html>");
        toggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        toggle.setAlignmentX(Component.LEFT_ALIGNMENT);
        toggle.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                boolean show = !advanced.isVisible();
                advanced.setVisible(show);
                toggle.setText(show
                        ? "<html><a href=''>Hide advanced options</a></html>"
                        : "<html><a href=''>Show advanced options</a></html>");
                advanced.revalidate();
            }
        });
        p.add(toggle);
        p.add(verticalGap(6));
        p.add(advanced);

        return p;
    }

    private JComponent buildOpenAiCard() {
        JPanel p = subPanel();
        openaiKeyField = new JBPasswordField();
        openaiTestLabel = new JBLabel(" ");
        addKeyBlock(p, "OpenAI API key", openaiKeyField, openaiTestLabel,
                "https://api.openai.com/v1/models");
        p.add(linkLabel("Get an API key →", "https://platform.openai.com/api-keys"));
        return p;
    }

    private JComponent buildGroqCard() {
        JPanel p = subPanel();
        groqKeyField = new JBPasswordField();
        groqTestLabel = new JBLabel(" ");
        addKeyBlock(p, "Groq API key", groqKeyField, groqTestLabel,
                "https://api.groq.com/openai/v1/models");
        p.add(linkLabel("Get an API key →", "https://console.groq.com/keys"));
        return p;
    }

    private void addKeyBlock(JPanel parent, String label, JBPasswordField field,
                             JBLabel statusLabel, String url) {
        int[] requestVersion = {0};
        field.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void changed() {
                requestVersion[0]++;
                statusLabel.setText(" ");
                statusLabel.setIcon(null);
                refreshBackendBadges();
                fireChanged();
            }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent event) { changed(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent event) { changed(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent event) { changed(); }
        });
        parent.add(fieldLabel(label));
        constrainHeight(field);
        parent.add(field);
        parent.add(verticalGap(8));
        JButton testBtn = new JButton("Test connection");
        constrainHeight(testBtn);
        testBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        testBtn.addActionListener(e -> {
            statusLabel.setIcon(null);
            statusLabel.setText("Testing…");
            statusLabel.setForeground(UIUtil.getContextHelpForeground());
            String key = new String(field.getPassword());
            int version = ++requestVersion[0];
            JPanel currentRoot = root;
            testBtn.setEnabled(false);
            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                String result = httpAuthCheck(url, key);
                SwingUtilities.invokeLater(() -> {
                    if (root != currentRoot) return;
                    testBtn.setEnabled(true);
                    if (version == requestVersion[0]) renderTestResult(statusLabel, result);
                });
            });
        });
        parent.add(testBtn);
        statusLabel.setBorder(new EmptyBorder(JBUI.insets(6, 0, 0, 0)));
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        parent.add(statusLabel);
    }

    private JComponent buildModesSection(boolean expanded) {
        SectionPanel section = section("Dictation Mode",
                "Controls how transcribed text is post-processed before being inserted.",
                expanded);
        modeDictate = wrapRadio("Dictate — raw transcription, natural punctuation");
        modeCode = wrapRadio("Code — spoken punctuation mapped to symbols, fillers stripped");
        modeCommand = wrapRadio("Command — formatted as an instruction for AI chat");
        ButtonGroup bg = new ButtonGroup();
        bg.add(modeDictate); bg.add(modeCode); bg.add(modeCommand);
        section.add(modeDictate);
        section.add(verticalGap(4));
        section.add(modeCode);
        section.add(verticalGap(4));
        section.add(modeCommand);
        return section;
    }

    private JComponent buildAudioSection(boolean expanded) {
        SectionPanel section = section("Audio Capture",
                "Whisper records via sox (rec) or ffmpeg. Auto picks whichever it finds.",
                expanded);
        toolAuto = wrapRadio("Auto-detect");
        toolSox = wrapRadio("sox (rec)");
        toolFfmpeg = wrapRadio("ffmpeg");
        ButtonGroup bg = new ButtonGroup();
        bg.add(toolAuto); bg.add(toolSox); bg.add(toolFfmpeg);
        section.add(toolAuto);
        section.add(verticalGap(4));
        section.add(toolSox);
        section.add(verticalGap(4));
        section.add(toolFfmpeg);

        audioDetectedLabel = wrapHelpLabel(" ");
        audioDetectedLabel.setBorder(new EmptyBorder(JBUI.insets(10, 0, 0, 0)));
        section.add(audioDetectedLabel);

        if (!Recorder.anyToolAvailable()) {
            String hint = SystemInfo.isMac
                    ? "Install with: brew install sox (or ffmpeg)"
                    : "Install sox or ffmpeg via your package manager";
            JTextArea warn = wrapLabel("No recorder detected. " + hint);
            warn.setForeground(JBColor.namedColor("Label.errorForeground", JBColor.RED));
            warn.setBorder(new EmptyBorder(JBUI.insets(6, 0, 0, 0)));
            section.add(warn);
        }
        return section;
    }

    private JComponent buildAdvancedSection(boolean expanded) {
        SectionPanel section = section("Advanced", null, expanded);

        section.add(fieldLabel("Language"));
        languageBox = new ComboBox<>(LANGUAGE_CHOICES.keySet().toArray(new String[0]));
        languageBox.setEditable(true);
        languageBox.setRenderer(new MapLabelRenderer(LANGUAGE_CHOICES));
        languageBox.addActionListener(e -> fireChanged());
        constrainHeight(languageBox);
        section.add(languageBox);

        section.add(verticalGap(12));
        notificationsBox = new JCheckBox("Show notifications");
        notificationsBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        notificationsBox.addItemListener(e -> fireChanged());
        section.add(notificationsBox);

        section.add(verticalGap(10));
        JBLabel hotkey = new JBLabel("Toggle recording: " + (SystemInfo.isMac ? "⌘M" : "Ctrl+M"));
        hotkey.setForeground(UIUtil.getContextHelpForeground());
        hotkey.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.add(hotkey);

        return section;
    }

    // ---------- modify/apply/reset ----------

    @Override
    public boolean isModified() {
        if (root == null || initialState == null) return false;
        WhisperSettings.State s = initialState;
        return selectedBackend != s.backend
                || readMode() != s.mode
                || readTool() != s.recordingTool
                || !new String(openaiKeyField.getPassword()).equals(s.openaiApiKey)
                || !new String(groqKeyField.getPassword()).equals(s.groqApiKey)
                || !localPathOverrideField.getText().trim().equals(s.localWhisperPath)
                || !readChoice(localModelBox).equals(s.localWhisperModel)
                || !readChoice(languageBox).equals(s.language)
                || notificationsBox.isSelected() != s.showNotifications;
    }

    @Override
    public void apply() {
        WhisperSettings.State settings = WhisperSettings.getInstance().getState();
        if (selectedBackend != initialState.backend) settings.backend = selectedBackend;
        if (readMode() != initialState.mode) settings.mode = readMode();
        if (readTool() != initialState.recordingTool) settings.recordingTool = readTool();
        String openaiKey = new String(openaiKeyField.getPassword());
        String groqKey = new String(groqKeyField.getPassword());
        String localPath = localPathOverrideField.getText().trim();
        String model = readChoice(localModelBox);
        String language = readChoice(languageBox);
        if (!openaiKey.equals(initialState.openaiApiKey)) settings.openaiApiKey = openaiKey;
        if (!groqKey.equals(initialState.groqApiKey)) settings.groqApiKey = groqKey;
        if (!localPath.equals(initialState.localWhisperPath)) settings.localWhisperPath = localPath;
        if (!model.equals(initialState.localWhisperModel)) settings.localWhisperModel = model;
        if (!language.equals(initialState.language)) settings.language = language;
        if (notificationsBox.isSelected() != initialState.showNotifications) {
            settings.showNotifications = notificationsBox.isSelected();
        }
        reset();
    }

    @Override
    public void reset() {
        suppressChange = true;
        try { doReset(); } finally { suppressChange = false; }
        fireChanged();
    }

    private void doReset() {
        WhisperSettings.State s = WhisperSettings.getInstance().getState();
        initialState = new WhisperSettings.State();
        XmlSerializerUtil.copyBean(s, initialState);
        selectBackend(s.backend);
        switch (s.mode) {
            case DICTATE -> modeDictate.setSelected(true);
            case CODE -> modeCode.setSelected(true);
            case COMMAND -> modeCommand.setSelected(true);
        }
        switch (s.recordingTool) {
            case AUTO -> toolAuto.setSelected(true);
            case SOX -> toolSox.setSelected(true);
            case FFMPEG -> toolFfmpeg.setSelected(true);
        }
        openaiKeyField.setText(s.openaiApiKey);
        groqKeyField.setText(s.groqApiKey);
        localPathOverrideField.setText(s.localWhisperPath);
        localModelBox.setSelectedItem(s.localWhisperModel);
        languageBox.setSelectedItem(s.language);
        notificationsBox.setSelected(s.showNotifications);
        refreshLocalStatus();
        refreshAudioStatus();
        refreshBackendBadges();
        openaiTestLabel.setText(" ");
        openaiTestLabel.setIcon(null);
        groqTestLabel.setText(" ");
        groqTestLabel.setIcon(null);
    }

    @Override
    public void disposeUIResources() {
        root = null;
        onChange = null;
        initialState = null;
    }

    private static String readChoice(ComboBox<String> choices) {
        return String.valueOf(choices.getEditor().getItem()).trim();
    }

    private void watchChoice(ComboBox<String> choices) {
        if (choices.getEditor().getEditorComponent() instanceof JTextField editor) {
            editor.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                @Override public void insertUpdate(javax.swing.event.DocumentEvent event) { fireChanged(); }
                @Override public void removeUpdate(javax.swing.event.DocumentEvent event) { fireChanged(); }
                @Override public void changedUpdate(javax.swing.event.DocumentEvent event) { fireChanged(); }
            });
        }
    }

    private WhisperSettings.Mode readMode() {
        if (modeCode.isSelected()) return WhisperSettings.Mode.CODE;
        if (modeCommand.isSelected()) return WhisperSettings.Mode.COMMAND;
        return WhisperSettings.Mode.DICTATE;
    }

    private WhisperSettings.RecordingTool readTool() {
        if (toolSox.isSelected()) return WhisperSettings.RecordingTool.SOX;
        if (toolFfmpeg.isSelected()) return WhisperSettings.RecordingTool.FFMPEG;
        return WhisperSettings.RecordingTool.AUTO;
    }

    private void selectBackend(WhisperSettings.Backend b) {
        boolean changed = selectedBackend != b;
        selectedBackend = b;
        localCard.setSelected(b == WhisperSettings.Backend.LOCAL);
        openaiCard.setSelected(b == WhisperSettings.Backend.OPENAI);
        groqCard.setSelected(b == WhisperSettings.Backend.GROQ);
        detailsLayout.show(detailsPanel, switch (b) {
            case LOCAL -> CARD_LOCAL;
            case OPENAI -> CARD_OPENAI;
            case GROQ -> CARD_GROQ;
        });
        if (changed) fireChanged();
    }

    private void refreshBackendBadges() {
        String localModel = (String) localModelBox.getSelectedItem();
        boolean localReady = !localPathOverrideField.getText().trim().isEmpty()
                || (localModel != null && LocalSetup.isReady(localModel));
        localCard.setReady(localReady);
        openaiCard.setReady(!new String(openaiKeyField.getPassword()).isEmpty());
        groqCard.setReady(!new String(groqKeyField.getPassword()).isEmpty());
    }

    private void refreshLocalStatus() {
        String model = (String) localModelBox.getSelectedItem();
        if (model == null) return;
        boolean ready = LocalSetup.isReady(model);
        if (ready) {
            localStatusLabel.setIcon(AllIcons.General.InspectionsOK);
            localStatusLabel.setText("Installed and ready");
            localSetupButton.setVisible(false);
            localReinstallLink.setVisible(true);
            localPathLabel.setVisible(false);
        } else {
            localStatusLabel.setIcon(AllIcons.General.BalloonWarning);
            localStatusLabel.setText("Not installed for this model");
            localSetupButton.setVisible(true);
            localReinstallLink.setVisible(false);
            localPathLabel.setVisible(false);
        }
        try {
            LocalSetup.Paths p = LocalSetup.getLocalPaths();
            localStatusLabel.setToolTipText("Path: " + p.binary);
        } catch (IOException ignored) {
            localStatusLabel.setToolTipText(null);
        }
        refreshBackendBadges();
    }

    private void refreshAudioStatus() {
        String sox = Recorder.resolveBinary("rec");
        String ff = Recorder.resolveBinary("ffmpeg");
        StringBuilder sb = new StringBuilder("Detected: ");
        sb.append(sox != null ? "sox ✓ (" + sox + ")" : "sox ✗");
        sb.append("    ");
        sb.append(ff != null ? "ffmpeg ✓ (" + ff + ")" : "ffmpeg ✗");
        audioDetectedLabel.setText(sb.toString());
    }

    // ---------- setup runner ----------

    private void runLocalSetup() {
        String model = readChoice(localModelBox);
        if (model.isEmpty() || !SETUP_RUNNING.compareAndSet(false, true)) return;
        JPanel currentRoot = root;
        localSetupButton.setEnabled(false);
        localReinstallLink.setEnabled(false);
        localStatusLabel.setIcon(AllIcons.General.BalloonInformation);
        localStatusLabel.setText("Installing whisper.cpp + " + model + " model…");

        var project = ProjectManager.getInstance().getDefaultProject();
        ProgressManager.getInstance().run(new Task.Backgroundable(project,
                "Whisper: Setting up local transcription", false) {
            @Override
            public void run(@org.jetbrains.annotations.NotNull ProgressIndicator indicator) {
                String error = null;
                try {
                    LocalSetup.setup(model, indicator);
                } catch (Exception ex) {
                    error = ex.getMessage() != null ? ex.getMessage() : ex.toString();
                } finally {
                    SETUP_RUNNING.set(false);
                }
                String finalError = error;
                SwingUtilities.invokeLater(() -> {
                    if (root != currentRoot) return;
                    localSetupButton.setEnabled(true);
                    localReinstallLink.setEnabled(true);
                    if (finalError == null) {
                        refreshLocalStatus();
                    } else {
                        localStatusLabel.setIcon(AllIcons.General.BalloonError);
                        localStatusLabel.setText("Setup failed: " + finalError);
                    }
                });
            }
        });
    }

    // ---------- key tests ----------

    private static String httpAuthCheck(String url, String apiKey) {
        if (apiKey == null || apiKey.isBlank()) return "Enter an API key first";
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            int code = connection.getResponseCode();
            if (code == 200) return "OK";
            if (code == 401 || code == 403) return "Invalid API key (" + code + ")";
            return "HTTP " + code;
        } catch (Exception e) {
            return "Network error: " + e.getMessage();
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void renderTestResult(JBLabel label, String result) {
        if ("OK".equals(result)) {
            label.setIcon(AllIcons.General.InspectionsOK);
            label.setText("Connected");
            label.setForeground(UIUtil.getLabelForeground());
        } else {
            label.setIcon(AllIcons.General.BalloonError);
            label.setText(result);
            label.setForeground(JBColor.namedColor("Label.errorForeground", JBColor.RED));
        }
    }

    // ---------- helpers ----------

    private static SectionPanel section(String title, String description, boolean expanded) {
        return new SectionPanel(title, description, expanded);
    }

    /** Collapsible section: bold clickable header with chevron, body hidden when collapsed. */
    private static final class SectionPanel extends JPanel {
        private final JPanel body;
        private final JBLabel chevron;
        private boolean expanded;

        SectionPanel(String title, String description, boolean expanded) {
            super();
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setOpaque(false);
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setBorder(new EmptyBorder(JBUI.insets(0, 0, 8, 0)));
            this.expanded = expanded;

            JPanel header = new JPanel(new BorderLayout(8, 0));
            header.setOpaque(false);
            header.setAlignmentX(Component.LEFT_ALIGNMENT);
            header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            chevron = new JBLabel();
            JBLabel titleLabel = new JBLabel(title);
            Font base = titleLabel.getFont();
            titleLabel.setFont(base.deriveFont(Font.BOLD, base.getSize2D() + 2f));
            JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            left.setOpaque(false);
            left.add(chevron);
            left.add(titleLabel);
            header.add(left, BorderLayout.WEST);

            MouseAdapter toggle = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) { setExpanded(!SectionPanel.this.expanded); }
            };
            header.addMouseListener(toggle);
            chevron.addMouseListener(toggle);
            titleLabel.addMouseListener(toggle);
            left.addMouseListener(toggle);

            super.add(header);

            body = new JPanel();
            body.setOpaque(false);
            body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
            body.setAlignmentX(Component.LEFT_ALIGNMENT);
            body.setBorder(new EmptyBorder(JBUI.insets(8, 0, 0, 0)));

            if (description != null) {
                JTextArea desc = wrapHelpLabel(description);
                desc.setBorder(new EmptyBorder(JBUI.insets(0, 0, 10, 0)));
                body.add(desc);
            }
            super.add(body);
            applyChevron();
            body.setVisible(expanded);
        }

        @Override public Component add(Component c) { body.add(c); return c; }

        void setExpanded(boolean e) {
            this.expanded = e;
            body.setVisible(e);
            applyChevron();
            revalidate();
            repaint();
        }

        private void applyChevron() {
            chevron.setIcon(expanded ? AllIcons.General.ArrowDown : AllIcons.General.ArrowRight);
        }
    }

    private static JComponent sectionSeparator() {
        JPanel wrap = new JPanel();
        wrap.setLayout(new BoxLayout(wrap, BoxLayout.Y_AXIS));
        wrap.setOpaque(false);
        wrap.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrap.add(verticalGap(14));

        JPanel line = new JPanel();
        line.setBackground(JBColor.border());
        line.setOpaque(true);
        line.setAlignmentX(Component.LEFT_ALIGNMENT);
        Dimension d = new Dimension(Integer.MAX_VALUE, JBUI.scale(1));
        line.setMaximumSize(d);
        line.setPreferredSize(new Dimension(10, JBUI.scale(1)));
        line.setMinimumSize(new Dimension(10, JBUI.scale(1)));
        wrap.add(line);

        wrap.add(verticalGap(14));
        return wrap;
    }

    private static JBLabel fieldLabel(String text) {
        JBLabel l = new JBLabel(text);
        Font f = l.getFont();
        l.setFont(f.deriveFont(Font.BOLD));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setBorder(new EmptyBorder(JBUI.insets(0, 0, 4, 0)));
        return l;
    }

    private JRadioButton wrapRadio(String text) {
        JRadioButton r = new JRadioButton(text);
        r.setAlignmentX(Component.LEFT_ALIGNMENT);
        r.setOpaque(false);
        r.addItemListener(e -> fireChanged());
        return r;
    }

    private static void constrainHeight(JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        Dimension pref = c.getPreferredSize();
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, pref.height));
    }

    private static JPanel subPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    private static Component verticalGap(int h) {
        Component c = Box.createRigidArea(new Dimension(0, JBUI.scale(h)));
        if (c instanceof JComponent jc) jc.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }

    private static JTextArea wrapLabel(String text) {
        JTextArea a = new JTextArea(text);
        a.setEditable(false);
        a.setOpaque(false);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setFocusable(false);
        a.setBorder(null);
        a.setFont(UIManager.getFont("Label.font"));
        a.setForeground(UIManager.getColor("Label.foreground"));
        a.setAlignmentX(Component.LEFT_ALIGNMENT);
        return a;
    }

    private static JTextArea wrapHelpLabel(String text) {
        JTextArea a = wrapLabel(text);
        a.setForeground(UIUtil.getContextHelpForeground());
        return a;
    }

    private static JBLabel linkLabel(String text, String url) {
        JBLabel l = new JBLabel("<html><a href=''>" + text + "</a></html>");
        l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        l.setBorder(new EmptyBorder(JBUI.insets(8, 0, 0, 0)));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                try { Desktop.getDesktop().browse(URI.create(url)); } catch (Exception ignored) {}
            }
        });
        return l;
    }

    // ---------- inner types ----------

    private final class BackendCard extends JPanel {
        private final JBLabel titleLabel;
        private final JTextArea descLabel;
        private final JBLabel badgeLabel;

        BackendCard(WhisperSettings.Backend backend, String title, String description) {
            super(new BorderLayout());
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setAlignmentX(Component.LEFT_ALIGNMENT);

            JPanel inner = new JPanel();
            inner.setOpaque(false);
            inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
            inner.setBorder(new EmptyBorder(JBUI.insets(12, 14, 12, 14)));

            titleLabel = new JBLabel(title);
            Font tf = titleLabel.getFont();
            titleLabel.setFont(tf.deriveFont(Font.BOLD));
            badgeLabel = new JBLabel(" ");
            badgeLabel.setForeground(UIUtil.getContextHelpForeground());
            JPanel header = new JPanel(new BorderLayout());
            header.setOpaque(false);
            header.add(titleLabel, BorderLayout.WEST);
            header.add(badgeLabel, BorderLayout.EAST);
            header.setAlignmentX(Component.LEFT_ALIGNMENT);
            inner.add(header);

            descLabel = wrapHelpLabel(description);
            descLabel.setBorder(new EmptyBorder(JBUI.insets(4, 0, 0, 0)));
            inner.add(descLabel);

            add(inner, BorderLayout.CENTER);
            setSelected(false);
            MouseAdapter clickAll = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    selectBackend(backend);
                }
            };
            attachClickRecursive(this, clickAll);
        }

        private static void attachClickRecursive(Component c, MouseAdapter listener) {
            c.addMouseListener(listener);
            if (c instanceof Container container) {
                for (Component child : container.getComponents()) {
                    attachClickRecursive(child, listener);
                }
            }
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }

        void setSelected(boolean s) {
            Color border = s
                    ? JBColor.namedColor("Component.focusColor", new JBColor(new Color(0x3574F0), new Color(0x3574F0)))
                    : JBColor.border();
            int thickness = s ? 2 : 1;
            setBorder(BorderFactory.createLineBorder(border, thickness, true));
            repaint();
        }

        void setReady(boolean ready) {
            if (ready) {
                badgeLabel.setIcon(AllIcons.General.InspectionsOK);
                badgeLabel.setText("Ready");
                badgeLabel.setForeground(UIUtil.getLabelForeground());
            } else {
                badgeLabel.setIcon(AllIcons.General.BalloonWarning);
                badgeLabel.setText("Setup");
                badgeLabel.setForeground(UIUtil.getContextHelpForeground());
            }
        }
    }

    private static final class ScrollableVPanel extends JPanel implements Scrollable {
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 16; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return 64; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    private static final class MapLabelRenderer extends DefaultListCellRenderer {
        private final Map<String, String> labels;
        MapLabelRenderer(Map<String, String> labels) { this.labels = labels; }
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            String key = String.valueOf(value);
            String text = labels.getOrDefault(key, key);
            return super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus);
        }
    }
}
