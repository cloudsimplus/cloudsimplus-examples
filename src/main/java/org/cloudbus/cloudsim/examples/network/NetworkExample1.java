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
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.network.topologies.BriteNetworkTopology;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.resources.SanStorage;
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerTimeShared;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;

import java.util.ArrayList;
import java.util.List;

/**
 * A simple example showing how to create a Datacenter with 1 host and a network
 * topology, running 1 cloudlet on it.
 * There is just one VM with a single PE of 250 MIPS.
 * The Cloudlet requires 1 PE and has a length of 40000 MI.
 * This way, the Cloudlet will take 160 seconds to finish (40000/250).
 *
 * <p>The Cloudlet is not requiring any files from a {@link SanStorage},
 * but since a network topology is defined from the file topology.brite,
 * communication delay between network elements is simulated,
 * causing the Cloudlet to start executing just after a few seconds.</p>
 */
public class NetworkExample1 {
    private static final String NETWORK_TOPOLOGY_FILE = "topology.brite";
    private static final int VM_PES = 1;
    private final Datacenter datacenter0;
    private final DatacenterBroker broker;

    private final List<Cloudlet> cloudletList;
    private final List<Vm> vmList;
    private final CloudSimPlus simulation;

    public static void main(String[] args) {
        new NetworkExample1();
    }

    private NetworkExample1() {
        //Enables just some level of log messages.
        //Make sure to import org.cloudsimplus.util.Log;
        //Log.setLevel(ch.qos.logback.classic.Level.WARN);

        System.out.println("Starting " + getClass().getSimpleName());

        vmList = new ArrayList<>();
        cloudletList = new ArrayList<>();
        simulation = new CloudSimPlus();

        datacenter0 = createDatacenter();
        broker = new DatacenterBrokerSimple(simulation);
        configureNetwork();

        createAndSubmitVms(broker);
        createAndSubmitCloudlets(broker);

        simulation.start();

        final var cloudletFinishedList = broker.getCloudletFinishedList();
        new CloudletsTableBuilder(cloudletFinishedList).build();
        System.out.println(getClass().getSimpleName() + " finished!");
    }

    private void configureNetwork() {
        //load the network topology file
        final var networkTopology = BriteNetworkTopology.getInstance(NETWORK_TOPOLOGY_FILE);
        simulation.setNetworkTopology(networkTopology);

        //maps CloudSimPlus entities to BRITE entities
        //Datacenter will correspond to BRITE node 0
        int briteNode = 0;
        networkTopology.mapNode(datacenter0, briteNode);

        //Broker will correspond to BRITE node 3
        briteNode = 3;
        networkTopology.mapNode(broker, briteNode);
    }

    private void createAndSubmitCloudlets(DatacenterBroker broker) {
        final long length = 40000;
        final long fileSize = 300;
        final long outputSize = 300;
        //The RAM, CPU and Bandwidth UtilizationModel.
        final var utilizationModel = new UtilizationModelFull();

        final var cloudlet1 =
            new CloudletSimple(length, VM_PES)
                .setFileSize(fileSize)
                .setOutputSize(outputSize)
                .setUtilizationModel(utilizationModel);

        //add the cloudlet to the list
        cloudletList.add(cloudlet1);

        //submit cloudlet list to the broker
        broker.submitCloudletList(cloudletList);
    }

    private void createAndSubmitVms(DatacenterBroker broker) {
        final int mips = 250;
        final long size = 10000; //image size (Megabyte)
        final int ram = 512; //vm memory (Megabyte)
        final long bw = 1000; //in Megabits/s
        final Vm vm1 = new VmSimple(mips, VM_PES)
                .setRam(ram).setBw(bw).setSize(size)
                .setCloudletScheduler(new CloudletSchedulerTimeShared());

        vmList.add(vm1);
        broker.submitVmList(vmList);
    }

    private Datacenter createDatacenter() {
        final var hostList = new ArrayList<Host>();
        final var peList = new ArrayList<Pe>();

        final long mips = 1000;
        peList.add(new PeSimple(mips));

        final long ram = 2048; // in Megabytes
        final long storage = 1000000; // in Megabytes
        final long bw = 10000; //in Megabits/s

        final var host = new HostSimple(ram, bw, storage, peList);
        hostList.add(host);

        return new DatacenterSimple(simulation, hostList);
    }
}
