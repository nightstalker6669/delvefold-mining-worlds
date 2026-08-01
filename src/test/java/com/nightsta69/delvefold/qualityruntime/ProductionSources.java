package com.nightsta69.delvefold.qualityruntime;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class ProductionSources {
    private static final Path ROOT = Path.of("src/main/java/com/nightsta69/delvefold");

    private ProductionSources() {
    }

    static String read(String relativePath) throws IOException {
        return Files.readString(ROOT.resolve(relativePath));
    }

    static List<Path> allJavaFiles() throws IOException {
        try (var paths = Files.walk(ROOT)) {
            return paths.filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .toList();
        }
    }

    static String block(String source, String declaration) {
        int declarationStart = source.indexOf(declaration);
        assertTrue(declarationStart >= 0, () -> "Missing source declaration: " + declaration);
        int openingBrace = source.indexOf('{', declarationStart);
        assertTrue(openingBrace >= 0, () -> "Missing opening brace for: " + declaration);
        int depth = 0;
        for (int index = openingBrace; index < source.length(); index++) {
            char current = source.charAt(index);
            if (current == '{') {
                depth++;
            } else if (current == '}' && --depth == 0) {
                return source.substring(declarationStart, index + 1);
            }
        }
        throw new AssertionError("Unterminated source block: " + declaration);
    }

    static List<String> invocations(String source, String methodName) {
        List<String> result = new ArrayList<>();
        String marker = methodName + '(';
        for (int start = source.indexOf(marker); start >= 0; start = source.indexOf(marker, start + marker.length())) {
            int opening = start + methodName.length();
            int depth = 0;
            for (int index = opening; index < source.length(); index++) {
                char current = source.charAt(index);
                if (current == '(') {
                    depth++;
                } else if (current == ')' && --depth == 0) {
                    result.add(source.substring(opening + 1, index));
                    break;
                }
            }
        }
        return List.copyOf(result);
    }

    static boolean hasTopLevelComma(String arguments) {
        int depth = 0;
        for (int index = 0; index < arguments.length(); index++) {
            char current = arguments.charAt(index);
            if (current == '(') {
                depth++;
            } else if (current == ')') {
                depth--;
            } else if (current == ',' && depth == 0) {
                return true;
            }
        }
        return false;
    }
}
