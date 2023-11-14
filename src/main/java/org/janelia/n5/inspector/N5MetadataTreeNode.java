package org.janelia.n5.inspector;

import com.google.gson.JsonElement;
import org.janelia.saalfeldlab.n5.GsonN5Reader;
import org.janelia.saalfeldlab.n5.N5Reader;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

public class N5MetadataTreeNode extends N5TreeNode {

        private JsonElement jsonElement;

        public N5MetadataTreeNode(N5Reader reader, String path, String label, JsonElement jsonElement) {
            super(reader, path, label);
            this.jsonElement = jsonElement;
        }

        public List<EditorTab> getEditorTabs() {
            if (reader instanceof GsonN5Reader) {
                GsonN5Reader gsonN5Reader = (GsonN5Reader) reader;
                String jsonText = gsonN5Reader.getGson().toJson(jsonElement);
                JTextArea textArea = new JTextArea();
                textArea.setEditable(false);
                textArea.setText(jsonText);
                JScrollPane scrollPane = new JScrollPane(textArea);
                return Collections.singletonList(new EditorTab("JSON", "Displays the object as JSON", scrollPane));
            }

            return Collections.emptyList();
        }
    }