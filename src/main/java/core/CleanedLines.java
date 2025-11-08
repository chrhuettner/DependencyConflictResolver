package core;

import java.util.List;

public record CleanedLines(List<String> lines, int indexBeforeCleaning, int indexAfterCleaning) {
}
