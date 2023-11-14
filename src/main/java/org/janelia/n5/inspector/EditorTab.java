package org.janelia.n5.inspector;

import javax.swing.*;

public class EditorTab {

        private final String title;
        private final String tip;
        private final Icon icon;
        private final JComponent component;

        public EditorTab(String title, String tip, JComponent component) {
            this(title, tip, null, component);
        }
        public EditorTab(String title, String tip, Icon icon, JComponent component) {
            this.title = title;
            this.tip = tip;
            this.icon = icon;
            this.component = component;
        }

        public String getTitle() {
            return title;
        }

        public String getTip() {
            return tip;
        }

        public Icon getIcon() {
            return icon;
        }

        public JComponent getComponent() {
            return component;
        }
    }