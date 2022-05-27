# CloudSim Plus Examples [![Build Status](https://github.com/cloudsimplus/cloudsimplus-examples/actions/workflows/maven.yml/badge.svg)](https://github.com/cloudsimplus/cloudsimplus-examples/actions/workflows/maven.yml)

This module contains several [CloudSim Plus](https://github.com/cloudsimplus/cloudsimplus) examples,
including those inherited from CloudSim and new ones for exclusive features. 
Those later ones are available into the [org.cloudsimplus.examples](src/main/java/org/cloudsimplus/examples) package.
They have more meaningful names, making it easier to get an overview of what an example is about, before even opening it.

To get started you can check the [ReducedExample.java](src/main/java/org/cloudsimplus/examples/ReducedExample.java), 
which shows the minimum code required to build cloud simulations using CloudSim Plus. 
The example places all the required code inside the `main()` method just for convenience, making it easier for new users to understand the code. 

However, that code is not reusable. If you try to build simulations that way, you'll end up with a messy and duplicated code.
Therefore, after you understood how to build basic simulations, you may want to try checking the [BasicFirstExample.java](src/main/java/org/cloudsimplus/examples/BasicFirstExample.java). It's a structured and reusable code that gives a picture of the best ways to write your simulations.
