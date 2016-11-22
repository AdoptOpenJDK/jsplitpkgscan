module jsplitpkg {
    exports jdk;

    provides java.util.spi.ToolProvider
            with jdk.Main.JsplitpgkscanToolsProvider;
}
