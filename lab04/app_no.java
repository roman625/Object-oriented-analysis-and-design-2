import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import java.util.List;

public class SpaceTraderApp {
    public static class Planet {
        private final String name;
        private final int x;
        private final int y;
        private final int price;
        private final Color color;

        public Planet(String name, int x, int y, int price, Color color) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.price = price;
            this.color = color;
        }

        public String getName() { return name; }
        public int getX() { return x; }
        public int getY() { return y; }
        public int getPrice() { return price; }
        public Color getColor() { return color; }
    }

    public static class Ship {
        private static Ship instance;
        
        private double fuel = 100;
        private BigDecimal wallet = new BigDecimal("1000.00"); // BigDecimal напрямую (без Currency)
        private int cargoCount = 0;
        private Planet currentPlanet;

        private static final double FUEL_RATE = 0.75;
        private static final int MAX_FUEL = 100;
        private static final BigDecimal LITER_PRICE = new BigDecimal("2.00");

        private Ship() {}

        public static Ship getInstance() {
            if (instance == null) instance = new Ship();
            return instance;
        }

        public double getFuel() { return fuel; }
        public BigDecimal getWallet() { return wallet; }
        public int getCargoCount() { return cargoCount; }
        public Planet getCurrentPlanet() { return currentPlanet; }
        public void setCurrentPlanet(Planet p) { this.currentPlanet = p; }

        public boolean flyTo(Planet target) {
            if (target.equals(currentPlanet)) return false;
            
            // Расчет дистанции напрямую через координаты (без Position)
            double dist = Math.sqrt(Math.pow(currentPlanet.getX() - target.getX(), 2) + 
                                    Math.pow(currentPlanet.getY() - target.getY(), 2));
            double cost = dist * FUEL_RATE;
            
            if (fuel >= cost) {
                fuel -= cost;
                currentPlanet = target;
                return true;
            }
            return false;
        }

        public void refuel() {
            BigDecimal needed = BigDecimal.valueOf(MAX_FUEL - fuel);
            BigDecimal cost = needed.multiply(LITER_PRICE);
            // Прямое сравнение BigDecimal (без Currency.isGreaterThanOrEqual)
            if (wallet.compareTo(cost) >= 0 && needed.doubleValue() > 0) {
                wallet = wallet.subtract(cost);
                fuel = MAX_FUEL;
            }
        }

        public void buyCargo() {
            BigDecimal price = BigDecimal.valueOf(currentPlanet.getPrice());
            if (wallet.compareTo(price) >= 0) {
                wallet = wallet.subtract(price);
                cargoCount++;
            }
        }

        public void sellCargo() {
            if (cargoCount > 0) {
                wallet = wallet.add(BigDecimal.valueOf(currentPlanet.getPrice()));
                cargoCount--;
            }
        }
    }

    private static final BigDecimal WIN_GOAL = new BigDecimal("5000.00");
    private static List<Planet> planets = new ArrayList<>();
    private static DefaultTableModel priceModel;
    private static JLabel statsLabel;
    private static JProgressBar fuelBar;
    private static MapPanel radar;

    public static void main(String[] args) throws Exception {
        initDB();
        planets = loadPlanets();
        
        Ship ship = Ship.getInstance();
        ship.setCurrentPlanet(planets.get(0));
        
        SwingUtilities.invokeLater(SpaceTraderApp::gui);
    }

    static void initDB() throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:h2:./game_db", "sa", "")) {
            Statement s = c.createStatement();
            s.execute("DROP TABLE IF EXISTS planets");
            s.execute("CREATE TABLE planets(name VARCHAR(50), x INT, y INT, price INT, r INT, g INT, b INT)");
            s.execute("INSERT INTO planets VALUES " +
                      "('Earth', 0, 0, 100, 0, 150, 255), " +
                      "('Mars', 45, 30, 160, 255, 80, 0), " +
                      "('Jupiter', 80, -30, 310, 255, 180, 100), " +
                      "('Saturn', 15, 85, 250, 220, 220, 100), " +
                      "('Pluto', 125, 120, 650, 180, 180, 200)");
        }
    }

    static List<Planet> loadPlanets() throws Exception {
        List<Planet> l = new ArrayList<>();
        try (Connection c = DriverManager.getConnection("jdbc:h2:./game_db", "sa", "")) {
            ResultSet rs = c.createStatement().executeQuery("SELECT * FROM planets");
            while(rs.next()) {
                l.add(new Planet(rs.getString(1), rs.getInt(2), rs.getInt(3), rs.getInt(4), 
                      new Color(rs.getInt(5), rs.getInt(6), rs.getInt(7))));
            }
        }
        return l;
    }

    static void gui() {
        JFrame f = new JFrame("STELLAR TERMINAL v12.0 - NO VALUE OBJECTS");
        f.setSize(1200, 850);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.getContentPane().setBackground(new Color(5, 5, 10));
        f.setLayout(new BorderLayout(10, 10));

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.setBorder(new EmptyBorder(10, 20, 10, 20));
        statsLabel = new JLabel();
        statsLabel.setFont(new Font("Monospaced", Font.BOLD, 22));
        statsLabel.setForeground(Color.CYAN);
        fuelBar = new JProgressBar(0, 100);
        fuelBar.setPreferredSize(new Dimension(300, 25));
        fuelBar.setStringPainted(true);
        top.add(statsLabel, BorderLayout.WEST);
        top.add(fuelBar, BorderLayout.EAST);
        f.add(top, BorderLayout.NORTH);

        radar = new MapPanel();
        f.add(radar, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setPreferredSize(new Dimension(0, 320));
        bottom.setOpaque(false);

        priceModel = new DefaultTableModel(new String[]{"STATION DATA", "EARTH", "MARS", "JUPITER", "SATURN", "PLUTO"}, 0);
        JTable priceTable = new JTable(priceModel);
        priceTable.setRowHeight(45);
        bottom.add(new JScrollPane(priceTable), BorderLayout.CENTER);

        JPanel control = new JPanel(new GridLayout(planets.size() + 3, 1, 5, 5));
        control.setPreferredSize(new Dimension(250, 0));
        control.setOpaque(false);
        
        Ship ship = Ship.getInstance();

        for (Planet p : planets) {
            JButton b = new JButton("FLY TO " + p.getName().toUpperCase());
            b.addActionListener(e -> {
                if (ship.flyTo(p)) refresh();
                else if (!p.equals(ship.getCurrentPlanet())) 
                    JOptionPane.showMessageDialog(null, "OUT OF FUEL RANGE!");
            });
            control.add(b);
        }
        
        JButton refuelBtn = new JButton("REFUEL SHIP");
        refuelBtn.setBackground(new Color(50, 50, 150)); refuelBtn.setForeground(Color.WHITE);
        refuelBtn.addActionListener(e -> { ship.refuel(); refresh(); });

        JButton buyBtn = new JButton("BUY CARGO");
        buyBtn.setBackground(new Color(0, 80, 0)); buyBtn.setForeground(Color.WHITE);
        buyBtn.addActionListener(e -> { ship.buyCargo(); refresh(); });
        
        JButton sellBtn = new JButton("SELL CARGO");
        sellBtn.setBackground(new Color(80, 0, 0)); sellBtn.setForeground(Color.WHITE);
        sellBtn.addActionListener(e -> {
            ship.sellCargo();
            refresh();
            if (ship.getWallet().compareTo(WIN_GOAL) >= 0) {
                JOptionPane.showMessageDialog(null, "VICTORY! Balance: " + ship.getWallet() + " cr");
                System.exit(0);
            }
        });
        
        control.add(refuelBtn); control.add(buyBtn); control.add(sellBtn);
        bottom.add(control, BorderLayout.EAST);
        f.add(bottom, BorderLayout.SOUTH);

        refresh();
        f.setLocationRelativeTo(null);
        f.setVisible(true);
    }

    static void refresh() {
        Ship ship = Ship.getInstance();
        statsLabel.setText(String.format("CASH: %.2f cr | CARGO: %d | AT: %s", 
                ship.getWallet(), ship.getCargoCount(), ship.getCurrentPlanet().getName()));
        fuelBar.setValue((int)ship.getFuel());
        
        priceModel.setRowCount(0);
        Object[] r1 = new Object[6]; r1[0] = "MARKET PRICE";
        for(int i=0; i<5; i++) r1[i+1] = planets.get(i).getPrice() + " cr";
        priceModel.addRow(r1);

        Object[] r2 = new Object[6]; r2[0] = "FUEL TO REACH";
        for(int i=0; i<5; i++) {
            Planet p = planets.get(i);
            // Дублирование формулы расчета дистанции без Position
            double d = Math.sqrt(Math.pow(ship.getCurrentPlanet().getX() - p.getX(), 2) + 
                                 Math.pow(ship.getCurrentPlanet().getY() - p.getY(), 2));
            r2[i+1] = (int)(d * 0.75) + " L";
        }
        priceModel.addRow(r2);
        radar.repaint();
    }

    static class MapPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(5, 5, 15)); g2.fillRect(0, 0, getWidth(), getHeight());

            Ship ship = Ship.getInstance();
            double scale = Math.min(getWidth(), getHeight()) / 400.0;
            int mx = getWidth()/2 - 100; int my = getHeight()/2 + 100;

            for (Planet p : planets) {
                int x = mx + (int)(p.getX() * scale); 
                int y = my - (int)(p.getY() * scale);
                g2.setColor(p.getColor()); g2.fillOval(x-12, y-12, 24, 24);
                g2.setColor(Color.WHITE); g2.drawString(p.getName(), x+15, y+5);
            }

            if (ship.getCurrentPlanet() != null) {
                int sx = mx + (int)(ship.getCurrentPlanet().getX() * scale); 
                int sy = my - (int)(ship.getCurrentPlanet().getY() * scale);
                g2.setColor(Color.GREEN); g2.setStroke(new BasicStroke(2));
                g2.drawRect(sx-18, sy-18, 36, 36);
            }
        }
    }
}
