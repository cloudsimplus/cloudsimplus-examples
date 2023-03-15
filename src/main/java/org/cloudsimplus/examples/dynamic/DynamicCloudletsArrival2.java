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
package org.cloudsimplus.examples.dynamic;

import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerTimeShared;
import org.cloudsimplus.schedulers.vm.VmSchedulerSpaceShared;
import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;

import java.util.ArrayList;
import java.util.List;

/**
 * An example showing how to delay the submission of cloudlets.
 * Although there is enough resources to run all cloudlets simultaneously,
 * the example delays the creation of each cloudlet inside a VM,
 * simulating the dynamic arrival of cloudlets.
 * For each instantiated cloudlet will be defined a different
 * submission delay.
 * Even there is enough resources to run all cloudlets simultaneously,
 * it is used a CloudletSchedulerTimeShared, analyzing the output
 * you can see that each cloudlet starts in a different time,
 * simulating different arrivals.
 *
 * @author Manoel Campos da Silva Filho
 * @since CloudSim Plus 1.0
 */
public class DynamicCloudletsArrival2 {
    /**
     * Number of Processor Elements (CPU Cores) of each Host.
     */
    private static final int HOST_PES_NUMBER = 4;

    /**
     * Number of Processor Elements (CPU Cores) of each VM and cloudlet.
     */
    private static final int VM_PES_NUMBER = HOST_PES_NUMBER;

    /**
     * Number of Cloudlets to create simultaneously.
     * Other cloudlets will be enqueued.
     */
    private static final int NUMBER_OF_CLOUDLETS = VM_PES_NUMBER;

    private final List<Host> hostList;
    private final List<Vm> vmList;
    private final List<Cloudlet> cloudletList;
    private final DatacenterBroker broker;
    private final Datacenter datacenter;
    private final CloudSimPlus simulation;

    /**
     * Starts the example execution, calling the class constructor\
     * to build and run the simulation.
     *
     * @param args command line parameters
     */
    public static void main(String[] args) {
        new DynamicCloudletsArrival2();
    }

    /**
     * Default constructor that builds and starts the simulation.
     */
    private DynamicCloudletsArrival2() {
        /*Enables just some level of log messages.
          Make sure to import org.cloudsimplus.util.Log;*/
        //Log.setLevel(ch.qos.logback.classic.Level.WARN);

        System.out.println("Starting " + getClass().getSimpleName());
        simulation = new CloudSimPlus();

        this.hostList = new ArrayList<>();
        this.vmList = new ArrayList<>();
        this.cloudletList = new ArrayList<>();
        this.datacenter = createDatacenter();
        this.broker = new DatacenterBrokerSimple(simulation);

        createAndSubmitVmAndCloudlets();

        runSimulationAndPrintResults();
        System.out.println(getClass().getSimpleName() + " finished!");
    }

    private void runSimulationAndPrintResults() {
        simulation.start();
        final var cloudletFinishedList = broker.getCloudletFinishedList();
        new CloudletsTableBuilder(cloudletFinishedList).build();
    }

    /**
     * Creates cloudlets and submit them to the broker, applying
     * a different submission delay for each one (simulating the dynamic cloudlet arrival).
     *
     * @param vm Vm to run the cloudlets to be created
     *
     * @see #createCloudlet(int, Vm, DatacenterBroker)
     */
    private void createAndSubmitCloudlets(Vm vm) {
        int cloudletId = cloudletList.size();
        double submissionDelay = 0;
        final var newCloudletList = new ArrayList<Cloudlet>(NUMBER_OF_CLOUDLETS);
        for(int i = 0; i < NUMBER_OF_CLOUDLETS; i++){
            final var cloudlet = createCloudlet(cloudletId++, vm, broker);
            cloudlet.setSubmissionDelay(submissionDelay);
            submissionDelay += 10;
            newCloudletList.add(cloudlet);
        }

        broker.submitCloudletList(newCloudletList);
        cloudletList.addAll(newCloudletList);
    }

    /**
     * Creates one Vm and a group of cloudlets to run inside it,
     * and submit the Vm and its cloudlets to the broker.
     *
     * @see #createVm(int, DatacenterBroker)
     */
    private void createAndSubmitVmAndCloudlets() {
        final var newVmList = new ArrayList<Vm>();
        final var vm = createVm(this.vmList.size(), broker);
        newVmList.add(vm);

        broker.submitVmList(newVmList);
        this.vmList.addAll(newVmList);

        createAndSubmitCloudlets(vm);
    }

    /**
     * Creates a VM with pre-defined configuration.
     *
     * @param id the VM id
     * @param broker the broker that will be submit the VM
     * @return the created VM
     *
     */
    private Vm createVm(int id, DatacenterBroker broker) {
        final int mips = 1000;
        final long size = 10000; // image size (Megabyte)
        final int ram = 512; // vm memory (Megabyte)
        final long bw = 1000;

        return new VmSimple(id, mips, VM_PES_NUMBER)
                .setRam(ram).setBw(bw).setSize(size)
                .setCloudletScheduler(new CloudletSchedulerTimeShared());
    }

    /**
     * Creates a cloudlet with pre-defined configuration.
     *
     * @param id Cloudlet id
     * @param vm vm to run the cloudlet
     * @param broker the broker that will submit the cloudlets
     * @return the created cloudlet
     */
    private Cloudlet createCloudlet(int id, Vm vm, DatacenterBroker broker) {
        final long fileSize = 300;
        final long outputSize = 300;
        final long length = 10000; //in number of Million Instructions (MI)
        final int pesNumber = 1;

        return new CloudletSimple(id, length, pesNumber)
            .setFileSize(fileSize)
            .setOutputSize(outputSize)
            .setUtilizationModelCpu(new UtilizationModelFull())
            .setUtilizationModelRam(new UtilizationModelDynamic(0.1))
            .setUtilizationModelBw(new UtilizationModelDynamic(0.3))
            .setVm(vm);
    }

    /**
     * Creates a Datacenter with pre-defined configuration.
     *
     * @return the created Datacenter
     */
    private Datacenter createDatacenter() {
        final var host = createHost(0);
        hostList.add(host);

        return new DatacenterSimple(simulation, hostList);
    }

    /**
     * Creates a host with pre-defined configuration.
     *
     * @param id The Host id
     * @return the created host
     */
    private Host createHost(int id) {
        final var peList = new ArrayList<Pe>();
        final long mips = 1000;

        for(int i = 0; i < HOST_PES_NUMBER; i++){
            peList.add(new PeSimple(mips));
        }

        final long ram = 2048; // in Megabytes
        final long storage = 1000000; // in Megabytes
        final long bw = 10000; //in Megabits/s

        return new HostSimple(ram, bw, storage, peList).setVmScheduler(new VmSchedulerSpaceShared());
    }
}
