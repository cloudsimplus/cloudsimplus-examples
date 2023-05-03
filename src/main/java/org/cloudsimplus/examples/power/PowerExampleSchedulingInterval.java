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
package org.cloudsimplus.examples.power;

import ch.qos.logback.classic.Level;
import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.power.models.PowerModelHostSimple;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerTimeShared;
import org.cloudsimplus.schedulers.vm.VmSchedulerTimeShared;
import org.cloudsimplus.util.Log;
import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.HostResourceStats;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;

import java.util.ArrayList;
import java.util.List;

/**
 * An example to show how the accuracy of power consumption may change
 * according to different Datacenter scheduling intervals.
 * As lower is its value, higher is the power consumption accuracy
 * because power consumption data will be collected in smaller intervals.
 *
 * <p>CloudSim Plus has a very accurate and consistent power consumption computation.
 * This way, you can see in this example that results just change
 * when a scheduling interval is set with a value equals to the time the last
 * Cloudlet finishes.
 *
 * <p>You are strongly encouraged to firstly check the {@link PowerExample} to understand the details.</p>
 *
 * @author Manoel Campos da Silva Filho
 * @author Alexandre Henrique Teixeira Dias
 * @since CloudSim Plus 4.0.0
 */
public class PowerExampleSchedulingInterval {
    private static final int HOSTS = 2;
    private static final int HOST_PES = 8;

    private static final int VMS = 2;
    private static final int VM_PES = 4;

    private static final int CLOUDLETS = 4;
    private static final int CLOUDLET_PES = 2;
    private static final int CLOUDLET_LENGTH = 50000;

    /**
     * Defines the power a Host uses, even if it's idle (in Watts).
     */
    private static final double STATIC_POWER = 35;

    /**
     * The max power a Host uses (in Watts).
     */
    private static final int MAX_POWER = 50;

    private final int schedulingInterval;

    private CloudSimPlus simulation;
    private final DatacenterBroker broker0;
    private List<Vm> vmList;
    private List<Cloudlet> cloudletList;
    private Datacenter datacenter0;
    private List<Host> hostList;

    public static void main(String[] args) {
        Log.setLevel(Level.WARN);
        final int[] SCHEDULING_INTERVALS_SECS = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 100};
        for(final int interval : SCHEDULING_INTERVALS_SECS) {
            new PowerExampleSchedulingInterval(interval);
        }
    }

    /**
     * Instantiates and run the example with a specific configuration.
     * @param schedulingInterval the {@link Datacenter#getSchedulingInterval()} (in seconds)
     *
     */
    private PowerExampleSchedulingInterval(final int schedulingInterval) {
        simulation = new CloudSimPlus();
        hostList = new ArrayList<>(HOSTS);
        this.schedulingInterval = schedulingInterval;
        datacenter0 = createDatacenterSimple();
        broker0 = new DatacenterBrokerSimple(simulation);

        vmList = createVms();
        cloudletList = createCloudlets();
        broker0.submitVmList(vmList);
        broker0.submitCloudletList(cloudletList);

        simulation.start();

        System.out.println("------------------------------- SIMULATION FOR SCHEDULING INTERVAL = " + schedulingInterval+" -------------------------------");
        //new CloudletsTableBuilder(broker0.getCloudletFinishedList()).build();
        printHostsCpuUtilizationAndPowerConsumption();
        System.out.println();
    }

    private void printHostsCpuUtilizationAndPowerConsumption() {
        System.out.println();
        for (final Host host : hostList) {
            printHostCpuUtilizationAndPowerConsumption(host);
        }
    }

    private void printHostCpuUtilizationAndPowerConsumption(final Host host) {
        final HostResourceStats cpuStats = host.getCpuUtilizationStats();

        //The total Host's CPU utilization for the time specified by the map key
        final double utilizationPercentMean = cpuStats.getMean();
        final double watts = host.getPowerModel().getPower(utilizationPercentMean);
        System.out.printf(
            "\tHost %d CPU Usage mean: %6.1f%% | Power Consumption mean: %8.0f W%n",
            host.getId(), utilizationPercentMean * 100, watts);
    }

    private Datacenter createDatacenterSimple() {
        for(int i = 0; i < HOSTS; i++) {
            final var host = createPowerHost(i);
            hostList.add(host);
        }

        final var dc = new DatacenterSimple(simulation, hostList);
        dc.setSchedulingInterval(schedulingInterval);
        return dc;
    }

    private Host createPowerHost(final int id) {
        final var peList = new ArrayList<Pe>(HOST_PES);
        for (int i = 0; i < HOST_PES; i++) {
            peList.add(new PeSimple(1000));
        }

        final long ram = 2048; //in Megabytes
        final long bw = 10000; //in Megabits/s
        final long storage = 1000000; //in Megabytes

        final var host = new HostSimple(ram, bw, storage, peList);
        host
            .setId(id)
            .setVmScheduler(new VmSchedulerTimeShared())
            .setPowerModel(new PowerModelHostSimple(MAX_POWER, STATIC_POWER));
        host.enableUtilizationStats();
        return host;
    }

    private List<Vm> createVms() {
        final var newVmList = new ArrayList<Vm>(VMS);
        for (int i = 0; i < VMS; i++) {
            final var vm = new VmSimple(i, 1000, VM_PES);
            vm.setRam(512).setBw(1000).setSize(10000)
              .setCloudletScheduler(new CloudletSchedulerTimeShared())
              .enableUtilizationStats();
            newVmList.add(vm);
        }

        return newVmList;
    }

    private List<Cloudlet> createCloudlets() {
        final var list = new ArrayList<Cloudlet>(CLOUDLETS);
        final var utilization = new UtilizationModelDynamic(0.2);
        for (int i = 0; i < CLOUDLETS; i++) {
            //Sets half of the cloudlets with the defined length and the other half with the double of it
            final long length = i < CLOUDLETS/2 ? CLOUDLET_LENGTH : CLOUDLET_LENGTH*2;
            final var cloudlet =
                new CloudletSimple(i, length, CLOUDLET_PES)
                    .setFileSize(1024)
                    .setOutputSize(1024)
                    .setUtilizationModelCpu(new UtilizationModelFull())
                    .setUtilizationModelRam(utilization)
                    .setUtilizationModelBw(utilization);
            list.add(cloudlet);
        }

        return list;
    }
}
