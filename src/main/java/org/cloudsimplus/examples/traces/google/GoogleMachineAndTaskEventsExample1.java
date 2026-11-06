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
package org.cloudsimplus.examples.traces.google;

import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;
import org.cloudsimplus.builders.tables.TextTableColumn;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.placementpolicies.VmPlacementPolicySimple;
import org.cloudsimplus.resources.CoreCapacity;
import org.cloudsimplus.schedulers.vm.VmSchedulerTimeShared;
import org.cloudsimplus.traces.google.GoogleMachineEventsTraceReader;
import org.cloudsimplus.traces.google.GoogleTaskEventsTraceReader;
import org.cloudsimplus.traces.google.MachineEvent;
import org.cloudsimplus.traces.google.TaskEvent;
import org.cloudsimplus.traces.google.TaskVmRequest;
import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;

/// An example that creates both Hosts and Cloudlets from
/// [Google Cluster Data](https://github.com/google/cluster-data) trace files:
/// Hosts come from a "machine events" trace and Cloudlets from a "task events" trace.
///
/// The machine ID in the task events defines where each task runs:
/// the tasks of a user in the same machine run inside the same VM, which is placed in the Host with that ID.
/// The trace doesn't define VMs, so they are created by the [#createVm(TaskVmRequest)] method.
/// You can confirm that in the "Host" and "VM" columns of the results.
///
/// The machine IDs in the trace files must match. That's why the machine events
/// sample used here is different from the one in the [GoogleMachineEventsExample1].
/// Tasks without a machine ID in the trace run in any VM of their user.
///
/// @author Manoel Campos da Silva Filho
/// @since CloudSim Plus 9.0.0
public class GoogleMachineAndTaskEventsExample1 {
    private static final String MACHINE_EVENTS_FILE = "workload/google-traces/machine-events-sample-2.csv";
    private static final String TASK_EVENTS_FILE = "workload/google-traces/task-events-sample-1.csv";

    /// The capacity of the largest machine, which the normalized values in the trace files are relative to.
    private static final int MAX_CORES = 8;
    private static final long MAX_RAM = 16384; //in Megabytes
    private static final long HOST_STORAGE = 1000000; //in Megabytes
    private static final long HOST_BW = 10000; //in Megabits/s
    private static final double MIPS = 1000;

    private static final long VM_BW = 100; //in Megabits/s
    private static final long VM_SIZE = 10000; //in Megabytes

    /// Defines a negative length for Cloudlets, so that they run until the trace says they finished.
    private static final int CLOUDLET_LENGTH = -10_000;
    private static final long CLOUDLET_FILE_SIZE = 1024; //in bytes

    private final CloudSimPlus simulation;

    public static void main(String[] args) {
        new GoogleMachineAndTaskEventsExample1();
    }

    private GoogleMachineAndTaskEventsExample1() {
        simulation = new CloudSimPlus();
        final Datacenter datacenter = createDatacenterFromTraceFile();
        final var taskReader = createTaskEventsReader(datacenter);
        taskReader.process();

        simulation.start();

        taskReader.getBrokerManager().getBrokers().stream().sorted().forEach(this::printCloudlets);
    }

    /// Creates a Datacenter with the Hosts defined in the machine events trace.
    /// Hosts added during the simulation would be added to the same Datacenter.
    private Datacenter createDatacenterFromTraceFile() {
        final var datacenter = DatacenterSimple.of(simulation, new VmPlacementPolicySimple());
        final var reader = GoogleMachineEventsTraceReader.getInstance(MACHINE_EVENTS_FILE, this::createHost);
        reader.setMaxCpuCores(MAX_CORES);
        reader.setMaxRamCapacity(MAX_RAM);
        reader.setDatacenterForLaterHosts(datacenter);
        datacenter.addHostList(new ArrayList<>(reader.process()));
        return datacenter;
    }

    /// Creates a reader that runs tasks in the Hosts given by the trace.
    private GoogleTaskEventsTraceReader createTaskEventsReader(final Datacenter datacenter) {
        final var reader =
            GoogleTaskEventsTraceReader
                .getInstance(simulation, TASK_EVENTS_FILE, this::createCloudlet)
                .mapTasksToHosts(this::createVm);

        //Makes the Datacenter place the VMs created by the reader in the Host with their machine ID
        datacenter.getVmPlacementPolicy().setFindHostForVmFunction(reader::findHostForVm);
        return reader;
    }

    private Host createHost(final MachineEvent event) {
        final Host host = HostSimple.of(event.getRam(), HOST_BW, HOST_STORAGE, new CoreCapacity(event.getCpuCores(), MIPS));
        host.setVmScheduler(new VmSchedulerTimeShared());
        return host;
    }

    /// Creates the VM that runs the tasks of a user in a machine.
    /// You can write your own code here to define how the VM is sized from the trace data.
    /// The request provides the peak of resources requested by the tasks running at the same time.
    ///
    /// The VM size doesn't use the peak of local disk space, because requests in the trace
    /// can exceed the machine capacity. In the sample trace, they exceed it in machine 1,
    /// which would make the Host refuse the VM of the second user.
    ///
    /// @param request the data about the tasks of a user in a machine
    /// @return the created VM
    private Vm createVm(final TaskVmRequest request) {
        final long cores = Math.max(1, (long)Math.ceil(request.peakCpuCores() * MAX_CORES));
        final long ram = Math.max(1, (long)Math.ceil(request.peakRam() * MAX_RAM));
        return VmSimple.of(new CoreCapacity(cores, MIPS)).setRam(ram).setBw(VM_BW).setSize(VM_SIZE);
    }

    /// Creates a Cloudlet from a task in the trace.
    /// The RAM [UtilizationModelDynamic] is required by the reader, since the max RAM usage may change in the trace.
    private Cloudlet createCloudlet(final TaskEvent event) {
        final long cores = Math.max(1, event.actualCpuCores(MAX_CORES));
        return CloudletSimple.of(CLOUDLET_LENGTH, cores)
            .setFileSize(CLOUDLET_FILE_SIZE)
            .setOutputSize(CLOUDLET_FILE_SIZE)
            .setUtilizationModelCpu(new UtilizationModelFull())
            .setUtilizationModelBw(new UtilizationModelDynamic(0.25))
            .setUtilizationModelRam(new UtilizationModelDynamic(0, event.getResourceRequestForRam()));
    }

    private void printCloudlets(final DatacenterBroker broker) {
        final Collection<Cloudlet> finished = broker.getCloudletFinishedList();
        final var cloudlets = finished.stream().sorted(Comparator.comparingLong(Cloudlet::getId)).toList();
        new CloudletsTableBuilder(cloudlets)
            .addColumn(new TextTableColumn("Job", "ID"), Cloudlet::getJobId, 0)
            .setTitle("Simulation results for " + broker.getName())
            .build();
    }
}
