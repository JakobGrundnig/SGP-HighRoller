package highroller.agents;

import at.ac.tuwien.ifs.sge.game.Game;
import at.ac.tuwien.ifs.sge.util.Util;
import at.ac.tuwien.ifs.sge.util.tree.DoubleLinkedTree;
import at.ac.tuwien.ifs.sge.util.tree.Tree;
import at.ac.tuwien.ifs.sge.game.risk.board.Risk;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * MCTSAgent provides the core Monte Carlo Tree Search functionality.
 * This class implements the UCT (Upper Confidence Bound for Trees) algorithm
 * and can be used by game-specific agents to make strategic decisions.
 */
public class MCTSAgent<G extends Game<A, ?>, A> {
    private static final int MAX_PRINT_THRESHOLD = 97;
    private static final int MAX_SIMULATION_DEPTH = 50; // Prevent infinite simulations
    private final double exploitationConstant;
    private final Tree<HrGameNode<A>> tree;
    private final Random random;
    private final int playerId;
    private long START_TIME;
    private long TIMEOUT;
    private long TIME_BUFFER = 100_000_000; // 100ms buffer to ensure we don't exceed time limit

    private Comparator<Tree<HrGameNode<A>>> gameTreeUCTComparator;
    private Comparator<Tree<HrGameNode<A>>> gameTreeSelectionComparator;
    private Comparator<Tree<HrGameNode<A>>> gameTreePlayComparator;
    private Comparator<HrGameNode<A>> gameNodePlayComparator;
    private Comparator<Tree<HrGameNode<A>>> gameTreeWinComparator;
    private Comparator<HrGameNode<A>> gameNodeWinComparator;
    private Comparator<Tree<HrGameNode<A>>> gameTreeMoveComparator;
    private Comparator<HrGameNode<A>> gameNodeMoveComparator;
    private Comparator<HrGameNode<A>> gameNodeGameComparator;
    private Comparator<Tree<HrGameNode<A>>> gameTreeGameComparator;

    /**
     * Creates a new MCTS agent with the specified parameters.
     * @param exploitationConstant The exploration-exploitation balance parameter
     * @param playerId The ID of this agent's player
     */
    public MCTSAgent(double exploitationConstant, int playerId) {
        this.exploitationConstant = exploitationConstant;
        this.playerId = playerId;
        this.tree = new DoubleLinkedTree<>();
        this.random = new Random();
    }

    /**
     * Initializes the agent for a new game.
     * Sets up the game tree and initializes all necessary comparators for tree operations.
     */
    public void setUp() {
        tree.clear();
        tree.setNode(new HrGameNode<>());

        gameTreeUCTComparator = Comparator.comparingDouble(
                (Tree<HrGameNode<A>> t) -> upperConfidenceBound(t, exploitationConstant));
        gameNodePlayComparator = Comparator.comparingInt(
                (HrGameNode<A> n) -> n.getPlays());
        gameTreePlayComparator = (Tree<HrGameNode<A>> o1, Tree<HrGameNode<A>> o2) -> gameNodePlayComparator.compare(o1.getNode(), o2.getNode());

        gameNodeWinComparator = Comparator.comparingInt(
                (HrGameNode<A> n) -> n.getWins());
        gameTreeWinComparator = (Tree<HrGameNode<A>> o1, Tree<HrGameNode<A>> o2) -> gameNodeWinComparator.compare(o1.getNode(), o2.getNode());

        gameNodeGameComparator = (HrGameNode<A> o1, HrGameNode<A> o2) -> {
            Game<A, ?> g1 = o1.getGame();
            Game<A, ?> g2 = o2.getGame();
            if (g1 == g2) return 0;
            if (g1 == null) return -1;
            if (g2 == null) return 1;
            return g1.hashCode() - g2.hashCode();
        };
        gameTreeGameComparator = (Tree<HrGameNode<A>> o1, Tree<HrGameNode<A>> o2) -> gameNodeGameComparator
                .compare(o1.getNode(), o2.getNode());

        gameTreeSelectionComparator = gameTreeUCTComparator.thenComparing(gameTreeGameComparator);

        gameNodeMoveComparator = gameNodePlayComparator.thenComparing(gameNodeWinComparator).thenComparing(gameNodeGameComparator);
        gameTreeMoveComparator = (Tree<HrGameNode<A>> o1, Tree<HrGameNode<A>> o2) -> gameNodeMoveComparator
                .compare(o1.getNode(), o2.getNode());
    }

    /**
     * Sets the computation time parameters for the next search.
     * @param computationTime Maximum time allowed for computation
     * @param timeUnit Unit of time for computation limit
     */
    public void setTimers(long computationTime, TimeUnit timeUnit) {
        START_TIME = System.nanoTime();
        TIMEOUT = timeUnit.toNanos(computationTime) - TIME_BUFFER; // Subtract buffer to ensure we don't exceed time limit
    }

    /**
     * Sorts promising candidates in the game tree to quickly identify winning moves.
     * @param tree Current game tree
     * @param comparator Comparator used for sorting nodes
     * @return true if a determined winning path is found, false otherwise
     */
    public boolean sortPromisingCandidates(Tree<HrGameNode<A>> tree, Comparator<HrGameNode<A>> comparator) {
        boolean isDetermined = true;
        while (!tree.isLeaf() && isDetermined && !shouldStopComputation()) {
            isDetermined = tree.getChildren().stream()
                    .allMatch(c -> c.getNode().getGame().getCurrentPlayer() >= 0);
            Game<A, ?> game = tree.getNode().getGame();
            if (game instanceof Risk) {
                int playerId = this.playerId;
                // Use RiskMetricsCalculator to sort children by victory likelihood
                tree.getChildren().sort((c1, c2) -> {
                    RiskMetricsCalculator calc1 = new RiskMetricsCalculator((Risk) c1.getNode().getGame(), playerId);
                    RiskMetricsCalculator calc2 = new RiskMetricsCalculator((Risk) c2.getNode().getGame(), playerId);
                    return Double.compare(calc2.getGameStateScore(), calc1.getGameStateScore());
                });
            } else if (tree.getNode().getGame().getCurrentPlayer() == playerId) {
                tree.sort(comparator);
            } else {
                tree.sort(comparator.reversed());
            }
            tree = tree.getChild(0);
        }
        return isDetermined && tree.getNode().getGame().isGameOver();
    }

    /**
     * Selection phase of MCTS.
     * Traverses the tree from root to leaf using UCT formula to select promising nodes.
     * @param tree Current game tree
     * @return Selected leaf node for expansion
     */
    public Tree<HrGameNode<A>> selection(Tree<HrGameNode<A>> tree) {
        while (!tree.isLeaf() && !shouldStopComputation()) {
            List<Tree<HrGameNode<A>>> children = new ArrayList<>(tree.getChildren());
            if (tree.getNode().getGame().getCurrentPlayer() < 0) {
                A action = tree.getNode().getGame().determineNextAction();
                for (Tree<HrGameNode<A>> child : children) {
                    if (child.getNode().getGame().getPreviousAction().equals(action)) {
                        tree = child;
                        break;
                    }
                }
            } else {
                tree = Collections.max(children, gameTreeSelectionComparator);
            }
        }
        return tree;
    }

    /**
     * Expansion phase of MCTS.
     * Adds all possible child nodes to the selected leaf node.
     * @param tree Leaf node to expand
     */
    public void expansion(Tree<HrGameNode<A>> tree) {
        if (tree.isLeaf() && !shouldStopComputation()) {
            Game<A, ?> game = tree.getNode().getGame();
            Set<A> possibleActions = game.getPossibleActions();
            for (A possibleAction : possibleActions) {
                if (shouldStopComputation()) break;
                tree.add(new HrGameNode<>(game, possibleAction));
            }
        }
    }

    /**
     * Simulation phase of MCTS.
     * Runs a random playout from the given node to determine the outcome.
     * @param tree Node to simulate from
     * @param simulationsAtLeast Minimum number of simulations to perform
     * @param proportion Time proportion for simulation
     * @return true if the simulation resulted in a win, false otherwise
     */
    public boolean simulation(Tree<HrGameNode<A>> tree, int simulationsAtLeast, int proportion) {
        if (shouldStopComputation()) return false;
        
        int simulationsDone = tree.getNode().getPlays();
        if (simulationsDone < simulationsAtLeast && !shouldStopComputation(proportion)) {
            return simulation(tree);
        }
        return simulation(tree);
    }

    /**
     * Performs a single simulation from the given node.
     * @param tree Node to simulate from
     * @return true if the simulation resulted in a win, false otherwise
     */
    private boolean simulation(Tree<HrGameNode<A>> tree) {
        if (shouldStopComputation()) return false;
        
        Game<A, ?> game = tree.getNode().getGame();
        int depth = 0;
        
        while (!game.isGameOver() && depth < MAX_SIMULATION_DEPTH && !shouldStopComputation()) {
            if (game.getCurrentPlayer() < 0) {
                game = game.doAction();
            } else {
                Set<A> actions = game.getPossibleActions();
                if (actions.isEmpty()) break;
                
                // Use victory likelihood for Risk games
                if (game instanceof Risk) {
                    game = game.doAction(selectActionWithVictoryLikelihood((Risk)game, actions));
                } else {
                    game = game.doAction(Util.selectRandom(actions, random));
                }
            }
            depth++;
        }
        
        return hasWon(game);
    }

    /**
     * Selects an action based on victory likelihood for Risk games.
     * Uses a UCT-like formula to balance exploration and exploitation.
     * @param game Current Risk game state
     * @param actions Set of possible actions
     * @return Selected action
     */
    @SuppressWarnings("unchecked")
    private A selectActionWithVictoryLikelihood(Risk game, Set<A> actions) {
        List<A> actionList = new ArrayList<>(actions);
        List<Double> likelihoods = new ArrayList<>();
        List<Integer> visits = new ArrayList<>();
        double totalVisits = 0.0;

        // Initialize visits and calculate initial likelihoods
        for (A action : actionList) {
            Risk nextGame = (Risk) game.doAction((at.ac.tuwien.ifs.sge.game.risk.board.RiskAction) action);
            RiskMetricsCalculator calculator = new RiskMetricsCalculator(nextGame, playerId);
            double likelihood = calculator.getGameStateScore();
            likelihoods.add(likelihood);
            visits.add(1); // Start with 1 visit to avoid division by zero
            totalVisits += 1;
        }

        // Use UCT-like formula to select action
        double bestScore = Double.NEGATIVE_INFINITY;
        A bestAction = null;
        
        for (int i = 0; i < actionList.size(); i++) {
            // UCT formula: exploitation term + exploration term
            double exploitation = likelihoods.get(i);
            double exploration = exploitationConstant * Math.sqrt(Math.log(totalVisits) / visits.get(i));
            double score = exploitation + exploration;
            
            if (score > bestScore) {
                bestScore = score;
                bestAction = actionList.get(i);
            }
        }
        
        // Update visit count for the selected action
        int selectedIndex = actionList.indexOf(bestAction);
        visits.set(selectedIndex, visits.get(selectedIndex) + 1);
        totalVisits += 1;
        
        return bestAction;
    }

    /**
     * Determines if the given game state is a winning state for this agent.
     * @param game Game state to evaluate
     * @return true if the game state is a win, false otherwise
     */
    private boolean hasWon(Game<A, ?> game) {
        if (shouldStopComputation()) return false;
        
        double[] evaluation = game.getGameUtilityValue();
        double score = Util.scoreOutOfUtility(evaluation, playerId);
        if (!game.isGameOver() && score > 0) {
            evaluation = game.getGameHeuristicValue();
            score = Util.scoreOutOfUtility(evaluation, playerId);
        }

        boolean win = score == 1D;
        boolean tie = score > 0;

        win = win || (tie && random.nextBoolean());
        return win;
    }

    /**
     * Backpropagation phase of MCTS.
     * Updates the statistics of all nodes along the path from leaf to root.
     * @param tree Leaf node to start backpropagation from
     * @param win Whether the simulation resulted in a win
     */
    public void backpropagation(Tree<HrGameNode<A>> tree, boolean win) {
        while (!tree.isRoot() && !shouldStopComputation()) {
            tree = tree.getParent();
            tree.getNode().incPlays();
            if (win) {
                tree.getNode().incWins();
            }
        }
    }

    /**
     * Calculates the Upper Confidence Bound (UCB) value for a node.
     * This is used in the UCT formula to balance exploration and exploitation.
     * @param tree Node to calculate UCB for
     * @param c Exploration constant
     * @return UCB value for the node
     */
    private double upperConfidenceBound(Tree<HrGameNode<A>> tree, double c) {
        double w = tree.getNode().getPlays();
        double n = Math.max(tree.getNode().getPlays(), 1);
        double N = n;
        if (!tree.isRoot()) {
            N = tree.getParent().getNode().getPlays();
        }
        return (w / n) + c * Math.sqrt(Math.log(N) / n);
    }

    /**
     * Checks if computation should stop based on time constraints.
     * @return true if computation should stop, false otherwise
     */
    private boolean shouldStopComputation() {
        return System.nanoTime() - START_TIME >= TIMEOUT;
    }

    /**
     * Checks if computation should stop based on time constraints and proportion.
     * @param proportion Time proportion to check against
     * @return true if computation should stop, false otherwise
     */
    private boolean shouldStopComputation(int proportion) {
        return (System.nanoTime() - START_TIME) * proportion >= TIMEOUT;
    }

    /**
     * Gets the current game tree.
     * @return The current game tree
     */
    public Tree<HrGameNode<A>> getTree() {
        return tree;
    }

    /**
     * Gets the game tree move comparator.
     * @return The game tree move comparator
     */
    public Comparator<Tree<HrGameNode<A>>> getGameTreeMoveComparator() {
        return gameTreeMoveComparator;
    }
} 