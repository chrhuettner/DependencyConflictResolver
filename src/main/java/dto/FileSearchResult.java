package dto;

import java.io.File;
import java.util.jar.JarEntry;

public record FileSearchResult(File file, JarEntry entry) {
}
