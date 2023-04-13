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
package org.cloudsimplus.examples.traces;

import org.cloudsimplus.allocationpolicies.VmAllocationPolicyFirstFit;
import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.listeners.DatacenterBrokerEventInfo;
import org.cloudsimplus.listeners.EventListener;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerSpaceShared;
import org.cloudsimplus.traces.SwfWorkloadFileReader;
import org.cloudsimplus.traces.TraceReaderAbstract;
import org.cloudsimplus.util.Log;
import org.cloudsimplus.util.TimeUtil;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * An example showing how to dynamically create cloudlets from a workload trace
 * file in the Standard Workload Format (.swf file) defined by the
 * <a href="http://www.cs.huji.ac.il/labs/parallel/workload/">Hebrew University
 * of Jerusalem</a>. This example uses the workload file
 * "<i>NASA-iPSC-1993-3.1-cln.swf.gz</i>", which was downloaded from the given
 * web page and is located at the resources folder of this project.
 * The workload file has 18239 jobs that will be created as Cloudlets.
 *
 * <p>Considering the large number of cloudlets that can have a workload file,
 * that can cause the simulation to consume a lot of resources
 * at the developer machine and can <b>spend a long time to finish</b>,
 * the example allow to limit the maximum number of cloudlets to be submitted
 * to the DatacenterBroker.
 * See the {@link #maxCloudletsToCreateFromWorkloadFile} attribute for more details.
 * </p>
 *
 * <p>
 * The workload file is compressed in <i>gz</i> format and the
 * <i>swf</i> file inside it is just a text file that can be opened in any text
 * editor. For more information about the workload format, check
 * <a href="http://www.cs.huji.ac.il/labs/parallel/workload/swf.html">this
 * page</a>.
 * </p>
 *
 * <p>Check important details at {@link SwfWorkloadFileReader}
 * and {@link TraceReaderAbstract}.</p>
 *
 * @author Manoel Campos da Silva Filho
 * @since CloudSim Plus 1.0.0
 */
public class SwfWorkloadFormatExample1 {
    /**
     * The workload file to be read.
     */
    private static final String WORKLOAD_FILENAME = "workload/swf/NASA-iPSC-1993-3.1-cln.swf.gz";

    private final CloudSimPlus simulation;

    /**
     * Defines the maximum number of cloudlets to be created
     * from the given workload file.
     * {@link Integer#MAX_VALUE} indicates that every job inside the workload file
     * will be created as one cloudlet.
     */
    private int maxCloudletsToCreateFromWorkloadFile = Integer.MAX_VALUE;

    private final int HOST_PES = 12;

    private static final int  VM_MIPS = 10000;
    private static final int  VM_PES = 4;
    private static final long VM_SIZE = 2000;
    private static final int  VM_RAM = 1000;
    private static final long VM_BW = 50000;

    private List<Cloudlet> cloudletList;
    private List<Vm> vmlist;
    private Datacenter datacenter0;
    private DatacenterBroker broker;

    /**
     * Creates main() to run this example.
     *
     * @param args the args
     */
    public static void main(String[] args) {
        new SwfWorkloadFormatExample1();
    }

    private SwfWorkloadFormatExample1() {
        Log.setLevel(ch.qos.logback.classic.Level.WARN);

        final int waitSecs = 5;
        System.out.printf(
            "Starting %s in %d seconds. Since it reads a workload file, it can take some minutes to finish...%n",
            getClass().getSimpleName(), waitSecs);
        sleep(waitSecs);
        final double startSecs = TimeUtil.currentTimeSecs();
        System.out.printf("Simulation started at %s%n%n", LocalTime.now());

        simulation = new CloudSimPlus();
        try {
            broker = new DatacenterBrokerSimple(simulation);

            /*Vms and cloudlets are created before the Datacenter and host
            because the example is creating: (i) hosts based on VM requirements;
            (ii) VMs based on cloudlet requirements.*/
            createCloudletsFromWorkloadFile();
            createVms();

            datacenter0 = createDatacenter();

            broker.submitVmList(vmlist);
            broker.submitCloudletList(cloudletList);
            broker.addOnVmsCreatedListener(this::onVmsCreated);

            simulation.start();

            final var cloudletFinishedList = broker.getCloudletFinishedList();
            new CloudletsTableBuilder(cloudletFinishedList).build();

            System.out.printf("Simulation finished at %s. Execution time: %.2f seconds%n", LocalTime.now(), TimeUtil.elapsedSeconds(startSecs));
        } catch (Exception e) {
            System.out.printf("Error during simulation execution: %s%n", e.getMessage());
        }
    }

    /**
     * Method executed when all VMs submitted to the broker are placed
     * into some Host.
     * @param info
     * @see DatacenterBroker#addOnVmsCreatedListener(EventListener)
     */
    private void onVmsCreated(DatacenterBrokerEventInfo info) {
        System.out.printf("%d VMs from Broker %d placed into some Host%n", vmlist.size(), info.getDatacenterBroker().getId());
    }

    private void sleep(final long seconds) {
        try {
            Thread.sleep(seconds * 1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Creates a list of VMs according to the number of Cloudlets,
     * in order to try accommodating all Cloudlets into those VMs.
     */
    private void createVms() {
        final double totalCloudletPes = cloudletList.stream().mapToDouble(Cloudlet::getPesNumber).sum();
        /* The number to multiply the VM_PES was chosen at random.
        * It's used to reduce the number of VMs to create. */
        final int totalVms = (int)Math.ceil(totalCloudletPes / (VM_PES*6));

        vmlist = new ArrayList<>();
        for (int i = 0; i < totalVms; i++) {
            final var vm = new VmSimple(VM_MIPS, VM_PES)
                            .setRam(VM_RAM).setBw(VM_BW).setSize(VM_SIZE)
                            .setCloudletScheduler(new CloudletSchedulerSpaceShared());
            vmlist.add(vm);
        }

        System.out.printf("# Created %12d VMs for the %s%n", vmlist.size(), broker);
    }

    private void createCloudletsFromWorkloadFile() {
        final var reader = SwfWorkloadFileReader.getInstance(WORKLOAD_FILENAME, VM_MIPS);
        reader.setMaxLinesToRead(maxCloudletsToCreateFromWorkloadFile);
        this.cloudletList = reader.generateWorkload();

        System.out.printf("# Created %12d Cloudlets for %s%n", this.cloudletList.size(), broker);
    }

    /**
     * Creates the Datacenter with a number of Hosts according to the number of created VMs,
     * in order to try accommodating all VMs into those Hosts.
     *
     * @return the created Datacenter
     */
    private Datacenter createDatacenter() {
        final var hostList = createHosts(vmlist.size()/2);
        var datacenter = new DatacenterSimple(simulation, hostList, new VmAllocationPolicyFirstFit());

        System.out.printf("# Added   %12d Hosts to %s%n", hostList.size(), datacenter);
        return datacenter;
    }

    /**
     * Creates a specific number of PM's with the same capacity.
     *
     * @param hostsNumber number of hosts to create
     * @return the created host
     */
    private List<Host> createHosts(final long hostsNumber) {
        final long ram = VM_RAM * 100;
        final long storage = VM_SIZE * 1000;
        final long bw = VM_BW * 1000;

        final var hostList = new ArrayList<Host>((int)hostsNumber);
        for (int i = 0; i < hostsNumber; i++) {
            List<Pe> peList = createPeList(VM_MIPS);
            final var host = new HostSimple(ram, bw, storage, peList);
            hostList.add(host);
        }

        return hostList;
    }

    private List<Pe> createPeList(final long mips) {
        final var peList = new ArrayList<Pe>(HOST_PES);
        for (int i = 0; i < HOST_PES; i++) {
            peList.add(new PeSimple(mips));
        }

        return peList;
    }
}
