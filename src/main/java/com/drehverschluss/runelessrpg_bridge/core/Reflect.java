package com.drehverschluss.runelessrpg_bridge.core;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Supplier;

/**
 * Reads switches of other mods by reflection, without a dependency. Every method throws if the value cannot be read;
 * callers treat that as "active" (the safe side).
 */
public final class Reflect {
    private Reflect() {}

    /** Value of a static field that holds a config value (anything with {@code get()}) or a plain value. */
    public static Object staticValue(String className, String fieldName) throws ReflectiveOperationException {
        Class<?> owner = Class.forName(className, true, Reflect.class.getClassLoader());
        Field field = owner.getDeclaredField(fieldName);
        field.setAccessible(true);
        return unwrap(field.get(null));
    }

    /** Result of a static method without parameters. */
    public static Object staticCall(String className, String methodName) throws ReflectiveOperationException {
        Class<?> owner = Class.forName(className, true, Reflect.class.getClassLoader());
        Method method = owner.getDeclaredMethod(methodName);
        method.setAccessible(true);
        return unwrap(method.invoke(null));
    }

    private static Object unwrap(Object value) {
        if (value instanceof Supplier<?> supplier) {
            return supplier.get();
        }
        return value;
    }

    public static boolean bool(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        throw new IllegalArgumentException("not a boolean: " + value);
    }
}
