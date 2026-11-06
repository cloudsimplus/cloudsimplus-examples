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
package org.cloudsimplus.examples.dynamic;

import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.distributions.PoissonDistr;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.resources.CoreCapacity;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;

import java.util.ArrayList;
import java.util.List;

/// Shows how to submit Cloudlets that arrive according to a Poisson process,
/// using a [PoissonDistr] to generate the time between arrivals.
///
/// All arrivals are generated before the simulation starts, so that each Cloudlet
/// submission delay is the time of the previous arrival plus the generated inter-arrival time.
/// The arrival rate and the delays use the same time unit, which here is seconds.
///
/// @author Manoel Campos da Silva Filho
/// @since CloudSim Plus 9.0.0
/// @see RandomCloudletsArrivalExample
public class PoissonCloudletsArrivalExample {
    /// The mean number of Cloudlets arriving per second: 1 Cloudlet every 2.5 seconds, on average.
    private static final double ARRIVALS_PER_SECOND = 0.4;

    /// Cloudlets arriving after this time (in seconds) aren't created.
    private static final double ARRIVALS_TIME_LIMIT = 100;

    private static final long SEED = 1;

    private static final int HOST_CORES = 8;
    private static final int VMS = 2;
    private static final int VM_CORES = 4;
    private static final int MIPS = 1000;
    private static final int CLOUDLET_LENGTH = 5000;

    public static void main(final String[] args) {
        new PoissonCloudletsArrivalExample();
    }

    private PoissonCloudletsArrivalExample() {
        final var simulation = new CloudSimPlus();
        DatacenterSimple.of(simulation, List.of(createHost()));

        final DatacenterBroker broker = new DatacenterBrokerSimple(simulation);
        broker.submitVmList(createVms());

        final var poisson = new PoissonDistr(ARRIVALS_PER_SECOND, SEED);
        final var cloudlets = createCloudlets(poisson);
        broker.submitCloudletList(cloudlets);

        simulation.start();

        new CloudletsTableBuilder(broker.getCloudletFinishedList()).build();
        System.out.printf(
            "%d Cloudlets arrived in %.0f seconds. %.0f were expected, with a mean inter-arrival time of %.2f seconds.%n",
            cloudlets.size(), ARRIVALS_TIME_LIMIT, ARRIVALS_PER_SECOND * ARRIVALS_TIME_LIMIT, poisson.getInterArrivalMeanTime());
    }

    private static List<Cloudlet> createCloudlets(final PoissonDistr poisson) {
        final var cloudlets = new ArrayList<Cloudlet>();
        double arrivalTime = poisson.sample();
        while (arrivalTime <= ARRIVALS_TIME_LIMIT) {
            cloudlets.add(createCloudlet(arrivalTime));
            arrivalTime += poisson.sample();
        }

        return cloudlets;
    }

    private static Cloudlet createCloudlet(final double arrivalTime) {
        final var cloudlet = CloudletSimple.of(CLOUDLET_LENGTH, 1, new UtilizationModelFull());
        cloudlet.setSubmissionDelay(arrivalTime);
        return cloudlet;
    }

    private static Host createHost() {
        final long ram = 8192; // in Megabytes
        final long bw = 10_000; // in Megabits/s
        final long storage = 100_000; // in Megabytes
        return HostSimple.of(ram, bw, storage, new CoreCapacity(HOST_CORES, MIPS));
    }

    private static List<Vm> createVms() {
        final var vms = new ArrayList<Vm>(VMS);
        for (int i = 0; i < VMS; i++) {
            vms.add(VmSimple.of(new CoreCapacity(VM_CORES, MIPS)).setRam(1024).setBw(1000).setSize(10_000));
        }

        return vms;
    }
}
