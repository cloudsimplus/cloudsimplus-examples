package org.cloudbus.cloudsim.examples.network.applications;

import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.cloudlets.network.NetworkCloudlet;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.network.NetworkVm;

import java.util.ArrayList;
import java.util.List;

/**
 * An example of "Bag of Tasks" distributed application that is compounded by
 * 3 {@link NetworkCloudlet}, where 2 of them send data to the first created one,
 * which waits the data to be received.
 *
 * @author Saurabh Kumar Garg
 * @author Rajkumar Buyya
 * @author Manoel Campos da Silva Filho
 */
public class NetworkVmsExampleBagOfTasksApp extends NetworkVmExampleAbstract {
    private static final long CLOUDLET_LENGTH = 1;

    /**
     * Starts the execution of the example.
     * @param args
     */
    public static void main(String[] args) {
        new NetworkVmsExampleBagOfTasksApp();
    }

    private NetworkVmsExampleBagOfTasksApp(){
        super();
    }

    @Override
    public List<NetworkCloudlet> createNetworkCloudlets(DatacenterBroker broker){
        final int CLOUDLETS_BY_APP = 2;
        final var netCloudletList = new ArrayList<NetworkCloudlet>(CLOUDLETS_BY_APP+1);
        //basically, each task runs the simulation and then data is consolidated in one task

        for(int i = 0; i < CLOUDLETS_BY_APP; i++){
            final var utilizationModel = new UtilizationModelFull();
            final var cloudlet = new NetworkCloudlet(i, CLOUDLET_PES);
            final NetworkVm vm = getVmList().get(i);
            cloudlet
                    .setFileSize(CLOUDLET_FILE_SIZE)
                    .setOutputSize(CLOUDLET_OUTPUT_SIZE)
                    .setUtilizationModel(utilizationModel)
                    .setVm(vm)
                    .setBroker(vm.getBroker());
            netCloudletList.add(cloudlet);
        }

        createTasksForNetworkCloudlets(netCloudletList);

        return netCloudletList;
    }

    private void createTasksForNetworkCloudlets(final List<NetworkCloudlet> networkCloudletList) {
        for (var cloudlet : networkCloudletList) {
            addExecutionTask(cloudlet);

            //NetworkCloudlet 0 waits data from other Cloudlets
            if (cloudlet.getId()==0){
                /*
                If there are a total of N Cloudlets, since the first one receives packets
                from all the other ones, this for creates the tasks for the first Cloudlet
                to wait packets from N-1 other Cloudlets.
                 */
                for(int j=1; j < networkCloudletList.size(); j++) {
                    addReceiveTask(cloudlet, networkCloudletList.get(j));
                }
            }
            //The other NetworkCloudlets send data to the first one
            else addSendTask(cloudlet, networkCloudletList.get(0));
        }
    }
}
