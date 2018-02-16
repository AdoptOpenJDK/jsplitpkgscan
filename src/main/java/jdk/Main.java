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

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.Set;
import java.util.spi.ToolProvider;
import javax.lang.model.SourceVersion;
import javax.tools.Tool;

public class Main {
    private static final String jsplitpgkscan = "jsplitpgkscan";

    /**
     * Default main entry point used as main class. At the end of the analyzing
     * run, the {@link System#exit(int)} is being called.
     *
     * @param arguments command line arguments
     */
    public static void main(String... arguments) {
        JsplitpgkscanTask task = new JsplitpgkscanTask();
        int rc = task.run(arguments);
        System.exit(rc);
    }

    /**
     * Entry point that does <i>not</i> call System.exit.
     *
     * @param arguments command line arguments
     * @param out output stream
     * @return an exit code. 0 means success, non-zero means an error occurred.
     */
    public static int run(String[] arguments, PrintWriter out) {
        JsplitpgkscanTask task = new JsplitpgkscanTask();
        task.setLog(out);
        return task.run(arguments);
    }


    public static class JsplitpgkscanToolProvider implements ToolProvider {
        @Override
        public String name() {
            return jsplitpgkscan;
        }

        @Override
        public int run(PrintWriter out, PrintWriter err, String... arguments) {
            return Main.run(arguments, out);
        }

        @Override
        public String toString() {
            return jsplitpgkscan;
        }
    }

    public static class JsplitpgkscanTool implements Tool {
        @Override
        public String name() {
            return jsplitpgkscan;
        }

        @Override
        public int run(InputStream in, OutputStream out, OutputStream err, String... arguments) {
            return Main.run(arguments, new PrintWriter(out));
        }

        @Override
        public Set<SourceVersion> getSourceVersions() {
            return Collections.emptySet();
        }

        @Override
        public String toString() {
            return jsplitpgkscan;
        }
    }
}

