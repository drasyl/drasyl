package test.util;

import org.drasyl.handler.membership.cyclon.CyclonNeighbor;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import static java.util.Objects.requireNonNull;

/**
 * As {@link CyclonNeighbor#equals(Object)} only compares {@link CyclonNeighbor#getAddress()}, we need this
 * special matcher that also compares {@link CyclonNeighbor#getAge()} in addition.
 */
public class EqualNeighborWithSameAge extends TypeSafeMatcher<CyclonNeighbor> {
    private final CyclonNeighbor left;

    private EqualNeighborWithSameAge(final CyclonNeighbor left) {
        this.left = requireNonNull(left);
    }

    @Override
    protected boolean matchesSafely(final CyclonNeighbor right) {
        return left.equals(right) &&
                left.getAge() == right.getAge();
    }

    @Override
    public void describeTo(final Description description) {
        description.appendText("equal neighbor with same age as " + left);
    }

    public static Matcher<CyclonNeighbor> equalNeighborWithSameAge(final CyclonNeighbor left) {
        return new EqualNeighborWithSameAge(left);
    }
}
