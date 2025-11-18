# Resolving Dependency Conflicts via Source Code Modification

This project is part of my Master's thesis focused on resolving dependency conflicts in software projects by analyzing and modifying the source code directly. The goal is to automate the resolution of library version clashes between dependencies by modifying the source code of the broken dependency.

## Approach
1. Extract dependencies using `mavenSourceLinkPre` and `mavenSourceLinkBreaking`.
2. Verify that `preCommitReproductionCommand` works.
3. Extract source code and all dependencies from the broken container.
4. Execute `breakingUpdateReproductionCommand` to get the broken Logs.
5. Determine if the errors of the Log are fixable via source code modification.
6. Get the error context (target class, target method, method parameters) for each error.
7. Fix trivial errors deterministically, complex errors with LLMs.
8. Inject fix into the broken container.
9. Execute the container to validate the result.


## Requirements
Docker, Ollama.
Make sure to pull the Ollama models first before using them!

## Usage

Create a local folder with any name, henceforth referred to as `workFolder`.
Inside the `workFolder`, create a folder file with any name, henceforth referred to as `bumpFolder`.
Inside `bumpFolder`, add a json file for each project with the following format:

```
{
    "project" : "<github_project>",
    "updatedDependency" : {
        "dependencyGroupID" : "<group id>",
        "dependencyArtifactID" : "<artifact id>",
        "previousVersion" : "<label indicating the previous version of the dependency>",
        "newVersion" : "<label indicating the new version of the dependency>",
        "mavenSourceLinkPre" : "<maven source jar link for the previous release of the updated dependency if it exists>",
        "mavenSourceLinkBreaking" : "<maven source jar link for the breaking release of the updated dependency if it exists>",
        "updatedFileType" : "JAR"
    },
    "preCommitReproductionCommand" : "docker run <preCommitImage>",
    "breakingUpdateReproductionCommand" : "docker run <breakingImage>"
}
```

Inside the `workFolder`, create a JSON file with any name, henceforth referred to as `jsonConfig`.
Inside the `jsonConfig`, specify the following:

```
{
  "pathToBUMPFolder": "<path to bumpFolder>",
  "threads": <amount of concurrent threads>,
  "llmRetries": <amount of times the llm may iterate over errors>,
  "pathToOutput": <path to output>,
  "llmProvider": "<ollama|openai|anthropic",
  "ollamaUri": "<uri to access ollama (usually http://localhost:11434 or http://host.docker.internal:11434)>",
  "llmName": "<name of the llm (for example qwen3-coder:480b-cloud)>",
  "dockerHostUri": "uri to access docker (usually tcp://localhost:2375 or tcp://host.docker.internal:2375)>",
  "dockerUsername": "<optional docker username>",
  "dockerPassword": "<optional docker password>",
  "dockerRegistryUri": "<optional docker registry>",
  "wordSimilarityModel": "<name of encoder model (for example nomic-embed-text)>",
  "llmApiKey": "<api key if llmProvider is openai or anthropic>"
}
```

Finally, run the docker container:
```
docker run --rm -v <pathToWorkFolder>:/app/<workFolder> chrhuettner/dependencyconflictresolver:latest bump /app/<workFolder>/<jsonConfig>
```

The program will automatically generate folders for caching and intermediary results. The fixed classes will be located inside the ``correctedClasses`` folder, further subdivided into iterations. If the same class is in multiple iteration folders, take the file inside the highest iteration (the others are just intermediary results and still erroneous).


If you want to execute BUMP projects, you may copy the ``downloaded``, ``oldContainerLogs``, ``projectSources`` and ``brokenClasses`` folders to your ``workFolder`` beforehand. This will speed up the execution.  


## Attribution
This project uses [japicmp](https://github.com/siom79/japicmp) to calculate the differences between dependency versions.
Source code analysis is done using [Spoon](https://spoon.gforge.inria.fr/), compiled dependency and JRE analysis with [ASM](https://asm.ow2.io/).
Furthermore, it is benchmarked with [BUMP](https://github.com/chains-project/bump) and uses a modified form of its json format.