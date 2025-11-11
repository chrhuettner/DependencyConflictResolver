package core.context;

import core.BrokenCode;
import core.Context;
import core.ErrorLocation;
import core.LogParser;

import static core.BumpRunner.primitiveClassNameToWrapperName;

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
                targetClass = sym.substring(sym.indexOf("class") + "class".length() + 1);
            } else if (sym.startsWith("method")) {
                targetMethod = compileError.details.get("symbol");
                targetMethod = targetMethod.substring(targetMethod.indexOf(" ") + 1);
                if (targetMethod.indexOf("(") != targetMethod.indexOf(")") - 1) {
                    String parameterString = targetMethod.substring(targetMethod.indexOf("(") + 1, targetMethod.indexOf(")"));
                    String[] parameters = parameterString.split(",");
                    targetMethodParameterClassNames = new String[parameters.length];
                    for (int i = 0; i < parameters.length; i++) {
                        targetMethodParameterClassNames[i] = primitiveClassNameToWrapperName(parameters[i]);
                    }
                }

                targetMethod = targetMethod.substring(0, targetMethod.indexOf("("));

                if (compileError.details.containsKey("location")) {
                    targetClass = compileError.details.get("location");
                    if (targetClass.contains("of type")) {
                        targetClass = targetClass.substring(targetClass.indexOf("of type") + "of type".length() + 1);
                    } else {
                        targetClass = targetClass.substring(targetClass.indexOf("class") + "class".length() + 1);
                    }
                }
            }
        }
        return new ErrorLocation(targetClass, targetMethod, targetMethodParameterClassNames);
    }

}
