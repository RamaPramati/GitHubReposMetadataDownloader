package com.githubrepodownloader.main.java.server;

import akka.actor.UntypedActor;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.NoSuchElementException;
import java.util.logging.Logger;

/**
 * Created by ramakrishnas on 26/7/16.
 */
class RepoDataDownloaderActor extends UntypedActor {

    private static final Logger LOGGER = Logger.getLogger(GitReposMetadataDownloaderActor.class.getName());
    private PrintWriter repoMetadataWriter = null;

    public RepoDataDownloaderActor() throws FileNotFoundException {
        Config conf = ConfigFactory.load();
        repoMetadataWriter = new PrintWriter(conf.getString("metadataDir") + getSelf().path().name() + ".txt");
    }

    @Override
    public void onReceive(Object message) throws Exception {
        if (message.equals("COMPLETED")) {
            LOGGER.info("####### Check the ReposMetadata.txt file...... ");
            repoMetadataWriter.close();
        } else {
            try {
                String repoMetadata = GitReposMetadataDownloaderHelper.
                        getResponseAsString("https://api.github.com/repos/" + message) + "\n";
                repoMetadataWriter.write(repoMetadata);
            } catch (NoSuchElementException noSuchElementException) {
                LOGGER.info("Failed to download repo metadata: " + noSuchElementException.getMessage());
                // self().tell(message, getSelf());
            } finally {
                sender().tell(new UpdateTaskDone(), getSelf());
            }
        }
    }
}
