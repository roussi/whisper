package dev.aroussi.whisper.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import dev.aroussi.whisper.WhisperSettings;
import dev.aroussi.whisper.WhisperStatusBarFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public final class SetModeAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        List<WhisperSettings.Mode> modes = Arrays.asList(WhisperSettings.Mode.values());
        JBPopupFactory.getInstance().createListPopup(
                new BaseListPopupStep<>("Select dictation mode", modes) {
                    @Override
                    public @NotNull String getTextFor(WhisperSettings.Mode value) {
                        return switch (value) {
                            case DICTATE -> "Dictate — Raw transcription as-is";
                            case CODE -> "Code — Cleaned for code (punctuation symbols)";
                            case COMMAND -> "Command — Format as instruction/prompt";
                        };
                    }
                    @Override
                    public @Nullable PopupStep<?> onChosen(WhisperSettings.Mode selected, boolean finalChoice) {
                        WhisperSettings.getInstance().getState().mode = selected;
                        WhisperStatusBarFactory.refresh(e.getProject());
                        return FINAL_CHOICE;
                    }
                }
        ).showInBestPositionFor(e.getDataContext());
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
