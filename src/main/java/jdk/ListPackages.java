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

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

/**
 * Lists the packages of the given JAR file or exploded directory
 * and reports the list of split packages
 */
class ListPackages {
    private static final String MODULE_INFO = "module-info.class";

    private final URI location;
    private final Set<String> packages;

    ListPackages(Path path, Function<Path, Set<String>> pkgFunction) throws IOException {
        this.location = path.toUri();
        this.packages = pkgFunction.apply(path);
    }

    private ListPackages(ModuleReference mref) {
        this.location = mref.location().get();
        this.packages = mref.descriptor().packages();
    }

    Set<String> packages() {
        return packages;
    }

    URI location() {
        return location;
    }

    /**
     * Walks the given directory and returns all packages.
     *
     * This method needs to be updated to include resources
     * for #ResourceEncapsulation.
     */
    static Set<String> packages(Path dir) {
        try {
            return Files.find(dir, Integer.MAX_VALUE,
                (p, attr) -> p.getFileName().toString().endsWith(".class") &&
                    !p.getFileName().toString().equals(MODULE_INFO))
                .map(Path::getParent)
                .map(dir::relativize)
                .map(Path::toString)
                .map(p -> p.replace(File.separator, "."))
                .map(ListPackages::specialCaseTranslator)
                .collect(Collectors.toSet());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Returns all packages of the given JAR file.
     */
    static Set<String> jarFilePackages(Path path) {
        try (JarFile jf = new JarFile(path.toFile())) {
            return jf.stream()
                .map(JarEntry::getName)
                .filter(n -> n.endsWith(".class") && !n.equals(MODULE_INFO))
                .map(ListPackages::toPackage)
                .map(ListPackages::specialCaseTranslator)
                .collect(Collectors.toSet());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static Map<String, ListPackages> packageToModule() {
        Map<String, ListPackages> map = new HashMap<>();
        ModuleFinder.ofSystem().findAll()
            .stream()
            .map(mref -> new ListPackages(mref))
            .forEach(o -> o.packages().forEach(pn -> map.put(pn, o)));
        return map;
    }

    private static String toPackage(String name) {
        int i = name.lastIndexOf('/');
        return i != -1 ? name.substring(0, i).replace("/", ".") : "";
    }

    private static String specialCaseTranslator(String packageName) {
        if (packageName.startsWith("WEB-INF.classes.")) {
            return packageName.substring(16);
        }
        return packageName;
    }
}
