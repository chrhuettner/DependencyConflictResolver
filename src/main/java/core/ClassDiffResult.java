package core;

import japicmp.model.JApiMethod;

import java.util.List;

public record ClassDiffResult(String classDiff, List<JApiMethod> methodsWithSameName, List<SimilarityResult> similarMethods) {
}
