package context;

import dto.BrokenCode;
import dto.ErrorLocation;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ImportProvider extends BrokenCodeRegexProvider {


    protected ImportProvider(Context context) {
        super(context, Pattern.compile("import\\s+(?:static\\s+)?\\w+(?:\\.(\\w+))*;"));
    }

    @Override
    public ErrorLocation getErrorLocation(LogParser.CompileError compileError, BrokenCode brokenCode) {
        Matcher matcher = findMatch(compileError, brokenCode);
        String targetClass = matcher.group(1);
        return new ErrorLocation(targetClass, null, null);
    }


}
