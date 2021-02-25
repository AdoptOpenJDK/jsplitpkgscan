module jdk.jsplitpkgscan {
    requires java.compiler;
    provides java.util.spi.ToolProvider
            with jdk.jsplitpkgscan.Main.JsplitpgkscanToolProvider;
    provides javax.tools.Tool
            with jdk.jsplitpkgscan.Main.JsplitpgkscanTool;
}
