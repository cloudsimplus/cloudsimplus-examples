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
package org.cloudsimplus.examples.synchronous;

import ch.qos.logback.classic.Level;
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
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.util.Log;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * An example showing how to run the simulation synchronously,
 * (by calling {@link CloudSimPlus#startSync()}).
 * This way, the researcher can interact with the simulation,
 * for instance to collect data.
 *
 * <p>A synchronous simulation is similar to setting a {@link Datacenter#setSchedulingInterval(double) scheduling interval}:
 * the simulation clock will be increased at the pace of the given interval.
 * However, using the {@link CloudSimPlus#startSync()} we can collect
 * simulation data inside a loop,
 * without requiring to use {@link org.cloudsimplus.listeners.EventListener}s for that.
 * </p>
 *
 * <p>In this example, we are collecting VMs' CPU utilization inside a loop,
 * after each call of the {@link CloudSimPlus#runFor(double)} method.</p>
 *
 * @author Pawel Koperek
 * @author Manoel Campos da Silva Filho
 * @since CloudSim Plus 4.5.0
 */
public class SynchronousSimulationExample1 {
    /**
     * Defines the time (in seconds) to run the simulation for.
     * The clock is increased in the interval defined here.
     */
    private static final double INTERVAL = 2;
    private static final int HOSTS = 2;
    private static final int HOST_PES = 4;

    private static final int VMS = 4;
    private static final int VM_PES = 2;

    private static final int CLOUDLETS = 8;
    private static final int CLOUDLET_PES = 2;
    private static final int CLOUDLET_LENGTH = 10000;

    private final CloudSimPlus simulation;
    private final DatacenterBroker broker0;
    private List<Vm> vmList;
    private List<Cloudlet> cloudletList;
    private Datacenter datacenter0;
    private double previousClock;

    public static void main(String[] args) {
        new SynchronousSimulationExample1();
    }

    private SynchronousSimulationExample1() {
        Log.setLevel(Level.WARN);

        simulation = new CloudSimPlus();
        datacenter0 = createDatacenter();

        //Creates a broker that is a software acting on behalf of a cloud customer to manage his/her VMs and Cloudlets
        broker0 = new DatacenterBrokerSimple(simulation);

        vmList = createVms();
        cloudletList = createCloudlets();
        broker0.submitVmList(vmList);
        broker0.submitCloudletList(cloudletList);

        //Sets a termination time, trying to stop the simulation before such a deadline
        //simulation.terminateAt(20);

        simulation.startSync();
        while(simulation.isRunning()){
            simulation.runFor(INTERVAL);
            printVmCpuUtilization();
        }

        final var cloudletFinishedList = broker0.getCloudletFinishedList();
        cloudletFinishedList.sort(Comparator.comparingLong(Cloudlet::getId));
        new CloudletsTableBuilder(cloudletFinishedList).build();
    }

    private void printVmCpuUtilization() {
        /*To avoid printing to much data, just prints if the simulation clock
         * has changed, it's multiple of the interval to increase clock
         * and there is some VM already running. */
        if(simulation.clock() == previousClock ||
            Math.round(simulation.clock()) % INTERVAL != 0 ||
            broker0.getVmExecList().isEmpty())
        {
            return;
        }

        previousClock = simulation.clock();
        System.out.printf("\t\tVM CPU utilization for Time %.0f%n", simulation.clock());
        for (final Vm vm : vmList) {
            System.out.printf(" Vm %5d |", vm.getId());
        }
        System.out.println();

        for (final Vm vm : vmList) {
            System.out.printf(" %7.0f%% |", vm.getCpuPercentUtilization()*100);
        }
        System.out.printf("%n%n");
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
        return new DatacenterSimple(simulation, hostList);
    }

    private Host createHost() {
        final var peList = new ArrayList<Pe>(HOST_PES);
        //List of Host's CPUs (Processing Elements, PEs)
        for (int i = 0; i < HOST_PES; i++) {
            //Uses a PeProvisionerSimple by default to provision PEs for VMs
            peList.add(new PeSimple(1000));
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
        final var list = new ArrayList<Vm>(VMS);
        for (int i = 0; i < VMS; i++) {
            //Uses a CloudletSchedulerTimeShared by default to schedule Cloudlets
            final Vm vm = new VmSimple(1000, VM_PES);
            vm.setRam(512).setBw(1000).setSize(10000);
            list.add(vm);
        }

        return list;
    }

    /**
     * Creates a list of Cloudlets with different submission delays.
     */
    private List<Cloudlet> createCloudlets() {
        final var list = new ArrayList<Cloudlet>(CLOUDLETS);

        for (int i = 0; i < CLOUDLETS; i++) {
            final var cloudlet = new CloudletSimple(CLOUDLET_LENGTH, CLOUDLET_PES);
            cloudlet.setUtilizationModelCpu(new UtilizationModelFull())
                    .setSizes(1024)
                    .setSubmissionDelay(i);
            list.add(cloudlet);
        }

        return list;
    }
}
