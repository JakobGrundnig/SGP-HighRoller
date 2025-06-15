# HighRoller - Risk Game AI

## Made by:
- Alexander Nestorov
  - 12019871
- Jakob Grundnig
  - 12024006

## Overview
HighRoller is an AI agent that uses Monte Carlo Tree Search (MCTS) to play Risk. It implements the UCT (Upper Confidence Bound for Trees) algorithm to make strategic decisions.

## Features
- Monte Carlo Tree Search with UCT
- Adaptive strategy based on game position
- Performance optimized metrics calculation
- Dynamic attack potential evaluation
- Continent control awareness

## Components

### HighRoller
The main agent class that implements the game interface and manages the MCTS process.

### MCTSAgent
Core MCTS implementation with the following phases:
1. **Selection**: Traverses the tree using UCT formula
2. **Expansion**: Adds child nodes to the selected leaf
3. **Simulation**: Performs random playouts
4. **Backpropagation**: Updates node statistics

### RiskMetricsCalculator
The RiskMetricsCalculator is a sophisticated component that evaluates game states and calculates various metrics for AI decision making. It implements an adaptive strategy that changes based on the player's position in the game.

#### Game State Score
The GameStateScore is a weighted combination of four key metrics, with weights that dynamically adjust based on the player's position:

##### Base Metrics
1. **Territory Control (0.05-0.3)**
   - Measures the proportion of territories controlled
   - Weight increases when behind in troops (0.3)
   - Weight decreases when ahead (0.05)
   - Normal weight: 0.2

2. **Troop Strength (0.1-0.4)**
   - Measures the proportion of total troops owned
   - Weight increases when behind in troops (0.4)
   - Weight decreases when ahead (0.1)
   - Normal weight: 0.3

3. **Continent Control (0.05-0.2)**
   - Evaluates progress towards continent bonuses
   - Weight increases when behind in troops (0.2)
   - Weight decreases when ahead (0.05)
   - Normal weight: 0.1

4. **Attack Potential (0.1-0.8)**
   - Measures the ability to make successful attacks
   - Weight increases significantly when ahead (0.8)
   - Weight decreases when behind (0.1)
   - Normal weight: 0.4

##### Position-Based Strategy
The calculator detects three main positions:

1. **Significant Advantage** (territoryRatio > 1.5 && troopRatio > 1.5)
   - Territory Control: 0.05
   - Troop Strength: 0.1
   - Continent Control: 0.05
   - Attack Potential: 0.8
   - Focus: Aggressive attacking to finish the game

2. **Behind in Troops** (troopRatio < 0.8)
   - Territory Control: 0.3
   - Troop Strength: 0.4
   - Continent Control: 0.2
   - Attack Potential: 0.1
   - Focus: Growth and scaling

3. **Balanced Position**
   - Territory Control: 0.2
   - Troop Strength: 0.3
   - Continent Control: 0.1
   - Attack Potential: 0.4
   - Focus: Balanced approach

##### Performance Optimizations
The calculator implements several performance optimizations:

1. **Caching**
   - Territory and troop counts
   - Advantage metrics (ratios and flags)
   - Attack potentials for territories
   - Continent control scores
   - Overall game state score

2. **Efficient Calculations**
   - Pre-calculated player territories
   - Cached continent territory counts
   - Optimized attack potential calculations
   - Lazy initialization of expensive metrics

3. **Memory Management**
   - Reuse of data structures
   - Primitive type usage where possible
   - Efficient map operations

##### Usage in MCTS
The GameStateScore is used in the MCTS simulation to:
1. Evaluate the quality of game states
2. Influence the probability of winning in tie situations
3. Guide the selection of promising moves
4. Help determine when to be aggressive vs. defensive

## Usage

### Running a Match
To run a match between two agents:
```bash
java -jar sge-1.0.7-exe.jar match --file=sge-risk-1.0.7-exe.jar --directory=agents --agent HighRoller RandomAgent
```

### Building
```bash
./gradlew build
```

## Links
- [Risk Strategy Guide](https://www.hasbro.com/common/instruct/Risk.PDF)
- [Monte Carlo Tree Search](https://en.wikipedia.org/wiki/Monte_Carlo_tree_search)
- [UCT Algorithm](https://en.wikipedia.org/wiki/Upper_confidence_bound_for_trees)