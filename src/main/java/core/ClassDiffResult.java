package core;

import japicmp.model.JApiMethod;

import java.util.List;

public record ClassDiffResult(String classDiff, String methodDiff, List<JApiMethod> methodsWithSameName, List<SimilarityResult> similarMethods) {
}
