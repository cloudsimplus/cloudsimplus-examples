/*
 * Title:        CloudSim Toolkit
 * Description:  CloudSim (Cloud Simulation) Toolkit for Modeling and Simulation
 *               of Clouds
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2009, The University of Melbourne, Australia
 */
package org.cloudsimplus.examples.network;

import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.datacenters.DatacenterCharacteristicsSimple;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.network.topologies.BriteNetworkTopology;
import org.cloudsimplus.provisioners.ResourceProvisionerSimple;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.schedulers.vm.VmSchedulerTimeShared;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;

import java.util.ArrayList;
import java.util.List;

/**
 * A simple example showing how to create a Datacenter with 1 host and a network
 * topology, running 1 cloudlet on it. Here, instead of using a BRITE file
 * describing the links, they are just inserted in the code.
 *
 * <p>The example defines a given latency for communication with the created broker.
 * Since the broker receives multiple messages (such as for VM and Cloudlet creation),
 * cloudlets start running just after such multiple messages are received.
 * If 3 messages are sent to the broker for starting a Cloudlet,
 * the total delay is the network latency multiplied by 3.</p>
 */
public class NetworkExample4 {
    private static final int VM_PES = 1;

    /** In Megabits/s. */
    private static final double NETWORK_BW = 10.0;

    /** In seconds. */
    private static final double NETWORK_LATENCY = 10.0;

    private final DatacenterBroker broker;
    private final Datacenter datacenter0;

    private final List<Cloudlet> cloudletList;
    private final List<Vm> vmList;
    private final CloudSimPlus simulation;

    public static void main(String[] args) {
        new NetworkExample4();
    }

    private NetworkExample4() {
        /*Enables just some level of log messages.
          Make sure to import org.cloudsimplus.util.Log;*/
        //Log.setLevel(ch.qos.logback.classic.Level.WARN);

        System.out.println("Starting " + getClass().getSimpleName());

        vmList = new ArrayList<>();
        cloudletList = new ArrayList<>();

        simulation = new CloudSimPlus();
        datacenter0 = createDatacenter();
        broker = new DatacenterBrokerSimple(simulation);
        configureNetwork();

        createAndSubmitVms();
        createAndSubmitCloudlets();

        simulation.start();

        new CloudletsTableBuilder(broker.getCloudletFinishedList()).build();
        System.out.println(getClass().getSimpleName() + " finished!");
    }

    private void configureNetwork() {
        //Configure network by mapping CloudSimPlus entities to BRITE entities
        final var networkTopology = new BriteNetworkTopology();
        simulation.setNetworkTopology(networkTopology);
        networkTopology.addLink(datacenter0, broker, NETWORK_BW, NETWORK_LATENCY);
    }

    private void createAndSubmitCloudlets() {
        final long length = 100_000;
        final long fileSize = 1000; // in bytes
        final long outputSize = 1000;  // in bytes
        final var utilizationModel = new UtilizationModelFull();

        final var cloudlet1 =
            new CloudletSimple(length, VM_PES)
                .setFileSize(fileSize)
                .setOutputSize(outputSize)
                .setUtilizationModel(utilizationModel);

        cloudletList.add(cloudlet1);
        broker.submitCloudletList(cloudletList);
    }

    private void createAndSubmitVms() {
        final int mips = 1000;
        final long size = 10000; //image size (Megabyte)
        final int ram = 512; //vm memory (Megabyte)
        final long bw = 1000;

        final Vm vm1 = new VmSimple(mips, VM_PES)
            .setRam(ram).setBw(bw).setSize(size);

        vmList.add(vm1);
        broker.submitVmList(vmList);
    }

    private Datacenter createDatacenter() {
        final var hostList = new ArrayList<Host>();
        final var peList = new ArrayList<Pe>();

        final long mips = 1000;
        peList.add(new PeSimple(mips));

        long ram = 2048; //host memory (Megabyte)
        long storage = 1000000; //host storage (Megabyte)
        long bw = 10000; //Megabits/s

        final Host host = new HostSimple(ram, bw, storage, peList)
            .setRamProvisioner(new ResourceProvisionerSimple())
            .setBwProvisioner(new ResourceProvisionerSimple())
            .setVmScheduler(new VmSchedulerTimeShared());
        hostList.add(host);

        final var dc = new DatacenterSimple(simulation, hostList);
        dc.setCharacteristics(new DatacenterCharacteristicsSimple(3.0, 0.05, 0.1, 0.1));
        return dc;
    }
}
