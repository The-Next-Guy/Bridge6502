package org.xemplarsoft.bridge.assy;

import jsyntaxpane.DefaultSyntaxKit;

public class AssemblySyntaxKit extends DefaultSyntaxKit {
    public AssemblySyntaxKit() {
        super(new Assembly6502Lexer());
    }
}
