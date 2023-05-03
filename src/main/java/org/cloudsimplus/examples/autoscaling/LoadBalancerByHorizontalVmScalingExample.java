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
package org.cloudsimplus.examples.autoscaling;

import org.cloudsimplus.autoscaling.HorizontalVmScaling;
import org.cloudsimplus.autoscaling.HorizontalVmScalingSimple;
import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.core.Simulation;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.distributions.ContinuousDistribution;
import org.cloudsimplus.distributions.UniformDistr;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.listeners.EventInfo;
import org.cloudsimplus.listeners.EventListener;
import org.cloudsimplus.provisioners.ResourceProvisionerSimple;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerTimeShared;
import org.cloudsimplus.schedulers.vm.VmSchedulerTimeShared;
import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import static java.util.Comparator.comparingDouble;

/**
 * An example that balances load by dynamically creating VMs,
 * according to the arrival of Cloudlets.
 * Cloudlets are {@link #createNewCloudlets(EventInfo) dynamically created and submitted to the broker
 * at specific time intervals}.
 *
 * <p>A {@link HorizontalVmScalingSimple}
 * is set to each {@link #createListOfScalableVms(int) initially created VM},
 * that will check at {@link #SCHEDULING_INTERVAL specific time intervals}
 * if the VM {@link #isVmOverloaded(Vm) is overloaded or not} to then
 * request the creation of a new VM to attend arriving Cloudlets.</p>
 *
 * <p>The example uses CloudSim Plus {@link EventListener} feature
 * to enable monitoring the simulation and dynamically creating objects such as Cloudlets and VMs.
 * It relies on
 * <a href="http://www.oracle.com/webfolder/technetwork/tutorials/obe/java/Lambda-QuickStart/index.html">Java 8 Lambda Expressions</a>
 * to set a Listener for the {@link Simulation#addOnClockTickListener(EventListener) onClockTick event}.
 * That Listener gets notified every time the simulation clock advances and then creates and submits new Cloudlets.
 * </p>
 *
 * <p>The {@link DatacenterBroker} is accountable to perform horizontal down scaling.
 * The down scaling is enabled by setting a {@link Function} using the {@link DatacenterBroker#setVmDestructionDelayFunction(Function)}.
 * This Function defines the time the broker has to wait to destroy a VM after it becomes idle.
 * If no Function is set, the broker just destroys VMs after all running Cloudlets are finished
 * and there is no Cloudlet waiting to be created.
 * </p>
 *
 * @author Manoel Campos da Silva Filho
 * @since CloudSim Plus 1.0
 */
public class LoadBalancerByHorizontalVmScalingExample {
    /**
     * The interval in which the Datacenter will schedule events.
     * As lower is this interval, sooner the processing of VMs and Cloudlets
     * is updated and you will get more notifications about the simulation execution.
     * However, that also affect the simulation performance.
     *
     * <p>A large schedule interval, such as 15, will make that just
     * at every 15 seconds the processing of VMs is updated. If a VM is overloaded, just
     * after this time the creation of a new one will be requested
     * by the VM's {@link HorizontalVmScaling Horizontal Scaling} mechanism.</p>
     *
     * <p>If this interval is defined using a small value, you may get
     * more dynamically created VMs than expected.
     * Accordingly, this value has to be trade-off.
     * For more details, see {@link Datacenter#getSchedulingInterval()}.</p>
    */
    private static final int SCHEDULING_INTERVAL = 5;

    /**
     * The interval to request the creation of new Cloudlets.
     */
    private static final int CLOUDLETS_CREATION_INTERVAL = SCHEDULING_INTERVAL * 2;

    private static final int HOSTS = 50;
    private static final int HOST_PES = 32;
    private static final int VMS = 4;
    private static final int CLOUDLETS = 6;
    private final CloudSimPlus simulation;
    private final Datacenter dc0;
    private final DatacenterBroker broker0;
    private final List<Host> hostList;
    private final List<Vm> vmList;
    private final List<Cloudlet> cloudletList;

    /**
     * Different lengths that will be randomly assigned to created Cloudlets.
     */
    private static final long[] CLOUDLET_LENGTHS = {2000, 4000, 10000, 16000, 2000, 30000, 20000};
    private final ContinuousDistribution rand;

    private int createdCloudlets;
    private int createsVms;

    public static void main(String[] args) {
        new LoadBalancerByHorizontalVmScalingExample();
    }

    /**
     * Default constructor that builds the simulation scenario and starts the simulation.
     */
    private LoadBalancerByHorizontalVmScalingExample() {
        /*Enables just some level of log messages.
          Make sure to import org.cloudsimplus.util.Log;*/
        //Log.setLevel(ch.qos.logback.classic.Level.WARN);

        /*You can remove the seed parameter to get a dynamic one, based on current computer time.
        * With a dynamic seed you will get different results at each simulation run.*/
        final long seed = 1;
        rand = new UniformDistr(0, CLOUDLET_LENGTHS.length, seed);
        hostList = new ArrayList<>(HOSTS);
        vmList = new ArrayList<>(VMS);
        cloudletList = new ArrayList<>(CLOUDLETS);

        simulation = new CloudSimPlus();
        simulation.addOnClockTickListener(this::createNewCloudlets);

        dc0 = createDatacenter();
        broker0 = new DatacenterBrokerSimple(simulation);

        /**
         * Defines the Vm Destruction Delay Function as a lambda expression
         * so that the broker will wait 10 seconds before destroying any idle VM.
         * By commenting this line, no down scaling will be performed
         * and idle VMs will be destroyed just after all running Cloudlets
         * are finished and there is no waiting Cloudlet.
         * @see DatacenterBroker#setVmDestructionDelayFunction(Function)
         * */
        broker0.setVmDestructionDelay(10.0);

        vmList.addAll(createListOfScalableVms(VMS));

        createCloudletList();
        broker0.submitVmList(vmList);
        broker0.submitCloudletList(cloudletList);

        simulation.start();

        printSimulationResults();
    }

    private void printSimulationResults() {
        final var cloudletFinishedList = broker0.getCloudletFinishedList();
        final Comparator<Cloudlet> sortByVmId = comparingDouble(c -> c.getVm().getId());
        final Comparator<Cloudlet> sortByStartTime = comparingDouble(Cloudlet::getStartTime);
        cloudletFinishedList.sort(sortByVmId.thenComparing(sortByStartTime));

        new CloudletsTableBuilder(cloudletFinishedList).build();
    }

    private void createCloudletList() {
        for (int i = 0; i < CLOUDLETS; i++) {
            cloudletList.add(createCloudlet());
        }
    }

    /**
     * Creates new Cloudlets at every {@link #CLOUDLETS_CREATION_INTERVAL} seconds, up to the 50th simulation second.
     * A reference to this method is set as the {@link EventListener}
     * to the {@link Simulation#addOnClockTickListener(EventListener)}.
     * The method is called every time the simulation clock advances.
     *
     * @param info the information about the OnClockTick event that has happened
     */
    private void createNewCloudlets(final EventInfo info) {
        final long time = (long) info.getTime();
        if (time % CLOUDLETS_CREATION_INTERVAL == 0 && time <= 50) {
            final int cloudletsNumber = 4;
            System.out.printf("\t#Creating %d Cloudlets at time %d.%n", cloudletsNumber, time);
            final List<Cloudlet> newCloudlets = new ArrayList<>(cloudletsNumber);
            for (int i = 0; i < cloudletsNumber; i++) {
                final var cloudlet = createCloudlet();
                cloudletList.add(cloudlet);
                newCloudlets.add(cloudlet);
            }

            broker0.submitCloudletList(newCloudlets);
        }
    }

    /**
     * Creates a Datacenter and its Hosts.
     */
    private Datacenter createDatacenter() {
        for (int i = 0; i < HOSTS; i++) {
            hostList.add(createHost());
        }

        return new DatacenterSimple(simulation, hostList).setSchedulingInterval(SCHEDULING_INTERVAL);
    }

    private Host createHost() {
        final var peList = new ArrayList<Pe>(HOST_PES);
        for (int i = 0; i < HOST_PES; i++) {
            peList.add(new PeSimple(1000));
        }

        final long ram = 2048; // in Megabytes
        final long storage = 1000000; // in Megabytes
        final long bw = 10000; //in Megabits/s
        return new HostSimple(ram, bw, storage, peList)
            .setRamProvisioner(new ResourceProvisionerSimple())
            .setBwProvisioner(new ResourceProvisionerSimple())
            .setVmScheduler(new VmSchedulerTimeShared());
    }

    /**
     * Creates a list of initial VMs in which each VM is able to scale horizontally
     * when it is overloaded.
     *
     * @param vmsNumber number of VMs to create
     * @return the list of scalable VMs
     * @see #createHorizontalVmScaling(Vm)
     */
    private List<Vm> createListOfScalableVms(final int vmsNumber) {
        final var newVmList = new ArrayList<Vm>(vmsNumber);
        for (int i = 0; i < vmsNumber; i++) {
            final Vm vm = createVm();
            createHorizontalVmScaling(vm);
            newVmList.add(vm);
        }

        return newVmList;
    }

    /**
     * Creates a {@link HorizontalVmScaling} object for a given VM.
     *
     * @param vm the VM for which the Horizontal Scaling will be created
     * @see #createListOfScalableVms(int)
     */
    private void createHorizontalVmScaling(final Vm vm) {
        final var horizontalScaling = new HorizontalVmScalingSimple();
        horizontalScaling
             .setVmSupplier(this::createVm)
             .setOverloadPredicate(this::isVmOverloaded);
        vm.setHorizontalScaling(horizontalScaling);
    }

    /**
     * A {@link Predicate} that checks if a given VM is overloaded or not,
     * based on upper CPU utilization threshold.
     * A reference to this method is assigned to each {@link HorizontalVmScaling} created.
     *
     * @param vm the VM to check if it is overloaded
     * @return true if the VM is overloaded, false otherwise
     * @see #createHorizontalVmScaling(Vm)
     */
    private boolean isVmOverloaded(final Vm vm) {
        return vm.getCpuPercentUtilization() > 0.7;
    }

    /**
     * Creates a Vm object.
     *
     * @return the created Vm
     */
    private Vm createVm() {
        final int id = createsVms++;
        return new VmSimple(id, 1000, 2)
            .setRam(512).setBw(1000).setSize(10000)
            .setCloudletScheduler(new CloudletSchedulerTimeShared());
    }

    private Cloudlet createCloudlet() {
        final int id = createdCloudlets++;
        final var utilizadionModelDynamic = new UtilizationModelDynamic(0.1);

        //randomly selects a length for the cloudlet
        final long length = CLOUDLET_LENGTHS[(int) rand.sample()];
        return new CloudletSimple(id, length, 2)
            .setFileSize(1024)
            .setOutputSize(1024)
            .setUtilizationModelBw(utilizadionModelDynamic)
            .setUtilizationModelRam(utilizadionModelDynamic)
            .setUtilizationModelCpu(new UtilizationModelFull());
    }
}
