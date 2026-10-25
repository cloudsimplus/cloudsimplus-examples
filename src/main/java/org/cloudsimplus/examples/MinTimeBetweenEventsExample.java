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

package org.cloudsimplus.examples;

import ch.qos.logback.classic.Level;
import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;
import org.cloudsimplus.builders.tables.TableColumn;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.core.Simulation;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.distributions.ContinuousDistribution;
import org.cloudsimplus.distributions.UniformDistr;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.provisioners.ResourceProvisionerSimple;
import org.cloudsimplus.resources.CoreCapacity;
import org.cloudsimplus.schedulers.vm.VmSchedulerSpaceShared;
import org.cloudsimplus.util.Log;
import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/// An example showing how the [Simulation#getMinTimeBetweenEvents()] may affect the processing
/// of events in your simulation.
/// This way, it may affect simulation accuracy, regarding the execution time of Cloudlets.
///
/// These issues happen when the time between some events is smaller than the
/// [Simulation#getMinTimeBetweenEvents()] (for instance, some Cloudlets are finishing
/// in a time interval smaller than the mentioned simulation attribute).
/// Therefore, you may need to fine tune such attribute according to your simulation.
///
/// This example reproduces the configuration
/// [Issue #163](https://github.com/cloudsimplus/cloudsimplus/issues/163).
/// The documentation of the [#MIN_TIME_BETWEEN_EVENTS] constant below shows how
/// to configure the attribute to make CloudSim Plus process all events
/// accurately in this scenario and then all submitted Cloudlets be finished.
///
/// @author Natthasak Vechprasit
/// @author Manoel Campos da Silva Filho
/// @since CloudSim Plus 4.0.7
public class MinTimeBetweenEventsExample {
    /// The minimum time between an event and the previous one to get the current event to be processed.
    ///
    /// If events happen in a time interval smaller than this value, that will
    /// impact results accuracy. The created Cloudlets take about 0.1 second to finish,
    /// but with the current MIN_TIME_BETWEEN_EVENTS, you'll see the ExecTime is greater than
    /// that.
    ///
    /// You can fix that by changing this constant to 0.01, for instance.
    ///
    /// @see CloudSimPlus#getMinTimeBetweenEvents()
    private static final double MIN_TIME_BETWEEN_EVENTS = 0.5;

    private static final int HOST_PES_NUMBER = 4;
    private static final int VM_PES_NUMBER = HOST_PES_NUMBER;
    private static final int CLOUDLETS = 6;

    private static final long HOST_MIPS = 1000;

    /// The CPU capacity of every Host: a single [CoreCapacity] object is reused for all of them.
    /// That's safe because a [CoreCapacity] is immutable: it never changes after it's created,
    /// and each Host copies its values into its own Processor.
    /// So, changing the capacity of one Host's Processor doesn't affect the other Hosts.
    /// Reusing it avoids creating one temporary object per Host (fewer allocations and less GC work in large simulations).
    private static final CoreCapacity HOST_CAPACITY = new CoreCapacity(HOST_PES_NUMBER, HOST_MIPS);

    private static final int VM_MIPS = 1000;

    /// The CPU capacity of every VM: a single [CoreCapacity] object is reused for all of them.
    /// That's safe because a [CoreCapacity] is immutable: it never changes after it's created,
    /// and each VM copies its values into its own Processor.
    /// So, changing the capacity of one VM (such as by vertical scaling) doesn't affect the other VMs.
    /// Reusing it avoids creating one temporary object per VM (fewer allocations and less GC work in large simulations).
    private static final CoreCapacity VM_CAPACITY = new CoreCapacity(VM_PES_NUMBER, VM_MIPS);

    private static final int CLOUDLET_CORES = 1;
    private static final long CLOUDLET_LENGTH = 100;     //in number of Million Instructions (MI)

    private final List<Host> hostList;
    private final List<Vm> vmList;
    private final List<Cloudlet> cloudletList;
    private final DatacenterBroker broker;
    private final Datacenter datacenter;
    private final CloudSimPlus simulation;
    private final ContinuousDistribution random;

    public static void main(String[] args) {
        new MinTimeBetweenEventsExample();
    }

    private MinTimeBetweenEventsExample() {
        Log.setLevel(Level.WARN);

        System.out.println("Starting " + getClass().getSimpleName());
        simulation = new CloudSimPlus(MIN_TIME_BETWEEN_EVENTS);

        this.hostList = new ArrayList<>();
        this.vmList = new ArrayList<>();
        this.cloudletList = new ArrayList<>();
        this.datacenter = createDatacenter();
        this.broker = new DatacenterBrokerSimple(simulation);

        final var vm = createAndSubmitVm();
        final long seed = 1547040598054L;
        random =  new UniformDistr(seed);
        createAndSubmitCloudlets(vm);

        runSimulationAndPrintResults();
    }

    private Datacenter createDatacenter() {
        hostList.add(createHost());
        return new DatacenterSimple(simulation, hostList);
    }

    private Host createHost() {
        final long ram = 4096; // host memory (Megabyte)
        final long storage = 1000000; // host storage (Megabyte)
        final long bw = 10000; //Megabits/s

        return new HostSimple(ram, bw, storage, HOST_CAPACITY)
            .setRamProvisioner(new ResourceProvisionerSimple())
            .setBwProvisioner(new ResourceProvisionerSimple())
            .setVmScheduler(new VmSchedulerSpaceShared());
    }

    private Vm createAndSubmitVm() {
        final var list = new ArrayList<Vm>();
        final Vm vm = createVm(this.vmList.size());
        list.add(vm);

        broker.submitVmList(list);
        this.vmList.addAll(list);

        return vm;
    }

    private Vm createVm(final int id) {
        final long size = 10000; // image size (Megabyte)
        final int ram = 4096;    // vm memory (Megabyte)
        final long bw = 1000;

        //It uses a CloudletSchedulerTimeShared by default
        final Vm vm = new VmSimple(id, VM_CAPACITY);
        vm.setRam(ram).setBw(bw).setSize(size);
        return vm;
    }

    /// Creates a list of Cloudlets to be run inside a given VM
    /// @param vm the VM to run the Cloudlets
    private void createAndSubmitCloudlets(final Vm vm) {
        final var list = new ArrayList<Cloudlet>(CLOUDLETS);

        for (int i = 0; i < CLOUDLETS; i++) {
            list.add(createCloudlet(vm));
        }

        broker.submitCloudletList(list);
        cloudletList.addAll(list);
    }

    /// Creates a cloudlet with random submission delay for a given VM.
    /// @param vm the VM to run the Cloudlet
    /// @return the created Cloudlet
    private Cloudlet createCloudlet(final Vm vm) {
        final long fileSize = 300;   //in bytes
        final long outputSize = 300; //in bytes
        final var utilizationModel = new UtilizationModelDynamic(0.1);

        final var cloudlet = new CloudletSimple(CLOUDLET_LENGTH, CLOUDLET_CORES)
            .setFileSize(fileSize)
            .setOutputSize(outputSize)
            .setUtilizationModelCpu(new UtilizationModelFull())
            .setUtilizationModelRam(utilizationModel)
            .setUtilizationModelBw(utilizationModel)
            .setVm(vm);

        final double delay = random.sample();
        cloudlet.setSubmissionDelay(delay);

        return cloudlet;
    }

    private void runSimulationAndPrintResults() {
        simulation.start();

        broker.getCloudletSubmittedList().sort(Comparator.comparingDouble(Cloudlet::getStartTime));
        final var builder = new CloudletsTableBuilder(broker.getCloudletSubmittedList());
        builder
            .column(9,  this::formatColumn)
            .column(10, this::formatColumn)
            .column(11, this::formatColumn)
            .build();

        final int notFinished = broker.getCloudletCreatedList().size() - broker.getCloudletFinishedList().size();
        System.out.printf("%nDatacenter Scheduling Interval: %.2f%n", datacenter.getSchedulingInterval());
        System.out.printf("Min time between events: %.2f%n", simulation.getMinTimeBetweenEvents());
        System.out.printf("Seed for the random Cloudlet's submission delay generator: %d%n", random.getSeed());
        System.out.printf("Cloudlets created: %d | Cloudlets not finished: %d%n", cloudletList.size(), notFinished);
        if(notFinished > 0){
            System.out.printf("%n----------> Try decreasing the value of MIN_TIME_BETWEEN_EVENTS constant in this example! <----------%n%n");
        }
        System.out.printf("%n%s finished!%n", getClass().getSimpleName());
    }

    private void formatColumn(final TableColumn col) {
        col.setFormat("%.2f");
    }
}
