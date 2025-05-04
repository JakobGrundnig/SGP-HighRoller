package highroller.agents;

import at.ac.tuwien.ifs.sge.engine.Logger;
import java.util.List;
import java.util.concurrent.TimeUnit;

import at.ac.tuwien.ifs.sge.agent.*;
import at.ac.tuwien.ifs.sge.game.Game;

public class FirstAgent<G extends Game<A, ?>, A> extends AbstractGameAgent<G, A> implements GameAgent<G, A> {
    private static int INSTANCE_NR_COUNTER = 1;
    private final Logger log;
    private final int instanceNr;


    public FirstAgent() {
        this(null);
    }
    public FirstAgent(Logger log) {
        this.log = log;
        this.instanceNr = INSTANCE_NR_COUNTER++;
    }



    @Override
    public A computeNextAction(G game, long computationTime, TimeUnit timeUnit) {
        log.debug("computeNextAction");
        super.setTimers(computationTime, timeUnit);
        return List.copyOf(game.getPossibleActions()).get(0);
    }

    @Override
    public void tearDown() {
        log.debug("tearDown");
        FirstAgent.super.tearDown();
    }

    @Override
    public void ponderStart() {
        log.debug("ponderStart");
        FirstAgent.super.ponderStart();
    }

    @Override
    public void ponderStop() {
        log.debug("ponderStop");
        FirstAgent.super.ponderStop();
    }

    @Override
    public void destroy() {
        log.debug("destroy");
        FirstAgent.super.destroy();
    }

    public String toString() {
        if (instanceNr > 1 || FirstAgent.INSTANCE_NR_COUNTER > 2) {
            return String.format("%s%d", "HighRoller#", instanceNr);
        }
        return "HighRoller";
    }


}

