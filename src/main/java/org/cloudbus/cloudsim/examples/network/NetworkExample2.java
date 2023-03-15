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
 * A simple example showing how to create 2 datacenters with 1 host.
 * It sets a network topology and then run 2 cloudlets.
 */
public class NetworkExample2 {
    private static final String NETWORK_TOPOLOGY_FILE = "topology.brite";
    private static final int VM_PES = 1;

    private final List<Datacenter> datacenterList;
    private final List<Cloudlet> cloudletList;
    private final List<Vm> vmlist;
    private final CloudSimPlus simulation;
    private final DatacenterBroker broker;

    public static void main(String[] args) {
        new NetworkExample2();
    }

    private NetworkExample2() {
        /*Enables just some level of log messages.
          Make sure to import org.cloudsimplus.util.Log;*/
        //Log.setLevel(ch.qos.logback.classic.Level.WARN);

        System.out.println("Starting " + getClass().getSimpleName());

        vmlist = new ArrayList<>();
        cloudletList = new ArrayList<>();
        datacenterList = new ArrayList<>();
        simulation = new CloudSimPlus();

        for (int i = 0; i < 2; i++) {
            datacenterList.add(createDatacenter());
        }

        broker = new DatacenterBrokerSimple(simulation);
        configureNetwork();

        createAndSubmitVms();
        createAndSubmitCloudlets();

        simulation.start();

        new CloudletsTableBuilder(broker.getCloudletFinishedList()).build();
        System.out.println(getClass().getSimpleName() + " finished!");
    }

    private void configureNetwork() {
        //Configures network by loading the network topology file
        final var networkTopology = BriteNetworkTopology.getInstance(NETWORK_TOPOLOGY_FILE);
        simulation.setNetworkTopology(networkTopology);

        //Maps CloudSimPlus entities to BRITE entities
        //Datacenter0 will correspond to BRITE node 0
        int briteNode = 0;
        networkTopology.mapNode(datacenterList.get(0), briteNode);

        //Datacenter1 will correspond to BRITE node 2
        briteNode = 2;
        networkTopology.mapNode(datacenterList.get(1), briteNode);

        //Broker will correspond to BRITE node 3
        briteNode = 3;
        networkTopology.mapNode(broker, briteNode);
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

        cloudletList.add(cloudlet1);
        cloudletList.add(cloudlet2);

        broker.bindCloudletToVm(cloudletList.get(0), vmlist.get(0));
        broker.bindCloudletToVm(cloudletList.get(0), vmlist.get(1));
        broker.submitCloudletList(cloudletList);
    }

    private void createAndSubmitVms() {
        final int mips = 250;
        final long size = 10000; //image size (Megabyte)
        final int ram = 512; //vm memory (Megabyte)
        final long bw = 1000;

        final Vm vm1 = new VmSimple(mips, VM_PES)
            .setRam(ram).setBw(bw).setSize(size);

        final Vm vm2 = new VmSimple(mips, VM_PES)
            .setRam(ram).setBw(bw).setSize(size);

        vmlist.add(vm1);
        vmlist.add(vm2);

        broker.submitVmList(vmlist);
    }

    private Datacenter createDatacenter() {
        final var hostList = new ArrayList<Host>();
        final var peList = new ArrayList<Pe>();

        final long mips = 1000;
        peList.add(new PeSimple(mips));

        final int ram = 2048; //host memory (Megabyte)
        final long storage = 1000000; //host storage
        final long bw = 10000;

        final Host host = new HostSimple(ram, bw, storage, peList);
        hostList.add(host);

        return new DatacenterSimple(simulation, hostList);
    }
}
