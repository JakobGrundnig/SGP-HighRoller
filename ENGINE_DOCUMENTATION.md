# Risk Game Engine Documentation

## Overview
The Risk Game Engine is a Java-based implementation of the classic board game Risk. It provides a robust framework for managing game state, player actions, and game mechanics. The engine is designed to be flexible and extensible, supporting various game configurations and rule variations.

## Core Components

### 1. Risk Class
The main game class that implements the core game logic and state management.

#### Key Features:
- Game state management
- Turn handling
- Action validation and execution
- Player management
- Game phase control

#### Important Methods:
- `isGameOver()`: Checks if the game has ended
- `getPossibleActions()`: Returns valid actions for the current player
- `doAction(RiskAction)`: Executes a game action
- `getCurrentPlayer()`: Returns the current player's ID
- `getUtilityValue(int)`: Calculates the utility value for a player

### 2. RiskBoard Class
Manages the game board state, including territories, continents, and game pieces.

#### Key Features:
- Territory management
- Continent control
- Army placement and movement
- Card system
- Mission system

#### Important Components:
1. **Territories**
   - Represents individual territories on the board
   - Tracks ownership and troop counts
   - Manages connections between territories

2. **Continents**
   - Groups of connected territories
   - Provides bonus armies for control
   - Manages continent-specific rules

3. **Card System**
   - Manages risk cards (Infantry, Cavalry, Artillery, Joker)
   - Handles card trading and bonuses
   - Controls card distribution

4. **Mission System**
   - Manages player objectives
   - Tracks mission completion
   - Handles mission-specific victory conditions

#### Important Methods:
- `getTerritories()`: Returns all territories
- `getContinents()`: Returns all continents
- `neighboringTerritories(int)`: Returns adjacent territories
- `getTerritoryTroops(int)`: Returns troops in a territory
- `getMobileTroops(int)`: Returns movable troops in a territory

### 3. Game Phases
The game progresses through several distinct phases:

1. **Initial Selection**
   - Players select starting territories
   - Initial army placement

2. **Reinforcement Phase**
   - Players receive new armies
   - Place reinforcements on owned territories

3. **Attack Phase**
   - Players attack adjacent territories
   - Dice-based combat resolution
   - Territory capture

4. **Occupy Phase**
   - Move troops into newly captured territories
   - Draw cards for successful captures

5. **Fortify Phase**
   - Move troops between owned territories
   - End of turn processing

### 4. Configuration System
The engine supports various game configurations:

- Number of players
- Map layout
- Card rules
- Mission types
- Combat rules
- Reinforcement rules

## Game Rules Implementation

### Combat System
- Dice-based combat resolution
- Attacker can use 1-3 dice
- Defender can use 1-2 dice
- Highest dice values are compared
- Ties go to the defender

### Card Trading
- Three types of cards: Infantry, Cavalry, Artillery
- Joker cards can substitute for any type
- Trading sets for bonus armies
- Territory control bonuses

### Victory Conditions
- Complete elimination of opponents
- Mission completion
- Territory control objectives

## Technical Details

### State Management
- Immutable game state
- Action-based state transitions
- Deep copy support for game state

### Player Interface
- Action validation
- State observation
- Turn management
- Utility calculation

### Performance Considerations
- Efficient territory connectivity checking
- Optimized combat resolution
- Memory-efficient state representation

## Usage

### Basic Game Setup
```java
Risk game = new Risk(numberOfPlayers);
// or with custom configuration
Risk game = new Risk(configuration, numberOfPlayers);
```

### Game Flow
1. Initialize game with desired configuration
2. Players take turns executing actions
3. Game progresses through phases
4. Check for victory conditions
5. Handle game end conditions

### Action Execution
```java
Set<RiskAction> possibleActions = game.getPossibleActions();
RiskAction selectedAction = // player selects action
Game<RiskAction, RiskBoard> nextState = game.doAction(selectedAction);
```

## Extending the Engine

### Adding New Features
1. Extend RiskAction for new actions
2. Modify RiskBoard for new state
3. Update game logic in Risk class
4. Add new configuration options

### Custom Rules
1. Modify RiskConfiguration
2. Implement custom action handlers
3. Add new victory conditions
4. Customize combat resolution

## Best Practices

1. **State Management**
   - Always use immutable state
   - Validate actions before execution
   - Handle edge cases appropriately

2. **Performance**
   - Cache frequently accessed data
   - Optimize territory connectivity checks
   - Minimize object creation

3. **Error Handling**
   - Validate all inputs
   - Provide clear error messages
   - Handle edge cases gracefully

4. **Testing**
   - Test all game phases
   - Verify victory conditions
   - Check edge cases
   - Validate state transitions

## Detailed Component Documentation

### Territory System

#### Territory Class (RiskTerritory)
Represents a single territory on the game board with its properties and state.

##### Properties:
- `territoryId`: Unique identifier for the territory
- `occupantPlayerId`: ID of the player currently controlling the territory
- `troops`: Number of armies stationed in the territory
- `continentId`: ID of the continent this territory belongs to

##### Key Methods:
- `getTerritoryId()`: Returns the territory's unique identifier
- `getOccupantPlayerId()`: Returns the ID of the controlling player
- `getTroops()`: Returns the number of armies in the territory
- `getContinentId()`: Returns the ID of the containing continent

#### Territory Management Methods (RiskBoard)
- `getTerritories()`: Returns a map of all territories
- `getTerritoryIds()`: Returns a set of all territory IDs
- `isTerritory(int territoryId)`: Checks if a territory exists
- `getTerritoryOccupantId(int territoryId)`: Gets the controlling player
- `getTerritoryTroops(int territoryId)`: Gets the number of troops
- `neighboringTerritories(int territoryId)`: Returns adjacent territories
- `neighboringEnemyTerritories(int territoryId)`: Returns adjacent enemy territories
- `neighboringFriendlyTerritories(int territoryId)`: Returns adjacent friendly territories
- `getMobileTroops(int territoryId)`: Returns troops available for movement
- `getMaxAttackingTroops(int territoryId)`: Returns maximum troops for attack

### Continent System

#### Continent Class (RiskContinent)
Represents a group of connected territories with bonus armies for control.

##### Properties:
- `continentId`: Unique identifier for the continent
- `bonus`: Number of bonus armies for controlling the continent
- `territories`: Set of territory IDs in the continent

##### Key Methods:
- `getContinentId()`: Returns the continent's unique identifier
- `getBonus()`: Returns the bonus armies for control
- `getTerritories()`: Returns the set of territories in the continent

#### Continent Management Methods (RiskBoard)
- `getContinents()`: Returns a map of all continents
- `getContinentIds()`: Returns a set of all continent IDs
- `isContinent(int continentId)`: Checks if a continent exists
- `getContinentBonus(int continentId)`: Gets the bonus armies
- `continentConquered(int player, int continent)`: Checks if player controls continent
- `continentsConquered(int player, Collection<Integer> targetIds)`: Checks multiple continents
- `playerConqueredContinents()`: Returns map of player to conquered continents

### Card System

#### Card Class (RiskCard)
Represents a risk card with its type and associated territory.

##### Properties:
- `cardType`: Type of card (Infantry, Cavalry, Artillery, Joker)
- `territoryId`: Associated territory ID (-1 for Joker)

##### Key Methods:
- `getCardType()`: Returns the card type
- `getTerritoryId()`: Returns the associated territory ID

#### Card Management Methods (RiskBoard)
- `getPlayerCards(int player)`: Returns player's cards
- `couldTradeInCards(int player)`: Checks if player can trade cards
- `hasToTradeInCards(int player)`: Checks if player must trade cards
- `getTradeInSlots(int player)`: Returns valid card combinations for trading
- `tradeIn(Set<Integer> cardIds, int player)`: Executes card trade
- `getTradeInBonus(int n)`: Gets bonus for nth trade-in
- `getTradeInBonus()`: Gets current trade-in bonus
- `drawCardIfPossible(int player)`: Draws a card for player
- `getCardsLeft()`: Returns remaining cards in deck
- `getDiscardedPile()`: Returns discarded cards
- `getNumberOfCards()`: Returns total number of cards

### Mission System

#### Mission Class (RiskMission)
Represents a player's objective for winning the game.

##### Properties:
- `missionType`: Type of mission (e.g., CONQUER_CONTINENTS, ELIMINATE_PLAYER)
- `targetIds`: Set of target IDs (continents, players, or territories)
- `atLeast`: Minimum number of targets to control (if applicable)

##### Key Methods:
- `getMissionType()`: Returns the mission type
- `getTargetIds()`: Returns the target IDs
- `getAtLeast()`: Returns the minimum target requirement

#### Mission Management Methods (RiskBoard)
- `missionFulfilled(int player)`: Checks if player completed their mission
- `missionFulfilled(RiskMission mission)`: Checks if mission is completed
- `territoriesOccupied(int player, Collection<Integer> targetIds, int atLeast)`: Checks territory control
- `selectRandomMissions(List<RiskMission> missionList, RiskMission[] playerMissions)`: Assigns missions to players

### AI Agent Considerations

#### Territory Evaluation
- Territory connectivity and strategic value
- Continent control potential
- Border security and attack vectors
- Resource distribution and reinforcement needs

#### Combat Strategy
- Optimal dice usage based on troop numbers
- Risk assessment for attacks
- Defensive positioning
- Territory capture prioritization

#### Card Management
- Card collection strategy
- Optimal trade-in timing
- Territory-card matching
- Joker usage optimization

#### Mission Planning
- Mission progress tracking
- Alternative victory paths
- Resource allocation for mission completion
- Opponent mission interference

#### State Evaluation
- Territory control assessment
- Continent bonus calculation
- Card value estimation
- Mission completion probability
- Opponent strength analysis

#### Action Selection
- Reinforcement placement optimization
- Attack target prioritization
- Fortification strategy
- Card trading decisions
- Mission-focused moves 