package solver.nondeterministic;

import core.*;
import japicmp.model.JApiConstructor;
import japicmp.model.JApiMethod;
import japicmp.model.JApiParameter;
import provider.AIProvider;
import provider.AIProviderException;
import solver.CodeConflictSolver;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static core.BumpRunner.usePromptCaching;

public class LLMCodeConflictSolver extends CodeConflictSolver {

    static String systemContext = """
            You are a software engineer assistant specialized in resolving dependency conflicts by modifying source code.
            Given code diffs between different versions of a shared dependency and client library code that breaks because of API changes, you suggest minimal, safe code adaptations so the client library works correctly with the target dependency version.
            Focus on clear, maintainable code changes and explain your reasoning if needed.
            """;

    public static String codeStart = "---BEGIN UPDATED java CODE---";
    public static String codeEnd = "---END UPDATED java CODE---";

    static String promptTemplatePrefix = """
            You are a software migration assistant.
            
            I am upgrading my project from %s %s to version %s of a dependency that includes breaking API changes.
            
            You are given the following input:
            """;
    static String promptTemplateMethodDiff = """
            **Method-level diff** showing changes to the method being used in my code:
            ```diff
            %s
            ```
            """;
    static String promptTemplateFullDiff = """
            **Full diff of the dependency** between versions %s and %s (for additional context):
            ```diff
            %s
            ```
            """;
    static String promptTemplateErroneousClass = """
            
            Class from my project that is broken after the upgrade to version %s, provided to you as a list seperated by commas:
            ```
            %s
            ```
            """;

    static String promptTemplateErroneousScope = """
            
            Scope from my project that is broken after the upgrade:
            ```
            %s
            ```
            """;
    static String promptTemplateCodeLine = """ 
            **Line of code from my project** that is broken after the upgrade to version %s:
            ```java
            %s
            ```
            """;
    static String promptTemplateSimilarity = """       
            **Method-similarity** showing similar methods that could be used instead of the broken one:
            ```similarity
            %s
            ```
            """;
    static String promptTemplateError = """
            
            **Error** showing the error:
            ```
            %s
            ```
            """;


    static String promptTemplateSuffix = """    
             ----
            
             Your task:
             - Read both diffs and the broken code snippet.
             - Identify what changed in the method being used.
             - Consider the method similarity when you are not sure which method to choose.
             - Focus your reasoning and output **only on the provided broken line**, not on the entire method or class scope.
             - Output the following in the exact format below.
            
             ----
            
             **RESPONSE FORMAT (STRICT)**
            
             1. A **very brief** explanation of why the code is failing.
                - Maximum 2 sentences.
                - No introductions like "Sure" or "Here's the issue".
            
             2. Then output **only the changed or inserted lines**, using this exact format (do not deviate):
            
             ```
             ---BEGIN UPDATED java CODE---
             // Insert your updated java code here
             ---END UPDATED java CODE---
             ```
            
             ----
            
             **Rules**:
            - Do NOT include full classes or unrelated methods.
            - Do NOT output anything before or after the required format.
            - Do NOT explain anything outside the two-sentence explanation above.
            - Do NOT include headings, intros, or closing remarks.
            - Only start your updated java code with slashes if you want it to be commented out code.
            - Preserve all trailing symbols (such as braces, semicolons, parentheses) exactly as in the original code to ensure it compiles.
            - When the error message occurs on a return statement, inspect the expected type in the error message and make the returned expression match that type.
            - Restrict changes strictly to the given broken line. Do not rewrite the surrounding method or signature unless the error explicitly requires it.
            - Always ensure that the types you construct or return match the expected types indicated by the method signature or compiler error.
            - When a type mismatch occurs, fix the type by using the correct class or constructor rather than forcing a conversion.
            - Prefer consistency with the dependency’s updated API (as shown in the diff) over preserving old code patterns.
            - Avoid guessing types based on naming; instead, infer them from the diff, return types, and error messages.
            
             Your response will be **automatically parsed**, so it must match the format **exactly**.
            """;
    private AIProvider aiProvider;

    public LLMCodeConflictSolver(Context context, AIProvider aiProvider) {
        super(context);
        this.aiProvider = aiProvider;
    }

    public AIProvider getProvider() {
        return aiProvider;
    }

    public String buildPrompt(BrokenCode brokenCode, ErrorLocation errorLocation) {

        String erroneousClass = readBrokenClass(context.getStrippedClassName(), context.getTargetDirectoryClasses(), context.getStrippedFileName());
        String erroneousScope = readBrokenEnclosingScope(context.getStrippedClassName(), context.getTargetDirectoryClasses(), context.getStrippedFileName(), context.getCompileError().line);

        String errorPrompt = "line: " + context.getCompileError().line + ", column: " + context.getCompileError().column + System.lineSeparator() + context.getCompileError().message;

        for (String detail : context.getCompileError().details.keySet()) {
            errorPrompt = errorPrompt + System.lineSeparator() + detail + " " + context.getCompileError().details.get(detail);
        }

        return assemblePrompt(context.getDependencyArtifactId(), context.getPreviousVersion(), context.getNewVersion(), context.getTargetPathOld().toString(), context.getTargetPathNew().toString(),
                errorLocation.className(), errorLocation.methodName(), brokenCode.code(), errorLocation.targetMethodParameterClassNames(), errorPrompt, erroneousClass, erroneousScope);
    }

    public String assemblePrompt(String libraryName, String oldVersion, String newVersion, String pathToOldLibraryJar, String pathToNewLibraryJar, String brokenClassName,
                                 String brokenMethodName, String brokenCode, String[] parameterTypeNames, String error, String erroneousClass, String erroneousScope) {
        JarDiffUtil jarDiffUtil;
        if (brokenClassName != null && brokenClassName.startsWith("java.")) {
            jarDiffUtil = new JarDiffUtil("testFiles/Java_Src/rt.jar", "testFiles/Java_Src/rt.jar");
        } else {
            jarDiffUtil = new JarDiffUtil(pathToOldLibraryJar, pathToNewLibraryJar);
        }


        ClassDiffResult classDiffResult = jarDiffUtil.getJarDiff(brokenClassName, brokenMethodName);


        StringBuilder methodSimilarityString = new StringBuilder();

        for (SimilarityResult result : classDiffResult.similarMethods()) {
            String formattedString = String.format("Similarity between '%s' and '%s': %.4f%n", brokenMethodName, JarDiffUtil.getFullMethodSignature(result.method().getNewMethod().get().toString(), result.method().getReturnType().getNewReturnType(), true), result.similarity());
            methodSimilarityString.append(formattedString).append(System.lineSeparator());
        }

        JApiConstructor constructor;
        JApiMethod conflictingMethod;
        String methodChange = "";
        if (classDiffResult.constructors() != null && classDiffResult.constructors().size() > 0) {
            constructor = inferTargetConstructor(classDiffResult.constructors(), parameterTypeNames);
            System.out.println(constructor);
            methodChange = JarDiffUtil.buildConstructorChangeReport(constructor);

        } else {
            conflictingMethod = inferTargetMethod(classDiffResult.methodsWithSameName(), parameterTypeNames);
            System.out.println(conflictingMethod);
            methodChange = JarDiffUtil.buildMethodChangeReport(conflictingMethod);
        }

        //List<ConflictType> conflictTypes = ConflictType.getConflictTypesFromMethod(conflictingMethod);

        /*for (ConflictType conflictType : conflictTypes) {
            System.out.println(conflictType.name());
        }*/

        StringBuilder assembledPrompt = new StringBuilder();

        assembledPrompt.append(String.format(promptTemplatePrefix, libraryName, oldVersion, newVersion));

        if (!methodChange.isEmpty()) {
            assembledPrompt.append(String.format(promptTemplateMethodDiff, methodChange));
        }

        if (!classDiffResult.classDiff().isEmpty()) {
            assembledPrompt.append(String.format(promptTemplateFullDiff, newVersion, oldVersion, classDiffResult.classDiff()));
        }

        if (!erroneousClass.isEmpty()) {
            //assembledPrompt.append(String.format(promptTemplateErroneousClass,  newVersion, erroneousClass));
        }

        if (!erroneousScope.isEmpty()) {
            assembledPrompt.append(String.format(promptTemplateErroneousScope, erroneousScope));
        }

        if (!brokenCode.isEmpty()) {
            assembledPrompt.append(String.format(promptTemplateCodeLine, newVersion, brokenCode));
        }

        if (!methodSimilarityString.isEmpty()) {
            assembledPrompt.append(String.format(promptTemplateSimilarity, methodSimilarityString));
        }

        if (!error.isEmpty()) {
            assembledPrompt.append(String.format(promptTemplateError, error));
        }

        assembledPrompt.append(promptTemplateSuffix);


        return assembledPrompt.toString();
    }

    public JApiConstructor inferTargetConstructor(List<JApiConstructor> constructors, String[] parameterTypeNames) {
        if (constructors.size() == 1) {
            return constructors.get(0);
        }

        ArrayList<JApiConstructor> candidates = new ArrayList<>(constructors.size());
        candidates.addAll(constructors);


        for (int i = candidates.size() - 1; i >= 0; i--) {
            JApiConstructor constructor = candidates.get(i);
            if (constructor.getParameters().size() != parameterTypeNames.length) {
                candidates.remove(i);
                continue;
            }

            for (int j = 0; j < constructor.getParameters().size(); j++) {
                JApiParameter parameter = constructor.getParameters().get(j);
                if (!parameter.getType().endsWith(parameterTypeNames[j]) && !parameterTypeNames[j].equals("java.lang.Object")) {
                    candidates.remove(i);
                    break;
                }
            }
        }

        if (candidates.isEmpty()) {
            System.err.println("No constructors with the same signature found");
            return null;
        }

        if (candidates.size() == 1) {
            return candidates.get(0);
        }


        return candidates.get(0);
    }

    public JApiMethod inferTargetMethod(List<JApiMethod> methodsWithSameName, String[] parameterTypeNames) {
        if (methodsWithSameName.size() == 1) {
            return methodsWithSameName.get(0);
        }

        ArrayList<JApiMethod> candidates = new ArrayList<>(methodsWithSameName.size());
        candidates.addAll(methodsWithSameName);


        for (int i = candidates.size() - 1; i >= 0; i--) {
            JApiMethod method = candidates.get(i);
            if (method.getParameters().size() != parameterTypeNames.length) {
                candidates.remove(i);
                continue;
            }

            for (int j = 0; j < method.getParameters().size(); j++) {
                JApiParameter parameter = method.getParameters().get(j);
                if (!parameter.getType().endsWith(parameterTypeNames[j]) && !parameterTypeNames[j].equals("java.lang.Object")) {
                    candidates.remove(i);
                    break;
                }
            }
        }

        if (candidates.isEmpty()) {
            System.err.println("No methods with the same signature found");


            return null;
        }

        if (candidates.size() == 1) {
            return candidates.get(0);
        }


        return candidates.get(0);
    }

    public ConflictResolutionResult sendAndPrintCode(String prompt) {
        System.out.println(aiProvider.getModel());
        ConflictResolutionResult result = aiProvider.sendPromptAndReceiveResponse(prompt, systemContext);
        System.out.println(result);
        return result;
    }

    public String readBrokenClass(String className, String directory, String fileName) {
        try {
            return Files.readAllLines(Path.of(directory + "/" + fileName + "_" + className)).toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    public String readBrokenEnclosingScope(String className, String directory, String fileName, int lineNumber) {
        Pattern methodDeclarationPattern = Pattern.compile("(public|protected|private|static|\\s) +[\\w\\<\\>\\[\\]]+\\s+(\\w+) *\\([^\\)]*\\) *(\\{?|[^;])");
        try {
            List<String> lines = Files.readAllLines(Path.of(directory + "/" + fileName + "_" + className));

            int start = 0;
            for (int i = lineNumber - 1; i >= 0; i--) {
                Matcher declarationMatcher = methodDeclarationPattern.matcher(lines.get(i));

                if (declarationMatcher.find()) {
                    start = i;
                    break;
                }
            }

            int openCurlyBraces = 0;
            int closedCurlyBraces = 0;

            int end = 0;

            outerloop:
            for (int i = start; i < lines.size(); i++) {
                for (int j = 0; j < lines.get(i).length(); j++) {
                    char c = lines.get(i).charAt(j);
                    if (c == '{') {
                        openCurlyBraces++;
                    } else if (c == '}') {
                        closedCurlyBraces++;
                    }

                    if (openCurlyBraces == closedCurlyBraces && openCurlyBraces != 0) {
                        end = i;
                        break outerloop;
                    }
                }
            }

            String scope = "";

            for (int i = start; i <= end; i++) {
                scope = scope + lines.get(i) + System.lineSeparator();
            }

            return scope;


        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public boolean errorIsFixableBySolver(LogParser.CompileError compileError, BrokenCode brokenCode, ErrorLocation errorLocation) {
        return true;
    }

    @Override
    public boolean errorIsTargetedBySolver(LogParser.CompileError compileError, BrokenCode brokenCode, ErrorLocation errorLocation) {
        return true;
    }

    @Override
    public ProposedChange solveConflict(LogParser.CompileError compileError, BrokenCode brokenCode, ErrorLocation errorLocation) {
        ConflictResolutionResult result = null;

        String llmResponseFileName = context.getTargetDirectoryLLMResponses() + "/" + context.getStrippedFileName() + "_" + context.getCompileError().line + "_" + errorLocation.className() + "_" + context.getActiveProvider() + ".txt";

        if (!usePromptCaching || !Files.exists(Path.of(llmResponseFileName))) {
            String prompt = buildPrompt(brokenCode, errorLocation);
            System.out.println(prompt);

            try {
                Files.write(Path.of(context.getTargetDirectoryPrompts() + "/" + context.getStrippedFileName() + "_" + context.getCompileError().line + "_" + errorLocation.className() + ".txt"), prompt.getBytes());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }


            while (result == null) {
                try {
                    result = sendAndPrintCode(prompt);
                } catch (AIProviderException e) {
                    e.printStackTrace();
                }
            }


            try {
                FileOutputStream fileOutputStream = new FileOutputStream(llmResponseFileName);
                ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream);
                objectOutputStream.writeObject(result);
                objectOutputStream.flush();
                objectOutputStream.close();
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        } else {
            try {
                System.out.println("Loading LLM response from stored responses");
                FileInputStream fileInputStream = new FileInputStream(llmResponseFileName);
                ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream);
                result = (ConflictResolutionResult) objectInputStream.readObject();
                objectInputStream.close();
            } catch (FileNotFoundException | ClassNotFoundException e) {
                throw new RuntimeException(e);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return new ProposedChange(context.getStrippedClassName(), result.code(), context.getCompileError().file, brokenCode.start(), brokenCode.end());
    }
}
