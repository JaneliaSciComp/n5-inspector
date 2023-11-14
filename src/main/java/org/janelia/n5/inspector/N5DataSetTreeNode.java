package org.janelia.n5.inspector;

import bdv.util.BdvFunctions;
import bdv.util.BdvHandlePanel;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import net.imglib2.cache.img.CachedCellImg;
import org.fife.hex.swing.HexEditor;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class N5DataSetTreeNode extends N5TreeNode {

        private DatasetAttributes datasetAttributes;

        public N5DataSetTreeNode(String path, String label, N5Reader reader, DatasetAttributes datasetAttributes) {
            super(reader, path ,label);
            this.datasetAttributes = datasetAttributes;
        }


        public InputStream getInputStream(long[] gridPosition) {
            return null;
        }


        public List<EditorTab> getEditorTabs() {

            List<EditorTab> viewers = new ArrayList<>();

            try {
                // Initialize grid positions
                int numDimensions = datasetAttributes.getNumDimensions();
                long[] initialGridPosition = new long[numDimensions];
                for (int i = 0; i < numDimensions; i++) {
                    initialGridPosition[i] = 0;
                }

                // Create hex editor
                JPanel hexEditorControllerPanel = new JPanel();
                hexEditorControllerPanel.setLayout(new BoxLayout(hexEditorControllerPanel, BoxLayout.LINE_AXIS));
                JPanel hexEditorPanel = new JPanel();
                hexEditorPanel.setLayout(new BorderLayout());
                hexEditorPanel.add(hexEditorControllerPanel, BorderLayout.NORTH);

                HexEditor hexEditor = new HexEditor();
                InputStream inputStream = getInputStream(initialGridPosition);
                if (inputStream != null) {
                    hexEditor.open(inputStream);
                }
                hexEditorPanel.add(hexEditor, BorderLayout.CENTER);

                // Create a spinner control for each dimension
                SpinnerNumberModel[] models = new SpinnerNumberModel[numDimensions];
                for (int i = 0; i < numDimensions; i++) {
                    long dimension = datasetAttributes.getDimensions()[i];
                    int blockSize = datasetAttributes.getBlockSize()[i];
                    long numBlocks = dimension / blockSize;
                    SpinnerNumberModel blockModel = new SpinnerNumberModel(0, 0, numBlocks-1, 1);
                    models[i] = blockModel;
                    JSpinner spinner = new JSpinner(blockModel);
                    spinner.addChangeListener(e -> {
                        long[] gridPosition = new long[numDimensions];
                        for (int j = 0; j < numDimensions; j++) {
                            Double value = (Double) models[j].getValue();
                            gridPosition[j] = value.longValue();
                        }
                        SwingUtilities.invokeLater(() -> {
                            try {
                                InputStream inputStream2 = getInputStream(gridPosition);
                                if (inputStream2 != null) {
                                    hexEditor.open(inputStream2);
                                    hexEditor.updateUI();
                                }
                            } catch (IOException ex) {
                                throw new RuntimeException(ex);
                            }
                        });
                    });
                    hexEditorControllerPanel.add(spinner);
                }

                viewers.add(new EditorTab(
                        "Hex Editor",
                        "Show the content in hex",
                        hexEditorPanel));

                BdvOptions options = BdvOptions.options().frameTitle("N5 Viewer");
                BdvHandlePanel bdvHandle = new BdvHandlePanel(null, options);
                CachedCellImg<?, ?> ts = N5Utils.openVolatile(getReader(), getPath());
                // We use the addTo option so as to not trigger a new window being opened
                BdvStackSource<?> show = BdvFunctions.show(ts, getPath(), BdvOptions.options().addTo(bdvHandle));
                viewers.add(new EditorTab(
                        "BigDataViewer",
                        "Displays the data set in a BDV",
                        show.getBdvHandle().getViewerPanel()));

            }
            catch (Exception ex) {
                throw new RuntimeException(ex);
            }

            return viewers;
        }
    }