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
package org.cloudsimplus.examples.resourceusage;

import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;
import org.cloudsimplus.builders.tables.TextTableColumn;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.Datacenter;
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
 * An example showing how to create a Datacenter with two hosts,
 * with one Vm in each one, and run 1 cloudlet in each Vm.
 * At the end, it shows the total CPU utilization of hosts
 * into a Datacenter.
 *
 * <p>Cloudlets run in VMs with different MIPS requirements and will
 * take different times to complete the execution, depending on the requested VM
 * performance.</p>
 *
 * @author Manoel Campos da Silva Filho
 * @since CloudSim Plus 1.0
 */
public class HostsCpuUsageExample {
    private List<Cloudlet> cloudletList;
    private List<Vm> vmlist;
    private List<Host> hostList;
    private DatacenterBroker broker;
    private CloudSimPlus simulation;

    private static final int NUMBER_OF_VMS = 2;
    private static final int NUMBER_OF_HOSTS = 2;

    public static void main(String[] args) {
        new HostsCpuUsageExample();
    }

    private HostsCpuUsageExample(){
        /*Enables just some level of log messages.
          Make sure to import org.cloudsimplus.util.Log;*/
        //Log.setLevel(ch.qos.logback.classic.Level.WARN);

        System.out.println("Starting " + getClass().getSimpleName());
        simulation = new CloudSimPlus();

        @SuppressWarnings("unused")
        final var datacenter0 = createDatacenter();

        broker = new DatacenterBrokerSimple(simulation);

        vmlist = new ArrayList<>(NUMBER_OF_VMS);
        cloudletList = new ArrayList<>(NUMBER_OF_VMS);

        final int pesNumber = 1; //number of cpus
        final int mips = 1000;
        for (int i = 1; i <= NUMBER_OF_VMS; i++) {
            final var vm = createVm(pesNumber, mips);
            vmlist.add(vm);

            final var cloudlet = createCloudlet(pesNumber);
            cloudletList.add(cloudlet);
        }

        //Link each Cloudlet to a spacific VM
        for (int i = 0; i < NUMBER_OF_VMS; i++) {
            broker.bindCloudletToVm(cloudletList.get(i), vmlist.get(i));
        }

        broker.submitVmList(vmlist);
        broker.submitCloudletList(cloudletList);

        simulation.start();

        final var cloudletFinishedList = broker.getCloudletFinishedList();
        new CloudletsTableBuilder(cloudletFinishedList)
            .addColumn(new TextTableColumn("Host  ", "MIPS  "), cloudlet -> cloudlet.getVm().getHost().getMips(), 5)
            .addColumn(new TextTableColumn("VM MIPS"), cloudlet -> cloudlet.getVm().getMips(), 7)
            .build();

        System.out.printf("%nHosts CPU utilization statistics%n");
        printCpuUtilizationForAllHosts();
        printCpuUtilizationForAllVms();
        System.out.println(getClass().getSimpleName() + " finished!");
    }

    private Cloudlet createCloudlet(final int pesNumber) {
        final long length = 10000;
        final long fileSize = 300;
        final long outputSize = 300;
        final var utilizationModel = new UtilizationModelFull();

        return new CloudletSimple(length, pesNumber)
            .setFileSize(fileSize)
            .setOutputSize(outputSize)
            .setUtilizationModel(utilizationModel);
    }

    /**
     * Creates a VM enabling the collection of CPU utilization history.
     * This way, hosts can get the CPU utilization based on VM utilization.
     * @param pesNumber
     * @param mips
     * @return
     */
    private Vm createVm(final int pesNumber, final long mips) {
        final long size = 10000; //image size (Megabyte)
        final int ram = 2048; //vm memory (Megabyte)
        final long bw = 1000;
        final var vm = new VmSimple(mips, pesNumber)
            .setRam(ram).setBw(bw).setSize(size)
            .setCloudletScheduler(new CloudletSchedulerTimeShared());
        vm.enableUtilizationStats();
        return vm;
    }

    /**
     * Shows CPU utilization mean of all hosts into a given Datacenter.
     */
    private void printCpuUtilizationForAllHosts() {
        for (final var host : hostList) {
            final double mipsByPe = host.getTotalMipsCapacity() / (double)host.getPesNumber();
            final double cpuUsageMean = host.getCpuUtilizationStats().getMean()*100;
            System.out.printf(
                "\tHost %d: PEs number: %2d MIPS by PE: %.0f CPU Utilization mean: %6.2f%%%n",
                host.getId(), host.getPesNumber(), mipsByPe, cpuUsageMean);
        }
    }

    private void printCpuUtilizationForAllVms() {
        System.out.printf("%nVMs CPU utilization mean%n");
        for (final var vm : vmlist) {
            final double vmCpuUsageMean = vm.getCpuUtilizationStats().getMean()*100;
            System.out.printf("\tVM %d CPU Utilization mean: %6.2f%%%n", vm.getId(), vmCpuUsageMean);
        }
    }

    /**
     * Creates a datacenter setting the scheduling interval
     * that, between other things, defines the times to collect
     * VM CPU utilization.
     *
     * @return
     */
    private Datacenter createDatacenter() {
        hostList = new ArrayList<>(NUMBER_OF_HOSTS);
        final int pesNumber = 1;
        final int mips = 2000;
        for (int i = 1; i <= 2; i++) {
            final var host = createHost(i, pesNumber, mips*i);
            hostList.add(host);
        }

        final var dc = new DatacenterSimple(simulation, hostList);
        dc.setSchedulingInterval(2);
        return dc;
    }

    private Host createHost(final int id, final int pesNumber, final long mips) {
        final var peList = new ArrayList<Pe>();
        for (int i = 0; i < pesNumber; i++) {
            peList.add(new PeSimple(mips));
        }

        final long ram = 2048; //host memory (Megabyte)
        final long storage = 1000000; //host storage (Megabyte)
        final long bw = 10000; //Megabits/s

        final var host = new HostSimple(ram, bw, storage, peList);
        host.setId(id)
            .setVmScheduler(new VmSchedulerTimeShared());
        host.enableUtilizationStats();
        return host;
    }
}
