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
package org.cloudsimplus.examples.listeners;

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
import org.cloudsimplus.listeners.CloudletVmEventInfo;
import org.cloudsimplus.listeners.EventListener;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerSpaceShared;
import org.cloudsimplus.schedulers.vm.VmSchedulerTimeShared;
import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;

import java.util.ArrayList;
import java.util.List;

/**
 * A simple example showing how to create a data center with 1 host and run
 * cloudlets on it, and receive notifications when each cloudlet finishes executing.
 *
 * <p>The example uses the new Cloudlet listeners to get these
 * notifications while the simulation is running.</p>
 *
 * @see Cloudlet#addOnFinishListener(EventListener)
 * @see EventListener
 *
 * @author Manoel Campos da Silva Filho
 */
public class CloudletListenersExample1 {
    /**
     * Number of Processor Elements (CPU Cores) of each Host.
     */
    private static final int HOST_PES_NUMBER = 4;

    /**
     * Number of Processor Elements (CPU Cores) of each VM and cloudlet.
     */
    private static final int VM_PES_NUMBER = 2;

    /**
     * Number of Cloudlets to create.
     */
    private static final int NUMBER_OF_CLOUDLETS = 4;

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
        new CloudletListenersExample1();
    }

    /**
     * Default constructor that builds and starts the simulation.
     */
    private CloudletListenersExample1() {
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

        createAndSubmitVms();
        createAndSubmitCloudlets(this.vmList.get(0));

        runSimulationAndPrintResults();
        System.out.println(getClass().getSimpleName() + " finished!");
    }

    /**
     * A Listener function that will be called every time a cloudlet
     * starts running into a VM. All cloudlets will use this same listener.
     *
     * @param eventInfo information about the happened event
     * @see #createCloudlet(long, Vm, long)
     */
    private void onCloudletStartListener(CloudletVmEventInfo eventInfo) {
        System.out.printf(
            "%n\t#EventListener: Cloudlet %d just started running at Vm %d at time %.2f%n",
            eventInfo.getCloudlet().getId(), eventInfo.getVm().getId(), eventInfo.getTime());
    }

    /**
     * A Listener function that will be called every time a cloudlet
     * finishes running into a VM. All cloudlets will use this same listener.
     *
     * @param eventInfo information about the happened event
     * @see #createCloudlet(long, Vm, long)
     */
    private void onCloudletFinishListener(CloudletVmEventInfo eventInfo) {
        System.out.printf(
                "%n\t#EventListener: Cloudlet %d finished running at Vm %d at time %.2f%n",
                eventInfo.getCloudlet().getId(), eventInfo.getVm().getId(), eventInfo.getTime());
    }

    private void runSimulationAndPrintResults() {
        simulation.start();

        final var cloudletFinishedList = broker.getCloudletFinishedList();
        new CloudletsTableBuilder(cloudletFinishedList).build();
    }

    /**
     * Creates cloudlets and submit them to the broker.
     * @param vm Vm to run the cloudlets to be created
     *
     * @see #createCloudlet(long, Vm, long)
     */
    private void createAndSubmitCloudlets(Vm vm) {
        long cloudletId;
        long length = 10000;
        for(int i = 0; i < NUMBER_OF_CLOUDLETS; i++){
            cloudletId = vm.getId() + i;
            final var cloudlet = createCloudlet(cloudletId, vm, length*(i+1));
            this.cloudletList.add(cloudlet);
        }

        this.broker.submitCloudletList(cloudletList);
    }

    /**
     * Creates VMs and submit them to the broker.
     */
    private void createAndSubmitVms() {
        final var vm0 = createVm(0);
        this.vmList.add(vm0);
        this.broker.submitVmList(vmList);
    }

    /**
     * Creates a VM with pre-defined configuration.
     *
     * @param id the VM id
     * @return the created VM
     */
    private Vm createVm(int id) {
        final int mips = 1000;
        final long size = 10000; // image size (Megabyte)
        final int ram = 512; // vm memory (Megabyte)
        final long bw = 1000;

        final var vm = new VmSimple(id, mips, VM_PES_NUMBER)
            .setRam(ram).setBw(bw).setSize(size)
            .setCloudletScheduler(new CloudletSchedulerSpaceShared());

        return vm;
    }

    /**
     * Creates a cloudlet with pre-defined configuration.
     *
     * @param id Cloudlet id
     * @param vm vm to run the cloudlet
     * @param length the cloudlet length in number of Million Instructions (MI)
     * @return the created cloudlet
     */
    private Cloudlet createCloudlet(long id, Vm vm, long length) {
        final long fileSize = 300;
        final long outputSize = 300;
        final int pesNumber = 1;
        final var utilizationModel = new UtilizationModelDynamic(0.2);
        final var cloudlet
            = new CloudletSimple(id, length, pesNumber)
                    .setFileSize(fileSize)
                    .setOutputSize(outputSize)
                    .setUtilizationModelCpu(new UtilizationModelFull())
                    .setUtilizationModelBw(utilizationModel)
                    .setUtilizationModelRam(utilizationModel)
                    .setVm(vm)
                    .addOnStartListener(this::onCloudletStartListener)
                    .addOnFinishListener(this::onCloudletFinishListener);
        return cloudlet;
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

        final long ram = 2048; // host memory (Megabyte)
        final long storage = 1000000; // host storage (Megabyte)
        final long bw = 10000; //Megabits/s

        return new HostSimple(ram, bw, storage, peList).setVmScheduler(new VmSchedulerTimeShared());
    }
}
