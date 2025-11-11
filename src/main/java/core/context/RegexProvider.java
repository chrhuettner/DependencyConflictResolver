package core.context;

import core.BrokenCode;
import core.Context;
import core.LogParser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class RegexProvider extends ContextProvider {
    protected final Pattern pattern;

    protected RegexProvider(Context context, Pattern pattern) {
        super(context);
        this.pattern = pattern;
    }

    public Pattern getPattern() {
        return pattern;
    }

    @Override
    public boolean errorIsTargetedByProvider(LogParser.CompileError compileError, BrokenCode brokenCode) {
        return constructMatcher(compileError, brokenCode).find();
    }

    public Matcher findMatch(LogParser.CompileError compileError, BrokenCode brokenCode) {
        Matcher matcher = constructMatcher(compileError, brokenCode);

        if(!matcher.find()){
            throw new RuntimeException("Invalid call to find Match!");
        }

        return matcher;
    }

    public abstract Matcher constructMatcher(LogParser.CompileError compileError, BrokenCode brokenCode);
}
