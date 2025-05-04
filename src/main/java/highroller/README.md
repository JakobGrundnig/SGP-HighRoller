# ReadMe
## Commands
```bash
 cd .\src\main\java\highroller
```
**Agents:** \
**Self-playing:** \
RandomAgent \
".\agents\sge-alphabetaagent-1.0.4.jar" \
**Manual:** \
".\agents\RiskAgent-1.0.4.jar" \
**match between 2 agents:**
```bash
java -jar .\sge-1.0.7-exe.jar match --file=sge-risk-1.0.7-exe.jar --directory=agents --agent RandomAgent RandomAgent

```
## Build
```bash
./gradlew clean build
```


