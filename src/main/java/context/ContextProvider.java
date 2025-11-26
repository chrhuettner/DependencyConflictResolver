package context;

import dto.BrokenCode;
import dto.ErrorLocation;

import java.util.ArrayList;
import java.util.List;


public interface ContextProvider {
    boolean errorIsTargetedByProvider(LogParser.CompileError compileError, BrokenCode brokenCode);

    ErrorLocation getErrorLocation(LogParser.CompileError compileError, BrokenCode brokenCode);

    static List<ContextProvider> getContextProviders(Context context) {
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
}
