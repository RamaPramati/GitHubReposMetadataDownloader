package com.githubrepodownloader.main.java.client;

/**
 * Created by ramakrishnas on 26/7/16.
 */

import akka.actor.ActorRef;
import akka.actor.UntypedActor;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.util.logging.Logger;

class ClientGitReposMetadataDownloaderActor extends UntypedActor {

    private static final Logger LOGGER = Logger.getLogger(ClientGitReposMetadataDownloaderActor.class.getName());
    Config conf = ConfigFactory.load();
    ActorRef githubReposMetadataDownloaderActor = context().actorFor(conf.getString("remoteGitReposMetadataDownloaderPath"));

    @Override
    public void onReceive(Object message) throws Exception {
        if (message.equals("RECEIVED"))
            LOGGER.info("Remote actor started the given task........");
        else if (message.equals("INPUT-ERROR"))
            LOGGER.info("Wrong input........");
        else if (message.equals("COMPLETED")) {
            LOGGER.info("Remote actor completed the given task.......");
            context().system().shutdown();
        } else
            githubReposMetadataDownloaderActor.tell(message, getSelf());
    }
}
