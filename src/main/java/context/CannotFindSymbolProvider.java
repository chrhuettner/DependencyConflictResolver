package context;

import docker.ContainerUtil;
import dto.BrokenCode;
import dto.ErrorLocation;

import java.io.File;
import java.nio.file.Path;

public class CannotFindSymbolProvider extends ContextProvider {
    public CannotFindSymbolProvider(Context context) {
        super(context);
    }

    @Override
    public boolean errorIsTargetedByProvider(LogParser.CompileError compileError, BrokenCode brokenCode) {
        return compileError.message.equals("cannot find symbol");
    }

    @Override
    public ErrorLocation getErrorLocation(LogParser.CompileError compileError, BrokenCode brokenCode) {
        String targetClass = null;
        String targetMethod = null;
        String[] targetMethodParameterClassNames = null;
        if (compileError.details.containsKey("symbol")) {
            String sym = compileError.details.get("symbol");
            if (sym.startsWith("symbol")) {
                sym = sym.substring("symbol".length() + 1).trim();
            }
            if (sym.startsWith("class")) {
                targetClass = extractClassNameFromString(sym);
            } else if (sym.startsWith("method")) {
                targetMethod = compileError.details.get("symbol");
                targetMethod = targetMethod.substring(targetMethod.indexOf(" ") + 1);
                if (targetMethod.indexOf("(") != targetMethod.indexOf(")") - 1) {
                    String parameterString = targetMethod.substring(targetMethod.indexOf("(") + 1, targetMethod.indexOf(")"));
                    String[] parameters = parameterString.split(",");
                    targetMethodParameterClassNames = new String[parameters.length];
                    for (int i = 0; i < parameters.length; i++) {
                        targetMethodParameterClassNames[i] = ContextUtil.primitiveClassNameToWrapperName(parameters[i]);
                    }
                }

                targetMethod = targetMethod.substring(0, targetMethod.indexOf("("));

                if (compileError.details.containsKey("location")) {
                    targetClass = extractClassNameFromString(compileError.details.get("location"));
                }
            } else if (sym.startsWith("variable")) {
                String variableName = sym.substring("variable".length() + 1).trim();

                Path classPath = ContainerUtil.getPathWithRespectToIteration(context.getTargetDirectoryClasses(), context.getStrippedFileName(), context.getStrippedClassName(), context.getIteration(), true);
                String classNameOfVariable = ContextUtil.getClassNameOfVariable(variableName, classPath, brokenCode.start());

                String srcDir = context.getOutputDirSrcFiles().toPath().resolve(Path.of(context.getDependencyArtifactId() + "_" + context.getStrippedFileName())).toString();
                if (classNameOfVariable == null) {
                    String parent = ContainerUtil.readParent(context.getStrippedClassName(), context.getTargetDirectoryClasses(), context.getStrippedFileName(), context.getIteration());
                    if (parent != null) {
                        Path parentPath = ContainerUtil.searchForClassInSourceFiles(new File(srcDir), parent);
                        if (parentPath != null) {
                            targetClass = ContextUtil.getClassNameOfVariable(variableName, parentPath, Integer.MAX_VALUE);
                        } else {
                            SourceCodeAnalyzer sourceCodeAnalyzer = new SourceCodeAnalyzer(srcDir);
                            targetClass = sourceCodeAnalyzer.getTypeOfFieldInClass(new File(srcDir + "/tmp/dependencies"), parent, variableName);
                        }
                    }
                }

                if(targetClass == null) {
                    //assume static
                    targetClass = variableName;
                }
            } else if (compileError.details.containsKey("location")) {
                targetClass = extractClassNameFromString(compileError.details.get("location"));
            }
        }
        return new ErrorLocation(targetClass, targetMethod, targetMethodParameterClassNames);
    }

    private String extractClassNameFromString(String location) {
        String targetClass = location;
        if (targetClass.contains("of type")) {
            targetClass = targetClass.substring(targetClass.indexOf("of type") + "of type".length() + 1);
        } else {
            targetClass = targetClass.substring(targetClass.indexOf("class") + "class".length() + 1);
        }
        return targetClass;
    }

}
