package dto;

public record ErrorLocation (String className, String methodName, String[] targetMethodParameterClassNames){}