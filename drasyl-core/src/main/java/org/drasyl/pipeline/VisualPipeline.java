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

/**
 * Helper class to visualize the {@link Handler} order of a given {@link DefaultPipeline}.
 */
@SuppressWarnings({ "java:S106" })
public class VisualPipeline {
    private static final int MAX_LINE_WIDTH = 76;
    private static final String DIV_LINE = "--------------------------------------------------------------------------------";

    private VisualPipeline() {
    }

    /**
     * Prints the current inbound {@link Handler}s in the sequence of execution to the console.
     *
     * @param pipeline the pipeline that should be printed
     */
    public static void printInboundOrder(final DefaultPipeline pipeline) {
        AbstractHandlerContext currentContext = pipeline.head.getNext();

        System.out.println("\nInbound Handler Order:");

        for (boolean first = true;
             currentContext.getNext() != null;
             currentContext = currentContext.findNextInbound(),
                     first = false) {
            if (!first) {
                printArrow();
            }

            printPretty(currentContext.name());
        }
    }

    /**
     * Prints the current outbound {@link SimpleOutboundHandler}s in the sequence of execution to
     * the console.
     *
     * @param pipeline the pipeline that should be printed
     */
    public static void printOnlySimpleInboundHandler(final DefaultPipeline pipeline) {
        AbstractHandlerContext currentContext = pipeline.head;

        System.out.println("\nSimpleInbound Handler Order:");

        for (;
             currentContext.getNext() != null;
             currentContext = currentContext.findNextInbound()) {
            if (currentContext.handler() instanceof SimpleInboundHandler) {
                printArrow();
                printPretty(currentContext.name());
            }
        }
    }

    /**
     * Prints the current outbound {@link Handler}s in the sequence of execution to the console.
     *
     * @param pipeline the pipeline that should be printed
     */
    public static void printOutboundOrder(final DefaultPipeline pipeline) {
        AbstractHandlerContext currentContext = pipeline.tail.getPrev();

        System.out.println("\nOutbound Handler Order:");

        for (boolean first = true;
             currentContext.getPrev() != null;
             currentContext = currentContext.findPrevOutbound(), first = false) {
            if (!first) {
                printArrow();
            }

            printPretty(currentContext.name());
        }
    }

    /**
     * Prints the current outbound {@link SimpleOutboundHandler}s in the sequence of execution to
     * the console.
     *
     * @param pipeline the pipeline that should be printed
     */
    public static void printOnlySimpleOutboundHandler(final DefaultPipeline pipeline) {
        AbstractHandlerContext currentContext = pipeline.tail;

        System.out.println("\nSimpleOutbound Handler Order:");

        for (;
             currentContext.getPrev() != null;
             currentContext = currentContext.findPrevOutbound()) {
            if (currentContext.handler() instanceof SimpleOutboundHandler || currentContext.handler() instanceof SimpleDuplexHandler) {
                printArrow();
                printPretty(currentContext.name());
            }
        }
    }

    private static void printPretty(final String s) {
        System.out.println(DIV_LINE);
        System.out.println("| " + String.format("%-" + MAX_LINE_WIDTH + "." + MAX_LINE_WIDTH + "s", s) + " |");
        System.out.println(DIV_LINE);
    }

    private static void printArrow() {
        System.out.printf("%41.41s%n", "↓↓↓");
    }
}
