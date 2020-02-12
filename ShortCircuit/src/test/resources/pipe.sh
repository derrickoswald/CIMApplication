#!/bin/bash
while read line; do
    export FILE=${line/$'\r'/};
    ulimit -Sn `ulimit -Hn`;
    pushd $1/$FILE > /dev/null;
    gridlabd.exe $FILE.glm 2>&1 | awk '{print ENVIRON["FILE"] " " $0}' > $FILE.out;
    cat output_data/* > output.txt;
    cat $FILE.out;
    popd > /dev/null;
done