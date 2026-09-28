package ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

public class RobotPanel extends JPanel {
    private Map<String, JTextField> fields = new HashMap<>();
    private PlantViewer viewer;
    private JPanel heading;
    private JPanel base;

    public RobotPanel(PlantViewer viewer, String name) {
        this.viewer = viewer;
        this.setPreferredSize(new Dimension(200, 90));

        setLayout(new BorderLayout());

        heading = new JPanel();
        heading.add(new JLabel("Robot: " + name));
        add(heading, BorderLayout.NORTH);

        base = new JPanel();
        addField("at","Location:", "none");
        addField("status","Status:", "idle");
        addField("holding","Holding:", "none");
        addField("energy","Energy Level:", "");
        base.setLayout(new GridLayout(base.getComponentCount()/2, 2));
        add(base, BorderLayout.CENTER);

        this.viewer.revalidate();
    }

    public void addField(String key, String name, String value) {
        JTextField textField = new JTextField(value);
        base.add(new JLabel(name));
        base.add(textField);
        fields.put(key, textField);

    }

    public void setField(String key, String value) {
        fields.get(key).setText(value);
    }
}
