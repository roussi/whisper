package dev.aroussi.whisper.transcriber;

import dev.aroussi.whisper.WhisperSettings;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class Transcriber {

    public static final class Config {
        public WhisperSettings.Backend backend;
        public String apiKey;
        public String language = "en";
        public String localWhisperPath;
        public String localWhisperModel = "base";
        public String localModelsDir;
    }

    public static String transcribe(Path audioPath, Config cfg) throws IOException, InterruptedException {
        long size = Files.size(audioPath);
        if (size < 1000) return "";

        return switch (cfg.backend) {
            case OPENAI -> transcribeApi("api.openai.com", "/v1/audio/transcriptions",
                    cfg.apiKey, "whisper-1", audioPath, cfg.language);
            case GROQ -> transcribeApi("api.groq.com", "/openai/v1/audio/transcriptions",
                    cfg.apiKey, "whisper-large-v3", audioPath, cfg.language);
            case LOCAL -> transcribeLocal(audioPath, cfg);
        };
    }

    private static String transcribeLocal(Path audioPath, Config cfg) throws IOException, InterruptedException {
        String binary = cfg.localWhisperPath != null && !cfg.localWhisperPath.isEmpty()
                ? cfg.localWhisperPath : "whisper";
        String modelPath = cfg.localModelsDir != null
                ? Paths.get(cfg.localModelsDir, "ggml-" + cfg.localWhisperModel + ".bin").toString()
                : "models/ggml-" + cfg.localWhisperModel + ".bin";

        List<String> args = new ArrayList<>(List.of(
                binary,
                "-m", modelPath,
                "-f", audioPath.toString(),
                "-l", cfg.language,
                "--no-timestamps",
                "-np"
        ));

        ProcessBuilder pb = new ProcessBuilder(args);
        pb.redirectErrorStream(false);
        Process p = pb.start();
        String stdout = new String(p.getInputStream().readAllBytes());
        String stderr = new String(p.getErrorStream().readAllBytes());
        int code = p.waitFor();
        if (code != 0) {
            throw new IOException("Local whisper failed (code " + code + "): "
                    + stderr.substring(Math.max(0, stderr.length() - 500)));
        }
        return cleanWhisperOutput(stdout);
    }

    private static String transcribeApi(String host, String path, String apiKey,
                                        String model, Path audioPath, String language)
            throws IOException, InterruptedException {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IOException("API key is required.");
        }
        String boundary = "----Whisper" + System.currentTimeMillis();
        byte[] body = buildMultipartBody(boundary, model, language, audioPath);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://" + host + path))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(BodyPublishers.ofByteArray(body))
                .build();

        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            throw new IOException("API error (" + res.statusCode() + "): " + res.body());
        }
        return cleanWhisperOutput(extractTextField(res.body()));
    }

    private static byte[] buildMultipartBody(String boundary, String model,
                                             String language, Path audioPath) throws IOException {
        var out = new java.io.ByteArrayOutputStream();
        OutputStream os = out;
        String pre = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"audio.wav\"\r\n"
                + "Content-Type: audio/wav\r\n\r\n";
        os.write(pre.getBytes());
        try (InputStream in = Files.newInputStream(audioPath)) {
            in.transferTo(os);
        }
        String post = "\r\n--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"model\"\r\n\r\n" + model + "\r\n"
                + "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"language\"\r\n\r\n" + language + "\r\n"
                + "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"response_format\"\r\n\r\njson\r\n"
                + "--" + boundary + "--\r\n";
        os.write(post.getBytes());
        return out.toByteArray();
    }

    private static String extractTextField(String json) {
        var m = Pattern.compile("\"text\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
        if (!m.find()) return "";
        return m.group(1)
                .replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    private static final Pattern[] STRIP = {
            Pattern.compile("\\[BLANK_AUDIO\\]", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\[SILENCE\\]", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\[INAUDIBLE\\]", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\(blank audio\\)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\(silence\\)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\(inaudible\\)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\[MUSIC\\]", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\[NOISE\\]", Pattern.CASE_INSENSITIVE),
    };

    static String cleanWhisperOutput(String raw) {
        String r = raw;
        for (Pattern p : STRIP) r = p.matcher(r).replaceAll("");
        return r.replaceAll("\\s{2,}", " ").trim();
    }
}
