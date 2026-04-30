package dev.aroussi.whisper.modes;

import dev.aroussi.whisper.WhisperSettings;

import java.util.List;
import java.util.regex.Pattern;

public final class Modes {

    private static final Pattern FILLERS = Pattern.compile(
            "\\b(um+|uh+|ah+|eh+|er+|like|you know|basically|actually|so yeah|i mean)\\b",
            Pattern.CASE_INSENSITIVE);

    private record Sub(Pattern p, String r) {}

    private static final List<Sub> PUNCT = List.of(
            new Sub(Pattern.compile("\\.?\\s*triple equals\\s*\\.?", Pattern.CASE_INSENSITIVE), "==="),
            new Sub(Pattern.compile("\\.?\\s*strict equals\\s*\\.?", Pattern.CASE_INSENSITIVE), "==="),
            new Sub(Pattern.compile("\\.?\\s*double equals\\s*\\.?", Pattern.CASE_INSENSITIVE), "=="),
            new Sub(Pattern.compile("\\.?\\s*not equals?\\s*\\.?", Pattern.CASE_INSENSITIVE), "!="),
            new Sub(Pattern.compile("\\.?\\s*bang equals\\s*\\.?", Pattern.CASE_INSENSITIVE), "!="),
            new Sub(Pattern.compile("\\.?\\s*fat arrow\\s*\\.?", Pattern.CASE_INSENSITIVE), " => "),
            new Sub(Pattern.compile("\\.?\\s*arrow\\s*\\.?", Pattern.CASE_INSENSITIVE), " => "),
            new Sub(Pattern.compile("\\.?\\s*open\\s+par[ae][ni](?:thesis)?\\.?\\s*", Pattern.CASE_INSENSITIVE), "("),
            new Sub(Pattern.compile("\\.?\\s*left\\s+par[ae][ni](?:thesis)?\\.?\\s*", Pattern.CASE_INSENSITIVE), "("),
            new Sub(Pattern.compile("\\.?\\s*close\\s+par[ae][ni](?:thesis)?\\.?\\s*", Pattern.CASE_INSENSITIVE), ")"),
            new Sub(Pattern.compile("\\.?\\s*right\\s+par[ae][ni](?:thesis)?\\.?\\s*", Pattern.CASE_INSENSITIVE), ")"),
            new Sub(Pattern.compile("\\.?\\s*open\\s+bracket\\.?\\s*", Pattern.CASE_INSENSITIVE), "["),
            new Sub(Pattern.compile("\\.?\\s*left\\s+bracket\\.?\\s*", Pattern.CASE_INSENSITIVE), "["),
            new Sub(Pattern.compile("\\.?\\s*close\\s+bracket\\.?\\s*", Pattern.CASE_INSENSITIVE), "]"),
            new Sub(Pattern.compile("\\.?\\s*right\\s+bracket\\.?\\s*", Pattern.CASE_INSENSITIVE), "]"),
            new Sub(Pattern.compile("\\.?\\s*open\\s+(?:brace|curly)\\.?\\s*", Pattern.CASE_INSENSITIVE), "{"),
            new Sub(Pattern.compile("\\.?\\s*left\\s+(?:brace|curly)\\.?\\s*", Pattern.CASE_INSENSITIVE), "{"),
            new Sub(Pattern.compile("\\.?\\s*close\\s+(?:brace|curly)\\.?\\s*", Pattern.CASE_INSENSITIVE), "}"),
            new Sub(Pattern.compile("\\.?\\s*right\\s+(?:brace|curly)\\.?\\s*", Pattern.CASE_INSENSITIVE), "}"),
            new Sub(Pattern.compile("\\.?\\s*(?:period|full stop)\\.?\\s*", Pattern.CASE_INSENSITIVE), "."),
            new Sub(Pattern.compile("\\.?\\s*comma\\.?\\s*", Pattern.CASE_INSENSITIVE), ", "),
            new Sub(Pattern.compile("\\.?\\s*(?:semicolon|semi\\s*colon)\\.?\\s*", Pattern.CASE_INSENSITIVE), ";"),
            new Sub(Pattern.compile("\\.?\\s*colon\\.?\\s*", Pattern.CASE_INSENSITIVE), ":"),
            new Sub(Pattern.compile("\\.?\\s*(?:equals|equal sign)\\.?\\s*", Pattern.CASE_INSENSITIVE), " = "),
            new Sub(Pattern.compile("\\.?\\s*(?:dash|hyphen|minus)\\.?\\s*", Pattern.CASE_INSENSITIVE), "-"),
            new Sub(Pattern.compile("\\.?\\s*plus\\.?\\s*", Pattern.CASE_INSENSITIVE), " + "),
            new Sub(Pattern.compile("\\.?\\s*(?:asterisk|star|times)\\.?\\s*", Pattern.CASE_INSENSITIVE), "*"),
            new Sub(Pattern.compile("\\.?\\s*(?:forward\\s+slash|slash)\\.?\\s*", Pattern.CASE_INSENSITIVE), "/"),
            new Sub(Pattern.compile("\\.?\\s*(?:backslash|back\\s*slash)\\.?\\s*", Pattern.CASE_INSENSITIVE), "\\\\"),
            new Sub(Pattern.compile("\\.?\\s*pipe\\.?\\s*", Pattern.CASE_INSENSITIVE), "|"),
            new Sub(Pattern.compile("\\.?\\s*(?:ampersand|and sign)\\.?\\s*", Pattern.CASE_INSENSITIVE), "&"),
            new Sub(Pattern.compile("\\.?\\s*(?:exclamation|bang)\\.?\\s*", Pattern.CASE_INSENSITIVE), "!"),
            new Sub(Pattern.compile("\\.?\\s*question\\s+mark\\.?\\s*", Pattern.CASE_INSENSITIVE), "?"),
            new Sub(Pattern.compile("\\.?\\s*(?:at sign|at symbol)\\.?\\s*", Pattern.CASE_INSENSITIVE), "@"),
            new Sub(Pattern.compile("\\.?\\s*(?:hash|pound sign|hashtag)\\.?\\s*", Pattern.CASE_INSENSITIVE), "#"),
            new Sub(Pattern.compile("\\.?\\s*dollar sign\\.?\\s*", Pattern.CASE_INSENSITIVE), "$"),
            new Sub(Pattern.compile("\\.?\\s*percent\\.?\\s*", Pattern.CASE_INSENSITIVE), "%"),
            new Sub(Pattern.compile("\\.?\\s*(?:caret|hat)\\.?\\s*", Pattern.CASE_INSENSITIVE), "^"),
            new Sub(Pattern.compile("\\.?\\s*tilde\\.?\\s*", Pattern.CASE_INSENSITIVE), "~"),
            new Sub(Pattern.compile("\\.?\\s*(?:backtick|back\\s*tick)\\.?\\s*", Pattern.CASE_INSENSITIVE), "`"),
            new Sub(Pattern.compile("\\.?\\s*underscore\\.?\\s*", Pattern.CASE_INSENSITIVE), "_"),
            new Sub(Pattern.compile("\\.?\\s*new\\s+line\\.?\\s*", Pattern.CASE_INSENSITIVE), "\n")
    );

    public static String postProcess(String text, WhisperSettings.Mode mode) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return "";
        return switch (mode) {
            case DICTATE -> trimmed;
            case CODE -> cleanForCode(trimmed);
            case COMMAND -> formatAsCommand(trimmed);
        };
    }

    private static String cleanForCode(String text) {
        String r = FILLERS.matcher(text).replaceAll("");
        for (Sub s : PUNCT) r = s.p.matcher(r).replaceAll(s.r);
        r = Pattern.compile("(?:^|\\s)tab(?:\\s|$)", Pattern.CASE_INSENSITIVE).matcher(r).replaceAll("\t");
        return r.replaceAll("\\s{2,}", " ").trim();
    }

    private static String formatAsCommand(String text) {
        String r = FILLERS.matcher(text).replaceAll("").replaceAll("\\s{2,}", " ").trim();
        if (r.isEmpty()) return "";
        r = Character.toUpperCase(r.charAt(0)) + r.substring(1);
        if (!r.matches(".*[.!?]$")) r += ".";
        return r;
    }
}
