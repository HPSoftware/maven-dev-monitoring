package com.hp.qc.mavenmonitoring;

import org.apache.maven.execution.*;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by tsadok on 21/01/2015.
 */
public class ExecutionListenerHandler implements InvocationHandler{

    private static final String METHOD_NAME_OF_SESSION_ENDED = "sessionEnded";
    private final ExecutionListener defaultExecutionListener;
    private final MvnMonitorMojo mvnMonitorMojo;

    public ExecutionListenerHandler(ExecutionListener defaultExecutionListener, MvnMonitorMojo mvnMonitorMojo) {
        this.defaultExecutionListener = defaultExecutionListener;
        this.mvnMonitorMojo = mvnMonitorMojo;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (isExecutedSessionEnded(method)) {
            sessionEnded();
        }

        return method.invoke(defaultExecutionListener, args);
    }

    @SuppressWarnings("deprecation")
    public void sessionEnded() {

        try {
            MuteConsole.setMutingMode(!mvnMonitorMojo.getEnableLogging());
            MavenSession session = mvnMonitorMojo.getSession();

            JsonObjectBuilder builder = Json.createObjectBuilder();

            setJsonField(builder, "result", getResult(session));
            setJsonField(builder, "resultExceptionMessage", getResultExceptionMessage(session));
            setJsonField(builder, "userName", session.getExecutionProperties().getProperty("env.USERNAME"));
            setJsonField(builder, "mvnCommandLine", session.getExecutionProperties().getProperty("env.MAVEN_CMD_LINE_ARGS"));
            setJsonField(builder, "javaVersion", session.getExecutionProperties().getProperty("java.version"));
            setJsonField(builder, "mvnVersion", session.getExecutionProperties().getProperty("maven.version"));
            setJsonField(builder, "computerName", session.getExecutionProperties().getProperty("env.COMPUTERNAME"));
            setJsonField(builder, "topLevelProject", getTopLevelProject(session));
            setJsonField(builder, "executionDate", getDate());
            setJsonField(builder, "innerFailedProject", getInnerFailedProject(session));
            setJsonField(builder, "eventType", mvnMonitorMojo.getEventType());
            builder.add("totalTime", getTime(session));

            JsonObject json = builder.build();

            RestJClientProxy.getInstance().createMvnMonitorEvent(json);
        }
        catch (Exception e) {
                if (mvnMonitorMojo.getEnableLogging()) {
                    mvnMonitorMojo.getLog().error("maven-dev-monitoring exception in ExecutionListenerHandler", e);
                }
        } finally {
            MuteConsole.revertMute();
        }

    }

    private void setJsonField(JsonObjectBuilder builder, String fieldName, String value)
    {
        if (value == null)
            builder.add(fieldName, JsonObject.NULL);
        else
            builder.add(fieldName, value);
    }

    private String getDate() {
        DateTime dt = new DateTime();
        DateTimeFormatter fmt = ISODateTimeFormat.dateTime();
        String str = fmt.print(dt);
        return str;
    }

    /* Since Kibana 4 does not support nested objects aggregation
        There is no point in extracting nested info

        If in the future this info will be interesting we can report it as a seperate event
    private JsonArrayBuilder getProjectList(MavenSession session) {

        JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();

        for (MavenProject project : session.getProjects()) {

            JsonObjectBuilder objBuilder = Json.createObjectBuilder();
            setJsonField(objBuilder, "projectName", project.getName());

            BuildSummary buildSummary =  session.getResult().getBuildSummary(project);
            if (buildSummary != null)
            {
                objBuilder.add("time", buildSummary.getTime()/1000);
                if (buildSummary instanceof BuildSuccess)
                {
                    objBuilder.add("result", "SUCCESS");
                }
                else
                {
                    objBuilder.add("result", "FAILURE");
                }
            }
            else
            {
                objBuilder.add("time", 0);
                objBuilder.add("result", "SKIPPED");
            }

            arrayBuilder.add(objBuilder);
        }

        return arrayBuilder;
    } */

    private String getInnerFailedProject(MavenSession session) {

        for (MavenProject project : session.getProjects()) {

            BuildSummary buildSummary = session.getResult().getBuildSummary(project);
            if (buildSummary != null) {
                if (buildSummary instanceof BuildFailure) {
                    return project.getName();
                }
            }
        }

        return null;
    }

    private String getTopLevelProject(MavenSession session) {
        MavenProject p = session.getTopLevelProject();
        if (p != null) {
            return p.getName();
        } else {
            return null;
        }
    }

    private String getResult(MavenSession session) {

        if (session.getResult().hasExceptions()) {
            return "FAILED";
        } else {
            return "SUCCEED";
        }
    }

    private String getResultExceptionMessage(MavenSession session) {

        if (session.getResult().hasExceptions()) {
            return session.getResult().getExceptions().get(0).getMessage();
        } else {
            return null;
        }
    }

    private long getTime(MavenSession session) {
        Date finish = new Date();
        long time = finish.getTime() - session.getRequest().getStartTime().getTime();
        return time/1000;
    }

    private boolean isExecutedSessionEnded(Method method) {
        return method.getName().equals(METHOD_NAME_OF_SESSION_ENDED);
    }
}
