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

import org.cloudbus.cloudsim.brokers.DatacenterBroker;
import org.cloudbus.cloudsim.brokers.DatacenterBrokerSimple;
import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
import org.cloudbus.cloudsim.cloudlets.network.NetworkCloudlet;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.datacenters.Datacenter;
import org.cloudbus.cloudsim.datacenters.DatacenterSimple;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.hosts.HostSimple;
import org.cloudbus.cloudsim.resources.Pe;
import org.cloudbus.cloudsim.resources.PeSimple;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelDynamic;
import org.cloudbus.cloudsim.vms.Vm;
import org.cloudbus.cloudsim.vms.VmSimple;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;
import org.cloudsimplus.builders.tables.MarkdownTableColumn;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A variation of BasicFirstExample.java which runs two kinds of cloudlets
 * ("long" and a "short") and prints the simulations results in several different
 * styles to demonstrate the functionality of CloudletsTableBuilder.
 * 
 * The actual simulation is not the focus in this example, the formatting is.
 * 
 * @author Lennart Demes
 * @author Manoel Campos da Silva Filho
 */
public class CloudletsTableBuilderFormattingExample {
    private static final int  HOSTS = 1;
    private static final int  HOST_PES = 8;
    private static final int  HOST_MIPS = 1000;
    private static final int  HOST_RAM = 2048; //in Megabytes
    private static final long HOST_BW = 10_000; //in Megabits/s
    private static final long HOST_STORAGE = 1_000_000; //in Megabytes

    private static final int VMS = 2;
    private static final int VM_PES = 4;

    private static final int CLOUDLETS = 4;
    private static final int CLOUDLET_PES = 2;
    
    /**
     * Cloudlet lengths are deliberately arbitrary to create more
     * interesting results for custom formatting.
     */
    private static final int CLOUDLET_LENGTH_SHORT = 124_963;
    private static final int CLOUDLET_LENGTH_LONG = 984_174_395;

    private final CloudSim simulation;
    private DatacenterBroker broker0;
    private List<Vm> vmList;
    private List<Cloudlet> cloudletList;
    private Datacenter datacenter0;

    public static void main(String[] args) {
        new CloudletsTableBuilderFormattingExample();
    }

    private CloudletsTableBuilderFormattingExample() {
        /*Enables just some level of log messages.
          Make sure to import org.cloudsimplus.util.Log;*/
        //Log.setLevel(ch.qos.logback.classic.Level.WARN);

        simulation = new CloudSim();
        datacenter0 = createDatacenter();

        //Creates a broker that is a software acting on behalf a cloud customer to manage his/her VMs and Cloudlets
        broker0 = new DatacenterBrokerSimple(simulation);

        vmList = createVms();
        cloudletList = createCloudlets();
        broker0.submitVmList(vmList);
        broker0.submitCloudletList(cloudletList);

        simulation.start();

        final List<Cloudlet> finishedCloudlets = broker0.getCloudletFinishedList();

        demonstrateCloudletTableFormattingOptions(finishedCloudlets);
        
    }
    
    /**
     * Displays a list of cloudlets as a table while demonstrating several formatting/display options.
     * @param finishedCloudlets The list of cloudlets to use for the demonstration.
     */
    private void demonstrateCloudletTableFormattingOptions(List<Cloudlet> finishedCloudlets) {
    	
    	/**
    	 * Prints the results with default settings.
    	 */
    	new CloudletsTableBuilder(finishedCloudlets)
    		.build();
    	
    	/**
    	 * Prints the results with a custom title.
    	 */
    	new CloudletsTableBuilder(finishedCloudlets)
    		.setTitle("This is a custom title")
    		.build();
    	
    	/**
    	 * Prints the results with 3 instead of one decimal point for time columns.
    	 */
    	new CloudletsTableBuilder(finishedCloudlets)
    		.setTimeFormat("%.3f")
    		.setTitle("3 decimal points in time columns")
    		.build();
    	
    	/**
    	 * Prints the results with additional separators in the length columns to increase readability.
    	 */
    	new CloudletsTableBuilder(finishedCloudlets)
    		.setLengthFormat("%,d")
    		.setTitle("Separators in length columns")
    		.build();
    	
    	/**
    	 * Adds an entire additional column for displaying the submission delay of a cloudlet.
    	 */
    	new CloudletsTableBuilder(finishedCloudlets)
    		.setTitle("Additional column for submission delay")
    		.addColumn( new MarkdownTableColumn("SubmissionDelay") , Cloudlet::getSubmissionDelay)
    		.build();
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

        //Uses a VmAllocationPolicySimple by default to allocate VMs
        return new DatacenterSimple(simulation, hostList);
    }

    private Host createHost() {
        final var peList = new ArrayList<Pe>(HOST_PES);
        //List of Host's CPUs (Processing Elements, PEs)
        for (int i = 0; i < HOST_PES; i++) {
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
        final var vmList = new ArrayList<Vm>(VMS);
        for (int i = 0; i < VMS; i++) {
            //Uses a CloudletSchedulerTimeShared by default to schedule Cloudlets
            final var vm = new VmSimple(HOST_MIPS, VM_PES);
            vm.setRam(512).setBw(1000).setSize(10_000);
            vmList.add(vm);
        }

        return vmList;
    }

    /**
     * Creates a list of Cloudlets, toggling between a "long" cloudlet with a high number of MIs
     * and a "short" cloudlet with less MIs. "Short" cloudlets also receive a smaller submission delay.
     */
    private List<Cloudlet> createCloudlets() {
        final var cloudletList = new ArrayList<Cloudlet>(CLOUDLETS);

        //UtilizationModel defining the Cloudlets use only 50% of any resource all the time
        final var utilizationModel = new UtilizationModelDynamic(0.5);

        for (int i = 0; i < CLOUDLETS; i++) {
        	
        	if(i % 2 == 0) {
        		final var cloudlet = new CloudletSimple(CLOUDLET_LENGTH_SHORT, CLOUDLET_PES, utilizationModel);
        		cloudlet.setSizes(1024);
        		cloudlet.setSubmissionDelay(0.7);
                cloudletList.add(cloudlet);
        	}else {
        		final var cloudlet = new CloudletSimple(CLOUDLET_LENGTH_LONG, CLOUDLET_PES, utilizationModel);
        		cloudlet.setSizes(2048);
        		cloudlet.setSubmissionDelay(1.2);
                cloudletList.add(cloudlet);
        	}
        	
            
        }

        return cloudletList;
    }
}
