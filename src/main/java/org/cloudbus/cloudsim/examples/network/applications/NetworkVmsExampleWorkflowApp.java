package org.cloudsimplus.examples.network.applications;

import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.cloudlets.network.NetworkCloudlet;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.network.NetworkVm;

import java.util.Arrays;
import java.util.List;

/**
 * An example of a Workflow Distributed Application that is compounded by
 * 3 {@link NetworkCloudlet}, each one having different stages
 * such as sending, receiving or processing data.
 *
 * @author Saurabh Kumar Garg
 * @author Rajkumar Buyya
 * @author Manoel Campos da Silva Filho
 */
public class NetworkVmsExampleWorkflowApp extends NetworkVmExampleAbstract {
    /**
     * Starts the execution of the example.
     * @param args
     */
    public static void main(String[] args) {
        new NetworkVmsExampleWorkflowApp();
    }

    private NetworkVmsExampleWorkflowApp(){
        super();
    }

    @Override
    public List<NetworkCloudlet> createNetworkCloudlets(DatacenterBroker broker) {
        final var networkCloudlets = new NetworkCloudlet[3];
        final List<NetworkVm> selectedVms = randomlySelectVmsForApp(networkCloudlets.length);

        for(int i = 0; i < networkCloudlets.length; i++){
            networkCloudlets[i] = createNetworkCloudlet(selectedVms.get(i), broker);
            System.out.printf(
                "Created NetworkCloudlet %d for Application %d%n",
                networkCloudlets[i].getId(), broker.getId());
        }

        //NetworkCloudlet 0 Tasks
        addExecutionTask(networkCloudlets[0]);
        addSendTask(networkCloudlets[0], networkCloudlets[2]);

        //NetworkCloudlet 1 Tasks
        addExecutionTask(networkCloudlets[1]);
        addSendTask(networkCloudlets[1], networkCloudlets[2]);

        //NetworkCloudlet 2 Tasks
        addReceiveTask(networkCloudlets[2], networkCloudlets[0]);
        addReceiveTask(networkCloudlets[2], networkCloudlets[1]);
        addExecutionTask(networkCloudlets[2]);

        return Arrays.asList(networkCloudlets);
    }

    /**
     * Creates a {@link NetworkCloudlet}.
     *
     * @param vm the VM that will run the created {@link NetworkCloudlet)
     * @param broker the broker that will own the create NetworkCloudlet
     * @return
     */
    private NetworkCloudlet createNetworkCloudlet(NetworkVm vm, DatacenterBroker broker) {
        final var utilizationModel = new UtilizationModelFull();
        final var cloudlet = new NetworkCloudlet(1, CLOUDLET_PES);
        cloudlet
                .setFileSize(CLOUDLET_FILE_SIZE)
                .setOutputSize(CLOUDLET_OUTPUT_SIZE)
                .setUtilizationModel(utilizationModel)
                .setVm(vm)
                .setBroker(vm.getBroker());

        return cloudlet;
    }
}
