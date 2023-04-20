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

import org.cloudsimplus.allocationpolicies.VmAllocationPolicy;
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
import org.cloudsimplus.listeners.EventInfo;
import org.cloudsimplus.listeners.EventListener;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerTimeShared;
import org.cloudsimplus.schedulers.vm.VmSchedulerTimeShared;
import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;

import java.util.ArrayList;
import java.util.List;

/**
 * An example showing how to create Hosts at simulation runtime,
 * enabling to simulate the physical expansion of a Datacenter by
 * the addition of new Hosts (PMs).
 * The example starts by creating 2 hosts and 4 VMs (2 VMs for each Host).
 * Then it creates 4 Cloudlets (1 for each VM).
 *
 * <p>After the simulation starts and reaches 5 seconds (defined in {@link #SCHEDULING_INTERVAL}),
 * it's created a new Host and VM. Two new Cloudlets are created for this new VM
 * the Cloudlets and the VM is submitted by the broker to the Datacenter.
 * Since the Hosts created before starting the simulation
 * don't have enough PEs to place this new VM, the {@link VmAllocationPolicy}
 * will chose the 5th Host (created dynamically) to place that VM
 * and run the Cloudlets.
 * </p>
 *
 * <p>Since these two new Cloudlets are running in the same VM,
 * which is using a {@link CloudletSchedulerTimeShared}, and there aren't
 * enough VM PEs for all the 2 Cloudlets, they will spend the double of the time to finish,
 * because they will share the VM PEs.
 * Realize that the start time of these 2 dynamically created Cloudlets is 5,
 * showing they were submitted to the broker just that time.
 * </p>
 *
 * <p>To allow track the simulation time and know when the clock is in 5 seconds,
 * it is required to call {@link Datacenter#setSchedulingInterval(double)}
 * to set the scheduling interval. Check the method documentation for more details.
 * This example uses the CloudSim Plus Listener features to enable your simulation
 * to be notified when some event happens.
 * In this case, it's being used the {@link CloudSimPlus#addOnClockTickListener(EventListener)}
 * to pass a {@link EventListener} object that will be notified
 * when the simulation clock advances.
 * </p>
 *
 * <p>In fact, this {@link EventListener} is a Java 8+ {@link FunctionalInterface}
 * that enables using Lambda Expression
 * or method references in order to provide a {@link FunctionalInterface}.
 * This example uses a reference to the {@link #clockTickListener(EventInfo)} method
 * as being the {@link EventListener} required by the {@link CloudSimPlus#addOnClockTickListener(EventListener)}.</p>
 *
 * <p><b>Lambda Expressions, method references and Functional Interfaces are Java 8+ features.</b>
 * If you don't know what these features are, I suggest checking out this
 * <a href="http://www.oracle.com/webfolder/technetwork/tutorials/obe/java/Lambda-QuickStart/index.html">tutorial</a></p>
 *
 * @author Manoel Campos da Silva Filho
 * @since CloudSim Plus 1.3.0
 */
public class DynamicHostCreation {
    /**
     * @see Datacenter#getSchedulingInterval()
     */
    private static final int SCHEDULING_INTERVAL = 5;

    private static final int HOSTS = 2;
    private static final int HOST_PES = 4;

    private static final int VM_PES = 2;

    private static final int CLOUDLET_PES = 2;
    private static final int CLOUDLET_LENGTH = 10000;

    private final CloudSimPlus simulation;
    private final DatacenterBroker broker0;
    private List<Vm> vmList;
    private List<Cloudlet> cloudletList;
    private Datacenter datacenter0;

    public static void main(String[] args) {
        new DynamicHostCreation();
    }

    private DynamicHostCreation() {
        /*Enables just some level of log messages.
          Make sure to import org.cloudsimplus.util.Log;*/
        //Log.setLevel(ch.qos.logback.classic.Level.WARN);

        simulation = new CloudSimPlus();
        datacenter0 = createDatacenter();

        //Creates a broker that is a software acting on behalf of a cloud customer to manage his/her VMs and Cloudlets
        broker0 = new DatacenterBrokerSimple(simulation);

        vmList = createVms(4);
        cloudletList = createCloudlets(4);

        broker0.submitVmList(vmList);
        broker0.submitCloudletList(cloudletList);

        simulation.addOnClockTickListener(this::clockTickListener);
        simulation.start();

        final var cloudletFinishedList = broker0.getCloudletFinishedList();
        new CloudletsTableBuilder(cloudletFinishedList).build();
    }

    /**
     * Event listener which is called every time the simulation clock advances.
     * @param info information about the event happened.
     */
    private void clockTickListener(final EventInfo info) {
        final int time = (int)info.getTime();

        /* Checks if the time specified in the scheduling interval has passed.
         * This way, creates a new PM just once at that time. */
        if(time == SCHEDULING_INTERVAL) {
            final var host = createHost();
            datacenter0.addHost(host);
            System.out.printf("%n %.2f: # Physically expanding the %s by adding the new %s to it.", info.getTime(), datacenter0, host);

            //Creates and submits a new VM
            final var vm = createVm(vmList.size());
            System.out.printf("%.2f: # Created %s%n", info.getTime(), vm);
            broker0.submitVm(vm);

            //Creates and submits 2 Cloudlets, binding them to the new VM
            final var newCloudletList = createCloudlets(2);
            broker0.submitCloudletList(newCloudletList, vm);
            System.out.printf("%.2f: # Created %d Cloudlets for %s%n", info.getTime(), newCloudletList.size(), vm);

            /* Removes the listener so that this event is fired just once.
             * This way, it's ensured the new Host, VMs and Cloudlets are created only one time. */
            simulation.removeOnClockTickListener(info.getListener());
        }
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

        final var dc = new DatacenterSimple(simulation, hostList);
        dc.setSchedulingInterval(SCHEDULING_INTERVAL);
        return dc;
    }

    private Host createHost() {
        final var peList = new ArrayList<Pe>(HOST_PES);
        //List of Host's CPUs (Processing Elements, PEs)
        for (int i = 0; i < HOST_PES; i++) {
            peList.add(new PeSimple(1000));
        }

        final long ram = 2048; //in Megabytes
        final long bw = 10000; //in Megabits/s
        final long storage = 1000000; //in Megabytes
        final var vmScheduler = new VmSchedulerTimeShared();
        final var host = new HostSimple(ram, bw, storage, peList);
        host.setVmScheduler(vmScheduler);
        return host;
    }

    /**
     * Creates a list of VMs.
     */
    private List<Vm> createVms(final int count) {
        final var newVmList = new ArrayList<Vm>(count);
        for (int i = 0; i < count; i++) {
            newVmList.add(createVm(i));
        }

        return newVmList;
    }

    private Vm createVm(final int id) {
        return new VmSimple(id,1000, VM_PES)
            .setRam(512).setBw(1000).setSize(10000)
            .setCloudletScheduler(new CloudletSchedulerTimeShared());
    }

    private List<Cloudlet> createCloudlets(final int count) {
        final var newCloudletList = new ArrayList<Cloudlet>(count);
        final var utilization = new UtilizationModelFull();
        for (int i = 0; i < count; i++) {
            final var cloudlet =
                new CloudletSimple(CLOUDLET_LENGTH, CLOUDLET_PES)
                    .setFileSize(1024)
                    .setOutputSize(1024)
                    .setUtilizationModelCpu(new UtilizationModelFull())
                    .setUtilizationModelRam(new UtilizationModelDynamic(0.2));
            newCloudletList.add(cloudlet);
        }

        return newCloudletList;
    }
}
