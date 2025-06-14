package highroller.agents;

import at.ac.tuwien.ifs.sge.game.risk.board.Risk;
import at.ac.tuwien.ifs.sge.game.risk.board.RiskBoard;
import at.ac.tuwien.ifs.sge.game.risk.board.RiskTerritory;
import at.ac.tuwien.ifs.sge.game.risk.board.RiskContinent;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * RiskMetricsCalculator provides methods to evaluate game states and calculate various metrics
 * for Risk game AI decision making. It implements aggressive attack strategies and evaluates
 * overall game state quality.
 */
public class RiskMetricsCalculator {
    private final RiskBoard board;
    private final int playerId;

    /**
     * Creates a new RiskMetricsCalculator for the specified game state and player.
     * @param game The current Risk game state
     * @param playerId The ID of the player to calculate metrics for
     */
    public RiskMetricsCalculator(Risk game, int playerId) {
        this.board = game.getBoard();
        this.playerId = playerId;
    }

    /**
     * Calculates the total number of territories controlled by the player.
     * @return The number of territories owned by the player
     */
    public int getTerritoryCount() {
        return (int) board.getTerritories().values().stream()
                .filter(t -> t.getOccupantPlayerId() == playerId)
                .count();
    }

    /**
     * Calculates the total number of troops across all territories in the game.
     * @return The total number of troops in the game
     */
    public int getTotalGameTroops() {
        return board.getTerritories().values().stream()
                .mapToInt(RiskTerritory::getTroops)
                .sum();
    }

    /**
     * Calculates the total troop strength of the player.
     * @return The total number of troops owned by the player
     */
    public int getTotalTroopStrength() {
        return board.getTerritories().values().stream()
                .filter(t -> t.getOccupantPlayerId() == playerId)
                .mapToInt(RiskTerritory::getTroops)
                .sum();
    }

    /**
     * Calculates the attack potential of a territory.
     * Favors territories with superior numbers against neighbors.
     * Implements aggressive attack strategy rules:
     * - Strongly favors attacks with 2:1 or better troop ratio
     * - Requires at least 5 troops for equal-number attacks
     * - Penalizes attacks with inferior numbers
     * 
     * @param territoryId The territory to evaluate
     * @return A score between 0 and 1 indicating attack potential
     */
    public double getAttackPotential(int territoryId) {
        RiskTerritory territory = board.getTerritories().get(territoryId);
        if (territory.getOccupantPlayerId() != playerId) {
            return 0.0;
        }

        int attackerTroops = territory.getTroops();
        if (attackerTroops <= 1) {
            return 0.0; // Can't attack with 1 troop
        }

        double totalPotential = 0.0;
        int validTargets = 0;

        // Get neighboring enemy territories
        Set<Integer> neighbors = board.neighboringEnemyTerritories(territoryId);
        for (int neighborId : neighbors) {
            RiskTerritory neighbor = board.getTerritories().get(neighborId);
            int defenderTroops = neighbor.getTroops();
            
            // Calculate attack potential based on troop ratio
            double troopRatio = (double) attackerTroops / defenderTroops;
            
            // Apply aggressive strategy rules
            double potential;
            if (troopRatio >= 2.0) {
                potential = 1.0; // Ideal situation
            } else if (troopRatio >= 1.0) {
                // For equal or slightly superior numbers, require minimum troops
                potential = attackerTroops >= 5 ? 0.8 : 0.3;
            } else {
                potential = 0.1; // Discourage attacks with inferior numbers
            }
            
            totalPotential += potential;
            validTargets++;
        }

        return validTargets > 0 ? totalPotential / validTargets : 0.0;
    }

    /**
     * Calculates the overall attack potential for the player across all territories.
     * Aggregates individual territory attack potentials to evaluate the player's
     * overall offensive capability.
     * 
     * @return A score between 0 and 1 indicating overall attack potential
     */
    public double getOverallAttackPotential() {
        double totalPotential = 0.0;
        int validTerritories = 0;

        for (Map.Entry<Integer, RiskTerritory> entry : board.getTerritories().entrySet()) {
            if (entry.getValue().getOccupantPlayerId() == playerId) {
                double potential = getAttackPotential(entry.getKey());
                if (potential > 0) {
                    totalPotential += potential;
                    validTerritories++;
                }
            }
        }

        return validTerritories > 0 ? totalPotential / validTerritories : 0.0;
    }

    /**
     * Calculates a score for continent control.
     * Evaluates how close the player is to controlling each continent
     * and the potential bonus troops from continent control.
     * 
     * @return A score between 0 and 1 indicating continent control potential
     */
    private double calculateContinentScore() {
        double totalScore = 0.0;
        int continentCount = 0;

        for (Map.Entry<Integer, RiskContinent> entry : board.getContinents().entrySet()) {
            int continentId = entry.getKey();
            RiskContinent continent = entry.getValue();
            
            // Count territories in this continent owned by the player
            int totalTerritories = (int) board.getTerritories().values().stream()
                    .filter(t -> t.getContinentId() == continentId)
                    .count();
            int playerTerritories = (int) board.getTerritories().values().stream()
                    .filter(t -> t.getContinentId() == continentId && t.getOccupantPlayerId() == playerId)
                    .count();
            
            // Calculate progress towards controlling the continent
            double progress = (double) playerTerritories / totalTerritories;
            
            // Weight by continent bonus
            double bonus = continent.getTroopBonus();
            totalScore += progress * (bonus / 10.0); // Normalize bonus
            continentCount++;
        }

        return continentCount > 0 ? totalScore / continentCount : 0.0;
    }

    /**
     * Calculates an overall game state score that combines multiple metrics
     * to evaluate the player's position. The score emphasizes aggressive play
     * and attack potential while considering other strategic factors.
     * 
     * Metrics used:
     * - Territory control (30%): Number of territories owned
     * - Troop strength (30%): Total number of troops
     * - Continent control (20%): Progress towards continent bonuses
     * - Attack potential (20%): Ability to make successful attacks
     * 
     * @return A score between 0 and 1 indicating overall game state quality
     */
    public double getGameStateScore() {
        // Get base metrics
        double territoryScore = (double) getTerritoryCount() / board.getTerritories().size();
        double troopScore = (double) getTotalTroopStrength() / getTotalGameTroops();
        double continentScore = calculateContinentScore();
        double attackPotential = getOverallAttackPotential();

        // Weight the metrics, emphasizing attack potential
        double[] weights = {0.3, 0.3, 0.2, 0.2}; // Increased weight for attack potential
        double[] metrics = {territoryScore, troopScore, continentScore, attackPotential};

        // Calculate weighted sum
        double score = 0.0;
        for (int i = 0; i < weights.length; i++) {
            score += weights[i] * metrics[i];
        }

        return score;
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
} 