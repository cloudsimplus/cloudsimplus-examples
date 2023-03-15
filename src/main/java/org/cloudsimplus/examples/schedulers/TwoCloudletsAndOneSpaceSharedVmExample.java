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

import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerSpaceShared;
import org.cloudsimplus.schedulers.vm.VmSchedulerTimeShared;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;

import java.util.ArrayList;
import java.util.List;

/**
 * A simple example showing how to create a data center with 1 host, 1 VM and
 * run 2 cloudlets on it that will run sequentially: first one cloudlet executes
 * until complete, after the other one do the same. Once there is only one
 * cloudlet running on the VM by time, each one uses all VM's CPU capacity while
 * executing. By this way, one cloudlet finishes prior to the other, but the
 * execution time (the time using the processor) is the same. Using the cloudlet
 * space shared scheduler, the cloudlet is not interrupted when it starts to run
 * (because the non-preemptive nature of the scheduler).
 *
 * @author Manoel Campos da Silva Filho
 * @since CloudSim Plus 1.0
 */
public class TwoCloudletsAndOneSpaceSharedVmExample {
    private List<Cloudlet> cloudletList;
    private List<Vm> vmlist;
    private CloudSimPlus simulation;

    /**
     * Creates main() to run this example.
     *
     * @param args the args
     */
    public static void main(String[] args) {
        new TwoCloudletsAndOneSpaceSharedVmExample();
    }

    private TwoCloudletsAndOneSpaceSharedVmExample(){
        /*Enables just some level of log messages.
          Make sure to import org.cloudsimplus.util.Log;*/
        //Log.setLevel(ch.qos.logback.classic.Level.WARN);

        System.out.println("Starting " + getClass().getSimpleName());

        simulation = new CloudSimPlus();

        // Datacenters are the resource providers in CloudSimPlus. We need at
        // list one of them to run a CloudSimPlus simulation
        final var datacenter0 = createDatacenter();

        final var broker = new DatacenterBrokerSimple(simulation);

        // Fourth step: Create one virtual machine
        vmlist = new ArrayList<>();

        // VM description
        int vmid = 0;
        int mips = 1000;
        long size = 10000; // image size (Megabyte)
        int ram = 512; // vm memory (Megabyte)
        long bw = 1000;
        int pesNumber = 1; // number of cpus

        final var vm = new VmSimple(vmid, mips, pesNumber)
            .setRam(ram).setBw(bw).setSize(size)
            .setCloudletScheduler(new CloudletSchedulerSpaceShared());
        vmlist.add(vm);

        // submit vm list to the broker
        broker.submitVmList(vmlist);

        // Fifth step: Create one Cloudlet
        cloudletList = new ArrayList<>();

        // Cloudlet properties
        int id = -1;
        long length = 10000;
        long fileSize = 300;
        long outputSize = 300;
        final var utilizationModel = new UtilizationModelFull();

        final var cloudlet1 = new CloudletSimple(++id, length, pesNumber)
            .setFileSize(fileSize)
            .setOutputSize(outputSize)
            .setUtilizationModel(utilizationModel)
            .setVm(vm);
        cloudletList.add(cloudlet1);

        final var cloudlet2 = new CloudletSimple(++id, length, pesNumber)
            .setFileSize(fileSize)
            .setOutputSize(outputSize)
            .setUtilizationModel(utilizationModel)
            .setVm(vm);
        cloudletList.add(cloudlet2);

        // submit cloudlet list to the broker
        broker.submitCloudletList(cloudletList);

        simulation.start();

        final var cloudletFinishedList = broker.getCloudletFinishedList();
        new CloudletsTableBuilder(cloudletFinishedList).build();
        System.out.println(getClass().getSimpleName() + " finished!");
    }
    /**
     * Creates the Datacenter.
     *
     * @return the Datacenter
     */
    private Datacenter createDatacenter() {
        final var hostList = new ArrayList<Host>();
        final var peList = new ArrayList<Pe>();

        final long mips = 1000;

        peList.add(new PeSimple(mips));

        final long ram = 20000; //in Megabytes
        final long bw = 100000; //in Megabytes
        final long storage = 10000000; //in Megabytes
        final var host = new HostSimple(ram, bw, storage, peList)
            .setVmScheduler(new VmSchedulerTimeShared());

        hostList.add(host);

        return new DatacenterSimple(simulation, hostList);
    }
}
