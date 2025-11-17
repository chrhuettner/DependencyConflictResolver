package context;

import dto.BrokenCode;
import dto.ErrorLocation;

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
        String parameterTypeNames = matcher.group(2);
        String targetClass = matcher.group(3);
        return new ErrorLocation(targetClass, targetMethod, parameterTypeNames.split(","));
    }
}
