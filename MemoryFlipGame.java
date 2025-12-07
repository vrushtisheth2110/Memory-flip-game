import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import javax.swing.Timer;

public class MemoryFlipGame {
    private JFrame frame;
    private JPanel panel, buttonPanel;
    private JButton[] buttons;
    private String[] values;
    private Color[] colors;
    private int firstSelection = -1, secondSelection = -1, matchedPairs = 0, level = 1, timeLeft, moves;
    private Timer gameTimer;
    private JLabel timerLabel;
    private boolean isPaused = false;
    private JButton pauseButton, resumeButton, newGameButton, exitButton, leaderboardButton;
    private long levelStartTime;
    private int[] levelScores = {0, 0, 0};
    private String csvFile = "leaderboard.csv";
    private String playerName;
    private static final int NUM_TILES = 16;
    private static final int NUM_COLORS = 8;
    private static final int CORRECT_MATCH_SCORE = 10;
    private static final int INCORRECT_FLIP_PENALTY = -2;
    private long levelTimeLimit;
    private long pauseStartTime;
    private int totalScore = 0;
    private boolean gameFailed = false;
    private static final Color DISABLED_COLOR = Color.GRAY;
    private static final Color[] LIGHT_COLORS = {
            new Color(255, 204, 204),
            new Color(204, 255, 204),
            new Color(204, 204, 255),
            new Color(255, 255, 204),
            new Color(255, 204, 255),
            new Color(255, 229, 204),
            new Color(229, 204, 255),
            new Color(204, 255, 255)
    };

    public MemoryFlipGame() {
        playerName = JOptionPane.showInputDialog("Enter your name:");
        if (playerName == null || playerName.trim().isEmpty()) {
            System.exit(0);
        }
        playerName = playerName.trim();
        if (!playerName.matches("[a-zA-Z ]+")) {
            JOptionPane.showMessageDialog(null, "Invalid name. Only characters are allowed.");
            System.exit(0);
        }
        frame = new JFrame("Memory Flip Game");
        frame.setSize(600, 750);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        timerLabel = new JLabel("Level: " + level + " | Time: --s", SwingConstants.CENTER);
        timerLabel.setFont(new Font("Arial", Font.BOLD, 30));
        frame.add(timerLabel, BorderLayout.NORTH);

        panel = new JPanel(new GridLayout(4, 4, 10, 10));
        frame.add(panel, BorderLayout.CENTER);

        buttonPanel = new JPanel(new GridLayout(1, 5, 10, 10)); // Adjusted to fit 5 buttons
        frame.add(buttonPanel, BorderLayout.SOUTH);
        createControlButtons();
        loadLevel();
        frame.setVisible(true);
    }

    private void createControlButtons() {
        String[] buttonNames = {"Pause", "Resume", "New Game", "Exit", "Leaderboard"};
        Color[] buttonColors = {Color.CYAN, Color.GREEN, Color.GREEN, Color.RED, Color.ORANGE};

        for (int i = 0; i < buttonNames.length; i++) {
            JButton button = new JButton(buttonNames[i]);
            button.setBackground(buttonColors[i]);
            button.addActionListener(e -> handleButtonAction(button.getText()));
            buttonPanel.add(button);

            if (button.getText().equals("Pause")) pauseButton = button;
            if (button.getText().equals("Resume")) {
                resumeButton = button;
                resumeButton.setEnabled(false); // Initially disabled
            }
            if (button.getText().equals("New Game")) newGameButton = button;
            if (button.getText().equals("Exit")) exitButton = button;
            if (button.getText().equals("Leaderboard")) leaderboardButton = button;
        }
    }

    private void handleButtonAction(String action) {
        switch (action) {
            case "Pause" -> pauseGame();
            case "Resume" -> resumeGame();
            case "New Game" -> {
                String newName = JOptionPane.showInputDialog("Enter your name:");
                if (newName == null || newName.trim().isEmpty()) {
                    System.exit(0);
                }
                newName = newName.trim();
                if (!newName.matches("[a-zA-Z ]+")) {
                    JOptionPane.showMessageDialog(null, "Invalid name. Only characters are allowed.");
                    System.exit(0);
                }
                playerName = newName;
                resetGame();
            }
            case "Exit" -> frame.dispose();
            case "Leaderboard" -> showLeaderboard();
        }
    }

    private void pauseGame() {
        gameTimer.stop();
        pauseButton.setEnabled(false);
        resumeButton.setEnabled(true);
        pauseStartTime = System.currentTimeMillis();
        isPaused = true;
        for (int i = 0; i < buttons.length; i++) {
            buttons[i].setBackground(DISABLED_COLOR);
            buttons[i].setEnabled(false);
        }
    }

    private void resumeGame() {
        levelStartTime += System.currentTimeMillis() - pauseStartTime;
        gameTimer.start();
        pauseButton.setEnabled(true);
        resumeButton.setEnabled(false);
        isPaused = false;
        for (int i = 0; i < buttons.length; i++) {
            if (buttons[i].getText().isEmpty()) {
                buttons[i].setBackground(Color.BLACK);
            } else {
                buttons[i].setBackground(colors[i]);
            }
            buttons[i].setEnabled(true);
        }
    }

    private void resetGame() {
        level = 1;
        moves = 0;
        totalScore = 0;
        levelScores = new int[]{0, 0, 0};
        gameFailed = false;
        loadLevel();
    }

    private void loadLevel() {
        setTimerForLevel();
        levelStartTime = System.currentTimeMillis();
        String[][] levelValues = {
                {"A", "A", "B", "B", "C", "C", "D", "D", "E", "E", "F", "F", "G", "G", "H", "H"},
                {"1", "1", "2", "2", "3", "3", "4", "4", "5", "5", "6", "6", "7", "7", "8", "8"},
                {"Apple", "Apple", "Banana", "Banana", "Cherry", "Cherry", "Date", "Date", "Elderberry", "Elderberry", "Fig", "Fig", "Grape", "Grape", "Honeydew", "Honeydew"}
        };

        values = levelValues[level - 1];
        shuffleArray();
        assignColors();
        panel.removeAll();
        buttons = new JButton[NUM_TILES];
        for (int i = 0; i < buttons.length; i++) {
            buttons[i] = new JButton();
            buttons[i].setBackground(Color.BLACK);
            buttons[i].setFont(new Font("Arial", Font.BOLD, 18));
            buttons[i].addActionListener(new TileClickListener(i));
            panel.add(buttons[i]);
        }

        panel.revalidate();
        panel.repaint();
        matchedPairs = 0;
        firstSelection = -1;
        secondSelection = -1;
        startTimer();
    }

    private void setTimerForLevel() {
        switch (level) {
            case 1 -> levelTimeLimit = 60000;
            case 2 -> levelTimeLimit = 50000;
            case 3 -> levelTimeLimit = 40000;
        }
    }

    private void startTimer() {
        gameTimer = new Timer(1000, e -> {
            if (!isPaused) {
                long currentTime = System.currentTimeMillis();
                long elapsed = currentTime - levelStartTime;
                timeLeft = (int) ((levelTimeLimit - elapsed) / 1000);
                if (timeLeft <= 0) {
                    timeLeft = 0;
                    gameTimer.stop();
                    if (matchedPairs < NUM_COLORS) {
                        if (!gameFailed) {
                            gameFailed = true;
                            JOptionPane.showMessageDialog(frame, "Time's up! Level Failed.");
                            saveProgress(false);
                            int choice = JOptionPane.showConfirmDialog(frame, "Do you want to play again?", "New Game", JOptionPane.YES_NO_OPTION);
                            if (choice == JOptionPane.YES_OPTION) {
                                resetGame();
                            } else {
                                frame.dispose();
                            }
                        }
                    }
                }
                timerLabel.setText("Level: " + level + " | Time: " + timeLeft + "s");
            }
        });
        gameTimer.start();
    }

    private void shuffleArray() {
        java.util.List<String> list = Arrays.asList(values);
        Collections.shuffle(list);
        list.toArray(values);
    }

    private void assignColors() {
        colors = new Color[NUM_TILES];
        Map<String, Color> colorMap = new HashMap<>();
        int colorIndex = 0;

        for (int i = 0; i < values.length; i++) {
            if (!colorMap.containsKey(values[i])) {
                colorMap.put(values[i], LIGHT_COLORS[colorIndex % LIGHT_COLORS.length]);
                colorIndex++;
            }
            colors[i] = colorMap.get(values[i]);
        }
    }

    private void saveProgress(boolean completed) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(csvFile, true))) {
            File file = new File(csvFile);
            if (file.length() == 0) {
                writer.write("Name,Date,Time,Level 1,Level 2,Level 3,Final Score");
                writer.newLine();
            }
            String date = new SimpleDateFormat("dd-MM-yyyy").format(new Date());
            String time = new SimpleDateFormat("HH:mm").format(new Date());

            if (completed) {
                String record = String.format("%s,%s,%s,%d,%d,%d,%d",
                        playerName,
                        date,
                        time,
                        levelScores[0],
                        levelScores[1],
                        levelScores[2],
                        totalScore);

                writer.write(record);
            } else {
                int[] scoresToSave = levelScores.clone();
                for (int i = level; i < scoresToSave.length; i++) {
                    scoresToSave[i] = 0;
                }
                String record = String.format("%s,%s,%s,%d,%d,%d,%d",
                        playerName,
                        date,
                        time,
                        scoresToSave[0],
                        scoresToSave[1],
                        scoresToSave[2],
                        0);

                writer.write(record);
            }
            writer.newLine();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(frame, "Error saving progress: " + e.getMessage());
        }
    }

    private void showLeaderboard() {
        File file = new File(csvFile);
        if (!file.exists()) {
            JOptionPane.showMessageDialog(frame, "Leaderboard file not found.");
            return;
        }
        try {
            java.util.List<String> scores = Files.readAllLines(Paths.get(csvFile));

            java.util.List<String> dataLines = scores.subList(1, scores.size());

            dataLines.sort(new Comparator<String>() {
                @Override
                public int compare(String a, String b) {
                    try {
                        int scoreA = Integer.parseInt(a.split(",")[6]);
                        int scoreB = Integer.parseInt(b.split(",")[6]);
                        return Integer.compare(scoreB, scoreA);
                    } catch (NumberFormatException e) {
                        System.err.println("Invalid score format: " + e.getMessage());
                        return 0;
                    }
                }
            });

            StringBuilder message = new StringBuilder("Top Scores:\n");
            for (int i = 0; i < Math.min(3, dataLines.size()); i++) {
                String[] record = dataLines.get(i).split(",");
                message.append(record[0]).append(": ").append(record[6]).append("\n");
            }
            JOptionPane.showMessageDialog(frame, message.toString());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(frame, "Error loading leaderboard: " + e.getMessage());
        }
    }

    private class TileClickListener implements ActionListener {
        private int index;

        public TileClickListener(int index) {
            this.index = index;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            handleTileClick(index);
        }
    }

    private void handleTileClick(int index) {
        if (buttons[index].getText().equals("") && !isPaused) {
            if (firstSelection == -1) {
                firstSelection = index;
                revealTile(index);
            } else if (secondSelection == -1 && index != firstSelection) {
                secondSelection = index;
                revealTile(index);
                moves++;

                if (values[firstSelection].equals(values[secondSelection])) {
                    levelScores[level - 1] += CORRECT_MATCH_SCORE;
                    totalScore += CORRECT_MATCH_SCORE;
                    matchedPairs++;
                    firstSelection = -1;
                    secondSelection = -1;

                    if (matchedPairs == NUM_COLORS) {
                        gameTimer.stop();
                        JOptionPane.showMessageDialog(frame, "Congratulations! You completed Level " + level + ".");
                        if (level == 3) {
                            saveProgress(true);
                            JOptionPane.showMessageDialog(frame, "Your final score is: " + totalScore);
                            showLeaderboard();
                            int choice = JOptionPane.showConfirmDialog(frame, "Do you want to play again?", "New Game", JOptionPane.YES_NO_OPTION);
                            if (choice == JOptionPane.YES_OPTION) {
                                resetGame();
                            } else {
                                frame.dispose();
                            }
                        } else {
                            level++;
                            loadLevel();
                        }
                    }
                } else {
                    levelScores[level - 1] += INCORRECT_FLIP_PENALTY;
                    totalScore += INCORRECT_FLIP_PENALTY;
                    Timer delay = new Timer(500, e -> {
                        hideTile(firstSelection);
                        hideTile(secondSelection);
                        firstSelection = -1;
                        secondSelection = -1;
                    });
                    delay.setRepeats(false);
                    delay.start();
                }
            }
        }
    }

    private void revealTile(int index) {
        buttons[index].setText(values[index]);
        buttons[index].setBackground(colors[index]);
    }

    private void hideTile(int index) {
        buttons[index].setText("");
        buttons[index].setBackground(Color.BLACK);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(MemoryFlipGame::new);
    }
}
