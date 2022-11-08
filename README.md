# CloudSim Plus Examples [![Build Status](https://github.com/cloudsimplus/cloudsimplus-examples/actions/workflows/maven.yml/badge.svg)](https://github.com/cloudsimplus/cloudsimplus-examples/actions/workflows/maven.yml)  
[![Open in Gitpod](https://gitpod.io/button/open-in-gitpod.svg)](https://gitpod.io/#https://github.com/cloudsimplus/cloudsimplus-examples)


This module contains several [CloudSim Plus](https://github.com/cloudsimplus/cloudsimplus) examples,
including those inherited from CloudSim and new ones for exclusive features. 
Those later ones are available into the [org.cloudsimplus.examples](src/main/java/org/cloudsimplus/examples) package.
They have more meaningful names, making it easier to get an overview of what an example is about, before even opening it.

To get started you can check the [ReducedExample.java](src/main/java/org/cloudsimplus/examples/ReducedExample.java), 
which shows the minimum code required to build cloud simulations using CloudSim Plus. 
The example places all the required code inside the `main()` method just for convenience, making it easier for new users to understand the code. 

However, that code is not reusable. If you try to build simulations that way, you'll end up with a messy and duplicated code.
Therefore, after you understood how to build basic simulations, you may want to try checking the [BasicFirstExample.java](src/main/java/org/cloudsimplus/examples/BasicFirstExample.java). It's a structured and reusable code that gives a picture of the best ways to write your simulations.

## 1. Downloading the project

You can download this project using (i) the download button on top of this page (ii) your own IDE for it or (iii) the command line as described below.

### Via Command Line

Considering that you have [git](https://git-scm.com) installed on your operating system, 
download the project source by cloning the repository issuing the following command at a terminal:

```bash
git clone https://github.com/cloudsimplus/cloudsimplus-examples.git
```

## 2 Running Examples

> **Warning**
> Before trying to use this project, make sure you have JDK 17 installed.

There are 2 ways to run the examples in this project, as presented below.

### By Means of an IDE

The easiest way to run the examples is relying on some IDE.
Below are required steps:

- Open/import the project in your IDE:
    - For NetBeans, just use the "Open project" menu and select the directory where the project was downloaded/cloned. Check a [NetBeans tutorial here](https://youtu.be/k2enNoxTYVw).
    - For Eclipse or IntelliJ IDEA, 
      you have to import the project selecting the folder where the project was cloned. 
      Check an [Eclipse tutorial here](https://youtu.be/oO-a5-cZBps).
- The most basic examples are in the root of the org.cloudbus.cloudsim.examples package. 
  You can run any one of the classes in this package to get a specific example. 
- If you want to build your own simulations, the easiest way is to create another class inside this project.

### Using the bootstrap.sh script

The project has a [bootstrap.sh](bootstrap.sh) script you can use to build and run CloudSim Plus examples. 
This is a script for Unix-like systems such as Linux, FreeBSD and macOS.

To run some example, type the following command at a terminal inside the project's root directory: `sh bootstrap.sh package.ExampleClassName`.
For instance, to run the `BasicFirstExample` you can type: `sh bootstrap.sh org.cloudsimplus.examples.BasicFirstExample`. 

The script checks if it is required to build the project, using maven in this case, making sure to download all dependencies. 
To see which examples are available, just navigate through the [src/main/java](src/main/java) directory.
To check more script options, run it without any parameter.  