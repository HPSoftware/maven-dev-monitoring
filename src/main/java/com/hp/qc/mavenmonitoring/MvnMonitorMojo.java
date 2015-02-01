package com.hp.qc.mavenmonitoring;


import org.apache.maven.execution.ExecutionListener;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;

import java.lang.reflect.Proxy;

/**
 * Monitor maven command execution, and report the command and execution time to
 * a centralize location for further analysis. This plugin can help improve the
 * maven environment for the development group, find out Jankins slaves bottlenecks and more.
 * Using the centralize location we can find out "weak computers", "execution regressions in specific commands",
 * "developer using maven in an inefficient way" and more
 * 
 * @goal monitor-and-report
 * @aggregator true
 * @threadSafe
 * @phase initialize  
 */
public class MvnMonitorMojo extends AbstractMojo {

    /**
	 * The Maven Session Object
	 * 
	 * @parameter expression = "${session}"
	 * @required
	 * @readonly
	 */
	protected MavenSession session;

	/**
	 * When false - no logging will be printed to the mvn console (even for
	 * exceptions). When true - logging will be printed to the console on both
	 * succeed and failed operations.
	 * 
	 * @parameter expression="${maven.dev.monitoring.enableLogging}" default-value="false"
	 */
	private boolean enableLogging;


	/**
	 * The HTTP url to POST the maven event into (some kind of REST service)
	 * 
	 * @parameter expression="${maven.dev.monitoring.serverUrl}" default-value="http://16.60.161.19:9200/agm/mvn-monitor"
	 */
	private String serverUrl;

    /**
     * The http proxy host to use when reporting the mvn event to the serverUrl
     *
     * @parameter expression="${maven.dev.monitoring.httpProxyHost}" default-value="${settings.proxies[0].host}"
     */
    private String httpProxyHost;

    /**
     * The http proxy port to use when reporting the mvn event to the serverUrl
     *
     * @parameter expression="${maven.dev.monitoring.httpProxyPort}" default-value="${settings.proxies[0].port}"
     */
    private Integer httpProxyPort;

	/**
	 * connection timeout (so user won't need to wait in case that the service is down for instance)
	 *
	 * @parameter expression="${maven.dev.monitoring.timeout}" default-value=5000
	 */
	private int timeout;

    /**
     * enabling the monitoring, if false - Mojo is not doing a thing
     *
     * @parameter expression="${maven.dev.monitoring.enableMonitoring}" default-value="true"
     */
    private boolean enableMonitoring;

    /**
     * The event type (i.e. - CIEvent, DevEvent, etc..), can help later on when
     * aggregating the data for reports.
     *
     * The best way to use this param is using settings.xml
     *
     * @parameter expression="${maven.dev.monitoring.eventType}" default-value="defaultEvent"
     */
    private String eventType;

	private static final Object lock = new Object();
	private static boolean initialized = false;

    public MavenSession getSession() {
        return session;
    }

    public boolean getEnableLogging()
    {
        return enableLogging;
    }

    public String getEventType()
    {
        return eventType;
    }

	public void execute() throws MojoExecutionException {
		synchronized (lock) {

			try {

				if (initialized||!enableMonitoring) {
					return;
				}

                //Take the current executionListener and add using reflaction the code that sends
                //mvn event over the net
                MavenExecutionRequest request = session.getRequest();
                ExecutionListener executionListener = request.getExecutionListener();

                ExecutionListenerHandler executionListenerHandler =
                        new ExecutionListenerHandler(executionListener, this);

                Object instance = Proxy.newProxyInstance(this.getClass()
                                .getClassLoader(), new Class[]{ExecutionListener.class},
                        executionListenerHandler);
                request.setExecutionListener((ExecutionListener) instance);

                //Init the REST client proxy
				RestJClientProxy.getInstance().init(serverUrl, timeout, enableLogging, httpProxyHost, httpProxyPort, getLog());

				initialized = true;
			}

			catch (Exception e) {
				if (enableLogging) {
					getLog().error("maven-dev-monitoring has a failure in Mojo execution: ", e);
				}
				initialized=true;
			}
		}
	}
}