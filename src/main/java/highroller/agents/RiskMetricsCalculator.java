package highroller.agents;

import at.ac.tuwien.ifs.sge.game.risk.board.Risk;
import at.ac.tuwien.ifs.sge.game.risk.board.RiskBoard;
import at.ac.tuwien.ifs.sge.game.risk.board.RiskTerritory;
import at.ac.tuwien.ifs.sge.game.risk.board.RiskContinent;
import at.ac.tuwien.ifs.sge.engine.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * RiskMetricsCalculator provides methods to evaluate game states and calculate various metrics
 * for Risk game AI decision making. It implements aggressive attack strategies and evaluates
 * overall game state quality.
 */
public class RiskMetricsCalculator {
    private final RiskBoard board;
    private final int playerId;
    private final Logger log;
    
    // Cache frequently accessed data
    private final Map<Integer, RiskTerritory> territories;
    private final Map<Integer, RiskContinent> continents;
    private final Set<Integer> playerTerritories;
    private int totalGameTroops = -1;
    private int playerTroops = -1;
    private int playerTerritoryCount = -1;

    /**
     * Creates a new RiskMetricsCalculator for the specified game state and player.
     * @param game The current Risk game state
     * @param playerId The ID of the player to calculate metrics for
     * @param log Logger instance for debugging
     */
    public RiskMetricsCalculator(Risk game, int playerId, Logger log) {
        if (game == null) {
            throw new IllegalArgumentException("Game cannot be null");
        }
        if (playerId < 0) {
            throw new IllegalArgumentException("Player ID must be non-negative");
        }
        
        this.board = game.getBoard();
        if (this.board == null) {
            throw new IllegalStateException("Game board cannot be null");
        }
        
        this.playerId = playerId;
        this.log = log;
        this.territories = board.getTerritories();
        this.continents = board.getContinents();
        
        if (this.territories == null || this.continents == null) {
            throw new IllegalStateException("Board territories or continents cannot be null");
        }
        
        // Pre-calculate player territories for faster access
        this.playerTerritories = territories.entrySet().stream()
                .filter(e -> e.getValue().getOccupantPlayerId() == playerId)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    /**
     * Calculates the total number of territories controlled by the player.
     * @return The number of territories owned by the player
     */
    public int getTerritoryCount() {
        if (playerTerritoryCount == -1) {
            playerTerritoryCount = playerTerritories.size();
        }
        return playerTerritoryCount;
    }

    /**
     * Calculates the total number of troops across all territories in the game.
     * @return The total number of troops in the game
     */
    public int getTotalGameTroops() {
        if (totalGameTroops == -1) {
            totalGameTroops = territories.values().stream()
                    .mapToInt(RiskTerritory::getTroops)
                    .sum();
        }
        return totalGameTroops;
    }

    /**
     * Calculates the total troop strength of the player.
     * @return The total number of troops owned by the player
     */
    public int getTotalTroopStrength() {
        if (playerTroops == -1) {
            playerTroops = playerTerritories.stream()
                    .mapToInt(id -> territories.get(id).getTroops())
                    .sum();
        }
        return playerTroops;
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
        RiskTerritory territory = territories.get(territoryId);
        if (territory == null) {
            return 0.0;
        }
        
        if (territory.getOccupantPlayerId() != playerId) {
            return 0.0;
        }

        int attackerTroops = territory.getTroops();
        if (attackerTroops <= 1) {
            return 0.0; // Can't attack with 1 troop
        }

        // Get neighboring enemy territories
        Set<Integer> neighbors = board.neighboringEnemyTerritories(territoryId);
        if (neighbors.isEmpty()) {
            return 0.0;
        }

        double totalPotential = 0.0;
        for (int neighborId : neighbors) {
            RiskTerritory neighbor = territories.get(neighborId);
            if (neighbor == null) {
                continue;
            }
            
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
        }

        return totalPotential / neighbors.size();
    }

    /**
     * Calculates the overall attack potential for the player across all territories.
     * Aggregates individual territory attack potentials to evaluate the player's
     * overall offensive capability.
     * 
     * @return A score between 0 and 1 indicating overall attack potential
     */
    public double getOverallAttackPotential() {
        if (playerTerritories.isEmpty()) {
            return 0.0;
        }

        double totalPotential = 0.0;
        int validTerritories = 0;

        for (int territoryId : playerTerritories) {
            double potential = getAttackPotential(territoryId);
            if (potential > 0) {
                totalPotential += potential;
                validTerritories++;
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
        if (continents.isEmpty()) {
            return 0.0;
        }

        double totalScore = 0.0;
        int continentCount = 0;

        // Pre-calculate territory counts per continent for the player
        Map<Integer, Integer> continentTerritoryCounts = new HashMap<>();
        Map<Integer, Integer> playerContinentTerritoryCounts = new HashMap<>();

        for (RiskTerritory territory : territories.values()) {
            int continentId = territory.getContinentId();
            continentTerritoryCounts.merge(continentId, 1, Integer::sum);
            if (territory.getOccupantPlayerId() == playerId) {
                playerContinentTerritoryCounts.merge(continentId, 1, Integer::sum);
            }
        }

        for (Map.Entry<Integer, RiskContinent> entry : continents.entrySet()) {
            int continentId = entry.getKey();
            RiskContinent continent = entry.getValue();
            
            int totalTerritories = continentTerritoryCounts.getOrDefault(continentId, 0);
            int playerTerritories = playerContinentTerritoryCounts.getOrDefault(continentId, 0);
            
            if (totalTerritories > 0) {
                // Calculate progress towards controlling the continent
                double progress = (double) playerTerritories / totalTerritories;
                
                // Weight by continent bonus
                double bonus = continent.getTroopBonus();
                totalScore += progress * (bonus / 10.0); // Normalize bonus
                continentCount++;
            }
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
        double territoryScore = (double) getTerritoryCount() / territories.size();
        double troopScore = (double) getTotalTroopStrength() / getTotalGameTroops();
//        double continentScore = calculateContinentScore();
//        double attackPotential = getOverallAttackPotential();

        // Weight the metrics, emphasizing attack potential
//        double[] weights = {0.2, 0.4, 0.1, 0.3}; // Increased weight for attack potential
//        double[] metrics = {territoryScore, troopScore, continentScore, attackPotential};

        // only territoryScore and troopScore
        double[] weights = {0.4, 0.6}; // Increased weight for attack potential
        double[] metrics = {territoryScore, troopScore};

        // Calculate weighted sum
        double score = 0.0;
        for (int i = 0; i < weights.length; i++) {
            score += weights[i] * metrics[i];
        }

//        log.trace("Game state score: " + score +
//                 " (territory: " + territoryScore +
//                 ", troops: " + troopScore +
//                 ", continent: " + continentScore +
//                 ", attack: " + attackPotential + ")");
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