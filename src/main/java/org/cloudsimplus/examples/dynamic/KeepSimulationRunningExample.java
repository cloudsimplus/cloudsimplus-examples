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
import org.cloudsimplus.core.Simulation;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.listeners.EventInfo;
import org.cloudsimplus.listeners.EventListener;
import org.cloudsimplus.allocators.ResourceAllocatorSimple;
import org.cloudsimplus.resources.CoreCapacity;
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerTimeShared;
import org.cloudsimplus.schedulers.vm.VmSchedulerTimeShared;
import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;

import java.util.ArrayList;
import java.util.List;

/// Shows how to keep the simulation running, even
/// when there is no event to be processed anymore.
/// It calls the [Simulation#terminateAt(double)] to define
/// the time the simulation must be terminated.
///
/// The example is useful when you want to run a simulation
/// for a specific amount of time.
/// For instance, if you want to run a simulation for 24 hours,
/// you just need to call `simulation.terminateAt(60*60*24)` (realize the value is in seconds).
///
/// It creates 4 Cloudlets and 2 VMs statically.
/// These Cloudlets will take 10 seconds to finish, but
/// the simulation is set to terminate
/// only after the time defined in [#TIME_TO_TERMINATE_SIMULATION].
///
/// This way, the simulation keeps waiting for dynamically
/// arrived events. Then, at a [defined time][#TIME_TO_CREATE_NEW_CLOUDLET],
/// new Cloudlets are created.
///
/// The example uses the CloudSim Plus [EventListener] feature
/// to enable monitoring the simulation and dynamically creating Cloudlets and VMs at runtime.
/// It relies on
/// [Java 8 Method References](https://docs.oracle.com/javase/tutorial/java/javaOO/methodreferences.html)
/// to set a method to be called for [onClockTick events][Simulation#addOnClockTickListener(EventListener)].
/// It enables getting notifications when the simulation clock advances, then creating and submitting new cloudlets.
///
/// Since the simulation was set to keep waiting for new events
/// until a defined time, the clock will be updated
/// even if no event arrives, to simulate time passing.
/// Check the [Simulation#terminateAt(double)] for details.
///
/// At the time defined in [#TIME_TO_CREATE_NEW_CLOUDLET],
/// 1 Cloudlet and 1 VM is created, so the results will show 5 Cloudlets.
/// The simulation will just end at the specified time.
///
/// @author Manoel Campos da Silva Filho
/// @since CloudSim Plus 2.3.0
/// @see RandomCloudletsArrivalExample
public class KeepSimulationRunningExample {
    /// @see #createDynamicCloudletAndVm(EventInfo)
    private static final double TIME_TO_CREATE_NEW_CLOUDLET = 15;

    /// @see Simulation#terminateAt(double)
    private static final double TIME_TO_TERMINATE_SIMULATION = TIME_TO_CREATE_NEW_CLOUDLET*2;

    /// @see Datacenter#getSchedulingInterval()
    private static final int SCHEDULING_INTERVAL = 1;

    private static final int HOSTS = 3;
    private static final int HOST_CORES = 8;

    /// The CPU capacity of every Host: a single [CoreCapacity] object is reused for all of them.
    /// That's safe because a [CoreCapacity] is immutable: it never changes after it's created,
    /// and each Host copies its values into its own Processor.
    /// So, changing the capacity of one Host's Processor doesn't affect the other Hosts.
    /// Reusing it avoids creating one temporary object per Host (fewer allocations and less GC work in large simulations).
    private static final CoreCapacity HOST_CAPACITY = new CoreCapacity(HOST_CORES, 1000);

    private static final int VMS = 2;
    private static final int VM_CORES = 4;

    private static final int CLOUDLETS = 4;
    private static final int CLOUDLET_CORES = 2;
    private static final int CLOUDLET_LENGTH = 10000;

    private final CloudSimPlus simulation;
    private final DatacenterBroker broker0;
    private final List<Vm> vmList;
    private final List<Cloudlet> cloudletList;
    private final Datacenter datacenter0;

    public static void main(String[] args) {
        new KeepSimulationRunningExample();
    }

    private KeepSimulationRunningExample() {
        /*Enables just some level of log messages.
          Make sure to import org.cloudsimplus.util.Log;*/
        //Log.setLevel(ch.qos.logback.classic.Level.WARN);

        simulation = new CloudSimPlus();
        simulation.terminateAt(TIME_TO_TERMINATE_SIMULATION);
        datacenter0 = createDatacenter();

        broker0 = new DatacenterBrokerSimple(simulation);

        vmList = new ArrayList<>(VMS);
        cloudletList = new ArrayList<>();

        createVms();
        createCloudlets();

        broker0.setVmDestructionDelayFunction(vm -> 0.2);
        broker0.submitVmList(vmList);
        broker0.submitCloudletList(cloudletList);

        simulation.addOnClockTickListener(this::createDynamicCloudletAndVm);

        simulation.start();

        final var cloudletFinishedList = broker0.getCloudletFinishedList();
        new CloudletsTableBuilder(cloudletFinishedList).build();
    }

    /// Creates a Datacenter and its Hosts.
    private Datacenter createDatacenter() {
        final var hostList = new ArrayList<Host>(HOSTS);
        for(int i = 0; i < HOSTS; i++) {
            final var host = createHost();
            hostList.add(host);
        }

        final var dc = new DatacenterSimple(simulation, hostList);
        dc.setSchedulingInterval(SCHEDULING_INTERVAL);
        return dc;
    }

    private Host createHost() {
        final long ram = 2048; //in Megabytes
        final long bw = 10000; //in Megabits/s
        final long storage = 1000000; //in Megabytes
        final var host = new HostSimple(ram, bw, storage, HOST_CAPACITY);
        host
            .setRamAllocator(new ResourceAllocatorSimple())
            .setBwAllocator(new ResourceAllocatorSimple())
            .setVmScheduler(new VmSchedulerTimeShared());
        return host;
    }

    /// Creates a list of VMs.
    private void createVms() {
        for (int i = 0; i < VMS; i++) {
            vmList.add(createVm(VM_CORES));
        }
    }

    private Vm createVm(final int pes) {
        return new VmSimple(new CoreCapacity(pes, 1000))
            .setRam(1000).setBw(1000).setSize(10000)
            .setCloudletScheduler(new CloudletSchedulerTimeShared());
    }

    /// Creates a list of Cloudlets.
    private void createCloudlets() {
        for (int i = 0; i < CLOUDLETS; i++) {
            cloudletList.add(createCloudlet());
        }
    }

    private Cloudlet createCloudlet() {
        final var um = new UtilizationModelDynamic(0.2);
        return new CloudletSimple(CLOUDLET_LENGTH, CLOUDLET_CORES)
            .setFileSize(1024)
            .setOutputSize(1024)
            .setUtilizationModelCpu(new UtilizationModelFull())
            .setUtilizationModelRam(um)
            .setUtilizationModelBw(um);
    }

    /// Simulates the dynamic arrival of a Cloudlet and a VM during simulation runtime.
    /// @param evt
    private void createDynamicCloudletAndVm(final EventInfo evt) {
        if((int)evt.getTime() == TIME_TO_CREATE_NEW_CLOUDLET){
            System.out.printf("%n# Dynamically creating 1 Cloudlet and 1 VM at time %.2f%n", evt.getTime());
            final var vm = createVm(VM_CORES*2);
            vmList.add(vm);
            final var cloudlet = createCloudlet();
            cloudletList.add(cloudlet);

            broker0.submitVm(vm);
            broker0.submitCloudlet(cloudlet);
        }
    }
}
