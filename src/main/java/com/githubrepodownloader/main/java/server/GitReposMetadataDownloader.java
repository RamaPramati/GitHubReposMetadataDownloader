package com.githubrepodownloader.main.java.server;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import com.typesafe.config.ConfigFactory;

/**
 * Created by ramakrishnas on 26/7/16.
 */
public class GitReposMetadataDownloader {
    public static void main(String args[]) {

        ActorSystem system = ActorSystem.create("GitRepoMetadataDownloaderActorSystem",
                ConfigFactory.load("gitHubReposMetadataDownloader.conf"));
        ActorRef gitReposMetadataDownloaderActor = system.actorOf(Props.create(GitReposMetadataDownloaderActor.class),
                "gitReposMetadataDownloaderActor");
    }
}
