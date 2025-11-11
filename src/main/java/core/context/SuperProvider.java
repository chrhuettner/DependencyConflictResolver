package core.context;


import core.BrokenCode;
import core.Context;
import core.ErrorLocation;
import core.LogParser;

import static core.BumpRunner.readParent;

public class SuperProvider extends ContextProvider {

    public SuperProvider(Context context) {
        super(context);
    }

    @Override
    public boolean errorIsTargetedByProvider(LogParser.CompileError compileError, BrokenCode brokenCode) {
        return brokenCode.code().trim().startsWith("super");
    }

    @Override
    public ErrorLocation getErrorLocation(LogParser.CompileError compileError, BrokenCode brokenCode) {
        String parent = readParent(context.getStrippedClassName(), context.getTargetDirectoryClasses(), context.getStrippedFileName());
        return new ErrorLocation(parent, parent, null);
    }
}
