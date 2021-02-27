#!/bin/bash
#
# Copyright (c) 2020-2021.
#
# This file is part of drasyl.
#
#  drasyl is free software: you can redistribute it and/or modify
#  it under the terms of the GNU Lesser General Public License as published by
#  the Free Software Foundation, either version 3 of the License, or
#  (at your option) any later version.
#
#  drasyl is distributed in the hope that it will be useful,
#  but WITHOUT ANY WARRANTY; without even the implied warranty of
#  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
#  GNU Lesser General Public License for more details.
#
#  You should have received a copy of the GNU Lesser General Public License
#  along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
#

# sum csv values and output as:
# 10 / 20  instructions covered
# 50,00 % covered
awk -F"," '{ instructions += $4 + $5; covered += $5 } END { print covered, "/", instructions, " instructions covered"; print 100*covered/instructions, "% covered" }' drasyl-all/target/site/jacoco-aggregate/jacoco.csv
