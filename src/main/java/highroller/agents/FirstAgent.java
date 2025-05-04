package highroller.agents;

import at.ac.tuwien.ifs.sge.engine.Logger;
import java.util.List;
import java.util.concurrent.TimeUnit;

import at.ac.tuwien.ifs.sge.agent.*;
import at.ac.tuwien.ifs.sge.game.Game;

public class FirstAgent<G extends Game<A, ?>, A> extends AbstractGameAgent<G, A> implements GameAgent<G, A> {

    public FirstAgent(Logger log) {
        super(log);
    }

    @Override
    public A computeNextAction(G game, long computationTime, TimeUnit timeUnit) {
        log.trace("computeNextAction");
        super.setTimers(computationTime, timeUnit);
        return List.copyOf(game.getPossibleActions()).get(0);
    }

    @Override
    public void tearDown() {
        log.trace("tearDown");
        FirstAgent.super.tearDown();
    }

    @Override
    public void ponderStart() {
        log.trace("ponderStart");
        FirstAgent.super.ponderStart();
    }

    @Override
    public void ponderStop() {
        log.trace("ponderStop");
        FirstAgent.super.ponderStop();
    }

    @Override
    public void destroy() {
        log.trace("destroy");
        FirstAgent.super.destroy();
    }

}

