#!/bin/bash
clear
echo ""
echo "This script allows you to build CloudSim Plus Examples and execute some of them."
echo "It requires maven to build all sources and create the JAR packages. Thus, make sure you have it installed."
echo "https://cloudsimplus.org"
echo ""

EXAMPLES_JAR="target/cloudsimplus-examples-*-with-dependencies.jar"

#No parameter was passed to the script. Show the usage help
if [ "$#" -eq 0 ]; then
    echo "Usage:"
    echo "	Build the project: $0 build"
    echo "	Run a specific example: $0 fully.qualified.example.class"
    echo "		The 'example_class' has to be replaced by the fully qualified class name (that includes the package name), for instance:"
    echo "		$0 org.cloudsimplus.examples.BasicFirstExample"
    echo "		If you try to run an example before building the project, it will be built automatically"
    echo ""
    exit 1
fi

#If the build parameter was passed or if the examples jar doesn't exist, build the project
if [ "$1" = "build" ] || [ $(find target/*-with-dependencies.jar | wc -l) -eq 0 ]; then
    echo "Building all modules, running test suits and creating JAR files"
    mvn clean install
fi

#If a parameter was passed and it is not equals to "build",
# it is expected to be a fully-qualified example class name. Thus, try to run that example.
if [ "$#" -eq 1 ] && [ "$1" != "build" ]; then    
	echo "Running the requested example $1:"
    echo "	java -cp $EXAMPLES_JAR $1"
    echo ""
    java -cp $EXAMPLES_JAR "$1"
fi
