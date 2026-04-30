package dev.aroussi.whisper;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.wm.CustomStatusBarWidget;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.StatusBarWidgetFactory;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public final class WhisperStatusBarFactory implements StatusBarWidgetFactory {

    public static final String WIDGET_ID = "dev.aroussi.whisper.WhisperStatusBar";

    @Override public @NotNull String getId() { return WIDGET_ID; }
    @Override public @NotNull @NlsContexts.ConfigurableName String getDisplayName() { return "Whisper"; }
    @Override public boolean isAvailable(@NotNull Project project) { return true; }

    @Override
    public @NotNull StatusBarWidget createWidget(@NotNull Project project) {
        return new Widget(project);
    }

    public static void refresh(Project project) {
        if (project == null || project.isDisposed()) return;
        StatusBar bar = WindowManager.getInstance().getStatusBar(project);
        if (bar != null) bar.updateWidget(WIDGET_ID);
    }

    private static final class Widget implements CustomStatusBarWidget {
        private final Project project;
        private final WhisperPanel panel;

        Widget(Project project) {
            this.project = project;
            this.panel = new WhisperPanel(project);
        }

        @Override public @NotNull String ID() { return WIDGET_ID; }
        @Override public @Nullable WidgetPresentation getPresentation() { return null; }

        @Override
        public void install(@NotNull StatusBar statusBar) {
            panel.startTicker();
        }

        @Override
        public JComponent getComponent() {
            return panel;
        }

        @Override
        public void dispose() {
            panel.stopTicker();
        }
    }

    private static final class WhisperPanel extends JPanel {
        private final Project project;
        private Timer pulseTimer;
        private float pulse = 0f;
        private boolean pulseUp = true;

        WhisperPanel(Project project) {
            this.project = project;
            setOpaque(false);
            setBorder(JBUI.Borders.empty(0, 6));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText("Click or Cmd+M / Ctrl+M to toggle recording");

            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    WhisperController.getInstance().toggle(project);
                }
            });

            pulseTimer = new Timer(60, e -> {
                if (WhisperController.getInstance().isRecording()
                        || WhisperController.getInstance().isTranscribing) {
                    pulse += pulseUp ? 0.08f : -0.08f;
                    if (pulse >= 1f) { pulse = 1f; pulseUp = false; }
                    else if (pulse <= 0f) { pulse = 0f; pulseUp = true; }
                    repaint();
                }
            });
        }

        void startTicker() { pulseTimer.start(); }
        void stopTicker() { pulseTimer.stop(); }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont());
            int w = fm.stringWidth(currentLabel()) + 24;
            return new Dimension(w, fm.getHeight() + 4);
        }

        private String currentLabel() {
            WhisperController c = WhisperController.getInstance();
            if (c.isTranscribing) return "Transcribing…";
            if (c.isRecording()) return "Recording";
            WhisperSettings.Mode mode = WhisperSettings.getInstance().getState().mode;
            return "Whisper [" + mode.name().toLowerCase() + "]";
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();

            WhisperController c = WhisperController.getInstance();
            int dotDiameter = 10;
            int dotX = 4;
            int dotY = (getHeight() - dotDiameter) / 2;

            Color dotColor;
            if (c.isRecording()) {
                int alpha = (int) (140 + 115 * pulse);
                dotColor = new Color(229, 57, 53, alpha);
                g2.setColor(new Color(229, 57, 53, 60));
                int glow = (int) (4 * pulse);
                g2.fillOval(dotX - glow, dotY - glow, dotDiameter + glow * 2, dotDiameter + glow * 2);
            } else if (c.isTranscribing) {
                int alpha = (int) (140 + 115 * pulse);
                dotColor = new Color(255, 167, 38, alpha);
            } else {
                dotColor = new Color(getForeground().getRed(), getForeground().getGreen(),
                        getForeground().getBlue(), 140);
            }
            g2.setColor(dotColor);
            g2.fillOval(dotX, dotY, dotDiameter, dotDiameter);

            int textX = dotX + dotDiameter + 6;
            int textY = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
            g2.setColor(getForeground());
            g2.drawString(currentLabel(), textX, textY);
            g2.dispose();
        }
    }
}
