package org.drasyl.jtasklet.vnmife;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.requireNonNull;

public class VNMIFETaskPayload {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final List<List<Integer>> x;
    private final List<List<Integer>> w;

    @JsonCreator
    public VNMIFETaskPayload(@JsonProperty("x") final List<List<Integer>> x,
                             @JsonProperty("w") final List<List<Integer>> w) {
        this.x = requireNonNull(x);
        this.w = requireNonNull(w);
    }

    public static VNMIFETaskPayload fromPath(final Path path) throws IOException {
        return OBJECT_MAPPER.readValue(path.toFile(), VNMIFETaskPayload.class);
    }

    public List<List<Integer>> getX() {
        return x;
    }

    public List<List<Integer>> getW() {
        return w;
    }

    public int getVecLen() {
        return x.isEmpty() ? 0 : x.get(0).size();
    }

    public int getBoundX() {
        return maxAbs(x);
    }

    public int getBoundY() {
        return maxAbs(w);
    }

    public void validateAgainstBounds(final int boundX, final int boundY) {
        final int maxX = maxAbs(x);
        if (maxX > boundX) {
            throw new IllegalArgumentException("x contains value with abs=" + maxX + " but broker bound-x is only " + boundX + ".");
        }

        final int maxY = maxAbs(w);
        if (maxY > boundY) {
            throw new IllegalArgumentException("w contains value with abs=" + maxY + " but broker bound-y is only " + boundY + ".");
        }
    }

    public List<String> clientIds(final String consumerId) {
        final List<String> clientIds = new ArrayList<>();
        for (int i = 0; i < x.size(); i++) {
            clientIds.add(consumerId + "-" + i);
        }
        return clientIds;
    }

    private static int maxAbs(final List<List<Integer>> matrix) {
        int max = 0;
        for (final List<Integer> row : matrix) {
            for (final Integer value : row) {
                if (value != null) {
                    max = Math.max(max, Math.abs(value));
                }
            }
        }
        return max;
    }
}
