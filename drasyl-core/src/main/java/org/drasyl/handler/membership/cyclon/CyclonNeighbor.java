package org.drasyl.handler.membership.cyclon;

import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.Preconditions;

import java.util.Arrays;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

public class CyclonNeighbor implements Comparable<CyclonNeighbor> {
    private final DrasylAddress address;
    private int age;

    private CyclonNeighbor(final DrasylAddress address, final int age) {
        this.address = requireNonNull(address);
        this.age = Preconditions.requireNonNegative(age);
    }

    public DrasylAddress getAddress() {
        return address;
    }

    public int getAge() {
        return age;
    }

    public void increaseAgeByOne() {
        age += 1;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final CyclonNeighbor neighbor = (CyclonNeighbor) o;
        return address.equals(neighbor.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(address);
    }

    @Override
    public String toString() {
        return "CyclonNeighbor{" +
                "address=" + address +
                ", age=" + age +
                '}';
    }

    @Override
    public int compareTo(final CyclonNeighbor o) {
        if (age == o.age) {
            return Arrays.compare(address.toByteArray(), o.address.toByteArray());
        }
        return Integer.compare(age, o.age);
    }

    public static CyclonNeighbor of(final DrasylAddress address,
                                    final int age) {
        return new CyclonNeighbor(address, age);
    }

    public static CyclonNeighbor of(final DrasylAddress address) {
        return of(address, 0);
    }
}
