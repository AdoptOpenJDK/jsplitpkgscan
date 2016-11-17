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

    int run(String... arguments) {
        if (log == null) {
            log = new PrintWriter(System.out);
        }
        int rc = 0;
        try { 
            options.help = arguments.length == 0;
            for (Iterator<String> argIt = Arrays.asList(arguments).iterator(); argIt.hasNext(); ) {
                String argument  = argIt.next();
                switch (argument) {
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
                        if (argument.startsWith("-")) {
                            options.help = true;
                            rc = 1;
                            break;
                        }
                        addAnalyzer(Paths.get(argument));
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

        Map<String, List<ListPackages>> packages = new HashMap<>();
        for (ListPackages analyzer : options.analyzers) {
            analyzer.packages().stream()
                    .forEach(packageName -> {
                        List<ListPackages> values =
                            packages.computeIfAbsent(packageName, key -> new ArrayList<>());
                        values.add(analyzer);
                        if (packageToModule.containsKey(packageName)) {
                            values.add(packageToModule.get(packageName));
                        }
                    });
        }

        List<Map.Entry<String, List<ListPackages>>> splitPkgs = packages.entrySet()
            .stream()
            .filter(element -> element.getValue().size() > 1)
            .filter(element -> element.getKey().startsWith(options.packageArg))
            .sorted(Map.Entry.comparingByKey())
            .collect(Collectors.toList());

        if (!splitPkgs.isEmpty()) {
            log.println("- Split packages:");
            splitPkgs.forEach(element -> {
                log.println(element.getKey()); // the package name
                element.getValue().stream()
                    .map(ListPackages::location)
                    .forEach(location -> log.format("    %s%n", location));
            });
        }

        if (options.all) {
            log.println("- All packages:");
            for (ListPackages analyzer : options.analyzers) {
                List<String> allPkgs = analyzer.packages()
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
    }

    private String getMessage(String key, Object... arguments) {
        try {
            return MessageFormat.format(ResourceBundleHelper.bundle.getString(key), arguments);
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
