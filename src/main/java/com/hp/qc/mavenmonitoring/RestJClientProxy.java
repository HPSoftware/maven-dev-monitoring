package com.hp.qc.mavenmonitoring;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.ClientResponse;
import org.apache.maven.plugin.logging.Log;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;

/**
 * Created with IntelliJ IDEA. User: tsadok Date: 03/10/12 Time: 15:55 To change this template use File | Settings | File
 * Templates.
 */
public class RestJClientProxy {

    private static RestJClientProxy instance;
    private String serverUrl;
    private int timeout;
    private boolean enableLogging = false;
    private Log logger;
    private Client jerseyClient;
    private String httpProxyHost;
    private Integer httpProxyPort;

    protected RestJClientProxy() {

    }

    public static RestJClientProxy getInstance() {
        if (instance == null) {
            instance = new RestJClientProxy();
        }
        return instance;
    }

    public synchronized void init(String serverUrl, int timeout, boolean enableLogging, String httpProxyHost, Integer httpProxyPort, Log log) {
        try {
            this.serverUrl = serverUrl;
            this.timeout = timeout;

            this.enableLogging = enableLogging;
            this.logger = log;

            this.jerseyClient = Client.create();

            this.httpProxyHost = httpProxyHost;
            this.httpProxyPort = httpProxyPort;

            jerseyClient.setConnectTimeout(timeout);
            jerseyClient.setReadTimeout(timeout);

            if (enableLogging) {
                logger.info("maven-dev-monitoring server is: " + serverUrl);
                logger.info("maven-dev-monitoring timeout is: " + timeout + " ms");
            }

        } catch (Exception e) {
            if (enableLogging) {
                logger.error("maven-dev-monitoring was not able to init RestJClientProxy, why:" + e.getMessage());
            }
        }
    }

    public void createMvnMonitorEvent(JsonObject json) {

        ProxyInfo proxyInfo = new ProxyInfo(false, System.getProperty("http.proxyHost"), System.getProperty("http.proxyPort"));

        try {

            WebResource webResource = jerseyClient.resource(serverUrl);

            if (enableLogging) {
                logger.info("maven-dev-monitoring going to report the mvn event: " + json.toString());
                logger.info("maven-dev-monitoring going to POST the json to: " + serverUrl);
            }

            String s = json.toString();

            changeProxySettings(proxyInfo);

            ClientResponse response = webResource.type("application/json")
                    .post(ClientResponse.class, json.toString());

            String output = response.getEntity(String.class);

            if (response.getStatus() != 201) {
                throw new RuntimeException("Failed : HTTP error code : "
                        + response.getStatus() + ", body: " + output);
            }

            if (enableLogging) {
                logger.info("maven-dev-monitoring successfully report the mvn event: " + json.toString() + " response from server: " + output);
            }
        } catch (Exception e) {

            if (enableLogging) {
                logger.error("maven-dev-monitoring couldn't send your mvn event: " + json.toString() + ")", e);
            }
        } finally {
            revertProxySettings(proxyInfo);
        }
    }

    private void changeProxySettings(ProxyInfo proxyInfo)
    {
        if (httpProxyPort != null && httpProxyHost != null) {
            System.setProperty("http.proxyHost", httpProxyHost);
            System.setProperty("http.proxyPort", httpProxyPort.toString());

            proxyInfo.setProxySettingsChanged(true);

            if (enableLogging) {
                logger.info("maven-dev-monitoring changed java http proxy settings from: ("
                        + proxyInfo.getCurrentProxyHost() + ":" + proxyInfo.getCurrentProxyPort() + ") to: ("
                        + httpProxyHost + ":" + httpProxyPort + ")");
            }
        }
        else
        {
            logger.info("maven-dev-monitoring did not change java http proxy settings since one of the proxy params was null." +
                    "java proxy settings to be used: (" + proxyInfo.getCurrentProxyHost() + ":" + proxyInfo.getCurrentProxyPort() + ")");
        }
    }

    private void revertProxySettings(ProxyInfo proxyInfo)
    {
        if (proxyInfo.isProxySettingsChanged()) {
            if (proxyInfo.getCurrentProxyHost() == null){
                System.clearProperty("http.proxyHost");
            }
            else {
                System.setProperty("http.proxyHost", proxyInfo.getCurrentProxyHost());
            }

            if (proxyInfo.getCurrentProxyPort() == null){
                System.clearProperty("http.proxyPort");
            }
            else {
                System.setProperty("http.proxyPort", proxyInfo.getCurrentProxyPort());
            }

            if (enableLogging) {
                logger.info("maven-dev-monitoring changed java http proxy settings to the original settings: ("
                        + proxyInfo.getCurrentProxyHost() + ":" + proxyInfo.getCurrentProxyPort() + ") instead of: (" + httpProxyHost + ":" + httpProxyPort + ")");
            }
        }
    }

    private class ProxyInfo
    {
        boolean proxySettingsChanged = false;
        String currentProxyHost;
        String currentProxyPort;

        private ProxyInfo(boolean proxySettingsChanged, String currentProxyHost, String currentProxyPort) {
            this.proxySettingsChanged = proxySettingsChanged;
            this.currentProxyHost = currentProxyHost;
            this.currentProxyPort = currentProxyPort;
        }

        public boolean isProxySettingsChanged() {
            return proxySettingsChanged;
        }

        public void setProxySettingsChanged(boolean proxySettingsChanged) {
            this.proxySettingsChanged = proxySettingsChanged;
        }

        public String getCurrentProxyHost() {
            return currentProxyHost;
        }

        public String getCurrentProxyPort() {
            return currentProxyPort;
        }

    }
}
