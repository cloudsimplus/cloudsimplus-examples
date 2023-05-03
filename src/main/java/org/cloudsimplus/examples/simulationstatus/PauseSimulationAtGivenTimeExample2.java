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
package org.cloudsimplus.examples.simulationstatus;

import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.core.Simulation;
import org.cloudsimplus.core.events.SimEvent;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.listeners.EventInfo;
import org.cloudsimplus.listeners.EventListener;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerSpaceShared;
import org.cloudsimplus.schedulers.vm.VmSchedulerTimeShared;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;

import java.util.ArrayList;
import java.util.List;

/**
 * An example showing how to pause the simulation at a given time in order to collect
 * some partial results. In this example, such results are the cloudlets that have finished so far.
 * The example creates 4 Cloudlets that will run sequentially using a {@link CloudletSchedulerSpaceShared}.
 *
 * <p>The pause is scheduled after the simulation starts, by adding an
 * {@link Simulation#addOnEventProcessingListener(EventListener) OnEventProcessingListener}
 * that will be notified every time a simulation event is processed.</p>
 *
 * <p>This example uses CloudSim Plus Listener features to intercept when
 * the simulation was paused, allowing to collect the desired data.
 * This example uses the Java 8+ Lambda Functions features
 * to pass a listener to a {@link CloudSimPlus} instance, by means of the
 * {@link CloudSimPlus#addOnSimulationPauseListener(EventListener)} method.
 * However, the same feature can be used for Java 7 passing an anonymous class
 * that implements {@code EventListener<EventInfo>}.</p>
 *
 * @author Manoel Campos da Silva Filho
 * @since CloudSim Plus 1.0
 *
 * @see CloudSimPlus#pause(double)
 * @see CloudSimPlus#addOnSimulationPauseListener(EventListener)
 * @see EventListener
 */
public class PauseSimulationAtGivenTimeExample2 {
    /**
     * The interval in which the Datacenter will schedule events.
     * As lower is this interval, sooner the processing of Cloudlets inside VMs
     * is updated and you will get more notifications about the simulation execution.
     * However, it can affect the simulation performance.
     *
     * <p>For this example, a large schedule interval such as 15 will make that just
     * at every 15 seconds the processing of Cloudlets is updated.
     * Consider that each Cloudlet takes 10 seconds to finish and that
     * the simulation is paused at time 22. With a scheduling interval of 15 seconds,
     * at the time 22, the Cloudlets execution will be updated just 1 time, that means
     * if we get the list of finished cloudlets at time 22, it will not be updated yet with
     * the second Cloudlet that finished at time 20.</p>
     *
     * <p><b>Realise that changing this value, the method {@link #pauseSimulationAtSpecificTime(SimEvent)}
     * may not be called at the expected time, doesn't making the simulation to pause.
     * If you want to change this value, the number used inside the method mentioned above
     * has to be a multiple of this value.</b></p>
     *
     * <p>Considering this characteristics, the scheduling interval
     * was set to a lower value to get updates as soon as possible.
     * For more details, see {@link Datacenter#getSchedulingInterval()}.</p>
     */
    public static final int SCHEDULING_INTERVAL = 1;

    private final CloudSimPlus simulation;
    private final DatacenterBrokerSimple broker;
    private List<Cloudlet> cloudletList;
    private List<Vm> vmList;

    public static void main(String[] args) {
        new PauseSimulationAtGivenTimeExample2();
    }

    /**
     * Default constructor that builds the simulation.
     */
    private PauseSimulationAtGivenTimeExample2() {
        /*Enables just some level of log messages.
          Make sure to import org.cloudsimplus.util.Log;*/
        //Log.setLevel(ch.qos.logback.classic.Level.WARN);

        System.out.println("Starting " + getClass().getSimpleName());
        this.simulation = new CloudSimPlus();

        this.vmList = new ArrayList<>();
        this.cloudletList = new ArrayList<>();

        final var datacenter0 = createDatacenter();

        /*
        Creates a Broker accountable for submission of VMs and Cloudlets
        on behalf of a given cloud user (customer).
        */
        this.broker = new DatacenterBrokerSimple(simulation);

        final var vm0 = createVm();
        this.vmList.add(vm0);
        this.broker.submitVmList(vmList);

        for(int i = 0; i < 4; i++) {
            final var cloudlet = createCloudlet(vm0);
            this.cloudletList.add(cloudlet);
        }

        this.broker.submitCloudletList(cloudletList);

        /**
         * Sets a Listener that will be notified when any {@link SimEvent} is processed during the simulation
         * execution.
         * Realise that it is being used Java 8 Lambda Expressions to define a Listener
         * that will be executed only when a simulation event happens.
         * See the {@link #pauseSimulationAtSpecificTime(SimEvent)} method for more details.
         */
        this.simulation.addOnEventProcessingListener(this::pauseSimulationAtSpecificTime);

        /*
        * Sets a Listener that will be notified when the simulation is paused.
        * Realise that it is being used Java 8 Lambda Expressions to define a Listener
        * that will be executed only when the simulation is paused.
        * */
        this.simulation
            .addOnSimulationPauseListener(this::printCloudletsFinishedSoFarAndResumeSimulation);

        /* Starts the simulation and waits all cloudlets to be executed. */
        this.simulation.start();

        /*Prints results when the simulation is over
        (you can use your own code here to print what you want from this cloudlet list)*/
        printsListOfcloudletFinishedList("Finished cloudlets after simulation is complete");

        System.out.println(getClass().getSimpleName() + " finished!");
    }

    /**
     * Pauses the simulation when any event occurs at the a defined time.
     */
    private void pauseSimulationAtSpecificTime(SimEvent simEvent) {
        if(Math.floor(simEvent.getTime()) == 22){
            simulation.pause();
        }
    }

    private void printCloudletsFinishedSoFarAndResumeSimulation(EventInfo pauseInfo) {
        System.out.printf("%n# Simulation paused at %.2f second%n", pauseInfo.getTime());
        printsListOfcloudletFinishedList("Cloudlets Finished So Far");
        System.out.println();
        this.simulation.resume();
    }

    private void printsListOfcloudletFinishedList(String title) {
        //Gets the list of cloudlets finished so far a prints
        new CloudletsTableBuilder(broker.getCloudletFinishedList())
            .setTitle(title)
            .build();
    }

    private DatacenterSimple createDatacenter() {
        final var hostList = new ArrayList<Host>();
        final var host0 = createHost();
        hostList.add(host0);

        final var dc = new DatacenterSimple(simulation, hostList);
        dc.setSchedulingInterval(SCHEDULING_INTERVAL);
        return dc;
    }

    private Host createHost() {
        final long mips = 1000; // capacity of each CPU core (in Million Instructions per Second)
        final long ram = 2048; // host memory (Megabyte)
        final long storage = 1000000; // host storage (Megabyte)
        final long bw = 10000; //in Megabits/s

        final var peList = new ArrayList<Pe>(); //List of CPU cores

        /*Creates the Host's CPU cores and defines the provisioner
        used to allocate each core for requesting VMs.*/
        peList.add(new PeSimple(mips));

        return new HostSimple(ram, bw, storage, peList).setVmScheduler(new VmSchedulerTimeShared());
    }

    private Vm createVm() {
        return new VmSimple(1000, 1)
                .setRam(512)
                .setBw(1000)
                .setSize(10000)
                .setCloudletScheduler(new CloudletSchedulerSpaceShared());
    }

    private Cloudlet createCloudlet(Vm vm) {
        return new CloudletSimple(10000, vm.getPesNumber())
                .setFileSize(300)
                .setOutputSize(300)
                .setUtilizationModel(new UtilizationModelFull())
                .setVm(vm);
    }
}
