/*
 * Copyright (c) 2020-2021 Heiko Bornholdt and Kevin Röbert
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.drasyl.pipeline;

import org.drasyl.pipeline.skeleton.SimpleDuplexHandler;
import org.drasyl.pipeline.skeleton.SimpleInboundHandler;
import org.drasyl.pipeline.skeleton.SimpleOutboundHandler;
import org.drasyl.util.Ansi.Color;

import static org.drasyl.util.Ansi.ansi;

/**
 * Helper class to visualize the {@link Handler} order of a given {@link AbstractPipeline}.
 */
@SuppressWarnings({ "java:S106", "java:S1192", "unused" })
public final class VisualPipeline {
    private static final int MAX_LINE_WIDTH = 76;
    private static final String DIV_LINE = "--------------------------------------------------------------------------------";

    private VisualPipeline() {
        // util class
    }

    /**
     * Prints the current inbound {@link Handler}s in the sequence of execution to the console.
     *
     * @param pipeline the pipeline that should be printed
     * @param mask     the handler mask that should be fulfilled
     */
    public static void printInboundOrder(final AbstractPipeline pipeline, final int mask) {
        AbstractHandlerContext currentContext = pipeline.head.getNext();

        System.out.println("\nInbound Handler Order:");

        printPretty("NETWORK", Color.RED);

        for (; currentContext.getNext() != null;
             currentContext = currentContext.findNextInbound(mask)) {
            printArrow(Color.GREEN);
            printPretty(currentContext.name(), Color.GREEN);
        }

        printArrow(Color.GREEN);
        printPretty("APPLICATION", Color.MAGENTA);
    }

    /**
     * Prints the current outbound {@link SimpleInboundHandler}s in the sequence of execution to the
     * console.
     *
     * @param pipeline the pipeline that should be printed
     * @param mask     the handler mask that should be fulfilled
     */
    public static void printOnlySimpleInboundHandler(final AbstractPipeline pipeline,
                                                     final int mask) {
        AbstractHandlerContext currentContext = pipeline.head;

        System.out.println("\nSimpleInbound Handler Order:");

        printPretty("NETWORK", Color.RED);

        for (;
             currentContext.getNext() != null;
             currentContext = currentContext.findNextInbound(mask)) {
            if (currentContext.handler() instanceof SimpleInboundHandler) {
                printArrow(Color.GREEN);
                printPretty(currentContext.name(), Color.GREEN);
            }
        }

        printArrow(Color.GREEN);
        printPretty("APPLICATION", Color.MAGENTA);
    }

    /**
     * Prints the current outbound {@link Handler}s in the sequence of execution to the console.
     *
     * @param pipeline the pipeline that should be printed
     * @param mask     the handler mask that should be fulfilled
     */
    public static void printOutboundOrder(final AbstractPipeline pipeline, final int mask) {
        AbstractHandlerContext currentContext = pipeline.tail.getPrev();

        System.out.println("\nOutbound Handler Order:");

        printPretty("APPLICATION", Color.MAGENTA);

        for (;
             currentContext.getPrev() != null;
             currentContext = currentContext.findPrevOutbound(mask)) {
            printArrow(Color.BLUE);
            printPretty(currentContext.name(), Color.BLUE);
        }

        printArrow(Color.BLUE);
        printPretty("NETWORK", Color.RED);
    }

    /**
     * Prints the current outbound {@link SimpleOutboundHandler}s in the sequence of execution to
     * the console.
     *
     * @param pipeline the pipeline that should be printed
     * @param mask     the handler mask that should be fulfilled
     */
    public static void printOnlySimpleOutboundHandler(final AbstractPipeline pipeline,
                                                      final int mask) {
        AbstractHandlerContext currentContext = pipeline.tail;

        System.out.println("\nSimpleOutbound Handler Order:");

        printPretty("APPLICATION", Color.MAGENTA);

        for (;
             currentContext.getPrev() != null;
             currentContext = currentContext.findPrevOutbound(mask)) {
            if (currentContext.handler() instanceof SimpleOutboundHandler || currentContext.handler() instanceof SimpleDuplexHandler) {
                printArrow(Color.BLUE);
                printPretty(currentContext.name(), Color.BLUE);
            }
        }

        printArrow(Color.BLUE);
        printPretty("NETWORK", Color.RED);
    }

    private static void printPretty(final String s, final Color color) {
        System.out.println(ansi().color(color).format(DIV_LINE));
        System.out.println(ansi().color(color).format("| %-" + MAX_LINE_WIDTH + "." + MAX_LINE_WIDTH + "s |", s));
        System.out.println(ansi().color(color).format(DIV_LINE));
    }

    private static void printArrow(final Color color) {
        System.out.println(ansi().color(color).format("%41.41s%n", "↓↓↓"));
    }
}
