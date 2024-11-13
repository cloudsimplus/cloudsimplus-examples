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
import org.cloudsimplus.network.switches.AggregateSwitch;
import org.cloudsimplus.network.switches.EdgeSwitch;
import org.cloudsimplus.network.switches.RootSwitch;
import org.cloudsimplus.provisioners.ResourceProvisionerSimple;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerTimeShared;
import org.cloudsimplus.schedulers.vm.VmSchedulerTimeShared;
import org.cloudsimplus.vms.network.NetworkVm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A base class for network simulation examples
 * using objects such as{@link NetworkDatacenter},
 * {@link NetworkHost}, {@link NetworkVm} and {@link NetworkCloudlet}.
 *
 * <p>The class simulate applications that are compounded by a list of
 * {@link NetworkCloudlet}.</p>
 *
 * @author Saurabh Kumar Garg
 * @author Rajkumar Buyya
 * @author Manoel Campos da Silva Filho
 */
abstract class NetworkVmExampleAbstract {
    public static final int MAX_VMS_PER_HOST = 2;

    public static final double COST_PER_CPU_SEC = 3.0; // the cost of using processing in this resource
    public static final double COST_PER_MEM = 0.05; // the cost of using memory in this resource
    public static final double COST_PER_STORAGE = 0.001; // the cost of using storage in this resource
    public static final double COST_PER_BW = 0.0; // the cost of using bw in this resource

    public static final int  HOST_MIPS = 1000;
    public static final int  HOST_PES = 8;
    public static final int  HOST_RAM = 2048; // MEGA
    public static final long HOST_STORAGE = 1000000; // MEGA
    public static final long HOST_BW = 10000;

    public static final int  VM_MIPS = 1000;
    public static final long VM_SIZE = 10000; // image size (Megabyte)
    public static final int  VM_RAM = 512; // MEGA
    public static final long VM_BW = 1000;
    public static final int  VM_PES_NUMBER = HOST_PES / MAX_VMS_PER_HOST;

    /**
     * Number of fictitious applications to create.
     * Each application is just a list of {@link  NetworkCloudlet}.
     * @see #appMap
     */
    public static final int APPS_NUMBER = 1;

    public static final int CLOUDLET_PES = VM_PES_NUMBER;
    public static final int TASK_LENGTH = 4000;
    public static final int CLOUDLET_FILE_SIZE = 300;
    public static final int CLOUDLET_OUTPUT_SIZE = 300;
    public static final long TASK_RAM = 100; // in Megabytes
    private static final long PACKET_DATA_LENGTH_IN_BYTES = 200_000;
    private static final long PACKETS_TO_SEND = 100;
    private static final int SCHEDULING_INTERVAL = 5;

    private final CloudSimPlus simulation;

    private final List<NetworkVm> vmList;
    private final NetworkDatacenter datacenter;
    private final List<DatacenterBroker> brokerList;

    /**
     * A Map representing a list of cloudlets from different applications.
     * Each key represents the ID of an app that is composed by a list
     * of {@link  NetworkCloudlet}.
     */
    private final Map<Integer, List<NetworkCloudlet>> appMap;

    /**
     * Creates, starts, stops the simulation and shows results.
     */
    NetworkVmExampleAbstract() {
        /*Enables just some level of log messages.
          Make sure to import org.cloudsimplus.util.Log;*/
        //Log.setLevel(ch.qos.logback.classic.Level.WARN);

        System.out.println("Starting " + getClass().getSimpleName());
        simulation = new CloudSimPlus();

        this.datacenter = createDatacenter();
        this.brokerList = createBrokerForEachApp();
        this.vmList = new ArrayList<>();
        this.appMap = new HashMap<>();

        int appId = -1;
        for(DatacenterBroker broker: this.brokerList){
            this.vmList.addAll(createAndSubmitVMs(broker));
            this.appMap.put(++appId, createAppAndSubmitToBroker(broker));
        }

        simulation.start();
        showSimulationResults();
    }

    /**
     * Adds an execution task to the list of tasks of the given {@link NetworkCloudlet}.
     *
     * @param cloudlet the {@link NetworkCloudlet} the task will belong to
     */
    protected static void addExecutionTask(NetworkCloudlet cloudlet) {
        final var task = new CloudletExecutionTask(cloudlet.getTasks().size(), TASK_LENGTH);
        task.setMemory(TASK_RAM);
        cloudlet.addTask(task);
    }

    private void showSimulationResults() {
        for(int i = 0; i < APPS_NUMBER; i++){
            final DatacenterBroker broker = brokerList.get(i);
            final var newCloudletList = broker.getCloudletFinishedList();
            new CloudletsTableBuilder(newCloudletList)
                    .setTitle("%s (broker %d)".formatted(broker.getName(), broker.getId()))
                    .build();
            System.out.printf(
                "Number of NetworkCloudlets for %s (broker %d): %d%n",
                broker.getName(), broker.getId(), newCloudletList.size());
        }

        System.out.println();
        for(NetworkHost host: datacenter.getHostList()){
            System.out.printf("Host %d data transferred: %d bytes%n",
                    host.getId(), host.getTotalDataTransferBytes());
        }

        System.out.printf("%n%s finished!%n", getClass().getSimpleName());
    }

    /**
     * Create a {@link DatacenterBroker} for each list of {@link NetworkCloudlet},
     * representing cloudlets that compose the same application.
     *
     * @return the list of created NetworkDatacenterBroker
     */
    private  List<DatacenterBroker> createBrokerForEachApp() {
        final var brokerList = new ArrayList<DatacenterBroker>();
        for(int i = 1; i <= APPS_NUMBER; i++){
            brokerList.add(new DatacenterBrokerSimple(simulation, "App "+i));
        }

        return brokerList;
    }

    /**
     * Creates the Datacenter.
     *
     * @return the Datacenter
     */
    protected final NetworkDatacenter createDatacenter() {
        final int hostsNumber = EdgeSwitch.PORTS * AggregateSwitch.PORTS * RootSwitch.PORTS;
        final var netHostList = new ArrayList<NetworkHost>(hostsNumber);
        for (int i = 0; i < hostsNumber; i++) {
            final List<Pe> peList = createPEs(HOST_PES, HOST_MIPS);
            final var host = new NetworkHost(HOST_RAM, HOST_BW, HOST_STORAGE, peList);
            host
                .setRamProvisioner(new ResourceProvisionerSimple())
                .setBwProvisioner(new ResourceProvisionerSimple())
                .setVmScheduler(new VmSchedulerTimeShared());
            netHostList.add(host);
        }

        final var dc = new NetworkDatacenter(simulation, netHostList);
        dc.setSchedulingInterval(SCHEDULING_INTERVAL);
        dc.getCharacteristics()
            .setCostPerSecond(COST_PER_CPU_SEC)
            .setCostPerMem(COST_PER_MEM)
            .setCostPerStorage(COST_PER_STORAGE)
            .setCostPerBw(COST_PER_BW);

        createNetwork(dc);
        return dc;
    }

    protected List<Pe> createPEs(final int pesNumber, final long mips) {
        final var peList = new ArrayList<Pe>();
        for (int i = 0; i < pesNumber; i++) {
            peList.add(new PeSimple(mips));
        }

        return peList;
    }

    /**
     * Creates internal Datacenter network.
     * @param datacenter Datacenter where the network will be created
     */
    protected void createNetwork(final NetworkDatacenter datacenter) {
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

    /**
     * Gets the index of a switch where a Host will be connected,
     * considering the number of ports the switches have.
     * Ensures that each set of N Hosts is connected to the same switch
     * (where N is defined as the number of switch's ports).
     * Since the host ID is long but the switch array index is int,
     * the module operation is used safely convert a long to int
     * For instance, if the id is 2147483648 (higher than the max int value 2147483647),
     * it will be returned 0. For 2147483649 it will be 1 and so on.
     *
     * @param host the Host to get the index of the switch to connect to
     * @param switchPorts the number of ports (N) the switches where the Host will be connected have
     * @return the index of the switch to connect the host
     */
    public static int getSwitchIndex(final NetworkHost host, final int switchPorts) {
        return Math.round(host.getId() % Integer.MAX_VALUE) / switchPorts;
    }

    /**
     * Creates a list of virtual machines in a Datacenter for a given broker
     * and submit the list to the broker.
     *
     * @param broker The broker that will own the created VMs
     * @return the list of created VMs
     */
    protected final List<NetworkVm> createAndSubmitVMs(DatacenterBroker broker) {
        final int vmsNumber = getDatacenterHostList().size() * MAX_VMS_PER_HOST;
        final var netVmList = new ArrayList<NetworkVm>();
        for (int i = 0; i < vmsNumber; i++) {
            final var vm = new NetworkVm(i, VM_MIPS, VM_PES_NUMBER);
            vm.setRam(VM_RAM)
              .setBw(VM_BW)
              .setSize(VM_SIZE)
              .setCloudletScheduler(new CloudletSchedulerTimeShared());
            netVmList.add(vm);
        }

        broker.submitVmList(netVmList);
        return netVmList;
    }

    private List<NetworkHost> getDatacenterHostList() {
        return datacenter.getHostList();
    }

    /**
     * @return List of VMs of all Brokers.
     */
    public List<NetworkVm> getVmList() {
        return vmList;
    }

    public NetworkVm getVm(final int i) {
        return vmList.get(i);
    }

    public NetworkDatacenter getDatacenter() {
        return datacenter;
    }

    /**
     * Creates a list of {@link NetworkCloudlet}'s that represents
     * a single application and then submits the created cloudlets to a given Broker.
     *
     * @param broker the broker to submit the list of NetworkCloudlets of the application
     * @return the list of created  {@link NetworkCloudlet}'s
     */
    protected final List<NetworkCloudlet> createAppAndSubmitToBroker(DatacenterBroker broker) {
        final var netCloudletList = createNetworkCloudlets(broker);
        broker.submitCloudletList(netCloudletList);
        return netCloudletList;
    }

    /**
     * Creates a list of {@link NetworkCloudlet} that together represents the distributed
     * processes of a given application.
     *
     * @param broker broker to associate the NetworkCloudlets
     * @return the list of create NetworkCloudlets
     */
    protected abstract List<NetworkCloudlet> createNetworkCloudlets(DatacenterBroker broker);

    /**
     * Adds a SendTask to the list of tasks of the given {@link NetworkCloudlet}.
     *
     * @param sourceCloudlet the {@link NetworkCloudlet} from which packets will be sent
     * @param targetCloudlet the destination {@link NetworkCloudlet} to send packets to
     */
    protected void addSendTask(
        final NetworkCloudlet sourceCloudlet,
        final NetworkCloudlet targetCloudlet)
    {
        final var task = new CloudletSendTask(sourceCloudlet.getTasks().size());
        task.setMemory(TASK_RAM);
        sourceCloudlet.addTask(task);
        for(int i = 0; i < PACKETS_TO_SEND; i++) {
            task.addPacket(targetCloudlet, PACKET_DATA_LENGTH_IN_BYTES);
        }
    }

    /**
     * Adds a ReceiveTask to the list of tasks of the given {@link NetworkCloudlet}.
     *
     * @param targetCloudlet the {@link NetworkCloudlet} the task will belong to
     * @param sourceCloudlet the {@link NetworkCloudlet} expected to receive packets from
     */
    protected void addReceiveTask(final NetworkCloudlet targetCloudlet, final NetworkCloudlet sourceCloudlet) {
        final var task = new CloudletReceiveTask(targetCloudlet.getTasks().size(), sourceCloudlet.getVm());
        task.setMemory(TASK_RAM);
        task.setExpectedPacketsToReceive(PACKETS_TO_SEND);
        targetCloudlet.addTask(task);
    }
}
