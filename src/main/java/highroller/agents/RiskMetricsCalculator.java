package highroller.agents;

import at.ac.tuwien.ifs.sge.game.risk.board.Risk;
import at.ac.tuwien.ifs.sge.game.risk.board.RiskBoard;
import at.ac.tuwien.ifs.sge.game.risk.board.RiskTerritory;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for calculating various game metrics for Risk AI agents.
 * This class provides methods to evaluate game state and calculate important metrics
 * that can be used for decision making in AI agents.
 */
public class RiskMetricsCalculator {
    private final RiskBoard board;
    private final int playerId;

    /**
     * Creates a new RiskMetricsCalculator for the specified game and player.
     *
     * @param game The current Risk game instance
     * @param playerId The ID of the player to calculate metrics for
     */
    public RiskMetricsCalculator(Risk game, int playerId) {
        this.board = game.getBoard();
        this.playerId = playerId;
    }

    /**
     * Calculates the total number of territories owned by the player
     * @return number of territories owned
     */
    public int getTerritoryCount() {
        return board.getNrOfTerritoriesOccupiedByPlayer(playerId);
    }

    /**
     * Calculates the total number of troops owned by the player
     * @return total troop count
     */
    public int getTotalTroopStrength() {
        return board.getTerritories().values().stream()
                .filter(territory -> territory.getOccupantPlayerId() == playerId)
                .mapToInt(RiskTerritory::getTroops)
                .sum();
    }

    /**
     * Calculates the ratio of attacking to defending troops
     * @return ratio as a double (attacking/defending)
     */
    public double getAttackDefenseRatio() {
        int attackingTroops = board.getTerritories().entrySet().stream()
                .filter(entry -> entry.getValue().getOccupantPlayerId() == playerId)
                .mapToInt(entry -> board.getMaxAttackingTroops(entry.getKey()))
                .sum();

        int defendingTroops = board.getTerritories().values().stream()
                .filter(territory -> territory.getOccupantPlayerId() == playerId)
                .mapToInt(RiskTerritory::getTroops)
                .sum();

        return defendingTroops == 0 ? 0 : (double) attackingTroops / defendingTroops;
    }

    /**
     * Calculates the number of territories needed to complete control of each continent
     * @return Map of continent ID to number of territories needed
     */
    public Map<Integer, Integer> getContinentBonusPotential() {
        Map<Integer, Integer> potential = new HashMap<>();
        
        board.getContinents().forEach((continentId, continent) -> {
            int territoriesNeeded = board.getTerritories().entrySet().stream()
                    .filter(entry -> entry.getValue().getContinentId() == continentId)
                    .filter(entry -> entry.getValue().getOccupantPlayerId() != playerId)
                    .mapToInt(entry -> 1)
                    .sum();
            if (territoriesNeeded > 0) {
                potential.put(continentId, territoriesNeeded);
            }
        });
        
        return potential;
    }

    /**
     * Calculates the total number of troops on border territories
     * @return total border troops
     */
    public int getBorderStrength() {
        return board.getTerritories().entrySet().stream()
                .filter(entry -> entry.getValue().getOccupantPlayerId() == playerId)
                .filter(entry -> !board.neighboringEnemyTerritories(entry.getKey()).isEmpty())
                .mapToInt(entry -> entry.getValue().getTroops())
                .sum();
    }

    /**
     * Calculates the total number of enemy troops adjacent to player's territories
     * @return total threat level
     */
    public int getThreatLevel() {
        return board.getTerritories().entrySet().stream()
                .filter(entry -> entry.getValue().getOccupantPlayerId() == playerId)
                .flatMap(entry -> board.neighboringEnemyTerritories(entry.getKey()).stream())
                .mapToInt(territoryId -> board.getTerritoryTroops(territoryId))
                .sum();
    }

    /**
     * Calculates the expected value of trading cards
     * @return expected reinforcement value
     */
    public int getCardTradeValue() {
        if (!board.couldTradeInCards(playerId)) {
            return 0;
        }

        int currentBonus = board.getTradeInBonus();
        int territoryBonus = board.getTradeInTerritoryBonus();
        
        // Count territories that match player's cards
        int matchingTerritories = (int) board.getPlayerCards(playerId).stream()
                .filter(card -> card.getTerritoryId() != -1) // Exclude jokers
                .filter(card -> board.getTerritoryOccupantId(card.getTerritoryId()) == playerId)
                .count();

        return currentBonus + (matchingTerritories * territoryBonus);
    }

    /**
     * Calculates an overall victory likelihood score
     * @return score between 0 and 1
     */
    public double getVictoryLikelihood() {
        double territoryScore = (double) getTerritoryCount() / board.getTerritories().size();
        double troopScore = (double) getTotalTroopStrength() / getTotalGameTroops();
        double continentScore = calculateContinentScore();
        
        // Weighted combination of different factors (rebalanced without missionScore)
        return (0.4 * territoryScore) + 
               (0.4 * troopScore) + 
               (0.2 * continentScore);
    }

    private int getTotalGameTroops() {
        return board.getTerritories().values().stream()
                .mapToInt(RiskTerritory::getTroops)
                .sum();
    }

    private double calculateContinentScore() {
        int totalContinents = board.getContinents().size();
        int controlledContinents = 0;
        for (Map.Entry<Integer, at.ac.tuwien.ifs.sge.game.risk.board.RiskContinent> entry : board.getContinents().entrySet()) {
            int continentId = entry.getKey();
            boolean allOwned = board.getTerritories().values().stream()
                .filter(t -> t.getContinentId() == continentId)
                .allMatch(t -> t.getOccupantPlayerId() == playerId);
            if (allOwned) {
                controlledContinents++;
            }
        }
        return totalContinents == 0 ? 0.0 : (double) controlledContinents / totalContinents;
    }
} 