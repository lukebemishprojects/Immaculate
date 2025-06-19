package test;

import java.util.List;
import java.util.function.Consumer;

import javax.tools.JavaCompiler;

import test.anotherpackage.ToImport;

// spotless:off
import java.util.function.Function;
// spotless:on

public class Test {
    int i;

    @Deprecated
    public void test() {
        Class<?> clazz = Consumer.class;
        clazz = List.class;
        clazz = JavaCompiler.class;
        clazz = ToImport.class;
    }
}