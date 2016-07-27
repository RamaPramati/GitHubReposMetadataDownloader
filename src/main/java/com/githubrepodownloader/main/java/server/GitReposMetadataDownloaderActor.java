package com.githubrepodownloader.main.java.server;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.routing.RoundRobinPool;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import javafx.util.Pair;

import java.io.*;
import java.util.List;
import java.util.logging.Logger;

/**
 * Created by ramakrishnas on 26/7/16.
 */
class GitReposMetadataDownloaderActor extends UntypedActor {

    private static final Logger LOGGER = Logger.getLogger(GitReposMetadataDownloaderActor.class.getName());

    private Config conf = ConfigFactory.load();
    private int noOfRepos = 0;
    private int noOfWorkers = conf.getInt("noOfWorkers");

    private PrintWriter resultFileWriter = null;

    private ActorRef workerRouter = context().actorOf(new RoundRobinPool(noOfWorkers).
            props(Props.create(RepoDataDownloaderActor.class)), "workers");

    private ActorRef clientGitReposMetadataDownloaderActor = context().actorFor
            (conf.getString("clientGitReposMetadataDownloaderPath"));

    public GitReposMetadataDownloaderActor() throws FileNotFoundException {
        resultFileWriter = new PrintWriter(conf.getString("metadataDir") + "ReposMetadata.txt");
    }

    @Override
    public void onReceive(Object message) throws Exception {
        if (message instanceof UpdateTaskDone) {
            noOfRepos -= 1;
            if (0 == noOfRepos) {
                clientGitReposMetadataDownloaderActor.tell("COMPLETED", getSelf());
                String[] fileLocations = conf.getString("fileLocations").split(",");
                int fileLocaitonIndex = 0;
                while (fileLocaitonIndex < fileLocations.length - 1) {
                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader
                            (new FileInputStream(new File(conf.getString("metadataDir") +
                                    fileLocations[fileLocaitonIndex]))));
                    String aLine;
                    while ((aLine = bufferedReader.readLine()) != null)
                        resultFileWriter.println(aLine);
                    bufferedReader.close();
                    fileLocaitonIndex++;
                }
                resultFileWriter.close();
                sender().tell("COMPLETED", getSelf());
                LOGGER.info("Task done...");
            }
        } else if (message instanceof String) {
            sender().tell("RECEIVED", getSelf());
            String[] repoRange = message.toString().split("-");
            int since = Integer.valueOf(repoRange[0]);
            int to = Integer.valueOf(repoRange[1]);
            if (to > since) {
                LOGGER.info("Started Getting repositories info from " + since + " to " + to);
                int currentSince = since;
                while (currentSince < to) {
                    Pair<Integer, List<String>> allGithubRepos = GitReposMetadataDownloaderHelper.
                            getGitHubReposInfoFrom(currentSince);
                    noOfRepos += allGithubRepos.getValue().size();
                    allGithubRepos.getValue().forEach(repoInfo -> workerRouter.tell(repoInfo, getSelf()));
                    currentSince = allGithubRepos.getKey();
                }
            } else
                sender().tell("INPUT-ERROR", getSelf());
        }
    }
}
