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
package org.cloudsimplus.examples.costs;

import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.datacenters.DatacenterCharacteristicsSimple;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmCost;
import org.cloudsimplus.vms.VmSimple;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A simple example showing how to define datacenter resource utilization
 * and individual VM's costs ($).
 * It creates VMs that run some cloudlets.
 * Some VMs will just be idle all the time and
 * other ones won't be created due to lack of available Hosts.
 *
 * @author Manoel Campos da Silva Filho
 * @since CloudSim Plus 6.2.7
 */
public class CostsExample1 {
    /**
     * Datacenter scheduling interval (in seconds)
     * @see Datacenter#getSchedulingInterval()
     */
    private static final int SCHEDULING_INTERVAL = 1;

    private static final int HOSTS = 2;
    private static final int HOST_PES = 8;
    private static final int HOST_MIPS = 1000;

    private static final int VMS = 6;
    private static final int VM_PES = 4;
    private static final int VM_RAM = 512;
    private static final int VM_BW = 1000;
    private static final int VM_SIZE = 10000;

    private static final int CLOUDLET_BY_VM = 2;
    private static final int CLOUDLET_PES = 2;
    private static final int CLOUDLET_LENGTH = 100_000;

    private final CloudSimPlus simulation;
    private final DatacenterBroker broker0;
    private List<Vm> vmList;
    private List<Cloudlet> cloudletList;
    private Datacenter datacenter0;

    public static void main(String[] args) {
        new CostsExample1();
    }

    private CostsExample1() {
        /*Enables just some level of log messages.
          Make sure to import org.cloudsimplus.util.Log;*/
        //Log.setLevel(ch.qos.logback.classic.Level.WARN);

        simulation = new CloudSimPlus();
        datacenter0 = createDatacenter();
        broker0 = createBroker();

        vmList = createVms();
        broker0.submitVmList(vmList);

        cloudletList = createCloudlets();
        broker0.submitCloudletList(cloudletList);

        simulation.start();

        final var cloudletFinishedListList = broker0.getCloudletFinishedList();

        //Sorts cloudlets by VM id then Cloudlet id
        final Comparator<Cloudlet> vmComparator = Comparator.comparingLong(c -> c.getVm().getId());
        cloudletFinishedListList.sort(vmComparator.thenComparing(Cloudlet::getId));

        new CloudletsTableBuilder(cloudletFinishedListList).build();

        printTotalVmsCost();
    }

    /**
     * Creates a broker which is a software acting on behalf of a cloud customer to manage his/her VMs and Cloudlets.
     * It disables VMs creation retry, this way, failed VMs will be just added to the {@link DatacenterBroker#getVmFailedList()}.
     */
    private DatacenterBroker createBroker() {
        final var broker = new DatacenterBrokerSimple(simulation);
        //Destroys idle VMs after some time (in seconds)
        broker.setVmDestructionDelay(0.2);
        return broker;
    }

    /**
     * Creates a Datacenter and its Hosts.
     */
    private Datacenter createDatacenter() {
        final var hostList = new ArrayList<Host>(HOSTS);
        for(int i = 0; i < HOSTS; i++) {
            final var host = createHost();
            hostList.add(host);
        }

        //Uses a VmAllocationPolicySimple by default to allocate VMs
        final var dc = new DatacenterSimple(simulation, hostList).setSchedulingInterval(SCHEDULING_INTERVAL);

        // Those are monetary values. Consider any currency you want (such as Dollar)
        dc.setCharacteristics(new DatacenterCharacteristicsSimple(0.01, 0.02, 0.001, 0.005));
        return dc;
    }

    private Host createHost() {
        final var peList = new ArrayList<Pe>(HOST_PES);
        //List of Host's CPUs (Processing Elements, PEs)
        for (int i = 0; i < HOST_PES; i++) {
            //Uses a PeProvisionerSimple by default to provision PEs for VMs
            peList.add(new PeSimple(HOST_MIPS));
        }

        final long ram = 2048; //in Megabytes
        final long bw = 10000; //in Megabits/s
        final long storage = 1000000; //in Megabytes

        /*
        Uses ResourceProvisionerSimple by default for RAM and BW provisioning
        and VmSchedulerSpaceShared for VM scheduling.
        */
        return new HostSimple(ram, bw, storage, peList);
    }

    /**
     * Creates a list of VMs.
     */
    private List<Vm> createVms() {
        final var vmList = new ArrayList<Vm>(VMS);
        for (int id = 0; id < VMS; id++) {
            //Uses a CloudletSchedulerTimeShared by default to schedule Cloudlets
            final Vm vm = new VmSimple(id, HOST_MIPS, VM_PES);
            vm.setRam(VM_RAM).setBw(VM_BW).setSize(VM_SIZE);
            vmList.add(vm);
        }

        return vmList;
    }

    /**
     * Creates Cloudlets only for some VMs.
     */
    private List<Cloudlet> createCloudlets() {
        final var newCloudletList = new ArrayList<Cloudlet>(VMS * CLOUDLET_BY_VM);

        //UtilizationModel defining the Cloudlets use only 50% of any resource all the time
        final var utilizationModel = new UtilizationModelDynamic(0.5);

        long cloudletId = 0;
        for (int i = 1; i <= VMS/3; i++) {
            final Vm vm = vmList.get(i-1);
            for (int j = 0; j < CLOUDLET_BY_VM; j++) {
                final var cloudlet = new CloudletSimple(cloudletId, (cloudletId+1) * CLOUDLET_LENGTH, CLOUDLET_PES);
                cloudlet.setSizes(1024).setUtilizationModel(utilizationModel);
                cloudlet.setVm(vm);
                newCloudletList.add(cloudlet);
                cloudletId++;
            }
        }

        return newCloudletList;
    }

    /**
     * Computes and print the cost ($) of resources (processing, bw, memory, storage)
     * for each VM inside the datacenter.
     */
    private void printTotalVmsCost() {
        System.out.println();
        double totalCost = 0.0;
        int totalNonIdleVms = 0;
        double processingTotalCost = 0, memoryTotaCost = 0, storageTotalCost = 0, bwTotalCost = 0;
        for (final Vm vm : broker0.getVmCreatedList()) {
            final var cost = new VmCost(vm);
            processingTotalCost += cost.getProcessingCost();
            memoryTotaCost += cost.getMemoryCost();
            storageTotalCost += cost.getStorageCost();
            bwTotalCost += cost.getBwCost();

            totalCost += cost.getTotalCost();
            totalNonIdleVms += vm.getTotalExecutionTime() > 0 ? 1 : 0;
            System.out.println(cost);
        }

        System.out.printf(
            "Total cost ($) for %3d created VMs from %3d in DC %d: %8.2f$ %13.2f$ %17.2f$ %12.2f$ %15.2f$%n",
            totalNonIdleVms, broker0.getVmsNumber(), datacenter0.getId(),
            processingTotalCost, memoryTotaCost, storageTotalCost, bwTotalCost, totalCost);
    }
}
