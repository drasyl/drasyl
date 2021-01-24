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
    private static final Class<?>[] primitiveTypes;

    static {
        primitiveTypes = new Class[]{
                boolean.class,
                char.class,
                double.class,
                float.class,
                int.class,
                long.class,
                short.class,
                void.class,
                Boolean.class,
                Character.class,
                Double.class,
                Float.class,
                Integer.class,
                Long.class,
                Short.class,
                Void.class
        };
    }

    @SuppressWarnings("java:S1172")
    TypeValidator(final List<Class<?>> classes,
                  final List<String> packages,
                  final boolean allowAllPrimitives,
                  final boolean allowArrayOfTypes, final Supplier<Void> signatureExtender) {
        this.classes = classes;
        this.packages = packages;
        this.allowAllPrimitives = allowAllPrimitives;
        this.allowArrayOfTypes = allowArrayOfTypes;
    }

    private TypeValidator(final List<String> classes,
                          final List<String> packages,
                          final boolean allowAllPrimitives,
                          final boolean allowArrayOfTypes) {
        this.classes = Collections.synchronizedList(string2Class(classes));
        this.packages = Collections.synchronizedList(packages);
        this.allowAllPrimitives = allowAllPrimitives;
        this.allowArrayOfTypes = allowArrayOfTypes;

        // Always include byte and byte array
        this.classes.add(byte.class);
        this.classes.add(Byte.class);
        this.classes.add(byte[].class);
    }

    /**
     * Returns {@code true} if the {@code clazz} is a primitive type except of type {@code Void}.
     *
     * @param clazz the class to check
     * @return true if the type is a primitive type
     */
    public static boolean isPrimitive(final Class<?> clazz) {
        for (final Class<?> primitiveType : primitiveTypes) {
            if (primitiveType.equals(clazz)) {
                return true;
            }
        }

        return false;
    }

    private List<Class<?>> string2Class(final List<String> classes) {
        final List<Class<?>> transformedClasses = new ArrayList<>();

        for (final String c : classes) {
            try {
                final Class<?> clazz = Class.forName(c);
                transformedClasses.add(clazz);
            }
            catch (final ClassNotFoundException e) {
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
    public void addClass(final Class<?>... clazz) {
        synchronized (classes) {
            classes.addAll(List.of(clazz));
        }
    }

    /**
     * Adds a package to the list.
     *
     * @param p package that should be added
     */
    public void addPackage(final String p) {
        synchronized (packages) {
            packages.add(p);
        }
    }

    /**
     * Removes a class from the list.
     *
     * @param clazz class that should be removed
     */
    public void removeClass(final Class<?> clazz) {
        synchronized (classes) {
            classes.remove(clazz);
        }
    }

    /**
     * Removes a package from the list.
     *
     * @param p package that should be removed
     */
    public void removePackage(final String p) {
        synchronized (packages) {
            packages.remove(p);
        }
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
        if (allowAllPrimitives && isPrimitive(clazzToTest)) {
            return true;
        }

        for (final Class<?> clazz : classes) {
            if (isTypeOf(clazzToTest, clazz)) {
                return true;
            }
        }

        // Check if the class is inside an allowed package
        for (final String p : packages) {
            final Package pck = clazzToTest.getPackage();
            if (pck != null && pck.getName().startsWith(p)) {
                return true;
            }
        }

        return false;
    }

    private Class<?> unwrap(final Class<?> clazz, final boolean shouldBeUnwrapped) {
        if (shouldBeUnwrapped) {
            return unwrapArrayComponentType(clazz);
        }

        return clazz;
    }

    /**
     * Checks if {@code clazz} is a subclass of {@code superClass}.
     *
     * @param clazz      possible subclass
     * @param superClass the superclass
     * @return if clazz is a subclass of superClass true, otherwise false
     */
    public static boolean isTypeOf(final Class<?> clazz, final Class<?> superClass) {
        return superClass.isAssignableFrom(clazz);
    }

    /**
     * Returns the Class representing the component type of an array. If this class does not
     * represent an array class this method returns the {@code clazzToUnwrap}.
     *
     * @param clazzToUnwrap class that should be unwrapped
     * @return the {@code Class} representing the component type of this class if this class is an
     * array, otherwise the input is returned
     */
    public static Class<?> unwrapArrayComponentType(final Class<?> clazzToUnwrap) {
        final Class<?> unwrapped = clazzToUnwrap.getComponentType();
        if (unwrapped != null) {
            return unwrapped;
        }

        return clazzToUnwrap;
    }

    public static TypeValidator ofInboundValidator(final DrasylConfig config) {
        return of(config.getMarshallingInboundAllowedTypes(),
                config.getMarshallingInboundAllowedPackages(),
                config.isMarshallingInboundAllowAllPrimitives(),
                config.isMarshallingInboundAllowArrayOfDefinedTypes());
    }

    public static TypeValidator of(final List<String> classes,
                                   final List<String> packages,
                                   final boolean allowAllPrimitives,
                                   final boolean allowArrayOfTypes) {
        return new TypeValidator(classes, packages, allowAllPrimitives, allowArrayOfTypes);
    }

    public static TypeValidator ofOutboundValidator(final DrasylConfig config) {
        return of(config.getMarshallingOutboundAllowedTypes(),
                config.getMarshallingOutboundAllowedPackages(),
                config.isMarshallingOutboundAllowAllPrimitives(),
                config.isMarshallingOutboundAllowArrayOfDefinedTypes());
    }
}