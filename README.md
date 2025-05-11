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

#


