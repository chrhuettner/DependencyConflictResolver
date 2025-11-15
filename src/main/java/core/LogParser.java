package core;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class LogParser {

    // compile errors
    private static final Pattern COMPILE_ERROR = Pattern.compile(
            "^\\[ERROR\\] (.+\\.java):\\[(\\d+),(\\d+)\\] (.+)");

    private static final Pattern ALTERNATIVE_COMPILE_ERROR = Pattern.compile(
            "^\\[ERROR\\] (.+\\.java):(\\d+):(\\d+):\\ (.+)");

    // Warning errors
    private static final Pattern WARNING_ERROR = Pattern.compile(
            "^\\[WARNING\\] (.+\\.java):\\[(\\d+),(\\d+)\\] (.+)");

    // runtime exception line
    private static final Pattern EXCEPTION_LINE = Pattern.compile(
            "^([a-zA-Z0-9_.]+(?:Exception|Error))(?:: (.*))?");

    // stack trace lines
    private static final Pattern STACK_TRACE_LINE = Pattern.compile(
            "^\\s*at ([\\w.$]+)\\.(\\w+)\\(([^:]+):(\\d+)\\)");

    private static final Pattern DEPENDENCY_REQUIRES_DIFFERENT_VERSION = Pattern.compile(
            "Dependency [\\S]* requires [\\S]* [\\S]* or ((higher)|(lower))");

    private static final Pattern MODULE_COMPILED_WITH_DIFFERENT_VERSION = Pattern.compile(
            "\\[ERROR\\] [\\S]*: Module was compiled with an incompatible version");

    private static final Pattern CLASS_FILE_HAS_WRONG_VERSION = Pattern.compile(
            " *class file has wrong version");

    public static class CompileError {
        public String file;
        public int line;
        public int column;
        public String message;
        public boolean isImportRelated;
        public Map<String, String> details = new LinkedHashMap<>();

        public String toString() {
            return "CompileError{" +
                    "file='" + file + '\'' +
                    ", line=" + line +
                    ", column=" + column +
                    ", message='" + message + '\'' +
                    ", details=" + details +
                    '}';
        }
    }

    static class RuntimeError {
        String exception;
        String message;
        List<StackFrame> stack = new ArrayList<>();

        public String toString() {
            return "RuntimeError{" +
                    "exception='" + exception + '\'' +
                    ", message='" + message + '\'' +
                    ", stack=" + stack +
                    '}';
        }
    }

    static class StackFrame {
        String clazz;
        String method;
        String file;
        int line;

        public String toString() {
            return clazz + "." + method + "(" + file + ":" + line + ")";
        }
    }

    public static void main(String[] args) throws IOException {
        Path logFile = Paths.get("testFiles/brokenLogs/0ddd0efa29634a4783358cba727d0851236aa579_IDS-Messaging-Services"); // your log file
        List<Object> errors = parseLog(logFile);
        for (Object error : errors) {
            System.out.println(error);
        }

    }

    public static boolean lineMatchesPattern(String line, Pattern pattern) {
        Matcher m = pattern.matcher(line);
        return m.find();
    }

    public static boolean projectIsFixableThroughCodeModification(Path pathToLog) throws IOException {
        List<String> allLines = Files.readAllLines(pathToLog);
        for (String line : allLines) {
            if (line.startsWith("[ERROR] The following dependencies differ:")
                    || line.startsWith("Found Banned Dependency")
                    || line.startsWith("Dependency convergence error")
                    || line.startsWith("Require upper bound dependencies error")
                    || lineMatchesPattern(line, DEPENDENCY_REQUIRES_DIFFERENT_VERSION)
                    || lineMatchesPattern(line, MODULE_COMPILED_WITH_DIFFERENT_VERSION)
                    || lineMatchesPattern(line, CLASS_FILE_HAS_WRONG_VERSION)) {
                return false;
            }

        }
        return true;
    }

    public static List<Object> parseLog(Path path) throws IOException {
        List<Object> errors = new ArrayList<>();

        List<String> allLines = Files.readAllLines(path);
        boolean includeWarnings = false;
        for (String line : allLines) {
            if (line.contains("warnings found and -Werror specified")) {
                includeWarnings = true;
                break;
            }
        }

        try (BufferedReader br = Files.newBufferedReader(path)) {
            String line;
            CompileError currentCompile = null;
            RuntimeError currentRuntime = null;

            while ((line = br.readLine()) != null) {
                //System.out.println(line);
                Matcher m1 = COMPILE_ERROR.matcher(line);
                Matcher mw = WARNING_ERROR.matcher(line);
                Matcher alt = ALTERNATIVE_COMPILE_ERROR.matcher(line);
                boolean m1Found = m1.find();
                boolean altFound = alt.find();
                boolean mwFound = mw.find();
                if (altFound || m1Found || (includeWarnings && mwFound)) {
                    if (currentCompile != null) {
                        errors.add(currentCompile);
                    }
                    Matcher errorMatcher = m1;
                    if (includeWarnings && mwFound) {
                        System.out.println("INCLUDED WARNINGS");
                        errorMatcher = mw;
                    }

                    if (altFound && !mwFound && !m1Found) {
                        System.out.println("Alternative Error format found");
                        errorMatcher = alt;
                    }
                    currentCompile = new CompileError();
                    currentCompile.file = errorMatcher.group(1);
                    currentCompile.line = Integer.parseInt(errorMatcher.group(2));
                    currentCompile.column = Integer.parseInt(errorMatcher.group(3));
                    currentCompile.message = errorMatcher.group(4).trim();
                    currentCompile.isImportRelated = currentCompile.message.startsWith("package");

                    continue;
                }

                if (currentCompile != null && line.startsWith("  ")) {
                    String[] parts = line.trim().split(":", 2);
                    if (parts.length == 2) {
                        currentCompile.details.put(parts[0].trim(), parts[1].trim());
                    } else {
                        currentCompile.details.put(currentCompile.details.size() + "", parts[0].trim());
                    }
                    continue;
                } else if (currentCompile != null) {
                    errors.add(currentCompile);
                    currentCompile = null;
                }

                Matcher m2 = EXCEPTION_LINE.matcher(line);
                if (m2.find()) {
                    if (currentRuntime != null) {
                        errors.add(currentRuntime);
                    }
                    currentRuntime = new RuntimeError();
                    currentRuntime.exception = m2.group(1);
                    currentRuntime.message = m2.group(2);
                    continue;
                }

                Matcher m3 = STACK_TRACE_LINE.matcher(line);
                if (m3.find() && currentRuntime != null) {
                    StackFrame sf = new StackFrame();
                    sf.clazz = m3.group(1);
                    sf.method = m3.group(2);
                    sf.file = m3.group(3);
                    sf.line = Integer.parseInt(m3.group(4));
                    currentRuntime.stack.add(sf);
                    continue;
                }

                if (line.trim().isEmpty() && currentRuntime != null) {
                    errors.add(currentRuntime);
                    currentRuntime = null;
                }
            }

            if (currentCompile != null) {
                errors.add(currentCompile);
            }
            if (currentRuntime != null) {
                errors.add(currentRuntime);
            }
        }

        return errors;
    }
}

