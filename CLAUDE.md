# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Ready-to-run examples for the [CloudSim Plus](https://github.com/cloudsimplus/cloudsimplus) cloud simulation framework. It is a Java 25 Maven project (`maven.compiler.release=25`). Every class with a `main` method is a separate example. There is no library code and there are no tests (`src/test` does not exist).

## Dependency on CloudSim Plus

- The **project version sets the CloudSim Plus version**: the `cloudsimplus` dependency uses `${project.version}` (now `9.0.0-snapshot`). So the version must always be a real CloudSim Plus version.
- A snapshot version is not on Maven Central. It must be installed into `~/.m2` from the sibling framework project `../cloudsimplus` (`cd ../cloudsimplus && ./mvnw install`). If the build fails on missing or out-of-date framework classes, rebuild `../cloudsimplus` first.
- Logging uses SLF4J + Logback. Examples can lower verbosity with `Log.setLevel(ch.qos.logback.classic.Level.WARN)` (`org.cloudsimplus.util.Log`).

## Commands

```bash
./mvnw clean install                              # build; also creates target/cloudsimplus-examples-<ver>-with-dependencies.jar (shade plugin)
./mvnw -B package                                 # what CI runs (.github/workflows/maven.yml, JDK 25)
./mvnw dependency:resolve -Dclassifier=javadoc    # optional: download CloudSim Plus javadocs for the IDE
```

Run one example (the exec plugin is not configured; use the shaded jar):

```bash
java -cp target/cloudsimplus-examples-*-with-dependencies.jar org.cloudsimplus.examples.BasicFirstExample
sh bootstrap.sh org.cloudsimplus.examples.BasicFirstExample   # builds first if the shaded jar is missing
```

The `license-maven-plugin` (`mvn license:check` / `license:format`) expects a header file at `../COPYRIGHT`, which is not in this workspace.

## Architecture

- `org.cloudsimplus.examples` (root): basic examples. `ReducedExample` shows the minimum code, all in `main()`. `BasicFirstExample` is the reference structure for new examples.
- Sub-packages group examples by feature: `autoscaling`, `brokers`, `costs`, `dynamic`, `listeners`, `migration`, `performance`, `power`, `resourceusage`, `schedulers`, `simulationstatus`, `sla`, `synchronous`, `traces` (incl. `traces.google`). Some have a `package-info.java` describing them.
- `org.cloudbus.cloudsim.examples.network`: examples inherited from CloudSim. They are excluded from license header checks. `NetworkVmExampleAbstract` is one of the few shared base classes.
- Examples are mostly self-contained. There is no common helper module, and examples seldom import each other (a few reuse `BasicFirstExample` or `VmsRamAndBwUsageExample`).

Common structure of an example (see `BasicFirstExample`):

1. `private static final` constants for host, VM and cloudlet sizes (MIPS, PEs, RAM in MB, BW in Mbps, storage in MB, length in MI).
2. Constructor: `new CloudSimPlus()`, then `createDatacenter()` (`DatacenterSimple` with `HostSimple`s built from `PeSimple`s), then `new DatacenterBrokerSimple(simulation)`.
3. `createVms()` (`VmSimple`) and `createCloudlets()` (`CloudletSimple` + a `UtilizationModel*`), then `broker.submitVmList(...)` and `broker.submitCloudletList(...)`.
4. Optionally register event listeners (e.g. `simulation.addOnClockTickListener`, `vm.addOnHostAllocationListener`) for dynamic behaviour.
5. `simulation.start()`, then print results with `new CloudletsTableBuilder(broker.getCloudletFinishedList()).build()`.

Default policies (VM allocation, VM/cloudlet schedulers, provisioners) apply unless an example sets them on purpose. Usually that choice is the point of the example.

## Resources

`src/main/resources` holds input data. Examples load it from the classpath by relative path (e.g. `"workload/swf/NASA-iPSC-1993-3.1-cln.swf.gz"`, `"power-specs/..."`): Google cluster trace samples, PlanetLab and SWF workloads, SPEC power model files, `topology.brite` (network topology), and `CustomerSLA.json` (SLA examples).
