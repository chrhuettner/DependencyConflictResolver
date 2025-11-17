package dto;

import java.nio.file.Path;

public record ClassPath(Path path, String strippedClassName) {
}
