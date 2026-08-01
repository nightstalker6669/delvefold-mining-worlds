package com.nightsta69.delvefold.qualityruntime;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class PackageContractCoverageTest {
    private static final Pattern PACKAGE_CONTRACT = Pattern.compile(
            "\\A/\\*\\*.+?\\*/\\R@NullMarked\\Rpackage [A-Za-z_$][A-Za-z0-9_$.]*;"
                    + "\\R\\Rimport org\\.jspecify\\.annotations\\.NullMarked;\\R\\z",
            Pattern.DOTALL);

    @Test
    void everyProductionAndTestPackageIsDocumentedAndNullMarked() throws IOException {
        assertPackageContracts(Path.of("src/main/java"));
        assertPackageContracts(Path.of("src/test/java"));
    }

    private static void assertPackageContracts(Path sourceRoot) throws IOException {
        List<Path> packageDirectories;
        try (var paths = Files.walk(sourceRoot)) {
            packageDirectories = paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .filter(path -> !path.getFileName().toString().equals("package-info.java"))
                    .map(Path::getParent)
                    .distinct()
                    .sorted()
                    .toList();
        }

        for (Path directory : packageDirectories) {
            Path contract = directory.resolve("package-info.java");
            assertTrue(Files.isRegularFile(contract), () -> "Missing package contract: " + contract);
            String source = Files.readString(contract);
            assertTrue(
                    PACKAGE_CONTRACT.matcher(source).matches(),
                    () -> "Package contract must contain real Javadoc and a JSpecify @NullMarked package: " + contract);
        }
    }
}
