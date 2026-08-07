package storagesign;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class CompatibilityArchitectureTest {

    private static final Pattern CONTROL_FLOW_KEYWORD =
        Pattern.compile("\\b(if|while|for|switch)\\s*\\(");
    private static final Pattern NUMERIC_MINECRAFT_VERSION =
        Pattern.compile("(?<![\\w.])1\\.\\d{1,2}(?:\\.\\d+)?(?![\\w.])");
    private static final Pattern VERSION_CONTEXT =
        Pattern.compile("(?i)\\b(?:minecraft|bukkit|paper|server|version|mc)\\w*\\b");

    @Test
    void productSourceDoesNotLinkVersionSpecificCompatibilityApis() throws IOException {
        assertFalse(anyProductSourceMatches(source -> source.contains("import io.papermc.")));
        assertFalse(anyProductSourceMatches(
            source -> source.contains("import com.destroystokyo.paper.")));
        assertFalse(anyProductSourceMatches(
            source -> source.contains("org.bukkit.craftbukkit.")));
        assertFalse(anyProductSourceMatches(source -> source.contains("PatternType.valueOf(")));
        assertFalse(anyProductSourceMatches(source -> source.contains("PlayerSignOpenEvent event")));
        assertFalse(anyProductSourceMatches(source -> source.contains("getMinecraftVersion(")));
        assertFalse(anyProductSourceMatches(source -> source.contains("getBukkitVersion(")));
        assertFalse(anyProductSourceMatches(this::hasVersionSpecificNumericBranch));
    }

    @Test
    void detectsNumericVersionBranchesWithoutMatchingCommentsOrCoordinates() {
        assertTrue(hasVersionSpecificNumericBranch(
            "if (serverVersion.equals(\"1.21.4\")) {}"));
        assertTrue(hasVersionSpecificNumericBranch(
            "switch (minecraftVersion) { case \"1.21.4\" -> {} }"));
        assertTrue(hasVersionSpecificNumericBranch(
            "return bukkitVersion.equals(\"1.21.4\") ? a : b;"));
        assertFalse(hasVersionSpecificNumericBranch("// MC 1.13 to 1.14 migration"));
        assertFalse(hasVersionSpecificNumericBranch("return position.y() + 1.25;"));
    }

    private boolean anyProductSourceMatches(Predicate<String> condition) throws IOException {
        try (var files = Files.walk(Path.of("src/main/java"))) {
            return files.filter(path -> path.toString().endsWith(".java"))
                .map(this::read)
                .anyMatch(condition);
        }
    }

    private boolean hasVersionSpecificNumericBranch(String source) {
        String sourceWithoutComments = removeComments(source);
        var controlFlow = CONTROL_FLOW_KEYWORD.matcher(sourceWithoutComments);
        while (controlFlow.find()) {
            int conditionStart = controlFlow.end() - 1;
            String condition = parenthesizedExpression(sourceWithoutComments, conditionStart);
            if (NUMERIC_MINECRAFT_VERSION.matcher(condition).find()
                && VERSION_CONTEXT.matcher(condition).find()) {
                return true;
            }
            if ("switch".equals(controlFlow.group(1))
                && VERSION_CONTEXT.matcher(condition).find()
                && numericVersionInSwitchBody(sourceWithoutComments, conditionStart + condition.length())) {
                return true;
            }
        }
        return numericVersionInTernary(sourceWithoutComments);
    }

    private boolean numericVersionInSwitchBody(String source, int start) {
        int bodyStart = source.indexOf('{', start);
        if (bodyStart < 0) {
            return false;
        }
        int bodyEnd = matchingDelimiter(source, bodyStart, '{', '}');
        return bodyEnd >= 0
            && NUMERIC_MINECRAFT_VERSION.matcher(source.substring(bodyStart, bodyEnd + 1)).find();
    }

    private boolean numericVersionInTernary(String source) {
        for (int questionMark = source.indexOf('?'); questionMark >= 0;
             questionMark = source.indexOf('?', questionMark + 1)) {
            int start = Math.max(
                Math.max(source.lastIndexOf(';', questionMark), source.lastIndexOf('{', questionMark)),
                source.lastIndexOf('}', questionMark)
            ) + 1;
            int end = source.indexOf(';', questionMark);
            String expression = source.substring(start, end >= 0 ? end : source.length());
            if (NUMERIC_MINECRAFT_VERSION.matcher(expression).find()
                && VERSION_CONTEXT.matcher(expression).find()) {
                return true;
            }
        }
        return false;
    }

    private String parenthesizedExpression(String source, int start) {
        int end = matchingDelimiter(source, start, '(', ')');
        if (end >= 0) {
            return source.substring(start, end + 1);
        }
        throw new IllegalArgumentException("Unclosed control-flow condition");
    }

    private int matchingDelimiter(String source, int start, char opening, char closing) {
        int depth = 0;
        for (int index = start; index < source.length(); index++) {
            char character = source.charAt(index);
            if (character == opening) {
                depth++;
            } else if (character == closing && --depth == 0) {
                return index;
            }
        }
        return -1;
    }

    private String removeComments(String source) {
        StringBuilder result = new StringBuilder(source.length());
        boolean inString = false;
        boolean inCharacter = false;
        boolean escaped = false;
        for (int index = 0; index < source.length(); index++) {
            char character = source.charAt(index);
            char next = index + 1 < source.length() ? source.charAt(index + 1) : '\0';
            if (!inString && !inCharacter && character == '/' && next == '/') {
                index = source.indexOf('\n', index);
                if (index < 0) {
                    break;
                }
                result.append('\n');
            } else if (!inString && !inCharacter && character == '/' && next == '*') {
                int commentEnd = source.indexOf("*/", index + 2);
                if (commentEnd < 0) {
                    break;
                }
                for (int commentIndex = index; commentIndex < commentEnd; commentIndex++) {
                    if (source.charAt(commentIndex) == '\n') {
                        result.append('\n');
                    }
                }
                index = commentEnd + 1;
            } else {
                result.append(character);
                if (!escaped && character == '"' && !inCharacter) {
                    inString = !inString;
                } else if (!escaped && character == '\'' && !inString) {
                    inCharacter = !inCharacter;
                }
                escaped = character == '\\' && !escaped;
                if (character != '\\') {
                    escaped = false;
                }
            }
        }
        return result.toString();
    }

    private String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
