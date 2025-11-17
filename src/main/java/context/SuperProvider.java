package context;


import dto.BrokenCode;
import dto.ErrorLocation;
import docker.ContainerUtil;

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
        String parent = ContainerUtil.readParent(context.getStrippedClassName(), context.getTargetDirectoryClasses(), context.getStrippedFileName(), context.getIteration());
        return new ErrorLocation(parent, parent, null);
    }
}
