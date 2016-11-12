package jdk;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ResourceBundle;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class JsplitpgkscanTask {

    private static class Options {
        final List<ListPackages> analyzers;
        boolean help;
        boolean all;
        boolean version;
        String packageArg;

        Options() {
        	analyzers = new ArrayList<>();
        	packageArg = "";
        }
    }

    private static class ResourceBundleHelper {
        static final ResourceBundle bundle;

        static {
            Locale locale = Locale.getDefault();
            try {
                bundle = ResourceBundle.getBundle("jdk.resources.jsplitpkgscan", locale);
            } catch (MissingResourceException e) {
                throw new InternalError("Cannot find jdeps resource bundle for locale " + locale);
            }
        }
    }

    private static final String PROGNAME = "jsplitpkgscan";
    private static final Pattern JAR_FILE_PATTERN = Pattern.
        compile("^.+\\.(jar|rar|war)$", Pattern.CASE_INSENSITIVE);

    private final ResourceBundleHelper bundleHelper = new ResourceBundleHelper();
    private final Options options = new Options();
    private PrintWriter log;
    
    void setLog(PrintWriter out) {
        log = out;
    }

    int run(String... args) {
        if (log == null) {
            log = new PrintWriter(System.out);
        }
        int rc = 0;
        try { 
            options.help = args.length == 0;
            for (Iterator<String> argIt = Arrays.asList(args).iterator(); argIt.hasNext(); ) {
                String arg  = argIt.next();
                switch (arg) {
                    case "-?":
                    case "-h":
                    case "--help":
                        options.help = true;
                        break;
                    case "-f":
                        if (argIt.hasNext()) {
                            Path p = Paths.get(argIt.next());
                            if (Files.exists(p) && Files.isRegularFile(p)) {
                                try (Stream<String> lines = Files.lines(p)) {
                                    lines
                                        .map(Paths::get)
                                        .forEach(this::addAnalyzer);
                                    continue;
                                }
                            }
                        }
                        options.help = true;
                        rc = 1;
                        break;
                    case "-d":
                        if (argIt.hasNext()) {
                            Path p = Paths.get(argIt.next());
                            if (Files.isDirectory(p)) {
                                try (Stream<Path> list = Files.list(p)) {
                                    list.forEach(this::addAnalyzer);
                                    continue;
                                }
                            }
                        }                    
                        options.help = true;
                        rc = 1;
                        break;
                    case "-a":
                        options.all = true;
                        continue;
                    case "-p":
                        if (argIt.hasNext()) {
                            options.packageArg = argIt.next();
                            continue;
                        }
                        options.help = true;
                        rc = 1;
                        break;
                    default:
                        if (arg.startsWith("-")) {
                            options.help = true;
                            rc = 1;
                            break;
                        }
                        addAnalyzer(Paths.get(arg));
                }
            }
            if (options.help) {
                showHelp();
            } else {
                doAnalyze();
            }
        } catch (IOException e) {
            rc = 2;
            e.printStackTrace(log);
        } finally {
            log.flush();
        }
        return rc;
    }

    private void doAnalyze() {

        Map<String, ListPackages> packageToModule = ListPackages.packageToModule();

        Map<String, List<ListPackages>> pkgs = new HashMap<>();
        for (ListPackages analyzer : options.analyzers) {
            analyzer.packages().stream()
                    .forEach(pn -> {
                        List<ListPackages> values =
                            pkgs.computeIfAbsent(pn, k -> new ArrayList<>());
                        values.add(analyzer);
                        if (packageToModule.containsKey(pn)) {
                            values.add(packageToModule.get(pn));
                        }
                    });
        }

        List<Map.Entry<String, List<ListPackages>>> splitPkgs = pkgs.entrySet()
            .stream()
            .filter(e -> e.getValue().size() > 1)
            .filter(e -> e.getKey().startsWith(options.packageArg))
            .sorted(Map.Entry.comparingByKey())
            .collect(Collectors.toList());

        if (!splitPkgs.isEmpty()) {
            System.out.println("- Split packages:");
            splitPkgs.forEach(e -> {
                System.out.println(e.getKey());
                e.getValue().stream()
                    .map(ListPackages::location)
                    .forEach(location -> System.out.format("    %s%n", location));
            });
        }

        if (options.all) {
            System.out.println("- All packages:");
            for (ListPackages analyzer : options.analyzers) {
                List<String> allPkgs = analyzer.packages()
                    .stream()
                    .filter(e -> e.startsWith(options.packageArg))
                    .sorted()
                    .collect(Collectors.toList());
                if (!allPkgs.isEmpty()) {
                    System.out.println(analyzer.location());
                    allPkgs.forEach(p -> System.out.format("   %s%n", p));
                }
            }
        }
    }

    private String getMessage(String key, Object... args) {
        try {
            return MessageFormat.format(ResourceBundleHelper.bundle.getString(key), args);
        } catch (MissingResourceException e) {
            throw new InternalError("Missing message: " + key);
        }
    }

    private void addAnalyzer(Path path) {
        if (Files.exists(path)) {
            try {
                if (Files.isDirectory(path)) {
                    options.analyzers.add(new ListPackages(path, ListPackages::packages));
                } else {
                    Matcher m = JAR_FILE_PATTERN.matcher(String.valueOf(path.getFileName()));
                    if (m.matches()) {
                        options.analyzers.add(new ListPackages(path, ListPackages::jarFilePackages));
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private void showHelp() {
        log.println(getMessage("main.usage", PROGNAME));
    }
}
