package org.drasyl.pipeline.codec;

import java.util.Arrays;
import java.util.Objects;

/**
 * Simple class that holds a serialized object as byte array and the corresponding class of the
 * deserialized object.
 */
public class ObjectHolder {
    private final Class<?> clazz;
    private final byte[] object;

    private ObjectHolder(Class<?> clazz, byte[] object) {
        this.clazz = clazz;
        this.object = object;
    }

    public Class<?> getClazz() {
        return clazz;
    }

    public byte[] getObject() {
        return object;
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(clazz);
        result = 31 * result + Arrays.hashCode(object);
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ObjectHolder that = (ObjectHolder) o;
        return Objects.equals(clazz, that.clazz) &&
                Arrays.equals(object, that.object);
    }

    @Override
    public String toString() {
        return "ObjectHolder{" +
                "clazz=" + clazz +
                ", object=" + Arrays.toString(object) +
                '}';
    }

    public static ObjectHolder of(Class<?> clazz, byte[] o) {
        return new ObjectHolder(clazz, o);
    }
}
