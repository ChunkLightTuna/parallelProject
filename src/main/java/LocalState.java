import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The local state keeps track of the players, and the status of the cube
 */
public class LocalState extends UnicastRemoteObject implements RemoteState, Serializable {
    final String name;

    private final Cube cube;
    private long start;
    private long timeLeft;
    private long timeLimit;
    private transient Object playerLock;

    private ConcurrentHashMap<String, Player> players;
    private ConcurrentHashMap<String, Attacker> attackers;
    private ConcurrentHashMap<String, Defender> defenders;
    private ArrayList<Player> leaderboard;

    private boolean saved = false;

    private AtomicInteger currentState;

    /**
     * Constructor
     *
     * @param name      server name
     * @param size      cube size
     * @param blockHp   block hitpoints
     * @param timeLimit time limit in seconds
     * @throws RemoteException if rmi fails
     */
    LocalState(String name, int size, int blockHp, int timeLimit) throws RemoteException {
        synchronized (this) {
            this.name = name;
            this.cube = new Cube(size, blockHp);

            this.players = new ConcurrentHashMap<>();
            this.attackers = new ConcurrentHashMap<>();
            this.defenders = new ConcurrentHashMap<>();
            this.leaderboard = new ArrayList<>();

            this.playerLock = new Object();
            this.start = System.nanoTime();
            this.timeLimit = (long) (timeLimit * 1e9);

            this.currentState = new AtomicInteger(-2);
        }
    }

    /**
     * resets the transient fields of the different components
     * to enable deserialization
     *
     * @throws RemoteException if rmi fails
     */
    void reset() throws RemoteException {
        System.err.println("Resetting time");
        timeLimit = timeLeft;
        start = System.nanoTime();
        System.err.println("Resetting player lock");
        this.playerLock = new Object();
        System.err.println("Resetting block locks");
        for (GameBlock gb : cube.cubeMap.values()) {
            gb.resetLock();
        }
        System.err.println("Resetting player locks");
        for (Player p : players.values()) {
            p.resetLocks();
        }
        System.err.println("Resetting defender locks");
        for (Defender d : defenders.values()) {
            d.logout();
            d.resetLock();
        }
        System.err.println("Resetting attacker locks");
        for (Attacker a : attackers.values()) {
            a.logout();
        }
    }

    void savePoints() throws IOException {
        if (!saved) {
            saved = true;
            System.err.println("Saving Stats");
            BufferedWriter outputWriter = new BufferedWriter(
                    new FileWriter(System.getProperty("user.home") + "/PlayerScore.csv"));
            for (Map.Entry<String, Player> entry : players.entrySet()) {
                System.err.println(entry.getKey() + " " + entry.getValue().getScore() + "\n");
                outputWriter.write(entry.getKey() + " " + entry.getValue().getScore() + "\n");
            }
            outputWriter.close();
        }
    }

    /**
     * If the given username is already registered return the player if the player is not logged on, null otherwise
     * If the given username is not already registered register the username and return it
     *
     * @param username the name to look up
     * @return an active player or null
     * @throws RemoteException if rmi fail
     */
    public RemotePlayer login(String username) throws RemoteException {
        synchronized (playerLock) {
            try {
                if (players.get(username).login()) {
                    System.err.println("Success");
                    System.err.println(players.get(username).unameToString());
                    return players.get(username);
                }
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Return the role of a given player
     *
     * @param username the name to look up
     * @return 1 if the user is an attacker, 0 if it is a defender, -1 if the player is not registered
     */
    private int findRole(String username) {
        try {
            return players.get(username).getRole();
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * Logs the player of the game
     *
     * @param username the name to look up
     * @return true if the player was successfully logged out, false otherwise
     * @throws RemoteException if rmi fails
     */
    public synchronized boolean logout(String username) throws RemoteException {
        if (players.get(username) != null) {
            players.get(username).logout();
            return true;
        }
        return false;
    }

    /**
     * Creates a new player
     *
     * @param username the username of the new player
     * @param role     the role of the new player
     * @return the player if it was successfully created, null otherwise
     * @throws RemoteException if rmi fails
     */
    public synchronized boolean register(String username, int role) throws RemoteException {
        synchronized (playerLock) {
            if (players.get(username) == null) {
                if (role == 1) {
                    System.err.println("Registered attacker " + username);
                    Attacker atk = new Attacker(username, 0, 0);
                    players.putIfAbsent(username, atk);
                    attackers.putIfAbsent(username, atk);
                } else {
                    System.err.println("Registered defender " + username);
                    Defender def = new Defender(username, 0, 0);
                    players.putIfAbsent(username, def);
                    defenders.putIfAbsent(username, def);
                }
                leaderboard.add(players.get(username));
                return true;
            }
            return false;
        }
    }

    /**
     * Same as regular register() but allows to set the additional attributes of the player
     *
     * @param username  username
     * @param role      role
     * @param score     score
     * @param credits   credits
     * @param primary   primary ability rating
     * @param secondary secondary ability rating
     * @param items     number of available items
     * @return the player if it was successfully created, null otherwise
     * @throws RemoteException if rmi fails
     */
    @Override
    public boolean register(String username, int role, int score, int credits, int primary, int secondary, int items)
            throws RemoteException {
        synchronized (playerLock) {
            if (players.get(username) == null) {
                if (role == 1) {
                    System.err.println("Registered attacker " + username);
                    Attacker atk = new Attacker(username, score, credits, primary, secondary, items);
                    players.putIfAbsent(username, atk);
                    attackers.putIfAbsent(username, atk);
                } else {
                    System.err.println("Registered defender " + username);
                    Defender def = new Defender(username, score, credits, primary, secondary, items);
                    players.putIfAbsent(username, def);
                    defenders.putIfAbsent(username, def);
                }
                leaderboard.add(players.get(username));
                return true;
            }
            return false;
        }
    }

    /**
     * prints the top 10 players
     *
     * @return a String containing the 10 players with the highest overall score
     * @throws RemoteException if rmi fails
     */
    public String printLeaderBoards() throws RemoteException {
        Collections.sort(leaderboard);
        int min = leaderboard.size() >= 10 ? 10 : leaderboard.size();
        String s = "";
        for (int i = 0; i < min; i++) {
            s += (i + 1) + ": " + leaderboard.get(i).unameToString() + " " + leaderboard.get(i).getScore() + "\n";
        }
        return s;
    }

    /**
     * Prints how much time is left in the game in seconds
     */
    public void printTimeLeft() {
        long timePassed = System.nanoTime() - start;
        timeLeft = timeLimit - timePassed;
        System.err.format("%.0f/%.0f seconds left%n", timeLeft / 1e9,timeLimit / 1e9);
    }

    /**
     * prints the status of the game
     *
     * @return 1 if attackers won, -1 if defenders won,
     * 666 if the game crashed, 0 if the game is still running
     * @throws RemoteException if rmi fails
     */
    public int printStatus() throws RemoteException {
        long timePassed = System.nanoTime() - start;
        timeLeft = timeLimit - timePassed;
        try {
            if (!this.cube.isAlive()) {
                //Attackers won
                System.err.println("Cube destroyed, attackers won!");
                savePoints();
                currentState.set(1);
                return 1;
            }
            timePassed = System.nanoTime() - start;
            if (timePassed > timeLimit) {
                timeLeft = timeLimit - timePassed;
                System.err.println("Cube survived, defenders won");
                savePoints();
                currentState.set(0);
                return -1;
            }
        } catch (NullPointerException npe) {
            System.err.println("Could not find cube, something went wrong");
            currentState.set(666);
            return 666;
        } catch (IOException e) {
            e.printStackTrace();
        }
        currentState.set(0);
        return 0;
    }

    /**
     * Returns true if the game is still going, false otherwise
     *
     * @return true if the game is still going, false otherwise
     * @throws RemoteException if rmi fails
     */
    public boolean isAlive() throws RemoteException {
        try {
            if (!this.cube.isAlive()) {
                //Attackers won
//                System.err.println("Cube destroyed, attackers won!");
                return false;
            }
            long timePassed = System.nanoTime() - start;
            if (timePassed > timeLimit) {
                timeLeft = timeLimit - timePassed;
                return false;
            }
        } catch (NullPointerException npe) {
            return false;
        }
        return true;
    }

    @Override
    public int getState() throws RemoteException {
        return currentState.get();
    }

    /**
     * Parses a request from a client and returns the response as a string
     *
     * @param request a request from a client
     * @return a string containing a reply to a request
     * @throws RemoteException if rmi fails
     */
    public String parseRequest(String request) throws RemoteException {
        String[] tokens = request.split("-");
        String action = tokens[0];
//        System.err.println("Action : " + action);
        String resp = "";
        int res;
        switch (action) {
            case "REGISTER": {
                if (tokens.length == 3) {
                    if (register(tokens[1], Integer.parseInt(tokens[2]))) {
                        res = 1;
                    } else {
                        res = 0;
                    }
                    resp = "REGISTER-" + res + "-" + tokens[1] + "-" + tokens[2];
                } else {
                    if (register(tokens[1], Integer.parseInt(tokens[2]),
                            Integer.parseInt(tokens[3]), Integer.parseInt(tokens[4]),
                            Integer.parseInt(tokens[5]), Integer.parseInt(tokens[6]),
                            Integer.parseInt(tokens[6]))) {
                        res = 1;
                    } else {
                        res = 0;
                    }
                    resp = "REGISTER-" + res + "-" + tokens[1] + "-" + tokens[2];
                }
                break;
            }
            case "LOGIN": {
                if (login(tokens[1]) != null) {
                    res = 1;
                } else {
                    res = 0;
                }
                resp = "LOGIN-" + res + "-" + tokens[1] + "-" + findRole(tokens[1]);
                break;
            }
            case "LOGOUT": {
                if (logout(tokens[1])) {
                    res = 1;
                } else {
                    res = 0;
                }
                resp = "LOGOUT-" + res + "-" + tokens[1];
                break;
            }
            case "ATTACK": {
                res = requestPrimary(tokens[1], 1, tokens[2]);
                resp = "ATTACK-(" + res + ")-" + tokens[1];
                break;
            }
            case "REPAIR": {
                res = requestPrimary(tokens[1], 0, tokens[2]);
                resp = "REPAIR-(" + res + ")-" + tokens[1];
                break;
            }
            case "BOMB": {
                res = requestSecondary(tokens[1], 1, tokens[2]);
                resp = "BOMB-(" + res + ")-" + tokens[1];
                break;
            }
            case "SHIELD": {
                res = requestSecondary(tokens[1], 0, tokens[2]);
                resp = "SHIELD-(" + res + ")-" + tokens[1];
                break;
            }
            case "BUYBOMB": {
                res = buy(tokens[1], 1);
                resp = "BUYBOMB-(" + res + ")-" + tokens[1];
                break;
            }
            case "BUYSHIELD": {
                res = buy(tokens[1], 0);
                resp = "BUYSHIELD-(" + res + ")-" + tokens[1];
                break;
            }
            case "LVLATK": {
                res = levelPrimary(tokens[1], 1);
                resp = "LVLATK-(" + res + ")-" + tokens[1];
                break;
            }
            case "LVLREP": {
                res = levelPrimary(tokens[1], 0);
                resp = "LVLREP-(" + res + ")-" + tokens[1];
                break;
            }
            case "LVLSPD": {
                res = levelSecondary(tokens[1], Integer.parseInt(tokens[2]));
                resp = "LVLSPD-(" + res + ")-" + tokens[1];
                break;
            }
            case "GETTARGETS": {
                resp = "TARGETS-" + getTargets() + "\r\n";
                break;
            }
            case "GETEND": {
                res = getState();
                resp = "GETEND-(" + res + ")-" + printLeaderBoards();
                break;
            }
            case "BOOST": {
                res = requestBoost(tokens[1], Integer.parseInt(tokens[2]));
                resp = "BOOST-(" + res + ")";
                break;
            }
            case "GETPLAYER": {
                String pl;
                pl = players.get(tokens[1]).print();
                resp = "GETPLAYER-" + pl + "\r\n";
                break;
            }
        }
        return resp;
    }

    /**
     * Set the attack rating of a player
     *
     * @param u the username of the player
     * @param a the attack rating
     */
    void setAtk(String u, int a) {
        attackers.get(u).setPrimary(a);
    }

    /**
     * Set the repair rating of a player
     *
     * @param u the username of the player
     * @param a the repair rating
     */
    void setRep(String u, int a) {
        defenders.get(u).setRepairRating(a);
    }

    /**
     * Set the number of bombs of a player
     *
     * @param u the username of the player
     * @param a the number of bombs
     */
    void setBombs(String u, int a) {
        if (a > 0)
            attackers.get(u).setItems(a);
    }

    /**
     * Set the number of shields of a player
     *
     * @param u the username of the player
     * @param a the number of shields
     */
    void setShields(String u, int a) {
        defenders.get(u).setItems(a);
    }

    /**
     * Set the number of credits of a player
     *
     * @param u the username of the player
     * @param a the number of credits
     */
    void setCredits(String u, int a) throws RemoteException {
        players.get(u).gainCredits(a);
    }

    /**
     * Set the speed of a player
     *
     * @param u the username of the player
     * @param r the role of the player
     * @param a the speed
     */
    void setSpeed(String u, int r, int a) throws RemoteException {
        players.get(u).setSecondary(a);
    }

    /**
     * Set the number of credits required to level up attack rating
     *
     * @param u the username of the player
     * @param a the number of credits
     */
    void setLevelAr(String u, int a) {
        attackers.get(u).setLevelPrimary(a);
    }

    /**
     * Set the number of credits required to level up repair rating
     *
     * @param u the username of the player
     * @param a the number of credits
     */
    void setLevelRr(String u, int a) {
        defenders.get(u).setLevelPrimary(a);
    }

    /**
     * Set the number of credits required to level up speed
     *
     * @param u the username of the player
     * @param r the role of the player
     * @param a the number of credits
     */
    void setLevelSpd(String u, int r, int a) throws RemoteException {
        players.get(u).setLevelSecondary(a);
    }

    /**
     * print all the players
     *
     * @throws RemoteException if rmi fails
     */
    void printPlayers() throws RemoteException {
        for (Map.Entry<String, Player> entry : players.entrySet()) {
            System.err.println(entry.getValue().print());
            System.err.println("---------------");
        }
    }

    /**
     * Apply the user's primary ability to a block
     *
     * @param user  the username to look up
     * @param role  the role of the player
     * @param block the target block
     * @return the result of a successful attempt, -1 otherwise
     * @throws RemoteException if rmi fails
     */
    public int requestPrimary(String user, int role, String block) throws RemoteException {
//        System.err.println("Primary for " + block);
        int result;
        if (role == 1) {
            try {
                result = attackers.get(user).attack(cube.getBlock(block));
                if (cube.getBlock(block).getHp() <= 0) {
                    int pos = cube.currentLayer.layer.indexOf(cube.getBlock(block));
                    if (cube.currentLayer.layer.remove(pos) != null) {
                        System.err.println("Removed " + cube.getBlock(block).toString());
                    }
                }
            } catch (Exception e) {
                result = -1;
            }
        } else {
            try {
                result = defenders.get(user).repair(cube.getBlock(block));
            } catch (Exception e) {
                result = -1;
            }
        }
        return result;
    }

    /**
     * Apply the user's secondary ability to a block
     *
     * @param user  the username to look up
     * @param role  the role of the player
     * @param block the target block
     * @return the result of a successful attempt, -1 otherwise
     * @throws RemoteException if rmi fails
     */
    @Override
    public int requestSecondary(String user, int role, String block) throws RemoteException {
        try {
            if (role == 1) {
                Random rand = new Random();
                ArrayList<GameBlock> targets = new ArrayList<>();
                int r1;
                int idx = 0;
                int attempts = 0;
                int limit = cube.currentLayer.layer.size() >= 4 ? 4 : cube.currentLayer.layer.size();
                while ((idx < limit) && (attempts < 10)) {
                    GameBlock b = cube.currentLayer.layer.get(rand.nextInt(limit));
                    if (!targets.contains(b)) {
                        idx++;
                        targets.add(b);
                    }
                    attempts++;
                }
                GameBlock b1 = cube.getBlock(block);
                if (b1 != null) {
                    targets.add(b1);
                }
                r1 = attackers.get(user).bomb(targets);
                return r1;
            } else {
                return defenders.get(user).shield(cube.getBlock(block));
            }
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * request a boost for the given player
     *
     * @param user the username to lookup
     * @param role the role of the player
     * @return 1 if the boost succeeded, 0 if it failed
     * @throws RemoteException if rmi fails
     */
    @Override
    public int requestBoost(String user, int role) throws RemoteException {
        try {
            return players.get(user).boost();
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Request to upgrade the user's primary ability
     *
     * @param user the username to lookup
     * @param role the role of the user
     * @return the user's new primary ability level, or the credits needed to upgrade
     * or 0 if an exception occurs
     * @throws RemoteException if rmi fails
     */
    @Override
    public int levelPrimary(String user, int role) throws RemoteException {
        try {
            return players.get(user).upgradePrimary();
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Request to upgrade the user's secondary ability
     *
     * @param user the username to lookup
     * @param role the role of the user
     * @return the user's new secondary ability level, or the credits needed to upgrade
     * or 0 if an exception occurs
     * @throws RemoteException if rmi fails
     */
    @Override
    public int levelSecondary(String user, int role) throws RemoteException {
        try {
            return players.get(user).upgradeSecondary();
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Request to buy an item
     *
     * @param user the username to lookup
     * @param role the role of the user
     * @return the user's new item number, or the credits needed to buy an item
     * @throws RemoteException if rmi fails
     */
    @Override
    public int buy(String user, int role) throws RemoteException {
        try {
            return players.get(user).buyItem();
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Request the available blocks to attack
     *
     * @return the available blocks as as string
     */
    @Override
    public String getTargets() {
        return cube.currentLayer.toStringHp();
    }

    /**
     * Print the player stats
     *
     * @param player the username to look up
     * @param r      the role of the player
     * @return the player as a String
     * @throws RemoteException if rmi fails
     */
    @Override
    public String printPlayer(String player, int r) throws RemoteException {
        return players.get(player).print();
    }

}
