package core.context;

import core.BrokenCode;
import core.Context;
import core.ErrorLocation;
import core.LogParser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DeprecationProvider extends CompileErrorRegexProvider{

    protected DeprecationProvider(Context context) {
        super(context, Pattern.compile("(\\w+)\\(([\\w|\\.]*)\\) in ([\\w|\\.]*) has been deprecated"));
    }

    @Override
    public ErrorLocation getErrorLocation(LogParser.CompileError compileError, BrokenCode brokenCode) {
        Matcher matcher = findMatch(compileError, brokenCode);
        String targetMethod = matcher.group(1);
        String targetClass = matcher.group(3);
        return new ErrorLocation(targetClass, targetMethod, null);
    }
}
