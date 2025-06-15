package highroller.agents;

import at.ac.tuwien.ifs.sge.game.Game;
import at.ac.tuwien.ifs.sge.game.risk.board.Risk;
import at.ac.tuwien.ifs.sge.game.risk.board.RiskAction;
import at.ac.tuwien.ifs.sge.game.risk.board.RiskBoard;
import at.ac.tuwien.ifs.sge.game.risk.board.RiskContinent;
import at.ac.tuwien.ifs.sge.game.risk.board.RiskTerritory;
import at.ac.tuwien.ifs.sge.util.Util;
import at.ac.tuwien.ifs.sge.util.tree.Tree;
import at.ac.tuwien.ifs.sge.util.tree.DoubleLinkedTree;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * MCTSAgent provides the core Monte Carlo Tree Search functionality.
 * This class implements the UCT (Upper Confidence Bound for Trees) algorithm
 * and can be used by game-specific agents to make strategic decisions.
 */
public class MCTSAgent<G extends Game<A, ?>, A> {
    private static final int MAX_PRINT_THRESHOLD = 97;
    private static final int MAX_SIMULATION_DEPTH = 100; // Increased depth for better simulation
    private static final double EARLY_GAME_THRESHOLD = 0.3;
    private static final double LATE_GAME_THRESHOLD = 0.7;
    private static final int MAX_ACTIONS_TO_EVALUATE = 20; // Increased number of actions to evaluate
    private static final double MIN_SIMULATION_QUALITY = 0.6; // Minimum quality threshold for simulations
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
            if (tree.getNode().getGame().getCurrentPlayer() == playerId) {
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
                // Use game state score in selection if available
                if (tree.getNode().getGame() instanceof Risk) {
                    children.sort((o1, o2) -> {
                        double score1 = o1.getNode().hasGameStateScore() ? o1.getNode().getGameStateScore() : 0;
                        double score2 = o2.getNode().hasGameStateScore() ? o2.getNode().getGameStateScore() : 0;
                        return Double.compare(score2, score1);
                    });
                }
                tree = Collections.max(children, gameTreeSelectionComparator);
            }
        }
        return tree;
    }

    /**
     * Expansion phase of MCTS.
     * Adds all possible child nodes to the selected leaf node.
     * Computes the game state score for Risk games if not already computed.
     * @param tree Leaf node to expand
     */
    public void expansion(Tree<HrGameNode<A>> tree) {
        if (tree.isLeaf() && !shouldStopComputation()) {
            Game<A, ?> game = tree.getNode().getGame();
            Set<A> possibleActions = game.getPossibleActions();
            
            // Sort actions by potential value for Risk games
            if (game instanceof Risk) {
                List<A> sortedActions = new ArrayList<>(possibleActions);
                sortedActions.sort((a1, a2) -> {
                    try {
                        Game<A, ?> nextGame1 = game.doAction(a1);
                        Game<A, ?> nextGame2 = game.doAction(a2);
                        if (nextGame1 instanceof Risk && nextGame2 instanceof Risk) {
                            RiskMetricsCalculator calc1 = new RiskMetricsCalculator((Risk) nextGame1, playerId);
                            RiskMetricsCalculator calc2 = new RiskMetricsCalculator((Risk) nextGame2, playerId);
                            return Double.compare(calc2.getGameStateScore(), calc1.getGameStateScore());
                        }
                        return 0;
                    } catch (IllegalArgumentException e) {
                        return 0;
                    }
                });
                possibleActions = new LinkedHashSet<>(sortedActions);
            }

            for (A possibleAction : possibleActions) {
                if (shouldStopComputation()) break;
                Game<A, ?> nextGame = game.doAction(possibleAction);
                HrGameNode<A> childNode = new HrGameNode<>(nextGame);
                tree.add(childNode);

                // Compute and store game state score if it's a Risk game
                if (nextGame instanceof Risk) {
                    RiskMetricsCalculator calculator = new RiskMetricsCalculator((Risk) nextGame, playerId);
                    childNode.setGameStateScore(calculator.getGameStateScore());
                }
            }
        }
    }

    /**
     * Simulation phase of MCTS.
     * Runs a random playout from the given node to determine the outcome.
     * @param tree Node to simulate from
     * @param simulationsAtLeast Minimum number of simulations to perform
     * @param buffer Time buffer for simulation
     * @return true if the simulation resulted in a win, false otherwise
     */
    public boolean simulation(Tree<HrGameNode<A>> tree, int simulationsAtLeast, double buffer) {
        if (shouldStopComputation()) return false;

        int simulationsDone = tree.getNode().getPlays();
        if (simulationsDone < simulationsAtLeast && !shouldStopComputation(buffer)) {
            return simulation(tree);
        }

        // Check simulation quality
        double quality = calculateSimulationQuality(tree);
        if (quality < MIN_SIMULATION_QUALITY && !shouldStopComputation(buffer)) {
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
        int totalTerritories = game instanceof Risk ? ((Risk) game).getBoard().getTerritories().size() : 0;
        int currentTerritories = game instanceof Risk ? 
            (int) ((Risk) game).getBoard().getTerritories().values().stream()
                .filter(t -> t.getOccupantPlayerId() == playerId)
                .count() : 0;
        
        double gameProgress = totalTerritories > 0 ? (double) currentTerritories / totalTerritories : 0.0;

        while (!game.isGameOver() && depth < MAX_SIMULATION_DEPTH) {
            if (shouldStopComputation()) return false;

            int currentPlayer = game.getCurrentPlayer();

            // Skip inactive players
            if (currentPlayer < 0) {
                A action = game.determineNextAction();
                if (action == null) break;
                game = game.doAction(action);
                depth++;
                continue;
            }

            Set<A> actions = game.getPossibleActions();
            if (actions.isEmpty()) break;

            A selectedAction;
            if (game instanceof Risk) {
                // Different strategies based on game phase
                if (gameProgress < EARLY_GAME_THRESHOLD) {
                    // Early game: Focus on territory control and continent bonuses
                    selectedAction = selectEarlyGameAction((Risk) game, actions);
                } else if (gameProgress > LATE_GAME_THRESHOLD) {
                    // Late game: Focus on eliminating opponents and securing victory
                    selectedAction = selectLateGameAction((Risk) game, actions);
                } else {
                    // Mid game: Balance between early and late game strategies
                    selectedAction = selectMidGameAction((Risk) game, actions);
                }
            } else {
                selectedAction = Util.selectRandom(actions, random);
            }

            // Validate action before executing
            if (selectedAction == null || !game.isValidAction(selectedAction)) {
                break;
            }

            try {
                game = game.doAction(selectedAction);
                // Update game progress
                if (game instanceof Risk) {
                    currentTerritories = (int) ((Risk) game).getBoard().getTerritories().values().stream()
                        .filter(t -> t.getOccupantPlayerId() == playerId)
                        .count();
                    gameProgress = (double) currentTerritories / totalTerritories;
                }
            } catch (IllegalArgumentException e) {
                break;
            }
            depth++;
        }

        return hasWon(game);
    }

    private double calculateSimulationQuality(Tree<HrGameNode<A>> tree) {
        if (tree.getNode().getPlays() == 0) return 0.0;
        return (double) tree.getNode().getWins() / tree.getNode().getPlays();
    }

    private A selectEarlyGameAction(Risk game, Set<A> actions) {
        A bestAction = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        
        List<A> actionList = new ArrayList<>(actions);
        Collections.shuffle(actionList, random);
        int actionsToEvaluate = Math.min(MAX_ACTIONS_TO_EVALUATE, actionList.size());

        for (int i = 0; i < actionsToEvaluate; i++) {
            A action = actionList.get(i);
            if (!game.isValidAction((RiskAction) action)) continue;

            try {
                Risk nextGame = (Risk) game.doAction((RiskAction) action);
                RiskMetricsCalculator calculator = new RiskMetricsCalculator(nextGame, playerId);
                
                // Enhanced early game scoring
                double territoryScore = (double) calculator.getTerritoryCount() / nextGame.getBoard().getTerritories().size();
                double troopScore = (double) calculator.getTotalTroopStrength() / calculator.getTotalGameTroops();
                double continentScore = calculator.calculateContinentScore();
                double attackPotential = calculator.getOverallAttackPotential();
                
                // Weight factors based on game state
                double territoryWeight = 0.4;
                double continentWeight = 0.3;
                double troopWeight = 0.2;
                double attackWeight = 0.1;
                
                double score = territoryScore * territoryWeight +
                             continentScore * continentWeight +
                             troopScore * troopWeight +
                             attackPotential * attackWeight;
                
                if (score > bestScore) {
                    bestScore = score;
                    bestAction = action;
                }
            } catch (IllegalArgumentException e) {
                continue;
            }
        }

        return bestAction != null ? bestAction : Util.selectRandom(actions, random);
    }

    private A selectMidGameAction(Risk game, Set<A> actions) {
        A bestAction = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        
        List<A> actionList = new ArrayList<>(actions);
        Collections.shuffle(actionList, random);
        int actionsToEvaluate = Math.min(MAX_ACTIONS_TO_EVALUATE, actionList.size());

        for (int i = 0; i < actionsToEvaluate; i++) {
            A action = actionList.get(i);
            if (!game.isValidAction((RiskAction) action)) continue;

            try {
                Risk nextGame = (Risk) game.doAction((RiskAction) action);
                RiskMetricsCalculator calculator = new RiskMetricsCalculator(nextGame, playerId);
                
                // Enhanced mid game scoring
                double territoryScore = (double) calculator.getTerritoryCount() / nextGame.getBoard().getTerritories().size();
                double troopScore = (double) calculator.getTotalTroopStrength() / calculator.getTotalGameTroops();
                double attackPotential = calculator.getOverallAttackPotential();
                double continentScore = calculator.calculateContinentScore();
                
                // Weight factors based on game state
                double territoryWeight = 0.3;
                double troopWeight = 0.3;
                double attackWeight = 0.3;
                double continentWeight = 0.1;
                
                double score = territoryScore * territoryWeight +
                             troopScore * troopWeight +
                             attackPotential * attackWeight +
                             continentScore * continentWeight;
                
                if (score > bestScore) {
                    bestScore = score;
                    bestAction = action;
                }
            } catch (IllegalArgumentException e) {
                continue;
            }
        }

        return bestAction != null ? bestAction : Util.selectRandom(actions, random);
    }

    private A selectLateGameAction(Risk game, Set<A> actions) {
        A bestAction = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        
        List<A> actionList = new ArrayList<>(actions);
        Collections.shuffle(actionList, random);
        int actionsToEvaluate = Math.min(MAX_ACTIONS_TO_EVALUATE, actionList.size());

        for (int i = 0; i < actionsToEvaluate; i++) {
            A action = actionList.get(i);
            if (!game.isValidAction((RiskAction) action)) continue;

            try {
                Risk nextGame = (Risk) game.doAction((RiskAction) action);
                RiskMetricsCalculator calculator = new RiskMetricsCalculator(nextGame, playerId);
                
                // Enhanced late game scoring
                double territoryScore = (double) calculator.getTerritoryCount() / nextGame.getBoard().getTerritories().size();
                double troopScore = (double) calculator.getTotalTroopStrength() / calculator.getTotalGameTroops();
                double attackPotential = calculator.getOverallAttackPotential();
                double continentScore = calculator.calculateContinentScore();
                
                // Weight factors based on game state
                double territoryWeight = 0.3;
                double troopWeight = 0.2;
                double attackWeight = 0.4;
                double continentWeight = 0.1;
                
                double score = territoryScore * territoryWeight +
                             troopScore * troopWeight +
                             attackPotential * attackWeight +
                             continentScore * continentWeight;
                
                if (score > bestScore) {
                    bestScore = score;
                    bestAction = action;
                }
            } catch (IllegalArgumentException e) {
                continue;
            }
        }

        return bestAction != null ? bestAction : Util.selectRandom(actions, random);
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
     * @param buffer Time buffer to not exceed the limit
     * @return true if computation should stop, false otherwise
     */
    private boolean shouldStopComputation(double buffer) {
        buffer *= 1_000_000; // Convert to nanoseconds
        return (System.nanoTime() - START_TIME) - buffer >= TIMEOUT;
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