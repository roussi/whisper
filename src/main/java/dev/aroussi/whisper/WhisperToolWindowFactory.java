package dev.aroussi.whisper;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public final class WhisperToolWindowFactory implements ToolWindowFactory, DumbAware {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        toolWindow.setIcon(IconLoader.getIcon("/icons/mic.svg", WhisperToolWindowFactory.class));

        WhisperConfigurable configurable = new WhisperConfigurable();
        JComponent settingsPanel = configurable.createComponent();
        configurable.reset();

        JPanel root = new JPanel(new BorderLayout());
        if (settingsPanel != null) {
            root.add(settingsPanel, BorderLayout.CENTER);
        }
        root.add(buildFooter(project, configurable), BorderLayout.SOUTH);

        Content content = ContentFactory.getInstance().createContent(root, "", false);
        content.setDisposer(configurable::disposeUIResources);
        toolWindow.getContentManager().addContent(content);
    }

    private JComponent buildFooter(Project project, WhisperConfigurable configurable) {
        JPanel footer = new JPanel();
        footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));
        footer.setBorder(new EmptyBorder(JBUI.insets(6, 8, 8, 8)));

        JBLabel hint = new JBLabel("Changes are saved when you click Apply.");
        hint.setForeground(UIUtil.getContextHelpForeground());
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        footer.add(hint);

        JPanel buttons = new JPanel(new GridLayout(1, 2, 6, 0));
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttons.setBorder(new EmptyBorder(JBUI.insets(6, 0, 0, 0)));
        JButton reset = new JButton("Reset");
        reset.addActionListener(e -> configurable.reset());
        JButton apply = new JButton("Apply");
        apply.addActionListener(e -> {
            try {
                configurable.apply();
                WhisperStatusBarFactory.refresh(project);
                apply.setEnabled(false);
            } catch (Exception ex) {
                WhisperController.notifyError(project, "Could not save: " + ex.getMessage());
            }
        });
        buttons.add(reset);
        buttons.add(apply);
        footer.add(buttons);

        Runnable updateState = () -> {
            boolean dirty = configurable.isModified();
            apply.setEnabled(dirty);
            reset.setEnabled(dirty);
        };
        configurable.setOnChange(() -> SwingUtilities.invokeLater(updateState));
        SwingUtilities.invokeLater(updateState);
        return footer;
    }
}
