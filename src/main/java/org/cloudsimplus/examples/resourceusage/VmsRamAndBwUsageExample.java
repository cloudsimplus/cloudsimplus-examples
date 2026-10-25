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
package org.cloudsimplus.examples.resourceusage;

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
import org.cloudsimplus.resources.CoreCapacity;
import org.cloudsimplus.resources.*;
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerTimeShared;
import org.cloudsimplus.schedulers.vm.VmSchedulerTimeShared;
import org.cloudsimplus.utilizationmodels.UtilizationModel;
import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;

import java.util.*;

/// Shows how to use the [onClockTick Listener][Simulation#addOnClockTickListener(EventListener)]
/// to keep track os simulation clock and store VM's RAM and BW utilization along the time.
/// CloudSim Plus already has built-in features to obtain VM's CPU utilization.
/// Check [org.cloudsimplus.examples.power.PowerExample].
///
/// The example uses the CloudSim Plus [EventListener] feature
/// to enable monitoring the simulation and dynamically collect RAM and BW usage.
/// It relies on
/// [Java 8 Method References](https://docs.oracle.com/javase/tutorial/java/javaOO/methodreferences.html)
/// to set a method to be called for [onClockTick events][Simulation#addOnClockTickListener(EventListener)].
/// It enables getting notifications when the simulation clock advances, then creating and submitting new cloudlets.
///
/// @author Manoel Campos da Silva Filho
/// @since CloudSim Plus 4.1.2
///
/// @see VmsCpuUsageExample
/// @see org.cloudsimplus.examples.power.PowerExample
public class VmsRamAndBwUsageExample {
    /// @see Datacenter#getSchedulingInterval()
    private static final int SCHEDULING_INTERVAL = 1;

    private static final int HOSTS = 4;
    private static final int HOST_CORES = 8;

    /// The CPU capacity of every Host: a single [CoreCapacity] object is reused for all of them.
    /// That's safe because a [CoreCapacity] is immutable: it never changes after it's created,
    /// and each Host copies its values into its own Processor.
    /// So, changing the capacity of one Host's Processor doesn't affect the other Hosts.
    /// Reusing it avoids creating one temporary object per Host (fewer allocations and less GC work in large simulations).
    private static final CoreCapacity HOST_CAPACITY = new CoreCapacity(HOST_CORES, 1000);

    private static final int VMS = 2;
    private static final int VM_CORES = 4;

    private static final int CLOUDLETS = 5;
    private static final int CLOUDLET_CORES = 2;
    private static final int CLOUDLET_LENGTH = 10000;

    private final CloudSimPlus simulation;
    private final DatacenterBroker broker0;
    private final List<Vm> vmList;
    private final List<Cloudlet> cloudletList;
    private final Datacenter datacenter0;

    /// A map to store RAM utilization history for every VM.
    /// Each key is a VM and each value is another map.
    /// This entire data structure is usually called a multimap.
    ///
    /// Such an internal map stores RAM utilization for a VM.
    /// The keys of this internal map are the time the utilization was collected (in seconds)
    /// and the value the utilization percentage (from 0 to 1).
    private final Map<Vm, Map<Double, Double>> allVmsRamUtilizationHistory;

    /// @see #allVmsRamUtilizationHistory
    private final Map<Vm, Map<Double, Double>> allVmsBwUtilizationHistory;

    public static void main(String[] args) {
        new VmsRamAndBwUsageExample();
    }

    private VmsRamAndBwUsageExample() {
        /*Enables just some level of log messages.
          Make sure to import org.cloudsimplus.util.Log;*/
        //Log.setLevel(ch.qos.logback.classic.Level.WARN);

        simulation = new CloudSimPlus();
        datacenter0 = createDatacenter();

        broker0 = new DatacenterBrokerSimple(simulation);

        vmList = createVms();
        cloudletList = createCloudlets();
        broker0.submitVmList(vmList);
        broker0.submitCloudletList(cloudletList);

        allVmsRamUtilizationHistory = initializeUtilizationHistory();
        allVmsBwUtilizationHistory = initializeUtilizationHistory();
        simulation.addOnClockTickListener(this::onClockTickListener);

        simulation.start();

        final var cloudletFinishedList = broker0.getCloudletFinishedList();
        new CloudletsTableBuilder(cloudletFinishedList).build();

        printVmListResourceUtilizationHistory();
    }

    /// Prints the RAM and BW utilization history of every Vm.
    private void printVmListResourceUtilizationHistory() {
        System.out.println();
        for (final var vm : vmList) {
            printVmUtilizationHistory(vm);
        }
    }

    /// Prints the RAM and BW utilization history of a given Vm.
    private void printVmUtilizationHistory(Vm vm) {
        System.out.println(vm + " RAM and BW utilization history");
        System.out.println("----------------------------------------------------------------------------------");

        //Every VM has a utilization map, created by initializeUtilizationHistory()
        final Map<Double, Double> vmRamUtilization = Objects.requireNonNull(allVmsRamUtilizationHistory.get(vm));
        final Map<Double, Double> vmBwUtilization = Objects.requireNonNull(allVmsBwUtilizationHistory.get(vm));

        //Each entry has a resource utilization collected time and the RAM utilization at that time
        for (final var entry : vmRamUtilization.entrySet()) {
            final double time = entry.getKey();
            //RAM and BW utilization are collected at the same times
            final double bwUtilization = Objects.requireNonNull(vmBwUtilization.get(time));
            System.out.printf(
                "Time: %10.1f secs | RAM Utilization: %10.2f%% | BW Utilization: %10.2f%%%n",
                time, entry.getValue() * 100, bwUtilization * 100);
        }

        System.out.printf("----------------------------------------------------------------------------------%n%n");
    }

    /// Initializes a map that will store utilization history for
    /// some resource (such as RAM or BW) of every VM.
    /// It also creates an empty internal map to store
    /// the resource utilization for every VM along the simulation execution.
    /// The internal map for every VM will be empty.
    /// They are filled inside the [#onClockTickListener(EventInfo)].
    private Map<Vm, Map<Double, Double>> initializeUtilizationHistory() {
        //TreeMap sorts entries based on the key
        final Map<Vm, Map<Double, Double>> map = new HashMap<>(VMS);

        for (final var vm : vmList) {
            map.put(vm, new TreeMap<>());
        }

        return map;
    }

    /// Keeps track of simulation clock.
    /// Every time the clock changes, this method is called.
    /// To enable this method to be called at a defined
    /// interval, you need to set the [scheduling interval][Datacenter#setSchedulingInterval(double)].
    ///
    /// @param evt information about the clock tick event
    /// @see #SCHEDULING_INTERVAL
    private void onClockTickListener(final EventInfo evt) {
        collectVmResourceUtilization(this.allVmsRamUtilizationHistory, Ram.class);
        collectVmResourceUtilization(this.allVmsBwUtilizationHistory, Bandwidth.class);
    }

    /// Collects the utilization percentage of a given VM resource for every VM.
    /// CloudSim Plus already has built-in features to obtain VM's CPU utilization.
    /// Check [org.cloudsimplus.examples.power.PowerExample].
    ///
    /// @param allVmsUtilizationHistory the map where the collected utilization for every VM will be stored
    /// @param resourceClass the kind of resource to collect its utilization (usually [Ram] or [Bandwidth]).
    private void collectVmResourceUtilization(final Map<Vm, Map<Double, Double>> allVmsUtilizationHistory, Class<? extends ResourceManageable> resourceClass) {
        for (final var vm : vmList) {
            /*Gets the internal resource utilization map for the current VM.
            * The key of this map is the time the usage was collected (in seconds)
            * and the value the percentage of utilization (from 0 to 1). */
            final Map<Double, Double> vmUtilizationHistory = Objects.requireNonNull(allVmsUtilizationHistory.get(vm));
            vmUtilizationHistory.put(simulation.clock(), vm.getResource(resourceClass).getPercentUtilization());
        }
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
        host.setVmScheduler(new VmSchedulerTimeShared());
        return host;
    }

    /// Creates a list of VMs.
    private List<Vm> createVms() {
        final var newVmList = new ArrayList<Vm>(VMS);
        for (int i = 0; i < VMS; i++) {
            newVmList.add(createVm(VM_CORES));
        }
        return newVmList;
    }

    private Vm createVm(final int pes) {
        return new VmSimple(new CoreCapacity(pes, 1000))
            .setRam(1000).setBw(1000).setSize(10000)
            .setCloudletScheduler(new CloudletSchedulerTimeShared());
    }

    /// Creates a list of Cloudlets.
    private List<Cloudlet> createCloudlets() {
        final var newCloudletList = new ArrayList<Cloudlet>(CLOUDLETS);
        for (int i = 0; i < CLOUDLETS; i++) {
            newCloudletList.add(createCloudlet());
        }

        return newCloudletList;
    }

    /// Creates a Cloudlet with specific [UtilizationModel] for RAM, BW and CPU.
    /// You can change the method to use any [UtilizationModel] you want.
    /// @return
    private Cloudlet createCloudlet() {
        final var ramUtilizationModel = new UtilizationModelDynamic(0.2);
        final var bwUtilizationModel = new UtilizationModelDynamic(0.1);

        ramUtilizationModel.setUtilizationUpdateFunction(this::utilizationUpdate);
        bwUtilizationModel.setUtilizationUpdateFunction(this::utilizationUpdate);

        return new CloudletSimple(CLOUDLET_LENGTH, CLOUDLET_CORES)
            .setFileSize(1024)
            .setOutputSize(1024)
            .setUtilizationModelCpu(new UtilizationModelFull())
            .setUtilizationModelRam(ramUtilizationModel)
            .setUtilizationModelBw(bwUtilizationModel);
    }

    /// Defines how the Cloudlet's utilization of RAM and BW will increase along the simulation time.
    /// @return the updated utilization for RAM or BW
    /// @see #createCloudlet()
    private double utilizationUpdate(UtilizationModelDynamic utilizationModel) {
        return utilizationModel.getUtilization() + utilizationModel.getTimeSpan() * 0.01;
    }
}
