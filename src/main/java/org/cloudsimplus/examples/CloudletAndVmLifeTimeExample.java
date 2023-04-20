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

import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;
import org.cloudsimplus.builders.tables.MarkdownTableColumn;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.core.Lifetimed;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.schedulers.vm.VmSchedulerTimeShared;
import org.cloudsimplus.util.TimeUtil;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.utilizationmodels.UtilizationModelStochastic;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

/**
 * An example showing how to use the new {@link Lifetimed#setLifeTime(double)} to
 * define the maximum time  {@link Cloudlet} or {@link Vm} entity is allowed to execute.
 * After this time is reached, the entity is finished as soon as possible.
 *
 * <p>
 *     If the VM has a lifeTime set, it's Cloudlets follow the same lifetime.
 *     If both Cloudlet and Vm have a lifetime, the shorter defines the maximum
 *     amount of time the Cloudlet will execute.
 * </p>
 *
 * <p>The example creates all Cloudlets and some VMs with a given lifetime.
 * Since VM lifetime is shorter than their Cloudlets' lifetime,
 * such Cloudlets finish sooner than their own lifetime.</p>
 *
 * @author Manoel Campos da Silva Filho
 * @since CloudSim Plus 8.2.0
 * @see CloudletLifeTimeExample
 */
public class CloudletAndVmLifeTimeExample {
    private static final int HOSTS = 3;
    private static final int HOST_PES = 10;

    private static final int VMS = 4;
    private static final int VM_PES = 4;
    private static final int VM_MIPS = 1000;

    private static final int CLOUDLETS = 4;
    private static final int CLOUDLET_PES = 2;
    private static final int CLOUDLET_LENGTH = 10_000;

    /**
     * If the scheduling interval is not multiple of the VM/Cloudlet lifetime,
     * Cloudlets may execute more than you desire.
     * @see Datacenter#getSchedulingInterval()
     */
    private static final int SCHEDULING_INTERVAL = 3;

    /**
     * Maximum time (in seconds) Cloudlets are allowed to execute.
     * Set -1 to disable lifeTime and execute the Cloudlet entirely.
     */
    private static final double CLOUDLET_LIFE_TIME = 5;

    /**
     * Maximum time (in seconds) some VMs are allowed to execute.
     * @see Lifetimed#setLifeTime(double)
     */
    private static final double VM_LIFE_TIME = 3;

    private final CloudSimPlus simulation;
    private final DatacenterBroker broker0;
    private List<Vm> vmList;
    private List<Cloudlet> cloudletList;
    private Datacenter datacenter0;

    public static void main(String[] args) {
        new CloudletAndVmLifeTimeExample();
    }

    private CloudletAndVmLifeTimeExample() {
        /*Enables just some level of log messages.
          Make sure to import org.cloudsimplus.util.Log;*/
        //Log.setLevel(ch.qos.logback.classic.Level.WARN);

        final double startSecs = TimeUtil.currentTimeSecs();
        System.out.printf("Simulation started at %s%n%n", LocalTime.now());
        simulation = new CloudSimPlus();
        datacenter0 = createDatacenter();

        //Creates a broker that is a software acting on behalf of a cloud customer to manage his/her VMs and Cloudlets
        broker0 = new DatacenterBrokerSimple(simulation);

        vmList = createVms();
        setVmsLifeTime();

        cloudletList = createCloudlets();
        broker0.submitVmList(vmList);
        broker0.submitCloudletList(cloudletList);

        simulation.start();

        final var cloudletFinishedList = broker0.getCloudletFinishedList();
        cloudletFinishedList.sort(Comparator.comparingLong(c -> c.getVm().getId()));
        new CloudletsTableBuilder(cloudletFinishedList)
                .addColumn(new MarkdownTableColumn("Cloudlet", "LifeTime"), this::getLifeTimeStr)
                .addColumn(new MarkdownTableColumn("Vm      ", "LifeTime"), c -> getLifeTimeStr(c.getVm()))
                .build();
        System.out.printf("Simulation finished at %s. Execution time: %.2f seconds%n", LocalTime.now(), TimeUtil.elapsedSeconds(startSecs));
    }

    /**
     * Gets the lifetime as a String.
     * If the lifetime is {@link Double#MAX_VALUE} (the default value),
     * returns an empty string to indicate the attribute was not set.
     *
     * @param entity a Cloudlet of VM entity
     * @return a String lifetime
     */
    private String getLifeTimeStr(final Lifetimed entity) {
        return entity.getLifeTime() == Double.MAX_VALUE ? "" : "%.2f".formatted(entity.getLifeTime());
    }

    /**
     * Sets a lifetime for half of the VMs.
     */
    private void setVmsLifeTime() {
        vmList.stream()
              .filter(vm -> vm.getId() < VMS/2)
              .forEach(vm -> vm.setLifeTime(VM_LIFE_TIME));
    }

    private Datacenter createDatacenter() {
        final var hostList = new ArrayList<Host>(HOSTS);
        for(int i = 0; i < HOSTS; i++) {
            final var host = createHost();
            hostList.add(host);
        }

        final var datacenter = new DatacenterSimple(simulation, hostList);
        datacenter.setSchedulingInterval(SCHEDULING_INTERVAL);
        return datacenter;
    }

    private Host createHost() {
        final var peList = new ArrayList<Pe>(HOST_PES);
        //List of Host's CPUs (Processing Elements, PEs)
        IntStream.range(0, HOST_PES).forEach(i -> peList.add(new PeSimple(1000)));

        final long ram = 2048; //in Megabytes
        final long bw = 10000; //in Megabits/s
        final long storage = 1000000; //in Megabytes
        final var host = new HostSimple(ram, bw, storage, peList);
        host.setVmScheduler(new VmSchedulerTimeShared());
        return host;
    }

    private List<Vm> createVms() {
        final var vmList = new ArrayList<Vm>(VMS);
        for (int i = 0; i < VMS; i++) {
            final var vm = new VmSimple(i, VM_MIPS, VM_PES);
            vmList.add(vm);
        }

        return vmList;
    }

    private List<Cloudlet> createCloudlets() {
        final var cloudletList = new ArrayList<Cloudlet>(CLOUDLETS);
        for (int i = 0; i < CLOUDLETS; i++) {
            final var cloudlet = new CloudletSimple(i, CLOUDLET_LENGTH, CLOUDLET_PES);
            cloudlet
                    .setFileSize(1024)
                    .setOutputSize(1024)
                    .setUtilizationModelCpu(new UtilizationModelFull())
                    .setUtilizationModelBw(new UtilizationModelStochastic())
                    .setUtilizationModelRam(new UtilizationModelStochastic())
                    .setLifeTime(CLOUDLET_LIFE_TIME);
            cloudletList.add(cloudlet);
        }

        return cloudletList;
    }
}
