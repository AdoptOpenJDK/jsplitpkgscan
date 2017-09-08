/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package jdk;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.MessageFormat;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class JsplitpgkscanTask {
    interface BadArguments {
        String getKey();

        Object[] getArgs();

        boolean showUsage();
    }

    static class BadArgs extends Exception implements BadArguments {
        static final long serialVersionUID = 1L;

        BadArgs(String key, Object... args) {
            super(JsplitpgkscanTask.getMessage(key, args));
            this.key = key;
            this.args = args;
        }

        BadArgs showUsage(boolean b) {
            showUsage = b;
            return this;
        }

        final String key;
        final Object[] args;
        boolean showUsage;

        @Override
        public String getKey() {
            return key;
        }

        @Override
        public Object[] getArgs() {
            return args;
        }

        @Override
        public boolean showUsage() {
            return showUsage;
        }
    }

    class DirectoryEntryVisitor extends SimpleFileVisitor<Path> {
        private final Path libraryDirectory;

        public DirectoryEntryVisitor(Path libraryDirectory) {
            this.libraryDirectory = libraryDirectory;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            if (!libraryDirectory.equals(dir)) {
                if (dir.endsWith("WEB-INF/classes")) {
                    addAnalyzer(dir, libraryDirectory::relativize);
                    return FileVisitResult.SKIP_SUBTREE;
                }
            } else if (dir.endsWith("jar") || dir.endsWith("rar")) {
                addAnalyzer(dir, libraryDirectory::relativize);
                return FileVisitResult.SKIP_SUBTREE;
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            addAnalyzer(file, libraryDirectory::relativize);
            return FileVisitResult.CONTINUE;
        }
    }

    private static class Options {
        final List<Library> libraries;
        boolean help;
        boolean all;
        Path dotOutputDirectory;
        String packageArg;

        Options() {
            libraries = new ArrayList<>();
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
                throw new InternalError("Cannot find jsplitpkgscan resource bundle for locale " + locale);
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

    int run(String... arguments) {
        if (log == null) {
            log = new PrintWriter(System.out);
        }
        int rc = 0;
        try {
            handleOptions(arguments);
            if (options.help) {
                showHelp();
            } else {
                Consumer<Map<String, List<Library>>> packageProcessor;
                if (options.all) {
                    packageProcessor = this::reportAllPackages;
                } else if (options.dotOutputDirectory != null) {
                    packageProcessor = this::writeDotFiles;
                } else {
                    packageProcessor = this::reportSplitPackages;
                }
                doAnalyze(packageProcessor);
            }
        } catch (BadArgs badArguments) {
            reportError(badArguments.getKey(), badArguments.getArgs());
            if (badArguments.showUsage()) {
                log.println(getMessage("main.usage.summary", PROGNAME));
            }
            rc = 1;
        } finally {
            log.flush();
        }
        return rc;
    }

    private void handleOptions(String[] arguments) throws BadArgs {
        options.help = arguments.length == 0;
        for (Iterator<String> argIt = Arrays.asList(arguments).iterator(); argIt.hasNext(); ) {
            String argument = argIt.next();
            switch (argument) {
                case "-?":
                case "-h":
                case "--help":
                    options.help = true;
                    break;
                case "-f":
                    if (argIt.hasNext()) {
                        Path libraryDefinitionFile = Paths.get(argIt.next());
                        if (Files.exists(libraryDefinitionFile) && Files.isRegularFile(libraryDefinitionFile)) {
                            try (Stream<String> lines = Files.lines(libraryDefinitionFile)) {
                                lines
                                        .map(Paths::get)
                                        .forEach(path -> addAnalyzer(path, Path::getFileName));
                                continue;
                            } catch (IOException ioe) {
                                ioe.printStackTrace(log);
                                throw new BadArgs("err.scanning.dir", libraryDefinitionFile);
                            }
                        }
                        throw new BadArgs("err.invalid.path", libraryDefinitionFile);
                    }
                    throw new BadArgs("err.missing.arg", argument);
                case "-d":
                    if (argIt.hasNext()) {
                        Path libraryDirectory = Paths.get(argIt.next()).toAbsolutePath();
                        if (Files.isDirectory(libraryDirectory)) {
                            try {
                                Files.walkFileTree(libraryDirectory, new DirectoryEntryVisitor(libraryDirectory));
                                continue;
                            } catch (IOException ioe) {
                                ioe.printStackTrace(log);
                                throw new BadArgs("err.scanning.dir", libraryDirectory);
                            }
                        }
                        throw new BadArgs("err.invalid.path", libraryDirectory);
                    }
                    throw new BadArgs("err.missing.arg", argument);
                case "-a":
                    options.all = true;
                    continue;
                case "-p":
                    if (argIt.hasNext()) {
                        options.packageArg = argIt.next();
                        continue;
                    }
                    throw new BadArgs("err.missing.arg", argument);
                case "--dot-output":
                    if (argIt.hasNext()) {
                        Path dotOutputDirectory = Paths.get(argIt.next());
                        if (Files.exists(dotOutputDirectory) && Files.isDirectory(dotOutputDirectory)) {
                            options.dotOutputDirectory = dotOutputDirectory;
                            continue;
                        }
                        throw new BadArgs("err.missing.outputdir", dotOutputDirectory);
                    }
                    throw new BadArgs("err.missing.arg", argument);
                default:
                    if (argument.startsWith("-")) {
                        throw new BadArgs("err.unknown.option", argument).showUsage(true);
                    }
                    addAnalyzer(Paths.get(argument), Path::getFileName);
            }
        }
    }

    private void doAnalyze(Consumer<Map<String, List<Library>>> analyseConsumer) {
        Map<String, Library> packageToModule = Library.packageToModule();
        Map<String, List<Library>> packages = new HashMap<>();
        for (Library analyzer : options.libraries) {
            analyzer.packages().keySet().stream()
                    .forEach(packageName -> {
                        List<Library> values =
                                packages.computeIfAbsent(packageName, key -> new ArrayList<>());
                        values.add(analyzer);
                        if (packageToModule.containsKey(packageName)) {
                            values.add(packageToModule.get(packageName));
                        }
                    });
        }
        analyseConsumer.accept(packages);
    }

    private Stream<Map.Entry<String, List<Library>>> splitPackages(Map<String, List<Library>> packages) {
        return packages.entrySet()
                .stream()
                .filter(element -> element.getValue().size() > 1)
                .filter(element -> element.getKey().startsWith(options.packageArg))
                .sorted(Map.Entry.comparingByKey());
    }

    private void reportSplitPackages(Map<String, List<Library>> packages) {
        List<Map.Entry<String, List<Library>>> splitPkgs = splitPackages(packages).collect(Collectors.toList());
        if (!splitPkgs.isEmpty()) {
            log.println("- Split packages:");
            splitPkgs.forEach(element -> {
                log.println(element.getKey()); // the package name
                element.getValue().stream()
                        .distinct()
                        .sorted()
                        .forEach(library -> log.format("  %5d  %s%n", library.count(element.getKey()), library.location()));
            });
        }
    }

    private void reportAllPackages(Map<String, List<Library>> packages) {
        log.println("- All packages:");
        for (Library analyzer : options.libraries) {
            List<String> allPkgs = analyzer.packages().keySet()
                    .stream()
                    .filter(element -> element.startsWith(options.packageArg))
                    .sorted()
                    .collect(Collectors.toList());
            if (!allPkgs.isEmpty()) {
                log.println(analyzer.location());
                allPkgs.forEach(packageName -> log.format("   %s%n", packageName));
            }
        }
    }

    private void writeDotFiles(Map<String, List<Library>> packages) {
        try (BufferedWriter bw = Files.newBufferedWriter(options.dotOutputDirectory.resolve("summary.dot"));
             PrintWriter writer = new PrintWriter(bw)) {
            writer.format("digraph \"summary\" {%n");
            splitPackages(packages).forEach(element -> {
                element.getValue().forEach(library ->
                        writer.format("   %-50s -> \"%s\";%n", String.format("\"%s\"", library.location()), element.getKey())
                );
            });
            writer.println("}");
        } catch (IOException ioe) {
            ioe.printStackTrace(log);
        }
    }

    private static String getMessage(String key, Object... arguments) {
        try {
            return MessageFormat.format(ResourceBundleHelper.bundle.getString(key), arguments);
        } catch (MissingResourceException e) {
            throw new InternalError("Missing message: " + key);
        }
    }

    private void addAnalyzer(Path path, Function<Path, Path> relativizeFunction) {
        if (Files.exists(path)) {
            try {
                if (Files.isDirectory(path)) {
                    options.libraries.add(new Library(path, relativizeFunction, Library::packages));
                } else {
                    Matcher m = JAR_FILE_PATTERN.matcher(String.valueOf(path.getFileName()));
                    if (m.matches()) {
                        options.libraries.add(new Library(path, relativizeFunction, Library::jarFilePackages));
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

    private void reportError(String key, Object... args) {
        log.println(getMessage("error.prefix") + " " + getMessage(key, args));
    }

    void warning(String key, Object... args) {
        log.println(getMessage("warn.prefix") + " " + getMessage(key, args));
    }
}
