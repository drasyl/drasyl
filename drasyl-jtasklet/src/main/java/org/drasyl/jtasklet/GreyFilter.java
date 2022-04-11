package org.drasyl.jtasklet;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class GreyFilter {
    private final BufferedImage canvas;

    public GreyFilter(final File input) throws IOException {
        this.canvas = ImageIO.read(input);
    }

    public GreyFilter(final BufferedImage canvas) {
        this.canvas = canvas;
    }

    public Object[] getInput() {
        final int pixelCount = canvas.getHeight() * canvas.getWidth();
        final int[] r = new int[pixelCount];
        final int[] g = new int[pixelCount];
        final int[] b = new int[pixelCount];

        int i = 0;
        for (int y = 0; y < canvas.getHeight(); y++) {
            for (int x = 0; x < canvas.getWidth(); x++) {
                final int p = canvas.getRGB(x, y);

                r[i] = (p >> 16) & 0xFF;
                g[i] = (p >> 8) & 0xFF;
                b[i] = p & 0xFF;

                i++;
            }
        }

        return new Object[]{ pixelCount, r, g, b };
    }

    public void writeTo(final File output) throws IOException {
        ImageIO.write(canvas, "png", output);
    }

    public static GreyFilter of(final int height, final int width, final Object[] points) {
        final BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < canvas.getHeight(); y++) {
            for (int x = 0; x < canvas.getWidth(); x++) {
                int p = canvas.getRGB(x, y);
                final int grey = (Integer) points[y * canvas.getWidth() + x];
                final int a = (p >> 24) & 0xff;
                p = (a << 24) | (grey << 16) | (grey << 8) | grey;
                canvas.setRGB(x, y, p);
            }
        }

        return new GreyFilter(canvas);
    }

    public static void main(String[] args) throws IOException {
        GreyFilter greyFilter = new GreyFilter(new File("images/3phases.jpg"));
        final Object[] input = greyFilter.getInput();
        System.out.println();
    }
}

