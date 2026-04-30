package dev.aroussi.whisper;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import dev.aroussi.whisper.modes.Modes;
import dev.aroussi.whisper.recorder.Recorder;
import dev.aroussi.whisper.setup.LocalSetup;
import dev.aroussi.whisper.transcriber.Transcriber;

import java.nio.file.Files;
import java.nio.file.Path;

public final class WhisperController {

    private static final WhisperController INSTANCE = new WhisperController();
    public static WhisperController getInstance() { return INSTANCE; }

    private Recorder recorder;

    public boolean isRecording() {
        return recorder != null && recorder.isRecording();
    }

    public boolean isTranscribing = false;

    public void toggle(Project project) {
        if (isRecording()) {
            stopAndTranscribe(project);
        } else {
            start(project);
        }
    }

    private void start(Project project) {
        WhisperSettings.State s = WhisperSettings.getInstance().getState();
        try {
            recorder = new Recorder(s.recordingTool);
            recorder.start();
            WhisperStatusBarFactory.refresh(project);
        } catch (Exception e) {
            notifyError(project, "Recording failed: " + e.getMessage());
            recorder = null;
            Onboarding.checkAndPrompt(project);
        }
    }

    private void stopAndTranscribe(Project project) {
        WhisperSettings.State s = WhisperSettings.getInstance().getState();
        Recorder r = recorder;
        recorder = null;

        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Whisper: Transcribing", false) {
            @Override
            public void run(ProgressIndicator indicator) {
                Path audioPath = null;
                try {
                    isTranscribing = true;
                    WhisperStatusBarFactory.refresh(project);
                    audioPath = r.stop();

                    Transcriber.Config cfg = new Transcriber.Config();
                    cfg.backend = s.backend;
                    cfg.language = s.language;
                    cfg.localWhisperModel = s.localWhisperModel;

                    if (s.backend == WhisperSettings.Backend.OPENAI) {
                        if (s.openaiApiKey.isEmpty()) {
                            notifyError(project, "OpenAI API key not set. Configure under Settings -> Tools -> Whisper.");
                            return;
                        }
                        cfg.apiKey = s.openaiApiKey;
                    } else if (s.backend == WhisperSettings.Backend.GROQ) {
                        if (s.groqApiKey.isEmpty()) {
                            notifyError(project, "Groq API key not set. Configure under Settings -> Tools -> Whisper.");
                            return;
                        }
                        cfg.apiKey = s.groqApiKey;
                    } else {
                        if (!s.localWhisperPath.isEmpty()) {
                            cfg.localWhisperPath = s.localWhisperPath;
                        } else {
                            if (!LocalSetup.isReady(s.localWhisperModel)) {
                                notifyError(project, "Local whisper not set up. Run: Whisper: Setup Local Transcription.");
                                return;
                            }
                            LocalSetup.Paths paths = LocalSetup.getLocalPaths();
                            cfg.localWhisperPath = paths.binary.toString();
                            cfg.localModelsDir = paths.modelsDir.toString();
                        }
                    }

                    String text = Transcriber.transcribe(audioPath, cfg);
                    String processed = Modes.postProcess(text, s.mode);

                    if (!processed.isEmpty()) {
                        ApplicationManager.getApplication().invokeLater(() -> PasteInjector.insert(processed));
                    } else if (s.showNotifications) {
                        notifyInfo(project, "No speech detected");
                    }
                } catch (Exception e) {
                    notifyError(project, "Transcription failed: " + e.getMessage());
                } finally {
                    if (audioPath != null) {
                        try { Files.deleteIfExists(audioPath); } catch (Exception ignored) {}
                    }
                    isTranscribing = false;
                    WhisperStatusBarFactory.refresh(project);
                }
            }
        });
    }

    public static void notifyInfo(Project project, String msg) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup("Whisper")
                .createNotification(msg, NotificationType.INFORMATION)
                .notify(project);
    }

    public static void notifyError(Project project, String msg) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup("Whisper")
                .createNotification(msg, NotificationType.ERROR)
                .notify(project);
    }
}
