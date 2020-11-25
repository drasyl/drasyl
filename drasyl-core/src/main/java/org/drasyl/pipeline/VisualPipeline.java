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
package org.drasyl.pipeline;

import org.drasyl.pipeline.skeleton.SimpleDuplexHandler;
import org.drasyl.pipeline.skeleton.SimpleInboundHandler;
import org.drasyl.pipeline.skeleton.SimpleOutboundHandler;
import org.drasyl.util.AnsiColor;

/**
 * Helper class to visualize the {@link Handler} order of a given {@link DefaultPipeline}.
 */
@SuppressWarnings({ "java:S106", "java:S1192" })
public class VisualPipeline {
    private static final int MAX_LINE_WIDTH = 76;
    private static final String DIV_LINE = "--------------------------------------------------------------------------------";

    private VisualPipeline() {
    }

    /**
     * Prints the current inbound {@link Handler}s in the sequence of execution to the console.
     *
     * @param pipeline the pipeline that should be printed
     * @param mask     the handler mask that should be fulfilled
     */
    public static void printInboundOrder(final DefaultPipeline pipeline, final int mask) {
        AbstractHandlerContext currentContext = pipeline.head.getNext();

        System.out.println("\nInbound Handler Order:");

        printPretty("NETWORK", AnsiColor.COLOR_RED);

        for (; currentContext.getNext() != null;
             currentContext = currentContext.findNextInbound(mask)) {
            printArrow(AnsiColor.COLOR_GREEN);
            printPretty(currentContext.name(), AnsiColor.COLOR_GREEN);
        }

        printArrow(AnsiColor.COLOR_GREEN);
        printPretty("APPLICATION", AnsiColor.COLOR_PURPLE);
    }

    /**
     * Prints the current outbound {@link SimpleInboundHandler}s in the sequence of execution to the
     * console.
     *
     * @param pipeline the pipeline that should be printed
     * @param mask     the handler mask that should be fulfilled
     */
    public static void printOnlySimpleInboundHandler(final DefaultPipeline pipeline,
                                                     final int mask) {
        AbstractHandlerContext currentContext = pipeline.head;

        System.out.println("\nSimpleInbound Handler Order:");

        printPretty("NETWORK", AnsiColor.COLOR_RED);

        for (;
             currentContext.getNext() != null;
             currentContext = currentContext.findNextInbound(mask)) {
            if (currentContext.handler() instanceof SimpleInboundHandler) {
                printArrow(AnsiColor.COLOR_GREEN);
                printPretty(currentContext.name(), AnsiColor.COLOR_GREEN);
            }
        }

        printArrow(AnsiColor.COLOR_GREEN);
        printPretty("APPLICATION", AnsiColor.COLOR_PURPLE);
    }

    /**
     * Prints the current outbound {@link Handler}s in the sequence of execution to the console.
     *
     * @param pipeline the pipeline that should be printed
     * @param mask     the handler mask that should be fulfilled
     */
    public static void printOutboundOrder(final DefaultPipeline pipeline, final int mask) {
        AbstractHandlerContext currentContext = pipeline.tail.getPrev();

        System.out.println("\nOutbound Handler Order:");

        printPretty("APPLICATION", AnsiColor.COLOR_PURPLE);

        for (;
             currentContext.getPrev() != null;
             currentContext = currentContext.findPrevOutbound(mask)) {
            printArrow(AnsiColor.COLOR_BLUE);
            printPretty(currentContext.name(), AnsiColor.COLOR_BLUE);
        }

        printArrow(AnsiColor.COLOR_BLUE);
        printPretty("NETWORK", AnsiColor.COLOR_RED);
    }

    /**
     * Prints the current outbound {@link SimpleOutboundHandler}s in the sequence of execution to
     * the console.
     *
     * @param pipeline the pipeline that should be printed
     * @param mask     the handler mask that should be fulfilled
     */
    public static void printOnlySimpleOutboundHandler(final DefaultPipeline pipeline,
                                                      final int mask) {
        AbstractHandlerContext currentContext = pipeline.tail;

        System.out.println("\nSimpleOutbound Handler Order:");

        printPretty("APPLICATION", AnsiColor.COLOR_PURPLE);

        for (;
             currentContext.getPrev() != null;
             currentContext = currentContext.findPrevOutbound(mask)) {
            if (currentContext.handler() instanceof SimpleOutboundHandler || currentContext.handler() instanceof SimpleDuplexHandler) {
                printArrow(AnsiColor.COLOR_BLUE);
                printPretty(currentContext.name(), AnsiColor.COLOR_BLUE);
            }
        }

        printArrow(AnsiColor.COLOR_BLUE);
        printPretty("NETWORK", AnsiColor.COLOR_RED);
    }

    private static void printPretty(final String s, final AnsiColor color) {
        System.out.println(color.getColor() + DIV_LINE + AnsiColor.COLOR_RESET.getColor());
        System.out.println(color.getColor() + "| " + String.format("%-" + MAX_LINE_WIDTH + "." + MAX_LINE_WIDTH + "s", s) + " |" + AnsiColor.COLOR_RESET.getColor());
        System.out.println(color.getColor() + DIV_LINE + AnsiColor.COLOR_RESET.getColor());
    }

    private static void printArrow(final AnsiColor color) {
        System.out.print(color.getColor() + String.format("%41.41s%n", "↓↓↓") + AnsiColor.COLOR_RESET.getColor());
    }
}
