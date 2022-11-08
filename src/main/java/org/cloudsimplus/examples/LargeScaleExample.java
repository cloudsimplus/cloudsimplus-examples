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
import org.cloudbus.cloudsim.allocationpolicies.VmAllocationPolicyFirstFit;
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
import org.cloudsimplus.util.Log;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.cloudbus.cloudsim.util.TimeUtil.elapsedSeconds;
import static org.cloudbus.cloudsim.util.TimeUtil.secondsToStr;

/**
 * An example creating a huge number of Hosts, VMs and Cloudlets
 * to simulate a large-scale cloud infrastructure.
 *
 * <p>The example may run out of memory.
 * Try to increase heap memory space passing, for instance,
 * -Xmx6g to the java command line, where 6g means 6GB of maximum heap size.</p>
 *
 * <p>Your computer may not even have enough memory capacity to run this example
 * and it may just crashes with OutOfMemoryException.</p>
 *
 * <p>Some factors that drastically impact simulation performance and memory consumption
 * is the {@link #CLOUDLETS} number and {@link #SCHEDULING_INTERVAL}.</p>
 *
 * @author Manoel Campos da Silva Filho
 * @since ClodSimPlus 7.3.1
 */
public class LargeScaleExample {
    private static final int  HOSTS = 200_000;
    private static final int  HOST_PES = 16;
    private static final int  HOST_MIPS = 1000;
    private static final int  HOST_RAM = 2048; //in Megabytes
    private static final long HOST_BW = 10_000; //in Megabits/s
    private static final long HOST_STORAGE = 1_000_000; //in Megabytes

    private static final int VMS = HOSTS*4;
    private static final int VM_PES = HOST_PES/4;

    private static final int CLOUDLETS = VMS;
    private static final int CLOUDLET_PES = VM_PES/2;
    private static final int CLOUDLET_LENGTH = HOST_MIPS * 10;

    /**
     * Defines a time interval to process cloudlets execution
     * and possibly collect data. Setting a value greater than 0
     * enables that interval, which cause huge performance penaults for
     * lage scale simulations.
     *
     * @see Datacenter#setSchedulingInterval(double)
     */
    private static final double SCHEDULING_INTERVAL = -1;

    private final CloudSim simulation;
    private final DatacenterBroker broker0;
    private final List<Vm> vmList;
    private final List<Cloudlet> cloudletList;
    private final Datacenter datacenter0;
    private final double startSecs;

    public static void main(String[] args) {
        new LargeScaleExample();
    }

    private LargeScaleExample() {
        // Disable logging for performance improvements.
        Log.setLevel(Level.OFF);

        this.startSecs = System.currentTimeMillis()/1000.0;
        System.out.println("Creating simulation scenario at " + LocalDateTime.now());
        System.out.printf("Creating 1 Datacenter -> Hosts: %,d VMs: %,d Cloudlets: %,d%n", HOSTS, VMS, CLOUDLETS);

        simulation = new CloudSim();
        datacenter0 = createDatacenter();

        //Creates a broker that is a software acting on behalf a cloud customer to manage his/her VMs and Cloudlets
        broker0 = new DatacenterBrokerSimple(simulation);

        vmList = createVms();
        cloudletList = createCloudlets();
        brokerSubmit();

        System.out.println("Starting simulation after " + actualElapsedTime());
        simulation.start();

        final long submittedCloudlets = broker0.getCloudletSubmittedList().size();
        final long finishedCloudlets = broker0.getCloudletFinishedList().size();
        System.out.printf("Submitted Cloudlets: %d Finished Cloudlets: %d%n", submittedCloudlets, finishedCloudlets);

        System.out.printf(
            "Simulated time: %s Actual Execution Time: %s%n", simulatedTime(), actualElapsedTime());
    }

    private String simulatedTime() {
        return secondsToStr(simulation.clock());
    }

    private String actualElapsedTime() {
        return secondsToStr(elapsedSeconds(startSecs));
    }

    private void brokerSubmit() {
        System.out.printf("Submitting %,d VMs%n", VMS);
        broker0.submitVmList(vmList);

        System.out.printf("Submitting %,d Cloudlets%n", CLOUDLETS);
        broker0.submitCloudletList(cloudletList);
    }

    /**
     * Creates a Datacenter and its Hosts.
     */
    private Datacenter createDatacenter() {
        final var hostList = new ArrayList<Host>(HOSTS);
        System.out.printf("Creating %,d Hosts%n", HOSTS);
        for(int i = 0; i < HOSTS; i++) {
            final var host = createHost();
            hostList.add(host);
        }

        var dc = new DatacenterSimple(simulation, hostList, new VmAllocationPolicyFirstFit());
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

    /**
     * Creates a list of VMs.
     */
    private List<Vm> createVms() {
        final var vmList = new ArrayList<Vm>(VMS);
        System.out.printf("Creating %,d VMs%n", VMS);
        for (int i = 0; i < VMS; i++) {
            //Uses a CloudletSchedulerTimeShared by default to schedule Cloudlets
            final var vm = new VmSimple(HOST_MIPS, VM_PES);
            vm.setRam(512).setBw(1000).setSize(10_000);
            vmList.add(vm);
        }

        return vmList;
    }

    /**
     * Creates a list of Cloudlets.
     */
    private List<Cloudlet> createCloudlets() {
        final var cloudletList = new ArrayList<Cloudlet>(CLOUDLETS);

        //UtilizationModel defining the Cloudlets use only 50% of any resource all the time
        final var utilizationModel = new UtilizationModelDynamic(0.5);

        System.out.printf("Creating %,d Cloudlets%n", CLOUDLETS);
        for (int i = 0; i < CLOUDLETS; i++) {
            final var cloudlet = new CloudletSimple(CLOUDLET_LENGTH, CLOUDLET_PES, utilizationModel);
            cloudlet.setSizes(1024);
            cloudletList.add(cloudlet);
        }

        return cloudletList;
    }
}
