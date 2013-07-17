package org.jenkinsci.plugins.rabbitmqbuildtrigger;

import java.io.UnsupportedEncodingException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.logging.Level;
import java.util.logging.Logger;

import hudson.Extension;
import hudson.ExtensionPoint;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;

import org.jenkinsci.plugins.rabbitmqconsumer.listeners.ApplicationMessageListener;

/**
 * The extension listen application message then call triggers.
 * 
 * @author rinrinne a.k.a. rin_ne
 */
@Extension
public class RemoteBuildListener implements ExtensionPoint, ApplicationMessageListener {
    private static final String PLUGIN_NAME = "Remote Builder";

    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String KEY_PROJECT = "project";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_PARAMETER = "parameter";

    private static final Logger LOGGER = Logger.getLogger(RemoteBuildListener.class.getName());

    private final Set<RemoteBuildTrigger> triggers = new CopyOnWriteArraySet<RemoteBuildTrigger>();

    /**
     * @inheritDoc
     * @return the name.
     */
    public String getName() {
        return PLUGIN_NAME;
    }

    /**
     * @inheritDoc
     * @return the application id.
     */
    public String getAppId() {
        return RemoteBuildTrigger.PLUGIN_APPID;
    }

    /**
     * Adds trigger.
     * 
     * @param trigger
     *            the trigger.
     */
    public void addTrigger(RemoteBuildTrigger trigger) {
        triggers.add(trigger);
    }

    /**
     * Removes trigger.
     * 
     * @param trigger
     *            the trigger.
     */
    public void removeTrigger(RemoteBuildTrigger trigger) {
        triggers.remove(trigger);
    }

    /**
     * @inheritDoc
     * @param queueName
     *            the queue name.
     */
    public void onBind(String queueName) {
        LOGGER.info("Bind to: " + queueName);
    }

    /**
     * @inheritDoc
     * @param queueName
     *            the queue name.
     */
    public void onUnbind(String queueName) {
        LOGGER.info("Unbind from: " + queueName);
    }

    /**
     * Finds matched projects using given project name and token then schedule
     * build.
     * 
     * @inheritDoc
     * @param queueName
     *            the queue name.
     * @param json
     *            the content of message.
     */
    public void onReceive(String queueName, String contentType, byte[] body) {
        if (CONTENT_TYPE_JSON.equals(contentType)) {
            try {
                String msg = new String(body, "UTF-8");
                try {
                    JSONObject json = (JSONObject) JSONSerializer.toJSON(msg);
                    for (RemoteBuildTrigger t : triggers) {

                        if (t.getRemoteBuildToken() == null) {
                            LOGGER.log(Level.WARNING, "ignoring AMQP trigger for project {0}: no token set", t.getProjectName());
                            continue;
                        }

                        if (t.getProjectName().equals(json.getString(KEY_PROJECT))
                                && t.getRemoteBuildToken().equals(json.getString(KEY_TOKEN))) {
                            t.scheduleBuild(queueName, json.getJSONArray(KEY_PARAMETER));
                        }
                    }
                } catch (JSONException e) {
                    LOGGER.warning("Invalid JSON format string: ");
                }
            } catch (UnsupportedEncodingException e) {
                LOGGER.warning("Unsupported encoding. Is message body is not string?");
            }
        }
    }
}
