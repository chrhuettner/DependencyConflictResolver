package core.context;

import core.*;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static core.BumpRunner.*;

public class MethodChainProvider extends BrokenCodeRegexProvider{
    public static final Pattern METHOD_CHAIN_PATTERN = Pattern.compile(
                         "\\.?([A-Za-z_]\\w*)\\s*(?:\\([^()]*\\))?");

    protected MethodChainProvider(Context context) {
        super(context, Pattern.compile("((new|=|\\.)\\s*\\w+)\\s*(?=\\(.*\\))"));
    }

    @Override
    public ErrorLocation getErrorLocation(LogParser.CompileError compileError, BrokenCode brokenCode) {
        MethodChainAnalysis methodChainAnalysis = analyseMethodChain(context.getCompileError().column, brokenCode.start(), brokenCode.code(), context.getTargetDirectoryClasses(), context.getStrippedFileName(),
                context.getStrippedClassName(), context.getTargetPathOld(), context.getTargetPathNew(), context.getOutputDirSrcFiles().toPath().resolve(Path.of(context.getDependencyArtifactId() + "_" + context.getStrippedFileName())).toString());

        return new ErrorLocation(methodChainAnalysis.targetClass(), methodChainAnalysis.targetMethod(), methodChainAnalysis.parameterTypes());
    }

    public static MethodChainAnalysis analyseMethodChain(int compileErrorColumn, int line, String brokenCode, String targetDirectoryClasses,
                                                         String strippedFileName, String strippedClassName, Path targetPathOld, Path targetPathNew, String srcDirectory) {
        int errorIndex = Math.min(compileErrorColumn, brokenCode.length() - 1);
        String brokenSymbol = "";

        SourceCodeAnalyzer sourceCodeAnalyzer = new SourceCodeAnalyzer(srcDirectory);
        if (brokenCode.contains("=")) {
            brokenCode = brokenCode.substring(brokenCode.indexOf("=") + 1).trim();
        }

        boolean isConstructor = false;

        if (brokenCode.contains("new")) {
            brokenCode = brokenCode.substring(brokenCode.indexOf("new") + "new".length()).trim();
            isConstructor = true;
        }

        if (brokenCode.contains("return")) {
            brokenCode = brokenCode.substring(brokenCode.indexOf("return") + "return".length()).trim();
        }

        String targetClass = "";
        String targetMethod = "";

        String[] parameterNames = new String[0];


        List<String> precedingMethodChain = new ArrayList<>();

        while (brokenCode.indexOf("(") != brokenCode.indexOf(")") - 1) {
            int closingBraceIndex = getClosingBraceIndex(brokenCode, brokenCode.indexOf("("));
            String potentialInnerChain = brokenCode.substring(brokenCode.indexOf("(") + 1, closingBraceIndex);
            if (!potentialInnerChain.contains("(")) {
                break;
            }
            if (errorIndex >= brokenCode.indexOf("(") && errorIndex < closingBraceIndex) {
                brokenCode = potentialInnerChain;
            } else {
                brokenCode = brokenCode.substring(0, brokenCode.indexOf("(") + 1) + brokenCode.substring(closingBraceIndex);
            }

        }

        Matcher methodChainMatcher = METHOD_CHAIN_PATTERN.matcher(brokenCode);

        List<String[]> methodChainParams = new ArrayList<>();

        while (methodChainMatcher.find()) {

            String method = methodChainMatcher.group().replaceAll("\\.", "");
            if (method.contains("(")) {
                method = method.substring(0, method.indexOf("("));

                int methodCallStart = brokenCode.indexOf(method);
                int methodCallEnd = getClosingBraceIndex(brokenCode, methodCallStart);

                String methodCall = brokenCode.substring(methodCallStart, methodCallEnd + 1);
                methodChainParams.add(getParameterTypesOfMethodCall(sourceCodeAnalyzer, methodCall, targetDirectoryClasses, strippedFileName, strippedClassName, line).toArray(new String[]{}));
            } else {
                methodChainParams.add(new String[]{});
            }

            precedingMethodChain.add(method);
        }


        if (precedingMethodChain.size() > 0) {
            String classNameOfVariable;
            if (precedingMethodChain.get(0).equals("super")) {
                classNameOfVariable = readParent(strippedClassName, targetDirectoryClasses, strippedFileName);
            } else if (precedingMethodChain.get(0).equals("this")) {
                classNameOfVariable = strippedClassName;
            } else {

                classNameOfVariable = getClassNameOfVariable(precedingMethodChain.get(0), Paths.get(targetDirectoryClasses + "/" + strippedFileName + "_" + strippedClassName), line);

                if (precedingMethodChain.size() == 1) {
                    targetClass = classNameOfVariable;
                    if (targetMethod.isEmpty()) {
                        targetMethod = brokenSymbol;
                    }
                } else if (classNameOfVariable == null) {
                    String parent = readParent(strippedClassName, targetDirectoryClasses, strippedFileName);
                    if (parent != null) {
                        Path parentPath = searchForClassInSourceFiles(new File(srcDirectory), parent);
                        if (parentPath != null) {
                            classNameOfVariable = getClassNameOfVariable(precedingMethodChain.get(0), parentPath, Integer.MAX_VALUE);
                        } else {
                            classNameOfVariable = sourceCodeAnalyzer.getTypeOfFieldInClass(new File(srcDirectory + "/tmp/dependencies"), parent, precedingMethodChain.get(0));

                        }
                    }
                }
            }

            if (classNameOfVariable == null) {
                // Assume its a static call
                targetClass = precedingMethodChain.get(0);
                if (isConstructor) {
                    targetMethod = precedingMethodChain.get(0);
                }
            }
            if (precedingMethodChain.size() > 1) {
                if (classNameOfVariable == null) {
                    return null;
                }
                //JarDiffUtil jarDiffUtil = new JarDiffUtil(targetPathOld.toString(), targetPathNew.toString());

                String previousClassName = null;
                String intermediateClassName = classNameOfVariable;


                for (int i = 1; i < precedingMethodChain.size() - 1; i++) {
                    if (intermediateClassName == null) {
                        return new MethodChainAnalysis(previousClassName, precedingMethodChain.get(i - 1), methodChainParams.get(i - 1));
                    }
                    previousClassName = intermediateClassName;
                    intermediateClassName = sourceCodeAnalyzer.getReturnTypeOfMethod(intermediateClassName, precedingMethodChain.get(i), methodChainParams.get(i));
                }

                if (intermediateClassName == null) {
                    return new MethodChainAnalysis(previousClassName, precedingMethodChain.get(precedingMethodChain.size() - 2), methodChainParams.get(methodChainParams.size() - 2));
                }

                targetClass = intermediateClassName;
                targetMethod = precedingMethodChain.get(precedingMethodChain.size() - 1);

                System.out.println("targetClass: " + targetClass);
                System.out.println("targetMethod: " + targetMethod);

                String targetMethodParameters = brokenCode.substring(brokenCode.indexOf(targetMethod) + targetMethod.length());

                if (!(targetMethodParameters.isEmpty() || targetMethodParameters.equals("()"))) {


                    int openBrackets = 0;
                    int closingBrackets = 0;
                    int end = targetMethodParameters.length();
                    for (int i = 0; i < targetMethodParameters.length(); i++) {
                        char c = targetMethodParameters.charAt(i);
                        if (c == '(') {
                            openBrackets++;
                        } else if (c == ')') {
                            closingBrackets++;
                        }

                        if (openBrackets == closingBrackets) {
                            end = i - 1;
                        }
                    }

                    targetMethodParameters = targetMethodParameters.substring(1, end);


                    // TODO: This might fail if the parameter has a method invocation with multiple parameters seperated by ,

                    if (!targetMethodParameters.isBlank()) {
                        parameterNames = targetMethodParameters.split(",");

                        for (int i = 0; i < parameterNames.length; i++) {
                            String parameterName = parameterNames[i].trim();
                            if (parameterName.endsWith(".class")) {
                                parameterNames[i] = "java.lang.Class";
                            } else {
                                String callerClass = getClassNameOfVariable(parameterName, Paths.get(targetDirectoryClasses + "/" + strippedFileName + "_" + strippedClassName), line);
                                parameterNames[i] = callerClass;
                            }
                        /*if (parameterName.contains("(")) {
                            String[] splitParameter = parameterName.split("\\.");
                            String callerClass = getClassNameOfVariable(splitParameter[0], targetDirectoryClasses, strippedFileName, strippedClassName, line);
                            String methodName = splitParameter[1].substring(0, splitParameter[1].indexOf("("));
                            //parameterNames[i] = jarDiffUtil.getMethodReturnType(callerClass, methodName);
                        }*/
                        }
                    }
                }

            }
            System.out.println(brokenSymbol);
        } else {
            targetClass = brokenSymbol;
        }

        return new MethodChainAnalysis(targetClass, targetMethod, parameterNames);
    }
}
