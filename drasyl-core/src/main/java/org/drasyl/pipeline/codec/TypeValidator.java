/*
 * Copyright (c) 2020.
 *
 * This file is part of drasyl.
 *
 *  drasyl is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  drasyl is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.drasyl.pipeline.codec;

import com.google.common.primitives.Primitives;
import org.drasyl.DrasylConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * The {@link TypeValidator} allows to define which classes and packages may be handled by a codec.
 * It thus represents a marshalling whitelist.
 */
public class TypeValidator {
    private final List<Class<?>> classes;
    private final List<String> packages;
    private final boolean allowAllPrimitives;
    private final boolean allowArrayOfTypes;

    @SuppressWarnings("java:S1172")
    TypeValidator(List<Class<?>> classes,
                  List<String> packages,
                  boolean allowAllPrimitives,
                  boolean allowArrayOfTypes, Supplier<Void> signatureExtender) {
        this.classes = classes;
        this.packages = packages;
        this.allowAllPrimitives = allowAllPrimitives;
        this.allowArrayOfTypes = allowArrayOfTypes;
    }

    private TypeValidator(List<String> classes,
                          List<String> packages,
                          boolean allowAllPrimitives,
                          boolean allowArrayOfTypes) {
        this.classes = Collections.synchronizedList(string2Class(classes));
        this.packages = Collections.synchronizedList(packages);
        this.allowAllPrimitives = allowAllPrimitives;
        this.allowArrayOfTypes = allowArrayOfTypes;

        // Always include byte and byte array
        this.classes.add(byte.class);
        this.classes.add(byte[].class);
    }

    private List<Class<?>> string2Class(List<String> classes) {
        List<Class<?>> transformedClasses = new ArrayList<>();

        for (String c : classes) {
            try {
                Class<?> clazz = Class.forName(c);
                transformedClasses.add(clazz);
            }
            catch (ClassNotFoundException e) {
                // ignore
            }
        }

        return transformedClasses;
    }

    /**
     * Adds a class to the list.
     *
     * @param clazz class that should be added
     */
    public void addClass(Class<?> clazz) {
        synchronized (classes) {
            classes.add(clazz);
        }
    }

    /**
     * Adds a package to the list.
     *
     * @param p package that should be added
     */
    public void addPackage(String p) {
        synchronized (packages) {
            packages.add(p);
        }
    }

    /**
     * Removes a class from the list.
     *
     * @param clazz class that should be removed
     */
    public void removeClass(Class<?> clazz) {
        synchronized (classes) {
            classes.remove(clazz);
        }
    }

    /**
     * Removes a package from the list.
     *
     * @param p package that should be removed
     */
    public void removePackage(String p) {
        synchronized (packages) {
            packages.remove(p);
        }
    }

    /**
     * Returns the Class representing the component type of an array. If this class does not
     * represent an array class this method returns the {@code clazzToUnwrap}.
     *
     * @param clazzToUnwrap class that should be unwrapped
     * @return the {@code Class} representing the component type of this class if this class is an
     * array, otherwise the input is returned
     */
    public static Class<?> unwrapArrayComponentType(Class<?> clazzToUnwrap) {
        if (clazzToUnwrap.isArray()) {
            return clazzToUnwrap.getComponentType();
        }

        return clazzToUnwrap;
    }

    private Class<?> unwrap(Class<?> clazz, boolean shouldBeUnwrapped) {
        if (shouldBeUnwrapped) {
            return unwrapArrayComponentType(clazz);
        }

        return clazz;
    }

    /**
     * Validates a given {@code Class} if it is allowed or not.
     *
     * @param clazzToTest class that should be validated
     * @return if the class is allowed or not
     */
    public boolean validate(Class<?> clazzToTest) {
        clazzToTest = unwrap(clazzToTest, allowArrayOfTypes);

        // Check if class is primitive
        if (allowAllPrimitives && (clazzToTest.isPrimitive() || Primitives.isWrapperType(clazzToTest))) {
            return true;
        }

        for (Class<?> clazz : classes) {
            if (isTypeOf(clazzToTest, clazz)) {
                return true;
            }
        }

        // Check if the class is inside an allowed package
        for (String p : packages) {
            Package pck = clazzToTest.getPackage();
            if (pck != null && pck.getName().startsWith(p)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Checks if {@code clazz} is a subclass of {@code superClass}.
     *
     * @param clazz      possible subclass
     * @param superClass the superclass
     * @return if clazz is a subclass of superClass true, otherwise false
     */
    public static boolean isTypeOf(Class<?> clazz, Class<?> superClass) {
        return superClass.isAssignableFrom(clazz);
    }

    public static TypeValidator of(List<String> classes,
                                   List<String> packages,
                                   boolean allowAllPrimitives,
                                   boolean allowArrayOfTypes) {
        return new TypeValidator(classes, packages, allowAllPrimitives, allowArrayOfTypes);
    }

    public static TypeValidator of(DrasylConfig config) {
        return of(config.getMarshallingAllowedTypes(), config.getMarshallingAllowedPackages(), config.isMarshallingAllowAllPrimitives(), config.isMarshallingAllowArrayOfDefinedTypes());
    }
}