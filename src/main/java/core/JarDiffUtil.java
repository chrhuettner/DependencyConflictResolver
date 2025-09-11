package core;


import japicmp.cmp.JApiCmpArchive;
import japicmp.cmp.JarArchiveComparator;
import japicmp.cmp.JarArchiveComparatorOptions;
import japicmp.config.Options;
import japicmp.model.*;
import javassist.CtMethod;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JarDiffUtil {

    private JarArchiveComparator comparator;
    private List<JApiClass> jApiClasses;

    public JarDiffUtil(String file1, String file2) {
        this.comparator = createComparator();
        this.jApiClasses = compareJars(this.comparator, file1, file2);
    }

    public ClassDiffResult getJarDiff(String fullyQualifiedCallerClassName, String methodName) {
        List<JApiMethod> similarMethods = new ArrayList<>();
        List<JApiMethod> methodsWithSameName = new ArrayList<>();

        BuildDiffResult changes = buildChangeReport(jApiClasses, fullyQualifiedCallerClassName, methodName, similarMethods, methodsWithSameName);

        List<SimilarityResult> similarityResults = new ArrayList<>();
        if (!methodsWithSameName.isEmpty()) {
            similarityResults = getSimilarityOfMethods(similarMethods, getFullMethodSignature(methodsWithSameName.get(0).getOldMethod().get(), methodsWithSameName.get(0).getReturnType().getOldReturnType().toString()));
        }

        return new ClassDiffResult(changes.classResult(), methodsWithSameName, similarityResults);
    }

    public List<JApiMethod> getChangedMethods() {
        List<JApiMethod> removedMethods = new ArrayList<>();
        for (JApiClass jApiClass : jApiClasses) {
            if (!jApiClass.getAccessModifier().getNewModifier().orElse(jApiClass.getAccessModifier().getOldModifier().orElse(AccessModifier.PUBLIC)).equals(AccessModifier.PUBLIC) || jApiClass.getChangeStatus() == JApiChangeStatus.REMOVED) {
                continue;
            }
            for (JApiMethod jApiMethod : jApiClass.getMethods()) {
                if (!jApiMethod.getAccessModifier().getNewModifier().orElse(jApiMethod.getAccessModifier().getOldModifier().orElse(AccessModifier.PUBLIC)).equals(AccessModifier.PUBLIC)) {
                    continue;
                }
                if (jApiMethod.getChangeStatus() == JApiChangeStatus.MODIFIED && !jApiMethod.getCompatibilityChanges().isEmpty()) {

                    for (JApiCompatibilityChange jApiCompatibilityChange : jApiMethod.getCompatibilityChanges()) {
                        if (!jApiCompatibilityChange.isBinaryCompatible()) {
                            removedMethods.add(jApiMethod);
                            break;
                        }
                    }

                }
            }
        }
        return removedMethods;
    }

    private static JarArchiveComparator createComparator() {
        Options options = Options.newDefault();
        options.setIgnoreMissingClasses(true);
        JarArchiveComparatorOptions comparatorOptions = JarArchiveComparatorOptions.of(options);
        return new JarArchiveComparator(comparatorOptions);
    }

    private static List<JApiClass> compareJars(JarArchiveComparator comparator, String file1, String file2) {
        JApiCmpArchive left = new JApiCmpArchive(new File(file1), "");
        JApiCmpArchive right = new JApiCmpArchive(new File(file2), "");
        return comparator.compare(left, right);
    }

    private static BuildDiffResult buildChangeReport(List<JApiClass> jApiClasses, String fullyQualifiedCallerClassName, String methodName, List<JApiMethod> similarMethods, List<JApiMethod> methodsWithSameName) {
        StringBuilder classChanges = new StringBuilder();
        //StringBuilder methodChanges = new StringBuilder();

        for (JApiClass jApiClass : jApiClasses) {
            /*if (jApiClass.getChangeStatus() == JApiChangeStatus.UNCHANGED) {
                continue;
            }*/

            if (!jApiClass.getFullyQualifiedName().equals(fullyQualifiedCallerClassName)) {
                continue;
            }

            classChanges.append(buildClassChangeHeader(jApiClass));
            classChanges.append(System.lineSeparator());
            classChanges.append("Class methods: ").append(System.lineSeparator());

            for (JApiMethod jApiMethod : jApiClass.getMethods()) {

                if (jApiMethod.getName().equals(methodName)) {
                    methodsWithSameName.add(jApiMethod);

                    //methodChanges.append(buildMethodChangeReport(jApiMethod));
                    //methodChanges.append(System.lineSeparator()).append(System.lineSeparator());

                } else {
                    //if (jApiMethod.getChangeStatus() == JApiChangeStatus.REMOVED) {
                    //    continue;
                    //}
                    similarMethods.add(jApiMethod);
                }


                classChanges.append(buildMethodChangeReport(jApiMethod));
                classChanges.append(System.lineSeparator()).append(System.lineSeparator());
            }

            //classChanges.append("End of changed class methods.").append(System.lineSeparator());
        }

        return new BuildDiffResult(classChanges.toString());
    }

    private static String buildClassChangeHeader(JApiClass jApiClass) {
        StringBuilder header = new StringBuilder();
        List<JApiCompatibilityChange> classCompatibilityChanges = jApiClass.getCompatibilityChanges();

        String classCompatibilityChange = joinCompatibilityChanges(classCompatibilityChanges);

        header.append("Changed class: ")
                .append(jApiClass.getFullyQualifiedName())
                .append(", Status: ")
                .append(jApiClass.getChangeStatus());


        if (!classCompatibilityChange.isEmpty()) {
            header.append(", Compatibility change: ").append(classCompatibilityChange);
        }

        return header.toString();
    }

    public static String buildMethodChangeReport(JApiMethod jApiMethod) {
        if(jApiMethod == null){
            return "";
        }
        StringBuilder report = new StringBuilder();
        List<JApiCompatibilityChange> compatibilityChanges = jApiMethod.getCompatibilityChanges();

        report.append("- ");

        //String parameters = buildParameterString(jApiMethod.getParameters());
        CtMethod method = jApiMethod.getNewMethod().orElse(jApiMethod.getOldMethod().orElse(null));
        String returnType = jApiMethod.getReturnType().getNewReturnType().toString();
        if (returnType.equals("n.a.")) {
            returnType = jApiMethod.getReturnType().getOldReturnType().toString();
        }
        String parameters = getFullMethodSignature(method, returnType);
        if (!parameters.isEmpty()) {
            report.append(parameters).append(", ");
        }

        report.append(buildReturnTypeChangeString(jApiMethod.getReturnType()));

        String compatibilityChange = joinCompatibilityChanges(compatibilityChanges);
        if (!compatibilityChange.isEmpty()) {
            report.append(", Compatibility change: ").append(compatibilityChange);
        }

        return report.toString();
    }

    private static String joinCompatibilityChanges(List<JApiCompatibilityChange> changes) {
        if (changes == null || changes.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < changes.size(); i++) {
            sb.append(changes.get(i).getType());
            if (i != changes.size() - 1) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }


    private static String buildParameterString(List<JApiParameter> parameters) {
        if (parameters.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder("Parameters: ");
        for (int i = 0; i < parameters.size(); i++) {
            JApiParameter param = parameters.get(i);
            sb.append(param.getType())
                    .append(" (")
                    .append(param.getChangeStatus())
                    .append(")");

            if (i != parameters.size() - 1) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }

    private static String buildReturnTypeChangeString(JApiReturnType returnType) {
        String oldType = returnType.getOldReturnType();
        String newType = returnType.getNewReturnType();

        if (!oldType.equals(newType)) {
            StringBuilder sb = new StringBuilder();
            if (!oldType.equals("n.a.")) {
                sb.append("Old return type: ").append(oldType);
            }
            if (!newType.equals("n.a.")) {
                if (sb.length() > 0) sb.append(", ");
                sb.append("New return type: ").append(newType);
            }
            return sb.toString();
        } else {
            return "Return type: " + newType;
        }
    }

    private static List<SimilarityResult> getSimilarityOfMethods(List<JApiMethod> methods, String baseWord) {
        double[] baseVec = WordSimilarityModel.getEmbedding(baseWord);
        List<SimilarityResult> similarityResults = new ArrayList<>();

        for (JApiMethod method : methods) {
            if (method.getChangeStatus() == JApiChangeStatus.REMOVED) {
                continue;
            }
            double[] compareVec = WordSimilarityModel.getEmbedding(getFullMethodSignature(method.getNewMethod().get(), method.getReturnType().getNewReturnType().toString()));
            double similarity = WordSimilarityModel.cosineSimilarity(baseVec, compareVec);
            similarityResults.add(new SimilarityResult(method, similarity));
        }

        similarityResults.sort((t1, t2) -> Double.compare(t2.similarity(), t1.similarity()));

       /* for (SimilarityResult result : similarityResults) {
            System.out.printf("Similarity between '%s' and '%s': %.4f%n", baseWord, getFullMethodSignature(result.method().getNewMethod().get(), result.method().getReturnType().getNewReturnType().toString()), result.similarity());
        }*/

        return similarityResults;
    }

    static String getFullMethodSignature(CtMethod method, String returnType) {
        String result = method.toString();

        String resultPrefix = result.substring(result.indexOf("[") + 1, result.indexOf("("));

        String[] keywords = {
                "public", "protected", "private",
                "static", "final", "abstract",
                "synchronized", "native", "strictfp",
                "default", "volatile", "transient"
        };
        int lastKeywordIndex = -1;

        for (String kw : keywords) {
            int idx = resultPrefix.lastIndexOf(kw);
            if (idx > lastKeywordIndex) {
                lastKeywordIndex = idx;
            }
        }

        if (lastKeywordIndex != -1) {
            int insertPos = resultPrefix.indexOf(" ", lastKeywordIndex);
            if (insertPos == -1) insertPos = resultPrefix.length();
            resultPrefix = resultPrefix.substring(0, insertPos) + " " + returnType + resultPrefix.substring(insertPos);
        } else {
            int firstSpace = resultPrefix.indexOf(" ");
            if (firstSpace == -1) firstSpace = resultPrefix.length();
            resultPrefix = resultPrefix.substring(0, firstSpace) + " " + returnType + resultPrefix.substring(firstSpace);
        }


        String parametersAsString = result.substring(result.indexOf("(") + 1, result.indexOf(")"));
        StringBuilder parameterResult = new StringBuilder("(");
        if (!parametersAsString.isEmpty()) {
            String[] parameters = parametersAsString.split(";");
            for (int i = 0; i < parameters.length; i++) {
                if (i != 0) {
                    parameterResult.append(", ");
                }
                parameters[i] = parameters[i].substring(1);
                parameters[i] = parameters[i].replaceAll("/", ".");

                parameterResult.append(parameters[i]);
            }
        }
        parameterResult.append(")");


        return resultPrefix + parameterResult;
    }
}

