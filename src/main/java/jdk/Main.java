package jdk;

import java.io.PrintWriter;

public class Main {

    /**
     * Default main entry point used as main class. At the end of the analyzing
     * run, the {@link System.exit()} is being called.
     *
     * @param args command line arguments
     */
    public static void main(String... args) throws Exception {
        JsplitpgkscanTask task = new JsplitpgkscanTask();
        int rc = task.run(args);
        System.exit(rc);
    }

    /**
     * Entry point that does <i>not</i> call System.exit.
     *
     * @param args command line arguments
     * @param out output stream
     * @return an exit code. 0 means success, non-zero means an error occurred.
     */
    public static int run(String[] args, PrintWriter out) {
        JsplitpgkscanTask task = new JsplitpgkscanTask();
        task.setLog(out);
        return task.run(args);
    }
}