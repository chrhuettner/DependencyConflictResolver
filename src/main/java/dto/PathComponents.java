package dto;

import java.nio.file.Path;

public record PathComponents(Path path, String fileNameInContainer) {
}
