/*
 * CloudSim Plus: A modern, highly-extensible and easier-to-use Framework for
 * Modeling and Simulation of Cloud Computing Infrastructures and Services.
 * http://cloudsimplus.org
 *
 *     Copyright (C) 2015-2021 Universidade da Beira Interior (UBI, Portugal) and
 *     the Instituto Federal de Educação Ciência e Tecnologia do Tocantins (IFTO, Brazil).
 *
 *     This file is part of CloudSim Plus.
 *
 *     CloudSim Plus is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     CloudSim Plus is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with CloudSim Plus. If not, see <http://www.gnu.org/licenses/>.
 */
package org.cloudsimplus.examples.brokers;

import ch.qos.logback.classic.Level;
import org.cloudsimplus.allocationpolicies.VmAllocationPolicySimple;
import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.brokers.DatacenterBrokerBestFit;
import org.cloudsimplus.brokers.DatacenterBrokerHeuristic;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.core.Simulation;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.distributions.ContinuousDistribution;
import org.cloudsimplus.distributions.UniformDistr;
import org.cloudsimplus.heuristics.CloudletToVmMappingHeuristic;
import org.cloudsimplus.heuristics.CloudletToVmMappingSimulatedAnnealing;
import org.cloudsimplus.heuristics.CloudletToVmMappingSolution;
import org.cloudsimplus.heuristics.HeuristicSolution;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.provisioners.ResourceProvisionerSimple;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerTimeShared;
import org.cloudsimplus.schedulers.vm.VmSchedulerTimeShared;
import org.cloudsimplus.util.Log;
import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static java.util.Map.Entry;

/**
 * An example that uses a
 * <a href="http://en.wikipedia.org/wiki/Simulated_annealing">Simulated Annealing</a>
 * heuristic to find a suboptimal mapping between Cloudlets and Vm's submitted to a
 * DatacenterBroker. The number of {@link Pe}s of Vm's and Cloudlets are defined
 * randomly.
 *
 * <p>The {@link DatacenterBrokerHeuristic} is used
 * with the {@link CloudletToVmMappingSimulatedAnnealing} class
 * in order to find an acceptable solution with a high
 * {@link HeuristicSolution#getFitness() fitness value}.</p>
 *
 * <p>Different {@link CloudletToVmMappingHeuristic} implementations can be used
 * with the {@link DatacenterBrokerHeuristic} class.</p>
 *
 * <p>A comparison of cloudlet-VM mapping is done among the best fit approach,
 * heuristic approach and round robin mapping.</p>
 *
 * @author Humaira Abdul Salam
 * @author Manoel Campos da Silva Filho
 * @since CloudSim Plus 4.3.8
 */
public class DatacenterBrokersMappingComparison {
    /**
     * Simulated Annealing (SA) parameters.
     */
    public static final double SA_INITIAL_TEMPERATURE = 1.0;
    public static final double SA_COLD_TEMPERATURE = 0.0001;
    public static final double SA_COOLING_RATE = 0.003;
    public static final int    SA_NUMBER_OF_NEIGHBORHOOD_SEARCHES = 50;

    private static final int HOSTS_TO_CREATE = 100;
    private static final int VMS_TO_CREATE = 50;
    private static final int CLOUDLETS_TO_CREATE = 100;
    private final Simulation simulation;
    private final List<Cloudlet> cloudletList;
    private final ContinuousDistribution random;
    private final List<Vm> vmList;
    private DatacenterBroker broker;

    public static void main(String[] args) {
        //Enables just some level of log messages.
        Log.setLevel(Level.WARN);

        System.out.println("Starting comparison...");

        final long seed = System.currentTimeMillis();
        final boolean verbose = false;

        // Heuristic
        final var simulation0 = new CloudSimPlus();
        final var random0 = new UniformDistr(0, 1, seed);
        final var broker0 = createDatacenterBrokerHeuristic(simulation0, random0);
        new DatacenterBrokersMappingComparison(broker0, random0, verbose);

        // BestFit
        final var simulation1 = new CloudSimPlus();
        final var random1 = new UniformDistr(0, 1, seed);
        final var broker1 = new DatacenterBrokerBestFit(simulation1);
        new DatacenterBrokersMappingComparison(broker1, random1, verbose);

        // Simple - RoundRobin
        final var simulation2 = new CloudSimPlus();
        final var random2 = new UniformDistr(0, 1, seed);
        final var broker2 = new DatacenterBrokerSimple(simulation2);
        new DatacenterBrokersMappingComparison(broker2, random2, verbose);

        System.out.println("Comparison finished!");
    }

    /**
     * Default constructor where the simulation is built.
     */
    private DatacenterBrokersMappingComparison(final DatacenterBroker broker, final ContinuousDistribution random, final boolean verbose) {
        this.broker = broker;
        this.simulation = broker.getSimulation();
        this.random = random;

        final var datacenter = createDatacenter(simulation);

        vmList = createVms(random);
        cloudletList = createCloudlets(random);
        broker.submitVmList(vmList);
        broker.submitCloudletList(cloudletList);

        simulation.start();

        // print simulation results
        if (verbose) {
            final var cloudletFinishedList = broker.getCloudletFinishedList();
            cloudletFinishedList.sort(Comparator.comparingLong(Cloudlet::getId));
            new CloudletsTableBuilder(cloudletFinishedList).build();
        }

        print(verbose);
    }

    private static DatacenterBrokerHeuristic createDatacenterBrokerHeuristic(final CloudSimPlus sim, final ContinuousDistribution rand) {
        final var heuristic = createSimulatedAnnealingHeuristic(rand);
        final var broker = new DatacenterBrokerHeuristic(sim);
        broker.setHeuristic(heuristic);
        return broker;
    }

    private static CloudletToVmMappingSimulatedAnnealing createSimulatedAnnealingHeuristic(final ContinuousDistribution rand) {
        final var heuristic = new CloudletToVmMappingSimulatedAnnealing(SA_INITIAL_TEMPERATURE, rand);
        heuristic.setColdTemperature(SA_COLD_TEMPERATURE)
                 .setCoolingRate(SA_COOLING_RATE)
                 .setSearchesByIteration(SA_NUMBER_OF_NEIGHBORHOOD_SEARCHES);
        return heuristic;
    }

    private List<Cloudlet> createCloudlets(final ContinuousDistribution rand) {
        final var cloudletList = new ArrayList<Cloudlet>(CLOUDLETS_TO_CREATE);
        for (int i = 0; i < CLOUDLETS_TO_CREATE; i++) {
            cloudletList.add(createCloudlet(i, getRandomPesNumber(4, rand)));
        }

        return cloudletList;
    }

    private List<Vm> createVms(final ContinuousDistribution random) {
        final var vmList = new ArrayList<Vm>(VMS_TO_CREATE);
        for (int i = 0; i < VMS_TO_CREATE; i++) {
            vmList.add(createVm(i, getRandomPesNumber(4, random)));
        }

        return vmList;
    }

    private void print(final boolean verbose) {
        final double brokersMappingCost = computeBrokersMappingCost(verbose);
        System.out.printf(
            "The solution based on %s mapper costs %.2f.%n", broker.getClass().getSimpleName(), brokersMappingCost);
    }

    /**
     * Randomly gets a number of PEs (CPU cores).
     *
     * @param maxPesNumber the maximum value to get a random number of PEs
     * @return the randomly generated PEs number
     */
    private int getRandomPesNumber(final int maxPesNumber, final ContinuousDistribution random) {
        final double uniform = random.sample();

        /*always get an index between [0 and size[,
        regardless if the random number generator returns
        values between [0 and 1[ or >= 1*/
        return (int) (uniform >= 1 ? uniform % maxPesNumber : uniform * maxPesNumber) + 1;
    }

    private DatacenterSimple createDatacenter(final Simulation sim) {
        final var hostList = new ArrayList<Host>();
        for (int i = 0; i < HOSTS_TO_CREATE; i++) {
            hostList.add(createHost());
        }

        return new DatacenterSimple(sim, hostList, new VmAllocationPolicySimple());
    }

    private Host createHost() {
        final long mips = 1000; // capacity of each CPU core (in Million Instructions per Second)
        final int ram = 2048; // host memory (Megabyte)
        final long storage = 1000000; // host storage
        final long bw = 10000;

        final var peList = new ArrayList<Pe>();
        /*Creates the Host's CPU cores and defines the provisioner
        used to allocate each core for requesting VMs.*/
        for (int i = 0; i < 8; i++)
            peList.add(new PeSimple(mips));

        return new HostSimple(ram, bw, storage, peList)
            .setRamProvisioner(new ResourceProvisionerSimple())
            .setBwProvisioner(new ResourceProvisionerSimple())
            .setVmScheduler(new VmSchedulerTimeShared());
    }

    private Vm createVm(final long id, final int pesNumber) {
        final long mips = 1000;
        final long storage = 10000; // vm image size (Megabyte)
        final int ram = 512; // vm memory (Megabyte)
        final long bw = 1000; // vm bandwidth

        return new VmSimple(id, mips, pesNumber)
            .setRam(ram).setBw(bw).setSize(storage)
            .setCloudletScheduler(new CloudletSchedulerTimeShared());
    }

    private Cloudlet createCloudlet(final long id, final int pesNumber) {
        final long length = 400000; //in Million Instructions (MI)
        final long fileSize = 300; //Size (in bytes) before execution
        final long outputSize = 300; //Size (in bytes) after execution

        //Defines how RAM and Bandwidth resources are used
        final var ramAndBwUtilizationModel = new UtilizationModelDynamic(0.1);

        return new CloudletSimple(id, length, pesNumber)
            .setFileSize(fileSize)
            .setOutputSize(outputSize)
            .setUtilizationModelCpu(new UtilizationModelFull())
            .setUtilizationModelBw(ramAndBwUtilizationModel)
            .setUtilizationModelRam(ramAndBwUtilizationModel);
    }

    private double computeBrokersMappingCost(final boolean doPrint) {
        final var heuristic = new CloudletToVmMappingSimulatedAnnealing(SA_INITIAL_TEMPERATURE, random);
        final var mappingSolution = new CloudletToVmMappingSolution(heuristic);
        for (Cloudlet c : cloudletList) {
            if (c.isBoundToVm()) {
                mappingSolution.bindCloudletToVm(c, c.getVm());
            }
        }

        if (doPrint) {
            printSolution(
                "Best fit solution used by DatacenterBrokerSimple class",
                mappingSolution, false);
        }
        return mappingSolution.getCost();
    }

    private void printSolution(
        final String title,
        final CloudletToVmMappingSolution solution,
        final boolean showIndividualCloudletFitness)
    {
        System.out.printf(
                "%s (cost %.2f fitness %.6f)%n",
                title, solution.getCost(), solution.getFitness());

        if (!showIndividualCloudletFitness)
            return;

        for (Entry<Cloudlet, Vm> e : solution.getResult().entrySet()) {
            System.out.printf(
                "Cloudlet %3d (%d PEs, %6d MI) mapped to Vm %3d (%d PEs, %6.0f MIPS)%n",
                e.getKey().getId(),
                e.getKey().getPesNumber(), e.getKey().getLength(),
                e.getValue().getId(),
                e.getValue().getPesNumber(), e.getValue().getMips());
        }

        System.out.println();
    }
}
