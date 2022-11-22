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

import org.cloudbus.cloudsim.brokers.DatacenterBroker;
import org.cloudbus.cloudsim.brokers.DatacenterBrokerSimple;
import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.Simulation;
import org.cloudbus.cloudsim.datacenters.Datacenter;
import org.cloudbus.cloudsim.datacenters.DatacenterSimple;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.hosts.HostSimple;
import org.cloudbus.cloudsim.resources.Pe;
import org.cloudbus.cloudsim.resources.PeSimple;
import org.cloudbus.cloudsim.resources.Ram;
import org.cloudbus.cloudsim.schedulers.vm.VmSchedulerTimeShared;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModel;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelDynamic;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelFull;
import org.cloudbus.cloudsim.vms.Vm;
import org.cloudbus.cloudsim.vms.VmSimple;
import org.cloudsimplus.autoscaling.HorizontalVmScaling;
import org.cloudsimplus.autoscaling.VerticalVmScaling;
import org.cloudsimplus.autoscaling.VerticalVmScalingSimple;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;
import org.cloudsimplus.listeners.EventInfo;
import org.cloudsimplus.listeners.EventListener;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static java.util.Comparator.comparingDouble;
import static org.cloudbus.cloudsim.utilizationmodels.UtilizationModel.Unit;

/**
 * An example that scales VM RAM up or down, according to resource requests from running Cloudlets.
 * It relies on {@link UtilizationModelDynamic} to
 * set Vm RAM usage increasingly along the time.
 * Cloudlets are created with different lengths, so that they will finish gradually.
 * This way, it's possible to check that RAM usage decreases along the time.
 *
 * <p>A {@link VerticalVmScaling}
 * is set to each initially created VM (check {@link #createListOfScalableVms(int)}),
 * which will check at {@link #SCHEDULING_INTERVAL specific time intervals}
 * if a VM's RAM {@link #upperRamUtilizationThreshold(Vm) is overloaded or not},
 * according to a <b>static computed utilization threshold</b>.
 * Then it requests the RAM to be scaled up.</p>
 *
 * <p>The example uses the CloudSim Plus {@link EventListener} feature
 * to enable monitoring the simulation and dynamically creating objects such as Cloudlets and VMs.
 * It creates a Listener for the {@link Simulation#addOnClockTickListener(EventListener) onClockTick event}
 * to get notifications when the simulation clock advances, then creating and submitting new cloudlets.
 * </p>
 *
 * @author Manoel Campos da Silva Filho
 * @since CloudSim Plus 1.2.0
 * @see VerticalVmCpuScalingExample
 */
public class VerticalVmRamScalingExample {
    /**
     * The interval in which the Datacenter will schedule events.
     * As lower this interval is, sooner the processing of VMs and Cloudlets
     * is updated, and you get more notifications about the simulation execution.
     * However, it can affect the simulation performance.
     *
     * <p>For this example, a larger schedule interval such as 15 will make that just
     * at every 15 seconds the processing of VMs is updated. If a VM is overloaded, just
     * after this time the creation of a new one will be requested
     * by the VM's {@link HorizontalVmScaling Horizontal Scaling} mechanism.</p>
     *
     * <p>If this interval is defined using a small value, you may get
     * more dynamically created VMs than expected. Accordingly, this value
     * has to be trade-off.
     * For more details, see {@link Datacenter#getSchedulingInterval()}.</p>
    */
    private static final int SCHEDULING_INTERVAL = 1;

    private static final int HOSTS = 1;
    private static final int HOST_PES = 8;

    /** Host RAM capacity in Megabytes. */
    private static final long HOST_RAM = 20000;

    private static final int HOST_MIPS = 1000;
    private static final int VMS = 1;
    /**
     * Vm RAM capacity in Megabytes.
     */
    private static final int VM_RAM = 1000;
    private static final int VM_PES = 5;
    private static final int VM_MIPS = 1000;
    private final CloudSim simulation;
    private final DatacenterBroker broker0;
    private final List<Host> hostList;
    private final List<Vm> vmList;
    private final List<Cloudlet> cloudletList;

    /**
     * Different lengths (in MI) to be used when creating Cloudlets.
     * For each VM, one Cloudlet for each one of these lengths will be created.
     * Creating Cloudlets with different lengths, since some Cloudlets will finish prior to others along the time,
     * the VM resource usage will reduce when a Cloudlet finishes.
     */
    private static final long[] CLOUDLET_LENGTHS = {40_000, 50_000, 60_000, 70_000, 80_000};

    private static final int CLOUDLET_PES = 1;

    private int createdCloudlets;
    private int createsVms;

    public static void main(String[] args) {
        new VerticalVmRamScalingExample();
    }

    /**
     * Default constructor that builds the simulation scenario and starts the simulation.
     */
    private VerticalVmRamScalingExample() {
        /*Enables just some level of log messages.
          Make sure to import org.cloudsimplus.util.Log;*/
        //Log.setLevel(ch.qos.logback.classic.Level.WARN);

        hostList = new ArrayList<>(HOSTS);
        vmList = new ArrayList<>(VMS);
        cloudletList = new ArrayList<>(CLOUDLET_LENGTHS.length);

        simulation = new CloudSim();
        simulation.addOnClockTickListener(this::onClockTickListener);

        createDatacenter();
        broker0 = new DatacenterBrokerSimple(simulation);

        vmList.addAll(createListOfScalableVms(VMS));

        createCloudletList();
        broker0.submitVmList(vmList);
        broker0.submitCloudletList(cloudletList);

        simulation.start();

        printSimulationResults();
    }

    private void onClockTickListener(EventInfo event) {
        for (Vm vm : vmList) {
            System.out.printf("\t\tTime %6.1f: Vm %d Ram Usage: %6.2f%% (%4d of %4d MB)",
                event.getTime(), vm.getId(), vm.getRam().getPercentUtilization() * 100.0,
                vm.getRam().getAllocatedResource(), vm.getRam().getCapacity());

            System.out.printf(" | Host Ram Allocation: %6.2f%% (%5d of %5d MB). Running Cloudlets: %d",
                vm.getHost().getRam().getPercentUtilization() * 100,
                vm.getHost().getRam().getAllocatedResource(),
                vm.getHost().getRam().getCapacity(), vm.getCloudletScheduler().getCloudletExecList().size());
        }
    }

    private void printSimulationResults() {
        final var finishedCloudletsList = broker0.getCloudletFinishedList();
        final Comparator<Cloudlet> sortByVmId = comparingDouble(c -> c.getVm().getId());
        final Comparator<Cloudlet> sortByStartTime = comparingDouble(Cloudlet::getExecStartTime);
        finishedCloudletsList.sort(sortByVmId.thenComparing(sortByStartTime));

        new CloudletsTableBuilder(finishedCloudletsList).build();
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
            peList.add(new PeSimple(HOST_MIPS));
        }

        final long bw = 100000; //in Megabytes
        final long storage = 10000000; //in Megabytes
        return new HostSimple(HOST_RAM, bw, storage, peList).setVmScheduler(new VmSchedulerTimeShared());
    }

    /**
     * Creates a list of initial VMs in which each one is able to scale horizontally
     * when it is overloaded.
     *
     * @param vmsNumber number of VMs to create
     * @return the list of scalable VMs
     * @see #createVerticalRamScalingForVm(Vm)
     */
    private List<Vm> createListOfScalableVms(final int vmsNumber) {
        final var newVmList = new ArrayList<Vm>(vmsNumber);
        for (int i = 0; i < vmsNumber; i++) {
            Vm vm = createVm();
            createVerticalRamScalingForVm(vm);
            newVmList.add(vm);
        }

        return newVmList;
    }

    private Vm createVm() {
        final int id = createsVms++;

        return new VmSimple(id, VM_MIPS, VM_PES)
            .setRam(VM_RAM).setBw(1000).setSize(10000);
    }

    /**
     * Creates a {@link VerticalVmScaling} for the RAM of a given VM.
     *
     * @param vm the VM in which the VerticalVmScaling will be created
     * @see #createListOfScalableVms(int)
     */
    private void createVerticalRamScalingForVm(Vm vm) {
        var verticalRamScaling = new VerticalVmScalingSimple(Ram.class, 0.1);
        /* By uncommenting the line below, you will see that, instead of gradually
         * increasing or decreasing the RAM when the scaling object detects
         * the RAM usage is up or down the defined thresholds,
         * it will automatically calculate the amount of RAM to add/remove to
         * move the VM from the over or under-load condition.
        */
        //verticalRamScaling.setResourceScaling(new ResourceScalingInstantaneous());
        verticalRamScaling.setLowerThresholdFunction(this::lowerRamUtilizationThreshold);
        verticalRamScaling.setUpperThresholdFunction(this::upperRamUtilizationThreshold);
        vm.setRamVerticalScaling(verticalRamScaling);
    }

    /**
     * Defines the minimum RAM utilization percentage that indicates a Vm is underloaded.
     * This function is using a statically defined threshold, but it would be defined
     * a dynamic threshold based on any condition you want.
     * A reference to this method is assigned to each {@link VerticalVmScaling} created.
     *
     * @param vm the VM to check if its RAM is underloaded.
     *        The parameter is not being used internally, that means the same
     *        threshold is applied for any Vm.
     * @return the lower RAM utilization threshold
     */
    private double lowerRamUtilizationThreshold(Vm vm) {
        return 0.5;
    }

    /**
     * Defines the maximum RAM utilization percentage that indicates a Vm is overloaded.
     * This function is using a statically defined threshold, but it would be defined
     * a dynamic threshold based on any condition you want.
     * A reference to this method is assigned to each {@link VerticalVmScaling} created.
     *
     * @param vm the VM to check if its RAM is overloaded.
     *        The parameter is not being used internally, that means the same
     *        threshold is applied for any Vm.
     * @return the upper RAM utilization threshold
     */
    private double upperRamUtilizationThreshold(Vm vm) {
        return 0.7;
    }

    private void createCloudletList() {
        final int initialRamUtilization1 = 100; //MB

        final var ramModel1 = new UtilizationModelDynamic(Unit.ABSOLUTE, initialRamUtilization1);
        for (long length: CLOUDLET_LENGTHS) {
            cloudletList.add(createCloudlet(ramModel1, length));
        }

        final int initialRamUtilization2 = 10; //MB
        final int maxRamUtilization = 500; //MB
        final var ramModel2 = new UtilizationModelDynamic(Unit.ABSOLUTE, initialRamUtilization2);
        ramModel2
            .setMaxResourceUtilization(maxRamUtilization)
            .setUtilizationUpdateFunction(this::utilizationIncrement);
        cloudletList.get(0).setUtilizationModelRam(ramModel2);
    }

    /**
     * Creates a Cloudlet
     * @param ramUtilizationModel the object that defines how Cloudlet will use RAM
     * @param length the Cloudlet length in MI
     * @return
     */
    private Cloudlet createCloudlet(final UtilizationModel ramUtilizationModel, final long length) {
        final int id = createdCloudlets++;
        final double initialBwUtilizationPercent = 0.1;
        return new CloudletSimple(id, length, CLOUDLET_PES)
                .setUtilizationModelCpu(new UtilizationModelFull())
                .setUtilizationModelBw(new UtilizationModelDynamic(initialBwUtilizationPercent))
                .setUtilizationModelRam(ramUtilizationModel);
    }

    /**
     * Increments the RAM resource utilization (defined in absolute values)
     * in 10MB every second.
     *
     * @param um the Utilization Model that has called this function
     * @return the new resource utilization after the increment
     */
    private double utilizationIncrement(UtilizationModelDynamic um) {
        final int ramIncreaseMB = 10;
        return um.getUtilization() + um.getTimeSpan() * ramIncreaseMB;
    }
}
