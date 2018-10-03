#!/bin/bash
while read line; do
    export FILE=${line/$'\r'/};
    pushd simulation/$FILE > /dev/null;
    gridlabd $FILE.glm 2>&1 | awk '{print ENVIRON["FILE"] " " $0}' > $FILE.out;
    cat output_data/* > output.txt;
    cat $FILE.out;
    popd > /dev/null;
done