// TARGET_BACKEND: JVM

// WITH_RUNTIME
// FILE: Test.java

class Test {
    public static Class<?> apply(Runnable x) {
        return x.getClass();
    }

    public static interface ABC {
        void apply(String x1, String x2);
    }

    public static Class<?> applyABC(ABC x) {
        return x.getClass();
    }
}

// FILE: test.kt

import java.lang.reflect.Method
import kotlin.test.assertEquals

@Retention(AnnotationRetention.RUNTIME)
annotation class Ann(val x: String)

fun testMethod(method: Method, name: String) {
    assertEquals("OK", method.getAnnotation(Ann::class.java).x, "On method of test named `$name`")

    for ((index, annotations) in method.getParameterAnnotations().withIndex()) {
        val ann = annotations.filterIsInstance<Ann>().single()
        assertEquals("OK$index", ann.x, "On parameter $index of test named `$name`")
    }
}

fun testMethodNoAnnotations(method: Method, name: String) {
    assertEquals(0, method.getDeclaredAnnotations().size, "No method annotations expected `$name`")
    for ((index, annotations) in method.getParameterAnnotations().withIndex()) {
        assertEquals(0, annotations.size, "No parameter $index annotations expected `$name`")
    }
}


fun testClass(clazz: Class<*>, name: String, methodTest: (Method, String) -> Unit) {
    val invokes = clazz.getDeclaredMethods().single() { !it.isBridge() }
    methodTest(invokes, name)
}

@Ann("OK")
fun annotatedFunction(@Ann("OK0") x: String, @Ann("OK1") y: String) {
}

fun box(): String {
    testClass(Test.apply(@Ann("OK") fun(){}), "1", ::testMethod)
    testClass(Test.applyABC(@Ann("OK") fun(@Ann("OK0") x: String, @Ann("OK1") y: String){}), "2", ::testMethod)
    testClass(Test.applyABC(::annotatedFunction), "3", ::testMethodNoAnnotations)
    val reference = ::annotatedFunction
    testClass(Test.applyABC(reference), "4", ::testMethodNoAnnotations)
    return "OK"
}
