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
import org.cloudsimplus.autoscaling.VerticalVmScaling;
import org.cloudsimplus.autoscaling.VerticalVmScalingSimple;
import org.cloudsimplus.autoscaling.resources.ResourceScaling;
import org.cloudsimplus.autoscaling.resources.ResourceScalingGradual;
import org.cloudsimplus.autoscaling.resources.ResourceScalingInstantaneous;
import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.core.Simulation;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.listeners.EventInfo;
import org.cloudsimplus.listeners.EventListener;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.resources.Processor;
import org.cloudsimplus.schedulers.vm.VmSchedulerTimeShared;
import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static java.util.Comparator.comparingDouble;

/**
 * An example that scales VM PEs up or down, according to the arrival of Cloudlets.
 * A {@link VerticalVmScaling}
 * is set to each {@link #createListOfScalableVms(int) initially created VM}.
 * Every VM will check at {@link #SCHEDULING_INTERVAL specific time intervals}
 * if its PEs {@link #upperCpuUtilizationThreshold(Vm) are over or underloaded},
 * according to a <b>static computed utilization threshold</b>.
 * Then it requests such PEs to be up or down scaled, increasing or decreasing
 * the number of PEs.
 *
 * <p>The example uses the CloudSim Plus {@link EventListener} feature
 * to enable monitoring the simulation and dynamically create objects such as Cloudlets and VMs at runtime.
 * The {@link Simulation#addOnClockTickListener(EventListener) onClockTick listener}.
 * enables getting notifications when the simulation clock changes, then creating and submitting new cloudlets.
 * </p>
 *
 * @author Manoel Campos da Silva Filho
 * @since CloudSim Plus 1.2.0
 * @see VerticalVmRamScalingExample
 * @see VerticalVmCpuScalingDynamicThreshold
 */
public class VerticalVmCpuScalingExample {
    /**
     * The interval in which the Datacenter will schedule events (in seconds).
     * As lower is this interval, sooner the processing of VMs and Cloudlets is updated,
     * resulting in more notifications about the simulation execution.
     * However, it can affect the simulation performance.
     *
     * <p>A larger schedule interval makes processing of VMs to be updated less frequently.
     * If a VM is overloaded, just after that interval the creation of a new one will be requested
     * by the VM's {@link HorizontalVmScaling Horizontal Scaling} mechanism.</p>
     *
     * <p>If this interval is defined using a small value, you may get
     * more dynamically created VMs than expected. Accordingly, this value has to be trade-off.
     * For more details, see {@link Datacenter#getSchedulingInterval()}.</p>
    */
    private static final int SCHEDULING_INTERVAL = 1;
    private static final int HOSTS = 1;

    private static final int HOST_PES = 64;
    private static final int VMS = 1;
    private static final int VM_PES = 14;
    private static final int VM_RAM = 1200;
    private static final int VM_MIPS = 1000;
    private final CloudSimPlus simulation;
    private final DatacenterBroker broker0;
    private List<Host> hostList;
    private List<Vm> vmList;
    private List<Cloudlet> cloudletList;

    private static final int CLOUDLETS = 10;

    /** A base length (in MI) for initially created Cloudlets. */
    private static final int CLOUDLET_LEN_BASE = 80_000;

    private int createsVms;

    public static void main(String[] args) {
        new VerticalVmCpuScalingExample();
    }

    /**
     * Default constructor that builds the simulation scenario and starts the simulation.
     */
    private VerticalVmCpuScalingExample() {
        /*Enables just some level of log messages.
          Make sure to import org.cloudsimplus.util.Log;*/
        //Log.setLevel(ch.qos.logback.classic.Level.WARN);

        hostList = new ArrayList<>(HOSTS);
        vmList = new ArrayList<>(VMS);
        cloudletList = new ArrayList<>(CLOUDLETS);

        simulation = new CloudSimPlus();
        simulation.addOnClockTickListener(this::onClockTickListener);

        createDatacenter();
        broker0 = new DatacenterBrokerSimple(simulation);

        vmList.addAll(createListOfScalableVms(VMS));

        createCloudletListsWithDifferentDelays();
        broker0.submitVmList(vmList);
        broker0.submitCloudletList(cloudletList);

        simulation.start();

        printSimulationResults();
    }

    /**
     * Shows updates every time the simulation clock changes.
     * @param evt information about the event happened (that for this Listener is just the simulation time)
     */
    private void onClockTickListener(EventInfo evt) {
        vmList.forEach(vm ->
            System.out.printf(
                "\t\tTime %6.1f: Vm %d CPU Usage: %6.2f%% (%2d vCPUs. Running Cloudlets: #%d). RAM usage: %.2f%% (%d MB)%n",
                evt.getTime(), vm.getId(), vm.getCpuPercentUtilization()*100.0, vm.getPesNumber(),
                vm.getCloudletScheduler().getCloudletExecList().size(),
                vm.getRam().getPercentUtilization()*100, vm.getRam().getAllocatedResource())
        );
    }

    private void printSimulationResults() {
        final var finishedCloudletList = broker0.getCloudletFinishedList();
        final Comparator<Cloudlet> sortByVmId = comparingDouble(c -> c.getVm().getId());
        final Comparator<Cloudlet> sortByStartTime = comparingDouble(Cloudlet::getStartTime);
        finishedCloudletList.sort(sortByVmId.thenComparing(sortByStartTime));

        new CloudletsTableBuilder(finishedCloudletList).build();
    }

    private void createDatacenter() {
        for (int i = 0; i < HOSTS; i++) {
            hostList.add(createHost());
        }

        final var dc0 = new DatacenterSimple(simulation, hostList);
        dc0.setSchedulingInterval(SCHEDULING_INTERVAL);
    }

    private Host createHost() {
        final var peList = new ArrayList<Pe>(HOST_PES);
        for (int i = 0; i < HOST_PES; i++) {
            peList.add(new PeSimple(1000));
        }

        final long ram = 20000; //in Megabytes
        final long bw = 100000; //in Megabytes
        final long storage = 10000000; //in Megabytes
        return new HostSimple(ram, bw, storage, peList).setVmScheduler(new VmSchedulerTimeShared());
    }

    /**
     * Creates a list of initial VMs in which each VM is able to scale vertically
     * when it is over or underloaded.
     *
     * @param vmsNumber number of VMs to create
     * @return the list of scalable VMs
     * @see #createVerticalPeScaling()
     */
    private List<Vm> createListOfScalableVms(final int vmsNumber) {
        final var newVmList = new ArrayList<Vm>(vmsNumber);
        for (int i = 0; i < vmsNumber; i++) {
            final var vm = createVm();
            vm.setPeVerticalScaling(createVerticalPeScaling());
            newVmList.add(vm);
        }

        return newVmList;
    }

    private Vm createVm() {
        final int id = createsVms++;

        return new VmSimple(id, VM_MIPS, VM_PES).setRam(VM_RAM).setBw(1000).setSize(10000);
    }

    /**
     * Creates a {@link VerticalVmScaling} for scaling VM's CPU when it is under or overloaded.
     *
     * <p>Realize the lower and upper thresholds are defined inside this method by using
     * references to the methods {@link #lowerCpuUtilizationThreshold(Vm)}
     * and {@link #upperCpuUtilizationThreshold(Vm)}.
     * These methods enable defining thresholds in a dynamic way,
     * setting different thresholds for distinct VMs.
     * </p>
     *
     * <p>
     * However, if you are defining thresholds in a static way,
     * and they are the same for all VMs, you can use a Lambda Expression
     * like below, instead of creating a new method that just returns a constant value:<br>
     *
     * {@code verticalCpuScaling.setLowerThresholdFunction(vm -> 0.4);}
     * </p>
     *
     * @see #createListOfScalableVms(int)
     */
    private VerticalVmScaling createVerticalPeScaling() {
        //The percentage in which the number of PEs has to be scaled
        final double scalingFactor = 0.1;
        final var verticalCpuScaling = new VerticalVmScalingSimple(Processor.class, scalingFactor);

        /* By uncommenting the line below, you will see that, instead of gradually
         * increasing or decreasing the number of PEs, when the scaling object detects
         * the CPU usage is above or below the defined thresholds,
         * it will automatically calculate the number of PEs to add/remove,
         * moving the VM from the over or underload state.
        */
        //verticalCpuScaling.setResourceScaling(new ResourceScalingInstantaneous());

        /** Different from the commented line above, the line below implements a ResourceScaling using a Lambda Expression.
         * It is just an example which scales the resource twice the amount defined by the scaling factor.
         *
         * Realize that if the setResourceScaling method isn't called, a ResourceScalingGradual will be used,
         * scaling the resource according to the scaling factor.
         * The lower and upper thresholds after this line can also be defined using a Lambda Expression.
         *
         * So, here we are defining our own {@link ResourceScaling} instead of
         * using the available ones such as the {@link ResourceScalingGradual}
         * or {@link ResourceScalingInstantaneous}.
         */
        final double multiplier = 2;
        verticalCpuScaling.setResourceScaling(vs -> multiplier * vs.getScalingFactor() * vs.getAllocatedResource())
                          .setLowerThresholdFunction(this::lowerCpuUtilizationThreshold)
                          .setUpperThresholdFunction(this::upperCpuUtilizationThreshold);

        return verticalCpuScaling;
    }

    /**
     * Defines the minimum CPU utilization percentage that indicates a Vm is underloaded.
     * This function is using a statically defined threshold, but it would be defined
     * a dynamic one based on any condition you want.
     * A reference to this method is assigned to each {@link VerticalVmScaling} created.
     *
     * @param vm the VM to check if its CPU is underloaded.
     *        <b>The parameter is not being used internally, which means the same
     *        threshold is used for any Vm.</b>
     * @return the lower CPU utilization threshold
     * @see #createVerticalPeScaling()
     */
    private double lowerCpuUtilizationThreshold(Vm vm) {
        return 0.4;
    }

    /**
     * Defines the maximum CPU utilization percentage that indicates a Vm is overloaded.
     * This function is using a statically defined threshold, but it would be defined
     * a dynamic one based on any condition you want.
     * A reference to this method is assigned to each {@link VerticalVmScaling} created.
     *
     * @param vm the VM to check if its CPU is overloaded.
     *        <b>The parameter is not being used internally, that means the same
     *        threshold is used for any Vm.</b>
     * @return the upper CPU utilization threshold
     * @see #createVerticalPeScaling()
     */
    private double upperCpuUtilizationThreshold(Vm vm) {
        return 0.8;
    }

    /**
     * Creates several Cloudlets by increasing the arrival delay,
     * simulating their arrivals at different times.
     * That enables VMs CPU usage to rise gradually, along the arrival of
     * new Cloudlets (triggering CPU up scaling at some point in time).
     * Check the logs to understand how the scaling is working.
     */
    private void createCloudletListsWithDifferentDelays() {
        final int pesNumber = 2;
        final long cloudletsNumber = Math.round(CLOUDLETS * 1.5);
        for (int i = 1; i <= cloudletsNumber; i++) {
            final int delay = i * 2;
            final int length = CLOUDLET_LEN_BASE * i;
            cloudletList.add(createCloudlet(length, pesNumber, delay));
        }
    }

    /**
     * Creates a single Cloudlet with no delay, meaning the Cloudlet arrival time will
     * be zero (exactly when the simulation starts).
     *
     * @param length the Cloudlet length
     * @param pesNumber the number of PEs the Cloudlets requires
     * @return the created Cloudlet
     */
    private Cloudlet createCloudlet(final long length, final int pesNumber) {
        return createCloudlet(length, pesNumber, 0);
    }

    /**
     * Creates a single Cloudlet.
     *
     * @param length the length of the cloudlet to create.
     * @param pesNumber the number of PEs the Cloudlets requires.
     * @param delay the delay defining the arrival time of the Cloudlet at the Cloud infrastructure.
     * @return the created Cloudlet
     */
    private Cloudlet createCloudlet(final long length, final int pesNumber, final double delay) {
        /*
        Since a VM PE isn't used by two Cloudlets at the same time,
        the Cloudlet can use 100% of that CPU capacity at the time
        it is running. Even if a CloudletSchedulerTimeShared is used
        to share the same VM PE (vPE) between multiple Cloudlets,
        just one Cloudlet uses the PE at a time.
        Then it is preempted to enable other Cloudlets to use such a vPE.
         */
        final var utilizationCpu = new UtilizationModelFull();

        //Each Cloudlet will use 1% of RAM and BW
        final var utilizationModelDynamic = new UtilizationModelDynamic(0.01);
        final var cl = new CloudletSimple(length, pesNumber);
        cl
            .setUtilizationModelBw(utilizationModelDynamic)
            .setUtilizationModelRam(utilizationModelDynamic)
            .setUtilizationModelCpu(utilizationCpu)
            .setSubmissionDelay(delay);
        return cl;
    }
}
