package dev.aroussi.whisper.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import dev.aroussi.whisper.WhisperController;
import org.jetbrains.annotations.NotNull;

public final class ToggleRecordingAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        WhisperController.getInstance().toggle(e.getProject());
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
