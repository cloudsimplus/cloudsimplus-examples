package org.cloudbus.cloudsim.examples.network.applications;

import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;
import org.cloudsimplus.cloudlets.network.CloudletExecutionTask;
import org.cloudsimplus.cloudlets.network.CloudletReceiveTask;
import org.cloudsimplus.cloudlets.network.CloudletSendTask;
import org.cloudsimplus.cloudlets.network.NetworkCloudlet;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.network.NetworkDatacenter;
import org.cloudsimplus.hosts.network.NetworkHost;
import org.cloudsimplus.network.switches.EdgeSwitch;
import org.cloudsimplus.provisioners.ResourceProvisionerSimple;
import org.cloudsimplus.resources.CoreCapacity;
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerTimeShared;
import org.cloudsimplus.schedulers.vm.VmSchedulerTimeShared;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.network.NetworkVm;

import java.util.ArrayList;
import java.util.List;

import static org.cloudbus.cloudsim.examples.network.applications.NetworkVmExampleAbstract.getSwitchIndex;

/// A simple example simulating a distributed application.
/// It shows how 2 [NetworkCloudlet]'s communicate,
/// each one running inside VMs on different hosts.
///
/// @author Manoel Campos da Silva Filho
public class NetworkVmsExampleSimpleApp {
    private static final int HOSTS = 2;
    private static final int HOST_MIPS = 1000;
    private static final int HOST_CORES = 4;

    /// The CPU capacity of every VM: a single [CoreCapacity] object is reused for all of them.
    /// That's safe because a [CoreCapacity] is immutable: it never changes after it's created,
    /// and each VM copies its values into its own Processor.
    /// So, changing the capacity of one VM (such as by vertical scaling) doesn't affect the other VMs.
    /// Reusing it avoids creating one temporary object per VM (fewer allocations and less GC work in large simulations).
    private static final CoreCapacity VM_CAPACITY = new CoreCapacity(HOST_CORES, HOST_MIPS);

    /// The CPU capacity of every Host: a single [CoreCapacity] object is reused for all of them.
    /// That's safe because a [CoreCapacity] is immutable: it never changes after it's created,
    /// and each Host copies its values into its own Processor.
    /// So, changing the capacity of one Host's Processor doesn't affect the other Hosts.
    /// Reusing it avoids creating one temporary object per Host (fewer allocations and less GC work in large simulations).
    private static final CoreCapacity HOST_CAPACITY = VM_CAPACITY;

    private static final int HOST_RAM = 2048; // host memory (Megabyte)
    private static final long HOST_STORAGE = 1000000; // host storage
    private static final long HOST_BW = 10000;

    private static final int TASK_LENGTH = 4000;
    private static final int CLOUDLET_FILE_SIZE = 300;
    private static final int CLOUDLET_OUTPUT_SIZE = 300;
    private static final long PACKET_DATA_LENGTH_IN_BYTES = 1000;
    private static final int NUMBER_OF_PACKETS_TO_SEND = 1;
    private static final long TASK_RAM = 100; // in Megabytes

    private final CloudSimPlus simulation;

    private final List<NetworkVm> vmList;
    private final List<NetworkCloudlet> cloudletList;
    private final NetworkDatacenter datacenter;
    private final DatacenterBroker broker;

    /// Starts the execution of the example.
    /// @param args
    public static void main(String[] args) {
        new NetworkVmsExampleSimpleApp();
    }

    /// Creates, starts, stops the simulation and shows results.
    private NetworkVmsExampleSimpleApp() {
        System.out.println("Starting " + getClass().getSimpleName());
        simulation = new CloudSimPlus();

        datacenter = createDatacenter();
        broker = new DatacenterBrokerSimple(simulation);
        vmList = createAndSubmitVMs(broker);
        cloudletList = createNetworkCloudlets();
        broker.submitCloudletList(cloudletList);

        simulation.start();
        showSimulationResults();
    }

    private void showSimulationResults() {
        final var cloudletFinishedList = broker.getCloudletFinishedList();
        new CloudletsTableBuilder(cloudletFinishedList).build();

        System.out.println();
        for (NetworkHost host : datacenter.getHostList()) {
            System.out.printf("Host %d data transferred: %d bytes%n",
                    host.getId(), host.getTotalDataTransferBytes());
        }

        System.out.println(getClass().getSimpleName() + " finished!");
    }

    private NetworkDatacenter createDatacenter() {
        final var netHostList = new ArrayList<NetworkHost>();
        for (int i = 0; i < HOSTS; i++) {
            final NetworkHost host = createHost();
            netHostList.add(host);
        }

        final var dc = new NetworkDatacenter(simulation, netHostList);
        dc.setSchedulingInterval(5);

        createNetwork(dc);
        return dc;
    }

    private NetworkHost createHost() {
        final var host = new NetworkHost(HOST_RAM, HOST_BW, HOST_STORAGE, HOST_CAPACITY);
        host
            .setRamProvisioner(new ResourceProvisionerSimple())
            .setBwProvisioner(new ResourceProvisionerSimple())
            .setVmScheduler(new VmSchedulerTimeShared());

        return host;
    }

    /// Creates internal Datacenter network.
    ///
    /// @param datacenter Datacenter where the network will be created
    private void createNetwork(final NetworkDatacenter datacenter) {
        final var edgeSwitches = new EdgeSwitch[1];
        for (int i = 0; i < edgeSwitches.length; i++) {
            edgeSwitches[i] = new EdgeSwitch(simulation, datacenter);
            datacenter.addSwitch(edgeSwitches[i]);
        }

        for (NetworkHost host : datacenter.getHostList()) {
            final int switchNum = getSwitchIndex(host, edgeSwitches[0].getPorts());
            edgeSwitches[switchNum].connectHost(host);
        }
    }

    /// Creates a list of virtual machines in a Datacenter for a given broker and
    /// submit the list to the broker.
    ///
    /// @param broker The broker that will own the created VMs
    /// @return the list of created VMs
    private List<NetworkVm> createAndSubmitVMs(DatacenterBroker broker) {
        final var netVmList = new ArrayList<NetworkVm>();
        for (int i = 0; i < HOSTS; i++) {
            final NetworkVm vm = createVm(i);
            netVmList.add(vm);
        }

        broker.submitVmList(netVmList);
        return netVmList;
    }

    private NetworkVm createVm(int id) {
        final var vm = new NetworkVm(id, HOST_CAPACITY);
        vm
                .setRam(HOST_RAM)
                .setBw(HOST_BW)
                .setSize(HOST_STORAGE)
                .setCloudletScheduler(new CloudletSchedulerTimeShared());
        return vm;
    }

    /// Creates a list of [NetworkCloudlet] that together represents the
    /// distributed processes of a given fictitious application.
    ///
    /// @return the list of create NetworkCloudlets
    private List<NetworkCloudlet> createNetworkCloudlets() {
        final int cloudletsNumber = 2;
        final var netCloudletList = new ArrayList<NetworkCloudlet>(cloudletsNumber);

        for (int i = 0; i < cloudletsNumber; i++) {
            netCloudletList.add(createNetworkCloudlet(vmList.get(i)));
        }

        //NetworkCloudlet 0 Tasks
        addExecutionTask(netCloudletList.get(0));
        addSendTask(netCloudletList.get(0), netCloudletList.get(1));

        //NetworkCloudlet 1 Tasks
        addReceiveTask(netCloudletList.get(1), netCloudletList.get(0));
        addExecutionTask(netCloudletList.get(1));

        return netCloudletList;
    }

    /// Creates a [NetworkCloudlet].
    ///
    /// @param vm the VM that will run the created [NetworkCloudlet]
    /// @return
    private NetworkCloudlet createNetworkCloudlet(NetworkVm vm) {
        final var netCloudlet = new NetworkCloudlet(HOST_CORES);
        netCloudlet
                .setFileSize(CLOUDLET_FILE_SIZE)
                .setOutputSize(CLOUDLET_OUTPUT_SIZE)
                .setUtilizationModel(new UtilizationModelFull())
                .setVm(vm)
                .setBroker(vm.getBroker());

        return netCloudlet;
    }

    /// Adds an execution-task to the list of tasks of the given
    /// [NetworkCloudlet].
    ///
    /// @param cloudlet the [NetworkCloudlet] the task will belong to
    private static void addExecutionTask(NetworkCloudlet cloudlet) {
        final var task = new CloudletExecutionTask(cloudlet.getTasks().size(), TASK_LENGTH);
        task.setMemory(TASK_RAM);
        cloudlet.addTask(task);
    }

    /// Adds a send-task to the list of tasks of the given [NetworkCloudlet].
    ///
    /// @param sourceCloudlet the [NetworkCloudlet] from which packets will be sent
    /// @param destinationCloudlet the destination [NetworkCloudlet] to send packets to
    private void addSendTask(final NetworkCloudlet sourceCloudlet, final NetworkCloudlet destinationCloudlet) {
        final var task = new CloudletSendTask(sourceCloudlet.getTasks().size());
        task.setMemory(TASK_RAM);
        sourceCloudlet.addTask(task);
        for (int i = 0; i < NUMBER_OF_PACKETS_TO_SEND; i++) {
            task.addPacket(destinationCloudlet, PACKET_DATA_LENGTH_IN_BYTES);
        }
    }

    /// Adds a receive-task to the list of tasks of the given
    /// [NetworkCloudlet].
    ///
    /// @param cloudlet the [NetworkCloudlet] the task will belong to
    /// @param sourceCloudlet the [NetworkCloudlet] expected to receive packets from
    private void addReceiveTask(final NetworkCloudlet cloudlet, final NetworkCloudlet sourceCloudlet) {
        final var task = new CloudletReceiveTask(cloudlet.getTasks().size(), sourceCloudlet.getVm());
        task.setMemory(TASK_RAM);
        task.setExpectedPacketsToReceive(NUMBER_OF_PACKETS_TO_SEND);
        cloudlet.addTask(task);
    }
}
