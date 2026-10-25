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

import org.cloudsimplus.placementpolicies.VmPlacementPolicyRoundRobin;
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
import org.cloudsimplus.resources.CoreCapacity;
import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/// An example that show the usage of the [VmPlacementPolicyRoundRobin],
/// that cyclically places VMs into Hosts. This way, it places a VM into a Host
/// and moves to the next Host.
/// All hosts are not powered-on when created. As VMs need to be placed,
/// Hosts are activated on demand, as can be checked in the logs.
///
/// Keep in mind that such a policy is very naive and increases the number of active Hosts,
/// leading to higher power consumption.
///
/// @author Manoel Campos da Silva Filho
/// @since CloudSim Plus 4.4.2
public class VmPlacementPolicyRoundRobinExample {
    private static final int HOSTS = 4;
    private static final int HOST_CORES = 8;

    /// The CPU capacity of every Host: a single [CoreCapacity] object is reused for all of them.
    /// That's safe because a [CoreCapacity] is immutable: it never changes after it's created,
    /// and each Host copies its values into its own Processor.
    /// So, changing the capacity of one Host's Processor doesn't affect the other Hosts.
    /// Reusing it avoids creating one temporary object per Host (fewer allocations and less GC work in large simulations).
    private static final CoreCapacity HOST_CAPACITY = new CoreCapacity(HOST_CORES, 1000);

    private static final int VMS = 8;
    private static final int VM_CORES = 2;

    /// The CPU capacity of every VM: a single [CoreCapacity] object is reused for all of them.
    /// That's safe because a [CoreCapacity] is immutable: it never changes after it's created,
    /// and each VM copies its values into its own Processor.
    /// So, changing the capacity of one VM (such as by vertical scaling) doesn't affect the other VMs.
    /// Reusing it avoids creating one temporary object per VM (fewer allocations and less GC work in large simulations).
    private static final CoreCapacity VM_CAPACITY = new CoreCapacity(VM_CORES, 1000);

    private static final int CLOUDLETS = 8;
    private static final int CLOUDLET_CORES = 2;
    private static final int CLOUDLET_LENGTH = 10000;

    private final CloudSimPlus simulation;
    private final DatacenterBroker broker0;
    private List<Vm> vmList;
    private List<Cloudlet> cloudletList;
    private Datacenter datacenter0;

    public static void main(String[] args) {
        new VmPlacementPolicyRoundRobinExample();
    }

    private VmPlacementPolicyRoundRobinExample() {
        /*Enables just some level of log messages.
          Make sure to import org.cloudsimplus.util.Log;*/
        //Log.setLevel(ch.qos.logback.classic.Level.WARN);

        simulation = new CloudSimPlus();
        datacenter0 = createDatacenter();

        //Creates a broker that is a software acting on behalf of a cloud customer to manage his/her VMs and Cloudlets
        broker0 = new DatacenterBrokerSimple(simulation);

        vmList = createVms();
        cloudletList = createCloudlets();
        broker0.submitVmList(vmList);
        broker0.submitCloudletList(cloudletList);

        simulation.start();

        final var cloudletFinishedList = broker0.getCloudletFinishedList();
        cloudletFinishedList.sort(Comparator.comparingLong(cloudlet -> cloudlet.getVm().getId()));
        new CloudletsTableBuilder(cloudletFinishedList).build();
    }

    /// Creates a Datacenter and its Hosts.
    private Datacenter createDatacenter() {
        final var hostList = new ArrayList<Host>(HOSTS);
        for(int i = 0; i < HOSTS; i++) {
            final var host = createHost();
            hostList.add(host);
        }

        return new DatacenterSimple(simulation, hostList, new VmPlacementPolicyRoundRobin());
    }

    private Host createHost() {
        final long ram = 2048; //in Megabytes
        final long bw = 10000; //in Megabits/s
        final long storage = 1000000; //in Megabytes

        /*
        Uses ResourceAllocatorSimple by default for RAM and BW allocation
        and VmSchedulerSpaceShared for VM scheduling.
        */
        return new HostSimple(ram, bw, storage, HOST_CAPACITY, false);
    }

    /// Creates a list of VMs.
    private List<Vm> createVms() {
        final var list = new ArrayList<Vm>(VMS);
        for (int i = 0; i < VMS; i++) {
            //Uses a CloudletSchedulerTimeShared by default to schedule Cloudlets
            final Vm vm = new VmSimple(VM_CAPACITY);
            vm.setRam(512).setBw(1000).setSize(10000);
            list.add(vm);
        }

        return list;
    }

    /// Creates a list of Cloudlets.
    private List<Cloudlet> createCloudlets() {
        final var list = new ArrayList<Cloudlet>(CLOUDLETS);

        //UtilizationModel defining the Cloudlets use only 50% of any resource all the time
        final var utilizationModel = new UtilizationModelDynamic(0.5);

        for (int i = 0; i < CLOUDLETS; i++) {
            final var cloudlet = new CloudletSimple(CLOUDLET_LENGTH, CLOUDLET_CORES, utilizationModel);
            cloudlet.setSizes(1024);
            list.add(cloudlet);
        }

        return list;
    }
}
