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
/*
 * CloudSim Plus: A modern, highly-extensible and easier-to-use Framework for
 * Modeling and Simulation of Cloud Computing Infrastructures and Services.
 * http://cloudsimplus.org
 *
 *     Copyright (C) 2015-2016  Universidade da Beira Interior (UBI, Portugal) and
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
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerTimeShared;
import org.cloudsimplus.schedulers.vm.VmSchedulerTimeShared;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * An example showing how to create 1 Datacenter with 5 hosts,
 * 1 VM by host and 1 cloudlet by VM and perform VM allocation by
 * using Java 8+ Functional Programming to change, at runtime, the
 * policy used by a {@link VmAllocationPolicy}.
 *
 * <p>VMs are allocated based on a <b>Best Fit allocation policy</b>, which
 * selects the suitable Host with the lowest number of available PEs
 * that is enough to run the VM.
 * This policy is dynamically defined by changing the {@link Function}
 * of the {@link VmAllocationPolicy}, without requiring to create
 * a new class. The {@link VmAllocationPolicySimple} used in this example ignores power usage of
 * Hosts. This way, it isn't required to set a PowerModel for Hosts.
 * </p>
 *
 * It is used some constants to create simulation objects such as
 * {@link  DatacenterSimple}, {@link  Host} and {@link  Vm}.
 * The values of these constants were careful and accordingly chosen to allow
 * migration of VMs due to either under and overloaded hosts and
 * to allow one developer to know exactly how the simulation will run
 * and what will be the final results.
 * Several values impact the simulation results, such as
 * hosts CPU capacity and number of PEs, VMs and cloudlets requirements
 * and even VM bandwidth (that defines the VM migration time).
 *
 * <p>This way, if you want to change these values, you must
 * define new appropriated ones to allow the simulation
 * to run correctly.</p>
 *
 * @author Manoel Campos da Silva Filho
 */
public final class DynamicVmAllocationPolicyBestFitExample {
    private static final int    SCHEDULE_INTERVAL = 5;

    private static final int HOSTS = 5;
    private static final int VMS = 3;

    private static final int    HOST_MIPS = 1000; //for each PE

    private static final int    HOST_INITIAL_PES = 4;
    private static final long   HOST_RAM = 500000; //host memory (MB)
    private static final long   HOST_STORAGE = 1000000; //host storage

    private static final long   HOST_BW = 16000L; //Mb/s

    private static final int    VM_MIPS = 1000; //for each PE
    private static final long   VM_SIZE = 1000; //image size (MB)
    private static final int    VM_RAM = 10000; //VM memory (MB)
    private static final double VM_BW = HOST_BW/(double)VMS;
    private static final int    VM_PES = 2;

    private static final long   CLOUDLET_LENGHT = 20000;
    private static final long   CLOUDLET_FILESIZE = 300;
    private static final long   CLOUDLET_OUTPUTSIZE = 300;

    /**
     * List of all created VMs.
     */
    private final List<Vm> vmList = new ArrayList<>();

    private CloudSimPlus simulation;
    private List<Host> hostList;

    public static void main(String[] args) {
        new DynamicVmAllocationPolicyBestFitExample();
    }

    private DynamicVmAllocationPolicyBestFitExample(){
        /*Enables just some level of log messages.
          Make sure to import org.cloudsimplus.util.Log;*/
        //Log.setLevel(ch.qos.logback.classic.Level.WARN);

        System.out.println("Starting " + getClass().getSimpleName());
        simulation = new CloudSimPlus();

        @SuppressWarnings("unused")
        final var datacenter0 = createDatacenter();
        final var broker = new DatacenterBrokerSimple(simulation);
        createAndSubmitVms(broker);
        createAndSubmitCloudlets(broker);

        simulation.start();

        final var cloudletFinishedList = broker.getCloudletFinishedList();
        cloudletFinishedList.sort(
            Comparator.comparingLong((Cloudlet c) -> c.getVm().getHost().getId())
                      .thenComparingLong(c -> c.getVm().getId()));
        new CloudletsTableBuilder(cloudletFinishedList).build();
        System.out.println(getClass().getSimpleName() + " finished!");
    }

    /**
     * A method that defines a Best Fit policy to select a suitable Host with the least
     * available PEs to place a VM.
     * Using Java 8+ Functional Programming, this method is given as parameter
     * to the constructor of a {@link VmAllocationPolicySimple}
     *
     * @param allocationPolicy the {@link VmAllocationPolicy} that is trying to allocate a Host for the requesting VM
     * @param vm the VM to find a host to
     * @return an {@link Optional<Host>} which may contain a Host or an empty Optional if no suitable Host was found
     * @see #createDatacenter()
     */
    private Optional<Host> bestFitHostSelectionPolicy(VmAllocationPolicy allocationPolicy, Vm vm) {
        return allocationPolicy
            .getHostList()
            .stream()
            .filter(host -> host.isSuitableForVm(vm))
            .min(Comparator.comparingInt(Host::getFreePesNumber));
    }

    public void createAndSubmitCloudlets(DatacenterBroker broker) {
        final var newCloudletList = new ArrayList<Cloudlet>(VMS);
        for(final var vm: vmList){
            newCloudletList.add(createCloudlet(vm, broker));
        }

        broker.submitCloudletList(newCloudletList);
    }

    /**
     * Creates a Cloudlet.
     *
     * @param vm the VM that will run the Cloudlets
     * @param broker the broker that the created Cloudlets belong to
     * @return the created Cloudlets
     */
    public Cloudlet createCloudlet(Vm vm, DatacenterBroker broker) {
        final var cloudlet =
            new CloudletSimple(CLOUDLET_LENGHT, (int)vm.getPesNumber())
                .setFileSize(CLOUDLET_FILESIZE)
                .setOutputSize(CLOUDLET_OUTPUTSIZE)
                .setUtilizationModel(new UtilizationModelFull());
        broker.bindCloudletToVm(cloudlet, vm);
        return cloudlet;
    }

    public void createAndSubmitVms(DatacenterBroker broker) {
        final var newVmList = new ArrayList<Vm>(VMS);
        for(int i = 0; i < VMS; i++){
            final var vm = createVm(VM_PES);
            newVmList.add(vm);
        }

        vmList.addAll(newVmList);
        broker.submitVmList(newVmList);
    }

    public Vm createVm(int pes) {
        final var vm = new VmSimple(VM_MIPS, pes);
        vm
          .setRam(VM_RAM).setBw((long)VM_BW).setSize(VM_SIZE)
          .setCloudletScheduler(new CloudletSchedulerTimeShared());
        return vm;
    }

    /**
     * Creates a Datacenter with number of Hosts defined by {@link #HOSTS},
     * but only some of these Hosts will be active (powered on) initially.
     *
     * @return
     */
    private Datacenter createDatacenter() {
        this.hostList = new ArrayList<>();
        for(int i = 0; i < HOSTS; i++){
            final int pes = HOST_INITIAL_PES + i;
            final var host = createHost(pes, HOST_MIPS);
            hostList.add(host);
        }
        System.out.println();

        /*Creates a VmAllocationPolicy and changes, at runtime, the policy used to select a Host for a VM.*/
        final var allocationPolicy = new VmAllocationPolicySimple(this::bestFitHostSelectionPolicy);

        final var dc = new DatacenterSimple(simulation, hostList, allocationPolicy);

        hostList.forEach(host -> System.out.printf("#Created %s with %d PEs%n", host, host.getPesNumber()));
        System.out.println();

        dc.setSchedulingInterval(SCHEDULE_INTERVAL);
        return dc;
    }

    public Host createHost(int pesNumber, long mipsByPe) {
        final var peList = createPeList(pesNumber, mipsByPe);
        final var host = new HostSimple(HOST_RAM, HOST_BW, HOST_STORAGE, peList);
        host.setVmScheduler(new VmSchedulerTimeShared());
        return host;
    }

    public List<Pe> createPeList(int numberOfPEs, long mips) {
        final var peList = new ArrayList<Pe>(numberOfPEs);
        for(int i = 0; i < numberOfPEs; i++) {
            peList.add(new PeSimple(mips));
        }
        return peList;
    }
}
