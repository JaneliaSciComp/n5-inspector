package org.janelia.n5.inspector;

import com.google.gson.*;
import net.thisptr.jackson.jq.internal.misc.Lists;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.janelia.saalfeldlab.n5.*;
import org.janelia.saalfeldlab.n5.universe.N5Factory;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * A viewer for inspecting details of N5-compatible containers including OME-Zarr, HDF5, and N5.
 *
 * Inspired by "HDF View".
 *
 * @author Konrad Rokicki
 */
public class N5Inspector extends JFrame {

    public static final boolean DEBUG = false;

    private String defaultPath = "https://uk1s3.embassy.ebi.ac.uk/idr/zarr/v0.4/idr0062A/6001240.zarr";

    private JTextField pathField;
    private DefaultTreeModel treeModel;
    private JTree tree;
    private JTabbedPane tabbedPane;

    N5Inspector() {
        initUI();
    }

    private void initUI() {

        pathField = new JTextField(40);
        pathField.setText(defaultPath);

        JButton openButton = new JButton("Open");
        openButton.addActionListener(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String path = pathField.getText();
                if (StringUtils.isBlank(path)) return;
                open(path);
            }
        });

        setTitle("N5 Data View");
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel inputPanel = new JPanel();
        inputPanel.setLayout(new BoxLayout(inputPanel, BoxLayout.LINE_AXIS));
        inputPanel.add(pathField);
        inputPanel.add(openButton);

        Container contentPane = getContentPane();
        contentPane.setLayout(new BorderLayout());

        contentPane.add(inputPanel, BorderLayout.NORTH);

        // Start with an empty tree and hide the root node
        DefaultMutableTreeNode root = new DefaultMutableTreeNode(null);
        treeModel = new DefaultTreeModel(root);
        tree = new JTree(treeModel);
        tree.setShowsRootHandles(true);
        tree.setRootVisible(false);
        tree.getSelectionModel().addTreeSelectionListener(this::handleTreeNodeSelected);

        JScrollPane treeScrollPane = new JScrollPane(tree);
        treeScrollPane.setMinimumSize(new Dimension(200,200));

        tabbedPane = new JTabbedPane();
        tabbedPane.setMinimumSize(new Dimension(200,200));

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treeScrollPane, tabbedPane);
        splitPane.setResizeWeight(0.3);
        splitPane.setOneTouchExpandable(true);
        splitPane.setContinuousLayout(true);

        contentPane.add(splitPane, BorderLayout.CENTER);

        pack();

        // Maximize the window after packing
        setExtendedState(JFrame.MAXIMIZED_BOTH);
    }

    private void open(String pathStr) {
        Path path = Paths.get(pathStr);
        N5Factory n5Factory = new N5Factory()
                .gsonBuilder(new GsonBuilder().setPrettyPrinting())
                .zarrMapN5Attributes(false)
                .zarrMergeAttributes(false);

        SwingWorker<N5Reader, Void> worker = new SwingWorker<N5Reader, Void>() {
            @Override
            public N5Reader doInBackground() {
                return n5Factory.openReader(pathStr);
            }
            @Override
            public void done() {
                try {
                    N5Reader reader = get();
                    N5TreeNode n5TreeNode = new N5TreeNode(reader, "/", path.getFileName().toString());
                    DefaultMutableTreeNode root = n5TreeNode.wrap();
                    addChildren(reader, root, "", "");
                    addMetadata(reader, root, "");
                    tree.setRootVisible(true);
                    treeModel.setRoot(root);
                    setTreeExpandedState(tree, true);
                }
                catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
                finally {
                    setCursor(Cursor.getDefaultCursor());
                }
            }
        };

        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        worker.execute();
    }

    private void addChildren(N5Reader reader, DefaultMutableTreeNode parentNode, String path, String indent) {

        if (DEBUG) System.out.println(indent+"addChildren "+path);

        List<String> childNames = Arrays.asList(reader.list(path));
        Collections.sort(childNames);

        for (String childName : childNames) {
            String childPath = path+"/"+childName;

            if (reader.datasetExists(childPath)) {
                DatasetAttributes datasetAttributes = reader.getDatasetAttributes(childPath);
                if (DEBUG) System.out.println(indent + " - " + childPath + " (data set) " + datasetAttributes);

                if (datasetAttributes != null) {
                    N5DataSetTreeNode n5TreeNode = new N5DataSetTreeNode(childPath, childName, reader, datasetAttributes) {
                        public InputStream getInputStream(long[] gridPosition) {
                            DataBlock<?> dataBlock = reader.readBlock(
                                    childPath, datasetAttributes, gridPosition);
                            String posStr = StringUtils.join(ArrayUtils.toObject(gridPosition), ",");
                            if (dataBlock == null) {
                                if (DEBUG) System.out.println("No content at path=" + childPath + " pos=" + posStr);
                            } else {
                                if (DEBUG) System.out.println("Show content at path=" + childPath + " pos=" + posStr);
                            }
                            return dataBlock == null ? null : new ByteBufferBackedInputStream(dataBlock.toByteBuffer());
                        }
                    };
                    DefaultMutableTreeNode childNode = n5TreeNode.wrap();
                    parentNode.add(childNode);
                    addDataSetAttributeNodes(reader, childNode, childPath, datasetAttributes);
                    addMetadata(reader, childNode, childPath);
                }
                else {
                    throw new IllegalStateException("Data set without attributes: "+childPath);
                }
            }
            else {
                if (DEBUG) System.out.println(indent+" - "+childPath+" (group)");

                N5TreeNode n5TreeNode = new N5TreeNode(reader, childPath, childName);
                DefaultMutableTreeNode childNode = n5TreeNode.wrap();
                parentNode.add(childNode);

                addChildren(reader, childNode, childPath, indent+"  ");
                addMetadata(reader, childNode, childPath);
            }
        }
    }

    private void addDataSetAttributeNodes(N5Reader reader, DefaultMutableTreeNode parentNode,
                                          String path, DatasetAttributes datasetAttributes) {

        {
            DataType dataType = datasetAttributes.getDataType();
            N5TreeNode n5TreeNode = new N5TreeNode(reader, path+"#dataType","Data type: " + dataType.toString());
            DefaultMutableTreeNode childNode = n5TreeNode.wrap();
            parentNode.add(childNode);
        }

        {
            Compression compression = datasetAttributes.getCompression();
            StringBuilder compressionSb = new StringBuilder("Compression: ");
            if (reader instanceof GsonN5Reader) {
                GsonN5Reader gsonN5Reader = (GsonN5Reader) reader;
                JsonElement jsonElement = gsonN5Reader.getGson().toJsonTree(compression);
                compressionSb.append(jsonElement);
            }
            else {
                compressionSb.append(compression.getType());
            }
            N5TreeNode n5TreeNode = new N5TreeNode(reader, path+"#compression", compressionSb.toString());
            DefaultMutableTreeNode childNode = n5TreeNode.wrap();
            parentNode.add(childNode);
        }

        {
            long[] dimensions = datasetAttributes.getDimensions();
            String dimensionsStr = Arrays.stream(dimensions)
                    .mapToObj(String::valueOf)
                    .collect(Collectors.joining(" x "));
            N5TreeNode n5TreeNode = new N5TreeNode(reader,path+"#dimensions", "Dimensions: " + dimensionsStr);
            DefaultMutableTreeNode childNode = n5TreeNode.wrap();
            parentNode.add(childNode);
        }

        {
            int[] blockSize = datasetAttributes.getBlockSize();
            String blockSizeStr = Arrays.stream(blockSize)
                    .mapToObj(String::valueOf)
                    .collect(Collectors.joining(" x "));
            N5TreeNode n5TreeNode = new N5TreeNode(reader, path+"#blockSize", "Block size: " + blockSizeStr);
            DefaultMutableTreeNode childNode = n5TreeNode.wrap();
            parentNode.add(childNode);
        }
    }

    private void addMetadata(N5Reader reader, DefaultMutableTreeNode parentNode, String path) {
        if (reader instanceof GsonN5Reader) {
            GsonN5Reader gsonN5Reader = (GsonN5Reader) reader;
            JsonElement attributes = gsonN5Reader.getAttributes(path);
            if (attributes != null) {
                JsonObject attrObject = attributes.getAsJsonObject();
                addMetadata(reader, parentNode, attrObject);
            }
        }
    }

    private void addMetadata(N5Reader reader, DefaultMutableTreeNode parentNode, JsonObject attrObject) {

        List<String> childNames = Lists.newArrayList(attrObject.keySet());
        Collections.sort(childNames);

        for (String key : childNames) {
            JsonElement jsonElement = attrObject.get(key);
            addMetadata(reader, parentNode, key, jsonElement);
        }
    }

    private String getPrimitiveCSV(JsonArray jsonArray) {
        StringBuilder builder = new StringBuilder();
        for (JsonElement childElement : jsonArray.asList()) {
            if (childElement.isJsonPrimitive()) {
                JsonPrimitive jsonPrimitive = childElement.getAsJsonPrimitive();
                if (builder.length()>0) builder.append(",");
                builder.append(jsonPrimitive.toString());
            }
            else {
                return null;
            }
        }
        return builder.toString();
    }
    private void addMetadata(N5Reader reader, DefaultMutableTreeNode parentNode, String key, JsonElement jsonElement) {

        N5TreeNode parentUserObject = (N5TreeNode)parentNode.getUserObject();

        if (jsonElement.isJsonArray()) {
            JsonArray jsonArray = jsonElement.getAsJsonArray();
            String primitiveCSV = getPrimitiveCSV(jsonArray);

            if (primitiveCSV == null) {
                // There are some non-primitive children, so we need to show all the array members as nodes
                N5TreeNode n5TreeNode = new N5MetadataTreeNode(reader, parentUserObject.getPath(), key, jsonArray);
                DefaultMutableTreeNode childNode = n5TreeNode.wrap();
                parentNode.add(childNode);
                int i = 0;
                for (JsonElement childElement : jsonArray.asList()) {
                    addMetadata(reader, childNode, key+"["+i+"]", childElement);
                    i++;
                }
            }
            else {
                // Show all primitive members as a single node using CSV format
                String label = String.format("%s = %s", key, primitiveCSV);
                N5TreeNode n5TreeNode = new N5MetadataTreeNode(reader, parentUserObject.getPath(), label, jsonArray);
                DefaultMutableTreeNode childNode = n5TreeNode.wrap();
                parentNode.add(childNode);
            }
        }
        else if (jsonElement.isJsonObject()) {
            JsonObject childObject = jsonElement.getAsJsonObject();
            N5TreeNode n5TreeNode = new N5MetadataTreeNode(reader, parentUserObject.getPath(), key, childObject);
            DefaultMutableTreeNode childNode = n5TreeNode.wrap();
            parentNode.add(childNode);
            addMetadata(reader, childNode, childObject);
        }
        else if (jsonElement.isJsonPrimitive()) {
            JsonPrimitive jsonPrimitive = jsonElement.getAsJsonPrimitive();
            String nodeName = key == null ? jsonPrimitive.toString() : String.format("%s = %s", key, jsonPrimitive);
            N5TreeNode n5TreeNode = new N5MetadataTreeNode(reader, parentUserObject.getPath(), nodeName, jsonElement);
            parentNode.add(n5TreeNode.wrap());
        }
    }

    private void handleTreeNodeSelected(TreeSelectionEvent e) {

        DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
        if (node == null) return;

        // Remove all current tabs
        tabbedPane.removeAll();;

        // Repopulate with new tabs
        N5TreeNode n5TreeNode = (N5TreeNode) node.getUserObject();
        for (EditorTab editorTab : n5TreeNode.getEditorTabs()) {
            tabbedPane.addTab(editorTab.getTitle(), editorTab.getIcon(),
                    editorTab.getComponent(), editorTab.getTip());
        }

        tabbedPane.updateUI();
    }

    // From https://www.logicbig.com/tutorials/java-swing/jtree-expand-collapse-all-nodes.html
    public static void setTreeExpandedState(JTree tree, boolean expanded) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getModel().getRoot();
        setNodeExpandedState(tree, node, expanded);
    }

    public static void setNodeExpandedState(JTree tree, DefaultMutableTreeNode node, boolean expanded) {
        ArrayList<DefaultMutableTreeNode> list = Collections.list(node.children());
        for (DefaultMutableTreeNode treeNode : list) {
            setNodeExpandedState(tree, treeNode, expanded);
        }
        if (!expanded && node.isRoot()) {
            return;
        }
        TreePath path = new TreePath(node.getPath());
        if (expanded) {
            tree.expandPath(path);
        } else {
            tree.collapsePath(path);
        }
    }

    public static void main(String[] args) {
        EventQueue.invokeLater(() -> {
            N5Inspector n5View = new N5Inspector();
            n5View.setVisible(true);
        });
    }
}
