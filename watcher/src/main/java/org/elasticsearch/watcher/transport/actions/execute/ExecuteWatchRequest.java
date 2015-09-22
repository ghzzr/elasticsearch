/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.watcher.transport.actions.execute;

import org.elasticsearch.action.ActionRequestValidationException;
import org.elasticsearch.action.ValidateActions;
import org.elasticsearch.action.support.master.MasterNodeReadRequest;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.watcher.client.WatchSourceBuilder;
import org.elasticsearch.watcher.execution.ActionExecutionMode;
import org.elasticsearch.watcher.support.validation.Validation;
import org.elasticsearch.watcher.trigger.TriggerEvent;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * An execute watch request to execute a watch by id
 */
public class ExecuteWatchRequest extends MasterNodeReadRequest<ExecuteWatchRequest> {

    public static final String INLINE_WATCH_ID = "_inlined_";

    private String id;
    private boolean ignoreCondition = false;
    private boolean recordExecution = false;
    private @Nullable Map<String, Object> triggerData = null;
    private @Nullable Map<String, Object> alternativeInput = null;
    private Map<String, ActionExecutionMode> actionModes = new HashMap<>();
    private BytesReference watchSource;

    private boolean debug = false;

    public ExecuteWatchRequest() {
    }

    /**
     * @param id the id of the watch to execute
     */
    public ExecuteWatchRequest(String id) {
        this.id = id;
    }

    /**
     * @return The id of the watch to be executed
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the id of the watch to be executed
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * @return Should the condition for this execution be ignored
     */
    public boolean isIgnoreCondition() {
        return ignoreCondition;
    }

    /**
     * @param ignoreCondition set if the condition for this execution be ignored
     */
    public void setIgnoreCondition(boolean ignoreCondition) {
        this.ignoreCondition = ignoreCondition;
    }

    /**
     * @return Should this execution be recorded in the history index
     */
    public boolean isRecordExecution() {
        return recordExecution;
    }

    /**
     * @param recordExecution Sets if this execution be recorded in the history index
     */
    public void setRecordExecution(boolean recordExecution) {
        this.recordExecution = recordExecution;
    }

    /**
     * @return The alertnative input to use (may be null)
     */
    public Map<String, Object> getAlternativeInput() {
        return alternativeInput;
    }

    /**
     * @param alternativeInput Set's the alernative input
     */
    public void setAlternativeInput(Map<String, Object> alternativeInput) {
        this.alternativeInput = alternativeInput;
    }

    /**
     * @param data The data that should be associated with the trigger event.
     */
    public void setTriggerData(Map<String, Object> data) throws IOException {
        this.triggerData = data;
    }

    /**
     * @param event the trigger event to use
     */
    public void setTriggerEvent(TriggerEvent event) throws IOException {
        setTriggerData(event.data());
    }

    /**
     * @return the trigger to use
     */
    public Map<String, Object> getTriggerData() {
        return triggerData;
    }

    /**
     * @return the source of the watch to execute
     */
    public BytesReference getWatchSource() {
        return watchSource;
    }

    /**
     * @param watchSource instead of using an existing watch use this non persisted watch
     */
    public void setWatchSource(BytesReference watchSource) {
        this.watchSource = watchSource;
    }

    /**
     * @param watchSource instead of using an existing watch use this non persisted watch
     */
    public void setWatchSource(WatchSourceBuilder watchSource) {
        this.watchSource = watchSource.buildAsBytes(XContentType.JSON);
    }

    /**
     * @return  the execution modes for the actions. These modes determine the nature of the execution
     *          of the watch actions while the watch is executing.
     */
    public Map<String, ActionExecutionMode> getActionModes() {
        return actionModes;
    }

    /**
     * Sets the action execution mode for the give action (identified by its id).
     *
     * @param actionId      the action id.
     * @param actionMode    the execution mode of the action.
     */
    public void setActionMode(String actionId, ActionExecutionMode actionMode) {
        actionModes.put(actionId, actionMode);
    }

    /**
     * @return whether the watch should execute in debug mode. In debug mode the execution {@code vars}
     *         will be returned as part of the watch record.
     */
    public boolean isDebug() {
        return debug;
    }

    /**
     * @param debug indicates whether the watch should execute in debug mode. In debug mode the
     *              returned watch record will hold the execution {@code vars}
     */
    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException validationException = null;
        if (id == null && watchSource == null){
            validationException = ValidateActions.addValidationError("watch id is missing", validationException);
        }
        if (id != null) {
            Validation.Error error = Validation.watchId(id);
            if (error != null) {
                validationException = ValidateActions.addValidationError(error.message(), validationException);
            }
        }
        for (Map.Entry<String, ActionExecutionMode> modes : actionModes.entrySet()) {
            Validation.Error error = Validation.actionId(modes.getKey());
            if (error != null) {
                validationException = ValidateActions.addValidationError(error.message(), validationException);
            }
        }
        if (watchSource != null && id != null) {
            validationException = ValidateActions.addValidationError("a watch execution request must either have a watch id or an inline watch source but not both", validationException);
        }
        if (watchSource != null && recordExecution) {
            validationException = ValidateActions.addValidationError("the execution of an inline watch cannot be recorded", validationException);
        }
        return validationException;
    }

    @Override
    public void readFrom(StreamInput in) throws IOException {
        super.readFrom(in);
        id = in.readOptionalString();
        ignoreCondition = in.readBoolean();
        recordExecution = in.readBoolean();
        if (in.readBoolean()){
            alternativeInput = in.readMap();
        }
        if (in.readBoolean()) {
            triggerData = in.readMap();
        }
        long actionModesCount = in.readLong();
        actionModes = new HashMap<>();
        for (int i = 0; i < actionModesCount; i++) {
            actionModes.put(in.readString(), ActionExecutionMode.resolve(in.readByte()));
        }
        if (in.readBoolean()) {
            watchSource = in.readBytesReference();
        }
        debug = in.readBoolean();
    }


    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeOptionalString(id);
        out.writeBoolean(ignoreCondition);
        out.writeBoolean(recordExecution);
        out.writeBoolean(alternativeInput != null);
        if (alternativeInput != null) {
            out.writeMap(alternativeInput);
        }
        out.writeBoolean(triggerData != null);
        if (triggerData != null) {
            out.writeMap(triggerData);
        }
        out.writeLong(actionModes.size());
        for (Map.Entry<String, ActionExecutionMode> entry : actionModes.entrySet()) {
            out.writeString(entry.getKey());
            out.writeByte(entry.getValue().id());
        }
        out.writeBoolean(watchSource != null);
        if (watchSource != null) {
            out.writeBytesReference(watchSource);
        }
        out.writeBoolean(debug);
    }

    @Override
    public String toString() {
        return "execute[" + id + "]";
    }
}
