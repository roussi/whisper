package dev.aroussi.whisper;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.SystemInfo;
import dev.aroussi.whisper.recorder.Recorder;
import dev.aroussi.whisper.setup.LocalSetup;
import org.jetbrains.annotations.NotNull;

import java.awt.Desktop;
import java.net.URI;

public final class Onboarding implements StartupActivity.DumbAware {

    @Override
    public void runActivity(@NotNull Project project) {
        ApplicationManager.getApplication().invokeLater(() -> checkAndPrompt(project));
    }

    public static void checkAndPrompt(Project project) {
        WhisperSettings.State s = WhisperSettings.getInstance().getState();

        boolean recorderOk = Recorder.anyToolAvailable();
        boolean backendOk = switch (s.backend) {
            case LOCAL -> !s.localWhisperPath.isEmpty() || LocalSetup.isReady(s.localWhisperModel);
            case OPENAI -> !s.openaiApiKey.isEmpty();
            case GROQ -> !s.groqApiKey.isEmpty();
        };

        if (recorderOk && backendOk) return;

        StringBuilder msg = new StringBuilder();
        if (!recorderOk) {
            msg.append("• No audio recorder found. Install ");
            msg.append(SystemInfo.isMac
                    ? "with: brew install sox (or ffmpeg)"
                    : "sox or ffmpeg via your package manager");
            msg.append("\n");
        }
        if (!backendOk) {
            msg.append("• Transcription backend not ready: ");
            msg.append(switch (s.backend) {
                case LOCAL -> "run setup to install whisper.cpp (free).";
                case OPENAI -> "configure OpenAI API key in Settings.";
                case GROQ -> "configure Groq API key in Settings.";
            });
        }

        Notification n = NotificationGroupManager.getInstance()
                .getNotificationGroup("Whisper")
                .createNotification("Whisper setup needed", msg.toString(), NotificationType.INFORMATION);

        if (!recorderOk && SystemInfo.isMac) {
            n.addAction(NotificationAction.createSimple("Open brew.sh", () -> openUrl("https://brew.sh")));
        }
        if (!backendOk && s.backend == WhisperSettings.Backend.LOCAL) {
            n.addAction(NotificationAction.create("Run Local Setup", (e, notif) -> {
                notif.expire();
                AnAction action = ActionManager.getInstance().getAction("dev.aroussi.whisper.SetupLocal");
                if (action != null) action.actionPerformed(e);
            }));
        }
        n.addAction(NotificationAction.createSimple("Open Settings", () -> {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, WhisperConfigurable.class);
        }));

        n.notify(project);
    }

    private static void openUrl(String url) {
        try {
            Desktop.getDesktop().browse(new URI(url));
        } catch (Exception ignored) {}
    }
}
