package org.xemplarsoft.bridge.assy;

public final class AssemblyError {
    public final int lineNumber;
    public final String message;
    public final String file;
    public final Type type;
    public AssemblyError(int lineNumber, String file, String message, Type type){
        this.lineNumber = lineNumber;
        this.file = file;
        this.message = message;
        this.type = type;
    }

    public void printToErr(){
        System.err.println(this);
    }

    public String toString(){
        return (type == Type.WARNING ? "Warning" : "Error") + " at line " + lineNumber + " of file \"" + file + "\": " + message;
    }

    public enum Type{
        WARNING,
        ERROR
    }
}
