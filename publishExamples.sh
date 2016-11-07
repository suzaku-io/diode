#!/bin/bash

function copyExample {
  mkdir -p examples/$1
  cp ../examples/$1/target/scala-2.12/*.js examples/$1
  cp ../examples/$1/target/scala-2.12/classes/*.{js,css,png,jpg} examples/$1
  cat ../examples/$1/target/scala-2.12/classes/index.html | sed -re 's/src="\.\.\//src="/' | sed -re 's/-jsdeps./-jsdeps.min./' | sed -re 's/-fastopt/-opt/' | sed -re 's/.*workbench.js.*//' > examples/$1/index.html
}

examples=(raf treeview simple todomvc)

rm -rf _examples
mkdir _examples
cd _examples
for i in "${examples[@]}"
do
   :
   pushd ../examples/$i 
   sbt fullOptJS 
   popd
   copyExample $i
done
exit
