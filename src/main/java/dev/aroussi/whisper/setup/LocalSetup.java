package dev.aroussi.whisper.setup;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.SystemInfo;
import dev.aroussi.whisper.recorder.Recorder;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

public final class LocalSetup {

    private static final String WHISPER_DIR = "whisper.cpp";
    private static final String MODEL_BASE_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main";

    public static final class Paths {
        public final Path binary;
        public final Path modelsDir;
        Paths(Path binary, Path modelsDir) { this.binary = binary; this.modelsDir = modelsDir; }
    }

    public static Path getBaseDir() throws IOException {
        Path dir = java.nio.file.Paths.get(PathManager.getPluginsPath(), "whisper", "local");
        Files.createDirectories(dir);
        return dir;
    }

    public static Paths getLocalPaths() throws IOException {
        Path whisperDir = getBaseDir().resolve(WHISPER_DIR);
        List<Path> candidates = List.of(
                whisperDir.resolve(java.nio.file.Paths.get("build", "bin", "whisper-cli")),
                whisperDir.resolve(java.nio.file.Paths.get("build", "bin", "main")),
                whisperDir.resolve("main")
        );
        Path binary = candidates.stream()
                .filter(Files::exists)
                .findFirst()
                .orElse(candidates.get(0));
        return new Paths(binary, whisperDir.resolve("models"));
    }

    public static boolean isReady(String model) {
        try {
            Paths p = getLocalPaths();
            return Files.exists(p.binary)
                    && Files.exists(p.modelsDir.resolve("ggml-" + model + ".bin"));
        } catch (IOException e) {
            return false;
        }
    }

    public static void setup(String model, ProgressIndicator indicator) throws IOException, InterruptedException {
        Path whisperDir = getBaseDir().resolve(WHISPER_DIR);

        indicator.setText("Checking prerequisites...");
        checkPrerequisites();

        String git = resolveOrThrow("git");
        String cmake = resolveOrThrow("cmake");

        if (!Files.exists(whisperDir.resolve("CMakeLists.txt"))) {
            indicator.setText("Downloading whisper.cpp...");
            run(java.nio.file.Paths.get("."), git, "clone", "--depth", "1",
                    "https://github.com/ggerganov/whisper.cpp.git", whisperDir.toString());
        }

        Path buildDir = whisperDir.resolve("build");
        Files.createDirectories(buildDir);

        indicator.setText("Compiling whisper.cpp (may take a minute)...");
        run(buildDir, cmake, "..", "-DCMAKE_BUILD_TYPE=Release");
        run(buildDir, cmake, "--build", ".", "--config", "Release", "-j");

        Path modelsDir = whisperDir.resolve("models");
        Files.createDirectories(modelsDir);
        Path modelFile = modelsDir.resolve("ggml-" + model + ".bin");
        if (!Files.exists(modelFile)) {
            indicator.setText("Downloading " + model + " model...");
            downloadFile(MODEL_BASE_URL + "/ggml-" + model + ".bin", modelFile);
        }
    }

    private static void checkPrerequisites() {
        java.util.List<String> missing = new java.util.ArrayList<>();
        if (Recorder.resolveBinary("git") == null) missing.add("git");
        if (Recorder.resolveBinary("cmake") == null) missing.add("cmake");
        if (Recorder.resolveBinary("make") == null) missing.add("make");
        if (Recorder.resolveBinary("cc") == null
                && Recorder.resolveBinary("gcc") == null
                && Recorder.resolveBinary("clang") == null) {
            missing.add("C++ compiler (gcc/clang)");
        }
        if (!missing.isEmpty()) {
            String hint = SystemInfo.isMac
                    ? "Install Xcode CLT: `xcode-select --install` and cmake: `brew install cmake`"
                    : "Install build tools: `sudo apt install build-essential cmake git`";
            throw new IllegalStateException("Missing prerequisites: " + String.join(", ", missing) + ". " + hint);
        }
    }

    private static String resolveOrThrow(String cmd) {
        String bin = Recorder.resolveBinary(cmd);
        if (bin == null) throw new IllegalStateException("Missing required tool: " + cmd);
        return bin;
    }

    private static void run(Path cwd, String... cmd) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd).directory(cwd.toFile()).redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes());
        int code = p.waitFor();
        if (code != 0) {
            throw new IOException(cmd[0] + " failed (code " + code + "): "
                    + output.substring(Math.max(0, output.length() - 500)));
        }
    }

    private static void downloadFile(String url, Path dest) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<InputStream> res = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (res.statusCode() != 200) {
            throw new IOException("Download failed: HTTP " + res.statusCode());
        }
        try (InputStream in = res.body()) {
            Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
