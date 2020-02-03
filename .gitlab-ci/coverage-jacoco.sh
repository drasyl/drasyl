#!/bin/bash


# Collect all CSV files in one string
inputs=""
for D in *; do
    if [ -d "${D}" ]; then
        jac="$D/target/site/jacoco/jacoco.csv"
        if [ -f "$jac" ]; then
            inputs="$inputs $jac"
        fi
    fi
done

# sum csv values and output as:
# 10 / 20  instructions covered
# 50,00 % covered
awk -F"," '{ instructions += $4 + $5; covered += $5 } END { print covered, "/", instructions, " instructions covered"; print 100*covered/instructions, "% covered" }' $inputs

