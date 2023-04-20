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
package org.cloudsimplus.examples.dynamic;

import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.core.CloudSimTag;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerTimeShared;
import org.cloudsimplus.schedulers.vm.VmSchedulerTimeShared;
import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;

import java.util.ArrayList;
import java.util.List;

/**
 * An example showing how to create Cloudlets with an indefinite length,
 * so that each Cloudlet stops running only if:
 * <ul>
 *  <li>(i) it reaches {@link Long#MAX_VALUE} MI executed or</li>
 *  <li>(ii) a positive length is dynamically defined (the time or event when the length of such Cloudlets are set
 *  can be defined in uncountable ways, according to developer needs)</li>
 *  <li>(iii) {@link CloudSimTag#CLOUDLET_FINISH} message is sent to the {@link DatacenterBroker}.</li>
 *  <li>(iv) the simulation is terminated by defining a termination time.</li>
 * </ul>
 *
 * In this example it is called the {@link CloudSimPlus#terminateAt(double)}
 * method to define when the simulation must finish.
 * At that moment, the length of the Cloudlets is set so that they can finish executing.
 *
 * @author Manoel Campos da Silva Filho
 * @since CloudSim Plus 4.0.0
 */
public class IndefiniteLengthCloudlet {
    private static final int HOSTS = 1;
    private static final int HOST_PES = 8;

    private static final int VMS = 2;
    private static final int VM_PES = 4;
    private static final int VM_MIPS = 1000;

    private static final int CLOUDLETS = 2;
    private static final int CLOUDLET_PES = 2;

    /**
     * Defines a negative length for Cloudlets so that
     * they keep running until explicitly requested to finish.
     * In this example, Cloudlets are finished only when
     * the simulation termination time is reached.
     *
     * @see CloudSimPlus#terminateAt(double)
     */
    private static final int CLOUDLET_LENGTH = -10000;

    private final CloudSimPlus simulation;
    private final DatacenterBroker broker0;
    private List<Vm> vmList;
    private List<Cloudlet> cloudletList;
    private Datacenter datacenter0;

    public static void main(String[] args) {
        new IndefiniteLengthCloudlet();
    }

    private IndefiniteLengthCloudlet() {
        /*Enables just some level of log messages.
          Make sure to import org.cloudsimplus.util.Log;*/
        //Log.setLevel(ch.qos.logback.classic.Level.WARN);

        simulation = new CloudSimPlus();
        //Keeps the simulation running for 1 hour
        simulation.terminateAt(60*60);
        datacenter0 = createDatacenter();

        //Creates a broker that is a software acting on behalf of a cloud customer to manage his/her VMs and Cloudlets
        broker0 = new DatacenterBrokerSimple(simulation);

        vmList = createVms();

        cloudletList = createCloudlets();
        broker0.submitVmList(vmList);
        broker0.submitCloudletList(cloudletList);

        simulation.start();

        //final var cloudletFinishedList = broker0.getCloudletSubmittedList();
        final var cloudletFinishedList = broker0.getCloudletFinishedList();
        new CloudletsTableBuilder(cloudletFinishedList).build();
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

        return new DatacenterSimple(simulation, hostList);
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
        host.setVmScheduler(new VmSchedulerTimeShared());
        return host;
    }

    /**
     * Creates a list of VMs.
     */
    private List<Vm> createVms() {
        final var newVmList = new ArrayList<Vm>(VMS);
        for (int i = 0; i < VMS; i++) {
            final var vm =
                new VmSimple(i, VM_MIPS, VM_PES)
                    .setRam(512).setBw(1000).setSize(10000)
                    .setCloudletScheduler(new CloudletSchedulerTimeShared());

            newVmList.add(vm);
        }

        return newVmList;
    }

    /**
     * Creates Cloudlets with indefinite length by setting a negative value.
     * This way, the Cloudlets keep running until one of the conditions explained
     * in the documentation of this class is met.
     */
    private List<Cloudlet> createCloudlets() {
        final var newCloudletList = new ArrayList<Cloudlet>(CLOUDLETS);
        final var utilizationFull = new UtilizationModelFull();
        final var utilizationDynamic = new UtilizationModelDynamic(1.0/CLOUDLETS);

        for (int i = 0; i < CLOUDLETS; i++) {
            final var cloudlet =
                new CloudletSimple(CLOUDLET_LENGTH, CLOUDLET_PES)
                    .setFileSize(1024)
                    .setOutputSize(1024)
                    .setUtilizationModelCpu(utilizationFull)
                    .setUtilizationModelRam(utilizationDynamic)
                    .setUtilizationModelBw(utilizationDynamic);
            newCloudletList.add(cloudlet);
        }

        return newCloudletList;
    }
}
