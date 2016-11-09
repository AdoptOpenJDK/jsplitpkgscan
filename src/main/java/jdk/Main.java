package jdk;

import java.io.PrintWriter;

public class Main {

    public static void main(String... args) throws Exception {
        JsplitpgkscanTask t = new JsplitpgkscanTask();
        int rc = t.run(args);
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
        JsplitpgkscanTask t = new JsplitpgkscanTask();
        t.setLog(out);
        return t.run(args);
    }
}