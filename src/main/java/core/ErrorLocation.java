package core;

public record ErrorLocation (String className, String methodName, String[] targetMethodParameterClassNames){}