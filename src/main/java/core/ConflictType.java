package core;

import japicmp.model.JApiCompatibilityChange;
import japicmp.model.JApiCompatibilityChangeType;
import japicmp.model.JApiMethod;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;

import java.util.ArrayList;
import java.util.List;

public enum ConflictType {
    METHOD_REMOVED, METHOD_VISIBILITY_CHANGED, METHOD_TO_STATIC, METHOD_TO_NON_STATIC, METHOD_PARAMS_ADDED,
    METHOD_PARAMS_REMOVED, METHOD_PARAM_TYPES_CHANGED, METHOD_DEPRECATED, METHOD_RENAMED, METHOD_RETURN_TO_VOID,
    METHOD_RETURN_TO_NON_VOID, METHOD_BEHAVIOR_CHANGED, STATIC_METHOD_MOVED, NON_STATIC_METHOD_MOVED, METHOD_TO_ABSTRACT, NONE;

    public static List<ConflictType> getConflictTypesFromMethod(JApiMethod method) {
        List<JApiCompatibilityChange> changes = method.getCompatibilityChanges();
        List<ConflictType> conflictTypes = new ArrayList<ConflictType>();

        if (method.getOldMethod().isPresent() && method.getNewMethod().isPresent()) {
            conflictTypes.addAll(getMethodConflictTypesFromDiff(method.getOldMethod().get(), method.getNewMethod().get()));
        }
        for (JApiCompatibilityChange change : changes) {
            conflictTypes.add(getConflictTypeFromCompatibilityChange(change.getType()));
        }

        return conflictTypes;
    }

    public static ConflictType getConflictTypeFromCompatibilityChange(JApiCompatibilityChangeType type) {
        return switch (type) {
            case METHOD_REMOVED -> ConflictType.METHOD_REMOVED;
            case METHOD_LESS_ACCESSIBLE, METHOD_ADDED_TO_INTERFACE, METHOD_ADDED_TO_PUBLIC_CLASS -> ConflictType.METHOD_VISIBILITY_CHANGED;
            case METHOD_NOW_STATIC -> METHOD_TO_STATIC;
            case METHOD_NO_LONGER_STATIC -> ConflictType.METHOD_TO_NON_STATIC;
            case ANNOTATION_DEPRECATED_ADDED ->  METHOD_DEPRECATED;
            default -> {
                System.out.println(type.name());
                yield null;
            }
        };
    }

    private static List<ConflictType> getMethodConflictTypesFromDiff(CtMethod oldMethod, CtMethod newMethod) {
        try {
            List<ConflictType> conflictTypes = new ArrayList<ConflictType>();
            CtClass[] oldTypes = oldMethod.getParameterTypes();
            CtClass[] newTypes = newMethod.getParameterTypes();

            if (oldTypes.length > newTypes.length) {
                conflictTypes.add(METHOD_PARAMS_REMOVED);
            }else if (oldTypes.length < newTypes.length) {
                conflictTypes.add(METHOD_PARAMS_ADDED);
            }

            for (int i = 0; i < Math.min(oldTypes.length, newTypes.length); i++) {
                if(!oldTypes[i].equals(newTypes[i])) {
                    conflictTypes.add(METHOD_PARAM_TYPES_CHANGED);
                    break;
                }
            }

            if(!oldMethod.getReturnType().equals(newMethod.getReturnType())) {
                if(newMethod.getReturnType().equals(CtClass.voidType)) {
                    conflictTypes.add(METHOD_RETURN_TO_VOID);
                }else{
                    conflictTypes.add(METHOD_RETURN_TO_NON_VOID);
                }
            }

            return conflictTypes;
        } catch (NotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}
