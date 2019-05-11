#!/bin/bash

function copyExample {
  mkdir -p examples/$1 examples/$1/classes examples/$1/scalajs-bundler/main
  cp ../examples/$1/target/scala-2.12/*.js examples/$1
  cp ../examples/$1/target/scala-2.12/classes/*.{js,css,png,jpg} examples/$1/classes
  cp ../examples/$1/target/scala-2.12/scalajs-bundler/main/*opt-bundle.js examples/$1/scalajs-bundler/main
  # Modify index.html to work online
  cat ../examples/$1/target/scala-2.12/classes/index.html | sed -re 's/src="\.\.\//src="/' | sed -re 's/-jsdeps./-jsdeps.min./' | sed -re 's/-fastopt/-opt/' | sed -re 's/.*workbench.js.*//' > examples/$1/index.html
}

examples=(raf treeview simple)

rm -rf _examples
mkdir _examples
cd _examples
for i in "${examples[@]}"
do
   :
   pushd ../examples/$i 
   sbt clean fullOptJS
   popd
   copyExample $i
done

pushd ../examples/todomvc
sbt clean fullOptJS::webpack
popd
copyExample todomvc

exit
