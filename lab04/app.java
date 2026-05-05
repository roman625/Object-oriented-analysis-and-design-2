import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.util.*;
import java.util.List;

public class SpaceTraderApp {
    
    public record Position(int x, int y) {
        public double distanceTo(Position other) {
            return Math.sqrt(Math.pow(x - other.x, 2) + Math.pow(y - other.y, 2));
        }
    }

    public record Currency(BigDecimal amount) {
        public Currency add(BigDecimal other) { return new Currency(amount.add(other)); }
        public Currency subtract(BigDecimal other) { return new Currency(amount.subtract(other)); }
        public boolean isGreaterThanOrEqual(BigDecimal other) { return amount.compareTo(other) >= 0; }
        @Override public String toString() { return String.format("%.2f cr", amount); }
    }

    public record Planet(String name, Position pos, int price, Color color) {}

    public static class Ship {
        private static final Ship INSTANCE = new Ship();
        
        private static final double FUEL_RATE = 0.75;
        private static final int MAX_FUEL = 100;
        private static final BigDecimal WIN_GOAL = new BigDecimal("5000.00");
        private static final BigDecimal LITER_PRICE = new BigDecimal("2.00");
        
        private double fuel;
        private Currency wallet;
        private int cargoCount;
        
        private Ship() {
            this.fuel = MAX_FUEL;
            this.wallet = new Currency(new BigDecimal("1000.00"));
            this.cargoCount = 0;
        }
        
        public static Ship getInstance() {
            return INSTANCE;
        }
        
        public double getFuel() { return fuel; }
        public Currency getWallet() { return wallet; }
        public int getCargoCount() { return cargoCount; }
        public int getMaxFuel() { return MAX_FUEL; }
        public BigDecimal getWinGoal() { return WIN_GOAL; }
        public BigDecimal getLiterPrice() { return LITER_PRICE; }
        public double getFuelRate() { return FUEL_RATE; }
        
        public void setFuel(double fuel) { this.fuel = fuel; }
        public void setWallet(Currency wallet) { this.wallet = wallet; }
        public void setCargoCount(int cargoCount) { this.cargoCount = cargoCount; }
        
        public void addCargo() { cargoCount++; }
        public void removeCargo() { if (cargoCount > 0) cargoCount--; }
        public void spendFuel(double amount) { this.fuel -= amount; }
        public boolean hasEnoughFuel(double amount) { return fuel >= amount; }
        public boolean canAfford(BigDecimal cost) { return wallet.isGreaterThanOrEqual(cost); }
        
        public void refuel(BigDecimal cost) {
            wallet = wallet.subtract(cost);
            fuel = MAX_FUEL;
        }
        
        public boolean buyCargo(Planet planet) {
            BigDecimal price = BigDecimal.valueOf(planet.price);
            if (canAfford(price)) {
                wallet = wallet.subtract(price);
                addCargo();
                return true;
            }
            return false;
        }
        
        public boolean sellCargo(Planet planet) {
            if (cargoCount > 0) {
                wallet = wallet.add(BigDecimal.valueOf(planet.price));
                removeCargo();
                return true;
            }
            return false;
        }
        
        public boolean checkVictory() {
            return wallet.isGreaterThanOrEqual(WIN_GOAL);
        }
    }

    private static List<Planet> planets = new ArrayList<>();
    private static DefaultTableModel priceModel;
    private static JLabel statsLabel;
    private static JProgressBar fuelBar;
    private static MapPanel radar;
    private static Planet current;

    public static void main(String[] args) throws Exception {
        initDB();
        planets = loadPlanets();
        current = planets.get(0);
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
                Position p = new Position(rs.getInt(2), rs.getInt(3));
                l.add(new Planet(rs.getString(1), p, rs.getInt(4), 
                      new Color(rs.getInt(5), rs.getInt(6), rs.getInt(7))));
            }
        }
        return l;
    }

    static void gui() {
        JFrame f = new JFrame("STELLAR TERMINAL v11.0 - SINGLETON EDITION");
        f.setSize(1200, 850);
        f.setDefaultCloseOperation(3);
        f.getContentPane().setBackground(new Color(5, 5, 10));
        f.setLayout(new BorderLayout(10, 10));

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.setBorder(new EmptyBorder(10, 20, 10, 20));
        statsLabel = new JLabel();
        statsLabel.setFont(new Font("Monospaced", Font.BOLD, 22));
        statsLabel.setForeground(Color.CYAN);
        fuelBar = new JProgressBar(0, Ship.getInstance().getMaxFuel());
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
        
        for (Planet p : planets) {
            JButton b = new JButton("FLY TO " + p.name.toUpperCase());
            b.addActionListener(e -> jump(p));
            control.add(b);
        }
        
        JButton refuelBtn = new JButton("REFUEL SHIP");
        refuelBtn.setBackground(new Color(50, 50, 150)); refuelBtn.setForeground(Color.WHITE);
        refuelBtn.addActionListener(e -> {
            Ship ship = Ship.getInstance();
            double needed = ship.getMaxFuel() - ship.getFuel();
            BigDecimal cost = BigDecimal.valueOf(needed).multiply(ship.getLiterPrice());
            if (ship.canAfford(cost) && needed > 0) {
                ship.refuel(cost);
                refresh();
            }
        });

        JButton buyBtn = new JButton("BUY CARGO");
        buyBtn.setBackground(new Color(0, 80, 0)); buyBtn.setForeground(Color.WHITE);
        buyBtn.addActionListener(e -> {
            Ship ship = Ship.getInstance();
            if (ship.buyCargo(current)) {
                refresh();
            }
        });
        
        JButton sellBtn = new JButton("SELL CARGO");
        sellBtn.setBackground(new Color(80, 0, 0)); sellBtn.setForeground(Color.WHITE);
        sellBtn.addActionListener(e -> {
            Ship ship = Ship.getInstance();
            if (ship.sellCargo(current)) {
                refresh();
                if (ship.checkVictory()) {
                    JOptionPane.showMessageDialog(null, "VICTORY! Balance: " + ship.getWallet());
                    System.exit(0);
                }
            }
        });
        
        control.add(refuelBtn); control.add(buyBtn); control.add(sellBtn);
        bottom.add(control, BorderLayout.EAST);
        f.add(bottom, BorderLayout.SOUTH);

        refresh();
        f.setLocationRelativeTo(null);
        f.setVisible(true);
    }

    static void jump(Planet p) {
        if (p.equals(current)) return;
        Ship ship = Ship.getInstance();
        double d = current.pos.distanceTo(p.pos);
        double cost = d * ship.getFuelRate();
        if (ship.hasEnoughFuel(cost)) {
            ship.spendFuel(cost);
            current = p;
            refresh();
        } else {
            JOptionPane.showMessageDialog(null, "OUT OF FUEL RANGE!");
        }
    }

    static void refresh() {
        Ship ship = Ship.getInstance();
        statsLabel.setText(String.format("CASH: %s | CARGO: %d | AT: %s", 
            ship.getWallet(), ship.getCargoCount(), current.name));
        fuelBar.setValue((int)ship.getFuel());
        
        priceModel.setRowCount(0);
        Object[] r1 = new Object[6]; r1[0] = "MARKET PRICE";
        for(int i=0; i<5; i++) r1[i+1] = planets.get(i).price + " cr";
        priceModel.addRow(r1);

        Object[] r2 = new Object[6]; r2[0] = "FUEL TO REACH";
        for(int i=0; i<5; i++) {
            double c = current.pos.distanceTo(planets.get(i).pos) * Ship.getInstance().getFuelRate();
            r2[i+1] = (int)c + " L";
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

            double scale = Math.min(getWidth(), getHeight()) / 400.0;
            int mx = getWidth()/2 - 100; int my = getHeight()/2 + 100;

            for (Planet p : planets) {
                int x = mx + (int)(p.pos.x * scale); 
                int y = my - (int)(p.pos.y * scale);
                g2.setColor(p.color); g2.fillOval(x-12, y-12, 24, 24);
                g2.setColor(Color.WHITE); g2.drawString(p.name, x+15, y+5);
            }

            int sx = mx + (int)(current.pos.x * scale); 
            int sy = my - (int)(current.pos.y * scale);
            g2.setColor(Color.GREEN); g2.setStroke(new BasicStroke(2));
            g2.drawRect(sx-18, sy-18, 36, 36);
        }
    }
}
