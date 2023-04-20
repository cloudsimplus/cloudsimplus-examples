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

import org.cloudsimplus.allocationpolicies.VmAllocationPolicySimple;
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
import org.cloudsimplus.resources.File;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.resources.SanStorage;
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerTimeShared;
import org.cloudsimplus.schedulers.vm.VmSchedulerTimeShared;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;

import java.util.ArrayList;
import java.util.List;

 /**
  * An example of how to define a {@link SanStorage Storage Area Network (SAN)}
  * for a Datacenter, add files to this SAN and make Cloudlets
  * require files from that SAN.
  *
  * <p>Cloudlets' finish time is delayed due to transfer of required files
  * from the SAN to the VM.
  * The transfer time is computed based on the SAN's bandwidth and
  * size of the required files.</p>
  *
  * @author Manoel Campos da Silva Filho
  * @since CloudSim Plus 1.2.6
  */
 public class StorageAreaNetworkExample1 {
    private static final int HOSTS = 2;
    private static final int HOST_PES = 8;

    private static final int VMS = 4;
    private static final int VM_PES = 4;

    private static final int CLOUDLETS = VMS;
    private static final int CLOUDLET_PES = 2;
    private static final int CLOUDLET_LENGTH = 10000;
    private static final double NETWORK_LATENCY_SEC = 0.3;

    private static long SAN_BANDWIDTH_Mbps = 1;
    private static long SAN_CAPACITY_MB = 1024 * 1024 * 10;

    /**
     * A matrix of two rows each one containing an array of sizes
     * for different files to be added to a SAN storage.
     * Each row contains the sizes of the files for a SAN.
     *
     */
    private static int FILE_SIZES_MATRIX_MB[][] = {{1000, 10000, 5000, 25000}, {250000, 35000, 12000, 10000}};

    private static int SAN_COUNT = FILE_SIZES_MATRIX_MB.length;

    private final CloudSimPlus simulation;
    private final DatacenterBroker broker0;
    private List<Vm> vmList;
    private List<Cloudlet> cloudletList;
    private Datacenter datacenter0;

    public static void main(String[] args) {
        new StorageAreaNetworkExample1();
    }

    private StorageAreaNetworkExample1() {
        /*Enables just some level of log messages.
          Make sure to import org.cloudsimplus.util.Log;*/
        //Log.setLevel(ch.qos.logback.classic.Level.WARN);

        simulation = new CloudSimPlus();
        datacenter0 = createDatacenter();

        //Creates a broker that is a software acting on behalf of a cloud customer to manage his/her VMs and Cloudlets
        broker0 = new DatacenterBrokerSimple(simulation);

        vmList = createVms();
        cloudletList = createCloudlets();
        broker0.submitVmList(vmList);
        broker0.submitCloudletList(cloudletList);

        //Defines that the Cloudlet requires some files from the SAN
        cloudletList.get(0).addRequiredFile("file2.txt");
        cloudletList.get(0).addRequiredFile("file7.txt");

        /*
        Defines that the Cloudlet requires some files from the SAN.
        The file2.txt is being required by Cloudlet 0 and Cloudlet 1.
        */
        cloudletList.get(1).addRequiredFile("file1.txt");
        cloudletList.get(1).addRequiredFile("file2.txt");
        cloudletList.get(1).addRequiredFile("file5.txt");

        simulation.start();

        final var cloudletFinishedList = broker0.getCloudletFinishedList();
        new CloudletsTableBuilder(cloudletFinishedList).build();
    }

    /**
     * Creates a {@link SanStorage Storage Area Network (SAN)} array for a Datacenter.
     * @return the List of storage devices (the SAN array)
     * @see <a href="https://en.wikipedia.org/wiki/Disk_array">Disk Array</a>
     */
    private List<SanStorage> createSanArray() {
        final var sanStorageList = new ArrayList<SanStorage>(SAN_COUNT);
        int initialFileNumber = 0;
        for (int i = 0; i < SAN_COUNT; i++) {
            SanStorage san = new SanStorage("san"+i, SAN_CAPACITY_MB, SAN_BANDWIDTH_Mbps, NETWORK_LATENCY_SEC);
            addFilesToSanStorage(san, FILE_SIZES_MATRIX_MB[i], initialFileNumber);
            initialFileNumber = san.getNumStoredFile();
            sanStorageList.add(san);
        }

        return sanStorageList;
    }

    /**
     * Adds a list of files, whose sizes are defined in a given array,
     * to a {@link SanStorage}.
     * The name of the files will be defined incrementally.
     *
     * @param san the  {@link SanStorage} to add files to
     * @param fileSizesMB the array containing the sizes (in MB) of the files.
     * @param initialFileNumber the initial number to be used to compute the file numbered name
     */
    private void addFilesToSanStorage(SanStorage san, int[] fileSizesMB, int initialFileNumber) {
        File file;
        for (int i = 0; i < fileSizesMB.length; i++) {
            file = new File(getFileName(i + initialFileNumber), fileSizesMB[i]);
            System.out.printf("# Created file %s for SAN %s%n", file, san);
            san.addFile(file);
        }
    }

    /**
     * Generates a name to be used to a file.
     * @param fileNumber the integer index to be appended to a file
     * @return the generated filename
     */
    private String getFileName(int fileNumber) {
        return "file"+fileNumber+".txt";
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

        return new DatacenterSimple(simulation, hostList, new VmAllocationPolicySimple(), createSanArray());
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
    private List<Vm> createVms() {
        final var list = new ArrayList<Vm>(VMS);
        for (int i = 0; i < VMS; i++) {
            Vm vm =
                new VmSimple(i, 1000, VM_PES)
                    .setRam(512).setBw(1000).setSize(10000)
                    .setCloudletScheduler(new CloudletSchedulerTimeShared());

            list.add(vm);
        }

        return list;
    }

    /**
     * Creates a list of Cloudlets, binding it one to a specific VM,
     * since the {@link #CLOUDLETS number of Cloudlets}
     * is equal to {@link #VMS number of VMs}.
     */
    private List<Cloudlet> createCloudlets() {
        final var list = new ArrayList<Cloudlet>(CLOUDLETS);
        final var utilization = new UtilizationModelFull();
        for (int i = 0; i < CLOUDLETS; i++) {
            final var cloudlet =
                new CloudletSimple(i, CLOUDLET_LENGTH, CLOUDLET_PES)
                    .setFileSize(1024)
                    .setOutputSize(1024)
                    .setUtilizationModel(utilization);
            cloudlet.setVm(vmList.get(i));
            list.add(cloudlet);
        }

        return list;
    }
}
