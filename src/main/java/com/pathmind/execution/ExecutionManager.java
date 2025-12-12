package com.pathmind.execution;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.behavior.IPathingBehavior;
import baritone.api.process.ICustomGoalProcess;
import baritone.api.process.IExploreProcess;
import baritone.api.process.IFarmProcess;
import baritone.api.process.IMineProcess;
import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeConnection;
import com.pathmind.nodes.NodeParameter;
import com.pathmind.nodes.NodeType;
import com.pathmind.nodes.ParameterType;
import com.pathmind.data.NodeGraphData;
import com.pathmind.data.NodeGraphPersistence;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.Objects;

/**
 * Manages the execution state of the node graph.
 * Tracks which node is currently active and provides state information for overlays.
 */
public class ExecutionManager {
    private static ExecutionManager instance;
    private Node activeNode;
    private boolean isExecuting;
    private long executionStartTime;
    private long executionEndTime;
    private static final long MINIMUM_DISPLAY_DURATION = 3000; // 3 seconds minimum display
    private NodeGraphData lastExecutedGraph;
    private NodeGraphData lastGlobalGraph;
    private List<Node> activeNodes;
    private List<NodeConnection> activeConnections;
    private final Set<ConnectionKey> activeConnectionLookup;
    private final List<String> executingEvents;
    private volatile boolean cancelRequested;
    private final Map<Node, ChainController> activeChains;
    private final Map<ConnectionKey, Node> eventConnectionOwners;
    private final Set<Node> activeEventFunctionNodes;
    private boolean globalExecutionActive;
    private boolean lastSnapshotWasGlobal;
    private long activeNodeStartTime;
    private long activeNodePausedDuration;
    private long activeNodePauseStartTime;
    private long activeNodeEndTime;
    private boolean singleplayerPaused;

    private static final long NODE_EXECUTION_DELAY_MS = 150L;

    private static class ChainController {
        final Node startNode;
        volatile boolean cancelRequested;

        ChainController(Node startNode) {
            this.startNode = startNode;
            this.cancelRequested = false;
        }
    }

    private static final class ConnectionKey {
        private final String outputNodeId;
        private final int outputSocket;
        private final String inputNodeId;
        private final int inputSocket;

        ConnectionKey(String outputNodeId, int outputSocket, String inputNodeId, int inputSocket) {
            this.outputNodeId = outputNodeId;
            this.outputSocket = outputSocket;
            this.inputNodeId = inputNodeId;
            this.inputSocket = inputSocket;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof ConnectionKey)) {
                return false;
            }
            ConnectionKey other = (ConnectionKey) obj;
            return outputSocket == other.outputSocket
                    && inputSocket == other.inputSocket
                    && Objects.equals(outputNodeId, other.outputNodeId)
                    && Objects.equals(inputNodeId, other.inputNodeId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(outputNodeId, outputSocket, inputNodeId, inputSocket);
        }
    }

    private ExecutionManager() {
        this.activeNode = null;
        this.isExecuting = false;
        this.executionStartTime = 0;
        this.executionEndTime = 0;
        this.activeNodes = new ArrayList<>();
        this.activeConnections = new ArrayList<>();
        this.executingEvents = new ArrayList<>();
        this.cancelRequested = false;
        this.activeChains = new ConcurrentHashMap<>();
        this.globalExecutionActive = false;
        this.lastSnapshotWasGlobal = false;
        this.activeConnectionLookup = ConcurrentHashMap.newKeySet();
        this.eventConnectionOwners = new ConcurrentHashMap<>();
        this.activeEventFunctionNodes = ConcurrentHashMap.newKeySet();
        this.activeNodeStartTime = 0;
        this.activeNodePausedDuration = 0;
        this.activeNodePauseStartTime = 0;
        this.activeNodeEndTime = 0;
        this.singleplayerPaused = false;
    }
    
    public static ExecutionManager getInstance() {
        if (instance == null) {
            instance = new ExecutionManager();
        }
        return instance;
    }

    public void executeGraph(List<Node> nodes, List<NodeConnection> connections) {
        executeGraphInternal(nodes, connections, true);
    }

    private void executeGraphInternal(List<Node> nodes, List<NodeConnection> connections, boolean markGlobalSnapshot) {
        if (nodes == null || connections == null) {
            System.out.println("ExecutionManager: Cannot execute graph - missing nodes or connections.");
            return;
        }

        List<Node> startNodes = findStartNodes(nodes);
        if (startNodes.isEmpty()) {
            System.out.println("ExecutionManager: No START nodes found!");
            return;
        }

        // Ensure Baritone isn't still executing stale goals from a previous session
        cancelAllBaritoneCommands();

        List<NodeConnection> filteredConnections = filterConnections(connections);

        NodeGraphData snapshot = createGraphSnapshot(nodes, filteredConnections);
        this.lastExecutedGraph = snapshot;
        if (markGlobalSnapshot) {
            this.lastGlobalGraph = snapshot;
        }
        this.lastSnapshotWasGlobal = markGlobalSnapshot;
        this.activeNodes = new ArrayList<>(nodes);
        this.activeConnections = new ArrayList<>(filteredConnections);
        rebuildConnectionState(this.activeNodes, this.activeConnections);
        this.cancelRequested = false;

        startExecution(startNodes, markGlobalSnapshot);
        activeChains.clear();

        for (Node startNode : startNodes) {
            ChainController controller = new ChainController(startNode);
            activeChains.put(startNode, controller);
            CompletableFuture<Void> chainFuture = runChain(startNode, controller);
            chainFuture.whenComplete((ignored, throwable) -> handleChainCompletion(controller, throwable));
        }
    }

    public void replayLastGraph() {
        if (lastExecutedGraph == null) {
            System.out.println("ExecutionManager: No previously executed node graph to replay.");
            return;
        }

        if (!executeGraphSnapshot(lastExecutedGraph, lastSnapshotWasGlobal)) {
            System.out.println("ExecutionManager: No nodes available to replay.");
        }
    }

    public void playAllGraphs() {
        if (lastGlobalGraph != null && executeGraphSnapshot(lastGlobalGraph, true)) {
            return;
        }

        NodeGraphData savedGraph = NodeGraphPersistence.loadNodeGraph();
        if (savedGraph != null && executeGraphSnapshot(savedGraph, true)) {
            return;
        }

        System.out.println("ExecutionManager: No saved node graph available to play.");
    }

    public boolean executeBranch(Node startNode, List<Node> nodes, List<NodeConnection> connections) {
        if (startNode == null || startNode.getType() != NodeType.START) {
            System.out.println("ExecutionManager: Cannot execute branch - invalid START node.");
            return false;
        }
        if (nodes == null || connections == null) {
            System.out.println("ExecutionManager: Cannot execute branch - missing nodes or connections.");
            return false;
        }
        if (isChainActive(startNode)) {
            System.out.println("ExecutionManager: START node already executing, ignoring branch start request.");
            return false;
        }

        List<NodeConnection> filteredConnections = filterConnections(connections);
        Set<Node> branchNodeSet = collectBranchNodes(startNode, filteredConnections);

        for (Node node : nodes) {
            if (node.getType() == NodeType.EVENT_FUNCTION) {
                branchNodeSet.addAll(collectBranchNodes(node, filteredConnections));
            }
        }

        List<Node> branchNodes = new ArrayList<>();
        for (Node node : nodes) {
            if (branchNodeSet.contains(node)) {
                branchNodes.add(node);
            }
        }

        List<NodeConnection> branchConnections = new ArrayList<>();
        for (NodeConnection connection : filteredConnections) {
            if (branchNodeSet.contains(connection.getOutputNode()) && branchNodeSet.contains(connection.getInputNode())) {
                branchConnections.add(connection);
            }
        }

        this.lastExecutedGraph = createGraphSnapshot(branchNodes, branchConnections);
        this.lastSnapshotWasGlobal = false;
        this.activeNodes = branchNodes;
        this.activeConnections = branchConnections;
        rebuildConnectionState(this.activeNodes, this.activeConnections);
        this.cancelRequested = false;

        if (activeChains.isEmpty()) {
            startExecution(Collections.singletonList(startNode), false);
        } else {
            this.isExecuting = true;
        }

        ChainController controller = new ChainController(startNode);
        activeChains.put(startNode, controller);
        CompletableFuture<Void> chainFuture = runChain(startNode, controller);
        chainFuture.whenComplete((ignored, throwable) -> handleChainCompletion(controller, throwable));
        return true;
    }
    
    /**
     * Start execution with the given start node
     */
    private void startExecution(List<Node> startNodes, boolean markGlobal) {
        this.activeNode = startNodes.isEmpty() ? null : startNodes.get(0);
        if (this.activeNode != null) {
            resetActiveNodeTiming();
        } else {
            clearActiveNodeTiming();
        }
        this.isExecuting = true;
        this.globalExecutionActive = markGlobal;
        this.cancelRequested = false;
        this.executionStartTime = System.currentTimeMillis();
        this.executionEndTime = 0;
        if (!startNodes.isEmpty()) {
            System.out.println(
                    "ExecutionManager: Started execution with " + startNodes.size() +
                            " start node(s) at time " + this.executionStartTime);
        } else {
            System.out.println("ExecutionManager: Started execution without any root nodes at time " + this.executionStartTime);
        }
    }
    
    /**
     * Set the currently active node
     */
    public void setActiveNode(Node node) {
        this.activeNode = node;
        if (node != null) {
            resetActiveNodeTiming();
        } else {
            clearActiveNodeTiming();
        }
        System.out.println("ExecutionManager: Set active node to " + (node != null ? node.getType() : "null") + " at time " + System.currentTimeMillis());
    }
    
    /**
     * Stop execution
     */
    public void stopExecution() {
        System.out.println("ExecutionManager: Stopping execution at time " + System.currentTimeMillis());
        this.isExecuting = false;
        this.globalExecutionActive = false;
        if (cancelRequested) {
            this.executionEndTime = 0;
            this.executionStartTime = 0;
            this.activeNode = null;
            clearActiveNodeTiming();
            cancelRequested = false;
        } else {
            this.executionEndTime = System.currentTimeMillis();
            this.activeNodeEndTime = this.executionEndTime;
        }
        // Keep activeNode for minimum display duration
    }

    /**
     * Request that all executing node chains stop immediately.
     */
    public void requestStopAll() {
        cancelAllBaritoneCommands();

        if (!isExecuting && activeNode == null && activeChains.isEmpty()) {
            return;
        }

        System.out.println("ExecutionManager: Stop requested for all node trees at time " + System.currentTimeMillis());
        cancelRequested = true;
        for (ChainController controller : activeChains.values()) {
            controller.cancelRequested = true;
        }
        this.isExecuting = false;
        this.globalExecutionActive = false;
        this.activeNode = null;
        clearActiveNodeTiming();
        this.executionStartTime = 0;
        this.executionEndTime = 0;
        this.activeNodes.clear();
        this.activeConnections.clear();
        this.activeConnectionLookup.clear();
        this.executingEvents.clear();
        this.eventConnectionOwners.clear();
        this.activeEventFunctionNodes.clear();
        this.activeChains.clear();
    }

    private void cancelAllBaritoneCommands() {
        PreciseCompletionTracker.getInstance().cancelAllTasks();

        try {
            IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
            if (baritone == null) {
                return;
            }

            IPathingBehavior pathingBehavior = baritone.getPathingBehavior();
            if (pathingBehavior != null) {
                pathingBehavior.cancelEverything();
            }

            ICustomGoalProcess goalProcess = baritone.getCustomGoalProcess();
            if (goalProcess != null) {
                goalProcess.setGoal(null);
                goalProcess.onLostControl();
            }

            IMineProcess mineProcess = baritone.getMineProcess();
            if (mineProcess != null) {
                mineProcess.cancel();
            }

            IExploreProcess exploreProcess = baritone.getExploreProcess();
            if (exploreProcess != null && exploreProcess.isActive()) {
                exploreProcess.onLostControl();
            }

            IFarmProcess farmProcess = baritone.getFarmProcess();
            if (farmProcess != null && farmProcess.isActive()) {
                farmProcess.onLostControl();
            }
        } catch (Exception e) {
            System.err.println("ExecutionManager: Failed to cancel Baritone processes: " + e.getMessage());
        }
    }
    
    /**
     * Get the currently active node
     */
    public Node getActiveNode() {
        return activeNode;
    }

    public boolean requestStopForStart(Node startNode) {
        if (startNode == null) {
            return false;
        }

        ChainController controller = activeChains.get(startNode);
        if (controller == null) {
            System.out.println("ExecutionManager: No active chain found for requested START node stop.");
            return false;
        }

        controller.cancelRequested = true;
        System.out.println("ExecutionManager: Stop requested for START node " + startNode.getId() + " at time " + System.currentTimeMillis());
        return true;
    }

    public boolean isChainActive(Node startNode) {
        if (startNode == null) {
            return false;
        }
        ChainController controller = activeChains.get(startNode);
        return controller != null && !controller.cancelRequested;
    }
    
    /**
     * Check if execution is currently running or should still be displayed
     */
    public boolean isExecuting() {
        if (isExecuting) {
            return true;
        }

        return isCompletionDisplayActive();
    }

    /**
     * Returns true when the overlay should show completion state messaging.
     */
    public boolean isDisplayingCompletion() {
        if (isExecuting) {
            return false;
        }

        return isCompletionDisplayActive();
    }

    private boolean isCompletionDisplayActive() {
        if (executionEndTime > 0 && activeNode != null) {
            long timeSinceEnd = System.currentTimeMillis() - executionEndTime;
            if (timeSinceEnd < MINIMUM_DISPLAY_DURATION) {
                return true;
            } else {
                // Clear the active node after minimum display duration
                this.activeNode = null;
                this.executionEndTime = 0;
                clearActiveNodeTiming();
            }
        }
        return false;
    }

    public boolean isGlobalExecutionActive() {
        return globalExecutionActive;
    }
    
    /**
     * Get the execution start time
     */
    public long getExecutionStartTime() {
        return executionStartTime;
    }
    
    /**
     * Get the current execution duration in milliseconds
     */
    public long getExecutionDuration() {
        if (executionStartTime == 0) {
            return 0;
        }
        
        if (isExecuting) {
            return System.currentTimeMillis() - executionStartTime;
        } else if (executionEndTime > 0) {
            return executionEndTime - executionStartTime;
        }
        
        return 0;
    }

    /**
     * Get the elapsed duration for the currently active node in milliseconds.
     */
    public long getActiveNodeDuration() {
        if (activeNodeStartTime == 0) {
            return 0;
        }

        long referenceTime;
        if (isExecuting) {
            referenceTime = System.currentTimeMillis();
        } else if (activeNodeEndTime > 0) {
            referenceTime = activeNodeEndTime;
        } else {
            referenceTime = System.currentTimeMillis();
        }

        long pausedTime = activeNodePausedDuration;
        if (singleplayerPaused && activeNodePauseStartTime > 0) {
            pausedTime += referenceTime - activeNodePauseStartTime;
        }

        return Math.max(0, referenceTime - activeNodeStartTime - pausedTime);
    }

    /**
     * Update whether the singleplayer game is currently paused so node timers can be frozen.
     */
    public void setSingleplayerPaused(boolean paused) {
        if (this.singleplayerPaused == paused) {
            return;
        }

        this.singleplayerPaused = paused;
        if (paused) {
            if (activeNode != null) {
                this.activeNodePauseStartTime = System.currentTimeMillis();
            }
        } else if (activeNodePauseStartTime > 0) {
            this.activeNodePausedDuration += System.currentTimeMillis() - this.activeNodePauseStartTime;
            this.activeNodePauseStartTime = 0;
        }
    }

    private void resetActiveNodeTiming() {
        this.activeNodeStartTime = System.currentTimeMillis();
        this.activeNodePausedDuration = 0;
        this.activeNodeEndTime = 0;
        this.activeNodePauseStartTime = singleplayerPaused ? activeNodeStartTime : 0;
    }

    private void clearActiveNodeTiming() {
        this.activeNodeStartTime = 0;
        this.activeNodePausedDuration = 0;
        this.activeNodePauseStartTime = 0;
        this.activeNodeEndTime = 0;
    }

    private CompletableFuture<Void> runChain(Node currentNode, ChainController controller) {
        if (cancelRequested || controller == null || controller.cancelRequested) {
            return CompletableFuture.completedFuture(null);
        }

        return scheduleNodeStartDelay()
            .thenCompose(ignored -> {
                if (cancelRequested || controller.cancelRequested) {
                    return CompletableFuture.completedFuture(null);
                }

                setActiveNode(currentNode);

                if (cancelRequested || controller.cancelRequested) {
                    return CompletableFuture.completedFuture(null);
                }

                return currentNode.execute()
                    .thenCompose(ignoredFuture -> {
                        if (cancelRequested || controller.cancelRequested) {
                            return CompletableFuture.completedFuture(null);
                        }
                        return handleEventCallIfNeeded(currentNode, controller);
                    })
                    .thenCompose(ignoredFuture -> continueFromNode(currentNode, controller));
            });
    }

    private CompletableFuture<Void> scheduleNodeStartDelay() {
        if (NODE_EXECUTION_DELAY_MS <= 0L) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> { },
            CompletableFuture.delayedExecutor(NODE_EXECUTION_DELAY_MS, TimeUnit.MILLISECONDS));
    }

    private CompletableFuture<Void> handleEventCallIfNeeded(Node node, ChainController controller) {
        if (cancelRequested || controller.cancelRequested || node.getType() != NodeType.EVENT_CALL) {
            return CompletableFuture.completedFuture(null);
        }

        NodeParameter nameParam = node.getParameter("Name");
        String eventName = normalizeEventName(nameParam != null ? nameParam.getStringValue() : null);
        if (eventName.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        if (executingEvents.contains(eventName)) {
            System.out.println("ExecutionManager: Skipping recursive event call for " + eventName);
            return CompletableFuture.completedFuture(null);
        }

        List<Node> handlers = new ArrayList<>();
        for (Node candidate : activeNodes) {
            if (candidate.getType() == NodeType.EVENT_FUNCTION) {
                NodeParameter candidateParam = candidate.getParameter("Name");
                String candidateName = normalizeEventName(candidateParam != null ? candidateParam.getStringValue() : null);
                if (!candidateName.isEmpty() && candidateName.equals(eventName)) {
                    handlers.add(candidate);
                }
            }
        }

        if (handlers.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        executingEvents.add(eventName);
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (Node handler : handlers) {
            if (cancelRequested || controller.cancelRequested) {
                break;
            }
            chain = chain.thenCompose(ignored -> runEventHandler(handler, controller));
        }
        return chain.whenComplete((ignored, throwable) -> executingEvents.remove(eventName));
    }

    private CompletableFuture<Void> continueFromNode(Node currentNode, ChainController controller) {
        if (cancelRequested || controller == null || controller.cancelRequested) {
            return CompletableFuture.completedFuture(null);
        }

        int nextSocket = currentNode.consumeNextOutputSocket();

        if (currentNode.hasAttachedActionNode()) {
            Node attachedAction = currentNode.getAttachedActionNode();
            NodeType type = currentNode.getType();

            if (attachedAction != null) {
                if (type == NodeType.CONTROL_FOREVER && nextSocket != Node.NO_OUTPUT) {
                    return runChain(attachedAction, controller)
                        .thenCompose(ignored -> {
                            if (cancelRequested || controller.cancelRequested) {
                                return CompletableFuture.completedFuture(null);
                            }
                            return runChain(currentNode, controller);
                        });
                }

                if ((type == NodeType.CONTROL_REPEAT || type == NodeType.CONTROL_REPEAT_UNTIL) && nextSocket == 0) {
                    return runChain(attachedAction, controller)
                        .thenCompose(ignored -> {
                            if (cancelRequested || controller.cancelRequested) {
                                return CompletableFuture.completedFuture(null);
                            }
                            return runChain(currentNode, controller);
                        });
                }
            }
        }

        if (nextSocket == Node.NO_OUTPUT) {
            return CompletableFuture.completedFuture(null);
        }

        Node nextNode = getNextConnectedNode(currentNode, activeConnections, nextSocket);
        if (nextNode == null && nextSocket > 0) {
            nextNode = getNextConnectedNode(currentNode, activeConnections, 0);
        }
        if (nextNode != null) {
            return runChain(nextNode, controller);
        }
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<Void> runEventHandler(Node handler, ChainController controller) {
        if (handler == null) {
            return CompletableFuture.completedFuture(null);
        }

        setEventFunctionActive(handler, true);
        return runChain(handler, controller).whenComplete((ignored, throwable) -> setEventFunctionActive(handler, false));
    }

    private void handleChainCompletion(ChainController controller, Throwable throwable) {
        if (controller == null) {
            return;
        }

        if (throwable != null && !cancelRequested && !controller.cancelRequested) {
            System.err.println("ExecutionManager: Error during execution - " + throwable.getMessage());
            throwable.printStackTrace();
        }

        activeChains.remove(controller.startNode);

        if (activeChains.isEmpty() && isExecuting) {
            stopExecution();
            activeNodes.clear();
            activeConnections.clear();
            activeConnectionLookup.clear();
            executingEvents.clear();
            eventConnectionOwners.clear();
            activeEventFunctionNodes.clear();
        }
    }

    private boolean executeGraphSnapshot(NodeGraphData graphData, boolean markGlobalSnapshot) {
        if (graphData == null) {
            return false;
        }

        Map<String, Node> nodeMap = new HashMap<>();
        List<Node> nodes = new ArrayList<>();

        for (NodeGraphData.NodeData nodeData : graphData.getNodes()) {
            Node node = new Node(nodeData.getType(), nodeData.getX(), nodeData.getY());

            try {
                java.lang.reflect.Field idField = Node.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(node, nodeData.getId());
            } catch (Exception e) {
                System.err.println("ExecutionManager: Failed to set node ID during replay - " + e.getMessage());
            }

            if (nodeData.getMode() != null) {
                node.setMode(nodeData.getMode());
            }

            node.getParameters().clear();
            if (nodeData.getParameters() != null) {
                for (NodeGraphData.ParameterData paramData : nodeData.getParameters()) {
                    ParameterType paramType = ParameterType.valueOf(paramData.getType());
                    NodeParameter param = new NodeParameter(paramData.getName(), paramType, paramData.getValue());
                    node.getParameters().add(param);
                }
            }
            node.recalculateDimensions();

            nodes.add(node);
            nodeMap.put(nodeData.getId(), node);
        }

        for (NodeGraphData.NodeData nodeData : graphData.getNodes()) {
            if (nodeData.getAttachedSensorId() != null) {
                Node control = nodeMap.get(nodeData.getId());
                Node sensor = nodeMap.get(nodeData.getAttachedSensorId());
                if (control != null && sensor != null) {
                    control.attachSensor(sensor);
                }
            }
        }

        for (NodeGraphData.NodeData nodeData : graphData.getNodes()) {
            if (nodeData.getParentControlId() != null) {
                Node sensor = nodeMap.get(nodeData.getId());
                Node control = nodeMap.get(nodeData.getParentControlId());
                if (sensor != null && control != null && sensor.isSensorNode()) {
                    control.attachSensor(sensor);
                }
            }
        }

        for (NodeGraphData.NodeData nodeData : graphData.getNodes()) {
            if (nodeData.getAttachedActionId() != null) {
                Node control = nodeMap.get(nodeData.getId());
                Node child = nodeMap.get(nodeData.getAttachedActionId());
                if (control != null && child != null) {
                    control.attachActionNode(child);
                }
            }
        }

        for (NodeGraphData.NodeData nodeData : graphData.getNodes()) {
            if (nodeData.getParentActionControlId() != null) {
                Node child = nodeMap.get(nodeData.getId());
                Node control = nodeMap.get(nodeData.getParentActionControlId());
                if (child != null && control != null && control.canAcceptActionNode(child)) {
                    control.attachActionNode(child);
                }
            }
        }

        for (NodeGraphData.NodeData nodeData : graphData.getNodes()) {
            List<NodeGraphData.ParameterAttachmentData> attachments = nodeData.getParameterAttachments();
            if (attachments != null && !attachments.isEmpty()) {
                Node host = nodeMap.get(nodeData.getId());
                if (host != null) {
                    attachments.sort(java.util.Comparator.comparingInt(NodeGraphData.ParameterAttachmentData::getSlotIndex));
                    for (NodeGraphData.ParameterAttachmentData attachment : attachments) {
                        Node parameter = nodeMap.get(attachment.getParameterNodeId());
                        if (parameter != null) {
                            host.attachParameter(parameter, attachment.getSlotIndex());
                        }
                    }
                }
            } else if (nodeData.getAttachedParameterId() != null) {
                Node host = nodeMap.get(nodeData.getId());
                Node parameter = nodeMap.get(nodeData.getAttachedParameterId());
                if (host != null && parameter != null && parameter.getParentParameterHost() == null) {
                    host.attachParameter(parameter);
                }
            }
        }

        for (NodeGraphData.NodeData nodeData : graphData.getNodes()) {
            List<NodeGraphData.ParameterAttachmentData> attachments = nodeData.getParameterAttachments();
            if (attachments != null && !attachments.isEmpty()) {
                continue;
            }
            if (nodeData.getParentParameterHostId() != null) {
                Node parameter = nodeMap.get(nodeData.getId());
                Node host = nodeMap.get(nodeData.getParentParameterHostId());
                if (parameter != null && host != null && parameter.isParameterNode()
                    && parameter.getParentParameterHost() == null) {
                    host.attachParameter(parameter);
                }
            }
        }

        List<NodeConnection> connections = new ArrayList<>();
        for (NodeGraphData.ConnectionData connData : graphData.getConnections()) {
            Node outputNode = nodeMap.get(connData.getOutputNodeId());
            Node inputNode = nodeMap.get(connData.getInputNodeId());
            if (outputNode != null && inputNode != null) {
                if (outputNode.isSensorNode() || inputNode.isSensorNode()) {
                    continue;
                }
                connections.add(new NodeConnection(outputNode, inputNode, connData.getOutputSocket(), connData.getInputSocket()));
            }
        }

        if (nodes.isEmpty()) {
            return false;
        }

        executeGraphInternal(nodes, connections, markGlobalSnapshot);
        return true;
    }

    public boolean shouldAnimateConnection(NodeConnection connection) {
        if (connection == null || !isExecuting) {
            return false;
        }

        ConnectionKey key = toKey(connection);
        if (key == null) {
            return false;
        }

        Node owner = eventConnectionOwners.get(key);
        if (owner != null) {
            return activeEventFunctionNodes.contains(owner);
        }

        return activeConnectionLookup.contains(key);
    }

    private String normalizeEventName(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private Node getNextConnectedNode(Node currentNode, List<NodeConnection> connections, int outputSocket) {
        for (NodeConnection connection : connections) {
            if (connection.getOutputNode() == currentNode) {
                if (connection.getOutputSocket() == outputSocket) {
                    return connection.getInputNode();
                }
            }
        }
        return null;
    }

    private List<Node> findStartNodes(List<Node> nodes) {
        List<Node> startNodes = new ArrayList<>();
        if (nodes == null) {
            return startNodes;
        }

        for (Node node : nodes) {
            if (node.getType() == NodeType.START) {
                startNodes.add(node);
            }
        }

        return startNodes;
    }

    private Set<Node> collectBranchNodes(Node startNode, List<NodeConnection> connections) {
        LinkedHashSet<Node> visited = new LinkedHashSet<>();
        if (startNode == null) {
            return visited;
        }

        ArrayDeque<Node> stack = new ArrayDeque<>();
        stack.push(startNode);

        while (!stack.isEmpty()) {
            Node current = stack.pop();
            if (!visited.add(current)) {
                continue;
            }

            Node attachedSensor = current.getAttachedSensor();
            if (attachedSensor != null) {
                stack.push(attachedSensor);
            }

            Node attachedAction = current.getAttachedActionNode();
            if (attachedAction != null) {
                stack.push(attachedAction);
            }

            for (NodeConnection connection : connections) {
                if (connection.getOutputNode() == current) {
                    stack.push(connection.getInputNode());
                }
            }
        }

        return visited;
    }

    private NodeGraphData createGraphSnapshot(List<Node> nodes, List<NodeConnection> connections) {
        NodeGraphData snapshot = new NodeGraphData();

        for (Node node : nodes) {
            NodeGraphData.NodeData nodeData = new NodeGraphData.NodeData();
            nodeData.setId(node.getId());
            nodeData.setType(node.getType());
            nodeData.setMode(node.getMode());
            nodeData.setX(node.getX());
            nodeData.setY(node.getY());

            List<NodeGraphData.ParameterData> parameterDataList = new ArrayList<>();
            for (NodeParameter parameter : node.getParameters()) {
                NodeGraphData.ParameterData parameterData = new NodeGraphData.ParameterData();
                parameterData.setName(parameter.getName());
                parameterData.setValue(parameter.getStringValue());
                parameterData.setType(parameter.getType().name());
                parameterDataList.add(parameterData);
            }
            nodeData.setParameters(parameterDataList);
            nodeData.setAttachedSensorId(node.getAttachedSensorId());
            nodeData.setParentControlId(node.getParentControlId());
            nodeData.setAttachedActionId(node.getAttachedActionId());
            nodeData.setParentActionControlId(node.getParentActionControlId());
            nodeData.setAttachedParameterId(node.getAttachedParameterId());
            nodeData.setParentParameterHostId(node.getParentParameterHostId());

            snapshot.getNodes().add(nodeData);
        }

        for (NodeConnection connection : filterConnections(connections)) {
            NodeGraphData.ConnectionData connectionData = new NodeGraphData.ConnectionData(
                    connection.getOutputNode().getId(),
                    connection.getInputNode().getId(),
                    connection.getOutputSocket(),
                    connection.getInputSocket()
            );
            snapshot.getConnections().add(connectionData);
        }

        return snapshot;
    }

    private List<NodeConnection> filterConnections(List<NodeConnection> connections) {
        List<NodeConnection> filtered = new ArrayList<>();
        if (connections == null) {
            return filtered;
        }
        for (NodeConnection connection : connections) {
            if (connection == null) {
                continue;
            }
            Node output = connection.getOutputNode();
            Node input = connection.getInputNode();
            if (output == null || input == null) {
                continue;
            }
            if (output.isSensorNode() || input.isSensorNode()) {
                continue;
            }
            if (connection.getOutputSocket() < 0 || connection.getOutputSocket() >= output.getOutputSocketCount()) {
                continue;
            }
            filtered.add(connection);
        }
        return filtered;
    }

    private void rebuildConnectionState(List<Node> nodes, List<NodeConnection> connections) {
        activeConnectionLookup.clear();
        eventConnectionOwners.clear();
        activeEventFunctionNodes.clear();

        if (connections != null) {
            for (NodeConnection connection : connections) {
                ConnectionKey key = toKey(connection);
                if (key != null) {
                    activeConnectionLookup.add(key);
                }
            }
        }

        if (nodes == null || connections == null) {
            return;
        }

        for (Node node : nodes) {
            if (node.getType() != NodeType.EVENT_FUNCTION) {
                continue;
            }

            Set<Node> scopeNodes = collectBranchNodes(node, connections);
            if (scopeNodes.isEmpty()) {
                continue;
            }

            for (NodeConnection connection : connections) {
                if (scopeNodes.contains(connection.getOutputNode()) && scopeNodes.contains(connection.getInputNode())) {
                    ConnectionKey key = toKey(connection);
                    if (key != null) {
                        eventConnectionOwners.put(key, node);
                    }
                }
            }
        }
    }

    private void setEventFunctionActive(Node handler, boolean active) {
        if (handler == null || handler.getType() != NodeType.EVENT_FUNCTION) {
            return;
        }

        if (active) {
            activeEventFunctionNodes.add(handler);
        } else {
            activeEventFunctionNodes.remove(handler);
        }
    }

    private ConnectionKey toKey(NodeConnection connection) {
        if (connection == null) {
            return null;
        }

        Node output = connection.getOutputNode();
        Node input = connection.getInputNode();
        if (output == null || input == null) {
            return null;
        }

        return new ConnectionKey(output.getId(), connection.getOutputSocket(), input.getId(), connection.getInputSocket());
    }
}
