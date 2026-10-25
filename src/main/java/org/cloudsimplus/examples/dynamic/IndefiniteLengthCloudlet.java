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
import org.cloudsimplus.core.CloudSimTag;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.resources.CoreCapacity;
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerTimeShared;
import org.cloudsimplus.schedulers.vm.VmSchedulerTimeShared;
import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;

import java.util.ArrayList;
import java.util.List;

/// An example showing how to create Cloudlets with an indefinite length,
/// so that each Cloudlet stops running only if:
///
/// - (i) it reaches [Long#MAX_VALUE] MI executed or
/// - (ii) a positive length is dynamically defined (the time or event when the length of such Cloudlets are set
///   can be defined in uncountable ways, according to developer needs)
/// - (iii) [CloudSimTag#CLOUDLET_FINISH] message is sent to the [DatacenterBroker].
/// - (iv) the simulation is terminated by defining a termination time.
///
/// In this example it is called the [CloudSimPlus#terminateAt(double)]
/// method to define when the simulation must finish.
/// At that moment, the length of the Cloudlets is set so that they can finish executing.
///
/// @author Manoel Campos da Silva Filho
/// @since CloudSim Plus 4.0.0
public class IndefiniteLengthCloudlet {
    private static final int HOSTS = 1;
    private static final int HOST_CORES = 8;

    /// The CPU capacity of every Host: a single [CoreCapacity] object is reused for all of them.
    /// That's safe because a [CoreCapacity] is immutable: it never changes after it's created,
    /// and each Host copies its values into its own Processor.
    /// So, changing the capacity of one Host's Processor doesn't affect the other Hosts.
    /// Reusing it avoids creating one temporary object per Host (fewer allocations and less GC work in large simulations).
    private static final CoreCapacity HOST_CAPACITY = new CoreCapacity(HOST_CORES, 1000);

    private static final int VMS = 2;
    private static final int VM_CORES = 4;
    private static final int VM_MIPS = 1000;

    /// The CPU capacity of every VM: a single [CoreCapacity] object is reused for all of them.
    /// That's safe because a [CoreCapacity] is immutable: it never changes after it's created,
    /// and each VM copies its values into its own Processor.
    /// So, changing the capacity of one VM (such as by vertical scaling) doesn't affect the other VMs.
    /// Reusing it avoids creating one temporary object per VM (fewer allocations and less GC work in large simulations).
    private static final CoreCapacity VM_CAPACITY = new CoreCapacity(VM_CORES, VM_MIPS);

    private static final int CLOUDLETS = 2;
    private static final int CLOUDLET_CORES = 2;

    /// Defines a negative length for Cloudlets so that
    /// they keep running until explicitly requested to finish.
    /// In this example, Cloudlets are finished only when
    /// the simulation termination time is reached.
    ///
    /// @see CloudSimPlus#terminateAt(double)
    private static final int CLOUDLET_LENGTH = -10000;

    private final CloudSimPlus simulation;
    private final DatacenterBroker broker0;
    private List<Vm> vmList;
    private List<Cloudlet> cloudletList;
    private Datacenter datacenter0;

    public static void main(String[] args) {
        new IndefiniteLengthCloudlet();
    }

    private IndefiniteLengthCloudlet() {
        /*Enables just some level of log messages.
          Make sure to import org.cloudsimplus.util.Log;*/
        //Log.setLevel(ch.qos.logback.classic.Level.WARN);

        simulation = new CloudSimPlus();
        //Keeps the simulation running for 1 hour
        simulation.terminateAt(60*60);
        datacenter0 = createDatacenter();

        //Creates a broker that is a software acting on behalf of a cloud customer to manage his/her VMs and Cloudlets
        broker0 = new DatacenterBrokerSimple(simulation);

        vmList = createVms();

        cloudletList = createCloudlets();
        broker0.submitVmList(vmList);
        broker0.submitCloudletList(cloudletList);

        simulation.start();

        //final var cloudletFinishedList = broker0.getCloudletSubmittedList();
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

        return DatacenterSimple.of(simulation, hostList);
    }

    private Host createHost() {
        final long ram = 2048; //in Megabytes
        final long bw = 10000; //in Megabits/s
        final long storage = 1000000; //in Megabytes
        final var host = HostSimple.of(ram, bw, storage, HOST_CAPACITY);
        host.setVmScheduler(new VmSchedulerTimeShared());
        return host;
    }

    /// Creates a list of VMs.
    private List<Vm> createVms() {
        final var newVmList = new ArrayList<Vm>(VMS);
        for (int i = 0; i < VMS; i++) {
            final var vm =
                VmSimple.of(i, VM_CAPACITY)
                    .setRam(512).setBw(1000).setSize(10000)
                    .setCloudletScheduler(new CloudletSchedulerTimeShared());

            newVmList.add(vm);
        }

        return newVmList;
    }

    /// Creates Cloudlets with indefinite length by setting a negative value.
    /// This way, the Cloudlets keep running until one of the conditions explained
    /// in the documentation of this class is met.
    private List<Cloudlet> createCloudlets() {
        final var newCloudletList = new ArrayList<Cloudlet>(CLOUDLETS);
        final var utilizationFull = new UtilizationModelFull();
        final var utilizationDynamic = new UtilizationModelDynamic(1.0/CLOUDLETS);

        for (int i = 0; i < CLOUDLETS; i++) {
            final var cloudlet =
                CloudletSimple.of(CLOUDLET_LENGTH, CLOUDLET_CORES)
                    .setFileSize(1024)
                    .setOutputSize(1024)
                    .setUtilizationModelCpu(utilizationFull)
                    .setUtilizationModelRam(utilizationDynamic)
                    .setUtilizationModelBw(utilizationDynamic);
            newCloudletList.add(cloudlet);
        }

        return newCloudletList;
    }
}
