package solver.deterministic;

import context.*;
import core.JarDiffUtil;
import docker.ContainerUtil;
import dto.BrokenCode;
import dto.ErrorLocation;
import dto.ProposedChange;
import japicmp.model.JApiChangeStatus;
import japicmp.model.JApiClass;
import japicmp.model.JApiMethod;
import japicmp.model.JApiParameter;
import javassist.CtMethod;
import javassist.NotFoundException;
import solver.ContextAwareSolver;
import type.ConflictType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static japicmp.model.JApiChangeStatus.*;

public class MethodParameterSolver extends ContextAwareSolver {
    public MethodParameterSolver(Context context) {
        super(context);
    }

    @Override
    public boolean errorIsFixableBySolver(LogParser.CompileError compileError, BrokenCode brokenCode, ErrorLocation errorLocation) {
        JarDiffUtil jarDiffUtil = JarDiffUtil.getInstance(context.getTargetPathOld().toString(), context.getTargetPathNew().toString());

        JApiClass jApiClass = jarDiffUtil.getClassByName(errorLocation.className());
        JApiMethod jApiMethod = jarDiffUtil.getMethodOfClass(jApiClass, errorLocation.methodName(), errorLocation.targetMethodParameterClassNames());

        List<Integer> parametersToRemove = new ArrayList<>();
        HashMap<Integer, String> parametersToAdd = new HashMap<>();
        HashMap<Integer, String[]> parametersToCast = new HashMap<>();

        for (int i = 0; i < jApiMethod.getParameters().size(); i++) {
            JApiParameter jApiParameter = jApiMethod.getParameters().get(i);

            if (jApiParameter.getChangeStatus() == REMOVED) {
                parametersToRemove.add(i);
            } else if (jApiParameter.getChangeStatus() == NEW) {
                parametersToAdd.put(i, jApiParameter.getType());
            } else if (jApiParameter.getChangeStatus() == MODIFIED) {
                CtMethod oldMethod = jApiMethod.getOldMethod().orElse(null);
                if (oldMethod != null) {
                    try {
                        parametersToCast.put(i, new String[]{oldMethod.getParameterTypes()[i].getName(), jApiParameter.getType()});
                    } catch (NotFoundException e) {
                        throw new RuntimeException(e);
                    }

                }
            } else {
                if(errorLocation.targetMethodParameterClassNames().length > i) {
                    if (!SourceCodeAnalyzer.parameterIsCompatibleWithType(errorLocation.targetMethodParameterClassNames()[i], jApiParameter.getType())) {
                        parametersToCast.put(i, new String[]{errorLocation.targetMethodParameterClassNames()[i], jApiParameter.getType()});
                    }
                }
            }

        }

        String methodChain = brokenCode.code().trim();
        int methodInvocationStart = methodChain.indexOf(errorLocation.methodName());
        if (methodInvocationStart == -1) {
            throw new RuntimeException(errorLocation.methodName() + " not found in " + brokenCode.code());
        }
        int methodInvocationEnd = ContextUtil.getClosingBraceIndex(methodChain, methodInvocationStart);
        if (methodInvocationEnd == -1) {
            throw new RuntimeException(errorLocation.methodName() + " has no closing brace in " + brokenCode.code());
        }

        String methodInvocation = methodChain.substring(methodInvocationStart + errorLocation.methodName().length() + 1, methodInvocationEnd);
        List<Integer> separationIndices = ContextUtil.getOuterParameterSeparators(methodInvocation, 0);
        String[] parameters = new String[separationIndices.size() + 1];
        int lastIndex = 0;
        for (int j = 0; j < separationIndices.size(); j++) {
            parameters[j] = methodInvocation.substring(lastIndex, separationIndices.get(j));
            lastIndex = separationIndices.get(j) + 1;
        }
        parameters[separationIndices.size()] = methodInvocation.substring(lastIndex);

       for(int key : parametersToCast.keySet()) {
           String[] castFromTo = parametersToCast.get(key);
           if(!ContextUtil.parameterIsPrimitiveNumber(castFromTo[0]) && ContextUtil.parameterIsPrimitiveNumber(castFromTo[1])){
               return false;
           }
       }

       //TODO: ADD OTHERS



        return false;
    }


    @Override
    public boolean errorIsTargetedBySolver(LogParser.CompileError compileError, BrokenCode brokenCode, ErrorLocation errorLocation, List<ConflictType> conflictTypes) {
        return conflictTypes.contains(ConflictType.METHOD_PARAMETERS_ADDED) || conflictTypes.contains(ConflictType.METHOD_PARAMETERS_REMOVED) || conflictTypes.contains(ConflictType.METHOD_PARAMETER_TYPES_CHANGED);
    }

    @Override
    public ProposedChange solveConflict(LogParser.CompileError compileError, BrokenCode brokenCode, ErrorLocation errorLocation) {
        return null;
    }
}
