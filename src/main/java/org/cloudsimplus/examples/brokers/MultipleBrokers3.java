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
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerTimeShared;
import org.cloudsimplus.schedulers.vm.VmSchedulerTimeShared;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;

import java.util.ArrayList;
import java.util.List;

/**
 * An example showing how to create multiple VMs and Cloudlets for different customers,
 * each one represented by a {@link DatacenterBroker} object.
 * The example creates VMs and Cloudlets without defining an ID, which
 * are defined when such objects are submitted to their brokers.
 *
 * @author Manoel Campos da Silva Filho
 * @since CloudSim Plus 1.3.1
 */
public class MultipleBrokers3 {
    private static final int HOSTS = 2;
    private static final int HOST_PES = 8;

    private static final int BROKERS = 2;

    private static final int VMS = 2;
    private static final int VM_PES = 4;

    private static final int CLOUDLETS = 2;
    private static final int CLOUDLET_PES = 2;
    private static final int CLOUDLET_LENGTH = 10000;

    private final CloudSimPlus simulation;
    private final List<DatacenterBroker> brokers;
    private final List<Vm> vmList;
    private final List<Cloudlet> cloudletList;
    private final Datacenter datacenter0;

    public static void main(String[] args) {
        new MultipleBrokers3();
    }

    private MultipleBrokers3() {
        /*Enables just some level of log messages.
          Make sure to import org.cloudsimplus.util.Log;*/
        //Log.setLevel(ch.qos.logback.classic.Level.WARN);

        simulation = new CloudSimPlus();
        datacenter0 = createDatacenter();
        brokers = createBrokers();

        vmList = new ArrayList<>(BROKERS*VMS);
        cloudletList = new ArrayList<>(CLOUDLETS*VMS);
        createVmsAndCloudlets();

        simulation.start();
        printResults();
    }

    private void createVmsAndCloudlets() {
        int i = 0;
        for (var broker : brokers) {
            vmList.addAll(createAndSubmitVms(broker));
            cloudletList.addAll(createAndSubmitCloudlets(broker));
        }
    }

    private void printResults() {
        for (var broker : brokers) {
            new CloudletsTableBuilder(broker.getCloudletFinishedList())
                .setTitle(broker.getName())
                .build();
        }

        System.out.println();
        for (Vm vm : vmList) {
            System.out.printf(
                "%s -> Start Time: %.0f Finish Time: %.0f Total Execution Time: %.0f%n",
                vm, vm.getStartTime(), vm.getFinishTime(), vm.getTotalExecutionTime());
        }
        System.out.println();
    }

    private List<DatacenterBroker> createBrokers() {
        final var brokerList = new ArrayList<DatacenterBroker>(BROKERS);
        for(int i = 0; i < BROKERS; i++) {
            brokerList.add(new DatacenterBrokerSimple(simulation));
        }

        return brokerList;
    }

    /**
     * Creates a Datacenter and its Hosts.
     */
    private Datacenter createDatacenter() {
        final var hostList = new ArrayList<Host>(HOSTS);
        for(int i = 0; i < HOSTS; i++) {
            hostList.add(createHost());
        }

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
        final var host = new HostSimple(ram, bw, storage, peList);
        host
            .setRamProvisioner(new ResourceProvisionerSimple())
            .setBwProvisioner(new ResourceProvisionerSimple())
            .setVmScheduler(new VmSchedulerTimeShared());
        return host;
    }

    private List<Vm> createAndSubmitVms(DatacenterBroker broker) {
        final var vmList = new ArrayList<Vm>(VMS);
        for (int i = 0; i < VMS; i++) {
            final var vm =
                new VmSimple(1000, VM_PES)
                    .setRam(512).setBw(1000).setSize(10000)
                    .setCloudletScheduler(new CloudletSchedulerTimeShared());

            vmList.add(vm);
        }

        broker.submitVmList(vmList);

        return vmList;
    }

    private List<Cloudlet> createAndSubmitCloudlets(DatacenterBroker broker) {
        final var cloudletList = new ArrayList<Cloudlet>(CLOUDLETS);
        for (int i = 1; i <= CLOUDLETS; i++) {
            final var utilizationModel = new UtilizationModelFull();
            final var cloudlet = new CloudletSimple(CLOUDLET_LENGTH, CLOUDLET_PES);
            cloudlet
                .setFileSize(1024)
                .setOutputSize(1024)
                .setUtilizationModel(utilizationModel);
            cloudletList.add(cloudlet);
        }

        broker.submitCloudletList(cloudletList);
        return cloudletList;
    }
}
