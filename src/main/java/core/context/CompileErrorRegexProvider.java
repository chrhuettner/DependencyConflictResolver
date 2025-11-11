package core.context;

import core.BrokenCode;
import core.Context;
import core.ErrorLocation;
import core.LogParser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class CompileErrorRegexProvider extends RegexProvider {

    protected CompileErrorRegexProvider(Context context, Pattern pattern) {
        super(context, pattern);
    }

    @Override
    public Matcher constructMatcher(LogParser.CompileError compileError, BrokenCode brokenCode) {
        return pattern.matcher(compileError.message);
    }

}
