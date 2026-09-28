package ui;

import java.awt.BorderLayout;
import java.awt.GridLayout;

import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

public class PlantViewer extends JFrame {
    private JPanel floorplan;
    private JPanel robots;

    public PlantViewer() {
        this.setLayout(new BorderLayout());
        this.setTitle("Simple Plant Viewer");
        JPanel columns = new JPanel(new GridLayout(1, 2));
        add(columns, BorderLayout.CENTER);
        JPanel colPanel = new JPanel(new BorderLayout());
        colPanel.add(new JLabel("Zones"), BorderLayout.NORTH);
        colPanel.add(floorplan = new JPanel(), BorderLayout.CENTER);
        columns.add(colPanel);
        floorplan.setLayout(new BoxLayout(floorplan, BoxLayout.PAGE_AXIS));

        JPanel robPanel = new JPanel(new BorderLayout());
        robPanel.add(new JLabel("Robots"), BorderLayout.NORTH);
        robPanel.add(robots = new JPanel(), BorderLayout.CENTER);
        columns.add(robPanel);
        // columns.add(robots = new JPanel());
        // robots.setLayout(new BoxLayout(robots, BoxLayout.PAGE_AXIS));
        robots.setLayout(new GridLayout(1, 1));
        // robots.add(new JLabel("Robots"));
        setSize(400, 800);
        setVisible(true);
    }

    public ZonePanel addZone(String name, String type) {
        ZonePanel zPanel = new ZonePanel(this, name, type);
        floorplan.add(zPanel);
        // invalidate();
        return zPanel;
    }
    
    public RobotPanel addRobot(String name) {
        RobotPanel zPanel = new RobotPanel(this, name);
        robots.add(zPanel);
        robots.setLayout(new GridLayout(robots.getComponentCount(), 1));
        // invalidate();
        return zPanel;
    }
    
}
