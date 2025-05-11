package highroller.agents;

import at.ac.tuwien.ifs.sge.engine.Logger;

import java.util.*;
import java.util.concurrent.TimeUnit;

import at.ac.tuwien.ifs.sge.agent.*;
import at.ac.tuwien.ifs.sge.game.Game;
import at.ac.tuwien.ifs.sge.util.Util;
import at.ac.tuwien.ifs.sge.util.tree.DoubleLinkedTree;
import at.ac.tuwien.ifs.sge.util.tree.Tree;

public class HighRoller<G extends Game<A, ?>, A> extends AbstractGameAgent<G, A> implements GameAgent<G, A> {
    private static int INSTANCE_NR_COUNTER = 1;
    private static final int MAX_PRINT_THRESHOLD = 97;
    private final Logger log = null;
    private final int instanceNr;
    private static double DEFAULT_EXPLOITATION_CONSTANT = Math.sqrt(2);
    private final double exploitationConstant;
    private final Tree<HrGameNode<A>> tree;
    private double points; // Calculate current score based on our troups and enemy's troups

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



    public HighRoller() {
        this(null);
    }
    public HighRoller(Logger log) {
        this(DEFAULT_EXPLOITATION_CONSTANT, log);
    }
    public HighRoller(double exploitationConstant, Logger log) {
        super(log);
        this.exploitationConstant = exploitationConstant;
        instanceNr = INSTANCE_NR_COUNTER++;
        tree = new DoubleLinkedTree<>();
    }

    @Override
    public void setUp(int numberOfPlayers, int playerId) {
        log.debug("setUp");
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

        gameNodeMoveComparator = gameNodePlayComparator.thenComparing(gameNodeWinComparator).thenComparing(gameNodeGameComparator);
        gameTreeMoveComparator = (Tree<HrGameNode<A>> o1, Tree<HrGameNode<A>> o2) -> gameNodeMoveComparator
                .compare(o1.getNode(), o2.getNode());

    }

        @Override
    public A computeNextAction(G game, long computationTime, TimeUnit timeUnit) {
        log.debug("computeNextAction");
        super.setTimers(computationTime, timeUnit);

        boolean foundRoot = Util.findRoot(tree, game); // search for current gamestate in existing tree
        if (foundRoot) {
            // TODO set node to root?
        }

        log.tra_("Check if best move will eventually end game: ");
        if (sortPromisingCandidates(tree, gameNodeMoveComparator.reversed())) {
            log._trace("Yes");
            return Collections.max(tree.getChildren(), gameTreeMoveComparator).getNode().getGame()
                    .getPreviousAction();
        }
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
                        Math.round(tree.getNode().getPlays() * 11.1111111111F))); // TODO ?!?!

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

    // Add all possible nodes to the leaf node
    private void expansion(Tree<HrGameNode<A>> tree) {
        if (tree.isLeaf()) {
            Game<A, ?> game = tree.getNode().getGame();
            Set<A> possibleActions = game.getPossibleActions();
            for (A possibleAction : possibleActions) {
                tree.add(new HrGameNode<>(game, possibleAction));
            }
        }
    }

    private boolean simulation(Tree<HrGameNode<A>> tree, int simulationsAtLeast, int proportion) {
        int simulationsDone = tree.getNode().getPlays();
        if (simulationsDone < simulationsAtLeast && shouldStopComputation(proportion)) {
            int simulationsLeft = simulationsAtLeast - simulationsDone;
            return simulation(tree, nanosLeft() / simulationsLeft);
        } else if (simulationsDone == 0) {
            return simulation(tree, TIMEOUT / 2L - nanosElapsed()); // TODO 2L ?
        }

        return simulation(tree);
    }

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

    private boolean hasWon(Game<A, ?> game) {
        double[] evaluation = game.getGameUtilityValue(); // TODO ?
        double score = Util.scoreOutOfUtility(evaluation, playerId);
        if (!game.isGameOver() && score > 0) {
            evaluation = game.getGameHeuristicValue();
            score = Util.scoreOutOfUtility(evaluation, playerId);
        }

        boolean win = score == 1D;
        boolean tie = score > 0;

        win = win || (tie && random.nextBoolean()); // If win != null, win = (tie && random Boolean) // TODO ?
        return win;
    }

    private void backpropagation(Tree<HrGameNode<A>> tree, boolean win) {
        int depth = 0;
        while (!tree.isRoot() && (depth++ % 31 != 0 || !shouldStopComputation())) {
            tree = tree.getParent();
            tree.getNode().incPlays();
            // TODO change points
            if (win) {
                tree.getNode().incWins();
            }
        }
    }

    private double upperConfidenceBound(Tree<HrGameNode<A>> tree, double c) {
        double w = tree.getNode().getPlays();
        double n = Math.max(tree.getNode().getPlays(), 1);
        double N = n;
        if (!tree.isRoot()) {
            N = tree.getParent().getNode().getPlays();
        }

        return (w / n) + c * Math.sqrt(Math.log(N) / n);
    }

    @Override
    public void tearDown() {
        log.debug("tearDown");
        HighRoller.super.tearDown();
    }

    @Override
    public void ponderStart() {
        log.debug("ponderStart");
        HighRoller.super.ponderStart();
    }

    @Override
    public void ponderStop() {
        log.debug("ponderStop");
        HighRoller.super.ponderStop();
    }

    @Override
    public void destroy() {
        log.debug("destroy");
        HighRoller.super.destroy();
    }

    public String toString() {
        if (instanceNr > 1 || HighRoller.INSTANCE_NR_COUNTER > 2) {
            return String.format("%s%d", "HighRoller#", instanceNr);
        }
        return "HighRoller";
    }


}

