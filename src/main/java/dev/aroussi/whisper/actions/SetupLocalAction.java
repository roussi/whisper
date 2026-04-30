package dev.aroussi.whisper.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import dev.aroussi.whisper.WhisperController;
import dev.aroussi.whisper.WhisperSettings;
import dev.aroussi.whisper.setup.LocalSetup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class SetupLocalAction extends AnAction {

    private record ModelOption(String name, String description) {}

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        List<ModelOption> models = List.of(
                new ModelOption("tiny", "~75 MB — fastest"),
                new ModelOption("base", "~150 MB — balanced (recommended)"),
                new ModelOption("small", "~500 MB — better accuracy"),
                new ModelOption("medium", "~1.5 GB — high accuracy")
        );

        JBPopupFactory.getInstance().createListPopup(
                new BaseListPopupStep<>("Select model size", models) {
                    @Override
                    public @NotNull String getTextFor(ModelOption value) {
                        return value.name() + " — " + value.description();
                    }
                    @Override
                    public @Nullable PopupStep<?> onChosen(ModelOption selected, boolean finalChoice) {
                        runSetup(project, selected.name());
                        return FINAL_CHOICE;
                    }
                }
        ).showInBestPositionFor(e.getDataContext());
    }

    private void runSetup(Project project, String model) {
        WhisperSettings.getInstance().getState().localWhisperModel = model;
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Whisper: Setting up local transcription", false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    LocalSetup.setup(model, indicator);
                    WhisperController.notifyInfo(project, "Local transcription ready (model: " + model + ")");
                } catch (Exception e) {
                    WhisperController.notifyError(project, "Setup failed: " + e.getMessage());
                }
            }
        });
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
