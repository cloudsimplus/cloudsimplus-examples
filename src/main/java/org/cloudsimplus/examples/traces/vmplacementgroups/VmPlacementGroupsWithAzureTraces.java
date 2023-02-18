/*
 * CloudSim Plus: A modern, highly-extensible and easier-to-use Framework for
 * Modeling and Simulation of Cloud Computing Infrastructures and Services.
 * http://cloudsimplus.org
 *
 *     Copyright (C) 2023 IBM Research.
 *     Author: Pavlos Maniotis
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
package org.cloudsimplus.examples.traces.vmplacementgroups;

import java.util.List;

import org.cloudbus.cloudsim.allocationpolicies.VmAllocationPolicy;
import org.cloudbus.cloudsim.allocationpolicies.vmplacementgroups.VmAllocationPolicyWithPlacementGroups;
import org.cloudbus.cloudsim.brokers.DatacenterBroker;
import org.cloudbus.cloudsim.brokers.DatacenterBrokerSimple;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.datacenters.Datacenter;
import org.cloudbus.cloudsim.datacenters.DatacenterSimple;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.util.TimeUtil;
import org.cloudbus.cloudsim.vms.Vm;
import org.cloudbus.cloudsim.vms.vmplacementgroup.VmPlacementGroup;
import org.cloudsimplus.traces.azure.TracesParser;
import org.cloudsimplus.traces.azure.TracesSimulationManager;
import org.cloudsimplus.traces.azure.VmRecord;
import org.cloudsimplus.traces.azure.VmTypeRecord;
import org.cloudsimplus.util.Log;

import ch.qos.logback.classic.Level;

/**
 * An example of how to run simulations with {@link VmPlacementGroup} requests and 
 * trace files from the <i>AzureTracesForPacking2020</i> dataset. For more information about
 * the dataset follow the link in [1].
 *  
 * Other datasets can be used provided that they follow the same schema for the 
 * VM types (see {@link VmTypeRecord}) and VM requests (see {@link VmRecord}). 
 * 
 * For details on what is expected as input by the {@link TracesParser} please read 
 * its documentation. {@link TracesSimulationManager} is of significant importance in 
 * trace-based simulations. Check it out to understand how it works.
 * 
 * This example and the corresponding extensions to CloudSim Plus are an updated version of
 * the code developed to support the work in [2], [3], and [4]. The original code was developed
 * on top of CloudSim Plus 6.2.2 and was later ported to 7.3.2. 
 * 
 * <pre>
 * References:
 * 
 *  [1] https://github.com/Azure/AzurePublicDataset/blob/master/AzureTracesForPacking2020.md
 *  
 *  [2] Asser Tantawi, Pavlos Maniotis, Ming-Hung Chen, Claudia Misale, Seetharami Seelam, 
 *      Hao Yu, Laurent Schares, "Chic-sched: a HPC Placement-Group Scheduler on Hierarchical 
 *      Topologies with Constraints," 37th IEEE International Parallel & Distributed Processing 
 *      Symposium (IPDPS 2023), St. Petersburg, Florida, USA, May 15-19, 2023
 *  
 *  [3] P. Maniotis, L. Schares, D. M. Kuchta and B. Karacali, "Improving Data Center Network Locality 
 *      w/ Co-packaged Optics," 2021 European Conference on Optical Communication (ECOC), 2021, pp. 1-4, 
 *      doi: 10.1109/ECOC52684.2021.9606112.
 *      
 *  [4] P. Maniotis, L. Schares, D. M. Kuchta and B. Karacali, "Toward higher-radix switches with 
 *      co-packaged optics for improved network locality in data center and HPC networks [Invited]," 
 *      in Journal of Optical Communications and Networking, vol. 14, no. 6, pp. C1-C10, June 2022, 
 *      doi: 10.1364/JOCN.451449.
 * </pre>
 * 
 * @since CloudSim Plus 7.3.2
 * 
 * @author Pavlos Maniotis
 */
public class VmPlacementGroupsWithAzureTraces {
	
	private TracesSimulationManager simulationManager;
    private CloudSim simulation;
    private DatacenterBroker broker;
    private VmAllocationPolicy vmAllocationPolicy;
    @SuppressWarnings("unused") 
    private Datacenter datacenter; 
    private List<Host> hosts;
	
	private static Level LEVEL = ch.qos.logback.classic.Level.ERROR;

	// static variables with the configuration parameters of the TracesSimulationManager

	/** The working path for the simulations */
	private static String WORKING_PATH         = "Path\\to\\the\\working\\directory\\";
    
	/** The path where the dataset files reside */
	private static String TRACE_PATH           = "Path\\to\\the\\dataset\\files\\";
	
	
	
    /** Number of cores per host */
	private static long HOST_CORES             = 48;
    
	/** Amount of RAM per host */
	private static long HOST_RAM_MiB           = 393_216; // 384 GiB

	/** Amount of bandwidth per host */
	private static long HOST_BW_Mbps           = 100_000;
    
	/** Amount of storage per host */
	private static long HOST_STORAGE_MiB       = 25 * 1024; // 25 GiB
	
	
	
    /** Number of top-of-rack switches in the data center */
	private static long NUM_OF_SWITCHES        = 64;
	
    /** Number of hosts per top-of-rack switch */
	private static long HOSTS_PER_SWITCH       = 96;
	
	
	
    /** Stop simulating after that time  */
	private static long SIM_END_TIME_LIMIT_SEC = 3600 * 24 * 15; // 15 days
	
	/** Simulate VM requests starting from this timestamp  */
	private static long START_TIMES_FROM_SEC   = 0;
	
	/** Simulate VM requests until this timestamp */
	private static long START_TIMES_UNTIL_SEC  = 3600 * 24 * 7;
	
	/** Cap time for the VM lifetimes */
	private static long VM_CAP_SEC             = 3600 * 24 * 90; // 90 days
	

	
	/** Minimum number of hosts per request */
	private static long MIN_HOSTS_PER_REQUEST  = 48; 
	
	/** Minimum number of VMs per request */
	private static long MIN_VMS_PER_REQUEST    = 1;
	
	/** Threshold for VM start and VM end times to consider a set of requests as a group request */
	private static long GROUP_THRESHOLD_SEC    = 1;
	
	/** Batch size to be used for the requests submission to the broker */
	private static long BATCH_SIZE             = 1;
	
	/** Print to file? */
	private static boolean PRINT_TO_FILE       = true;
	
	
	/** Which {@link VmAllocationPolicyWithPlacementGroups} to simulate */
	private static VmAllocationPolicyWithPlacementGroups VM_ALLOCATION_POLICY_TYPE 
		= VmAllocationPolicyWithPlacementGroups.FirstFitWithGroups;
	
	/** Should the simulation finish when the first request allocation failure occurs? */
	private static boolean QUIT_ON_ALLOCATION_FAILURE = false;
	
	/** The minimal period between events to be simulated */
	private static double MIN_TIME_BETWEEN_EVENTS = 30;	
	

    /**
    * This function is called in the {@link #main(String[])} function 
    * after the configuration parameters are set.
    */
    public void run() {
    	    	
    	final double timeZeroTimestamp = TimeUtil.currentTimeSecs();
    	
        Log.setLevel(LEVEL);
    	
    	// Simulations start with the initialization of the TracesSimulationManager
        this.simulationManager = new TracesSimulationManager();
        
        this.simulationManager.setWorkingPath(WORKING_PATH);
        this.simulationManager.setTracePath(TRACE_PATH);
        this.simulationManager.setHostCores(HOST_CORES);
        this.simulationManager.setHostRamMiB(HOST_RAM_MiB);
        this.simulationManager.setHostBwMbps(HOST_BW_Mbps);
        this.simulationManager.setHostStorageMiB(HOST_STORAGE_MiB);
        this.simulationManager.setNumOfSwitches(NUM_OF_SWITCHES);
        this.simulationManager.setHostsPerSwitch(HOSTS_PER_SWITCH);
        this.simulationManager.setSimEndTimeLimitSec(SIM_END_TIME_LIMIT_SEC);
        this.simulationManager.setStartTimesFromSec(START_TIMES_FROM_SEC);
        this.simulationManager.setStartTimesUntilSec(START_TIMES_UNTIL_SEC);
        this.simulationManager.setVmCapSec(VM_CAP_SEC);
        this.simulationManager.setMinHostsPerRequest(MIN_HOSTS_PER_REQUEST);
        this.simulationManager.setMinVmsPerRequest(MIN_VMS_PER_REQUEST);
        this.simulationManager.setGroupThresholdSec(GROUP_THRESHOLD_SEC);
        this.simulationManager.setBatchSize(BATCH_SIZE);
        this.simulationManager.setVmAllocationPolicyType(VM_ALLOCATION_POLICY_TYPE);
        this.simulationManager.setQuitOnAllocationFailure(QUIT_ON_ALLOCATION_FAILURE);
        this.simulationManager.setMinTimeBetweenEvents(MIN_TIME_BETWEEN_EVENTS);
    	this.simulationManager.setPrintToFile(PRINT_TO_FILE);
        
        
        this.simulation = new CloudSim(MIN_TIME_BETWEEN_EVENTS);
        //this.simulation.terminateAt(SIM_END_TIME_LIMIT_SEC);   
        
        this.broker = new DatacenterBrokerSimple(this.simulation);
        
        this.simulationManager.initialize(this.simulation, this.broker);
        
        final double initializationCompletionTimestamp = TimeUtil.currentTimeSecs();
        
        
        
        this.simulationManager.getSimOut().println("\n"+"Start of simulation phase");
        
        this.hosts = this.simulationManager.getHosts();
        
        this.vmAllocationPolicy = this.simulationManager.getVmAllocationPolicy();
                
        this.datacenter = new DatacenterSimple(this.simulation, this.hosts, this.vmAllocationPolicy);
        //this.datacenter.setSchedulingInterval(0);
        
        
		final Vm vm = this.simulationManager.createVm(1, 0, 0, 0, 0);
	
		
		this.broker.submitVm(vm); // wake up the broker before starting the simulation
		                          // FIXME dynamic submissions are ignored without this trick 

				
		this.simulation.start();
        
       	this.simulationManager.saveResults();

       	
       	
       	this.simulationManager.getSimOut().println("\n"+"Simulation phase completed in " + TimeUtil.secondsToStr(TimeUtil.elapsedSeconds(initializationCompletionTimestamp)));
       	this.simulationManager.getSimOut().println("\n"+"Total simulation time : " +	TimeUtil.secondsToStr(TimeUtil.elapsedSeconds(timeZeroTimestamp)));       	       	
       	this.simulationManager.getSimOut().close();
    }
    
    /**
     * The main function, everything starts here.
     */
    public static void main(String[] args) {

    	PRINT_TO_FILE = true;
    	VM_ALLOCATION_POLICY_TYPE = VmAllocationPolicyWithPlacementGroups.FirstFitWithGroups;
		WORKING_PATH = WORKING_PATH + NUM_OF_SWITCHES + "_" + HOSTS_PER_SWITCH + "_" + HOST_BW_Mbps + "_" + VM_ALLOCATION_POLICY_TYPE + "\\";
		new VmPlacementGroupsWithAzureTraces().run();
    	
		
		// Uncomment to run simulations for all placement policies in a loop
		/*final String workingPath = WORKING_PATH;
		
		for (VmAllocationPolicyWithPlacementGroups policy : VmAllocationPolicyWithPlacementGroups.values()) {

			VM_ALLOCATION_POLICY_TYPE = policy;
			
			WORKING_PATH = workingPath + NUM_OF_SWITCHES + "_" + HOSTS_PER_SWITCH + "_" + HOST_BW_Mbps + "_" + VM_ALLOCATION_POLICY_TYPE + "\\";
			
			new VmPlacementGroupsWithAzureTraces().run();
		}*/
    }
}
