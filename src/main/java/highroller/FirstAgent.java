package highroller;

import at.ac.tuwien.ifs.sge.engine.Logger;
import java.util.List;
import java.util.concurrent.TimeUnit;

import at.ac.tuwien.ifs.sge.agent.*;
import at.ac.tuwien.ifs.sge.engine.Logger;
import at.ac.tuwien.ifs.sge.game.Game;

public class FirstAgent<G extends Game<A, ?>, A> extends AbstractGameAgent<G, A> implements GameAgent<G, A> {

    public FirstAgent(Logger log) {
        super(log);
    }
    // testing

    @Override
    public A computeNextAction(G game, long computationTime, TimeUnit timeUnit) {
        super.setTimers(computationTime, timeUnit);
        return List.copyOf(game.getPossibleActions()).get(0);
    }

    @Override
    public void tearDown() {
        FirstAgent.super.tearDown();
    }

    @Override
    public void ponderStart() {
        FirstAgent.super.ponderStart();
    }

    @Override
    public void ponderStop() {
        FirstAgent.super.ponderStop();
    }

    @Override
    public void destroy() {
        FirstAgent.super.destroy();
    }

}

