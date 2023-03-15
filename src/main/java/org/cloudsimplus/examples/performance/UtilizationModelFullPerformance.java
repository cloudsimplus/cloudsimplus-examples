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
package org.cloudsimplus.examples.performance;

import org.cloudsimplus.allocationpolicies.VmAllocationPolicy;
import org.cloudsimplus.allocationpolicies.VmAllocationPolicyFirstFit;
import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.util.BytesConversion;
import org.cloudsimplus.util.Log;
import org.cloudsimplus.util.TimeUtil;
import org.cloudsimplus.utilizationmodels.UtilizationModel;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toCollection;

/**
 * An example showing how the {@link UtilizationModelFull} increases simulation performance
 * and reduces memory utilization.
 *
 * <p>There are a set of constants you can change to see how the simulation
 * is impacted. The most impacting ones are {@link #CLOUDLETS},
 * {@link #CLOUDLET_LENGTH} and {@link #SCHEDULING_INTERVAL}.
 * Just play with these values to see the results.</p>
 *
 * @author Manoel Campos da Silva Filho
 * @since CloudSim Plus 4.4.1
 */
public class UtilizationModelFullPerformance {
    private static final int HOSTS = 20_000;
    private static final int HOST_PES = 16;

    private static final int VMS = HOSTS*4;
    private static final int VM_PES = 4;

    private static final int CLOUDLETS = VMS*2;
    private static final int CLOUDLET_PES = 2;
    private static final int CLOUDLET_LENGTH = 10_000;
    private static final int SCHEDULING_INTERVAL = 0;

    private static final VmAllocationPolicy VM_ALLOCATION_POLICY = new VmAllocationPolicyFirstFit();

    private final CloudSimPlus simulation;
    private final DatacenterBroker broker0;
    private List<Vm> vmList;
    private List<Cloudlet> cloudletList;
    private Datacenter datacenter0;
    private final UtilizationModel um = new UtilizationModelFull();

    public static void main(String[] args) {
        new UtilizationModelFullPerformance();
    }

    private UtilizationModelFullPerformance() {
        System.out.println("Starting " + this.getClass().getSimpleName() + " at " + LocalTime.now());
        final long startMillis = System.currentTimeMillis();
        Log.setLevel(ch.qos.logback.classic.Level.WARN);

        simulation = new CloudSimPlus();
        datacenter0 = createDatacenter();

        broker0 = new DatacenterBrokerSimple(simulation);

        vmList = createVms();
        cloudletList = createCloudlets();
        broker0.submitVmList(vmList);
        broker0.submitCloudletList(cloudletList);

        simulation.start();
        final double maxHeapUtilizationGB = getMaxHeapUtilizationGB();

        final double execMinutes = TimeUtil.millisecsToMinutes(System.currentTimeMillis() - startMillis);
        System.out.printf("UtilizationModel: %s (single instance)%n%n", um.getClass().getSimpleName());

        System.out.println("| Execution time | Simulation time | Max Heap Used | VmAllocationPolicy | Hosts      | VMs        | Cloudlets  | Cloudlet Len | DC Scheduling Interval |");
        System.out.println("| ---------------|-----------------|---------------|--------------------|------------|------------|------------|--------------|------------------------|");
        System.out.printf(
            "| %10.2f min | %11.2f min | %10.2f GB | %18s | %10d | %10d | %10d | %12d | %22d |",
            execMinutes, TimeUtil.secondsToMinutes(simulation.clock()), maxHeapUtilizationGB,
            VM_ALLOCATION_POLICY.getClass().getSimpleName().substring(18),
            HOSTS, VMS, CLOUDLETS, CLOUDLET_LENGTH, SCHEDULING_INTERVAL);
    }

    /**
     * Gets the maximum number of GB ever used by the application's heap.
     * @return the max heap utilization in GB
     * @see <a href="https://www.oracle.com/webfolder/technetwork/tutorials/obe/java/gc01/index.html">Java Garbage Collection Basics (for information about heap space)</a>
     */
    private double getMaxHeapUtilizationGB() {
        final double memoryBytes =
            ManagementFactory.getMemoryPoolMXBeans()
                             .stream()
                             .filter(bean -> bean.getType() == MemoryType.HEAP)
                             .filter(bean -> bean.getName().contains("Eden Space") || bean.getName().contains("Survivor Space"))
                             .map(MemoryPoolMXBean::getPeakUsage)
                             .mapToDouble(MemoryUsage::getUsed)
                             .sum();

        return BytesConversion.bytesToGigaBytes(memoryBytes);
    }

    private Datacenter createDatacenter() {
        final var hostList =
                IntStream.range(0, HOSTS)
                         .mapToObj(i -> createHost())
                         .collect(toCollection(() -> new ArrayList<>(HOSTS)));

        return new DatacenterSimple(simulation, hostList, VM_ALLOCATION_POLICY).setSchedulingInterval(SCHEDULING_INTERVAL);
    }

    private Host createHost() {
        final List<Pe> peList =
            IntStream.range(0, HOST_PES)
                     .mapToObj(i -> new PeSimple(1000))
                     .collect(toCollection(() -> new ArrayList<>(HOST_PES)));

        final long ramMB = 20480;
        final long bwMbps = 10000;
        final long storageMB = 1000000;
        return new HostSimple(ramMB, bwMbps, storageMB, peList);
    }

    private List<Vm> createVms() {
        final var newVmList = new ArrayList<Vm>(VMS);
        for (int i = 0; i < VMS; i++) {
            final Vm vm = new VmSimple(1000, VM_PES);
            newVmList.add(vm);
        }

        return newVmList;
    }

    private List<Cloudlet> createCloudlets() {
        final var newCloudletList = new ArrayList<Cloudlet>(CLOUDLETS);
        for (int i = 0; i < CLOUDLETS; i++) {
            final var cloudlet = new CloudletSimple(CLOUDLET_LENGTH, CLOUDLET_PES);
            cloudlet.setUtilizationModelCpu(um).setSizes(1024);
            newCloudletList.add(cloudlet);
        }

        return newCloudletList;
    }
}
