package test;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javax.tools.JavaCompiler;

import test.anotherpackage.ToImport; // retains comments

// spotless:off
import java.util.function.Function;
// spotless:on

// immaculate:off
import java.util.function.Function;
// immaculate:on

import java.util.function.Function; // immaculate:ignore

public class Test {
    int i;

    /**
     * Test referencing an import ({@link BiConsumer}) in a javadoc.
     */
    @Deprecated
    public void test() {
        Class<?> clazz = Consumer.class;
        clazz = List.class;
        clazz = JavaCompiler.class;
        clazz = ToImport.class;
        clazz = ToImport.class; // immaculate:ignore
    }
}
