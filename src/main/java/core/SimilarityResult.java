package core;

import japicmp.model.JApiMethod;

public record SimilarityResult (JApiMethod method, double similarity){}