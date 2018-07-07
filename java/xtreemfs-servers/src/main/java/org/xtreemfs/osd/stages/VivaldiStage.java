/*
 * Copyright (c) 2008-2011 by Juan Gonzalez de Benito, Bjoern Kolbeck,
 *               Barcelona Supercomputing Center, Zuse Institute Berlin
 *
 * Licensed under the BSD License, see LICENSE file for details.
 *
 */
package org.xtreemfs.osd.stages;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

import org.xtreemfs.common.KeyValuePairs;
import org.xtreemfs.common.uuids.Mapping;
import org.xtreemfs.common.uuids.ServiceUUID;
import org.xtreemfs.common.uuids.UnknownUUIDException;
import org.xtreemfs.dir.DIRClient;
import org.xtreemfs.foundation.TimeSync;
import org.xtreemfs.foundation.logging.Logging;
import org.xtreemfs.foundation.pbrpc.Schemes;
import org.xtreemfs.foundation.pbrpc.client.RPCAuthentication;
import org.xtreemfs.foundation.pbrpc.generatedinterfaces.RPC.MessageType;
import org.xtreemfs.foundation.pbrpc.generatedinterfaces.RPC.RPCHeader;
import org.xtreemfs.foundation.pbrpc.generatedinterfaces.RPC.RPCHeader.ErrorResponse;
import org.xtreemfs.foundation.pbrpc.server.UDPMessage;
import org.xtreemfs.osd.OSDRequest;
import org.xtreemfs.osd.OSDRequestDispatcher;
import org.xtreemfs.osd.vivaldi.VivaldiNode;
import org.xtreemfs.osd.vivaldi.ZipfGenerator;
import org.xtreemfs.pbrpc.generatedinterfaces.DIR.Service;
import org.xtreemfs.pbrpc.generatedinterfaces.DIR.ServiceSet;
import org.xtreemfs.pbrpc.generatedinterfaces.DIR.ServiceType;
import org.xtreemfs.pbrpc.generatedinterfaces.GlobalTypes.VivaldiCoordinates;
import org.xtreemfs.pbrpc.generatedinterfaces.OSD.xtreemfs_pingMesssage;
import org.xtreemfs.pbrpc.generatedinterfaces.OSDServiceConstants;

/**
 * 实现OSD的Vivaldi组件并定期更新其坐标。
 *  vivaldi 算法
 * （在去中心化系统中，信息是通过节点之间互相传播完成的，如果每个消息都向整个集群广播，会形成严重的网络风暴，这是需要严格避免的。
 *  那么如何在避免网络风暴的前提下，同时保证消息传递的效率和可靠性呢？Vivaldi算法就是为了解决这个问题提出的。
 *
 * Vivaldi算法通过启发式的学习算法，在集群通信的过程中计算节点之间的RTT，并动态学习网络的拓扑结构，每个节点可以选择离自己“最近”的N个节点进行广播，这样就可以最大程度减少网络风暴的出现，调整N可以修改信息的传播速度和效率以及对网络带宽的影响。）
 * Vivaldi是由MIT [1]开发的一种轻量级算法，它允许将坐标空间中的位置分配给网络中的每个节点，因此两个节点的坐标之间的距离预测它们之间的实际通信延迟。
 * 为了生成在有效坐标系中，有必要确定将使用哪个空间以及将使用哪个公式来计算两个给定点之间的距离。
 * 在我们的例子中，已经证明了实现二维空间，其中两个坐标之间的欧几里德距离准确地预测了它们相应节点之间的延迟，以非常小的错误概率生成有效结果。
 * 使算法正常工作，系统的节点也必须随机地和无限地保持联系以重新调整它们的位置，因此可以反映网络中的任何可能的变化。
 * 在每次重新调整中，一个节点与另一个邻居联系，获取其坐标并修改其自己的坐标，因此最终欧几里德距离与测量的往返时间尽可能相似。
 * 另一方面，一组节点有建立了一个有效的坐标系，有必要使用一些有助于减少引入新节点影响的机制，因此我们避免它们改变已经稳定的系统。
 * 这就是为什么除了坐标之外，Vivaldi在每个节点中保留一个局部错误，该错误通知节点关于其位置的确定程度。
 * 这样，具有更稳定位置的节点将具有更小的局部误差，并且当它们接触它以重新调整它们的位置时将影响更多其余节点（图4.1）。
 * 一旦系统被调整，网络的任何节点都可以确定哪个节点节点是最接近的，计算非常简单，在很短的时间内没有产生额外的流量。
 * 一些已经开发的Vivaldi实现可以在p2psim和Chord中找到。您可能也对Ledlie等人的工作感兴趣[6]。
 *
 * 服务端：
 * 由于我们的架构中有不同类型的节点，因此并非所有节点都以相同的方式集成Vivaldi。 虽然客户端通常在较短的时间内执行，但服务器已启动并运行，
 * 因此我们的想法是让OSD（此时它们是唯一实现Vivaldi的服务器）建立永久坐标系统，客户端可以通过该系统 ，找到它的位置。
 * Stage used in the OSD to manage Vivaldi.
 * OSD中有一个独立的阶段，负责管理Vivaldi，并为其余组件提供一些有效的坐标，
 * 用于定义当前坐标系中节点的位置。阶段保持无限期运行和定期联系一个不同的OSD，
 * 询问它的坐标和本地错误。利用该数据和自身OSD的坐标可以计算欧几里德距离并将其与针对所接触节点测量的实际RTT进行比较。
 * OSD重新调整其位置的频率由参数MIN_TIMEOUT_RECALCULATE和MAX_TIMEOUT_RECALCULATE定义。在执行重新调整之后，
 * 阶段通常计算包括在由这两个参数定义的时间间隔中的随机数，
 * 并在该秒数期间休眠直到下一次迭代。这种方式可以避免产生流量峰值，其中所有节点同时发送请求并及时分配净使用。
 * 较大的周期将减少网络中的开销，但会使节点调整得更慢以适应环境中可能的变化，
 * 而较小的周期将需要更多的流量但会产生更具反应性的系统。在每次迭代中，
 * 引入的阶段选择一个 从可用OSD列表中联系到的节点，其中包含目录服务中包含的信息。
 * 必须以某种方式更新此列表，以便阶段始终可以注意到节点脱机。
 *
 * 客户端：
 * 在我们的系统中，客户端通常在更短的时间内执行，因此他们必须能够更快地确定他们的位置。
 * 这可以做到，因为它们不影响其余的节点，它们只是从已经放置的OSD中获取一些所需的信息来定位自己。
 * 在Vivaldi中，每个节点负责其自己的坐标，并且通常必须重新计算它们至少一个在它们代表坐标系中的实际位置之前的次数很少。
 * 即使“调整”了一组OSD，客户端也需要多次重新计算其位置（每次针对一个节点），然后才能准确逼近其实际位置。
 * Vivaldi要求网络的节点产生流量并在它们之间进行通信。
 * 如在OSD的情况下，客户端还具有参数MIN_TIMEOUT_- RECALCULATE和MAX_TIMEOUT_RECALCULATE，允许定义重新计算周期。
 * 尽管OSD中的模拟参数具有相同的名称，但它们是不同的参数，因此它们都必须在不同的文件中定义。
 * 最后，重要的是要强调在客户端首次启动后，它保持坐标并保留它们在执行中，
 * 所以尽管它安装和卸载了很多不同的卷或打开并关闭了很多文件，但它仍然处于良好的位置。在重新启动客户端节点之前，不会重新初始化坐标。
 * @author Juan Gonzalez de Benito (BSC) & Björn Kolbeck (ZIB)
 */
public class VivaldiStage extends Stage {

    private static final int STAGEOP_ASYNC_PING = 1;
    private static final int STAGEOP_SYNC_PING = 2;
    /**
     * System time when the timer was last executed.
     */
    private long lastCheck;
    /**
     * The main OSDRequestDispatcher used by the OSD.
     */
    private final OSDRequestDispatcher master;
    /**
     * Client used to communicate with the Directory Service.
     */
    private final DIRClient dirClient;
    /**
     * List of already sent Vivaldi requests.
     */
    private HashMap<InetSocketAddress, SentRequest> sentRequests;
    /**
     * List of simultaneous VivaldiRetrys. Each of them keep track of the RTTs measured for the same node.
     */
    private HashMap<InetSocketAddress, VivaldiRetry> toBeRetried;
    /**
     * The node that includes the Vivaldi information of the OSD.
     */
    private VivaldiNode vNode;
    /**
     * Number of milliseconds until the next Vivaldi recalculation
     */
    private long nextRecalculationInMS;
    /**
     * Number of milliseconds until the next timer execution
     */
    private long nextTimerRunInMS;
    /**
     * List of existent OSDs. The OSD contact them to get their Vivaldi information
     * and use it to recalculate its position
     */
    private LinkedList<KnownOSD> knownOSDs;
    /**
     * Number of elapsed Vivaldi iterations
     */
    private long vivaldiIterations;
    private ZipfGenerator rankGenerator;
    private final double ZIPF_GENERATOR_SKEW = 0.5; // TODO(mno): move to global config?! (depends on keeping ZIPF or not)
    /**
     * Number of retries to be sent before accepting 'suspiciously high' RTT
     */
    private final int MAX_RETRIES_FOR_A_REQUEST;
    /**
     * Recalculation interval.
     *
     * The recalculation period is randomly determined and lies within:
     * [RECALCULATION_INTERVAL - RECALCULATION_EPSILON, RECALCULATION_INTERVAL + RECALCULATION_EPSILON]
     */
    private final int RECALCULATION_INTERVAL;
    /**
     * Recalculation epsilon.
     *
     * The recalculation period is randomly determined and lies within:
     * [RECALCULATION_INTERVAL - RECALCULATION_EPSILON, RECALCULATION_INTERVAL + RECALCULATION_EPSILON]
     */
    private final int RECALCULATION_EPSILON;
    /**
     * Number of times the node recalculates its position before updating
     * its list of existent OSDs.
     */
    private final int ITERATIONS_BEFORE_UPDATING;
    /**
     * Maximum number of milliseconds an OSD waits for a RESPONSE before discarding
     * its corresponding REQUEST. Expiration times under {@code TIMER_INTERVAL_IN_MS}
     * are not granted.
     */
    private final int MAX_REQUEST_TIMEOUT_IN_MS;
    /**
     * Period of time between timer executions.
     */
    private final int TIMER_INTERVAL_IN_MS;

    public VivaldiStage(OSDRequestDispatcher master, int maxRequestsQueueLength) {
        super("VivaldiSt", maxRequestsQueueLength);
        this.master = master;
        this.dirClient = master.getDIRClient();

        this.sentRequests = new HashMap<InetSocketAddress, SentRequest>();
        this.toBeRetried = new HashMap<InetSocketAddress, VivaldiRetry>();
        this.vNode = new VivaldiNode();

        MAX_RETRIES_FOR_A_REQUEST = master.getConfig().getVivaldiMaxRetriesForARequest();
        RECALCULATION_INTERVAL = master.getConfig().getVivaldiRecalculationInterval();
        RECALCULATION_EPSILON = master.getConfig().getVivaldiRecalculationEpsilon();
        ITERATIONS_BEFORE_UPDATING = master.getConfig().getVivaldiIterationsBeforeUpdating();
        MAX_REQUEST_TIMEOUT_IN_MS = master.getConfig().getVivaldiMaxRequestTimeout();
        TIMER_INTERVAL_IN_MS = master.getConfig().getVivaldiTimerInterval();
        
        //TOFIX: should  the coordinates be initialized from a file?
        if (Logging.isDebug()) {
            Logging.logMessage(
                    Logging.LEVEL_DEBUG,
                    this,
                    String.format("Coordinates initialized:(%.3f,%.3f)",
                    vNode.getCoordinates().getXCoordinate(),
                    vNode.getCoordinates().getYCoordinate()));
        }
        this.knownOSDs = null;
        this.rankGenerator = null;

        this.lastCheck = 0;
    }

    /**
     * The position of the node is recalculated from a list of previously measured RTTs. Even if the
     * resulting movement is too big, the node's coordinates will be modified.
     *
     * @param coordinatesJ Coordinates of the node where recalculating against. If the operation
     * has been triggered by a timeout, this parameter is {@code null}.
     *
     * @param availableRTTs List of RTTs measured after several retries.
     */
    private void forceVivaldiRecalculation(VivaldiCoordinates coordinatesJ, ArrayList<Long> availableRTTs) {

        //TOFIX:In this version, the recalculation is discarded when the last retry times out: (coordinatesJ==null)
        if ((coordinatesJ != null) && (availableRTTs.size() > 0)) {

            //Determine the minimum RTT of the whole sample
            long minRTT = availableRTTs.get(0);

            StringBuilder strbRTTs = new StringBuilder(Long.toString(minRTT));

            for (int i = 1; i < availableRTTs.size(); i++) {
                strbRTTs.append(",");
                strbRTTs.append(availableRTTs.get(i));

                if (availableRTTs.get(i) < minRTT) {
                    minRTT = availableRTTs.get(i);
                }
            }

            vNode.recalculatePosition(coordinatesJ, minRTT, true);
            if (Logging.isDebug()) {
                Logging.logMessage(Logging.LEVEL_DEBUG,
                        this,
                        String.format("Forced(%s):%d Viv:%.3f Own:(%.3f,%.3f) lE=%.3f Rem:(%.3f,%.3f) rE=%.3f",
                        strbRTTs.toString(),
                        minRTT,
                        VivaldiNode.calculateDistance(vNode.getCoordinates(), coordinatesJ),
                        vNode.getCoordinates().getXCoordinate(),
                        vNode.getCoordinates().getYCoordinate(),
                        vNode.getCoordinates().getLocalError(),
                        coordinatesJ.getXCoordinate(),
                        coordinatesJ.getYCoordinate(),
                        coordinatesJ.getLocalError()));
            }
        }
    }

    public void getVivaldiCoordinatesAsync(xtreemfs_pingMesssage coordinates, InetSocketAddress sender, OSDRequest request) {
        this.enqueueOperation(STAGEOP_ASYNC_PING, new Object[]{coordinates, sender}, request, null);
    }

    public void getVivaldiCoordinatesSync(xtreemfs_pingMesssage coordinates, OSDRequest request, VivaldiPingCallback listener) {
        this.enqueueOperation(STAGEOP_SYNC_PING, new Object[]{coordinates}, request, listener);
    }

    public static interface VivaldiPingCallback {

        public void coordinatesCallback(VivaldiCoordinates myCoordinates, ErrorResponse error);
    }

    @Override
    protected void processMethod(StageRequest method) {
        xtreemfs_pingMesssage msg = (xtreemfs_pingMesssage) method.getArgs()[0];

        if (method.getStageMethod() == STAGEOP_ASYNC_PING) {
            try {
                InetSocketAddress sender = (InetSocketAddress) method.getArgs()[1];
                if (msg.getRequestResponse()) {
                    RPCHeader.RequestHeader rqHdr = RPCHeader.RequestHeader.newBuilder().setAuthData(RPCAuthentication.authNone).setUserCreds(RPCAuthentication.userService).setInterfaceId(OSDServiceConstants.INTERFACE_ID).setProcId(OSDServiceConstants.PROC_ID_XTREEMFS_PING).build();
                    RPCHeader hdr = RPCHeader.newBuilder().setCallId(0).setMessageType(MessageType.RPC_REQUEST).setRequestHeader(rqHdr).build();
                    xtreemfs_pingMesssage response = xtreemfs_pingMesssage.newBuilder().setCoordinates(this.vNode.getCoordinates()).setRequestResponse(false).build();

                    // TODO(mno): Comment the following sleeps before committing. They are just for local testing with a simulated delay.
                    /*
                    if(master.getConfig().getUUID().toString().equals("test9-localhost-OSD") || sender.getPort() == 32649)
                        Thread.sleep(300);
                    else if (master.getConfig().getUUID().toString().equals("test8-localhost-OSD") || sender.getPort() == 32648)
                        Thread.sleep(150);
                    else
                        Thread.sleep(20);
                    */
                    
                    master.sendUDPMessage(hdr, response, sender);
                } else {
                    recalculateCoordinates(msg.getCoordinates(), sender);
                }
            } catch (Exception ex) {
                Logging.logError(Logging.LEVEL_WARN, this, ex);
            } finally {
                // free the request buffer, as it won't be freed otherwise
                // because the response is sent asynchronously
                method.getRequest().getRpcRequest().freeBuffers();
            }
        } else {
            VivaldiPingCallback callback = (VivaldiPingCallback) method.getCallback();
            callback.coordinatesCallback(this.vNode.getCoordinates(), null);
        }
    }

    protected void recalculateCoordinates(VivaldiCoordinates coordinatesJ, InetSocketAddress sender) {
        try {

            boolean coordinatesModified = false;

            SentRequest correspondingReq = sentRequests.remove(sender);

            if (correspondingReq != null) {

                //Calculate the RTT
                long now = System.currentTimeMillis();
                long estimatedRTT = now - correspondingReq.getSystemTime();


                //Two nodes will never be at the same position
                if (estimatedRTT == 0) {
                    estimatedRTT = 1;
                }

                //Recalculate Vivaldi
                VivaldiRetry prevRetry = toBeRetried.get(sender);
                boolean retriedTraceVar = false, forcedTraceVar = false;

                /* If MAX_RETRIES == 0 the coordinates must be recalculated without
                retrying*/
                boolean retryingDisabled = (MAX_RETRIES_FOR_A_REQUEST <= 0);

                if (!vNode.recalculatePosition(coordinatesJ, estimatedRTT, retryingDisabled)) {

                    //The RTT seems to be too big, so it might be a good idea to go for a retry

                    if (prevRetry == null) {
                        toBeRetried.put(sender, new VivaldiRetry(estimatedRTT));
                        retriedTraceVar = true;
                    } else {

                        prevRetry.addRTT(estimatedRTT);

                        prevRetry.setRetried(false);

                        if (prevRetry.numberOfRetries() > MAX_RETRIES_FOR_A_REQUEST) {

                            //Recalculate using the previous RTTs
                            forceVivaldiRecalculation(coordinatesJ, prevRetry.getRTTs());
                            coordinatesModified = true;

                            //Just for traceVar
                            forcedTraceVar = true;

                            toBeRetried.remove(sender);
                        } else {
                            retriedTraceVar = true;
                        }
                    }

                } else {

                    coordinatesModified = true;

                    if (prevRetry != null) {
                        /*The received RTT is correct but it has been necessary to retry
                        some request previously, so now our structures must be updated.*/
                        toBeRetried.remove(sender);
                    }
                }

                if (!forcedTraceVar && Logging.isDebug()) {
                    //TOFIX: Printing getHostName() without any kind of control could be dangerous (?)
                    Logging.logMessage(Logging.LEVEL_DEBUG,
                            this,
                            String.format("%s:%d Viv:%.3f Own:(%.3f,%.3f) lE=%.3f Rem:(%.3f,%.3f) rE=%.3f %s",
                            retriedTraceVar ? "RETRY" : "RTT",
                            estimatedRTT,
                            VivaldiNode.calculateDistance(vNode.getCoordinates(), coordinatesJ),
                            vNode.getCoordinates().getXCoordinate(),
                            vNode.getCoordinates().getYCoordinate(),
                            vNode.getCoordinates().getLocalError(),
                            coordinatesJ.getXCoordinate(),
                            coordinatesJ.getYCoordinate(),
                            coordinatesJ.getLocalError(),
                            sender.getHostName()));
                }

            }//there's not any previously registered request , so we just discard the response

            //Use coordinatesJ to update knownOSDs if possible
            if ((knownOSDs != null) && (!knownOSDs.isEmpty())) {

                //Check if the message has been sent by some of the knownOSDs
                String strAddress = sender.getHostName()
                        + ":"
                        + sender.getPort();

                int sendingOSD = 0;
                boolean OSDfound = false;

                //Look for the OSD that has sent the message and update its coordinates
                while ((!OSDfound) && (sendingOSD < knownOSDs.size())) {

                    if ((knownOSDs.get(sendingOSD).getStrAddress() != null)
                            && knownOSDs.get(sendingOSD).getStrAddress().equals(strAddress)) {

                        knownOSDs.get(sendingOSD).setCoordinates(coordinatesJ);
                        OSDfound = true;

                    } else {
                        sendingOSD++;
                    }
                }

                if (coordinatesModified) {
                    /*Client's position has been modified so we must
                     * re-sort knownOSDs accordingly to the new vivaldi distances*/

                    LinkedList<KnownOSD> auxOSDList = new LinkedList<KnownOSD>();

                    for (int i = knownOSDs.size() - 1; i >= 0; i--) {

                        KnownOSD insertedOSD = knownOSDs.get(i);

                        double insertedOSDDistance =
                                VivaldiNode.calculateDistance(insertedOSD.getCoordinates(),
                                this.vNode.getCoordinates());
                        int j = 0;
                        boolean inserted = false;

                        while ((!inserted) && (j < auxOSDList.size())) {
                            double prevOSDDistance =
                                    VivaldiNode.calculateDistance(auxOSDList.get(j).getCoordinates(),
                                    this.vNode.getCoordinates());

                            if (insertedOSDDistance <= prevOSDDistance) {
                                auxOSDList.add(j, insertedOSD);
                                inserted = true;
                            } else {
                                j++;
                            }
                        }
                        if (!inserted) {
                            auxOSDList.add(insertedOSD);
                        }

                    }

                    knownOSDs = auxOSDList;

                } else if (OSDfound) {

                    /* It's not necessary to resort the whole knownOSDs but only the OSD
                     * whose coordinates might have changed*/

                    KnownOSD kosd = knownOSDs.remove(sendingOSD);
                    kosd.setCoordinates(coordinatesJ);
                    double osdNewDistance =
                            VivaldiNode.calculateDistance(coordinatesJ, vNode.getCoordinates());

                    int i = 0;
                    boolean inserted = false;
                    while ((!inserted) && (i < knownOSDs.size())) {
                        double existingDistance =
                                VivaldiNode.calculateDistance(knownOSDs.get(i).getCoordinates(),
                                vNode.getCoordinates());
                        if (osdNewDistance <= existingDistance) {
                            knownOSDs.add(i, kosd);
                            inserted = true;
                        } else {
                            i++;
                        }
                    }

                    if (!inserted) {
                        knownOSDs.add(kosd);
                    }
                }
            }

        } catch (Exception ex) {
            Logging.logError(Logging.LEVEL_ERROR, this, ex);
        }
    }

    /**
     * Sends a message to a OSD requesting its coordinates.
     * (NOTE: the response is sent in processMethod(..).)
     *
     * @param osd Address of the OSD we want to contact.
     * @param myCoordinates Our own coordinates.
     */
    private void sendVivaldiRequest(InetSocketAddress osd, VivaldiCoordinates myCoordinates) {


        //It's not allowed to send two requests to the same OSD simultaneously
        if (sentRequests.get(osd) == null) {

            RPCHeader.RequestHeader rqHdr = RPCHeader.RequestHeader.newBuilder().
                    setAuthData(RPCAuthentication.authNone).
                    setUserCreds(RPCAuthentication.userService).
                    setInterfaceId(OSDServiceConstants.INTERFACE_ID).
                    setProcId(OSDServiceConstants.PROC_ID_XTREEMFS_PING).build();
            RPCHeader hdr = RPCHeader.newBuilder().setCallId(0).
                    setMessageType(MessageType.RPC_REQUEST).
                    setRequestHeader(rqHdr).build();

            
            xtreemfs_pingMesssage pingMsg = xtreemfs_pingMesssage.newBuilder().setCoordinates(myCoordinates).setRequestResponse(true).build();
            
            long systemTimeNow = System.currentTimeMillis();
            //getLocalSystemTime does not introduce such a big overhead, while currentTimeMillis is required to get the necessary precision.
            long localTimeNow = TimeSync.getLocalSystemTime();

            //If we're sending a request, we need to register it in our structures so we can process its response later
            sentRequests.put(osd, new SentRequest(localTimeNow, systemTimeNow));
            
            try {
                master.sendUDPMessage(hdr, pingMsg, osd);
            } catch (IOException ex) {
                Logging.logError(Logging.LEVEL_ERROR, this, ex);
            }
        }
    }

    /**
     * Keeps the list of sent requests updated, by eliminating those whose timeout
     * has expired.
     */
    private void maintainSentRequests() {

        final long localNow = TimeSync.getLocalSystemTime();
        ArrayList<InetSocketAddress> removedRequests = new ArrayList<InetSocketAddress>();

        //Check which requests have timed out
        for (InetSocketAddress reqKey : sentRequests.keySet()) {
            if (localNow >= sentRequests.get(reqKey).getLocalTime() + MAX_REQUEST_TIMEOUT_IN_MS) {
                if (Logging.isDebug()) {
                    Logging.logMessage(Logging.LEVEL_DEBUG, this, "OSD times out: " + reqKey.getHostName());
                }
                removedRequests.add(reqKey);
            }
        }

        //Manage the timed out requests
        for (InetSocketAddress removed : removedRequests) {
            //Is it the first time the node times out?
            VivaldiRetry prevRetry = toBeRetried.get(removed);

            //The retry is marked as 'not retried' so it will have priority when sending the next request
            if (prevRetry == null) {
                toBeRetried.put(removed, new VivaldiRetry());
            } else {
                //Take note of the new time out
                prevRetry.addTimeout();
                prevRetry.setRetried(false);

                //We've already retried too many times, so it's time to recalculate with the available info
                if (prevRetry.numberOfRetries() > MAX_RETRIES_FOR_A_REQUEST) {
                    forceVivaldiRecalculation(null, prevRetry.getRTTs());
                    toBeRetried.remove(removed);
                }
            }
            sentRequests.remove(removed);
        }
    }

    /**
     * Performs some operations that have been defined to be executed periodically.
     *
     * The frequency of the timer is defined by the attribute {@code
     * TIMER_INTERVAL_IN_MS}
     */
    private void executeTimer() {
        master.updateVivaldiCoordinates(vNode.getCoordinates());  

        //Remove the requests that are not needed anymore
        maintainSentRequests();
    }

    public void receiveVivaldiMessage(UDPMessage msg) {
        enqueueOperation(STAGEOP_ASYNC_PING, new Object[]{msg}, null, null);
    }

    /**
     * Updates the list of known OSDs, from the data stored in the DS. This
     * function is responsible of keeping a list of OSDs used by the algorithm.
     */
    private void updateKnownOSDs() {
        try {
            ServiceSet receivedOSDs = dirClient.xtreemfs_service_get_by_type(null, RPCAuthentication.authNone, RPCAuthentication.userService, ServiceType.SERVICE_TYPE_OSD);

            //We need our own UUID, to discard its corresponding entry
            String ownUUID = master.getConfig().getUUID().toString();

            knownOSDs = new LinkedList<KnownOSD>();

            for (Service osd : receivedOSDs.getServicesList()) {

                //If it's not our own entry and the referred service is not offline
                if (!ownUUID.equals(osd.getUuid()) && osd.getLastUpdatedS() != 0) {

                    //Parse the coordinates provided by the DS
                    String strCoords = KeyValuePairs.getValue(osd.getData().getDataList(), "vivaldi_coordinates");


                    if (strCoords != null) {

                        //Calculate distance from the client to the OSD
                        VivaldiCoordinates osdCoords = VivaldiNode.stringToCoordinates(strCoords);
                        KnownOSD newOSD = new KnownOSD(osd.getUuid(), osdCoords);

                        double insertedOSDDistance =
                                VivaldiNode.calculateDistance(osdCoords, this.vNode.getCoordinates());

                        //Insert the new OSD accordingly to its vivaldi distance

                        int i = 0;

                        boolean inserted = false;

                        while ((!inserted) && (i < knownOSDs.size())) {
                            double oldOSDDistance =
                                    VivaldiNode.calculateDistance(knownOSDs.get(i).getCoordinates(),
                                    this.vNode.getCoordinates());

                            if (insertedOSDDistance <= oldOSDDistance) {
                                knownOSDs.add(i, newOSD);
                                inserted = true;
                            } else {
                                i++;
                            }
                        }
                        if (!inserted) {
                            knownOSDs.add(newOSD);
                        }
                    }
                }
            }

            if (Logging.isDebug()) {
                Logging.logMessage(Logging.LEVEL_DEBUG, this, "Updating list of known OSDs (size: " + knownOSDs.size() + ")");
            }

        } catch (Exception exc) {
            Logging.logMessage(Logging.LEVEL_ERROR, this, "Error while updating known OSDs: " + exc);
            //Create an empty OSDs set
            knownOSDs = new LinkedList<KnownOSD>();
        } finally {
            //Adapt the Zipf generator to the new sample
            if (rankGenerator == null) {
                rankGenerator = new ZipfGenerator(knownOSDs.size(), ZIPF_GENERATOR_SKEW);
            } else {
                rankGenerator.setSize(knownOSDs.size());
            }

            //Previous requests are discarded
            sentRequests.clear();
            toBeRetried.clear();
        }
    }

    /**
     * Executes one Vivaldi iteration. For each of these iterations, the algorithm
     * chooses one random node from the list of known OSDs and sends it a Vivaldi
     * request, to recalculate then the position of the OSD using the received information.
     */
    private void iterateVivaldi() {

        if (vivaldiIterations % ITERATIONS_BEFORE_UPDATING == 1) {
            updateKnownOSDs();


        } //Start recalculation
        if ((knownOSDs != null) && (!knownOSDs.isEmpty())) {

            //It's still necessary to retry some request
            if (toBeRetried.size() > 0) {
                for (InetSocketAddress addr : toBeRetried.keySet()) {

                    if (!toBeRetried.get(addr).hasBeenRetried()) {

                        if (Logging.isDebug()) {
                            Logging.logMessage(Logging.LEVEL_DEBUG, this, "Retrying: " + addr.getHostName());
                        }

                        sendVivaldiRequest(addr, vNode.getCoordinates());
                        toBeRetried.get(addr).setRetried(true);
                    }
                }
            } else {

                //Choose a random OSD and send it a new request
                //int chosenIndex = (int)(Math.random()*knownOSDs.size());

                //if we get here, knownOSDs is not empty and therefore rankGenerator != null
                int chosenIndex = rankGenerator.next();

                KnownOSD chosenOSD = knownOSDs.get(chosenIndex);

                try {

                    //Get the corresponding InetAddress
                    ServiceUUID sUUID = new ServiceUUID(chosenOSD.getUUID());
                    sUUID.resolve(Schemes.SCHEME_PBRPCU);
                    InetSocketAddress osdAddr = null;
                    Mapping serviceMappings[] = sUUID.getMappings();

                    int mapIt = 0;

                    while ((osdAddr == null) && (mapIt < serviceMappings.length)) {

                        if (serviceMappings[mapIt].protocol.equals(Schemes.SCHEME_PBRPCU)) {

                            osdAddr = serviceMappings[mapIt].resolvedAddr;

                            if (Logging.isDebug()) {
                                Logging.logMessage(Logging.LEVEL_DEBUG, this, "Recalculating against: " + chosenOSD.getUUID());
                            }

                            //Only at this point we known which InetAddress corresponds with which UUID
                            chosenOSD.setStrAddress(osdAddr.getAddress().getHostAddress()
                                    + ":"
                                    + osdAddr.getPort());

                            //After receiving the response, we will be able to recalculate
                            sendVivaldiRequest(osdAddr, vNode.getCoordinates());
                        }
                        mapIt++;
                    }

                } catch (UnknownUUIDException unke) {
                    Logging.logMessage(Logging.LEVEL_ERROR, this, "Unknown UUID: " + chosenOSD.getUUID());
                } catch (Exception e) {
                    Logging.logMessage(Logging.LEVEL_ERROR, this, "Error detected while iterating Vivaldi");
                }
            }
        }

        vivaldiIterations = (vivaldiIterations + 1) % Long.MAX_VALUE;
    }

    /**
     * Main function of the stage. It keeps polling request methods from the stage
     * queue and processing them. If there are no methods available, the function
     * blocks until a new request is enqueued or the defined timer expires.
     */
    @Override
    public void run() {

        notifyStarted();

        vivaldiIterations = 0;

        long pollTimeoutInMS;

        nextRecalculationInMS = -1;
        nextTimerRunInMS = -1;

        while (!quit) {
            try {
                pollTimeoutInMS = checkTimer();
                final StageRequest op = q.poll(pollTimeoutInMS, TimeUnit.MILLISECONDS);
                if (op != null) {
                    processMethod(op);
                }
            } catch (InterruptedException ex) {
                break;
            } catch (Throwable ex) {
                Logging.logMessage(Logging.LEVEL_ERROR, this, "Error detected: " + ex);
                notifyCrashed(ex);
                break;
            }
        }
        notifyStopped();
    }

    /**
     * Checks if the main timer or the last recalculation period have expired
     * and executes, depending on the case, the {@code executeTimer}
     * function or a new Vivaldi iteration.
     *
     * @return the number of milliseconds before executing a new checking.
     */
    private long checkTimer() {

        final long now = TimeSync.getLocalSystemTime();

        //Elapsed time since last check


        long elapsedTime = lastCheck > 0 ? (now - lastCheck) : 0;

        lastCheck = now;

        nextRecalculationInMS -= elapsedTime;
        nextTimerRunInMS -= elapsedTime;

        //Need to execute our timer


        if (nextTimerRunInMS <= 0) {
            executeTimer();
            nextTimerRunInMS = TIMER_INTERVAL_IN_MS;
        }

        //Time to iterate
        if (nextRecalculationInMS <= 0) {
            //We must recalculate our position now
            iterateVivaldi();

            //Determine when the next recalculation will be executed
            nextRecalculationInMS = RECALCULATION_INTERVAL - RECALCULATION_EPSILON  + (long)(2 * RECALCULATION_EPSILON * Math.random());
        }

        long nextCheck = nextTimerRunInMS > nextRecalculationInMS ? nextRecalculationInMS : nextTimerRunInMS;

        return nextCheck;
    }

    public class KnownOSD {

        private String uuid;
        private String strAddress;
        private VivaldiCoordinates coordinates;

        public KnownOSD(String uuid, VivaldiCoordinates coordinates) {
            this.uuid = uuid;
            this.strAddress = null;
            this.coordinates = coordinates;
        }

        public String getUUID() {
            return this.uuid;
        }

        public VivaldiCoordinates getCoordinates() {
            return this.coordinates;
        }

        public String getStrAddress() {
            return this.strAddress;
        }

        public void setStrAddress(String strAddress) {
            this.strAddress = strAddress;
        }

        public void setCoordinates(VivaldiCoordinates newCoordinates) {
            this.coordinates = newCoordinates;
        }
    }

    public class VivaldiRetry {

        private ArrayList<Long> prevRTTs;
        private int numRetries;
        private boolean retried;

        public VivaldiRetry() {
            //New retry caused by a time out
            prevRTTs = new ArrayList<Long>();
            numRetries = 1;
            retried = false;
        }

        public VivaldiRetry(long firstRTT) {
            //New retry caused by a excessively big RTT
            prevRTTs = new ArrayList<Long>();
            prevRTTs.add(firstRTT);
            numRetries = 1;
            retried = false;
        }

        public ArrayList<Long> getRTTs() {
            return prevRTTs;
        }

        public void addRTT(long newRTT) {
            prevRTTs.add(newRTT);
            numRetries++;
        }

        public void addTimeout() {
            numRetries++;
        }

        public int numberOfRetries() {
            return numRetries;
        }

        public void setRetried(boolean p) {
            retried = p;
        }

        public boolean hasBeenRetried() {
            return retried;
        }
    }

    public class SentRequest {

        private long localTime;
        private long systemTime;

        public SentRequest(long localTime, long systemTime) {
            this.localTime = localTime;
            this.systemTime = systemTime;
        }

        public long getLocalTime() {
            return this.localTime;
        }

        public long getSystemTime() {
            return this.systemTime;
        }
    }
}
