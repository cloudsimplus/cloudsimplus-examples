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
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;

import java.util.ArrayList;
import java.util.List;

/**
 * A simple example showing how to create 2 datacenters with 1 host each and run
 * cloudlets of 2 users. It also sets a network topology.
 */
public class NetworkExample3 {
    private static final int VM_PES = 1;

    private final List<Datacenter> datacenterList;
    private final List<DatacenterBroker> brokerList;

    private final List<Cloudlet> cloudletList1;
    private final List<Cloudlet> cloudletList2;
    private final List<Vm> vmList1;
    private final List<Vm> vmList2;
    private final CloudSimPlus simulation;

    public static void main(String[] args) {
        new NetworkExample3();
    }

    private NetworkExample3() {
        /*Enables just some level of log messages.
          Make sure to import org.cloudsimplus.util.Log;*/
        //Log.setLevel(ch.qos.logback.classic.Level.WARN);

        System.out.println("Starting " + getClass().getSimpleName());

        datacenterList = new ArrayList<>();
        brokerList = new ArrayList<>();
        vmList1 = new ArrayList<>();
        vmList2 = new ArrayList<>();

        simulation = new CloudSimPlus();

        for (int i = 0; i < 2; i++) {
            datacenterList.add(createDatacenter());
        }

        for (int i = 0; i < 2; i++) {
            brokerList.add(new DatacenterBrokerSimple(simulation));
        }

        createNetwork();
        createAndSubmitVms();

        cloudletList1 = new ArrayList<>();
        cloudletList2 = new ArrayList<>();
        createAndSubmitCloudlets();

        simulation.start();

        for (var broker : brokerList) {
            printFinishedCloudletList(broker);
        }
        System.out.println(getClass().getSimpleName() + " finished!");
    }

    private void printFinishedCloudletList(DatacenterBroker broker) {
        new CloudletsTableBuilder(broker.getCloudletFinishedList())
                .setTitle("Broker " + broker)
                .build();
    }

    private void createAndSubmitCloudlets() {
        final long length = 40000;
        final long fileSize = 300;
        final long outputSize = 300;
        final var utilizationModel = new UtilizationModelFull();

        final var cloudlet1 =
            new CloudletSimple(length, VM_PES)
                .setFileSize(fileSize)
                .setOutputSize(outputSize)
                .setUtilizationModel(utilizationModel);

        final var cloudlet2 =
            new CloudletSimple(length, VM_PES)
                .setFileSize(fileSize)
                .setOutputSize(outputSize)
                .setUtilizationModel(utilizationModel);

        cloudletList1.add(cloudlet1);
        cloudletList2.add(cloudlet2);

        brokerList.get(0).submitCloudletList(cloudletList1);
        brokerList.get(1).submitCloudletList(cloudletList2);
    }

    private void createAndSubmitVms() {
        final long size = 10000; //image size (Megabyte)
        final int mips = 250;
        final int ram = 512; //vm memory (Megabyte)
        final long bw = 1000;

        final Vm vm1 = new VmSimple(mips, VM_PES)
                .setRam(ram).setBw(bw).setSize(size);

        final Vm vm2 = new VmSimple(mips, VM_PES)
                .setRam(ram).setBw(bw).setSize(size);

        vmList1.add(vm1);
        vmList2.add(vm2);

        brokerList.get(0).submitVmList(vmList1);
        brokerList.get(1).submitVmList(vmList2);
    }

    /**
     * Creates the network topology from a brite file.
     */
    private void createNetwork() {
        //load the network topology file
        final var networkTopology = BriteNetworkTopology.getInstance("topology.brite");
        simulation.setNetworkTopology(networkTopology);

        //Maps CloudSimPlus entities to BRITE entities
        //Datacenter0 will correspond to BRITE node 0
        int briteNode = 0;
        networkTopology.mapNode(datacenterList.get(0), briteNode);

        //Datacenter1 will correspond to BRITE node 2
        briteNode = 2;
        networkTopology.mapNode(datacenterList.get(1), briteNode);

        //Broker1 will correspond to BRITE node 3
        briteNode = 3;
        networkTopology.mapNode(brokerList.get(0), briteNode);

        //Broker2 will correspond to BRITE node 4
        briteNode = 4;
        networkTopology.mapNode(brokerList.get(1), briteNode);
    }

    private Datacenter createDatacenter() {
        final var hostList = new ArrayList<Host>();
        final var peList = new ArrayList<Pe>();

        final long mips = 1000;
        peList.add(new PeSimple(mips));

        final long ram = 2048; // in Megabytes
        final long storage = 1000000; // in Megabytes
        final long bw = 10000; //in Megabits/s

        final Host host = new HostSimple(ram, bw, storage, peList);
        hostList.add(host);

        return new DatacenterSimple(simulation, hostList);
    }
}
