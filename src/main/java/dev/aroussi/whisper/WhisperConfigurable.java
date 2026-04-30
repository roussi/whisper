package dev.aroussi.whisper;

import com.intellij.openapi.options.Configurable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public final class WhisperConfigurable implements Configurable {

    private JPanel root;
    private JComboBox<WhisperSettings.Backend> backendBox;
    private JComboBox<WhisperSettings.Mode> modeBox;
    private JComboBox<WhisperSettings.RecordingTool> toolBox;
    private JTextField openaiKeyField;
    private JTextField groqKeyField;
    private JTextField localPathField;
    private JTextField modelField;
    private JTextField languageField;
    private JCheckBox notificationsBox;

    @Override public @Nls String getDisplayName() { return "Whisper"; }

    @Override
    public @Nullable JComponent createComponent() {
        root = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.HORIZONTAL;
        g.insets = new Insets(4, 4, 4, 4);
        g.weightx = 1;

        backendBox = new JComboBox<>(WhisperSettings.Backend.values());
        modeBox = new JComboBox<>(WhisperSettings.Mode.values());
        toolBox = new JComboBox<>(WhisperSettings.RecordingTool.values());
        openaiKeyField = new JTextField();
        groqKeyField = new JTextField();
        localPathField = new JTextField();
        modelField = new JTextField();
        languageField = new JTextField();
        notificationsBox = new JCheckBox("Show notifications");

        int row = 0;
        addRow(g, row++, "Backend:", backendBox);
        addRow(g, row++, "Mode:", modeBox);
        addRow(g, row++, "Recording tool:", toolBox);
        addRow(g, row++, "OpenAI API key:", openaiKeyField);
        addRow(g, row++, "Groq API key:", groqKeyField);
        addRow(g, row++, "Local whisper path:", localPathField);
        addRow(g, row++, "Local whisper model:", modelField);
        addRow(g, row++, "Language:", languageField);
        g.gridx = 0; g.gridy = row; g.gridwidth = 2;
        root.add(notificationsBox, g);

        reset();
        return root;
    }

    private void addRow(GridBagConstraints g, int row, String label, JComponent field) {
        g.gridx = 0; g.gridy = row; g.gridwidth = 1; g.weightx = 0;
        root.add(new JLabel(label), g);
        g.gridx = 1; g.weightx = 1;
        root.add(field, g);
    }

    @Override
    public boolean isModified() {
        WhisperSettings.State s = WhisperSettings.getInstance().getState();
        return backendBox.getSelectedItem() != s.backend
                || modeBox.getSelectedItem() != s.mode
                || toolBox.getSelectedItem() != s.recordingTool
                || !openaiKeyField.getText().equals(s.openaiApiKey)
                || !groqKeyField.getText().equals(s.groqApiKey)
                || !localPathField.getText().equals(s.localWhisperPath)
                || !modelField.getText().equals(s.localWhisperModel)
                || !languageField.getText().equals(s.language)
                || notificationsBox.isSelected() != s.showNotifications;
    }

    @Override
    public void apply() {
        WhisperSettings.State s = WhisperSettings.getInstance().getState();
        s.backend = (WhisperSettings.Backend) backendBox.getSelectedItem();
        s.mode = (WhisperSettings.Mode) modeBox.getSelectedItem();
        s.recordingTool = (WhisperSettings.RecordingTool) toolBox.getSelectedItem();
        s.openaiApiKey = openaiKeyField.getText();
        s.groqApiKey = groqKeyField.getText();
        s.localWhisperPath = localPathField.getText();
        s.localWhisperModel = modelField.getText();
        s.language = languageField.getText();
        s.showNotifications = notificationsBox.isSelected();
    }

    @Override
    public void reset() {
        WhisperSettings.State s = WhisperSettings.getInstance().getState();
        backendBox.setSelectedItem(s.backend);
        modeBox.setSelectedItem(s.mode);
        toolBox.setSelectedItem(s.recordingTool);
        openaiKeyField.setText(s.openaiApiKey);
        groqKeyField.setText(s.groqApiKey);
        localPathField.setText(s.localWhisperPath);
        modelField.setText(s.localWhisperModel);
        languageField.setText(s.language);
        notificationsBox.setSelected(s.showNotifications);
    }
}
