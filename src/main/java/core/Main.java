package core;

import org.graalvm.polyglot.Context;

public class Main {
    public static void main(String[] args) {
        System.out.println("java");
        try (Context context = Context.create()) {
            context.eval("js", "print('js');");
        }
    }
}