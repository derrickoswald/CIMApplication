#!/bin/bash
while read line; do
    export FILE=${line/$'\r'/};
    ulimit -Sn `ulimit -Hn`;
    pushd $1/$FILE > /dev/null;
    gridlabd.exe $FILE.glm 2> $FILE.out;
    cat output_data/* > output.txt;
    echo -n "$FILE|";
    cat $FILE.out | tr '\r\n' '|';
    popd > /dev/null;
done