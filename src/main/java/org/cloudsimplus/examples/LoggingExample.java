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

import ch.qos.logback.classic.Level;
import org.cloudsimplus.allocationpolicies.VmAllocationPolicy;
import org.cloudsimplus.allocationpolicies.migration.VmAllocationPolicyMigrationStaticThreshold;
import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.core.Identifiable;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.schedulers.cloudlet.CloudletScheduler;
import org.cloudsimplus.selectionpolicies.VmSelectionPolicyRandomSelection;
import org.cloudsimplus.util.Log;
import org.cloudsimplus.utilizationmodels.UtilizationModel;
import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * An example showing how CloudSim Plus
 * is flexible in managing logs of different entities.
 *
 * @author Manoel Campos da Silva Filho
 * @since CloudSim Plus 5.3.0
 * @see #configureLogs()
 */
public class LoggingExample {
    private static final int SCHEDULING_INTERVAL_SECS = 10;
    private static final double HOST_OVER_UTILIZATION_MIGRATION_THRESHOLD = 0.7;

    private static final int[] HOST_PES = {8, 8, 4, 4, 4};

    private static final int VMS = 5;
    private static final int VM_PES = 4;

    private static final int CLOUDLETS = 2;
    private static final int CLOUDLET_PES = 1;
    private static final int CLOUDLET_LENGTH = 50_000;

    private static final int HOST_MIPS =       10_000;
    private static final int HOST_RAM =         8_000;
    private static final int HOST_BW =         10_000;
    private static final int HOST_STORAGE = 1_000_000;

    private static final int VM_MIPS =          1_000;
    private static final int VM_RAM =           2_000;
    private static final int VM_BW =            2_000;
    private static final int VM_SIZE =         10_000;
    private static final boolean DISABLE_MIGRATIONS = false;

    private final CloudSimPlus simulation;
    private final DatacenterBroker broker0;
    private List<Vm> vmList;
    private List<Cloudlet> cloudletList;
    private Datacenter datacenter0;

    public static void main(String[] args) {
        new LoggingExample();
    }

    private LoggingExample() {
        simulation = new CloudSimPlus();
        datacenter0 = createDatacenter();

        //Creates a broker that is a software acting on behalf of a cloud customer to manage his/her VMs and Cloudlets
        broker0 = new DatacenterBrokerSimple(simulation);

        vmList = createVms();
        broker0.submitVmList(vmList);

        //Creates Cloudlets that uses 5% of all resources all the time
        cloudletList = createCloudlets(new UtilizationModelDynamic(0.05));

        //Creates Cloudlets that increase resource utilization along the time
        cloudletList.addAll(createCloudlets(createDynamicUtilizationModel()));

        //Creates Cloudlets that increase resource utilization along the time,
        //but are submitted just after some delay.
        cloudletList.addAll(createCloudlets(createDynamicUtilizationModel(), 10));

        Comparator<Cloudlet> hostComparator = Comparator.comparingLong(c -> c.getVm().getHost().getId());
        cloudletList.sort(hostComparator.thenComparingLong(c -> c.getVm().getId()).thenComparingLong(Identifiable::getId));
        broker0.submitCloudletList(cloudletList);

        configureLogs();
        simulation.start();

        final var cloudletFinishedList = broker0.getCloudletFinishedList();
        cloudletFinishedList.sort(Comparator.comparingLong(Cloudlet::getId));
        new CloudletsTableBuilder(cloudletFinishedList).build();
    }

    private void configureLogs() {
        //Enables just some level of log messages for all entities.
        Log.setLevel(Level.INFO);

        //Enable different log levels for specific classes of objects
        Log.setLevel(DatacenterBroker.LOGGER, Level.ERROR);
        Log.setLevel(Datacenter.LOGGER, Level.WARN);
        Log.setLevel(VmAllocationPolicy.LOGGER, Level.OFF);
        Log.setLevel(CloudletScheduler.LOGGER, Level.WARN);
    }

    /**
     * Creates a UtilizationModel that starts the resource utilization at a given percentage
     * and keeps increasing along the time.
     */
    private UtilizationModelDynamic createDynamicUtilizationModel() {
        final var utilizationModel = new UtilizationModelDynamic(0.5);
        utilizationModel.setUtilizationUpdateFunction(model -> model.getUtilization() + model.getTimeSpan()*0.1);
        return utilizationModel;
    }

    /**
     * Creates a Datacenter and its Hosts.
     */
    private Datacenter createDatacenter() {
        final var hostList = new ArrayList<Host>(HOST_PES.length);
        for (int pes : HOST_PES) {
            final var host = createHost(pes);
            hostList.add(host);
        }

        /*Creates a VmAllocationPolicy that migrates VMs from under/overloaded hosts,
        selecting migrating VMs randomly.*/
        final var vmAllocationPolicy =
            new VmAllocationPolicyMigrationStaticThreshold(
                new VmSelectionPolicyRandomSelection(), HOST_OVER_UTILIZATION_MIGRATION_THRESHOLD);
        final var dc = new DatacenterSimple(simulation, hostList, vmAllocationPolicy);
        dc.setSchedulingInterval(SCHEDULING_INTERVAL_SECS);
        if(DISABLE_MIGRATIONS) {
            dc.disableMigrations();
        }
        dc.setHostSearchRetryDelay(60);
        return dc;
    }

    private Host createHost(final int pes) {
        final var peList = new ArrayList<Pe>(pes);
        //List of Host's CPUs (Processing Elements, PEs)
        for (int i = 0; i < pes; i++) {
            //Uses a PeProvisionerSimple by default to provision PEs for VMs
            peList.add(new PeSimple(HOST_MIPS));
        }

        /*
        Uses ResourceProvisionerSimple by default for RAM and BW provisioning
        and VmSchedulerSpaceShared for VM scheduling.
        */
        return new HostSimple(HOST_RAM, HOST_BW, HOST_STORAGE, peList);
    }

    /**
     * Creates a list of VMs.
     */
    private List<Vm> createVms() {
        final var list = new ArrayList<Vm>(VMS);
        for (int i = 0; i < VMS; i++) {
            //Uses a CloudletSchedulerTimeShared by default to schedule Cloudlets
            final Vm vm = new VmSimple(VM_MIPS, VM_PES);
            vm.setRam(VM_RAM).setBw(VM_BW).setSize(VM_SIZE);
            list.add(vm);
        }

        return list;
    }

    /**
     * Creates a list of Cloudlets.
     * @param utilizationModel the UtilizationModel to be used for all Cloudlet resources (CPU, RAM and BW).
     */
    private List<Cloudlet> createCloudlets(final UtilizationModel utilizationModel) {
        return createCloudlets(utilizationModel, 0);
    }

    /**
     * Creates a list of Cloudlets.
     * @param utilizationModel the UtilizationModel to be used for all Cloudlet resources (CPU, RAM and BW).
     * @param submissionDelay the delay to submit Cloudlets to the broker
     */
    private List<Cloudlet> createCloudlets(final UtilizationModel utilizationModel, final double submissionDelay) {
        final var list = new ArrayList<Cloudlet>(CLOUDLETS);

        for (int i = 0; i < CLOUDLETS; i++) {
            final var cloudlet = new CloudletSimple(CLOUDLET_LENGTH, CLOUDLET_PES, utilizationModel);
            cloudlet.setSubmissionDelay(submissionDelay);
            cloudlet.setSizes(1024);
            list.add(cloudlet);
        }

        return list;
    }
}
