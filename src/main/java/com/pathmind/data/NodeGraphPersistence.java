package com.pathmind.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeConnection;
import com.pathmind.nodes.NodeParameter;
import com.pathmind.nodes.NodeType;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles saving and loading node graphs to/from disk.
 */
public class NodeGraphPersistence {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(NodeType.class, new NodeTypeAdapter())
            .registerTypeAdapter(com.pathmind.nodes.NodeMode.class, new NodeModeAdapter())
            .create();

    private static final Map<String, String> IN_MEMORY_JSON_CACHE = new ConcurrentHashMap<>();

    /**
     * Save the current node graph to disk
     */
    public static boolean saveNodeGraph(List<Node> nodes, List<NodeConnection> connections) {
        return saveNodeGraphForPreset(PresetManager.getActivePreset(), nodes, connections);
    }

    public static boolean saveNodeGraphForPreset(String presetName, List<Node> nodes, List<NodeConnection> connections) {
        Path savePath = PresetManager.getPresetPath(presetName);
        NodeGraphData data = buildNodeGraphData(nodes, connections);
        cachePresetGraph(presetName, data);
        return writeNodeGraphDataToPath(data, savePath);
    }

    public static boolean saveNodeGraphToPath(List<Node> nodes, List<NodeConnection> connections, Path savePath) {
        NodeGraphData data = buildNodeGraphData(nodes, connections);
        return writeNodeGraphDataToPath(data, savePath);
    }

    /**
     * Load the node graph from disk
     */
    public static NodeGraphData loadNodeGraph() {
        return loadNodeGraphForPreset(PresetManager.getActivePreset());
    }

    public static NodeGraphData loadNodeGraphForPreset(String presetName) {
        Path savePath = PresetManager.getPresetPath(presetName);
        NodeGraphData data = loadNodeGraphFromPath(savePath);
        if (data != null) {
            cachePresetGraph(presetName, data);
            return data;
        }

        String key = cacheKeyForPath(savePath);
        if (key != null) {
            String cached = IN_MEMORY_JSON_CACHE.get(key);
            if (cached != null) {
                try {
                    return GSON.fromJson(cached, NodeGraphData.class);
                } catch (Exception e) {
                    System.err.println("Failed to deserialize cached node graph: " + e.getMessage());
                }
            }
        }

        return null;
    }

    public static NodeGraphData loadNodeGraphFromPath(Path savePath) {
        try {
            if (!Files.exists(savePath)) {
                System.out.println("No saved node graph found at: " + savePath);
                return null;
            }

            try (Reader reader = Files.newBufferedReader(savePath)) {
                NodeGraphData data = GSON.fromJson(reader, NodeGraphData.class);
                System.out.println("Node graph loaded successfully from: " + savePath);
                return data;
            }

        } catch (Exception e) {
            System.err.println("Failed to load node graph: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Convert loaded data back to Node objects
     */
    public static List<Node> convertToNodes(NodeGraphData data) {
        List<Node> nodes = new ArrayList<>();
        Map<String, Node> nodeMap = new HashMap<>();

        // Create nodes
        for (NodeGraphData.NodeData nodeData : data.getNodes()) {
            Node node = new Node(nodeData.getType(), nodeData.getX(), nodeData.getY());

            // Set the same ID using reflection
            try {
                java.lang.reflect.Field idField = Node.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(node, nodeData.getId());
            } catch (Exception e) {
                System.err.println("Failed to set node ID: " + e.getMessage());
            }

            // Save parameter values before setting mode (which will reinitialize parameters)
            Map<String, String> savedParamValues = new HashMap<>();
            if (nodeData.getParameters() != null) {
                for (NodeGraphData.ParameterData paramData : nodeData.getParameters()) {
                    savedParamValues.put(paramData.getName(), paramData.getValue());
                }
            }

            // Set the mode if it exists (this will reinitialize parameters)
            if (nodeData.getMode() != null) {
                node.setMode(nodeData.getMode());
            }

            // Restore saved parameter values (overwrite the default parameters with saved ones)
            if (!savedParamValues.isEmpty()) {
                for (NodeParameter param : node.getParameters()) {
                    String savedValue = savedParamValues.get(param.getName());
                    if (savedValue != null) {
                        param.setStringValue(savedValue);
                    }
                }
            }
            node.recalculateDimensions();

            nodes.add(node);
            nodeMap.put(nodeData.getId(), node);
        }

        for (NodeGraphData.NodeData nodeData : data.getNodes()) {
            if (nodeData.getAttachedSensorId() != null) {
                Node control = nodeMap.get(nodeData.getId());
                Node sensor = nodeMap.get(nodeData.getAttachedSensorId());
                if (control != null && sensor != null) {
                    control.attachSensor(sensor);
                }
            }
        }

        for (NodeGraphData.NodeData nodeData : data.getNodes()) {
            if (nodeData.getParentControlId() != null) {
                Node sensor = nodeMap.get(nodeData.getId());
                Node control = nodeMap.get(nodeData.getParentControlId());
                if (sensor != null && control != null && sensor.isSensorNode()) {
                    control.attachSensor(sensor);
                }
            }
        }

        for (NodeGraphData.NodeData nodeData : data.getNodes()) {
            if (nodeData.getAttachedActionId() != null) {
                Node control = nodeMap.get(nodeData.getId());
                Node child = nodeMap.get(nodeData.getAttachedActionId());
                if (control != null && child != null) {
                    control.attachActionNode(child);
                }
            }
        }

        for (NodeGraphData.NodeData nodeData : data.getNodes()) {
            if (nodeData.getParentActionControlId() != null) {
                Node child = nodeMap.get(nodeData.getId());
                Node control = nodeMap.get(nodeData.getParentActionControlId());
                if (child != null && control != null && control.canAcceptActionNode(child)) {
                    control.attachActionNode(child);
                }
            }
        }

        for (NodeGraphData.NodeData nodeData : data.getNodes()) {
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

        for (NodeGraphData.NodeData nodeData : data.getNodes()) {
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

        return nodes;
    }

    /**
     * Convert loaded data back to Connection objects
     */
    public static List<NodeConnection> convertToConnections(NodeGraphData data, Map<String, Node> nodeMap) {
        List<NodeConnection> connections = new ArrayList<>();

        for (NodeGraphData.ConnectionData connData : data.getConnections()) {
            Node outputNode = nodeMap.get(connData.getOutputNodeId());
            Node inputNode = nodeMap.get(connData.getInputNodeId());

            if (outputNode != null && inputNode != null) {
                if (outputNode.isSensorNode() || inputNode.isSensorNode()) {
                    continue;
                }
                NodeConnection connection = new NodeConnection(
                    outputNode,
                    inputNode,
                    connData.getOutputSocket(),
                    connData.getInputSocket()
                );
                connections.add(connection);
            } else {
                System.err.println("Failed to restore connection: missing node(s)");
            }
        }

        return connections;
    }

    /**
     * Get the save file path in the Minecraft saves directory
     */
    public static Path getDefaultSavePath() {
        return PresetManager.getPresetPath(PresetManager.getActivePreset());
    }

    /**
     * Check if a saved node graph exists
     */
    public static boolean hasSavedNodeGraph() {
        return hasSavedNodeGraph(PresetManager.getActivePreset());
    }

    public static boolean hasSavedNodeGraph(String presetName) {
        Path path = PresetManager.getPresetPath(presetName);
        if (Files.exists(path)) {
            return true;
        }
        String key = cacheKeyForPath(path);
        return key != null && IN_MEMORY_JSON_CACHE.containsKey(key);
    }

    private static void cachePresetGraph(String presetName, NodeGraphData data) {
        String key = cacheKeyForPreset(presetName);
        if (key != null && data != null) {
            IN_MEMORY_JSON_CACHE.put(key, GSON.toJson(data));
        }
    }

    private static String cacheKeyForPreset(String presetName) {
        return cacheKeyForPath(PresetManager.getPresetPath(presetName));
    }

    private static String cacheKeyForPath(Path path) {
        if (path == null) {
            return null;
        }
        Path fileName = path.getFileName();
        if (fileName == null) {
            return null;
        }
        String name = fileName.toString();
        if (name.endsWith(".json")) {
            name = name.substring(0, name.length() - 5);
        }
        return name.toLowerCase(Locale.ROOT);
    }

    private static NodeGraphData buildNodeGraphData(List<Node> nodes, List<NodeConnection> connections) {
        NodeGraphData data = new NodeGraphData();

        for (Node node : nodes) {
            NodeGraphData.NodeData nodeData = new NodeGraphData.NodeData();
            nodeData.setId(node.getId());
            nodeData.setType(node.getType());
            nodeData.setMode(node.getMode());
            nodeData.setX(node.getX());
            nodeData.setY(node.getY());

            List<NodeGraphData.ParameterData> paramDataList = new ArrayList<>();
            for (NodeParameter param : node.getParameters()) {
                NodeGraphData.ParameterData paramData = new NodeGraphData.ParameterData();
                paramData.setName(param.getName());
                paramData.setValue(param.getStringValue());
                paramData.setType(param.getType().name());
                paramDataList.add(paramData);
            }
            nodeData.setParameters(paramDataList);
            nodeData.setAttachedSensorId(node.getAttachedSensorId());
            nodeData.setParentControlId(node.getParentControlId());
            nodeData.setAttachedActionId(node.getAttachedActionId());
            nodeData.setParentActionControlId(node.getParentActionControlId());
            List<NodeGraphData.ParameterAttachmentData> attachmentData = new ArrayList<>();
            if (!node.getAttachedParameters().isEmpty()) {
                List<Integer> slotIndices = new ArrayList<>(node.getAttachedParameters().keySet());
                java.util.Collections.sort(slotIndices);
                for (Integer slotIndex : slotIndices) {
                    Node parameterNode = node.getAttachedParameter(slotIndex);
                    if (parameterNode != null) {
                        attachmentData.add(new NodeGraphData.ParameterAttachmentData(slotIndex, parameterNode.getId()));
                    }
                }
                if (!attachmentData.isEmpty()) {
                    nodeData.setAttachedParameterId(attachmentData.get(0).getParameterNodeId());
                } else {
                    nodeData.setAttachedParameterId(null);
                }
            } else {
                nodeData.setAttachedParameterId(null);
            }
            nodeData.setParameterAttachments(attachmentData);
            nodeData.setParentParameterHostId(node.getParentParameterHostId());

            data.getNodes().add(nodeData);
        }

        for (NodeConnection connection : connections) {
            if (connection.getOutputNode().isSensorNode() || connection.getInputNode().isSensorNode()) {
                continue;
            }
            NodeGraphData.ConnectionData connData = new NodeGraphData.ConnectionData();
            connData.setOutputNodeId(connection.getOutputNode().getId());
            connData.setInputNodeId(connection.getInputNode().getId());
            connData.setOutputSocket(connection.getOutputSocket());
            connData.setInputSocket(connection.getInputSocket());

            data.getConnections().add(connData);
        }

        return data;
    }

    private static boolean writeNodeGraphDataToPath(NodeGraphData data, Path savePath) {
        try {
            if (savePath.getParent() != null) {
                Files.createDirectories(savePath.getParent());
            }
            try (Writer writer = Files.newBufferedWriter(savePath)) {
                GSON.toJson(data, writer);
            }
            System.out.println("Node graph saved successfully to: " + savePath);
            return true;
        } catch (Exception e) {
            System.err.println("Failed to save node graph: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}

/**
 * Custom adapter for NodeType enum serialization
 */
class NodeTypeAdapter extends com.google.gson.TypeAdapter<NodeType> {
    @Override
    public void write(com.google.gson.stream.JsonWriter out, NodeType value) throws java.io.IOException {
        out.value(value.name());
    }

    @Override
    public NodeType read(com.google.gson.stream.JsonReader in) throws java.io.IOException {
        String name = in.nextString();
        try {
            if ("MINE".equals(name)) {
                return NodeType.COLLECT;
            }
            if ("CLOSE_INVENTORY".equals(name)) {
                return NodeType.CLOSE_GUI;
            }
            return NodeType.valueOf(name);
        } catch (IllegalArgumentException e) {
            // Handle unknown node types gracefully
            System.err.println("Unknown node type: " + name + ", skipping...");
            return null;
        }
    }
}

/**
 * Custom adapter for NodeMode enum serialization
 */
class NodeModeAdapter extends com.google.gson.TypeAdapter<com.pathmind.nodes.NodeMode> {
    @Override
    public void write(com.google.gson.stream.JsonWriter out, com.pathmind.nodes.NodeMode value) throws java.io.IOException {
        if (value == null) {
            out.nullValue();
            return;
        }
        out.value(value.name());
    }

    @Override
    public com.pathmind.nodes.NodeMode read(com.google.gson.stream.JsonReader in) throws java.io.IOException {
        if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
            in.nextNull();
            return null;
        }
        String name = in.nextString();
        try {
            if ("MINE_SINGLE".equals(name)) {
                return com.pathmind.nodes.NodeMode.COLLECT_SINGLE;
            }
            if ("MINE_MULTIPLE".equals(name)) {
                return com.pathmind.nodes.NodeMode.COLLECT_MULTIPLE;
            }
            return com.pathmind.nodes.NodeMode.valueOf(name);
        } catch (IllegalArgumentException e) {
            System.err.println("Unknown node mode: " + name + ", skipping...");
            return null;
        }
    }
}
