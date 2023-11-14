package org.janelia.n5.inspector;

import org.janelia.saalfeldlab.n5.N5Reader;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.Collections;
import java.util.List;

public class N5TreeNode {

        protected final String label;
        protected final String path;
        protected final N5Reader reader;

        public N5TreeNode(N5Reader reader, String path, String label) {
            this.reader = reader;
            this.path = path;
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        public N5Reader getReader() {
            return reader;
        }

        public String getPath() {
            return path;
        }

        public List<EditorTab> getEditorTabs() {
            return Collections.emptyList();
        }

        @Override
        public String toString() {
            return label;
        }

        public DefaultMutableTreeNode wrap() {
            return new DefaultMutableTreeNode(this);
        }
    }