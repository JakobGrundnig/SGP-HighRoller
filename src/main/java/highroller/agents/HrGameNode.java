package highroller.agents;

import at.ac.tuwien.ifs.sge.game.Game;
import at.ac.tuwien.ifs.sge.util.node.GameNode;
import java.util.Objects;

public class HrGameNode<A> implements GameNode<A> {

    private Game<A, ?> game;
    private int wins;
    private int plays;
    private double gameStateScore;

    public HrGameNode() {
        this(null);
    }

    public HrGameNode(Game<A, ?> game) {
        this(game, 0, 0, Double.NaN);
    }

    public HrGameNode(Game<A, ?> game, A action) {
        this(game.doAction(action));
    }

    public HrGameNode(Game<A, ?> game, int wins, int plays, double gameStateScore) {
        this.game = game;
        this.wins = wins;
        this.plays = plays;
        this.gameStateScore = gameStateScore;
    }

    public Game<A, ?> getGame() {
        return game;
    }

    public void setGame(Game<A, ?> game) {
        this.game = game;
    }

    public int getWins() {
        return wins;
    }

    public void setWins(int wins) {
        this.wins = wins;
    }

    public void incWins() {
        wins++;
    }

    public int getPlays() {
        return plays;
    }

    public void setPlays(int plays) {
        this.plays = plays;
    }

    public void incPlays() {
        plays++;
    }

    public double getGameStateScore() {
        return gameStateScore;
    }

    public void setGameStateScore(double score) {
        this.gameStateScore = score;
    }

    public boolean hasGameStateScore() {
        return !Double.isNaN(gameStateScore);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        HrGameNode<?> hrGameNode = (HrGameNode<?>) o;
        return wins == hrGameNode.wins &&
                plays == hrGameNode.plays &&
                game.equals(hrGameNode.game);
    }

    @Override
    public int hashCode() {
        return Objects.hash(game, wins, plays);
    }

}