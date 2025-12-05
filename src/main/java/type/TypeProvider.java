package type;

import context.Context;
import context.ContextUtil;
import context.LogParser;
import context.SourceCodeAnalyzer;
import core.JarDiffUtil;
import docker.ContainerUtil;
import dto.BrokenCode;
import dto.ErrorLocation;
import dto.FileSearchResult;
import japicmp.model.*;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static type.ConflictType.*;

public class TypeProvider {

    public static List<ConflictType> getConflictTypes(BrokenCode brokenCode, ErrorLocation errorLocation, LogParser.CompileError compileError, Context context) {
        List<ConflictType> conflictTypes = new ArrayList<>();

        if (compileError.message.startsWith("method does not override or implement a method from a supertype")) {
            conflictTypes.add(METHOD_NO_LONGER_OVERRIDES);
            return conflictTypes;
        }

        if (brokenCode.code().startsWith("import") || compileError.message.startsWith("cannot find symbol")) {
            if (errorLocation.className() != null) {
                if (errorLocation.methodName() != null) {
                    conflictTypes.add(METHOD_REMOVED);
                    return conflictTypes;
                }
                JarDiffUtil jarDiffUtil = JarDiffUtil.getInstance(context.getTargetPathOld().toString(), context.getTargetPathNew().toString());

                String alternative = jarDiffUtil.getAlternativeClassImport(errorLocation.className());
                if (alternative == null) {
                    conflictTypes.add(CLASS_REMOVED);
                    return conflictTypes;
                } else {
                    String simpleAlternativeName = alternative.substring(alternative.lastIndexOf('.') + 1);
                    if (simpleAlternativeName.equals(errorLocation.className())) {
                        conflictTypes.add(CLASS_MOVED);
                    } else {
                        conflictTypes.add(CLASS_REMOVED);
                    }

                    return conflictTypes;
                }
            }
        }

        Pattern pattern = Pattern.compile("Class (\\S*) should be declared as final");
        Matcher matcher = pattern.matcher(compileError.message);
        if (matcher.find()) {
            conflictTypes.add(PARENT_CLASS_SEALED);
            return conflictTypes;
        }

        if (errorLocation.className() != null && errorLocation.methodName() != null) {
            JarDiffUtil jarDiffUtil = JarDiffUtil.getInstance(context.getTargetPathOld().toString(), context.getTargetPathNew().toString());
            String srcDir = context.getOutputDirSrcFiles().toPath().resolve(Path.of(context.getDependencyArtifactId() + "_" + context.getStrippedFileName())).toString();
            SourceCodeAnalyzer sourceCodeAnalyzer = SourceCodeAnalyzer.getInstance(srcDir);
            JApiClass jApiClass = jarDiffUtil.getClassByName(errorLocation.className());

            if (jApiClass == null) {
                FileSearchResult searchResult = sourceCodeAnalyzer.getDependencyFileContainingClass(errorLocation.className());
                if (searchResult == null) {
                    conflictTypes.add(UNKNOWN_CLASS);
                    return conflictTypes;
                } else {
                    JarDiffUtil innerDiff = JarDiffUtil.getInstance(searchResult.file().toString(), searchResult.file().toString());
                    jApiClass = innerDiff.getClassByName(errorLocation.className());
                    if (jApiClass == null) {
                        conflictTypes.add(UNKNOWN_CLASS);
                        return conflictTypes;
                    }
                }

            }

            JApiMethod jApiMethod = jarDiffUtil.getMethodOfClass(jApiClass, errorLocation.methodName(), errorLocation.targetMethodParameterClassNames());

            if (jApiMethod == null) {

                if (jApiMethod == null) {
                    conflictTypes.add(UNKNOWN_METHOD);
                    return conflictTypes;
                }
            }


            for (JApiCompatibilityChange compatibilityChange : jApiClass.getCompatibilityChanges()) {
                switch (compatibilityChange.getType()) {
                    case JApiCompatibilityChangeType.CLASS_REMOVED:
                        conflictTypes.add(CLASS_REMOVED);
                        break;
                    case JApiCompatibilityChangeType.CLASS_LESS_ACCESSIBLE:
                    case JApiCompatibilityChangeType.CLASS_NO_LONGER_PUBLIC:
                        conflictTypes.add(CLASS_VISIBILITY_CHANGED);
                        break;
                    case JApiCompatibilityChangeType.SUPERCLASS_ADDED:
                    case JApiCompatibilityChangeType.SUPERCLASS_REMOVED:
                        conflictTypes.add(PARENT_CLASS_CHANGED);
                        break;
                    case JApiCompatibilityChangeType.INTERFACE_ADDED:
                    case JApiCompatibilityChangeType.INTERFACE_REMOVED:
                        conflictTypes.add(PARENT_INTERFACE_CHANGED);
                        break;

                }
            }


            for (JApiCompatibilityChange compatibilityChange : jApiMethod.getCompatibilityChanges()) {
                switch (compatibilityChange.getType()) {

                    case JApiCompatibilityChangeType.METHOD_REMOVED:
                        conflictTypes.add(METHOD_REMOVED);
                        break;
                    case JApiCompatibilityChangeType.METHOD_LESS_ACCESSIBLE:
                    case JApiCompatibilityChangeType.METHOD_LESS_ACCESSIBLE_THAN_IN_SUPERCLASS:
                        conflictTypes.add(METHOD_VISIBILITY_CHANGED);
                        break;
                    case JApiCompatibilityChangeType.METHOD_NO_LONGER_STATIC:
                        conflictTypes.add(METHOD_CHANGED_FROM_STATIC_TO_NON_STATIC);
                        break;
                    case JApiCompatibilityChangeType.METHOD_NOW_STATIC:
                        conflictTypes.add(METHOD_CHANGED_FROM_NON_STATIC_TO_STATIC);
                        break;
                    case JApiCompatibilityChangeType.ANNOTATION_ADDED:
                    case JApiCompatibilityChangeType.ANNOTATION_MODIFIED:
                    case JApiCompatibilityChangeType.ANNOTATION_REMOVED:
                    case JApiCompatibilityChangeType.ANNOTATION_DEPRECATED_ADDED:
                        conflictTypes.add(METHOD_ANNOTATION_CHANGED);
                        break;
                    case JApiCompatibilityChangeType.METHOD_RETURN_TYPE_CHANGED:
                        if (jApiMethod.getReturnType().getNewReturnType().equals(Void.TYPE.getName())) {
                            conflictTypes.add(METHOD_CHANGED_RETURN_TYPE_TO_NON_VOID);
                        } else {
                            conflictTypes.add(METHOD_CHANGED_RETURN_TYPE_TO_VOID);
                        }
                        break;
                    case JApiCompatibilityChangeType.METHOD_NOW_ABSTRACT:
                        conflictTypes.add(METHOD_CHANGED_TO_ABSTRACT);
                        break;
                    case JApiCompatibilityChangeType.METHOD_NOW_THROWS_CHECKED_EXCEPTION:
                    case JApiCompatibilityChangeType.METHOD_NO_LONGER_THROWS_CHECKED_EXCEPTION:
                        conflictTypes.add(METHOD_EXCEPTIONS_CHANGED);
                        break;
                }
            }

            boolean paramsChanged = false;
            boolean paramsRemoved = false;
            boolean paramsAdded = false;
            for (JApiParameter jApiParameter : jApiMethod.getParameters()) {
                switch (jApiParameter.getChangeStatus()) {
                    case JApiChangeStatus.MODIFIED:
                        paramsChanged = true;
                        break;
                    case JApiChangeStatus.REMOVED:
                        paramsRemoved = true;
                        break;
                    case JApiChangeStatus.NEW:
                        paramsAdded = true;
                        break;
                }
            }
            for (int i = 0; i < jApiMethod.getParameters().size(); i++) {
                if (!SourceCodeAnalyzer.parameterIsCompatibleWithType(errorLocation.targetMethodParameterClassNames()[i], jApiMethod.getParameters().get(i).getType())) {
                    paramsChanged = true;
                    break;
                }
            }

            if (paramsChanged) {
                conflictTypes.add(METHOD_PARAMETER_TYPES_CHANGED);
            }
            if (paramsRemoved) {
                conflictTypes.add(METHOD_PARAMETERS_REMOVED);
            }
            if (paramsAdded) {
                conflictTypes.add(METHOD_PARAMETERS_ADDED);
            }


        } else {
            conflictTypes.add(UNKNOWN);
            return conflictTypes;
        }

        return conflictTypes;
    }
}
