package dev.aroussi.whisper;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.IdeFocusManager;

import java.awt.datatransfer.StringSelection;

public final class PasteInjector {

    public static void insert(String text) {
        if (text == null || text.isEmpty()) return;

        Project project = activeProject();
        if (project != null && tryInsertAtEditorCaret(project, text)) {
            return;
        }

        CopyPasteManager.getInstance().setContents(new StringSelection(text));
        boolean simulated = simulatePaste();
        if (!simulated && project != null) {
            WhisperController.notifyInfo(project,
                    "Text copied to clipboard. Press " + (SystemInfo.isMac ? "⌘V" : "Ctrl+V") + " to paste. " +
                            "(Grant Accessibility access to your IDE for auto-paste in external apps.)");
        }
    }

    private static Project activeProject() {
        Project p = IdeFocusManager.getGlobalInstance().getLastFocusedFrame() != null
                ? IdeFocusManager.getGlobalInstance().getLastFocusedFrame().getProject()
                : null;
        if (p != null && !p.isDisposed()) return p;
        Project[] all = ProjectManager.getInstance().getOpenProjects();
        return all.length > 0 ? all[0] : null;
    }

    private static boolean tryInsertAtEditorCaret(Project project, String text) {
        Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        if (editor == null) return false;
        if (!editor.getContentComponent().hasFocus()) return false;

        try {
            ApplicationManager.getApplication().invokeAndWait(() ->
                    WriteCommandAction.runWriteCommandAction(project, "Whisper Insert", null, () -> {
                        for (Caret caret : editor.getCaretModel().getAllCarets()) {
                            int offset = caret.getOffset();
                            if (caret.hasSelection()) {
                                editor.getDocument().replaceString(
                                        caret.getSelectionStart(), caret.getSelectionEnd(), text);
                                caret.moveToOffset(caret.getSelectionStart() + text.length());
                            } else {
                                editor.getDocument().insertString(offset, text);
                                caret.moveToOffset(offset + text.length());
                            }
                        }
                    })
            );
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean simulatePaste() {
        try {
            ProcessBuilder pb;
            if (SystemInfo.isMac) {
                pb = new ProcessBuilder("osascript", "-e",
                        "tell application \"System Events\" to keystroke \"v\" using command down");
            } else if (SystemInfo.isWindows) {
                pb = new ProcessBuilder("powershell", "-command",
                        "Add-Type -AssemblyName System.Windows.Forms; "
                                + "[System.Windows.Forms.SendKeys]::SendWait('^v')");
            } else {
                pb = new ProcessBuilder("xdotool", "key", "ctrl+v");
            }
            Process p = pb.redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
