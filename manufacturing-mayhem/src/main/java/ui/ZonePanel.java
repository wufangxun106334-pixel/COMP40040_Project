package ui;

import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;

public class ZonePanel extends JPanel {
    private JTextField available = new JTextField("empty", 20);
    private JTextField status = new JTextField("idle", 20);
    private PlantViewer viewer;
    private JPanel heading;
    private JPanel base;

    public ZonePanel(PlantViewer viewer, String name, String type) {
        this.viewer = viewer;
        this.setPreferredSize(new Dimension(200, 90));

        setLayout(new BorderLayout());

        heading = new JPanel();
        heading.add(new JLabel(name + " ["+type+"]"));
        add(heading, BorderLayout.NORTH);

        base = new JPanel();
        base.setLayout(new GridLayout(2, 2));
        base.add(new JLabel("Occupied:"));
        base.add(available);
        base.add(new JLabel("Status:"));
        base.add(status);
        add(base, BorderLayout.CENTER);

        this.viewer.revalidate();
    }

    public void setAvailable(String item) {
        available.setText(item);
    }
    
    public void setStatus(String status) {
        this.status.setText(status);
    }

    private Map<String, JTextField> customFields = new HashMap<>();
    
    public void addCustomField(String name, String initial) {
        JTextField custom = new JTextField(initial, 20);
        customFields.put(name, custom);
        base.add(new JLabel(name));
        base.add(custom);
        base.setLayout(new GridLayout(2+customFields.size(), 1));
        
        this.setPreferredSize(new Dimension(200, 30*(3+customFields.size())));
    }

    public void setCustomField(String custom, String value) {
        customFields.get(custom).setText(value);
    }
    
}
