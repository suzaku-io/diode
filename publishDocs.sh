#!/bin/bash

gitbook build doc _book
cd _book
cp -r ../_examples/examples/* examples/
git init
git commit --allow-empty -m 'update book'
git checkout -b gh-pages
touch .nojekyll
git add .
git commit -am "update book"
git push git@github.com:suzaku-io/diode gh-pages --force
cd ..
