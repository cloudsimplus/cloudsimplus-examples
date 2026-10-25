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

import org.cloudsimplus.allocationpolicies.VmAllocationPolicySimple;
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
import org.cloudsimplus.provisioners.ResourceProvisionerSimple;
import org.cloudsimplus.resources.CoreCapacity;
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerSpaceShared;
import org.cloudsimplus.schedulers.vm.VmSchedulerTimeShared;
import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;

import java.util.ArrayList;
import java.util.List;

/// An example that runs 4 Cloudlets into a VM where its number
/// of PEs is just half of the total PEs required by all Cloudlets.
/// The Vm uses a [CloudletSchedulerSpaceShared], which
/// makes half of the Cloudlets to run first, while the other half
/// waits their finalization.
/// This way, the execution time of all cloudlets is the same,
/// but the start time of the second half is equal to
/// the finish time of the first half.
///
/// @author Manoel Campos da Silva Filho
/// @since CloudSim Plus 1.2.1
public class CloudletSchedulerSpaceSharedExample1 {
    private static final int HOSTS = 1;
    private static final int HOST_CORES = 4;

    /// The CPU capacity of every Host: a single [CoreCapacity] object is reused for all of them.
    /// That's safe because a [CoreCapacity] is immutable: it never changes after it's created,
    /// and each Host copies its values into its own Processor.
    /// So, changing the capacity of one Host's Processor doesn't affect the other Hosts.
    /// Reusing it avoids creating one temporary object per Host (fewer allocations and less GC work in large simulations).
    private static final CoreCapacity HOST_CAPACITY = new CoreCapacity(HOST_CORES, 1000);

    private static final int VMS = 1;
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
        new CloudletSchedulerSpaceSharedExample1();
    }

    private CloudletSchedulerSpaceSharedExample1() {
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

        final var cloudletFinishedListList = broker0.getCloudletFinishedList();
        new CloudletsTableBuilder(cloudletFinishedListList).build();
    }

    /// Creates a Datacenter that uses a [VmAllocationPolicySimple] by default.
    private Datacenter createDatacenter() {
        final var hostList = new ArrayList<Host>(HOSTS);
        for(int h = 0; h < HOSTS; h++) {
            final var host = createHost();
            hostList.add(host);
        }

        return new DatacenterSimple(simulation, hostList);
    }

    /// Creates a Host that uses a [ResourceProvisionerSimple] by default
    /// for RAM and BW.
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
        final var vmList = new ArrayList<Vm>(VMS);
        final int mips = 1000;
        for (int id = 0; id < VMS; id++) {
            final var vm = new VmSimple(id, new CoreCapacity(VM_CORES, mips));
            vm
              .setRam(512).setBw(1000).setSize(10000)
              .setCloudletScheduler(new CloudletSchedulerSpaceShared());

            vmList.add(vm);
        }

        return vmList;
    }

    /// Creates a list of Cloudlets.
    private List<Cloudlet> createCloudlets() {
        final var cloudletList = new ArrayList<Cloudlet>(CLOUDLETS);
        final var utilizationModelFull = new UtilizationModelFull();
        /* A utilization model for RAM and BW that uses only 50% of the resource capacity all the time. */
        final var utilizationModelDynamic = new UtilizationModelDynamic(0.5);
        for (int id = 0; id < CLOUDLETS; id++) {
            final var cloudlet = new CloudletSimple(id, CLOUDLET_LENGTH, CLOUDLET_CORES);
            cloudlet
                    .setFileSize(1024)
                    .setOutputSize(1024)
                    .setUtilizationModelCpu(utilizationModelFull)
                    .setUtilizationModelRam(utilizationModelDynamic)
                    .setUtilizationModelBw(utilizationModelDynamic);
            cloudletList.add(cloudlet);
        }

        return cloudletList;
    }
}
