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
package org.cloudsimplus.examples.brokers;

import org.cloudsimplus.allocationpolicies.VmAllocationPolicyBatchPlacementUnderloadedHosts;
import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.core.Identifiable;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.listeners.EventInfo;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.selectionpolicies.VmSelectionPolicyMinimumUtilization;
import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;

import java.util.ArrayList;
import java.util.List;

import static java.util.stream.Collectors.joining;

/**
 * An example showing how to enable a {@link DatacenterBroker} to
 * request creation of a Vm List in batch, instead of in a one-by-one basis.
 * It can use any {@link org.cloudsimplus.allocationpolicies.VmAllocationPolicy}
 * but it uses the {@link VmAllocationPolicyBatchPlacementUnderloadedHosts}
 * as an example.
 * Check {@link DatacenterBroker#setBatchVmCreation(boolean)}
 *
 * @author Manoel Campos da Silva Filho
 * @since CloudSim Plus 8.5.0
 */
public class DatacenterBrokerBatchVmCreationRequestExample {
    private static final int  HOSTS = 2;
    private static final int  HOST_PES = 8;
    private static final int  HOST_MIPS = 1000; // Million Instructions per Second (MIPS)
    private static final int  HOST_RAM = 2048; //in Megabytes
    private static final long HOST_BW = 10_000; //in Megabits/s
    private static final long HOST_STORAGE = 1_000_000; //in Megabytes

    private static final int VMS = 2;
    private static final int VM_PES = 4;

    private static final int CLOUDLET_PES = 2;
    private static final int CLOUDLET_LENGTH = 10_000; // Million Instructions (MI)

    private final CloudSimPlus simulation;
    private final DatacenterBroker broker0;
    private List<Vm> vmList;
    private List<Cloudlet> cloudletList;
    private Datacenter datacenter0;

    /** @see Datacenter#setSchedulingInterval(double) */
    private static final double SCHEDULING_INTERVAL = 1;

    /**
     * Indicates wether no cloudlets were created during simulation runtime yet.
     */
    private boolean noDynamicCloudletsCreated = true;

    public static void main(String[] args) {
        new DatacenterBrokerBatchVmCreationRequestExample();
    }

    private DatacenterBrokerBatchVmCreationRequestExample() {
        /*Enables just some level of log messages.
          Make sure to import org.cloudsimplus.util.Log;*/
        //Log.setLevel(ch.qos.logback.classic.Level.WARN);

        simulation = new CloudSimPlus();
        datacenter0 = createDatacenter();

        //Creates a broker that is a software acting on behalf of a cloud customer to manage his/her VMs and Cloudlets
        broker0 = createBroker();

        vmList = new ArrayList<>();
        cloudletList = new ArrayList<>();
        createVmsAndCloudlets();

        simulation.addOnClockTickListener(this::onClockTick);
        simulation.start();

        final var cloudletFinishedList = broker0.getCloudletFinishedList();
        new CloudletsTableBuilder(cloudletFinishedList).build();
    }

    /**
     * {@return a new broker} Enables batch VM creation.
     */
    private DatacenterBroker createBroker() {
        return new DatacenterBrokerSimple(simulation).setBatchVmCreation(true);
    }

    /**
     * @param info
     * @see Datacenter#setSchedulingInterval(double)
     */
    private void onClockTick(final EventInfo info) {
        if(info.getTime() >= 10 && noDynamicCloudletsCreated) {
            noDynamicCloudletsCreated = false;
            final var newVmList = createAndSubmitVms();
            final var newCloudlets = createAndSubmitCloudlets(newVmList);
            final String vmIds = getEntityId(newVmList);
            final String cloudletIds = getEntityId(newCloudlets);
            System.out.printf(
                "%.2f: Submitting %d VMs and %d Cloudlets during simulation runtime. VM IDs: %s | Cloudlet IDs: %s %n%n",
                info.getTime(), newVmList.size(), newCloudlets.size(), vmIds, cloudletIds);
        }
    }

    private static String getEntityId(final List<? extends Identifiable> entities) {
        return entities.stream().map(Identifiable::getId).map(String::valueOf).collect(joining(", "));
    }

    private void createVmsAndCloudlets() {
        final var newVms = createAndSubmitVms();
        createAndSubmitCloudlets(newVms);
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

        final var vmAllocationPolicy = new VmAllocationPolicyBatchPlacementUnderloadedHosts(new VmSelectionPolicyMinimumUtilization());
        final var dc = new DatacenterSimple(simulation, hostList, vmAllocationPolicy);
        dc.setSchedulingInterval(SCHEDULING_INTERVAL);
        return dc;
    }

    private Host createHost() {
        final var peList = new ArrayList<Pe>(HOST_PES);
        //List of Host's CPUs (Processing Elements, PEs)
        for (int i = 0; i < HOST_PES; i++) {
            //Uses a PeProvisionerSimple by default to provision PEs for VMs
            peList.add(new PeSimple(HOST_MIPS));
        }

        /*
        Uses ResourceProvisionerSimple by default for RAM and BW provisioning
        and VmSchedulerSpaceShared for VM scheduling.
        */
        return new HostSimple(HOST_RAM, HOST_BW, HOST_STORAGE, peList);
    }

    private ArrayList<Vm> createAndSubmitVms() {
        final var newVms = new ArrayList<Vm>(VMS);
        for (int i = 0; i < VMS; i++) {
            //Uses a CloudletSchedulerTimeShared by default to schedule Cloudlets
            final var vm = new VmSimple(HOST_MIPS, VM_PES);
            vm.setRam(512).setBw(1000).setSize(10_000);
            newVms.add(vm);
        }

        vmList.addAll(newVms);
        broker0.submitVmList(newVms);
        return newVms;
    }

    private List<Cloudlet> createAndSubmitCloudlets(final List<Vm> vms) {
        final var newCloulets = new ArrayList<Cloudlet>(vms.size());

        //UtilizationModel defining the Cloudlets use only 50% of any resource all the time
        final var utilizationModel = new UtilizationModelDynamic(0.5);

        for (final var vm : vms) {
            final var cloudlet = new CloudletSimple(CLOUDLET_LENGTH, CLOUDLET_PES, utilizationModel);
            cloudlet.setSizes(1024);
            broker0.bindCloudletToVm(cloudlet, vm);
            newCloulets.add(cloudlet);
        }

        cloudletList.addAll(newCloulets);
        broker0.submitCloudletList(newCloulets);
        return newCloulets;
    }
}
