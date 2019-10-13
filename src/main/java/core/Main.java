package core;


import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;

public class Main {
    public static void main(String[] args) {
        System.out.println("java");

        try (Context context = Context.newBuilder("core").allowAllAccess(true).build()) {
            Source text = Source.create("core","foo");
            System.out.println(context.eval(text).toString());
        }
    }
}