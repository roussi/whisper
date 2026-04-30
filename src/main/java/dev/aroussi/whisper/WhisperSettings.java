package dev.aroussi.whisper;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

@Service(Service.Level.APP)
@State(name = "WhisperSettings", storages = @Storage("whisper.xml"))
public final class WhisperSettings implements PersistentStateComponent<WhisperSettings.State> {

    public enum Backend { LOCAL, OPENAI, GROQ }
    public enum Mode { DICTATE, CODE, COMMAND }
    public enum RecordingTool { AUTO, SOX, FFMPEG }

    public static final class State {
        public Backend backend = Backend.LOCAL;
        public Mode mode = Mode.DICTATE;
        public RecordingTool recordingTool = RecordingTool.AUTO;
        public String openaiApiKey = "";
        public String groqApiKey = "";
        public String localWhisperPath = "";
        public String localWhisperModel = "base";
        public String language = "en";
        public boolean showNotifications = true;
    }

    private State state = new State();

    public static WhisperSettings getInstance() {
        return ApplicationManager.getApplication().getService(WhisperSettings.class);
    }

    @Override
    public @NotNull State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State newState) {
        XmlSerializerUtil.copyBean(newState, state);
    }
}
