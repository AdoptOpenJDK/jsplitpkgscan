module jsplitpkgscan {
    requires java.compiler;
    provides java.util.spi.ToolProvider
            with jdk.Main.JsplitpgkscanToolProvider;
    provides javax.tools.Tool
            with jdk.Main.JsplitpgkscanTool;
}
