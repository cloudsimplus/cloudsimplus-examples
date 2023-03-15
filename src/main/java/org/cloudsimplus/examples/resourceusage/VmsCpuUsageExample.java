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
import org.cloudsimplus.provisioners.ResourceProvisionerSimple;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerTimeShared;
import org.cloudsimplus.schedulers.vm.VmSchedulerTimeShared;
import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;

import java.util.ArrayList;
import java.util.List;

/**
 * An example showing how to create a Datacenter with two hosts and run
 * two cloudlets on it. At the end, it shows the total CPU utilization of VMs
 * into a Datacenter.
 *
 * <p>
 * Cloudlets run in VMs with different MIPS requirements and will
 * take different times to complete the execution, depending on the requested VM
 * performance.
 * </p>
 *
 * @author Manoel Campos da Silva Filho
 * @since CloudSim Plus 1.0
 *
 * @see VmsRamAndBwUsageExample
 * @see org.cloudsimplus.examples.power.PowerExample
 */
public class VmsCpuUsageExample {
    private List<Cloudlet> cloudletList;
    private List<Vm> vmlist;
    private DatacenterBroker broker;
    private static final int VMS = 2;
    private static final int HOSTS = 3;

    public static void main(String[] args) {
        new VmsCpuUsageExample();
    }

    private VmsCpuUsageExample() {
        /*Enables just some level of log messages.
          Make sure to import org.cloudsimplus.util.Log;*/
        //Log.setLevel(ch.qos.logback.classic.Level.WARN);

        System.out.println("Starting " + getClass().getSimpleName());
        final var simulation = new CloudSimPlus();

        @SuppressWarnings("unused")
        final var datacenter0 = createDatacenter(simulation);

        broker = new DatacenterBrokerSimple(simulation);

        vmlist = new ArrayList<>(VMS);
        cloudletList = new ArrayList<>(VMS);

        final int mips = 1000;
        final int pesNumber = 2;
        for (int i = 1; i <= VMS; i++) {
            final var vm = createVm(pesNumber*2, mips * i, i - 1);
            vmlist.add(vm);
            final var cloudlet = createCloudlet(pesNumber);
            cloudletList.add(cloudlet);
            cloudlet.setVm(vm);
        }
        cloudletList.add(createCloudlet(pesNumber));

        broker.submitVmList(vmlist);
        broker.submitCloudletList(cloudletList);

        simulation.start();

        new CloudletsTableBuilder(broker.getCloudletFinishedList())
            .addColumn(new TextTableColumn("VM MIPS"), cl -> cl.getVm().getMips(), 7)
            .build();

        printCpuUtilizationForAllVms();

        System.out.println(getClass().getSimpleName() + " finished!");
    }

    private Cloudlet createCloudlet(final int pesNumber) {
        final long length = 10000;
        final long fileSize = 300;
        final long outputSize = 300;
        final var utilizationModelDynamic = new UtilizationModelDynamic(0.25);
        final var utilizationModelCpu = new UtilizationModelDynamic(0.5);

        final var cloudlet = new CloudletSimple(length, pesNumber);
        cloudlet.setFileSize(fileSize)
            .setOutputSize(outputSize)
            .setUtilizationModelCpu(utilizationModelCpu)
            .setUtilizationModelBw(utilizationModelDynamic)
            .setUtilizationModelRam(utilizationModelDynamic);
        return cloudlet;
    }

    /**
     * Creates a VM enabling the collection of CPU utilization history.
     * @param pesNumber
     * @param mips
     * @param id
     * @return
     */
    private Vm createVm(final int pesNumber, final int mips, final int id) {
        final long size = 10000; //image size (Megabyte)
        final int ram = 2048; //vm memory (Megabyte)
        final long bw = 1000;

        //create two VMs
        final var vm = new VmSimple(id, mips, pesNumber);
        vm.setRam(ram).setBw(bw)
            .setSize(size)
            .setCloudletScheduler(new CloudletSchedulerTimeShared());
        vm.enableUtilizationStats();
        return vm;
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
     * @param simulation
     * @return
     */
    private static Datacenter createDatacenter(CloudSimPlus simulation) {
        final var hostList = new ArrayList<Host>(HOSTS);
        final int pesNumber = 4;
        final int mips = 1000;
        for (int i = 1; i <= HOSTS; i++) {
            final var host = createHost(i, pesNumber, mips*i);
            hostList.add(host);
        }

        final var dc = new DatacenterSimple(simulation, hostList);
        dc.setSchedulingInterval(2);
        return dc;
    }

    private static Host createHost(final int id, final int pesNumber, final long mips) {
        final var peList = new ArrayList<Pe>();
        for (int i = 0; i < pesNumber; i++) {
            peList.add(new PeSimple(mips));
        }

        final int ram = 2048; //host memory (Megabyte)
        final long storage = 1000000; //host storage
        final int bw = 10000;

        final var host = new HostSimple(ram, bw, storage, peList)
                .setId(id)
                .setStateHistoryEnabled(true)
                .setRamProvisioner(new ResourceProvisionerSimple())
                .setBwProvisioner(new ResourceProvisionerSimple())
                .setVmScheduler(new VmSchedulerTimeShared());
        return host;
    }
}
