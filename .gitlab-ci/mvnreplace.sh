#!/bin/bash



artreplace() {
    sed "s/${1}/${2}/g" $3 > temp
    mv temp $3
}

artifactId=$(cat pom.xml | grep -m1 "</artifactId>" | sed  -e 's/<artifactId>\|<\/artifactId>//g'| tr -d ' ')
artifactId=$(echo $artifactId|cut -d'>' -f 2)
artifactId=$(echo $artifactId|cut -d'<' -f 1)

branchname=$(echo $1 | sed -e 's/\//_/g')
branchname="$artifactId-$branchname"
artreplace $artifactId $branchname pom.xml

for D in *; do
    if [ -d "${D}" ]; then
        POM=$(echo "$D/pom.xml")  
        if [ -f "$POM" ]; then
            artreplace $artifactId $branchname $POM
        fi
    fi
done

