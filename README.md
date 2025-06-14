# ReadMe
## TODO 
- [ ] Debug to visualize the tree
- [ ] Additional heuristic based on own and enemy strength, to support the win/visits heuristic
- [ ] Culling the tree before the expansion or after first visit of the node
- [ ] Add a heuristic to the MCTS agent to make use of the "Strategy Tips" in [Links](#Links)

## Commands
**Agents:** \
HighRoller \
RandomAgent \
RiskAgent \
MCTSAgent \

**match between 2 agents:**
```bash
java -jar sge-1.0.7-exe.jar match --file=sge-risk-1.0.7-exe.jar --directory=agents --agent HighRoller RandomAgent --debug
```
## Build
```bash
./gradlew clean build
```
## Links
Calcultions/Strategy for the game Risk: \
http://www.datagenetics.com/blog/november22011/index.html
- STRATEGY TIP – It's better to attack then defend. Be aggressive.

- STRATEGY TIP – Always attack with superior numbers to maximize the chances of your attack being successeful.

- STRATEGY TIP – If attacking a region with the same number of armies as the defender, make sure that you have at least five armies if you want the odds in your favour (the more the better).

Strategy: \
https://web.mit.edu/sp.268/www/risk.pdf

## Agent Flow
### HighRoller - computeNextAction
- MCTSAgent - sortPromisingCandidates
  - check for Actions that end the game
- while not shouldStopComputation()
  - MCTSAgent
    - select
    - expand
    - simulate
    - backpropagate
  - return bestAction

### MCTSAgent 
  - sortPromisingCandidates
    - calculate and save GameStateScore for each candidate
    - sort by GameStateScore (TerritoryScore, TroopScore, ContinentScore, AttackPotential)
    - tree = getBestChild
  - selection
    - while not Leaf and not shouldStopComputation
      - tree = max(children, gameTreeSelectionComparator)
  - expansion
    - add all possible actions as child nodes to tree
  - simulation
    - while not game is over AND depth < max_depth AND not shouldStopComputation
      - selectActionWithHighestGameStateScore
  - backpropagation
    - Incline Plays and Wins

  - selectActionWithHighestGameStateScore
    - for all possible actions
      - check or set GameStateScore
      - UCT
        - Exploitation is based on GameStateScore
        - Exploration is based on visits
      - return bestAction
  - hasWon
    - return 1 if win OR UtilityScore && random.nextBoolean if tie

### RiskMetricsCalculator
  - GameStateScore
    - territoryScore = myTerritories / totalTerritories
    - troopScore = myTroops / totalTroops
    - continentScore = per Continent: 
      - myTerritories / totalTerritories) / continentCount
    - attackPotential = Per owned Territory:
      - 1 if myTroop/enemyTroop ratio >= 2
      - 0.8 if myTroops >= enemyTroops + 5
      - 0.3 if ratio >= 1
      - 0.1 else
      - divided by bordering enemy Territories