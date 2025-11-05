package core;

import ch.uzh.ifi.seal.changedistiller.ChangeDistiller;
import ch.uzh.ifi.seal.changedistiller.distilling.FileDistiller;
import ch.uzh.ifi.seal.changedistiller.model.entities.SourceCodeChange;
import japicmp.model.JApiMethod;
import japicmp.model.JApiParameter;
import provider.AIProvider;
import provider.ChatGPTProvider;
import provider.ClaudeProvider;
import provider.OllamaProvider;

import java.io.File;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Main {

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
    static String promptTemplateMethodDiff  = """
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


    //static WordSimilarityModel similarityModel;


    public static void main(String[] args) {
        //similarityModel = new WordSimilarityModel();
        AIProvider chatgptProvider = new ChatGPTProvider();
        AIProvider claudeProvider = new ClaudeProvider();
        AIProvider codeLama7bProvider = new OllamaProvider("codellama:7b");
        AIProvider codeLama13bProvider = new OllamaProvider("codellama:13b");
        AIProvider codeGemma7bProvider = new OllamaProvider("codegemma:7b");
        AIProvider deepseekCoder6b7Provider = new OllamaProvider("deepseek-coder:6.7b");
        AIProvider starCoder2_7bProvider = new OllamaProvider("starcoder2:7b");
        AIProvider deepSeekR1b5 = new OllamaProvider("deepseek-r1:1.5b");
        AIProvider qwen3_8b = new OllamaProvider("qwen3:8b");
        AIProvider qwen3_14b = new OllamaProvider("qwen3:14b");
        AIProvider starCoder2_15bProvider = new OllamaProvider("starcoder2:15b");
        AIProvider nomicEmbedTextProvider = new OllamaProvider("nomic-embed-text");
        AIProvider cogito8bProvider = new OllamaProvider("cogito:8b");
        AIProvider deepseekR1_7b = new OllamaProvider("deepseek-r1:7b");
        AIProvider gptOss20b = new OllamaProvider("gpt-oss:20b");

        List<AIProvider> providers = new ArrayList<>();

        //providers.add(chatgptProvider);
        //providers.add(claudeProvider);
        //providers.add(codeLama7bProvider);
        //providers.add(codeLama13bProvider);
        //providers.add(codeGemma7bProvider);   //Unpromising
        //providers.add(deepseekCoder6b7Provider);    //Unpromising
        //providers.add(starCoder2_7bProvider);       //Unpromising
        //providers.add(deepSeekR1b5);                //Unpromising
        providers.add(qwen3_8b);
        providers.add(qwen3_14b);
        //providers.add(starCoder2_15bProvider);   //Unpromising
        //providers.add(cogito8bProvider);
        //providers.add(deepseekR1_7b);
        //providers.add(gptOss20b);

        String oldVersion = "5.6.15.Final";
        String newVersion = "7.0.4.Final";

        String brokenCodeHibernate = """
                session.save(user);
                """;

        String hibernateLeft = "testFiles/hibernate-core-5.6.15.Final.jar";
        String hibernateRight = "testFiles/hibernate-core-7.0.4.Final.jar";

        String[] hibernateParameterTypeNames = new String[]{"java.lang.Object"};


        String hibernateMethodName = "save";
        String hibernateClassName = "org.hibernate.Session";
        String hibernateName = "Hibernate";

        String gsonOldVersion = "2.8.0";
        String gsonNewVersion = "2.10.1";

        String brokenCodeGson = """
                JsonObject obj = parser.parse(jsonStr).getAsJsonObject(); 
                """;

        String gsonLeft = "testFiles/gson-2.8.0.jar";
        String gsonRight = "testFiles/gson-2.10.1.jar";

        String gsonMethodName = "parse";
        String gsonClassName = "com.google.gson.JsonParser";
        String[] gsonParameterTypeNames = new String[]{"java.lang.String"};
        String gsonName = "Gson";

        String prompt = buildPrompt(hibernateName, oldVersion, newVersion, hibernateLeft, hibernateRight, hibernateClassName, hibernateMethodName, brokenCodeHibernate, hibernateParameterTypeNames, "25:16\n" +
                "java: cannot find symbol\n" +
                "  symbol:   method save(compatibility.User)\n" +
                "  location: variable session of type org.hibernate.Session", "", "");
        //String prompt = buildPrompt(gsonName, gsonOldVersion, gsonNewVersion, gsonLeft, gsonRight, gsonClassName, gsonMethodName, brokenCodeGson, gsonParameterTypeNames);

        System.out.println(prompt);

        //String result = chatgptProvider.sendPromptAndReceiveResponse(prompt, systemContext);

        //System.out.println(result);

        //System.out.println(claudeProvider.sendPromptAndReceiveResponse(prompt, systemContext));
        /*System.out.println("codeGemma7bProvider");
        System.out.println(codeGemma7bProvider.sendPromptAndReceiveResponse(prompt, systemContext));
        System.out.println("deepseekCoder6b7Provider");
        System.out.println(deepseekCoder6b7Provider.sendPromptAndReceiveResponse(prompt, systemContext));
        System.out.println("starCoder2_7bProvider");
        System.out.println(starCoder2_7bProvider.sendPromptAndReceiveResponse(prompt, systemContext));
        System.out.println("codeLama13bProvider");
        System.out.println(codeLama13bProvider.sendPromptAndReceiveResponse(prompt, systemContext));
        System.out.println("codeLama7bProvider");
        System.out.println(codeLama7bProvider.sendPromptAndReceiveResponse(prompt, systemContext));
        System.out.println("deepSeekR1b5");
        System.out.println(deepSeekR1b5.sendPromptAndReceiveResponse(prompt, systemContext));*/
        //System.out.println("qwen3_8b");
        //System.out.println(qwen3_8b.sendPromptAndReceiveResponse(prompt, systemContext));
        //System.out.println("starCoder2_15bProvider");
        //System.out.println(starCoder2_15bProvider.sendPromptAndReceiveResponse(prompt, systemContext));

        //System.out.println("cogito8bProvider");
        //System.out.println(cogito8bProvider.sendPromptAndReceiveResponse(prompt, systemContext).code());


        //System.out.println(getJarDiff("testFiles/gson-2.8.0.jar", "testFiles/gson-2.10.1.jar"));

        for (AIProvider provider : providers) {
            sendAndPrintCode(provider, prompt);
        }
    }

    public static JApiMethod inferTargetMethod(List<JApiMethod> methodsWithSameName, String[] parameterTypeNames) {
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

    public static ConflictResolutionResult sendAndPrintCode(AIProvider aiProvider, String prompt) {
        System.out.println(aiProvider.getModel());
        ConflictResolutionResult result = aiProvider.sendPromptAndReceiveResponse(prompt, systemContext);
        System.out.println(result);
        return result;
    }

    public static String buildPrompt(String libraryName, String oldVersion, String newVersion, String pathToOldLibraryJar, String pathToNewLibraryJar, String brokenClassName,
                                     String brokenMethodName, String brokenCode, String[] parameterTypeNames, String error, String erroneousClass, String erroneousScope) {
        JarDiffUtil jarDiffUtil;
        if(brokenClassName != null && brokenClassName.startsWith("java.")){
            jarDiffUtil = new JarDiffUtil("testFiles/Java_Src/rt.jar", "testFiles/Java_Src/rt.jar");
        }else{
            jarDiffUtil = new JarDiffUtil(pathToOldLibraryJar, pathToNewLibraryJar);
        }


        ClassDiffResult classDiffResult = jarDiffUtil.getJarDiff(brokenClassName, brokenMethodName);



        StringBuilder methodSimilarityString = new StringBuilder();

        for (SimilarityResult result : classDiffResult.similarMethods()) {
            String formattedString = String.format("Similarity between '%s' and '%s': %.4f%n", brokenMethodName, JarDiffUtil.getFullMethodSignature(result.method().getNewMethod().get().toString(), result.method().getReturnType().getNewReturnType(), true), result.similarity());
            methodSimilarityString.append(formattedString).append(System.lineSeparator());
        }

        JApiMethod conflictingMethod = inferTargetMethod(classDiffResult.methodsWithSameName(), parameterTypeNames);

        System.out.println(conflictingMethod);
        List<ConflictType> conflictTypes = ConflictType.getConflictTypesFromMethod(conflictingMethod);

        /*for (ConflictType conflictType : conflictTypes) {
            System.out.println(conflictType.name());
        }*/

        StringBuilder assembledPrompt = new StringBuilder();

        assembledPrompt.append(String.format(promptTemplatePrefix,  libraryName, oldVersion, newVersion));

        String methodChange = JarDiffUtil.buildMethodChangeReport(conflictingMethod);
        if(!methodChange.isEmpty()){
            assembledPrompt.append(String.format(promptTemplateMethodDiff,  methodChange));
        }

        if(!classDiffResult.classDiff().isEmpty()){
            assembledPrompt.append(String.format(promptTemplateFullDiff,  newVersion, oldVersion, classDiffResult.classDiff()));
        }

        if(!erroneousClass.isEmpty()){
            //assembledPrompt.append(String.format(promptTemplateErroneousClass,  newVersion, erroneousClass));
        }

        if(!erroneousScope.isEmpty()){
            assembledPrompt.append(String.format(promptTemplateErroneousScope, erroneousScope));
        }

        if(!brokenCode.isEmpty()){
            assembledPrompt.append(String.format(promptTemplateCodeLine,  newVersion, brokenCode));
        }

        if(!methodSimilarityString.isEmpty()){
            assembledPrompt.append(String.format(promptTemplateSimilarity,  methodSimilarityString));
        }

        if(!error.isEmpty()){
            assembledPrompt.append(String.format(promptTemplateError,  error));
        }

        assembledPrompt.append(promptTemplateSuffix);



        return assembledPrompt.toString();
    }

    /*public static String getJarDiff(String file1, String file2, String fullyQualifiedCallerClassName, String methodName) {
        File left = new File(file1);
        File right = new File(file2);
        Options options = Options.newDefault();
        options.setIgnoreMissingClasses(true);
        JarArchiveComparatorOptions comparatorOptions = JarArchiveComparatorOptions.of(options);

        JarArchiveComparator jarArchiveComparator = new JarArchiveComparator(comparatorOptions);


        JApiCmpArchive test = new JApiCmpArchive(left, "");
        JApiCmpArchive test2 = new JApiCmpArchive(right, "");
        List<JApiClass> jApiClasses = jarArchiveComparator.compare(test, test2);

        StringBuilder changes = new StringBuilder();

        List<JApiMethod> similarMethods = new ArrayList<>();

        for (JApiClass jApiClass : jApiClasses) {
            if (jApiClass.getChangeStatus() != JApiChangeStatus.UNCHANGED) {

                if (!jApiClass.getFullyQualifiedName().equals(fullyQualifiedCallerClassName)) {
                    continue;
                }

                String classCompatibilityChange = "";
                List<JApiCompatibilityChange> classCompatibilityChanges = jApiClass.getCompatibilityChanges();
                for (int i = 0; i < classCompatibilityChanges.size(); i++) {
                    classCompatibilityChange += classCompatibilityChanges.get(i).getType();
                    if (i != classCompatibilityChanges.size() - 1) {
                        classCompatibilityChange += ", ";
                    }
                }


                changes.append("Changed class: ")
                        .append(jApiClass.getFullyQualifiedName())
                        .append(", Status: ")
                        .append(jApiClass.getChangeStatus()).append(", Compatibility change: ");
                if (!classCompatibilityChange.isEmpty()) {
                    changes.append(classCompatibilityChange);
                }

                changes.append(System.lineSeparator());

                changes.append("Start of changed class methods: ").append(System.lineSeparator());

                for (JApiMethod jApiMethod : jApiClass.getMethods()) {

                    if (methodName != null && !jApiMethod.getName().equals(methodName)) {
                        continue;
                    }


                    String compatibilityChange = "";
                    List<JApiCompatibilityChange> compatibilityChanges = jApiMethod.getCompatibilityChanges();
                    for (int i = 0; i < compatibilityChanges.size(); i++) {
                        compatibilityChange += compatibilityChanges.get(i).getType();
                        if (i != compatibilityChanges.size() - 1) {
                            compatibilityChange += ", ";
                        }
                    }

                    if (jApiMethod.getChangeStatus() != JApiChangeStatus.REMOVED) {
                        similarMethods.add(jApiMethod);
                    }


                    changes.append("- ").append(jApiMethod.getName()).append(": ");

                    String parameters = "Parameters: ";
                    List<JApiParameter> parameterList = jApiMethod.getParameters();
                    for (int i = 0; i < parameterList.size(); i++) {
                        parameters += parameterList.get(i).getType();
                        parameters += " (" + parameterList.get(i).getChangeStatus() + ")";
                        if (i != parameterList.size() - 1) {
                            parameters += ", ";
                        }
                    }

                    if (!parameters.equals("Parameters: ")) {
                        changes.append(parameters).append(", ");
                    }

                    boolean oldReturnTypeExists = false;
                    if (!jApiMethod.getReturnType().getOldReturnType().equals(jApiMethod.getReturnType().getNewReturnType())) {
                        if (!jApiMethod.getReturnType().getOldReturnType().equals("n.a.")) {
                            changes.append("Old return type: ").append(jApiMethod.getReturnType().getOldReturnType());
                            oldReturnTypeExists = true;
                        }

                        if (!jApiMethod.getReturnType().getNewReturnType().equals("n.a.")) {
                            if (oldReturnTypeExists) {
                                changes.append(", ");
                            }
                            changes.append("New return type: ").append(jApiMethod.getReturnType().getNewReturnType());
                        }
                    } else {
                        changes.append("Return type: ").append(jApiMethod.getReturnType().getNewReturnType());
                    }

                    if (!compatibilityChange.isEmpty()) {
                        changes.append(", Compatibility change: ").append(compatibilityChange);
                    }
                    // Two line seperators somehow makes the AIs understand it better
                    changes.append(System.lineSeparator()).append(System.lineSeparator());
                }


                changes.append("End of changed class methods.").append(System.lineSeparator());
            }
        }


        if (methodName == null) {
            double[] baseVec = WordSimilarityModel.getEmbedding("save");
            List<SimilarityResult> similarityResults = new ArrayList<>();
            for (JApiMethod method : similarMethods) {
                double[] compareVec = WordSimilarityModel.getEmbedding(method.getName());
                double similarity = WordSimilarityModel.cosineSimilarity(baseVec, compareVec);
                //System.out.printf("Similarity between '%s' and '%s': %.4f%n", "save", word, similarity);
                similarityResults.add(new SimilarityResult(method.getName(), similarity));
            }
            similarityResults.sort((t1, t2) -> {
                if (t1.similarity() == t2.similarity()) {
                    return 0;
                }
                return (t1.similarity() < t2.similarity()) ? 1 : -1;
            });

            for (int i = 0; i < similarityResults.size(); i++) {
                System.out.printf("Similarity between '%s' and '%s': %.4f%n", "save", similarityResults.get(i).word(), similarityResults.get(i).similarity());
            }
        }


        return changes.toString();
    }*/

    public static String getCodeDiff(String file1, String file2) {
        File left = new File(file1);
        File right = new File(file2);

        FileDistiller distiller = ChangeDistiller.createFileDistiller(ChangeDistiller.Language.JAVA);
        try {
            distiller.extractClassifiedSourceCodeChanges(left, right);
        } catch (Exception e) {
        /* An exception most likely indicates a bug in ChangeDistiller. Please file a
           bug report at https://bitbucket.org/sealuzh/tools-changedistiller/issues and
           attach the full stack trace along with the two files that you tried to distill. */
            System.err.println("Warning: error while change distilling. " + e.getMessage());
        }

        List<SourceCodeChange> changes = distiller.getSourceCodeChanges();
        String codeDiff = "";
        if (changes != null) {
            for (SourceCodeChange change : changes) {
                codeDiff += change.getLabel() + " in " + change.getRootEntity() + " " +
                        "from line " + change.getChangedEntity().getStartPosition() + " to " + change.getChangedEntity().getEndPosition() + " " + change.getChangedEntity();

            }
        }
        return codeDiff;
    }
}