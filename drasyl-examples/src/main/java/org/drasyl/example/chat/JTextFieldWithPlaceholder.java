/*
 * Copyright (c) 2020-2021.
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
package org.drasyl.example.chat;

import javax.swing.JTextField;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

@SuppressWarnings({ "java:S110" })
public class JTextFieldWithPlaceholder extends JTextField {
    private final String placeholder;

    public JTextFieldWithPlaceholder(final int columns, final String placeholder) {
        super(columns);
        this.placeholder = placeholder;
    }

    public JTextFieldWithPlaceholder(final String placeholder) {
        this(0, placeholder);
    }

    @Override
    public void paintComponent(final Graphics g) {
        super.paintComponent(g);

        if (super.getText().length() > 0 || placeholder == null) {
            return;
        }

        final Graphics2D g2 = (Graphics2D) g;

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(super.getDisabledTextColor());
        g2.drawString(placeholder, getInsets().left, g.getFontMetrics().getMaxAscent() + getInsets().top);
    }
}
