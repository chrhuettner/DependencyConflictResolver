package core.context;

import core.BrokenCode;
import core.Context;
import core.ErrorLocation;
import core.LogParser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TypeCastProvider extends CompileErrorRegexProvider {
    protected TypeCastProvider(Context context) {
        super(context, Pattern.compile("incompatible types: (\\S*) cannot be converted to (\\S*)"));
    }

    @Override
    public ErrorLocation getErrorLocation(LogParser.CompileError compileError, BrokenCode brokenCode) {
        Matcher matcher = findMatch(compileError, brokenCode);
        return new ErrorLocation(null, null, new String[]{matcher.group(2)});
    }
}
