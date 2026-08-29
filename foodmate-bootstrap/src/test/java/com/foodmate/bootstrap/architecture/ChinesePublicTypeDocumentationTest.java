package com.foodmate.bootstrap.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** 公共类型类级 Javadoc 的中文说明契约测试。 */
class ChinesePublicTypeDocumentationTest {
    private static final Pattern PUBLIC_TYPE =
            Pattern.compile(
                    "^\\s*public\\s+(?:(?:final|abstract|static)\\s+)*(?:class|interface|record|enum)\\s+\\w+");
    private static final Pattern CHINESE = Pattern.compile("[\\u4e00-\\u9fff]");

    @Test
    void publicTypesWithClassDocumentationMustContainChineseDescription() throws IOException {
        Path root = projectRoot();
        List<String> violations =
                List.of(
                                "foodmate-shared",
                                "foodmate-application",
                                "foodmate-infra",
                                "foodmate-api",
                                "foodmate-bootstrap")
                        .stream()
                        .flatMap(module -> javaFiles(root.resolve(module + "/src/main/java")))
                        .flatMap(path -> documentedPublicTypes(path).stream())
                        .filter(document -> !CHINESE.matcher(document.comment()).find())
                        .map(document -> document.path() + ":" + document.line())
                        .sorted()
                        .toList();

        assertThat(violations).as("公共类型的类级 Javadoc 必须包含中文说明").isEmpty();
    }

    private static Stream<Path> javaFiles(Path sourceRoot) {
        if (!Files.isDirectory(sourceRoot)) return Stream.empty();
        try {
            return Files.walk(sourceRoot).filter(path -> path.toString().endsWith(".java"));
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取 Java 源码目录: " + sourceRoot, exception);
        }
    }

    private static List<Documentation> documentedPublicTypes(Path path) {
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            List<Documentation> documents = new java.util.ArrayList<>();
            for (int index = 0; index < lines.size(); index++) {
                if (!PUBLIC_TYPE.matcher(lines.get(index)).find()) continue;
                int end = index - 1;
                while (end >= 0
                        && (lines.get(end).isBlank() || lines.get(end).trim().startsWith("@"))) {
                    end--;
                }
                if (end < 0 || !lines.get(end).trim().endsWith("*/")) continue;
                int start = end;
                while (start >= 0 && !lines.get(start).trim().startsWith("/**")) start--;
                if (start >= 0) {
                    documents.add(
                            new Documentation(
                                    path.toString(),
                                    index + 1,
                                    String.join("\n", lines.subList(start, end + 1))));
                }
            }
            return documents;
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取 Java 源码文件: " + path, exception);
        }
    }

    private static Path projectRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            if (Files.isDirectory(current.resolve("foodmate-shared"))) return current;
            current = current.getParent();
        }
        throw new IllegalStateException("找不到 FoodMate 项目根目录");
    }

    private record Documentation(String path, int line, String comment) {}
}
