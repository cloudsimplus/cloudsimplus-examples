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
import org.cloudsimplus.distributions.ContinuousDistribution;
import org.cloudsimplus.distributions.UniformDistr;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.provisioners.ResourceProvisionerSimple;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerTimeShared;
import org.cloudsimplus.schedulers.vm.VmSchedulerTimeShared;
import org.cloudsimplus.util.Log;
import org.cloudsimplus.util.TimeUtil;
import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * An example showing how to implement a custom
 * {@link VmAllocationPolicy} that randomly
 * selects suitable Hosts to place submitted VMs.
 * Check the {@link #createDatacenter()} method for details.
 *
 * <p>Since CloudSim Plus provides functional
 * VmAllocationPolicy implementations, you don't
 * even need to create a new class to implement your own policy,
 * unless you really want to (to make reuse easy).</p>
 *
 * @author Manoel Campos da Silva Filho
 * @since CloudSim Plus 4.0.5
 */
public class RandomVmAllocationPolicyExample {
    private static final int HOSTS = 10;
    private static final int HOST_PES = 8;

    private static final int VMS = 4;
    private static final int VM_PES = 4;

    private static final int CLOUDLETS = 4;
    private static final int CLOUDLET_PES = 2;
    private static final int CLOUDLET_LENGTH = 10000;

    private final CloudSimPlus simulation;
    private final DatacenterBroker broker0;
    private List<Vm> vmList;
    private List<Cloudlet> cloudletList;
    private Datacenter datacenter0;

     /** A random number generator that returns values between [-1 .. 1],
     * according to the {@link Comparator#compare(Object, Object)} method used
     * to randomly sort Hosts to be allocated to VMs.
     */
    private final ContinuousDistribution random;

    public static void main(String[] args) {
        new RandomVmAllocationPolicyExample();
    }

    private RandomVmAllocationPolicyExample() {
        final double startSecs = TimeUtil.currentTimeSecs();
        //Enables just some level of log messages.
        Log.setLevel(ch.qos.logback.classic.Level.WARN);

        simulation = new CloudSimPlus();

        random = new UniformDistr();
        datacenter0 = createDatacenter();

        //Creates a broker that is a software acting on behalf of a cloud customer to manage his/her VMs and Cloudlets
        broker0 = new DatacenterBrokerSimple(simulation);

        vmList = createVms();
        cloudletList = createCloudlets();
        broker0.submitVmList(vmList);
        broker0.submitCloudletList(cloudletList);

        simulation.start();

        final var cloudletFinishedList = broker0.getCloudletFinishedList();
        cloudletFinishedList.sort(Comparator.comparingLong(cloudlet -> cloudlet.getVm().getId()));
        new CloudletsTableBuilder(cloudletFinishedList).build();
        System.out.println("Execution time: " + TimeUtil.secondsToStr(TimeUtil.elapsedSeconds(startSecs)));
    }

    /**
     * Creates a Datacenter and its Hosts, defining
     * a {@link VmAllocationPolicy} that randomly selects suitable Hosts
     * to place submitted VMs.
     */
    private Datacenter createDatacenter() {
        final var hostList = new ArrayList<Host>(HOSTS);
        for(int i = 0; i < HOSTS; i++) {
            final var host = createHost();
            hostList.add(host);
        }

        final var vmAllocationPolicy = new VmAllocationPolicySimple();

        //Replaces the default method that allocates Hosts to VMs by our own implementation
        vmAllocationPolicy.setFindHostForVmFunction(this::findRandomSuitableHostForVm);

        return new DatacenterSimple(simulation, hostList, vmAllocationPolicy);
    }

    /**
     * Define a specific policy to randomly select a suitable Host to place a given VM.
     * It's a very efficient policy since it doesn't perform sorting
     * in order to find a suitable Host.
     *
     * @param vmAllocationPolicy the {@link VmAllocationPolicy} containing Host allocation information
     * @param vm the {@link Vm} to find a host to be placed
     * @return an {@link Optional} that may contain a Host in which to place a Vm, or an {@link Optional#empty()}
     *         {@link Optional} if not suitable Host was found.
     */
    private Optional<Host> findRandomSuitableHostForVm(final VmAllocationPolicy vmAllocationPolicy, final Vm vm) {
        final var hostList = vmAllocationPolicy.getHostList();
        /* Despite the loop is bound to the number of Hosts inside the List,
        *  the index "i" is not used to get a Host at that position,
        *  but just to define that the maximum number of tries to find a
        *  suitable Host will be the number of available Hosts.*/
        for (int i = 0; i < hostList.size(); i++){
            final int randomIndex = (int)(random.sample() * hostList.size());
            final Host host = hostList.get(randomIndex);
            if(host.isSuitableForVm(vm)){
                return Optional.of(host);
            }
        }

        return Optional.empty();
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
        final var host = new HostSimple(ram, bw, storage, peList);
        host
            .setRamProvisioner(new ResourceProvisionerSimple())
            .setBwProvisioner(new ResourceProvisionerSimple())
            .setVmScheduler(new VmSchedulerTimeShared());
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
     * Creates a list of Cloudlets.
     */
    private List<Cloudlet> createCloudlets() {
        final var list = new ArrayList<Cloudlet>(CLOUDLETS);
        final var utilization = new UtilizationModelDynamic(0.2);
        for (int i = 0; i < CLOUDLETS; i++) {
            final var cloudlet =
                new CloudletSimple(i, CLOUDLET_LENGTH, CLOUDLET_PES)
                    .setFileSize(1024)
                    .setOutputSize(1024)
                    .setUtilizationModelBw(utilization)
                    .setUtilizationModelRam(utilization)
                    .setUtilizationModelCpu(new UtilizationModelFull());
            list.add(cloudlet);
        }

        return list;
    }
}
