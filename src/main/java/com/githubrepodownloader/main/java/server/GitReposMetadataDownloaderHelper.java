package com.githubrepodownloader.main.java.server;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import javafx.util.Pair;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.methods.GetMethod;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Created by ramakrishnas on 26/7/16.
 */
public class GitReposMetadataDownloaderHelper {

    private static final Logger LOGGER = Logger.getLogger(GitReposMetadataDownloaderHelper.class.getName());

    private static HttpClient client = new HttpClient(new MultiThreadedHttpConnectionManager());
    private static Config conf = ConfigFactory.load();
    private static String[] tokens = conf.getString("githubTokens").split(",");
    private static volatile String currentToken = tokens[0];
    private static volatile int lastIndex = 0;

    public static String getResponseAsString(String url) throws IOException, JSONException {
        GetMethod method = new GetMethod(url);
        method.setDoAuthentication(true);
        method.addRequestHeader("Authorization", "token " + currentToken);
        client.executeMethod(method);

        String requestLimitRemaining = method.getResponseHeader("X-RateLimit-Remaining").getValue();
        if (Integer.valueOf(requestLimitRemaining) == 0)
            updateToken();

        if (method.getStatusCode() == 200)
            return method.getResponseBodyAsString();
        else {
            LOGGER.info("Request failed with status:" + method.getStatusCode() + "Response:"
                    + method.getResponseHeaders().toString().concat("\n") +
                    "\n ResponseBody " + method.getResponseBodyAsString());
            return null;
        }
    }

    public static Pair<Integer, List<String>> getGitHubReposInfoFrom(int since) throws IOException, JSONException {
        List<String> reposInfo = new ArrayList<>();
        JSONArray reposInfoJson = new JSONArray(getResponseAsString("https://api.github.com/repositories?since=" + since));
        int noOfReposInfo = reposInfoJson.length();
        int nextSinceValue = since;
        for (int reposInfoIndex = 0; reposInfoIndex < noOfReposInfo; ++reposInfoIndex) {
            JSONObject repoInfoJson = reposInfoJson.getJSONObject(reposInfoIndex);
            nextSinceValue = repoInfoJson.getInt("id");
            if ("false".equals(repoInfoJson.getString("fork")))
                reposInfo.add(repoInfoJson.getString("full_name"));
        }
        return new Pair<>(nextSinceValue, reposInfo);
    }

    private static void updateToken() {
        if (lastIndex == tokens.length - 1) {
            lastIndex = 0;
            currentToken = tokens[lastIndex];
        } else {
            lastIndex = lastIndex + 1;
            currentToken = tokens[lastIndex];
        }
        LOGGER.info("limit 0,token changed :" + currentToken);
    }
}
