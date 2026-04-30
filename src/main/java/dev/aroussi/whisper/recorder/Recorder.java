package dev.aroussi.whisper.recorder;

import com.intellij.openapi.util.SystemInfo;
import dev.aroussi.whisper.WhisperSettings;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class Recorder {

    public enum Tool { SOX, FFMPEG }

    private Process process;
    private Path outputPath;
    private final Tool resolvedTool;
    private final String resolvedBinary;
    private final int sampleRate = 16000;
    private volatile boolean processExited = false;
    private volatile Integer earlyExitCode = null;

    public Recorder(WhisperSettings.RecordingTool requested) {
        if (requested == WhisperSettings.RecordingTool.AUTO) {
            String soxBin = resolveBinary("rec");
            if (soxBin != null) {
                this.resolvedTool = Tool.SOX;
                this.resolvedBinary = soxBin;
                return;
            }
            String ffBin = resolveBinary("ffmpeg");
            if (ffBin != null) {
                this.resolvedTool = Tool.FFMPEG;
                this.resolvedBinary = ffBin;
                return;
            }
            throw new IllegalStateException(
                    "No recording tool found. Install sox (`brew install sox`) or ffmpeg (`brew install ffmpeg`).");
        } else if (requested == WhisperSettings.RecordingTool.SOX) {
            this.resolvedTool = Tool.SOX;
            String bin = resolveBinary("rec");
            if (bin == null) throw new IllegalStateException("sox not found. Install: `brew install sox`.");
            this.resolvedBinary = bin;
        } else {
            this.resolvedTool = Tool.FFMPEG;
            String bin = resolveBinary("ffmpeg");
            if (bin == null) throw new IllegalStateException("ffmpeg not found. Install: `brew install ffmpeg`.");
            this.resolvedBinary = bin;
        }
    }

    public static boolean anyToolAvailable() {
        return resolveBinary("rec") != null || resolveBinary("ffmpeg") != null;
    }

    public boolean isRecording() {
        return process != null && process.isAlive();
    }

    public Path start() throws IOException {
        if (process != null) {
            throw new IllegalStateException("Already recording");
        }

        Path dir = Paths.get(System.getProperty("java.io.tmpdir"), "whisper-idea");
        Files.createDirectories(dir);
        outputPath = dir.resolve("recording-" + System.currentTimeMillis() + ".wav");

        List<String> cmd = new ArrayList<>();
        if (resolvedTool == Tool.SOX) {
            cmd.add(resolvedBinary);
            cmd.add("-q");
            cmd.add("-r"); cmd.add(String.valueOf(sampleRate));
            cmd.add("-c"); cmd.add("1");
            cmd.add("-b"); cmd.add("16");
            cmd.add("-e"); cmd.add("signed-integer");
            cmd.add(outputPath.toString());
        } else {
            cmd.add(resolvedBinary);
            cmd.add("-y");
            cmd.add("-f"); cmd.add(getInputFormat());
            cmd.add("-i"); cmd.add(getInputDevice());
            cmd.add("-ar"); cmd.add(String.valueOf(sampleRate));
            cmd.add("-ac"); cmd.add("1");
            cmd.add("-sample_fmt"); cmd.add("s16");
            cmd.add(outputPath.toString());
        }

        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        process = pb.start();

        process.onExit().thenAccept(p -> {
            processExited = true;
            earlyExitCode = p.exitValue();
        });

        return outputPath;
    }

    public Path stop() throws IOException, InterruptedException {
        if (process == null) {
            throw new IllegalStateException("Not recording");
        }
        Process proc = process;
        Path path = outputPath;

        if (processExited) {
            process = null;
            Integer code = earlyExitCode;
            if (code != null && code != 0) {
                throw new IOException("Recording failed (" + resolvedTool
                        + " exited with code " + code + "). Check microphone access.");
            }
            return path;
        }

        sendSigint(proc);
        boolean done = proc.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
        if (!done) {
            proc.destroyForcibly();
        }
        process = null;
        return path;
    }

    public void dispose() {
        if (process != null) {
            process.destroyForcibly();
            process = null;
        }
    }

    private void sendSigint(Process proc) {
        long pid = proc.pid();
        try {
            new ProcessBuilder("kill", "-INT", String.valueOf(pid)).start().waitFor();
        } catch (Exception ignored) {
            proc.destroy();
        }
    }

    /**
     * Resolve a binary by searching common install paths first (since IDE launched from Finder/Dock
     * inherits stripped PATH), then PATH.
     */
    public static String resolveBinary(String name) {
        List<String> candidates = new ArrayList<>(List.of(
                "/opt/homebrew/bin/" + name,
                "/usr/local/bin/" + name,
                "/opt/local/bin/" + name,
                "/usr/bin/" + name
        ));
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String dir : pathEnv.split(File.pathSeparator)) {
                if (!dir.isEmpty()) candidates.add(dir + "/" + name);
            }
        }
        for (String c : candidates) {
            File f = new File(c);
            if (f.canExecute()) return c;
        }
        return null;
    }

    private static String getInputFormat() {
        if (SystemInfo.isMac) return "avfoundation";
        if (SystemInfo.isWindows) return "dshow";
        return "pulse";
    }

    private static String getInputDevice() {
        if (SystemInfo.isMac) return ":default";
        if (SystemInfo.isWindows) return "audio=Microphone";
        return "default";
    }
}
