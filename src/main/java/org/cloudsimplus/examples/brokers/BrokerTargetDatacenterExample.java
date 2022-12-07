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

import org.cloudbus.cloudsim.brokers.DatacenterBroker;
import org.cloudbus.cloudsim.brokers.DatacenterBrokerSimple;
import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.datacenters.Datacenter;
import org.cloudbus.cloudsim.datacenters.DatacenterSimple;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.hosts.HostSimple;
import org.cloudbus.cloudsim.resources.Pe;
import org.cloudbus.cloudsim.resources.PeSimple;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelDynamic;
import org.cloudbus.cloudsim.vms.Vm;
import org.cloudbus.cloudsim.vms.VmSimple;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * An example showing how to make the {@link DatacenterBrokerSimple}
 * to set a specific target {@link Datacenter} to place VMs,
 * before trying the next Datacenter if VM placement fails
 * (due to lack of suitable Hosts).
 *
 * <p>You can see that VM 11 submitted to Broker 6 targeting Datacenter 3
 * could not be placed into that DC due to lack of suitable Hosts.
 * Then, the next Datacenter was selected, in a round-robin (circular) way.
 * </p>
 *
 * @author Manoel Campos da Silva Filho
 * @since CloudSim Plus 7.3.2
 * @see #createBrokersVmsAndCloudlets()
 */
public class BrokerTargetDatacenterExample {
    private static final int DATACENTERS = 3;
    private static final int HOSTS_BY_DC = 5;
    private static final int HOST_PES = 6;

    private static final int BASE_VM_NUMBER = 2;
    private static final int VM_PES = 4;

    private static final int CLOUDLET_PES = 2;
    private static final int CLOUDLET_LENGTH = 10_000;

    private final CloudSim simulation;
    private List<DatacenterBroker> brokerList;
    private List<Datacenter> datacenterList;
    private long lastHostId;
    private int lastVmId;
    private int lastCloudletId;

    public static void main(String[] args) {
        new BrokerTargetDatacenterExample();
    }

    private BrokerTargetDatacenterExample() {
        /*Enables just some level of log messages.
          Make sure to import org.cloudsimplus.util.Log;*/
        //Log.setLevel(ch.qos.logback.classic.Level.WARN);

        simulation = new CloudSim();
        brokerList = new ArrayList<>();
        datacenterList = createDatacenters();
        createBrokersVmsAndCloudlets();


        simulation.start();

        brokerList.forEach(BrokerTargetDatacenterExample::createCloudletsResultTable);
    }

    /**
     * Creates a broker for each available Datacenter
     * and submit VMs and Cloudlets.
     */
    private void createBrokersVmsAndCloudlets() {
        for (final var dc : datacenterList) {
            final var broker = createBroker(dc);

            /* Creates more VMs for each datacenter.
             * This way, some target datacenter won't have capacity to place
             * all VMs and the next one will be tried. */
            final int vmsNumber = (int)(BASE_VM_NUMBER * dc.getId());
            final var vmList = createVms(vmsNumber);
            final var cloudletList = createCloudlets(vmsNumber);
            broker.submitVmList(vmList);
            broker.submitCloudletList(cloudletList);
            brokerList.add(broker);
        }
    }

    /**
     * Creates a broker that sets a target Datacenter to initially place arriving VMs.
     * @param dc the target datacenter
     * @return the new broker
     */
    private DatacenterBrokerSimple createBroker(final Datacenter dc) {
        final var broker = new DatacenterBrokerSimple(simulation);
        broker.setName("Broker %d for %s".formatted(broker.getId(), dc));

        // Sets the initial target Datacenter
        broker.setLastSelectedDc(dc);
        return broker;
    }

    private static void createCloudletsResultTable(final DatacenterBroker broker) {
        new CloudletsTableBuilder(broker.getCloudletFinishedList())
                .setTitle(broker.getName())
                .build();
    }

    private List<Datacenter> createDatacenters(){
        return IntStream.range(0, DATACENTERS)
                        .mapToObj(i -> createDatacenter())
                        .toList();
    }

    /**
     * Creates a Datacenter and its Hosts.
     */
    private Datacenter createDatacenter() {
        final var hostList = IntStream.range(0, HOSTS_BY_DC)
                                      .mapToObj(i -> createHost())
                                      .toList();

        //Uses a VmAllocationPolicySimple by default to allocate VMs
        return new DatacenterSimple(simulation, hostList);
    }

    private Host createHost() {
        final var peList = new ArrayList<Pe>(HOST_PES);
        for (int i = 0; i < HOST_PES; i++) {
            peList.add(new PeSimple(1000));
        }

        final long ram = 2048; //in Megabytes
        final long bw = 10000; //in Megabits/s
        final long storage = 1000000; //in Megabytes

        /*
        Uses ResourceProvisionerSimple by default for RAM and BW provisioning
        and VmSchedulerSpaceShared for VM scheduling.
        */
        final Host host = new HostSimple(ram, bw, storage, peList);
        host.setId(lastHostId++);
        return host;
    }

    /**
     * Creates a list of VMs.
     */
    private List<Vm> createVms(final int vmsNumber) {
        final var vms = new ArrayList<Vm>(vmsNumber);
        for (int i = 0; i < vmsNumber; i++) {
            //Uses a CloudletSchedulerTimeShared by default to schedule Cloudlets
            final Vm vm = new VmSimple(lastVmId++, 1000, VM_PES);
            vm.setRam(512).setBw(1000).setSize(10000);
            vms.add(vm);
        }

        return vms;
    }

    /**
     * Creates a list of Cloudlets.
     */
    private List<Cloudlet> createCloudlets(int cloudletsNumber) {
        final var cloudlets = new ArrayList<Cloudlet>(cloudletsNumber);

        //UtilizationModel defining the Cloudlets use only 50% of any resource all the time
        final var utilizationModel = new UtilizationModelDynamic(0.5);

        for (int i = 0; i < cloudletsNumber; i++) {
            final var cloudlet = new CloudletSimple(lastCloudletId++, CLOUDLET_LENGTH, CLOUDLET_PES);
            cloudlet.setUtilizationModel(utilizationModel);
            cloudlets.add(cloudlet);
        }

        return cloudlets;
    }
}
