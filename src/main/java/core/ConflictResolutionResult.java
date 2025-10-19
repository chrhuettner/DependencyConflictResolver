package core;

import java.io.Serializable;

public record ConflictResolutionResult(String code, String response) implements Serializable { }
