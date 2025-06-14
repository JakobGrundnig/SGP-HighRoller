package highroller.agents;

import at.ac.tuwien.ifs.sge.engine.Logger;

import java.util.*;
import java.util.concurrent.TimeUnit;

import at.ac.tuwien.ifs.sge.agent.*;
import at.ac.tuwien.ifs.sge.game.Game;
import at.ac.tuwien.ifs.sge.util.Util;
import at.ac.tuwien.ifs.sge.util.tree.DoubleLinkedTree;
import at.ac.tuwien.ifs.sge.util.tree.Tree;

/**
 * HighRoller is an AI agent that uses Monte Carlo Tree Search (MCTS) to play Risk.
 * It implements the UCT (Upper Confidence Bound for Trees) algorithm to make strategic decisions.
 * The agent maintains a game tree of possible moves and uses simulation to evaluate their effectiveness.
 */
public class HighRoller<G extends Game<A, ?>, A> extends AbstractGameAgent<G, A> implements GameAgent<G, A> {
    private static int INSTANCE_NR_COUNTER = 1;
    private static final int MAX_PRINT_THRESHOLD = 97;
    private final int instanceNr;
    private static double DEFAULT_EXPLOITATION_CONSTANT = Math.sqrt(2);
    private final double exploitationConstant;
    private final Tree<HrGameNode<A>> tree;

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
     * Default constructor for HighRoller agent.
     * Creates a new instance with default exploitation constant.
     */
    public HighRoller() {
        this(null);
    }

    /**
     * Creates a HighRoller agent with specified logger.
     * @param log Logger instance for debugging and tracing
     */
    public HighRoller(Logger log) {
        this(DEFAULT_EXPLOITATION_CONSTANT, log);
    }

    /**
     * Creates a HighRoller agent with custom exploitation constant and logger.
     * @param exploitationConstant The exploration-exploitation balance parameter
     * @param log Logger instance for debugging and tracing
     */
    public HighRoller(double exploitationConstant, Logger log) {
        super(log);
        this.exploitationConstant = exploitationConstant;
        instanceNr = INSTANCE_NR_COUNTER++;
        tree = new DoubleLinkedTree<>();
    }

    /**
     * Initializes the agent for a new game.
     * Sets up the game tree and initializes all necessary comparators for tree operations.
     * @param numberOfPlayers Total number of players in the game
     * @param playerId The ID of this agent's player
     */
    @Override
    public void setUp(int numberOfPlayers, int playerId) {
        super.setUp(numberOfPlayers, playerId);
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

        gameNodeGameComparator = (HrGameNode<A> o1, HrGameNode<A> o2) -> gameComparator.compare(o1.getGame(), o2.getGame());
        gameTreeGameComparator = (Tree<HrGameNode<A>> o1, Tree<HrGameNode<A>> o2) -> gameNodeGameComparator
                .compare(o1.getNode(), o2.getNode());

        gameTreeSelectionComparator = gameTreeUCTComparator.thenComparing(gameTreeGameComparator);

        gameNodeMoveComparator = gameNodePlayComparator.thenComparing(gameNodeWinComparator).thenComparing(gameNodeGameComparator);
        gameTreeMoveComparator = (Tree<HrGameNode<A>> o1, Tree<HrGameNode<A>> o2) -> gameNodeMoveComparator
                .compare(o1.getNode(), o2.getNode());

    }

    /**
     * Computes the next action to take in the game using MCTS.
     * This is the main decision-making method that runs the MCTS algorithm
     * to find the best move within the given computation time.
     * @param game Current game state
     * @param computationTime Maximum time allowed for computation
     * @param timeUnit Unit of time for computation limit
     * @return The best action to take
     */
    @Override
    public A computeNextAction(G game, long computationTime, TimeUnit timeUnit) {
        log.debug("computeNextAction");
        super.setTimers(computationTime, timeUnit);

        log.tra_("Searching for root of tree");
        boolean foundRoot = Util.findRoot(tree, game); // search for current gamestate in existing tree
        if (foundRoot) {
            log._trace(", done.");
            // TODO set node to root?
        } else {
            log._trace(", failed.");
        }


            log.tra_("Check if best move will eventually end game: ");
        if (sortPromisingCandidates(tree, gameNodeMoveComparator.reversed())) {
            log._trace("Yes");
            return Collections.max(tree.getChildren(), gameTreeMoveComparator).getNode().getGame()
                    .getPreviousAction();
        }
        log._trace("No");
        int looped = 0;

        log.debugf("MCTS with %d simulations at confidence %.1f%%", tree.getNode().getPlays(),
                Util.percentage(tree.getNode().getWins(), tree.getNode().getPlays()));
        int printThreshold = 1; // TODO ?
        while (!shouldStopComputation()) {
            if (looped++ % printThreshold == 0) {
                log._deb_("\r");
                log.debf_("MCTS with %d simulations at confidence %.1f%%", tree.getNode().getPlays(),
                        Util.percentage(tree.getNode().getWins(), tree.getNode().getPlays()));
            }
            Tree<HrGameNode<A>> currentTree = tree;
            currentTree = selection(currentTree);
            expansion(currentTree);
            boolean won = simulation(currentTree, 128, 2); // TODO ?
            backpropagation(currentTree, won);

            if (printThreshold < MAX_PRINT_THRESHOLD) {
                printThreshold = Math.max(1, Math.min(MAX_PRINT_THRESHOLD,
                        Math.round(tree.getNode().getPlays() * 11.1111111111F)));

            }
        }


        long elapsedTime = Math.max(1, System.nanoTime() - START_TIME);
        log._deb_("\r");
        log.debf_("MCTS with %d simulations at confidence %.1f%%", tree.getNode().getPlays(),
                Util.percentage(tree.getNode().getWins(), tree.getNode().getPlays()));
        log._debugf(
                ", done in %s with %s/simulation.",
                Util.convertUnitToReadableString(elapsedTime,
                        TimeUnit.NANOSECONDS, timeUnit),
                Util.convertUnitToReadableString(elapsedTime / Math.max(1, tree.getNode().getPlays()),
                        TimeUnit.NANOSECONDS,
                        TimeUnit.NANOSECONDS));

        if (tree.isLeaf()) {
            log._debug(". Could not find a move, choosing the next best greedy option.");
            return Collections.max(game.getPossibleActions(),
                    (o1, o2) -> gameComparator.compare(game.doAction(o1), game.doAction(o2)));
        }

        return Collections.max(tree.getChildren(), gameTreeMoveComparator).getNode().getGame()
                .getPreviousAction();
    }

    /**
     * Sorts promising candidates in the game tree to quickly identify winning moves.
     * @param tree Current game tree
     * @param comparator Comparator used for sorting nodes
     * @return true if a determined winning path is found, false otherwise
     */
    private boolean sortPromisingCandidates(Tree<HrGameNode<A>> tree, Comparator<HrGameNode<A>> comparator) {
       boolean isDetermined = true;
       while (!tree.isLeaf() && isDetermined) {
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
    private Tree<HrGameNode<A>> selection(Tree<HrGameNode<A>> tree) {
        int depth = 0;
        while (!tree.isLeaf() && (depth++ % 31 != 0 || !shouldStopComputation())) {
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
    private void expansion(Tree<HrGameNode<A>> tree) {
        if (tree.isLeaf()) {
            Game<A, ?> game = tree.getNode().getGame();
            Set<A> possibleActions = game.getPossibleActions(); // TODO: Cull before expansion
            for (A possibleAction : possibleActions) {
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
    private boolean simulation(Tree<HrGameNode<A>> tree, int simulationsAtLeast, int proportion) {
        int simulationsDone = tree.getNode().getPlays();
        if (simulationsDone < simulationsAtLeast && shouldStopComputation(proportion)) {
            int simulationsLeft = simulationsAtLeast - simulationsDone;
            return simulation(tree, nanosLeft() / simulationsLeft);
        } else if (simulationsDone == 0) {
            return simulation(tree, TIMEOUT / 2L - nanosElapsed());
        }

        return simulation(tree);
    }

    /**
     * Performs a single simulation from the given node.
     * @param tree Node to simulate from
     * @return true if the simulation resulted in a win, false otherwise
     */
    private boolean simulation(Tree<HrGameNode<A>> tree) {
        Game <A, ?> game = tree.getNode().getGame();

        int depth = 0;
        while (!game.isGameOver() && (depth++ % 31 != 0 || !shouldStopComputation())) {

            if (game.getCurrentPlayer() < 0) {
                game = game.doAction();
            } else {
                game = game.doAction(Util.selectRandom(game.getPossibleActions(), random)); // TODO CHANGE ?
            }
        }
        return hasWon(game);
    }

    /**
     * Performs a time-limited simulation from the given node.
     * @param tree Node to simulate from
     * @param timeout Maximum time allowed for simulation
     * @return true if the simulation resulted in a win, false otherwise
     */
    private boolean simulation(Tree<HrGameNode<A>> tree, long timeout) {
        long startTime = System.nanoTime();
        Game<A, ?> game = tree.getNode().getGame();

        int depth = 0;
        while ((!game.isGameOver() && (System.nanoTime()) - startTime <= timeout) && (depth++ % 31 != 0 || !shouldStopComputation())) {
            if (game.getCurrentPlayer() < 0) {
                game = game.doAction();
            } else {
                game = game.doAction(Util.selectRandom(game.getPossibleActions(), random)); // TODO CHANGE ?
            }
        }
        return hasWon(game);
    }

    /**
     * Determines if the given game state is a winning state for this agent.
     * @param game Game state to evaluate
     * @return true if the game state is a win, false otherwise
     */
    private boolean hasWon(Game<A, ?> game) {
        double[] evaluation = game.getGameUtilityValue(); // TODO ?
        double score = Util.scoreOutOfUtility(evaluation, playerId);
        if (!game.isGameOver() && score > 0) {
            evaluation = game.getGameHeuristicValue();
            score = Util.scoreOutOfUtility(evaluation, playerId);
        }

        boolean win = score == 1D;
        boolean tie = score > 0;

        win = win || (tie && random.nextBoolean()); // If win != null, win = (tie && random Boolean)
        return win;
    }

    /**
     * Backpropagation phase of MCTS.
     * Updates the statistics of all nodes along the path from leaf to root.
     * @param tree Leaf node to start backpropagation from
     * @param win Whether the simulation resulted in a win
     */
    private void backpropagation(Tree<HrGameNode<A>> tree, boolean win) {
        int depth = 0;
        while (!tree.isRoot() && (depth++ % 31 != 0 || !shouldStopComputation())) {
            tree = tree.getParent();
            tree.getNode().incPlays();
            // TODO change additional heuristics
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
     * Cleans up resources when the agent is no longer needed.
     */
    @Override
    public void tearDown() {
        log.debug("tearDown");
        HighRoller.super.tearDown();
    }

    /**
     * Starts the pondering phase where the agent can think about moves in advance.
     */
    @Override
    public void ponderStart() {
        log.debug("ponderStart");
        HighRoller.super.ponderStart();
    }

    /**
     * Stops the pondering phase.
     */
    @Override
    public void ponderStop() {
        log.debug("ponderStop");
        HighRoller.super.ponderStop();
    }

    /**
     * Destroys the agent and cleans up all resources.
     */
    @Override
    public void destroy() {
        log.debug("destroy");
        HighRoller.super.destroy();
    }

    /**
     * Returns a string representation of the agent.
     * @return String representation of the agent
     */
    public String toString() {
        if (instanceNr > 1 || HighRoller.INSTANCE_NR_COUNTER > 2) {
            return String.format("%s%d", "HighRoller#", instanceNr);
        }
        return "HighRoller";
    }


}

