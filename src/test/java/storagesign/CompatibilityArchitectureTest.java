package storagesign;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CompatibilityArchitectureTest {

    @Test
    void productSourceDoesNotLinkVersionSpecificCompatibilityApis() throws IOException {
        String sources;
        try (var files = Files.walk(Path.of("src/main/java"))) {
            sources = files.filter(path -> path.toString().endsWith(".java"))
                .map(this::read)
                .reduce("", (left, right) -> left + "\n" + right);
        }

        assertFalse(sources.contains("import io.papermc."));
        assertFalse(sources.contains("PatternType.valueOf("));
        assertFalse(sources.contains("PlayerSignOpenEvent event"));
        assertFalse(sources.matches("(?s).*Minecraft\\s+1\\.21\\..*"));
    }

    private String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
