package dev.aroussi.whisper.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import dev.aroussi.whisper.WhisperSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public final class SelectBackendAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        List<WhisperSettings.Backend> backends = Arrays.asList(WhisperSettings.Backend.values());
        JBPopupFactory.getInstance().createListPopup(
                new BaseListPopupStep<>("Select transcription backend", backends) {
                    @Override
                    public @NotNull String getTextFor(WhisperSettings.Backend value) {
                        return switch (value) {
                            case LOCAL -> "Local (free) — whisper.cpp on your machine";
                            case OPENAI -> "OpenAI — Whisper API (requires key)";
                            case GROQ -> "Groq — Whisper Large V3 (fast, requires key)";
                        };
                    }
                    @Override
                    public @Nullable PopupStep<?> onChosen(WhisperSettings.Backend selected, boolean finalChoice) {
                        WhisperSettings.getInstance().getState().backend = selected;
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
