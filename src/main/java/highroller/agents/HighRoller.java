package highroller.agents;

import at.ac.tuwien.ifs.sge.engine.Logger;

import java.util.*;
import java.util.concurrent.TimeUnit;

import at.ac.tuwien.ifs.sge.agent.*;
import at.ac.tuwien.ifs.sge.game.Game;
import at.ac.tuwien.ifs.sge.util.Util;
import at.ac.tuwien.ifs.sge.util.tree.Tree;

/**
 * HighRoller is an AI agent that uses Monte Carlo Tree Search (MCTS) to play Risk.
 * It implements the UCT (Upper Confidence Bound for Trees) algorithm to make strategic decisions.
 * The agent maintains a game tree of possible moves and uses simulation to evaluate their effectiveness.
 */
public class HighRoller<G extends Game<A, ?>, A> extends AbstractGameAgent<G, A> {
    private static int INSTANCE_NR_COUNTER = 1;
    private final int instanceNr;
    private static double DEFAULT_EXPLOITATION_CONSTANT = Math.sqrt(2);
    private MCTSAgent<G, A> mctsAgent;

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
        instanceNr = INSTANCE_NR_COUNTER++;
    }

    @Override
    public void setUp(int numberOfPlayers, int playerId) {
        super.setUp(numberOfPlayers, playerId);
        mctsAgent = new MCTSAgent<>(DEFAULT_EXPLOITATION_CONSTANT, playerId);
        mctsAgent.setUp();
    }

    @Override
    public A computeNextAction(G game, long computationTime, TimeUnit timeUnit) {
        //log.debug("computeNextAction");
        super.setTimers(computationTime, timeUnit);
        mctsAgent.setTimers(computationTime, timeUnit);

        log.tra_("Searching for root of tree");
        boolean foundRoot = Util.findRoot(mctsAgent.getTree(), game);
        if (foundRoot) {
            log._trace(", done.");
        } else {
            log._trace(", failed.");
        }

        log.tra_("Check if best move will eventually end game: ");
        if (mctsAgent.sortPromisingCandidates(mctsAgent.getTree(), (o1, o2) -> gameComparator.compare(o1.getGame(), o2.getGame()))) {
            log._trace("Yes");
            return Collections.max(mctsAgent.getTree().getChildren(), mctsAgent.getGameTreeMoveComparator()).getNode().getGame()
                    .getPreviousAction();
        }
        log._trace("No");


        while (!shouldStopComputation()) {
            Tree<HrGameNode<A>> currentTree = mctsAgent.getTree();
            currentTree = mctsAgent.selection(currentTree);
            mctsAgent.expansion(currentTree);
            boolean won = mctsAgent.simulation(currentTree, 128, 0.5);
            mctsAgent.backpropagation(currentTree, won);
        }

        long elapsedTime = Math.max(1, System.nanoTime() - START_TIME);
        log._deb_("\r");
        log.debf_("MCTS with %d simulations at confidence %.1f%%", mctsAgent.getTree().getNode().getPlays(),
                Util.percentage(mctsAgent.getTree().getNode().getWins(), mctsAgent.getTree().getNode().getPlays()));
        log._debugf(
                ", done in %s with %s/simulation.",
                Util.convertUnitToReadableString(elapsedTime,
                        TimeUnit.NANOSECONDS, timeUnit),
                Util.convertUnitToReadableString(elapsedTime / Math.max(1, mctsAgent.getTree().getNode().getPlays()),
                        TimeUnit.NANOSECONDS,
                        TimeUnit.NANOSECONDS));

        if (mctsAgent.getTree().isLeaf()) {
            log._debug(". Could not find a move, choosing the next best greedy option.");
            return Collections.max(game.getPossibleActions(),
                    (o1, o2) -> gameComparator.compare(game.doAction(o1), game.doAction(o2)));
        }

        return Collections.max(mctsAgent.getTree().getChildren(), mctsAgent.getGameTreeMoveComparator()).getNode().getGame()
                .getPreviousAction();
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
