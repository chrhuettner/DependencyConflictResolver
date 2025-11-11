package core.context;

import core.BrokenCode;
import core.Context;
import core.ErrorLocation;
import core.LogParser;

import static core.BumpRunner.primitiveClassNameToWrapperName;

public class ImportProvider extends ContextProvider {
    public ImportProvider(Context context) {
        super(context);
    }

    @Override
    public boolean errorIsTargetedByProvider(LogParser.CompileError compileError, BrokenCode brokenCode) {
        return brokenCode.code().startsWith("import");
    }

    @Override
    public ErrorLocation getErrorLocation(LogParser.CompileError compileError, BrokenCode brokenCode) {
        return new ErrorLocation(getClassNameFromImport(brokenCode.code()), null, null);
    }

    private String getClassNameFromImport(String importLine){
        String targetClass = importLine.substring(importLine.indexOf(" "), importLine.indexOf(";")).trim();
        if (targetClass.contains(".")) {
            targetClass = targetClass.substring(targetClass.lastIndexOf(".") + 1);
        }
        return targetClass;
    }

}
