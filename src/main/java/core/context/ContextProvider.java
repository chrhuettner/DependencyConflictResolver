package core.context;

import core.*;

import java.util.ArrayList;
import java.util.List;

import static core.BumpRunner.extractClassIfNotCached;

public abstract class ContextProvider {
    protected Context context;

    public ContextProvider(Context context) {
        this.context = context;
    }

    public Context getContext() {
        return context;
    }

    public static List<ContextProvider> getContextProviders(Context context) {
        List<ContextProvider> contextProviders = new ArrayList<>();
        contextProviders.add(new CannotFindSymbolProvider(context));
        contextProviders.add(new ConstructorTypeProvider(context));
        contextProviders.add(new DeprecationProvider(context));
        contextProviders.add(new MethodChainProvider(context));
        contextProviders.add(new SuperProvider(context));
        contextProviders.add(new TypeCastProvider(context));
        contextProviders.add(new ImportProvider(context));

        return contextProviders;
    }

    public abstract boolean errorIsTargetedByProvider(LogParser.CompileError compileError, BrokenCode brokenCode);

    public abstract ErrorLocation getErrorLocation(LogParser.CompileError compileError, BrokenCode brokenCode);
}
