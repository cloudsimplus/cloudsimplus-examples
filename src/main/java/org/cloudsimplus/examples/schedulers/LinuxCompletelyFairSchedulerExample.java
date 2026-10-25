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
package org.cloudsimplus.examples.schedulers;

import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;
import org.cloudsimplus.builders.tables.TextTableColumn;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.resources.CoreCapacity;
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerCompletelyFair;
import org.cloudsimplus.schedulers.vm.VmSchedulerTimeShared;
import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;

import java.util.ArrayList;
import java.util.List;

/// An example that uses an implementation of the [Completely Fair Scheduler][CloudletSchedulerCompletelyFair]
/// used in the Linux Kernel for scheduling of Cloudlets execution inside a Vm.
/// It defines priority for some Cloudlets, so that they will have more time to use the CPU
/// than other Cloudlets.
///
/// It is strongly recommended to read the [CloudletSchedulerCompletelyFair] class documentation
/// to understand how this scheduler works.
///
/// @author Manoel Campos da Silva Filho
/// @see <a href="https://en.wikipedia.org/wiki/Completely_Fair_Scheduler">Completely Fair Scheduler (CFS)</a>
/// @since CloudSim Plus 1.0
public class LinuxCompletelyFairSchedulerExample {
    private static final int HOSTS_NUMBER = 1;
    private static final long HOST_MIPS = 1000;
    private static final int HOST_CORES = 3;
    private static final int VMS_NUMBER = 1;
    private static final int VM_CORES = HOST_CORES;
    private static final long VM_MIPS = HOST_MIPS;

    /// The CPU capacity of every VM: a single [CoreCapacity] object is reused for all of them.
    /// That's safe because a [CoreCapacity] is immutable: it never changes after it's created,
    /// and each VM copies its values into its own Processor.
    /// So, changing the capacity of one VM (such as by vertical scaling) doesn't affect the other VMs.
    /// Reusing it avoids creating one temporary object per VM (fewer allocations and less GC work in large simulations).
    private static final CoreCapacity VM_CAPACITY = new CoreCapacity(VM_CORES, VM_MIPS);

    private static final int CLOUDLETS_NUMBER = HOST_CORES*2;
    private static final int CLOUDLET_CORES = 1;
    private static final int CLOUDLET_LEN = 10000; //in MI

    private final CloudSimPlus simulation;
    private List<Cloudlet> cloudletList;
    private List<Vm> vmList;

    private int numberOfCreatedCloudlets = 0;
    private int numberOfCreatedVms = 0;

    public static void main(String[] args) {
        new LinuxCompletelyFairSchedulerExample();
    }

    /// Default constructor which builds and runs the simulation.
    private LinuxCompletelyFairSchedulerExample() {
        /*Enables just some level of log messages.
          Make sure to import org.cloudsimplus.util.Log;*/
        //Log.setLevel(ch.qos.logback.classic.Level.WARN);

        System.out.println("Starting " + getClass().getSimpleName());
        simulation = new CloudSimPlus();

        final var datacenter0 = createDatacenter();

        final var broker0 = new DatacenterBrokerSimple(simulation);

        createAndSubmitVms(broker0);
        createAndSubmitCloudlets(broker0);
        for(int i = 0; i < CLOUDLETS_NUMBER/2; i++){
            cloudletList.get(i).setPriority(4);
        }

        simulation.start();

        final var cloudletFinishedList = broker0.getCloudletFinishedList();
        new CloudletsTableBuilder(cloudletFinishedList)
            .addColumn(new TextTableColumn("Priority"), Cloudlet::getPriority, 2)
            .build();
        System.out.println(getClass().getSimpleName() + " finished!");
    }

    private void createAndSubmitCloudlets(final DatacenterBroker broker0) {
        this.cloudletList = new ArrayList<>(CLOUDLETS_NUMBER);
        for(int i = 0; i < CLOUDLETS_NUMBER; i++){
            this.cloudletList.add(createCloudlet(broker0));
        }
        broker0.submitCloudletList(cloudletList);
    }

    private void createAndSubmitVms(final DatacenterBroker broker0) {
        this.vmList = new ArrayList<>(VMS_NUMBER);
        for(int i = 0; i < VMS_NUMBER; i++){
            this.vmList.add(createVm(broker0));
        }
        broker0.submitVmList(vmList);
    }

    private Datacenter createDatacenter() {
        final var hostList = new ArrayList<Host>(HOSTS_NUMBER);
        for(int i = 0; i < HOSTS_NUMBER; i++){
            hostList.add(createHost());
        }

        return new DatacenterSimple(simulation, hostList);
    }

    private Host createHost() {
        final long ram = 2048; // host memory (Megabyte)
        final long storage = 1000000; // host storage
        final long bw = 10000;

        final var peList = new CoreCapacity(HOST_CORES, HOST_MIPS);

       return new HostSimple(ram, bw, storage, peList).setVmScheduler(new VmSchedulerTimeShared());
    }

    private Vm createVm(DatacenterBroker broker) {
        final long   storage = 10000; // vm image size (Megabyte)
        final int    ram = 512; // vm memory (Megabyte)
        final long   bw = 1000; // vm bandwidth

        return new VmSimple(numberOfCreatedVms++, VM_CAPACITY)
            .setRam(ram).setBw(bw).setSize(storage)
            .setCloudletScheduler(new CloudletSchedulerCompletelyFair());
    }

    private Cloudlet createCloudlet(DatacenterBroker broker) {
        final long fileSize = 300; //Size (in bytes) before execution
        final long outputSize = 300; //Size (in bytes) after execution
        final var utilization = new UtilizationModelFull();

        return new CloudletSimple(numberOfCreatedCloudlets++, CLOUDLET_LEN, CLOUDLET_CORES)
            .setFileSize(fileSize)
            .setOutputSize(outputSize)
            .setUtilizationModelCpu(utilization)
            .setUtilizationModelRam(new UtilizationModelDynamic(0.2));
    }
}

