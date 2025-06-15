package highroller.agents;

import at.ac.tuwien.ifs.sge.game.risk.board.Risk;
import at.ac.tuwien.ifs.sge.game.risk.board.RiskBoard;
import at.ac.tuwien.ifs.sge.game.risk.board.RiskTerritory;
import at.ac.tuwien.ifs.sge.game.risk.board.RiskContinent;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * RiskMetricsCalculator provides methods to evaluate game states and calculate various metrics
 * for Risk game AI decision making. It implements an adaptive strategy that changes based on
 * the player's position in the game.
 * 
 * Key Features:
 * - Dynamic weight adjustment based on game position
 * - Performance optimized with extensive caching
 * - Sophisticated attack potential evaluation
 * - Continent control awareness
 * 
 * The calculator evaluates game states using four main metrics:
 * 1. Territory Control (0.05-0.3 weight)
 *    - Measures proportion of territories controlled
 *    - Higher weight when behind
 * 
 * 2. Troop Strength (0.1-0.4 weight)
 *    - Measures proportion of total troops
 *    - Higher weight when behind
 * 
 * 3. Continent Control (0.05-0.2 weight)
 *    - Evaluates progress towards continent bonuses
 *    - Higher weight when behind
 * 
 * 4. Attack Potential (0.1-0.8 weight)
 *    - Measures ability to make successful attacks
 *    - Higher weight when ahead
 * 
 * Position Detection:
 * - Significant Advantage: territoryRatio > 1.5 && troopRatio > 1.5
 * - Behind in Troops: troopRatio < 0.8
 * - Balanced: Neither of the above
 * 
 * Performance Optimizations:
 * - Cached metrics and calculations
 * - Pre-calculated player territories
 * - Lazy initialization of expensive metrics
 * - Efficient map operations
 */
public class RiskMetricsCalculator {

    private final boolean[] CALCULATOR_CONFIG = {
        true,  // Territory count
        true,  // Troop strength
        true,  // Continent control
        true   // Attack potential
    };
    private final RiskBoard board;
    private final int playerId;
    
    // Cache for frequently accessed data
    private final Map<Integer, RiskTerritory> territories;
    private final Map<Integer, RiskContinent> continents;
    private final Set<Integer> playerTerritories;
    
    // Cached metrics
    private int totalGameTroops = -1;
    private int playerTroops = -1;
    private int playerTerritoryCount = -1;
    private double territoryRatio = -1;
    private double troopRatio = -1;
    private boolean hasSignificantAdvantage = false;
    private boolean isBehindInTroops = false;
    private Map<Integer, Double> attackPotentialCache;
    private Double overallAttackPotential = null;
    private Double continentScore = null;
    private Map<Integer, Integer> continentTerritoryCounts = null;
    private Map<Integer, Integer> playerContinentTerritoryCounts = null;

    /**
     * Creates a new RiskMetricsCalculator for the specified game state and player.
     * @param game The current Risk game state
     * @param playerId The ID of the player to calculate metrics for
     */
    public RiskMetricsCalculator(Risk game, int playerId) {
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
        
        // Initialize attack potential cache
        this.attackPotentialCache = new HashMap<>();
    }

    private void updateAdvantageMetrics() {
        if (territoryRatio == -1 || troopRatio == -1) {
            territoryRatio = (double) getTerritoryCount() / (board.getTerritories().size() - getTerritoryCount());
            troopRatio = (double) getTotalTroopStrength() / (getTotalGameTroops() - getTotalTroopStrength());
            hasSignificantAdvantage = territoryRatio > 1.5 && troopRatio > 1.5;
            isBehindInTroops = troopRatio < 0.8;
        }
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
        // Check cache first
        return attackPotentialCache.computeIfAbsent(territoryId, this::calculateAttackPotential);
    }

    private double calculateAttackPotential(int territoryId) {
        RiskTerritory territory = territories.get(territoryId);
        if (territory == null || territory.getOccupantPlayerId() != playerId) {
            return 0.0;
        }

        int attackerTroops = territory.getTroops();
        if (attackerTroops <= 1) {
            return 0.0;
        }

        Set<Integer> neighbors = board.neighboringEnemyTerritories(territoryId);
        if (neighbors.isEmpty()) {
            return 0.0;
        }

        updateAdvantageMetrics();

        double totalPotential = 0.0;
        for (int neighborId : neighbors) {
            RiskTerritory neighbor = territories.get(neighborId);
            if (neighbor == null) continue;
            
            int defenderTroops = neighbor.getTroops();
            double localTroopRatio = (double) attackerTroops / defenderTroops;
            
            double potential;
            if (hasSignificantAdvantage) {
                if (localTroopRatio >= 1.5) {
                    potential = 1.0;
                } else if (localTroopRatio >= 1.0) {
                    potential = attackerTroops >= 4 ? 0.9 : 0.5;
                } else {
                    potential = 0.3;
                }
            } else {
                if (localTroopRatio >= 2.0) {
                    potential = 1.0;
                } else if (localTroopRatio >= 1.0) {
                    potential = attackerTroops >= 5 ? 0.8 : 0.3;
                } else {
                    potential = 0.1;
                }
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
        if (overallAttackPotential == null) {
            if (playerTerritories.isEmpty()) {
                overallAttackPotential = 0.0;
            } else {
                double totalPotential = 0.0;
                int validTerritories = 0;

                for (int territoryId : playerTerritories) {
                    double potential = getAttackPotential(territoryId);
                    if (potential > 0) {
                        totalPotential += potential;
                        validTerritories++;
                    }
                }

                overallAttackPotential = validTerritories > 0 ? totalPotential / validTerritories : 0.0;
            }
        }
        return overallAttackPotential;
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

        initializeContinentCounts();

        double totalScore = 0.0;
        int continentCount = 0;

        for (Map.Entry<Integer, RiskContinent> entry : continents.entrySet()) {
            int continentId = entry.getKey();
            RiskContinent continent = entry.getValue();
            
            int totalTerritories = continentTerritoryCounts.getOrDefault(continentId, 0);
            int playerTerritories = playerContinentTerritoryCounts.getOrDefault(continentId, 0);
            
            if (totalTerritories > 0) {
                double progress = (double) playerTerritories / totalTerritories;
                double bonus = continent.getTroopBonus();
                totalScore += progress * (bonus / 10.0);
                continentCount++;
            }
        }

        return continentCount > 0 ? totalScore / continentCount : 0.0;
    }

    private void initializeContinentCounts() {
        if (continentTerritoryCounts == null) {
            continentTerritoryCounts = new HashMap<>();
            playerContinentTerritoryCounts = new HashMap<>();

            for (RiskTerritory territory : territories.values()) {
                int continentId = territory.getContinentId();
                continentTerritoryCounts.merge(continentId, 1, Integer::sum);
                if (territory.getOccupantPlayerId() == playerId) {
                    playerContinentTerritoryCounts.merge(continentId, 1, Integer::sum);
                }
            }
        }
    }

    /**
     * Calculates an overall game state score that combines multiple metrics
     * to evaluate the player's position. The score emphasizes aggressive play
     * and attack potential while considering other strategic factors.
     * 
     * Metrics used:
     * - Territory control
     * - Troop strength
     * - Continent control
     * - Attack potential
     * 
     * @return A score between 0 and 1 indicating overall game state quality
     */
    public double getGameStateScore() {
        updateAdvantageMetrics();
        
        if (continentScore == null) {
            continentScore = calculateContinentScore();
        }

        double score = 0.0;
        double weightSum = 0.0;

        if (CALCULATOR_CONFIG[0]) {
            double territoryScore = (double) getTerritoryCount() / board.getTerritories().size();
            double territoryWeight = hasSignificantAdvantage ? 0.05 : (isBehindInTroops ? 0.3 : 0.2);
            score += territoryWeight * territoryScore;
            weightSum += territoryWeight;
        }

        if (CALCULATOR_CONFIG[1]) {
            double troopScore = (double) getTotalTroopStrength() / getTotalGameTroops();
            double troopWeight = hasSignificantAdvantage ? 0.1 : (isBehindInTroops ? 0.4 : 0.3);
            score += troopWeight * troopScore;
            weightSum += troopWeight;
        }

        if (CALCULATOR_CONFIG[2]) {
            double continentWeight = hasSignificantAdvantage ? 0.05 : (isBehindInTroops ? 0.2 : 0.1);
            score += continentWeight * continentScore;
            weightSum += continentWeight;
        }

        if (CALCULATOR_CONFIG[3]) {
            double attackWeight = hasSignificantAdvantage ? 0.8 : (isBehindInTroops ? 0.1 : 0.4);
            score += attackWeight * getOverallAttackPotential();
            weightSum += attackWeight;
        }

        return weightSum > 0 ? score / weightSum : 0.0;
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