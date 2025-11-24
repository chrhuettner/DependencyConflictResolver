package context;


import dto.BrokenCode;
import dto.ErrorLocation;
import docker.ContainerUtil;

import java.util.regex.Pattern;

public class SuperProvider extends BrokenCodeRegexProvider {

    protected SuperProvider(Context context) {
        super(context, Pattern.compile("super\\("));
    }

    @Override
    public ErrorLocation getErrorLocation(LogParser.CompileError compileError, BrokenCode brokenCode) {
        String parent = ContainerUtil.readParent(context.getStrippedClassName(), context.getTargetDirectoryClasses(), context.getStrippedFileName(), context.getIteration());
        return new ErrorLocation(parent, parent, null);
    }
}
