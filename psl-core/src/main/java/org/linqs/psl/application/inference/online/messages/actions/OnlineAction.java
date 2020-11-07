/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2020 The Regents of the University of California
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.linqs.psl.application.inference.online.messages.actions;

import org.linqs.psl.application.inference.online.messages.OnlineMessage;
import org.linqs.psl.application.inference.online.messages.actions.controls.*;
import org.linqs.psl.application.inference.online.messages.actions.model.updates.AddAtom;
import org.linqs.psl.application.inference.online.messages.actions.model.updates.DeleteAtom;
import org.linqs.psl.application.inference.online.messages.actions.model.updates.ObserveAtom;
import org.linqs.psl.application.inference.online.messages.actions.model.updates.UpdateObservation;
import org.linqs.psl.application.inference.online.messages.actions.template.modifications.AddRule;

import java.util.UUID;

/**
 * Base class for online actions.
 * All actions should be able to freely convert to and from strings.
 */
public abstract class OnlineAction extends OnlineMessage {

    public OnlineAction(UUID identifier, String clientCommand) {
        super(identifier, clientCommand);
    }

    public OnlineAction() {
        super();
    }

    /**
     * Construct an OnlineAction given the name and necessary information.
     */
    public static OnlineAction getAction(String clientCommand) {
        return getAction(UUID.randomUUID(), clientCommand);
    }

    public static OnlineAction getAction(UUID actionID, String clientCommand) {
        String actionClass = clientCommand.split("\t")[0].trim();

        if (actionClass.equalsIgnoreCase("Add")) {
            return new AddAtom(actionID, clientCommand);
        } else if (actionClass.equalsIgnoreCase("AddRule")) {
            return new AddRule(actionID, clientCommand);
        } else if (actionClass.equalsIgnoreCase("Observe")) {
            return new ObserveAtom(actionID, clientCommand);
        } else if (actionClass.equalsIgnoreCase("Stop")) {
            return new Stop(actionID, clientCommand);
        } else if (actionClass.equalsIgnoreCase("Sync")) {
            return new Sync(actionID, clientCommand);
        } else if (actionClass.equalsIgnoreCase("Exit")) {
            return new Exit(actionID, clientCommand);
        } else if (actionClass.equalsIgnoreCase("Delete")) {
            return new DeleteAtom(actionID, clientCommand);
        } else if (actionClass.equalsIgnoreCase("Update")) {
            return new UpdateObservation(actionID, clientCommand);
        } else if (actionClass.equalsIgnoreCase("Query")) {
            return new QueryAtom(actionID, clientCommand);
        } else if (actionClass.equalsIgnoreCase("Write")) {
            return new WriteInferredPredicates(actionID, clientCommand);
        } else {
            throw new IllegalArgumentException("Unknown online action: '" + actionClass + "'.");
        }
    }
}
