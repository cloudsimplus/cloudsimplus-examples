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

import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
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
 * An example showing how to create 1 host and place multiple VMs at the same
 * {@link Pe Processor Element (CPU core)} of it,
 * using a VmSchedulerTimeShared policy at the Host.
 *
 * <p>The number of VMs to be created will be the double of the Host PEs number.
 * For each Host PE, two VMs requiring half the MIPS capacity of the PE will be
 * created. Each VM will have just one cloudlet that will use
 * all VM PEs and MIPS capacity.</p>
 *
 * <p>Thus, considering that each cloudlet has a length of 10000 MI and
 * each VM has a PE of 1000 MIPS, the cloudlet will spend 10 seconds to finish.
 * However, as each Host PE will be shared between two VMs using a time shared
 * scheduler, the cloudlet will spend the double of the time to finish,
 * as can be seen in the simulation results after running the example.</p>
 *
 * @author Manoel Campos da Silva Filho
 * @since CloudSim Plus 1.0
 */
public class SharingHostPEsUsingVmSchedulerTimeSharedExample {
    /**
     * Capacity of each CPU core (in Million Instructions per Second).
     */
    private static final long HOST_MIPS = 1000;
    /**
     * Number of processor elements (CPU cores) of each host.
     */
    private static final int HOST_PES_NUM = 2;

    /**
     * The total MIPS capacity across all the Host PEs.
     */
    private static final long HOST_TOTAL_MIPS_CAPACITY = HOST_MIPS*HOST_PES_NUM;

    /**
     * The length of each created cloudlet in Million Instructions (MI).
     */
    private static final long CLOUDLET_LENGTH = 10000;

    /**
     * Number of VMs to create.
     */
    private static final int NUMBER_OF_VMS = HOST_PES_NUM*2;

    private static final long VM_MIPS = HOST_TOTAL_MIPS_CAPACITY/NUMBER_OF_VMS;
    private final CloudSimPlus simulation;

    private List<Cloudlet> cloudletList;
    private List<Vm> vmList;
    private int numberOfCreatedCloudlets = 0;
    private int numberOfCreatedVms = 0;
    private int numberOfCreatedHosts = 0;

    public static void main(String[] args) {
        new SharingHostPEsUsingVmSchedulerTimeSharedExample();
    }

    /**
     * Default constructor where the simulation is built.
     */
    private SharingHostPEsUsingVmSchedulerTimeSharedExample() {
        /*Enables just some level of log messages.
          Make sure to import org.cloudsimplus.util.Log;*/
        //Log.setLevel(ch.qos.logback.classic.Level.WARN);

        System.out.println("Starting " + getClass().getSimpleName());
        simulation = new CloudSimPlus();

        this.vmList = new ArrayList<>();
        this.cloudletList = new ArrayList<>();

        final var datacenter0 = createDatacenter();

        /*Creates a Broker accountable for submission of VMs and Cloudlets
        on behalf of a given cloud user (customer).*/
        final var broker0 = new DatacenterBrokerSimple(simulation);

        createAndSubmitVmsAndCloudlets(broker0);

        /*Starts the simulation and waits all cloudlets to be executed*/
        simulation.start();

        /*Prints results when the simulation is over
        (you can use your own code here to print what you want from this cloudlet list)*/
        final var cloudletFinishedList = broker0.getCloudletFinishedList();
        new CloudletsTableBuilder(cloudletFinishedList).build();
        System.out.println(getClass().getSimpleName() + " finished!");
    }

    private void createAndSubmitVmsAndCloudlets(final DatacenterBroker broker0) {
        for(int i = 0; i < NUMBER_OF_VMS; i++){
            final var vm = createVm(broker0, VM_MIPS, 1);
            this.vmList.add(vm);

            /*Creates a cloudlet that represents an application to be run inside a VM.*/
            final var cloudlet = createCloudlet(broker0, vm);
            this.cloudletList.add(cloudlet);
        }

        broker0.submitVmList(vmList);
        broker0.submitCloudletList(cloudletList);
    }

    private DatacenterSimple createDatacenter() {
        final var hostList = new ArrayList<Host>();
        final var host0 = createHost();
        hostList.add(host0);

        return new DatacenterSimple(simulation, hostList);
    }

    private Host createHost() {
        // capacity of each CPU core (in Million Instructions per Second)
        final long ram = 2048; // in Megabytes
        final long storage = 1000000; // in Megabytes
        final long bw = 10000; //in Megabits/s

        final var peList = new ArrayList<Pe>();
        /*Creates the Host's CPU cores and defines the provisioner
        used to allocate each core for requesting VMs.*/
        for(int i = 0; i < HOST_PES_NUM; i++){
            peList.add(new PeSimple(HOST_MIPS));
        }

        return new HostSimple(ram, bw, storage, peList).setVmScheduler(new VmSchedulerTimeShared());
    }

    private Vm createVm(DatacenterBroker broker, long mips, int pesNumber) {
        final long storage = 10000; // vm image size (Megabyte)
        final int  ram = 512; // vm memory (Megabyte)
        final long bw = 1000; // vm bandwidth

        return new VmSimple(numberOfCreatedVms++, mips, pesNumber)
            .setRam(ram).setBw(bw).setSize(storage)
            .setCloudletScheduler(new CloudletSchedulerTimeShared());

    }

    private Cloudlet createCloudlet(DatacenterBroker broker, Vm vm) {
        final long fileSize = 300; //Size (in bytes) before execution
        final long outputSize = 300; //Size (in bytes) after execution
        final long  numberOfCpuCores = vm.getPesNumber(); //cloudlet will use all the VM's CPU cores

        //Defines how CPU, RAM and Bandwidth resources are used
        //Sets the same utilization model for all these resources.
        final var utilization = new UtilizationModelFull();

        return new CloudletSimple(numberOfCreatedCloudlets++, CLOUDLET_LENGTH, numberOfCpuCores)
            .setFileSize(fileSize)
            .setOutputSize(outputSize)
            .setUtilizationModel(utilization)
            .setVm(vm);
    }
}
